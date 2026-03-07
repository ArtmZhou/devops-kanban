package com.devops.kanban.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * DTO for PromptTemplate entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptTemplateDTO {
    private Long id;

    @NotBlank(message = "Phase is required")
    private String phase;

    @NotBlank(message = "Instruction is required")
    private String instruction;

    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
