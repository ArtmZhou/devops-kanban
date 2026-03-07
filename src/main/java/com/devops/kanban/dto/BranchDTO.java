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
public class BranchDTO {
    private String name;
    private String fullName;
    private boolean isCurrent;
    private boolean isRemote;
    private String upstream;
    private int aheadCount;
    private int behindCount;
    private LocalDateTime lastCommit;
    private String lastCommitMessage;
}
