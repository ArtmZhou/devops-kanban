package com.devops.kanban.controller;

import com.devops.kanban.dto.ApiResponse;
import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.origins:http://localhost:5173}")
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskDTO>>> getTasks(
            @RequestParam(required = false) Long projectId) {
        if (projectId == null) {
            return ResponseEntity.ok(ApiResponse.error("projectId is required"));
        }
        List<TaskDTO> tasks = taskService.findByProjectId(projectId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDTO>> getTask(@PathVariable Long id) {
        TaskDTO task = taskService.findById(id);
        if (task == null) {
            return ResponseEntity.ok(ApiResponse.error("Task not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskDTO>> createTask(@Valid @RequestBody TaskDTO dto) {
        TaskDTO created = taskService.create(dto);
        return ResponseEntity.ok(ApiResponse.success("Task created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskDTO>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskDTO dto) {
        TaskDTO updated = taskService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Task updated successfully", updated));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskDTO>> updateTaskStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) {
            return ResponseEntity.ok(ApiResponse.error("status is required"));
        }
        TaskDTO updated = taskService.updateStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Task status updated", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Task deleted successfully", null));
    }
}
