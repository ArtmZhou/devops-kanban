package com.devops.kanban.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionDTO {
    private Long id;

    @NotNull(message = "Task ID is required")
    private Long taskId;

    @NotNull(message = "Agent ID is required")
    private Long agentId;

    private String status; // PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
    private String worktreePath;
    private String branch;
    private String output;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
