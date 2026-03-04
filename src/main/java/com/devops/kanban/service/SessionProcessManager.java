package com.devops.kanban.service;

import com.devops.kanban.entity.Session;
import com.devops.kanban.repository.SessionRepository;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages AI agent processes for interactive sessions.
 * Handles process lifecycle, I/O streams, and real-time output broadcasting.
 * Uses PTY (pseudo-terminal) for proper TTY support required by interactive CLI tools.
 */
@Service
@Slf4j
public class SessionProcessManager {

    private static final long PERSIST_INTERVAL_MS = 5000; // 5 seconds debounce
    private static final int BUFFER_SIZE = 4096;  // Buffer size for PTY output
    private static final long FLUSH_INTERVAL_MS = 80; // 80ms time window for flushing chunks

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRepository sessionRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Active PTY process management
    private final ConcurrentHashMap<Long, PtyProcess> activeProcesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StringBuilder> sessionOutputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> lastPersistTime = new ConcurrentHashMap<>();

    // Process tracking for enhanced logging
    private final ConcurrentHashMap<Long, Long> processPids = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> sessionStartTimes = new ConcurrentHashMap<>();

    // Cache stdin streams to prevent premature closure
    // IMPORTANT: Never close these streams - let the process terminate naturally
    private final ConcurrentHashMap<Long, OutputStream> sessionStdins = new ConcurrentHashMap<>();

