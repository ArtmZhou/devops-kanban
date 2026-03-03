package com.devops.kanban.repository;

import com.devops.kanban.entity.Project;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    List<Project> findAll();
    Optional<Project> findById(Long id);
    Project save(Project project);
    void deleteById(Long id);
    Long getNextId();
}
