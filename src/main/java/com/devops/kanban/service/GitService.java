package com.devops.kanban.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Service for managing Git worktrees for isolated task execution.
 */
@Service
@Slf4j
public class GitService {

    private final Path worktreesDir;

    public GitService(@Value("${app.storage.path:./data}") String storagePath) {
        this.worktreesDir = Paths.get(storagePath, "worktrees");
    }

    /**
     * Create a new git worktree for isolated task execution
     *
     * @param projectId the project ID
     * @param branch the branch name
     * @return the path to the worktree
     */
    public Path createWorktree(Long projectId, String branch) {
        try {
            // Ensure worktrees directory exists
            Files.createDirectories(worktreesDir);

            // Create unique worktree path
            String worktreeName = "task-" + projectId + "-" + UUID.randomUUID().toString().substring(0, 8);
            Path worktreePath = worktreesDir.resolve(worktreeName);

            // For now, create a simple directory structure
            // In production, this would use actual git worktree commands
            Files.createDirectories(worktreePath);

            // Initialize git repo if needed
            if (!Files.exists(worktreePath.resolve(".git"))) {
                runCommand(worktreePath.toFile(), "git init");
                runCommand(worktreePath.toFile(), "git checkout -b " + branch);
            }

            log.info("Created worktree at: {}", worktreePath);
            return worktreePath;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create worktree", e);
        }
    }

    /**
     * Remove a git worktree
     *
     * @param worktreePath the path to the worktree
     */
    public void removeWorktree(Path worktreePath) {
        try {
            if (Files.exists(worktreePath)) {
                // In production, use: git worktree remove <path>
                // For now, just delete the directory
                deleteDirectory(worktreePath.toFile());
                log.info("Removed worktree at: {}", worktreePath);
            }
        } catch (Exception e) {
            log.warn("Failed to remove worktree: {}", worktreePath, e);
        }
    }

    /**
     * Clone a repository to a worktree
     *
     * @param repoUrl the repository URL
     * @param worktreePath the target path
     */
    public void cloneRepository(String repoUrl, Path worktreePath) {
        try {
            runCommand(new File("."), "git clone " + repoUrl + " " + worktreePath);
            log.info("Cloned repository to: {}", worktreePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone repository", e);
        }
    }

    /**
     * Create a new branch in the worktree
     *
     * @param worktreePath the worktree path
     * @param branchName the branch name
     */
    public void createBranch(Path worktreePath, String branchName) {
        try {
            runCommand(worktreePath.toFile(), "git checkout -b " + branchName);
            log.info("Created branch: {} in worktree: {}", branchName, worktreePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create branch", e);
        }
    }

    /**
     * Get the current branch name
     *
     * @param worktreePath the worktree path
     * @return the current branch name
     */
    public String getCurrentBranch(Path worktreePath) {
        try {
            return runCommandWithOutput(worktreePath.toFile(), "git rev-parse --abbrev-ref HEAD").trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void runCommand(File directory, String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.directory(directory);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }

    private String runCommandWithOutput(File directory, String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.directory(directory);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();
        return output.toString();
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
