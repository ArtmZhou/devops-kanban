package com.devops.kanban.spi;

import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.entity.TaskSource;

import java.util.List;
import java.util.Optional;

/**
 * SPI interface for external task source adapters.
 * Implement this interface to add support for new task sources (GitHub, Jira, etc.)
 */
public interface TaskSourceAdapter {

    /**
     * Returns the type of this adapter
     */
    TaskSource.TaskSourceType getType();

    /**
     * Validates the configuration JSON
     * @param configJson JSON configuration string
     * @return true if configuration is valid
     */
    boolean validateConfig(String configJson);

    /**
     * Fetches all tasks from the external source
     * @param source the task source configuration
     * @return list of tasks
     */
    List<TaskDTO> fetchTasks(TaskSource source);

    /**
     * Fetches a single task by external ID
     * @param source the task source configuration
     * @param externalId the external task ID
     * @return the task if found
     */
    Optional<TaskDTO> fetchTask(TaskSource source, String externalId);

    /**
     * Syncs task changes back to external source (optional)
     * @param source the task source configuration
     * @param task the task to sync
     * @return true if sync was successful
     */
    default boolean syncBack(TaskSource source, TaskDTO task) {
        return false;
    }

    /**
     * Tests the connection to the external source
     * @param source the task source configuration
     * @return true if connection is successful
     */
    default boolean testConnection(TaskSource source) {
        return true;
    }
}
