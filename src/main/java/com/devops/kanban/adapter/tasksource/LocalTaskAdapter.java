package com.devops.kanban.adapter.tasksource;

import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.entity.TaskSource;
import com.devops.kanban.spi.TaskSourceAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Local task source adapter - tasks stored in local file system.
 * This is the default adapter for tasks created directly in the system.
 */
@Component
public class LocalTaskAdapter implements TaskSourceAdapter {

    @Override
    public TaskSource.TaskSourceType getType() {
        return TaskSource.TaskSourceType.LOCAL;
    }

    @Override
    public boolean validateConfig(String configJson) {
        // Local storage doesn't need config
        return true;
    }

    @Override
    public List<TaskDTO> fetchTasks(TaskSource source) {
        // Local tasks are already in the repository
        return new ArrayList<>();
    }

    @Override
    public Optional<TaskDTO> fetchTask(TaskSource source, String externalId) {
        // Local tasks are already in the repository
        return Optional.empty();
    }

    @Override
    public boolean testConnection(TaskSource source) {
        return true;
    }
}
