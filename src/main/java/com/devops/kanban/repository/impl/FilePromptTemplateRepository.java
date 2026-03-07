package com.devops.kanban.repository.impl;

import com.devops.kanban.entity.PromptTemplate;
import com.devops.kanban.repository.PromptTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * File-based repository implementation for PromptTemplate.
 * Stores templates in data/prompt_templates.json.
 */
@Repository
public class FilePromptTemplateRepository extends AbstractFileRepository<PromptTemplate, Long>
        implements PromptTemplateRepository {

    public FilePromptTemplateRepository(@Value("${app.storage.path:./data}") String storagePath) {
        super(storagePath, new TypeReference<List<PromptTemplate>>() {}, PromptTemplate::getId);
    }

    @Override
    protected Path getFilePath() {
        return dataDir.resolve("prompt_templates.json");
    }

    @Override
    public List<PromptTemplate> findAll() {
        return readAll();
    }

    @Override
    public Optional<PromptTemplate> findById(Long id) {
        return findByIdGeneric(id);
    }

    @Override
    public Optional<PromptTemplate> findByPhase(String phase) {
        return readAll().stream()
                .filter(t -> t.getPhase() != null && t.getPhase().equals(phase))
                .findFirst();
    }

    @Override
    public PromptTemplate save(PromptTemplate template) {
        return saveGeneric(template, (t, id) -> {
            if (t.getId() == null) {
                t.setId(id);
                t.setCreatedAt(LocalDateTime.now());
            }
            t.setUpdatedAt(LocalDateTime.now());
        });
    }

    @Override
    public void deleteById(Long id) {
        deleteByIdGeneric(id);
    }

    @Override
    public boolean existsByPhase(String phase) {
        return readAll().stream()
                .anyMatch(t -> t.getPhase() != null && t.getPhase().equals(phase));
    }
}
