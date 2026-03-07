package com.devops.kanban.repository;

import com.devops.kanban.entity.PromptTemplate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PromptTemplate entities.
 */
public interface PromptTemplateRepository {
    /**
     * Find all prompt templates.
     */
    List<PromptTemplate> findAll();

    /**
     * Find a prompt template by ID.
     */
    Optional<PromptTemplate> findById(Long id);

    /**
     * Find a prompt template by phase.
     */
    Optional<PromptTemplate> findByPhase(String phase);

    /**
     * Save a prompt template.
     */
    PromptTemplate save(PromptTemplate template);

    /**
     * Delete a prompt template by ID.
     */
    void deleteById(Long id);

    /**
     * Get the next available ID.
     */
    Long getNextId();

    /**
     * Check if templates exist for all phases.
     */
    boolean existsByPhase(String phase);
}
