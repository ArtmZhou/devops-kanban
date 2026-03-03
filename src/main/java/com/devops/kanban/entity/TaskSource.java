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
public class TaskSource {
    private Long id;
    private Long projectId;
    private String name;
    private TaskSourceType type;
    private String config; // JSON config
    private boolean enabled;
    private Integer syncInterval; // seconds, 0 = real-time
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;

    public enum TaskSourceType {
        LOCAL, GITHUB, JIRA, CUSTOM
    }
}
