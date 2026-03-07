package com.devops.kanban.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitRequestDTO {
    private String message;
    private boolean addAll = true;
    private String authorName;
    private String authorEmail;
}
