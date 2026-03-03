package com.devops.kanban.repository.impl;

import com.devops.kanban.entity.Agent;
import com.devops.kanban.repository.AgentRepository;
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
public class FileAgentRepository implements AgentRepository {

    private final Path dataDir;
    private final ObjectMapper mapper;
    private final AtomicLong idGenerator = new AtomicLong(0);

    public FileAgentRepository(@Value("${app.storage.path:./data}") String storagePath) {
        this.dataDir = Paths.get(storagePath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        loadIdGenerator();
    }

    private Path getFilePath() {
        return dataDir.resolve("agents.json");
    }

    private List<Agent> readAll() {
        try {
            File file = getFilePath().toFile();
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return mapper.readValue(file, new TypeReference<List<Agent>>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeAll(List<Agent> agents) {
        try {
            Files.createDirectories(dataDir);
            mapper.writerWithDefaultPrettyPrinter().writeValue(getFilePath().toFile(), agents);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write agents", e);
        }
    }

    private void loadIdGenerator() {
        List<Agent> agents = readAll();
        long maxId = agents.stream()
                .mapToLong(a -> a.getId() != null ? a.getId() : 0)
                .max()
                .orElse(0);
        idGenerator.set(maxId);
    }

    @Override
    public List<Agent> findByProjectId(Long projectId) {
        return readAll().stream()
                .filter(a -> a.getProjectId().equals(projectId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Agent> findById(Long id) {
        return readAll().stream()
                .filter(a -> a.getId().equals(id))
                .findFirst();
    }

    @Override
    public Agent save(Agent agent) {
        List<Agent> agents = new ArrayList<>(readAll());

        if (agent.getId() == null) {
            agent.setId(getNextId());
            agent.setCreatedAt(LocalDateTime.now());
        }

        agents.removeIf(a -> a.getId().equals(agent.getId()));
        agents.add(agent);

        writeAll(agents);
        return agent;
    }

    @Override
    public void deleteById(Long id) {
        List<Agent> agents = new ArrayList<>(readAll());
        agents.removeIf(a -> a.getId().equals(id));
        writeAll(agents);
    }

    @Override
    public Long getNextId() {
        return idGenerator.incrementAndGet();
    }
}
