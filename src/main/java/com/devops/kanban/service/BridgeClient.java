package com.devops.kanban.service;

import com.devops.kanban.config.BridgeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for communicating with the Node.js Claude Bridge.
 *
 * Provides HTTP API for session lifecycle management and
 * relays messages between Java WebSocket and Node.js bridge.
 */
@Service
@Slf4j
public class BridgeClient {

    private final BridgeConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Track active sessions managed by bridge
    private final ConcurrentHashMap<String, BridgeSession> activeBridgeSessions = new ConcurrentHashMap<>();

    public BridgeClient(BridgeConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getTimeout()))
                .version(HttpClient.Version.HTTP_1_1)  // Force HTTP/1.1 to avoid HTTP/2 issues
                .build();

        log.info("[BridgeClient] Initialized | Enabled: {} | URL: {}",
            config.isEnabled(), config.getBaseUrl());
    }

    /**
     * Check if bridge is enabled
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Start a new session via the bridge
     *
     * @param sessionId     the unique session ID
     * @param workDir       working directory for the session
     * @param initialPrompt optional initial prompt to send
     * @return BridgeSession info if successful
     */
    public BridgeSession startSession(String sessionId, String workDir, String initialPrompt) {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Bridge is not enabled");
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("sessionId", sessionId);
            body.put("workDir", workDir);
            if (initialPrompt != null && !initialPrompt.isEmpty()) {
                body.put("initialPrompt", initialPrompt);
            }

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/session/start"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofMillis(config.getTimeout()))
                    .build();

            log.info("[BridgeClient] Starting session via bridge | SessionId: {} | WorkDir: {}", sessionId, workDir);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());

                if (json.has("success") && json.get("success").asBoolean()) {
                    JsonNode data = json.get("data");
                    BridgeSession session = new BridgeSession(
                            sessionId,
                            data.has("pid") ? data.get("pid").asLong() : -1,
                            data.has("status") ? data.get("status").asText() : "RUNNING",
                            workDir
                    );

                    activeBridgeSessions.put(sessionId, session);
                    log.info("[BridgeClient] Session started successfully | SessionId: {} | PID: {}",
                        sessionId, session.getPid());

                    return session;
                } else {
                    String message = json.has("message") ? json.get("message").asText() : "Unknown error";
                    throw new RuntimeException("Bridge returned error: " + message);
                }
            } else {
                throw new RuntimeException("Bridge returned status: " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[BridgeClient] Failed to start session | SessionId: {} | Error: {}",
                sessionId, e.getMessage(), e);
            throw new RuntimeException("Failed to start bridge session", e);
        }
    }

    /**
     * Stop a session via the bridge
     *
     * @param sessionId the session ID to stop
     * @return true if successful
     */
    public boolean stopSession(String sessionId) {
        if (!config.isEnabled()) {
            return false;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/session/" + sessionId))
                    .DELETE()
                    .timeout(Duration.ofMillis(config.getTimeout()))
                    .build();

            log.info("[BridgeClient] Stopping session via bridge | SessionId: {}", sessionId);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            activeBridgeSessions.remove(sessionId);

            if (response.statusCode() == 200) {
                log.info("[BridgeClient] Session stopped successfully | SessionId: {}", sessionId);
                return true;
            } else {
                log.warn("[BridgeClient] Failed to stop session | SessionId: {} | Status: {}",
                    sessionId, response.statusCode());
                return false;
            }

        } catch (Exception e) {
            log.error("[BridgeClient] Failed to stop session | SessionId: {} | Error: {}",
                sessionId, e.getMessage(), e);
            activeBridgeSessions.remove(sessionId);
            return false;
        }
    }

    /**
     * Get session info from bridge
     *
     * @param sessionId the session ID
     * @return BridgeSession info or null if not found
     */
    public BridgeSession getSession(String sessionId) {
        if (!config.isEnabled()) {
            return activeBridgeSessions.get(sessionId);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/session/" + sessionId))
                    .GET()
                    .timeout(Duration.ofMillis(config.getTimeout()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());

                if (json.has("success") && json.get("success").asBoolean()) {
                    JsonNode data = json.get("data");
                    return new BridgeSession(
                            sessionId,
                            data.has("pid") ? data.get("pid").asLong() : -1,
                            data.has("status") ? data.get("status").asText() : "UNKNOWN",
                            data.has("workDir") ? data.get("workDir").asText() : null
                    );
                }
            }

            return null;

        } catch (Exception e) {
            log.debug("[BridgeClient] Failed to get session info | SessionId: {} | Error: {}",
                sessionId, e.getMessage());
            return activeBridgeSessions.get(sessionId);
        }
    }

    /**
     * Check if bridge is healthy
     *
     * @return true if bridge is responding
     */
    public boolean isHealthy() {
        if (!config.isEnabled()) {
            log.debug("[BridgeClient] Health check skipped - bridge disabled");
            return false;
        }

        try {
            String healthUrl = config.getBaseUrl() + "/health";
            log.debug("[BridgeClient] Checking health at: {}", healthUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .GET()
                    .timeout(Duration.ofMillis(5000))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean healthy = response.statusCode() == 200;
            log.info("[BridgeClient] Health check result: {} | Status: {} | Body: {}",
                healthy, response.statusCode(), response.body());
            return healthy;

        } catch (Exception e) {
            log.warn("[BridgeClient] Health check failed: {} | Type: {}", e.getMessage(), e.getClass().getSimpleName());
            log.debug("[BridgeClient] Health check stack trace: ", e);
            return false;
        }
    }

    /**
     * Get WebSocket URL for a session
     */
    public String getWebSocketUrl(String sessionId) {
        return config.getWsUrl() + "?session=" + sessionId;
    }

    /**
     * Track an active bridge session (for non-bridge mode fallback)
     */
    public void trackSession(String sessionId, BridgeSession session) {
        activeBridgeSessions.put(sessionId, session);
    }

    /**
     * Remove session tracking
     */
    public void removeSession(String sessionId) {
        activeBridgeSessions.remove(sessionId);
    }

    /**
     * Simple data class for bridge session info
     */
    public static class BridgeSession {
        private final String sessionId;
        private final long pid;
        private volatile String status;
        private final String workDir;

        public BridgeSession(String sessionId, long pid, String status, String workDir) {
            this.sessionId = sessionId;
            this.pid = pid;
            this.status = status;
            this.workDir = workDir;
        }

        public String getSessionId() { return sessionId; }
        public long getPid() { return pid; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getWorkDir() { return workDir; }
    }
}
