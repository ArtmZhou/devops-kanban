package com.devops.kanban.repository;

import com.devops.kanban.entity.Execution;
import java.util.List;
import java.util.Optional;

public interface ExecutionRepository {
    List<Execution> findByTaskId(Long taskId);
    Optional<Execution> findById(Long id);
    Execution save(Execution execution);
    Long getNextId();
}
