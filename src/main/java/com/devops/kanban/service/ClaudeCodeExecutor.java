package com.devops.kanban.service;

import com.devops.kanban.entity.Session;
import com.devops.kanban.repository.SessionRepository;
import com.devops.kanban.util.PlatformUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Executes Claude Code CLI using PTY.
 * Uses interactive mode with --dangerously-skip-permissions for non-interactive execution.
 */
@Service
@Slf4j
public class ClaudeCodeExecutor {

    private static final int BUFFER_SIZE = 4096;

    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRepository sessionRepository;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final ConcurrentHashMap<Long, PtyProcess> activeProcesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StringBuilder> sessionOutputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, OutputStream> sessionStdins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> sessionStartTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> sessionIdExtracted = new ConcurrentHashMap<>();

    public ClaudeCodeExecutor(SimpMessagingTemplate messagingTemplate, SessionRepository sessionRepository) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRepository = sessionRepository;
        log.info("[ClaudeCodeExecutor] Initialized with PTY support");
    }

    /**
     * Spawn Claude Code CLI process using PTY in interactive mode
     *
     * @param sessionId       the session ID
     * @param claudeCliPath   path to Claude CLI
     * @param worktreePath    working directory path
     * @param initialPrompt   initial prompt to send
     * @param claudeSessionId Claude CLI's native session ID for --resume (null for first run)
     * @return true if spawned successfully
     */
    public boolean spawn(Long sessionId, String claudeCliPath, Path worktreePath, String initialPrompt, String claudeSessionId) {
        boolean isResume = claudeSessionId != null && !claudeSessionId.isEmpty();
        log.info("[Session-{}] Spawning Claude Code (interactive mode) | CLI: {} | WorkDir: {} | isResume: {}",
            sessionId, claudeCliPath, worktreePath, isResume);

        try {
            // 1. Build command directly (no env wrapper needed - we control env via ProcessBuilder)
            // Variables not included in the env map will be automatically unset
            List<String> command = new ArrayList<>();

            if (claudeCliPath.endsWith(".js")) {
                command.add("node");
                command.add(claudeCliPath);
            } else {
                command.add(claudeCliPath);
            }

            // Use print mode (-p) for clean output
            // Process will exit after completion, use --resume for multi-turn conversation
            command.add("-p");
            command.add("--dangerously-skip-permissions");
            command.add("--output-format");
            command.add("json");

            // Add --resume with session ID if this is a continuation
            if (claudeSessionId != null && !claudeSessionId.isEmpty()) {
                command.add("--resume");
                command.add(claudeSessionId);
                log.info("[Session-{}] Using --resume with Claude session ID: {}", sessionId, claudeSessionId);
            }

            // Pass initial prompt as command argument (works in interactive mode too)
            // Frontend will filter this from the output to avoid duplication
            if (initialPrompt != null && !initialPrompt.isEmpty()) {
                command.add(initialPrompt);
                log.info("[Session-{}] Passing initial prompt as command argument ({} chars)", sessionId, initialPrompt.length());
            }

            // 2. Build environment - include necessary system variables
            // Do NOT copy all system env vars to avoid CLAUDEDE leakage
            // Variables not added here will be unset in the process environment
            Map<String, String> env = new HashMap<>();
            env.put("PATH", System.getenv("PATH"));

            // Platform-specific home and user variables
            if (PlatformUtils.isWindows()) {
                // Windows requires these system variables for proper operation
                putIfNotNull(env, "SYSTEMROOT", System.getenv("SYSTEMROOT"));
                putIfNotNull(env, "COMSPEC", System.getenv("COMSPEC"));
                putIfNotNull(env, "WINDIR", System.getenv("WINDIR"));
                putIfNotNull(env, "TEMP", System.getenv("TEMP"));
                putIfNotNull(env, "TMP", System.getenv("TMP"));
                putIfNotNull(env, "USERPROFILE", System.getenv("USERPROFILE"));
                putIfNotNull(env, "USERNAME", System.getenv("USERNAME"));
                putIfNotNull(env, "APPDATA", System.getenv("APPDATA"));
                putIfNotNull(env, "LOCALAPPDATA", System.getenv("LOCALAPPDATA"));
                putIfNotNull(env, "ProgramFiles", System.getenv("ProgramFiles"));
                putIfNotNull(env, "ProgramFiles(x86)", System.getenv("ProgramFiles(x86)"));
                // Also set HOME for compatibility with some tools (like Node.js)
                String userProfile = System.getenv("USERPROFILE");
                if (userProfile != null) {
                    env.put("HOME", userProfile);
                }
            } else {
                putIfNotNull(env, "HOME", System.getenv("HOME"));
                putIfNotNull(env, "USER", System.getenv("USER"));
                putIfNotNull(env, "TMPDIR", System.getenv("TMPDIR"));
            }

            putIfNotNull(env, "LANG", System.getenv("LANG"));
            env.put("TERM", "xterm-256color");

            // Add ANTHROPIC_API_KEY if available
            String apiKey = System.getenv("ANTHROPIC_API_KEY");
            if (apiKey != null && !apiKey.isEmpty()) {
                env.put("ANTHROPIC_API_KEY", apiKey);
            }

            log.info("[Session-{}] Using print mode with permission bypass (nested execution enabled)", sessionId);

            // 3. Start PTY process
            PtyProcess process = new PtyProcessBuilder()
                    .setCommand(command.toArray(new String[0]))
                    .setDirectory(worktreePath.toString())
                    .setEnvironment(env)
                    .start();

            activeProcesses.put(sessionId, process);
            sessionStartTimes.put(sessionId, System.currentTimeMillis());
            sessionOutputs.put(sessionId, new StringBuilder());

            OutputStream stdin = process.getOutputStream();
            sessionStdins.put(sessionId, stdin);

            // 5. Start output readers - read both stdout and stderr (PTY combines them)
            executor.submit(() -> {
                Thread.currentThread().setName("session-" + sessionId + "-stdout");
                readOutputStream(sessionId, process.getInputStream());
            });

            // 6. Wait for process exit
            executor.submit(() -> {
                Thread.currentThread().setName("session-" + sessionId + "-waiter");
                try {
                    long deadline = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2);
                    while (process.isAlive() && System.currentTimeMillis() < deadline) {
                        Thread.sleep(1000);
                    }
                    if (process.isAlive()) {
                        log.warn("[Session-{}] Process timeout after 2 hours", sessionId);
                    }
                    int exitCode = process.exitValue();
                    handleProcessExit(sessionId, exitCode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[Session-{}] Process wait interrupted", sessionId);
                }
            });

            // 7. Broadcast running status
            broadcastStatus(sessionId, "RUNNING");

            log.info("[Session-{}] Claude Code spawned successfully in print mode", sessionId);
            return true;

        } catch (IOException e) {
            log.error("[Session-{}] Failed to spawn Claude Code: {}", sessionId, e.getMessage(), e);
            return false;
        }
    }

    private void readOutputStream(Long sessionId, InputStream inputStream) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytes = 0;
        int emptyReadCount = 0;

        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                totalBytes += bytesRead;

                // Log raw chunk for debugging (first 10KB only)
                if (totalBytes <= 10240) {
                    log.info("[Session-{}] Raw chunk ({} bytes): {}", sessionId, bytesRead,
                        chunk.substring(0, Math.min(200, chunk.length())).replace("\n", "\\n").replace("\r", "\\r"));
                }

                // Try to extract Claude CLI session ID from the raw chunk (only once)
                // Claude CLI outputs session_id somewhere in the output
                if (!sessionIdExtracted.getOrDefault(sessionId, false)) {
                    String claudeSessionId = extractClaudeSessionId(chunk);
                    if (claudeSessionId != null) {
                        log.info("[Session-{}] Detected Claude CLI session ID: {}", sessionId, claudeSessionId);
                        sessionIdExtracted.put(sessionId, true);
                        // Save to session entity
                        final String finalClaudeSessionId = claudeSessionId;
                        sessionRepository.findById(sessionId).ifPresent(session -> {
                            session.setClaudeSessionId(finalClaudeSessionId);
                            sessionRepository.save(session);
                            log.info("[Session-{}] Saved Claude session ID to session entity", sessionId);
                        });
                    }
                }

                // Strip ANSI codes but keep printable content
                String cleanContent = stripAnsiCodes(chunk);

                // Always broadcast, even if empty after stripping (for debugging)
                emptyReadCount = 0;
                StringBuilder output = sessionOutputs.get(sessionId);
                if (output != null) {
                    output.append(cleanContent);
                }
                if (!cleanContent.isEmpty()) {
                    broadcastChunk(sessionId, "stdout", cleanContent, false);
                    log.debug("[Session-{}] Broadcast {} chars", sessionId, cleanContent.length());
                } else {
                    // Still log when we get content before ANSI stripping
                    log.debug("[Session-{}] Received {} bytes, but empty after ANSI strip", sessionId, bytesRead);
                }

                persistOutputDebounced(sessionId);
            }

            log.info("[Session-{}] Output stream ended | Total bytes read: {}", sessionId, totalBytes);

        } catch (IOException e) {
            log.debug("[Session-{}] Output read error: {}", sessionId, e.getMessage());
        } finally {
            PtyProcess process = activeProcesses.get(sessionId);
            if (process != null && !process.isAlive()) {
                handleProcessExit(sessionId, process.exitValue());
            }
        }
    }

    public boolean sendInput(Long sessionId, String input) {
        PtyProcess process = activeProcesses.get(sessionId);
        OutputStream stdin = sessionStdins.get(sessionId);

        if (process == null || stdin == null) {
            log.warn("[Session-{}] No active process or stdin", sessionId);
            return false;
        }

        if (!process.isAlive()) {
            log.warn("[Session-{}] Process is not alive", sessionId);
            return false;
        }

        try {
            stdin.write((input + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            log.info("[Session-{}] Sent input: {} chars", sessionId, input.length());
            return true;
        } catch (IOException e) {
            log.error("[Session-{}] Failed to send input: {}", sessionId, e.getMessage());
            return false;
        }
    }

    public void stop(Long sessionId) {
        persistOutput(sessionId);

        PtyProcess process = activeProcesses.remove(sessionId);
        Long startTime = sessionStartTimes.remove(sessionId);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        if (process != null) {
            process.destroy();
            try {
                long deadline = System.currentTimeMillis() + 3000;
                while (process.isAlive() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(100);
                }
                if (process.isAlive()) {
                    log.warn("[Session-{}] Force killing process", sessionId);
                    process.destroyForcibly();
                }
                log.info("[Session-{}] Process stopped | Duration: {}ms", sessionId, duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        sessionStdins.remove(sessionId);
        broadcastStatus(sessionId, "STOPPED");
    }

    public boolean isAlive(Long sessionId) {
        PtyProcess process = activeProcesses.get(sessionId);
        return process != null && process.isAlive();
    }

    public int getExitCode(Long sessionId) {
        PtyProcess process = activeProcesses.get(sessionId);
        if (process != null && !process.isAlive()) {
            return process.exitValue();
        }
        return -1;
    }

    public String getOutput(Long sessionId) {
        StringBuilder output = sessionOutputs.get(sessionId);
        return output != null ? output.toString() : "";
    }

    private void handleProcessExit(Long sessionId, int exitCode) {
        Long startTime = sessionStartTimes.get(sessionId);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

        persistOutput(sessionId);

        log.info("[Session-{}] Process exited | Code: {} | Duration: {}ms",
            sessionId, exitCode, duration);

        String status = exitCode == 0 ? "COMPLETED" : "ERROR";
        broadcastStatus(sessionId, status);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "exit");
        payload.put("exitCode", exitCode);
        payload.put("status", status);
        payload.put("durationMs", duration);
        payload.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/status", payload);

        sessionStartTimes.remove(sessionId);
        sessionStdins.remove(sessionId);
    }

    private void broadcastChunk(Long sessionId, String stream, String content, boolean isComplete) {
        String role = "stdin".equals(stream) ? "user" : "assistant";

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "chunk");
        payload.put("stream", stream);
        payload.put("role", role);
        payload.put("content", content);
        payload.put("isComplete", isComplete);
        payload.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/output", payload);

        log.debug("[Session-{}] Broadcast {} chars (stream={})", sessionId, content.length(), stream);
    }

    private void broadcastStatus(Long sessionId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "status");
        payload.put("status", status);
        payload.put("sessionId", sessionId);
        payload.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/status", payload);

        log.info("[Session-{}] Status: {}", sessionId, status);
    }

    private final ConcurrentHashMap<Long, Long> lastPersistTime = new ConcurrentHashMap<>();
    private static final long PERSIST_INTERVAL_MS = 5000;

    private void persistOutputDebounced(Long sessionId) {
        long now = System.currentTimeMillis();
        Long last = lastPersistTime.get(sessionId);
        if (last == null || (now - last) > PERSIST_INTERVAL_MS) {
            persistOutput(sessionId);
            lastPersistTime.put(sessionId, now);
        }
    }

    public void persistOutput(Long sessionId) {
        StringBuilder output = sessionOutputs.get(sessionId);
        if (output == null || output.length() == 0) {
            return;
        }

        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setOutput(output.toString());
            sessionRepository.save(session);
            log.debug("[Session-{}] Persisted output ({} chars)", sessionId, output.length());
        });
    }

    private String stripAnsiCodes(String input) {
        if (input == null) return "";
        // More comprehensive ANSI escape sequence removal
        return input
                // CSI sequences (most common ANSI codes)
                .replaceAll("\\x1B\\[[0-?]*[ -/]*[@-~]", "")
                // OSC sequences (operating system commands)
                .replaceAll("\\x1B\\][^\\x07\\x1B]*(?:\\x07|\\x1B\\\\)", "")
                // Character set selection
                .replaceAll("\\x1B[()][AB012]", "")
                // Reverse index and next line
                .replaceAll("\\x1B[78]", "")
                // Application mode
                .replaceAll("\\x1B[=>]", "")
                // Remove control characters but KEEP newlines (0x0A) and tabs (0x09)
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    // Pattern to extract Claude CLI session ID from JSON output
    // Claude CLI outputs JSON with session_id field when using --json flag
    private static final Pattern CLAUDE_SESSION_ID_PATTERN = Pattern.compile(
        "\"session_id\"\\s*:\\s*\"([a-zA-Z0-9_-]+)\""
    );

    /**
     * Extract Claude CLI session ID from output chunk.
     * When using --json flag, Claude CLI outputs JSON with session_id field.
     * Format: {"type":"...","session_id":"xxx",...}
     *
     * @param chunk the output chunk to parse
     * @return the session ID if found, null otherwise
     */
    private String extractClaudeSessionId(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }

        // Extract session_id from any JSON object that contains it
        Matcher matcher = CLAUDE_SESSION_ID_PATTERN.matcher(chunk);
        if (matcher.find()) {
            String sessionId = matcher.group(1);
            log.info("[ClaudeCodeExecutor] Extracted session_id from JSON: {}", sessionId);
            return sessionId;
        }
        return null;
    }

    public void cleanup(Long sessionId) {
        stop(sessionId);
        sessionOutputs.remove(sessionId);
        lastPersistTime.remove(sessionId);
        sessionIdExtracted.remove(sessionId);
        log.debug("[Session-{}] Resources cleaned up", sessionId);
    }

    /**
     * Put a value into the map only if the value is not null.
     */
    private void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }
}
