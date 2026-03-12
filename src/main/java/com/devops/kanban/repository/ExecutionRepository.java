package com.devops.kanban.repository;

import com.devops.kanban.entity.Execution;
import java.util.List;
import java.util.Optional;

public interface ExecutionRepository {
    List<Execution> findByTaskId(Long taskId);
    List<Execution> findByTaskIds(List<Long> taskIds);
    List<Execution> findByAgentId(Long agentId);
    Optional<Execution> findById(Long id);
    Execution save(Execution execution);
    void deleteByTaskIds(List<Long> taskIds);
    Long getNextId();
}
