package com.devops.kanban.controller;

import com.devops.kanban.dto.ApiResponse;
import com.devops.kanban.entity.Project;
import com.devops.kanban.repository.ProjectRepository;
import com.devops.kanban.service.GitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/git")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.origins:http://localhost:5173}")
public class GitController {

    private final GitService gitService;
    private final ProjectRepository projectRepository;

    @GetMapping("/diff")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDiff(
            @RequestParam Long projectId,
            @RequestParam String source,
            @RequestParam String target) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            Path repoPath = getRepoPath(project);
            if (!gitService.isGitRepository(repoPath)) {
                return ResponseEntity.ok(ApiResponse.error("Not a valid Git repository"));
            }

            String diff = gitService.getDiff(repoPath, source, target);
            var diffEntries = gitService.getDiffEntries(repoPath, source, target);

            Map<String, Object> result = new HashMap<>();
            result.put("content", diff);
            result.put("filesChanged", diffEntries.size());
            result.put("source", source);
            result.put("target", target);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Failed to get diff: " + e.getMessage()));
        }
    }

    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<List<String>>> getBranches(@RequestParam Long projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            Path repoPath = getRepoPath(project);
            if (!gitService.isGitRepository(repoPath)) {
                return ResponseEntity.ok(ApiResponse.error("Not a valid Git repository"));
            }

            List<String> branches = gitService.listBranches(repoPath);
            return ResponseEntity.ok(ApiResponse.success(branches));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Failed to list branches: " + e.getMessage()));
        }
    }

    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<String>> mergeBranch(
            @RequestParam Long projectId,
            @RequestParam String source,
            @RequestParam String target) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            Path repoPath = getRepoPath(project);
            if (!gitService.isGitRepository(repoPath)) {
                return ResponseEntity.ok(ApiResponse.error("Not a valid Git repository"));
            }

            String result = gitService.mergeBranch(repoPath, source, target);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Failed to merge: " + e.getMessage()));
        }
    }

    @GetMapping("/worktrees")
    public ResponseEntity<ApiResponse<List<String>>> listWorktrees(@RequestParam Long projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));

            Path repoPath = getRepoPath(project);
            if (!gitService.isGitRepository(repoPath)) {
                return ResponseEntity.ok(ApiResponse.error("Not a valid Git repository"));
            }

            List<String> worktrees = gitService.listWorktrees(repoPath);
            return ResponseEntity.ok(ApiResponse.success(worktrees));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Failed to list worktrees: " + e.getMessage()));
        }
    }

    private Path getRepoPath(Project project) {
        if (project.getLocalPath() != null && !project.getLocalPath().isEmpty()) {
            return Paths.get(project.getLocalPath());
        }
        return Paths.get(".");
    }
}
