package com.devops.kanban.service;

import com.devops.kanban.converter.EntityDTOConverter;
import com.devops.kanban.dto.PromptTemplateDTO;
import com.devops.kanban.entity.PromptTemplate;
import com.devops.kanban.entity.Task;
import com.devops.kanban.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j4.SlfArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing prompt templates.
 * Handles business logic for template CRUD operations and default template initialization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;
    private final EntityDTOConverter converter;

    /**
     * Default templates for each task phase.
     * These are used as fallback when no user-defined template exists.
     */
    private static final Map<String, String> DEFAULT_TEMPLATES = new HashMap<>();

    static {
        DEFAULT_TEMPLATES.put("TODO", "Analyze the task, prepare implementation plan, consider dependencies and risks. Identify required resources and stakeholders. Document requirements clearly.");
        DEFAULT_TEMPLATES.put("DESIGN", "Focus on architecture design and technical selection. Consider extensibility, maintainability, and design patterns. Document the design decisions and API contracts, and data models.");
        DEFAULT_TEMPLATES.put("DEVELOPMENT", "Implement the code following coding standards and best practices. Handle errors gracefully, add appropriate logging, and write clean and maintainable code with proper comments and variable names.");
        DEFAULT_TEMPLATES.put("TESTING", "Write comprehensive test cases covering positive, negative, and edge cases. Ensure good test coverage, validate all inputs and outputs. Use appropriate testing frameworks and mock external dependencies.");
        DEFAULT_TEMPLATES.put("RELEASE", "Prepare release notes, update deployment configurations, verify environment variables. Create rollback procedures, document the deployment steps clearly. Ensure backward compatibility.");
        DEFAULT_TEMPLATES.put("DONE", "Review all changes, update documentation if clean up temporary files, close connections, release resources. Create summary report with lessons learned and recommendations.");
        DEFAULT_TEMPLATES.put("BLOCKED", "Analyze the blocker, identify root cause, propose solutions. Document the issue and any workarounds if available.");
        DEFAULT_TEMPLATES.put("CANCELLED", "Review any partial work, document the reason for cancellation. Archive or work if it might be reused later.");
    }

    /**
     * Initialize default templates if they don't exist.
     * Called on application startup.
     */
    public void initializeDefaultTemplates() {
        List<PromptTemplate> existingTemplates = promptTemplateRepository.findAll();

        for (Task.TaskStatus phase : Task.TaskStatus.values()) {
            String phaseName = phase.name();
            if (!promptTemplateRepository.existsByPhase(phaseName)) {
                PromptTemplate template = new PromptTemplate();
                template.setPhase(phaseName);
                template.setInstruction(DEFAULT_TEMPLATES.getOrDefault(phaseName, "Complete this task."));
                template.setDefault(true);
                template.setCreatedAt(LocalDateTime.now());
                template.setUpdatedAt(LocalDateTime.now());
                promptTemplateRepository.save(template);
                log.info("Created default prompt template for phase: {}", phaseName);
            }
        }
        log.info("Initialized {} prompt templates", existingTemplates.size());
    }

    /**
     * Get all prompt templates.
     */
    public List<PromptTemplateDTO> findAll() {
        return promptTemplateRepository.findAll().stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get a prompt template by phase.
     */
    public PromptTemplateDTO findByPhase(String phase) {
        return promptTemplateRepository.findByPhase(phase)
                .map(converter::toDTO)
                .orElse(null);
    }

    /**
     * Get a prompt template by ID.
     */
    public PromptTemplateDTO findById(Long id) {
        return promptTemplateRepository.findById(id)
                .map(converter::toDTO)
                .orElse(null);
    }

    /**
     * Get the instruction for a task phase.
     * Returns custom template if exists, otherwise default instruction.
     */
    public String getInstructionForPhase(String status) {
        if (status == null) {
            return DEFAULT_TEMPLATES.get("TODO");
        }

        // First try to find custom template
        Optional<PromptTemplate> template = promptTemplateRepository.findByPhase(status);
        if (template.isPresent() && !template.get().isDefault()) {
            return template.get().getInstruction();
        }

        // Fall back to default templates
        return DEFAULT_TEMPLATES.getOrDefault(status, DEFAULT_TEMPLATES.get("TODO"));
    }

    /**
     * Update a prompt template.
     */
    public PromptTemplateDTO update(Long id, PromptTemplateDTO dto) {
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PromptTemplate not found with id: " + id));

        template.setPhase(dto.getPhase());
        template.setInstruction(dto.getInstruction());
        template.setDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        template.setUpdatedAt(LocalDateTime.now());

        template = promptTemplateRepository.save(template);
        return converter.toDTO(template);
    }

    /**
     * Reset a template to default values.
     */
    public PromptTemplateDTO resetToDefault(Long id) {
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PromptTemplate not found with id: " + id));

        String phase = template.getPhase();
        String defaultInstruction = DEFAULT_TEMPLATES.getOrDefault(phase, "Complete this task.");

        template.setInstruction(defaultInstruction);
        template.setDefault(true);
        template.setUpdatedAt(LocalDateTime.now());

        template = promptTemplateRepository.save(template);
        return converter.toDTO(template);
    }
}
