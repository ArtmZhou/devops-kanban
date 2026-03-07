package com.devops.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorktreeDTO {
    private String path;
    private String branch;
    private String headCommitHash;
    private String headCommitMessage;
    private LocalDateTime lastModified;
    private boolean isDirty;
    private Long taskId;
}
