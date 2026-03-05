package com.devops.kanban.service;

import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.entity.*;
import com.devops.kanban.repository.AgentRepository;
import com.devops.kanban.repository.ExecutionRepository;
import com.devops.kanban.repository.TaskRepository;
import com.devops.kanban.spi.AgentAdapter;
import com.devops.kanban.util.PlatformUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages AI agent execution for tasks.
 */
@Service
@Slf4j
public class AgentExecutionService {

    private final AgentRepository agentRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;
    private final GitService gitService;
    private final Map<Agent.AgentType, AgentAdapter> adapters;

    public AgentExecutionService(
            AgentRepository agentRepository,
            TaskRepository taskRepository,
            ExecutionRepository executionRepository,
            GitService gitService,
            List<AgentAdapter> adapterList) {
        this.agentRepository = agentRepository;
        this.taskRepository = taskRepository;
        this.executionRepository = executionRepository;
        this.gitService = gitService;
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(AgentAdapter::getType, Function.identity()));
    }

    /**
     * Start execution of a task with an agent
     */
    public Execution startExecution(Long taskId, Long agentId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (!agent.isEnabled()) {
            throw new IllegalStateException("Agent is disabled: " + agentId);
        }

        // Create worktree for isolation
        String branch = "task-" + taskId + "-" + System.currentTimeMillis();
        Path worktree = gitService.createWorktree(task.getProjectId(), branch);

        // Create execution record
        Execution execution = Execution.builder()
                .taskId(taskId)
                .agentId(agentId)
                .status(Execution.ExecutionStatus.PENDING)
                .worktreePath(worktree.toString())
                .branch(branch)
                .startedAt(LocalDateTime.now())
                .build();
        execution = executionRepository.save(execution);

        // Execute asynchronously
        executeAsync(execution, agent, toDTO(task), worktree);

        return execution;
    }

    /**
     * Stop a running execution
     */
    public void stopExecution(Long executionId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));

        if (execution.getStatus() == Execution.ExecutionStatus.RUNNING) {
            execution.setStatus(Execution.ExecutionStatus.CANCELLED);
            execution.setCompletedAt(LocalDateTime.now());
            executionRepository.save(execution);

            // Cleanup worktree
            gitService.removeWorktree(Path.of(execution.getWorktreePath()));
        }
    }

    /**
     * Get execution by ID
     */
    public Execution getExecution(Long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
    }

    /**
     * Get all executions for a task
     */
    public List<Execution> getExecutionsByTaskId(Long taskId) {
        return executionRepository.findByTaskId(taskId);
    }

    @Async
    protected void executeAsync(Execution execution, Agent agent, TaskDTO task, Path worktree) {
        try {
            AgentAdapter adapter = getAdapter(agent.getType());

            // Update status to running
            execution.setStatus(Execution.ExecutionStatus.RUNNING);
            executionRepository.save(execution);

            // Prepare worktree
            adapter.prepare(task, worktree);

            // Build command
            String command = adapter.buildCommand(agent, task, worktree);
            log.info("Executing command: {}", command);

            // Execute command with platform-appropriate shell
            List<String> shellCommand = new ArrayList<>();
            shellCommand.addAll(Arrays.asList(PlatformUtils.getShellPrefix()));
            shellCommand.add(command);
            ProcessBuilder pb = new ProcessBuilder(shellCommand);
            pb.directory(worktree.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            // Parse result
            AgentAdapter.ExecutionResult result = adapter.parseResult(exitCode, output.toString());

            execution.setOutput(output.toString());
            execution.setStatus(result.success() ? Execution.ExecutionStatus.SUCCESS : Execution.ExecutionStatus.FAILED);

            // Update task status on success
            if (result.success()) {
                Task taskEntity = taskRepository.findById(execution.getTaskId()).orElse(null);
                if (taskEntity != null) {
                    taskEntity.setStatus(Task.TaskStatus.DONE);
                    taskRepository.save(taskEntity);
                }
            }

        } catch (Exception e) {
            log.error("Execution failed", e);
            execution.setStatus(Execution.ExecutionStatus.FAILED);
            execution.setOutput("Error: " + e.getMessage());
        } finally {
            execution.setCompletedAt(LocalDateTime.now());
            executionRepository.save(execution);

            // Cleanup
            try {
                gitService.removeWorktree(worktree);
            } catch (Exception e) {
                log.warn("Failed to cleanup worktree", e);
            }
        }
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
}
