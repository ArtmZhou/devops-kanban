package com.devops.kanban.controller;

import com.devops.kanban.dto.ApiResponse;
import com.devops.kanban.dto.TaskSourceDTO;
import com.devops.kanban.entity.TaskSource;
import com.devops.kanban.service.TaskSourceManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/task-sources")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.origins:http://localhost:5173}")
public class TaskSourceController {

    private final TaskSourceManager taskSourceManager;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskSourceDTO>>> getTaskSources(
            @RequestParam(required = false) Long projectId) {
        if (projectId == null) {
            return ResponseEntity.ok(ApiResponse.error("projectId is required"));
        }
        List<TaskSourceDTO> sources = taskSourceManager.getSourcesByProjectId(projectId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(sources));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskSourceDTO>> getTaskSource(@PathVariable Long id) {
        TaskSource source = taskSourceManager.getSourcesByProjectId(null).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (source == null) {
            return ResponseEntity.ok(ApiResponse.error("Task source not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(toDTO(source)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskSourceDTO>> createTaskSource(@Valid @RequestBody TaskSourceDTO dto) {
        TaskSource source = toEntity(dto);
        source = taskSourceManager.createSource(source);
        return ResponseEntity.ok(ApiResponse.success("Task source created successfully", toDTO(source)));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<ApiResponse<Integer>> syncTaskSource(@PathVariable Long id) {
        int syncedCount = taskSourceManager.syncTasks(id);
        return ResponseEntity.ok(ApiResponse.success(
                "Synced " + syncedCount + " tasks", syncedCount));
    }

    @GetMapping("/{id}/test")
    public ResponseEntity<ApiResponse<Boolean>> testTaskSource(@PathVariable Long id) {
        boolean result = taskSourceManager.testConnection(id);
        return ResponseEntity.ok(ApiResponse.success(
                result ? "Connection successful" : "Connection failed", result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTaskSource(@PathVariable Long id) {
        taskSourceManager.deleteSource(id);
        return ResponseEntity.ok(ApiResponse.success("Task source deleted successfully", null));
    }

    private TaskSourceDTO toDTO(TaskSource source) {
        return TaskSourceDTO.builder()
                .id(source.getId())
                .projectId(source.getProjectId())
                .name(source.getName())
                .type(source.getType() != null ? source.getType().name() : "LOCAL")
                .config(source.getConfig())
                .enabled(source.isEnabled())
                .syncInterval(source.getSyncInterval())
                .lastSyncAt(source.getLastSyncAt())
                .createdAt(source.getCreatedAt())
                .build();
    }

    private TaskSource toEntity(TaskSourceDTO dto) {
        return TaskSource.builder()
                .id(dto.getId())
                .projectId(dto.getProjectId())
                .name(dto.getName())
                .type(dto.getType() != null ? TaskSource.TaskSourceType.valueOf(dto.getType()) : TaskSource.TaskSourceType.LOCAL)
                .config(dto.getConfig())
                .enabled(dto.isEnabled())
                .syncInterval(dto.getSyncInterval())
                .build();
    }
}
