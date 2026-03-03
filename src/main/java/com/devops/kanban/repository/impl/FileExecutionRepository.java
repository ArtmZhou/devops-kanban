package com.devops.kanban.repository.impl;

import com.devops.kanban.entity.Execution;
import com.devops.kanban.repository.ExecutionRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class FileExecutionRepository implements ExecutionRepository {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final AtomicLong idGenerator = new AtomicLong(0);

    public FileExecutionRepository(@Value("${app.storage.path:./data}") String storagePath) {
        this.dataDir = Paths.get(storagePath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        loadIdGenerator();
    }

    private Path getFilePath() {
        return dataDir.resolve("executions.json");
    }

    private List<Execution> readAll() {
        try {
            File file = getFilePath().toFile();
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return mapper.readValue(file, new TypeReference<List<Execution>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeAll(List<Execution> executions) {
        try {
            Files.createDirectories(dataDir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(getFilePath().toFile(), executions);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write executions", e);
        }
    }

    private void loadIdGenerator() {
        List<Execution> executions = readAll();
        long maxId = executions.stream()
                .mapToLong(e -> e.getId() != null ? e.getId() : 0)
                .max()
                .orElse(0);
        idGenerator.set(maxId);
    }

    @Override
    public List<Execution> findByTaskId(Long taskId) {
        return readAll().stream()
                .filter(e -> e.getTaskId().equals(taskId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Execution> findById(Long id) {
        return readAll().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst();
    }

    @Override
    public Execution save(Execution execution) {
        List<Execution> executions = new ArrayList<>(readAll());

        if (execution.getId() == null) {
            execution.setId(getNextId());
        }

        executions.removeIf(e -> e.getId().equals(execution.getId()));
        executions.add(execution);

        writeAll(executions);
        return execution;
    }

    @Override
    public Long getNextId() {
        return idGenerator.incrementAndGet();
    }
}
