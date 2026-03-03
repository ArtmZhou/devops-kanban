package com.devops.kanban.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskSourceDTO {
    private Long id;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Type is required")
    private String type; // LOCAL, GITHUB, JIRA, CUSTOM

    private String config; // JSON config
    private boolean enabled;
    private Integer syncInterval; // seconds, 0 = real-time
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
}
