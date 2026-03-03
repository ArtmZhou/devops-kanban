package com.devops.kanban.repository.impl;

import com.devops.kanban.entity.Project;
import com.devops.kanban.repository.ProjectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class FileProjectRepository implements ProjectRepository {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final AtomicLong idGenerator = new AtomicLong(0);

    public FileProjectRepository(@Value("${app.storage.path:./data}") String storagePath) {
        this.dataDir = Paths.get(storagePath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        loadIdGenerator();
    }

    private Path getFilePath() {
        return dataDir.resolve("projects.json");
    }

    private List<Project> readAll() {
        try {
            File file = getFilePath().toFile();
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return mapper.readValue(file, new TypeReference<List<Project>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeAll(List<Project> projects) {
        try {
            Files.createDirectories(dataDir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(getFilePath().toFile(), projects);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write projects", e);
        }
    }

    private void loadIdGenerator() {
        List<Project> projects = readAll();
        long maxId = projects.stream()
                .mapToLong(p -> p.getId() != null ? p.getId() : 0)
                .max()
                .orElse(0);
        idGenerator.set(maxId);
    }

    @Override
    public List<Project> findAll() {
        return readAll();
    }

    @Override
    public Optional<Project> findById(Long id) {
        return readAll().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();
    }

    @Override
    public Project save(Project project) {
        List<Project> projects = new ArrayList<>(readAll());

        if (project.getId() == null) {
            project.setId(getNextId());
            project.setCreatedAt(LocalDateTime.now());
        }
        project.setUpdatedAt(LocalDateTime.now());

        projects.removeIf(p -> p.getId().equals(project.getId()));
        projects.add(project);

        writeAll(projects);
        return project;
    }

    @Override
    public void deleteById(Long id) {
        List<Project> projects = new ArrayList<>(readAll());
        projects.removeIf(p -> p.getId().equals(id));
        writeAll(projects);
    }

    @Override
    public Long getNextId() {
        return idGenerator.incrementAndGet();
    }
}
