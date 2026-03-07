package com.devops.kanban.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

/**
 * Prompt template entity for task phase-specific prompts.
 * Each task phase (TODO, DESIGN, DEVELOPMENT, TESTING, RELEASE, DONE)
 * can have a customized prompt instruction.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptTemplate {
    private Long id;
    private String phase;  // Task phase: TODO, DESIGN, DEVELOPMENT, TESTING, RELEASE, DONE
    private String instruction;  // The prompt instruction content
    private boolean isDefault;  // Whether this is a system default template
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
