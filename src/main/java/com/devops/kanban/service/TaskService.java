package com.devops.kanban.service;

import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.entity.Task;
import com.devops.kanban.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    public List<TaskDTO> findByProjectId(Long projectId) {
        return taskRepository.findByProjectId(projectId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public TaskDTO findById(Long id) {
        return taskRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    public TaskDTO create(TaskDTO dto) {
        Task task = toEntity(dto);
        task = taskRepository.save(task);
        return toDTO(task);
    }

    public TaskDTO update(Long id, TaskDTO dto) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

        task.setTitle(dto.getTitle());
        task.setDescription(dto.getDescription());
        if (dto.getStatus() != null) {
            task.setStatus(Task.TaskStatus.valueOf(dto.getStatus()));
        }
        if (dto.getPriority() != null) {
            task.setPriority(Task.TaskPriority.valueOf(dto.getPriority()));
        }
        task.setAssignee(dto.getAssignee());

        task = taskRepository.save(task);
        return toDTO(task);
    }

    public TaskDTO updateStatus(Long id, String status) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

        task.setStatus(Task.TaskStatus.valueOf(status));
        task = taskRepository.save(task);
        return toDTO(task);
    }

    public void delete(Long id) {
        taskRepository.deleteById(id);
    }

    private TaskDTO toDTO(Task task) {
        return TaskDTO.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus() != null ? task.getStatus().name() : Task.TaskStatus.TODO.name())
                .priority(task.getPriority() != null ? task.getPriority().name() : Task.TaskPriority.MEDIUM.name())
                .assignee(task.getAssignee())
                .sourceId(task.getSourceId())
                .externalId(task.getExternalId())
                .syncedAt(task.getSyncedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private Task toEntity(TaskDTO dto) {
        return Task.builder()
                .id(dto.getId())
                .projectId(dto.getProjectId())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .status(dto.getStatus() != null ? Task.TaskStatus.valueOf(dto.getStatus()) : Task.TaskStatus.TODO)
                .priority(dto.getPriority() != null ? Task.TaskPriority.valueOf(dto.getPriority()) : Task.TaskPriority.MEDIUM)
                .assignee(dto.getAssignee())
                .sourceId(dto.getSourceId())
                .externalId(dto.getExternalId())
                .syncedAt(dto.getSyncedAt())
                .build();
    }
}