    public SessionProcessManager(SimpMessagingTemplate messagingTemplate, SessionRepository sessionRepository) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Start a new PTY process for a session
     *
     * @param sessionId  the session ID
     * @param command    the command array to execute
     * @param workingDir the working directory
     * @return the started PTY process
     */
    public PtyProcess startProcess(Long sessionId, String[] command, Path workingDir) {
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");

            // Build environment variables
            Map<String, String> env = new HashMap<>(System.getenv());

            // Remove CLAUDE-related environment variables to allow nested Claude Code sessions
            // The parent Claude Code sets these vars which prevents child sessions from running
            // IMPORTANT: Only remove CLAUDE* vars, NOT ANTHROPIC* vars (API credentials needed by Claude CLI)
            int removedCount = 0;
            java.util.List<String> removedKeys = new java.util.ArrayList<>();
            for (String key : new java.util.HashSet<>(env.keySet())) {
                String upperKey = key.toUpperCase();
                if (upperKey.startsWith("CLAUDE")) {  // Only remove CLAUDE*, preserve ANTHROPIC* API credentials
                    removedKeys.add(key + "=" + env.get(key));
                    env.remove(key);
                    removedCount++;
                }
            }
            log.info("[Session-{}] Removed {} CLAUDE env vars: {}", sessionId, removedCount, removedKeys);

            // Use dumb terminal to disable color output - prevents ANSI escape codes
            // NO_COLOR=1 provides additional hint to tools that support it
            env.put("TERM", "dumb");
            env.put("NO_COLOR", "1");

            log.debug("[Session-{}] Environment cleaned | Removed CLAUDE* vars (preserved ANTHROPIC* API creds) | TERM=dumb | NO_COLOR=1", sessionId);

            // Build PTY process - use WinPTY instead of ConPTY
            // ConPTY on Windows 10 has a known EOF signal bug that closes streams prematurely
            // See: https://github.com/microsoft/terminal/discussions/15006
            PtyProcess process = new PtyProcessBuilder()
                    .setCommand(command)
                    .setDirectory(workingDir.toString())
                    .setEnvironment(env)
                    .setUseWinConPty(false)  // Use WinPTY to avoid ConPTY EOF bug
                    .start();

            // Store process reference
            activeProcesses.put(sessionId, process);

            // Cache stdin stream reference - NEVER close this until process terminates
            // This prevents the Ink framework from detecting EOF and exiting prematurely
            OutputStream stdin = process.getOutputStream();
            sessionStdins.put(sessionId, stdin);
            log.debug("[Session-{}] Cached stdin stream reference", sessionId);

            // Store output buffer for this session
            sessionOutputs.put(sessionId, new StringBuilder());

            // Track process start time
            sessionStartTimes.put(sessionId, System.currentTimeMillis());

            // PtyProcess doesn't expose PID directly on all platforms
            // We use -1 as placeholder since PID is only for logging
            long pid = -1;
            processPids.put(sessionId, pid);

            // Enhanced logging with thread info
            String threadName = Thread.currentThread().getName();
            long threadId = Thread.currentThread().getId();
            log.info("[Session-{}] PTY process started | PID: {} | Thread: {}({}) | Command: {} | WorkingDir: {}",
                sessionId, pid, threadName, threadId, String.join(" ", command), workingDir);

            // Start PTY output reader thread with identifiable name
            executor.submit(() -> {
                Thread.currentThread().setName("session-" + sessionId + "-pty-reader");
                readPtyStream(sessionId, process.getInputStream());
            });

            // Broadcast running status with PID so frontend can display it immediately
            broadcastStatus(sessionId, "RUNNING");

            // Heartbeat: Send newline after startup to wake up interactive CLI (e.g., Ink framework)
            // This solves the issue where the CLI outputs welcome message then enters raw mode,
            // causing stdout to appear "stuck" because it's waiting for user input.
            executor.submit(() -> {
                try {
                    // Wait for CLI to finish initialization (welcome screen output)
                    Thread.sleep(500);

                    // Check if process is still alive before sending heartbeat
                    if (process.isAlive()) {
                        // Use cached stdin stream - NEVER close this!
                        OutputStream heartbeatStdin = sessionStdins.get(sessionId);
                        if (heartbeatStdin != null) {
                            // Send empty newline to wake up the interactive CLI's input loop
                            heartbeatStdin.write('\n');
                            heartbeatStdin.flush();
                            log.info("[Session-{}] Sent heartbeat newline to wake up interactive CLI", sessionId);
                        } else {
                            log.warn("[Session-{}] No cached stdin stream for heartbeat", sessionId);
                        }
                    }
                } catch (Exception e) {
                    log.debug("[Session-{}] Heartbeat failed: {}", sessionId, e.getMessage());
                }
            });

            return process;

        } catch (IOException e) {
            log.error("[Session-{}] Failed to start PTY process | Command: {} | Error: {}",
                sessionId, String.join(" ", command), e.getMessage(), e);
            throw new RuntimeException("Failed to start PTY process", e);
        }
    }

    /**
     * Stop a running PTY process
     * PtyProcess.destroy() sends SIGTERM on Unix, we then wait for termination.
     *
     * @param sessionId the session ID
     */
    public void stopProcess(Long sessionId) {
        // Persist output before stopping
        persistOutput(sessionId);

        PtyProcess process = activeProcesses.remove(sessionId);
        Long pid = processPids.get(sessionId);
        Long startTime = sessionStartTimes.get(sessionId);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        if (process != null) {
            // Destroy the PTY process
            process.destroy();

            try {
                // Wait for process to terminate (with timeout)
                long deadline = System.currentTimeMillis() + 3000; // 3 second timeout
                while (process.isAlive() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100);
                }

                if (process.isAlive()) {
                    log.warn("[Session-{}][PID-{}] Process did not terminate gracefully after {}ms",
                        sessionId, pid, duration);
                }

                log.info("[Session-{}][PID-{}] PTY process stopped | Duration: {}ms",
                    sessionId, pid, duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Session-{}][PID-{}] Interrupted while stopping process | Duration: {}ms",
                    sessionId, pid, duration);
            }
        }

        // Cleanup tracking maps
        processPids.remove(sessionId);
        sessionStartTimes.remove(sessionId);

        // Broadcast stopped status
        broadcastStatus(sessionId, "STOPPED");
    }

    /**
     * Send input to a running PTY process
     * Sends raw text input + newline for interactive mode
     *
     * @param sessionId the session ID
     * @param input     the input to send
     * @return true if input was sent successfully
     */
    public boolean sendInput(Long sessionId, String input) {
        PtyProcess process = activeProcesses.get(sessionId);
        Long pid = processPids.get(sessionId);

        if (process == null || !process.isAlive()) {
            log.warn("[Session-{}][PID-{}] No active PTY process", sessionId, pid);
            return false;
        }

        try {
            // Use cached stdin stream - NEVER close this!
            OutputStream stdin = sessionStdins.get(sessionId);
            if (stdin == null) {
                log.warn("[Session-{}][PID-{}] No cached stdin stream", sessionId, pid);
                return false;
            }

            // Send raw text + newline for interactive mode
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
            stdin.flush();

            log.debug("[Session-{}][PID-{}] Sent input to PTY: {}", sessionId, pid, input);

            // Store input in output buffer for persistence (display format)
            StringBuilder output = sessionOutputs.get(sessionId);
            if (output != null) {
                output.append("> ").append(input).append("\n");
                // Persist immediately to ensure input is saved
                persistOutput(sessionId);
            }

            // Echo the input to output via WebSocket (display format)
            broadcastChunk(sessionId, "stdin", "> " + input + "\n", true);
            return true;

        } catch (IOException e) {
            log.error("[Session-{}][PID-{}] Failed to write to PTY stdin: {}", sessionId, pid, e.getMessage());
            return false;
        }
    }

    /**
     * Check if a PTY process is alive
     *
     * @param sessionId the session ID
     * @return true if process is running
     */
    public boolean isProcessAlive(Long sessionId) {
        PtyProcess process = activeProcesses.get(sessionId);
        boolean alive = process != null && process.isAlive();
        Long pid = processPids.get(sessionId);
        log.debug("[Session-{}][PID-{}] Process alive check: {}", sessionId, pid, alive);
        return alive;
    }

    /**
     * Get the exit code of a PTY process
     *
     * @param sessionId the session ID
     * @return the exit code, or -1 if process is still running or not found
     */
    public int getExitCode(Long sessionId) {
        PtyProcess process = activeProcesses.get(sessionId);
        Long pid = processPids.get(sessionId);
        if (process != null && !process.isAlive()) {
            int exitCode = process.exitValue();
            log.debug("[Session-{}][PID-{}] Exit code: {}", sessionId, pid, exitCode);
            return exitCode;
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
            log.debug("[Session-{}] Initialized output buffer with {} chars from storage", sessionId, storedOutput.length());
        }
    }

    /**
     * Clear session resources
     *
     * @param sessionId the session ID
     */
    public void cleanup(Long sessionId) {
        Long pid = processPids.get(sessionId);
        log.debug("[Session-{}][PID-{}] Cleaning up session resources", sessionId, pid);

        // Save output before cleanup
        persistOutput(sessionId);
        stopProcess(sessionId);
        sessionOutputs.remove(sessionId);
        lastPersistTime.remove(sessionId);
        processPids.remove(sessionId);
        sessionStartTimes.remove(sessionId);
        // Just remove reference, don't close - let process terminate naturally
        sessionStdins.remove(sessionId);
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

        Long pid = processPids.get(sessionId);
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setOutput(output.toString());
            sessionRepository.save(session);
            log.debug("[Session-{}][PID-{}] Persisted output ({} chars)", sessionId, pid, output.length());
        });
    }

    /**
     * Read from PTY input stream and broadcast to WebSocket
     * Parses stream-json format output and extracts text content
     */
    private void readPtyStream(Long sessionId, InputStream inputStream) {
        Long pid = processPids.get(sessionId);
        String threadName = Thread.currentThread().getName();
        log.info("[Session-{}][PID-{}] PTY reader thread started: {}", sessionId, pid, threadName);

        StringBuilder lineBuffer = new StringBuilder();
        byte[] byteBuffer = new byte[BUFFER_SIZE];
        long totalBytes = 0;

        try {
            int bytesRead;
            while ((bytesRead = inputStream.read(byteBuffer)) != -1) {
                String chunk = new String(byteBuffer, 0, bytesRead, StandardCharsets.UTF_8);
                lineBuffer.append(chunk);
                totalBytes += bytesRead;

                // 🔍 Diagnostic: Log raw output (first 2KB for debugging)
                if (totalBytes <= 2048) {
                    log.info("[Session-{}][RAW] {} bytes: {}", sessionId, bytesRead,
                        chunk.replace("\n", "\\n").replace("\r", "\\r"));
                }

                // Process complete lines (stream-json is line-based)
                int newlineIndex;
                while ((newlineIndex = lineBuffer.indexOf("\n")) >= 0) {
                    String line = lineBuffer.substring(0, newlineIndex);
                    lineBuffer.delete(0, newlineIndex + 1);

                    // Parse JSON line and extract content
                    String displayContent = parseStreamJsonLine(line);

                    if (!displayContent.isEmpty()) {
                        // Broadcast the parsed content
                        broadcastChunk(sessionId, "stdout", displayContent + "\n", true);

                        // Store to output buffer
                        StringBuilder output = sessionOutputs.get(sessionId);
                        if (output != null) {
                            output.append(displayContent).append("\n");
                        }
                    }
                }

                // Debounced persistence
                persistOutputDebounced(sessionId);
            }

            // Process any remaining content
            if (lineBuffer.length() > 0) {
                String displayContent = parseStreamJsonLine(lineBuffer.toString());
                if (!displayContent.isEmpty()) {
                    broadcastChunk(sessionId, "stdout", displayContent, true);

                    StringBuilder output = sessionOutputs.get(sessionId);
                    if (output != null) {
                        output.append(displayContent);
                    }
                }
                persistOutput(sessionId);
            }

        } catch (IOException e) {
            log.debug("[Session-{}][PID-{}] PTY stream ended: {}", sessionId, pid, e.getMessage());
        } finally {
            // 🔍 Log complete output summary for diagnostics
            StringBuilder output = sessionOutputs.get(sessionId);
            String outputSummary = output != null && output.length() > 0
                ? output.substring(0, Math.min(500, output.length())).replace("\n", "\\n").replace("\r", "\\r")
                : "(empty)";
            log.info("[Session-{}][PID-{}] PTY reader finished | Total bytes: {} | Output preview: {}",
                sessionId, pid, totalBytes, outputSummary);

            // 🔍 Check and log detailed process state
            PtyProcess process = activeProcesses.get(sessionId);
            if (process != null) {
                boolean alive = process.isAlive();
                int exitCode = alive ? -1 : process.exitValue();
                log.info("[Session-{}] Process state | isAlive: {} | ExitCode: {}", sessionId, alive, exitCode);

                if (!alive) {
                    handleProcessEnd(sessionId, exitCode);
                }
            } else {
                // Process was removed, likely stopped intentionally
                log.info("[Session-{}][PID-{}] Process already removed from active list", sessionId, pid);
            }
        }
    }

    /**
     * Broadcast a chunk of output to WebSocket subscribers
     */
    private void broadcastChunk(Long sessionId, String stream, String content, boolean isComplete) {
        String role = "stdin".equals(stream) ? "user" : "assistant";

        log.debug("[Session-{}] Broadcasting {} chars via WebSocket (stream={}, isComplete={})",
            sessionId, content.length(), stream, isComplete);

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
     * Handle PTY process termination
     */
    private void handleProcessEnd(Long sessionId, int exitCode) {
        Long pid = processPids.get(sessionId);
        Long startTime = sessionStartTimes.get(sessionId);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        log.info("[Session-{}][PID-{}] PTY process ended | ExitCode: {} | Duration: {}ms",
            sessionId, pid, exitCode, duration);

        // Persist final output
        persistOutput(sessionId);

        // Broadcast final status
        String status = exitCode == 0 ? "COMPLETED" : "ERROR";
        broadcastStatus(sessionId, status);

        // Broadcast exit code with duration info
        Map<String, Object> payload = Map.of(
                "type", "exit",
                "exitCode", exitCode,
                "status", status,
                "pid", pid != null ? pid : -1,
                "durationMs", duration,
                "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/status", payload);

        // Cleanup
        processPids.remove(sessionId);
        sessionStartTimes.remove(sessionId);
    }

    /**
     * Broadcast status update to WebSocket subscribers
     */
    private void broadcastStatus(Long sessionId, String status) {
        Long pid = processPids.get(sessionId);
        Long startTime = sessionStartTimes.get(sessionId);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        log.info("[Session-{}] Broadcasting status: {} | PID: {} | Duration: {}ms",
            sessionId, status, pid != null ? pid : -1, duration);

        Map<String, Object> payload = Map.of(
                "type", "status",
                "status", status,
                "sessionId", sessionId,
                "pid", pid != null ? pid : -1,
                "durationMs", duration,
                "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/status", payload);
    }

    /**
     * Parse output line - now handles raw text output from interactive mode
     * Simply strips ANSI codes and returns clean text
     */
    private String parseStreamJsonLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return "";
        }

        // Strip any ANSI codes and return clean text
        return stripAnsiCodes(line);
    }

    /**
     * Strip ANSI escape codes from a string for clean display
     * Handles comprehensive ANSI sequences: colors, cursor movement, screen clearing, etc.
     */
    private String stripAnsiCodes(String input) {
        if (input == null) return "";

        return input
                // CSI sequences - ESC [ ... final_byte (comprehensive pattern)
                // Covers: colors (SGR), cursor movement, erase, scrolling, etc.
                .replaceAll("\\x1B\\[[0-?]*[ -/]*[@-~]", "")
                // OSC sequences - ESC ] ... BEL or ESC ] ... ST
                // Covers: window title, clipboard, hyperlinks, etc.
                .replaceAll("\\x1B\\][^\\x07\\x1B]*(?:\\x07|\\x1B\\\\)", "")
                // Character set selection - ESC ( or ESC )
                .replaceAll("\\x1B[()][AB012]", "")
                // Save/restore cursor
                .replaceAll("\\x1B[78]", "")
                // Keypad mode
                .replaceAll("\\x1B[=>]", "")
                // PM and APC sequences (rarely used but can appear)
                .replaceAll("\\x1B\\^[.*?\\x1B\\\\", "")
                .replaceAll("\\x1B_[.*?\\x1B\\\\", "")
                // Remove remaining control characters except \n, \r, \t
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }
}
