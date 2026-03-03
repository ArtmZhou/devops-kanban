package com.devops.kanban.repository.impl;

import com.devops.kanban.entity.Task;
import com.devops.kanban.repository.TaskRepository;
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
public class FileTaskRepository implements TaskRepository {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final AtomicLong idGenerator = new AtomicLong(0);

    public FileTaskRepository(@Value("${app.storage.path:./data}") String storagePath) {
        this.dataDir = Paths.get(storagePath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        loadIdGenerator();
    }

    private Path getFilePath(Long projectId) {
        return dataDir.resolve("tasks_" + projectId + ".json");
    }

    private Path getAllTasksFilePath() {
        return dataDir.resolve("all_tasks.json");
    }

    private List<Task> readByProjectId(Long projectId) {
        try {
            File file = getFilePath(projectId).toFile();
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return mapper.readValue(file, new TypeReference<List<Task>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private List<Task> readAllTasks() {
        try {
            File file = getAllTasksFilePath().toFile();
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return mapper.readValue(file, new TypeReference<List<Task>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeByProjectId(Long projectId, List<Task> tasks) {
        try {
            Files.createDirectories(dataDir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(getFilePath(projectId).toFile(), tasks);

            // Also update all_tasks.json
            List<Task> allTasks = new ArrayList<>();
            Files.list(dataDir)
                    .filter(p -> p.getFileName().toString().startsWith("tasks_"))
                    .forEach(p -> {
                        try {
                            List<Task> projectTasks = mapper.readValue(p.toFile(), new TypeReference<List<Task>>() {});
                            allTasks.addAll(projectTasks);
                        } catch (IOException ignored) {}
                    });
            mapper.writerWithDefaultPrettyPrinter().writeValue(getAllTasksFilePath().toFile(), allTasks);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write tasks", e);
        }
    }

    private void loadIdGenerator() {
        long maxId = 0;
        try {
            if (Files.exists(dataDir)) {
                for (File file : dataDir.toFile().listFiles((dir, name) -> name.startsWith("tasks_"))) {
                    List<Task> tasks = mapper.readValue(file, new TypeReference<List<Task>>() {});
                    maxId = Math.max(maxId, tasks.stream()
                            .mapToLong(t -> t.getId() != null ? t.getId() : 0)
                            .max()
                            .orElse(0));
                }
            }
        } catch (IOException ignored) {}
        idGenerator.set(maxId);
    }

    @Override
    public List<Task> findByProjectId(Long projectId) {
        return readByProjectId(projectId);
    }

    @Override
    public Optional<Task> findById(Long id) {
        return readAllTasks().stream()
                .filter(t -> t.getId().equals(id))
                .findFirst();
    }

    @Override
    public Task save(Task task) {
        Long projectId = task.getProjectId();
        List<Task> tasks = new ArrayList<>(readByProjectId(projectId));

        if (task.getId() == null) {
            task.setId(getNextId());
            task.setCreatedAt(LocalDateTime.now());
        }
        task.setUpdatedAt(LocalDateTime.now());

        tasks.removeIf(t -> t.getId().equals(task.getId()));
        tasks.add(task);

        writeByProjectId(projectId, tasks);
        return task;
    }

    @Override
    public void deleteById(Long id) {
        Task task = findById(id).orElse(null);
        if (task != null) {
            List<Task> tasks = new ArrayList<>(readByProjectId(task.getProjectId()));
            tasks.removeIf(t -> t.getId().equals(id));
            writeByProjectId(task.getProjectId(), tasks);
        }
    }

    @Override
    public Long getNextId() {
        return idGenerator.incrementAndGet();
    }
}
