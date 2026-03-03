package com.devops.kanban.adapter.agent;

import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.entity.Agent;
import com.devops.kanban.entity.Execution;
import com.devops.kanban.spi.AgentAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * OpenAI Codex adapter - executes tasks using Codex CLI.
 */
@Component
public class CodexAdapter implements AgentAdapter {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Agent.AgentType getType() {
        return Agent.AgentType.CODEX;
    }

    @Override
    public boolean validateConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return true;
        }
        try {
            mapper.readTree(configJson);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String buildCommand(Agent agent, TaskDTO task, Path worktreePath) {
        String commandTemplate = agent.getCommand();
        if (commandTemplate == null || commandTemplate.isEmpty()) {
            commandTemplate = "codex \"{prompt}\"";
        }

        String prompt = buildPrompt(task);

        String command = commandTemplate
                .replace("{prompt}", escapeShell(prompt))
                .replace("{worktree}", worktreePath.toString())
                .replace("{taskId}", String.valueOf(task.getId()))
                .replace("{taskTitle}", escapeShell(task.getTitle()));

        return String.format("cd \"%s\" && %s", worktreePath, command);
    }

    @Override
    public ExecutionResult parseResult(int exitCode, String output) {
        if (exitCode == 0) {
            return new ExecutionResult(true, "Codex execution completed", output);
        } else {
            return new ExecutionResult(false, "Codex execution failed", output);
        }
    }

    private String buildPrompt(TaskDTO task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(task.getTitle());

        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            prompt.append("\n\n").append(task.getDescription());
        }

        return prompt.toString();
    }

    private String escapeShell(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`");
    }
}
