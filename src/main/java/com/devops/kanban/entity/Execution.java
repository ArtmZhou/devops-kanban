package com.devops.kanban.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Execution {
    private Long id;
    private Long taskId;
    private Long agentId;
    private ExecutionStatus status;
    private String worktreePath;
    private String branch;
    private String output;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public enum ExecutionStatus {
        PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
    }
}
