package com.devops.kanban.repository.impl;

import com.devops.kanban.entity.TaskSource;
import com.devops.kanban.repository.TaskSourceRepository;
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
import java.util.stream.Collectors;

@Repository
public class FileTaskSourceRepository implements TaskSourceRepository {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final AtomicLong idGenerator = new AtomicLong(0);

    public FileTaskSourceRepository(@Value("${app.storage.path:./data}") String storagePath) {
        this.dataDir = Paths.get(storagePath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        loadIdGenerator();
    }

    private Path getFilePath() {
        return dataDir.resolve("task_sources.json");
    }

    private List<TaskSource> readAll() {
        try {
            File file = getFilePath().toFile();
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return mapper.readValue(file, new TypeReference<List<TaskSource>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeAll(List<TaskSource> sources) {
        try {
            Files.createDirectories(dataDir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(getFilePath().toFile(), sources);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write task sources", e);
        }
    }

    private void loadIdGenerator() {
        List<TaskSource> sources = readAll();
        long maxId = sources.stream()
                .mapToLong(s -> s.getId() != null ? s.getId() : 0)
                .max()
                .orElse(0);
        idGenerator.set(maxId);
    }

    @Override
    public List<TaskSource> findByProjectId(Long projectId) {
        return readAll().stream()
                .filter(s -> s.getProjectId().equals(projectId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TaskSource> findById(Long id) {
        return readAll().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst();
    }

    @Override
    public TaskSource save(TaskSource source) {
        List<TaskSource> sources = new ArrayList<>(readAll());

        if (source.getId() == null) {
            source.setId(getNextId());
            source.setCreatedAt(LocalDateTime.now());
        }

        sources.removeIf(s -> s.getId().equals(source.getId()));
        sources.add(source);

        writeAll(sources);
        return source;
    }

    @Override
    public void deleteById(Long id) {
        List<TaskSource> sources = new ArrayList<>(readAll());
        sources.removeIf(s -> s.getId().equals(id));
        writeAll(sources);
    }

    @Override
    public Long getNextId() {
        return idGenerator.incrementAndGet();
    }
}
