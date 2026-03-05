package com.devops.kanban.service;

import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.entity.*;
import com.devops.kanban.repository.AgentRepository;
import com.devops.kanban.repository.ProjectRepository;
import com.devops.kanban.repository.SessionRepository;
import com.devops.kanban.repository.TaskRepository;
import com.devops.kanban.spi.AgentAdapter;
import com.devops.kanban.util.PlatformUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages AI session lifecycle for tasks.
 */
@Service
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final TaskRepository taskRepository;
    private final AgentRepository agentRepository;
    private final ProjectRepository projectRepository;
    private final GitService gitService;
    private final ClaudeCodeExecutor claudeCodeExecutor;
    private final Map<Agent.AgentType, AgentAdapter> adapters;

    public SessionService(
            SessionRepository sessionRepository,
            TaskRepository taskRepository,
            AgentRepository agentRepository,
            ProjectRepository projectRepository,
            GitService gitService,
            ClaudeCodeExecutor claudeCodeExecutor,
            List<AgentAdapter> adapterList) {
        this.sessionRepository = sessionRepository;
        this.taskRepository = taskRepository;
        this.agentRepository = agentRepository;
        this.projectRepository = projectRepository;
        this.gitService = gitService;
        this.claudeCodeExecutor = claudeCodeExecutor;
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(AgentAdapter::getType, Function.identity()));
    }

    /**
     * Create a new session for a task
     *
     * @param taskId  the task ID
     * @param agentId the agent ID
     * @return the created session
     */
    public Session createSession(Long taskId, Long agentId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        log.info("[Session] Creating session | TaskId: {} | AgentId: {} | ProjectId: {} | Task: '{}'",
            taskId, agentId, task.getProjectId(), task.getTitle());

        if (!agent.isEnabled()) {
            log.warn("[Session] Agent is disabled | AgentId: {} | AgentName: {}", agentId, agent.getName());
            throw new IllegalStateException("Agent is disabled: " + agentId);
        }

        // Check for existing active session
        Optional<Session> existingSession = sessionRepository.findActiveByTaskId(taskId);
        if (existingSession.isPresent()) {
            log.warn("[Session] Task already has active session | TaskId: {} | ExistingSessionId: {}",
                taskId, existingSession.get().getId());
            throw new IllegalStateException("Task already has an active session: " + existingSession.get().getId());
        }

        // Get project's local path for worktree creation
        Project project = projectRepository.findById(task.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + task.getProjectId()));

        if (project.getLocalPath() == null || project.getLocalPath().isBlank()) {
            log.error("[Session] Project missing localPath | ProjectId: {} | ProjectName: {}",
                task.getProjectId(), project.getName());
            throw new IllegalStateException(
                "Project '" + project.getName() + "' does not have a local path configured. " +
                "Please set the local repository path in project settings.");
        }
        Path localPath = Paths.get(project.getLocalPath());

        // Create worktree for isolation
        String branch = "session-" + taskId + "-" + System.currentTimeMillis();
        log.debug("[Session] Creating worktree | LocalPath: {} | Branch: {}", localPath, branch);
        Path worktree = gitService.createWorktree(localPath, task.getProjectId(), branch);

        // Create session record
        Session session = Session.builder()
                .taskId(taskId)
                .agentId(agentId)
                .status(Session.SessionStatus.CREATED)
                .worktreePath(worktree.toString())
                .branch(branch)
                .sessionId(UUID.randomUUID().toString())
                .startedAt(LocalDateTime.now())
                .build();

        session = sessionRepository.save(session);
        log.info("[Session-{}] Created successfully | Worktree: {} | Branch: {} | UUID: {} | AgentType: {}",
            session.getId(), session.getWorktreePath(), session.getBranch(), session.getSessionId(), agent.getType());

        return session;
    }

    /**
     * Start a session's agent process
     *
     * @param sessionId the session ID
     * @return the updated session
     */
    public Session startSession(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        log.info("[Session-{}] Starting session | CurrentStatus: {} | TaskId: {} | AgentId: {}",
            sessionId, session.getStatus(), session.getTaskId(), session.getAgentId());

        if (session.getStatus() != Session.SessionStatus.CREATED &&
            session.getStatus() != Session.SessionStatus.STOPPED) {
            log.warn("[Session-{}] Cannot start - invalid state: {}", sessionId, session.getStatus());
            throw new IllegalStateException("Session is not in a startable state: " + session.getStatus());
        }

        // Double-check: ensure no process is already running for this session
        if (claudeCodeExecutor.isAlive(sessionId)) {
            log.warn("[Session-{}] Cannot start - process already running", sessionId);
            throw new IllegalStateException("Session process is already running");
        }

        Long taskId = session.getTaskId();
        Long agentId = session.getAgentId();

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        AgentAdapter adapter = getAdapter(agent.getType());
        TaskDTO taskDTO = toDTO(task);

        try {
            // Prepare worktree
            log.debug("[Session-{}] Preparing worktree at: {}", sessionId, session.getWorktreePath());
            adapter.prepare(taskDTO, Paths.get(session.getWorktreePath()));

            // Get Claude CLI path from adapter
            String claudeCliPath = getClaudeCliPath(adapter);

            log.info("[Session-{}] Starting Claude Code Executor | CLI: {} | WorkDir: {}",
                sessionId, claudeCliPath, session.getWorktreePath());

            // Extract initial prompt from task
            String initialPrompt = buildInitialPrompt(taskDTO);

            // Save initial prompt to session for frontend filtering
            session.setInitialPrompt(initialPrompt);
            session = sessionRepository.save(session);

            log.info("[Session-{}] Starting fresh session (no Claude session ID yet)",
                sessionId);

            // Start Claude Code directly using ClaudeCodeExecutor
            // Pass null for claudeSessionId since this is the first run
            boolean started = claudeCodeExecutor.spawn(
                sessionId,
                claudeCliPath,
                Paths.get(session.getWorktreePath()),
                initialPrompt,
                null  // First run, no Claude session ID yet
            );

            if (!started) {
                throw new RuntimeException("Failed to spawn Claude Code process");
            }

            // Update session status
            session.setStatus(Session.SessionStatus.RUNNING);
            session.setLastHeartbeat(LocalDateTime.now());
            session = sessionRepository.save(session);

            log.info("[Session-{}] Session started successfully | Status: {} | Worktree: {}",
                sessionId, session.getStatus(), session.getWorktreePath());

            // Start heartbeat monitor
            startHeartbeatMonitor(sessionId);

        } catch (Exception e) {
            log.error("[Session-{}] Failed to start session | Error: {} | Worktree: {}",
                sessionId, e.getMessage(), session.getWorktreePath(), e);
            session.setStatus(Session.SessionStatus.ERROR);
            session.setStoppedAt(LocalDateTime.now());
            sessionRepository.save(session);
            throw new RuntimeException("Failed to start session", e);
        }

        return session;
    }

    /**
     * Get Claude CLI path from adapter or use default
     */
    private String getClaudeCliPath(AgentAdapter adapter) {
        if (adapter instanceof com.devops.kanban.adapter.agent.ClaudeCodeAdapter) {
            com.devops.kanban.adapter.agent.ClaudeCodeAdapter claudeAdapter =
                (com.devops.kanban.adapter.agent.ClaudeCodeAdapter) adapter;
            return claudeAdapter.getClaudeCliPath();
        }
        // Default paths using PlatformUtils
        if (PlatformUtils.isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return appData + "\\npm\\node_modules\\@anthropic-ai\\claude-code\\cli.js";
            } else {
                return PlatformUtils.getHomeDirectory() + "\\AppData\\Roaming\\npm\\node_modules\\@anthropic-ai\\claude-code\\cli.js";
            }
        } else {
            return "claude";
        }
    }

    /**
     * Build initial prompt from task
     */
    private String buildInitialPrompt(TaskDTO task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Task: ").append(task.getTitle()).append("\n\n");

        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            prompt.append("Description:\n").append(task.getDescription()).append("\n\n");
        }

        prompt.append("Please complete this task. Make the necessary changes and ensure the code works correctly.");

        return prompt.toString();
    }

    /**
     * Stop a running session
     *
     * @param sessionId the session ID
     * @return the updated session
     */
    public Session stopSession(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        log.info("[Session-{}] Stopping session | CurrentStatus: {} | TaskId: {}",
            sessionId, session.getStatus(), session.getTaskId());

        if (session.getStatus() != Session.SessionStatus.RUNNING &&
            session.getStatus() != Session.SessionStatus.IDLE) {
            log.warn("[Session-{}] Cannot stop - session not running: {}", sessionId, session.getStatus());
            throw new IllegalStateException("Session is not running: " + session.getStatus());
        }

        // Get final output from ClaudeCodeExecutor
        String finalOutput = claudeCodeExecutor.getOutput(sessionId);
        log.debug("[Session-{}] Final output length: {} chars", sessionId, finalOutput != null ? finalOutput.length() : 0);

        // Stop via ClaudeCodeExecutor
        claudeCodeExecutor.stop(sessionId);

        // Update session status and output
        session.setStatus(Session.SessionStatus.STOPPED);
        session.setStoppedAt(LocalDateTime.now());
        session.setOutput(finalOutput);
        session = sessionRepository.save(session);

        log.info("[Session-{}] Session stopped successfully | Status: {} | OutputLength: {}",
            sessionId, session.getStatus(), finalOutput != null ? finalOutput.length() : 0);
        return session;
    }

    /**
     * Send input to a running session
     *
     * @param sessionId the session ID
     * @param input     the input to send
     * @return true if input was sent successfully
     */
    public boolean sendInput(Long sessionId, String input) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // Check if process is still running
        if (claudeCodeExecutor.isAlive(sessionId)) {
            // Process is running, send input directly
            log.debug("[Session-{}] Sending input to running process: {}",
                sessionId, input.length() > 50 ? input.substring(0, 50) + "..." : input);
            return claudeCodeExecutor.sendInput(sessionId, input);
        }

        // Process has ended - check if we can resume
        if (session.getStatus() == Session.SessionStatus.STOPPED ||
            session.getStatus() == Session.SessionStatus.IDLE) {
            // Resume the session with --resume flag
            log.info("[Session-{}] Process ended, resuming with new input", sessionId);
            return resumeSession(session, input);
        }

        log.warn("[Session-{}] Cannot send input - session not running: {}", sessionId, session.getStatus());
        throw new IllegalStateException("Session is not accepting input: " + session.getStatus());
    }

    /**
     * Resume a stopped session with new input
     */
    private boolean resumeSession(Session session, String input) {
        Long sessionId = session.getId();
        Long agentId = session.getAgentId();

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        AgentAdapter adapter = getAdapter(agent.getType());
        String claudeCliPath = getClaudeCliPath(adapter);

        log.info("[Session-{}] Resuming session | CLI: {} | WorkDir: {} | ClaudeSessionId: {}",
            sessionId, claudeCliPath, session.getWorktreePath(), session.getClaudeSessionId());

        // Check if we have the Claude session ID for --resume
        String claudeSessionId = session.getClaudeSessionId();
        if (claudeSessionId == null || claudeSessionId.isEmpty()) {
            log.error("[Session-{}] No Claude session ID stored, cannot use --resume.", sessionId);
            throw new IllegalStateException(
                "Cannot resume session: Claude CLI session ID not found. " +
                "The session may have been created without running Claude CLI. " +
                "Please start a new session instead.");
        }

        try {
            // Prepare worktree
            adapter.prepare(null, Paths.get(session.getWorktreePath()));

            // Start Claude Code with --resume and the stored Claude session ID
            boolean started = claudeCodeExecutor.spawn(
                sessionId,
                claudeCliPath,
                Paths.get(session.getWorktreePath()),
                input,  // New input as initial prompt
                claudeSessionId  // Claude CLI's native session ID for --resume
            );

            if (!started) {
                throw new RuntimeException("Failed to resume Claude Code process");
            }

            // Update session status
            session.setStatus(Session.SessionStatus.RUNNING);
            session.setLastHeartbeat(LocalDateTime.now());
            sessionRepository.save(session);

            log.info("[Session-{}] Session resumed successfully", sessionId);

            // Start heartbeat monitor
            startHeartbeatMonitor(sessionId);

            return true;

        } catch (Exception e) {
            log.error("[Session-{}] Failed to resume session: {}", sessionId, e.getMessage(), e);
            session.setStatus(Session.SessionStatus.ERROR);
            session.setStoppedAt(LocalDateTime.now());
            sessionRepository.save(session);
            return false;
        }
    }

    /**
     * Continue a stopped session with new input
     * This is a public wrapper for resumeSession that can be called from the controller
     *
     * @param sessionId the session ID
     * @param input     the input to send (will trigger resume if session is stopped)
     * @return true if session was continued successfully
     */
    public boolean continueSession(Long sessionId, String input) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        log.info("[Session-{}] Continue session requested | CurrentStatus: {}", sessionId, session.getStatus());

        // Verify session is in a resumable state
        if (session.getStatus() != Session.SessionStatus.STOPPED &&
            session.getStatus() != Session.SessionStatus.IDLE) {
            log.warn("[Session-{}] Cannot continue - session not stopped or idle: {}", sessionId, session.getStatus());
            throw new IllegalStateException("Session can only be continued when STOPPED or IDLE, current status: " + session.getStatus());
        }

        // Delegate to resumeSession
        return resumeSession(session, input);
    }

    /**
     * Get session by ID
     */
    public Optional<Session> getSession(Long sessionId) {
        return sessionRepository.findById(sessionId);
    }

    /**
     * Get session by WebSocket session ID
     */
    public Optional<Session> getSessionBySessionId(String wsSessionId) {
        return sessionRepository.findBySessionId(wsSessionId);
    }

    /**
     * Get active session for a task
     */
    public Optional<Session> getActiveSessionByTaskId(Long taskId) {
        return sessionRepository.findActiveByTaskId(taskId);
    }

    /**
     * Get all sessions for a task
     */
    public List<Session> getSessionsByTaskId(Long taskId) {
        return sessionRepository.findByTaskId(taskId);
    }

    /**
     * Get all sessions for a task with output loaded
     */
    public List<Session> getSessionsWithOutputByTaskId(Long taskId) {
        List<Session> sessions = sessionRepository.findByTaskId(taskId);
        // Output is already stored in session entity, no need to load separately
        return sessions;
    }

    /**
     * Get session output
     */
    public String getSessionOutput(Long sessionId) {
        // First check in-memory output from ClaudeCodeExecutor
        String output = claudeCodeExecutor.getOutput(sessionId);
        if (output != null && !output.isEmpty()) {
            return output;
        }
        // Then check storage
        return sessionRepository.findById(sessionId)
                .map(session -> session.getOutput() != null ? session.getOutput() : "")
                .orElse("");
    }

    /**
     * Delete a session
     */
    public void deleteSession(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        log.info("[Session-{}] Deleting session | Status: {} | TaskId: {} | Worktree: {}",
            sessionId, session.getStatus(), session.getTaskId(), session.getWorktreePath());

        // Stop if running
        if (session.getStatus() == Session.SessionStatus.RUNNING ||
            session.getStatus() == Session.SessionStatus.IDLE) {
            log.debug("[Session-{}] Stopping running process before deletion", sessionId);
            claudeCodeExecutor.stop(sessionId);
        }

        // Cleanup worktree
        try {
            log.debug("[Session-{}] Removing worktree: {}", sessionId, session.getWorktreePath());
            gitService.removeWorktree(Paths.get(session.getWorktreePath()));
        } catch (Exception e) {
            log.warn("[Session-{}] Failed to cleanup worktree: {} | Error: {}",
                sessionId, session.getWorktreePath(), e.getMessage());
        }

        // Cleanup process resources
        claudeCodeExecutor.cleanup(sessionId);

        // Delete session
        sessionRepository.delete(sessionId);
        log.info("[Session-{}] Session deleted successfully", sessionId);
    }

    /**
     * Update session status based on process state
     */
    public void updateSessionStatus(Long sessionId, Session.SessionStatus status) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(status);
            session.setLastHeartbeat(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    private AgentAdapter getAdapter(Agent.AgentType type) {
        AgentAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter found for agent type: " + type);
        }
        return adapter;
    }

    private TaskDTO toDTO(Task task) {
        return TaskDTO.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus().name())
                .priority(task.getPriority().name())
                .assignee(task.getAssignee())
                .sourceId(task.getSourceId())
                .externalId(task.getExternalId())
                .syncedAt(task.getSyncedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private void startHeartbeatMonitor(Long sessionId) {
        log.debug("[Session-{}] Starting heartbeat monitor thread", sessionId);

        // Simple heartbeat - just update lastHeartbeat periodically
        Thread heartbeatThread = new Thread(() -> {
            Thread.currentThread().setName("session-" + sessionId + "-heartbeat");
            log.debug("[Session-{}] Heartbeat monitor started | Thread: {}",
                sessionId, Thread.currentThread().getName());

            // Monitor ClaudeCodeExecutor process
            while (claudeCodeExecutor.isAlive(sessionId)) {
                try {
                    Thread.sleep(5000); // 5 seconds
                    sessionRepository.findById(sessionId).ifPresent(session -> {
                        session.setLastHeartbeat(LocalDateTime.now());
                        sessionRepository.save(session);
                        log.trace("[Session-{}] Heartbeat updated", sessionId);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.debug("[Session-{}] Heartbeat monitor interrupted", sessionId);
                    break;
                }
            }

            // Process ended - update status
            int exitCode = claudeCodeExecutor.getExitCode(sessionId);
            log.info("[Session-{}] Heartbeat monitor: process ended | ExitCode: {}", sessionId, exitCode);

            sessionRepository.findById(sessionId).ifPresent(session -> {
                if (exitCode == 0) {
                    session.setStatus(Session.SessionStatus.STOPPED);
                } else {
                    session.setStatus(Session.SessionStatus.ERROR);
                }
                session.setStoppedAt(LocalDateTime.now());
                sessionRepository.save(session);
                log.info("[Session-{}] Final status updated | Status: {} | ExitCode: {}",
                    sessionId, session.getStatus(), exitCode);
            });
        }, "session-" + sessionId + "-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
}
