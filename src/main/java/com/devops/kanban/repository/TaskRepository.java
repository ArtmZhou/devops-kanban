package com.devops.kanban.repository;

import com.devops.kanban.entity.Task;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    List<Task> findByProjectId(Long projectId);
    Optional<Task> findById(Long id);
    Task save(Task task);
    void deleteById(Long id);
    Long getNextId();
}
