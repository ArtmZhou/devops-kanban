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
public class Task {
    private Long id;
    private Long projectId;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private String assignee;

    // External task source fields
    private Long sourceId;
    private String externalId;
    private LocalDateTime syncedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum TaskStatus {
        TODO, IN_PROGRESS, IN_REVIEW, DONE, BLOCKED
    }

    public enum TaskPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
