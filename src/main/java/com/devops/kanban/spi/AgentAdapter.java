package com.devops.kanban.spi;

import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.entity.Agent;
import com.devops.kanban.entity.Execution;

import java.nio.file.Path;

/**
 * SPI interface for AI Agent adapters.
 * Implement this interface to add support for new AI agents (Claude, Codex, Cursor, etc.)
 */
public interface AgentAdapter {

    /**
     * Returns the type of this adapter
     */
    Agent.AgentType getType();

    /**
     * Validates the configuration JSON
     * @param configJson JSON configuration string
     * @return true if configuration is valid
     */
    boolean validateConfig(String configJson);

    /**
     * Builds the command to execute the agent
     * @param agent the agent configuration
     * @param task the task to execute
     * @param worktreePath the isolated worktree path
     * @return the command string to execute
     */
    String buildCommand(Agent agent, TaskDTO task, Path worktreePath);

    /**
     * Prepares the worktree before execution
     * @param task the task to execute
     * @param worktreePath the worktree path
     */
    default void prepare(TaskDTO task, Path worktreePath) throws Exception {
        // Default: no preparation needed
    }

    /**
     * Cleans up after execution
     * @param execution the execution result
     */
    default void cleanup(Execution execution) {
        // Default: no cleanup needed
    }

    /**
     * Parses the execution result
     * @param exitCode the process exit code
     * @param output the process output
     * @return the execution result
     */
    ExecutionResult parseResult(int exitCode, String output);

    /**
     * Result of agent execution
     */
    record ExecutionResult(
            boolean success,
            String message,
            String summary
    ) {}
}
