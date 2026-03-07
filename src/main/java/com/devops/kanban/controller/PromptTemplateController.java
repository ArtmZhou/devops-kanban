package com.devops.kanban.controller;

import com.devops.kanban.dto.ApiResponse;
import com.devops.kanban.dto.PromptTemplateDTO;
import com.devops.kanban.service.PromptTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prompt-templates")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.origins:http://localhost:5173,http://localhost:3000}")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromptTemplateDTO>>> getAllTemplates() {
        List<PromptTemplateDTO> templates = promptTemplateService.findAll();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @GetMapping("/phase/{phase}")
    public ResponseEntity<ApiResponse<PromptTemplateDTO>> getTemplateByPhase(@PathVariable String phase) {
        PromptTemplateDTO template = promptTemplateService.findByPhase(phase);
        if (template == null) {
            return ResponseEntity.ok(ApiResponse.error("Template not found for phase: " + phase));
        }
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PromptTemplateDTO>> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody PromptTemplateDTO dto) {
        PromptTemplateDTO updated = promptTemplateService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Template updated successfully", updated));
    }

    @PostMapping("/{id}/reset")
    public ResponseEntity<ApiResponse<PromptTemplateDTO>> resetTemplate(@PathVariable Long id) {
        try {
            PromptTemplateDTO reset = promptTemplateService.resetToDefault(id);
            return ResponseEntity.ok(ApiResponse.success("Template reset to default", reset));
        } catch (IllegalArgumentException e) {
            log.warn("Failed to reset template", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<List<PromptTemplateDTO>>> initializeDefaults() {
        promptTemplateService.initializeDefaultTemplates();
        List<PromptTemplateDTO> templates = promptTemplateService.findAll();
        return ResponseEntity.ok(ApiResponse.success("Default templates initialized", templates));
    }
}
