package com.devops.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitStatusDTO {
    private String branch;
    private List<FileStatus> added;
    private List<FileStatus> modified;
    private List<FileStatus> deleted;
    private List<FileStatus> untracked;
    private boolean hasUncommittedChanges;
    private int aheadCount;
    private int behindCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FileStatus {
        private String path;
        private String oldPath; // For renames
        private String status; // ADDED, MODIFIED, DELETED, RENAMED, UNTRACKED
    }
}
