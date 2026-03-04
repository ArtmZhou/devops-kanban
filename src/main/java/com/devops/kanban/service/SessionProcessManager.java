package com.devops.kanban.service;

import com.devops.kanban.entity.Session;
import com.devops.kanban.repository.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages AI agent processes for interactive sessions.
 * Handles process lifecycle, I/O streams, and real-time output broadcasting.
 */
@Service
@Slf4j
public class SessionProcessManager {

    private static final long PERSIST_INTERVAL_MS = 5000; // 5 seconds debounce
    private static final int BUFFER_SIZE = 256;  // Character buffer size for streaming
    private static final long FLUSH_INTERVAL_MS = 80; // 80ms time window for flushing chunks

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRepository sessionRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Active process management
    private final ConcurrentHashMap<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, BufferedWriter> processInputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StringBuilder> sessionOutputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastPersistTime = new ConcurrentHashMap<>();

    public SessionProcessManager(SimpMessagingTemplate messagingTemplate, SessionRepository sessionRepository) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Start a new process for a session
     *
     * @param sessionId  the session ID
     * @param command    the command array to execute
     * @param workingDir the working directory
     * @return the started process
     */
    public Process startProcess(Long sessionId, String[] command, Path workingDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.environment().putAll(System.getenv());
            // Remove CLAUDECODE env var to allow nested Claude Code sessions
            pb.environment().remove("CLAUDECODE");

            // Don't merge stderr - we want to handle them separately
            Process process = pb.start();

            // Store process reference
            activeProcesses.put(sessionId, process);

            // Store output buffer for this session
            sessionOutputs.put(sessionId, new StringBuilder());

            // Setup stdin writer for interactive input
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            processInputs.put(sessionId, writer);

            // Start stdout reader thread
            executor.submit(() -> readStream(sessionId, process.getInputStream(), "stdout"));

            // Start stderr reader thread
            executor.submit(() -> readStream(sessionId, process.getErrorStream(), "stderr"));

            log.info("Started process for session {}: {}", sessionId, String.join(" ", command));
            return process;

        } catch (IOException e) {
            log.error("Failed to start process for session {}", sessionId, e);
            throw new RuntimeException("Failed to start process", e);
        }
    }

    /**
     * Stop a running process
     * First attempts graceful termination (SIGTERM), then forces kill (SIGKILL) if needed.
     *
     * @param sessionId the session ID
     */
    public void stopProcess(Long sessionId) {
        // Persist output before stopping
        persistOutput(sessionId);

        Process process = activeProcesses.remove(sessionId);
        BufferedWriter writer = processInputs.remove(sessionId);

        if (process != null) {
            // First attempt graceful termination (SIGTERM)
            process.destroy();

            try {
                // Wait 2 seconds for graceful termination
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    log.warn("Process for session {} did not terminate gracefully, forcing...", sessionId);
                    process.destroyForcibly();
                    // Wait 1 second for forced termination to complete
                    process.waitFor(1, TimeUnit.SECONDS);
                }
                log.info("Stopped process for session {} (exit code: {})", sessionId, process.exitValue());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Force kill if interrupted
                process.destroyForcibly();
                log.warn("Interrupted while stopping process for session {}", sessionId);
            }
        }

        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.warn("Failed to close process input for session {}", sessionId);
            }
        }

        // Broadcast stopped status
        broadcastStatus(sessionId, "STOPPED");
    }

    /**
     * Send input to a running process
     *
     * @param sessionId the session ID
     * @param input     the input to send
     * @return true if input was sent successfully
     */
    public boolean sendInput(Long sessionId, String input) {
        BufferedWriter writer = processInputs.get(sessionId);
        if (writer == null) {
            log.warn("No active input stream for session {}", sessionId);
            return false;
        }

        try {
            writer.write(input);
            writer.newLine();
            writer.flush();
            log.debug("Sent input to session {}: {}", sessionId, input);

            // Store input in output buffer for persistence
            StringBuilder output = sessionOutputs.get(sessionId);
            if (output != null) {
                output.append("> ").append(input).append("\n");
                // Persist immediately to ensure input is saved
                persistOutput(sessionId);
            }

            // Echo the input to output via WebSocket
            broadcastChunk(sessionId, "stdin", "> " + input + "\n", true);
            return true;

        } catch (IOException e) {
            log.error("Failed to send input to session {}", sessionId, e);
            return false;
        }
    }

    /**
     * Check if a process is alive
     *
     * @param sessionId the session ID
     * @return true if process is running
     */
    public boolean isProcessAlive(Long sessionId) {
        Process process = activeProcesses.get(sessionId);
        return process != null && process.isAlive();
    }

    /**
     * Get the exit code of a process
     *
     * @param sessionId the session ID
     * @return the exit code, or -1 if process is still running or not found
     */
    public int getExitCode(Long sessionId) {
        Process process = activeProcesses.get(sessionId);
        if (process != null && !process.isAlive()) {
            return process.exitValue();
        }
        return -1;
    }

    /**
     * Get the accumulated output for a session
     *
     * @param sessionId the session ID
     * @return the output string
     */
    public String getOutput(Long sessionId) {
        StringBuilder output = sessionOutputs.get(sessionId);
        if (output != null && output.length() > 0) {
            return output.toString();
        }
        // Try to load from storage if not in memory
        return loadOutputFromStorage(sessionId);
    }

    /**
     * Load output from storage for a session
     */
    private String loadOutputFromStorage(Long sessionId) {
        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        return sessionOpt.map(session -> session.getOutput() != null ? session.getOutput() : "").orElse("");
    }

    /**
     * Initialize output buffer from storage (for resuming sessions)
     *
     * @param sessionId the session ID
     */
    public void initializeOutput(Long sessionId) {
        String storedOutput = loadOutputFromStorage(sessionId);
        if (storedOutput != null && !storedOutput.isEmpty()) {
            StringBuilder buffer = sessionOutputs.computeIfAbsent(sessionId, k -> new StringBuilder());
            buffer.setLength(0); // Clear existing
            buffer.append(storedOutput);
            log.debug("Initialized output buffer for session {} with {} chars", sessionId, storedOutput.length());
        }
    }

    /**
     * Clear session resources
     *
     * @param sessionId the session ID
     */
    public void cleanup(Long sessionId) {
        // Save output before cleanup
        persistOutput(sessionId);
        stopProcess(sessionId);
        sessionOutputs.remove(sessionId);
        lastPersistTime.remove(sessionId);
    }

    /**
     * Persist output with debounce to avoid too frequent writes
     */
    private void persistOutputDebounced(Long sessionId) {
        long now = System.currentTimeMillis();
        Long last = lastPersistTime.get(sessionId);
        if (last == null || (now - last) > PERSIST_INTERVAL_MS) {
            persistOutput(sessionId);
            lastPersistTime.put(sessionId, now);
        }
    }

    /**
     * Immediately persist output to storage
     */
    public void persistOutput(Long sessionId) {
        StringBuilder output = sessionOutputs.get(sessionId);
        if (output == null || output.length() == 0) {
            return;
        }

        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setOutput(output.toString());
            sessionRepository.save(session);
            log.debug("Persisted output for session {} ({} chars)", sessionId, output.length());
        });
    }

    /**
     * Read from an input stream and broadcast to WebSocket with character-level streaming
     */
    private void readStream(Long sessionId, InputStream inputStream, String streamName) {
        StringBuilder lineBuffer = new StringBuilder();
        char[] charBuffer = new char[BUFFER_SIZE];
        long lastFlushTime = System.currentTimeMillis();

        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            int charsRead;
            while ((charsRead = reader.read(charBuffer)) != -1) {
                String chunk = new String(charBuffer, 0, charsRead);
                lineBuffer.append(chunk);

                long now = System.currentTimeMillis();
                boolean shouldFlush =
                        lineBuffer.length() >= BUFFER_SIZE ||
                        (now - lastFlushTime) >= FLUSH_INTERVAL_MS ||
                        chunk.contains("\n");

                if (shouldFlush) {
                    boolean isComplete = chunk.contains("\n");
                    broadcastChunk(sessionId, streamName, lineBuffer.toString(), isComplete);

                    // Store to output buffer
                    StringBuilder output = sessionOutputs.get(sessionId);
                    if (output != null) {
                        output.append(lineBuffer);
                    }

                    lineBuffer.setLength(0);
                    lastFlushTime = now;

                    // Debounced persistence after each flush
                    persistOutputDebounced(sessionId);
                }
            }

            // Flush remaining content
            if (lineBuffer.length() > 0) {
                broadcastChunk(sessionId, streamName, lineBuffer.toString(), true);

                StringBuilder output = sessionOutputs.get(sessionId);
                if (output != null) {
                    output.append(lineBuffer);
                }
            }

        } catch (IOException e) {
            if (!e.getMessage().contains("Stream closed")) {
                log.debug("Stream read error for session {}: {}", sessionId, e.getMessage());
            }
        } finally {
            // Check if process has ended
            Process process = activeProcesses.get(sessionId);
            if (process != null && !process.isAlive()) {
                handleProcessEnd(sessionId, process.exitValue());
            }
        }
    }

    /**
     * Broadcast a chunk of output to WebSocket subscribers
     */
    private void broadcastChunk(Long sessionId, String stream, String content, boolean isComplete) {
        String role = "stdin".equals(stream) ? "user" : "assistant";

        Map<String, Object> payload = Map.of(
                "type", "chunk",
                "stream", stream,
                "role", role,
                "content", content,
                "isComplete", isComplete,
                "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/output", payload);
    }

    /**
     * Handle process termination
     */
    private void handleProcessEnd(Long sessionId, int exitCode) {
        log.info("Process ended for session {} with exit code {}", sessionId, exitCode);

        // Persist final output
        persistOutput(sessionId);

        // Broadcast final status
        String status = exitCode == 0 ? "COMPLETED" : "ERROR";
        broadcastStatus(sessionId, status);

        // Broadcast exit code
        Map<String, Object> payload = Map.of(
                "type", "exit",
                "exitCode", exitCode,
                "status", status
        );
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/status", payload);

        // Cleanup
        processInputs.remove(sessionId);
    }

    /**
     * Broadcast status update to WebSocket subscribers
     */
    private void broadcastStatus(Long sessionId, String status) {
        Map<String, Object> payload = Map.of(
                "type", "status",
                "status", status,
                "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/status", payload);
    }
}
