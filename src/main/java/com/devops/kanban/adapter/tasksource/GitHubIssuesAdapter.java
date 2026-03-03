package com.devops.kanban.adapter.tasksource;

import com.devops.kanban.dto.TaskDTO;
import com.devops.kanban.entity.TaskSource;
import com.devops.kanban.spi.TaskSourceAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GitHub Issues adapter - fetches tasks from GitHub Issues.
 */
@Component
public class GitHubIssuesAdapter implements TaskSourceAdapter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public TaskSource.TaskSourceType getType() {
        return TaskSource.TaskSourceType.GITHUB;
    }

    @Override
    public boolean validateConfig(String configJson) {
        try {
            JsonNode config = mapper.readTree(configJson);
            return config.has("repo") && config.get("repo").asText().contains("/");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<TaskDTO> fetchTasks(TaskSource source) {
        List<TaskDTO> tasks = new ArrayList<>();
        try {
            JsonNode config = mapper.readTree(source.getConfig());
            String repo = config.get("repo").asText();
            String token = config.has("token") ? config.get("token").asText() : null;

            String url = "https://api.github.com/repos/" + repo + "/issues?state=open";
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github.v3+json");

            if (token != null && !token.isEmpty()) {
                requestBuilder.header("Authorization", "token " + token);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode issues = mapper.readTree(response.body());
                for (JsonNode issue : issues) {
                    // Skip pull requests
                    if (issue.has("pull_request")) continue;

                    TaskDTO task = TaskDTO.builder()
                            .projectId(source.getProjectId())
                            .sourceId(source.getId())
                            .externalId(String.valueOf(issue.get("number").asInt()))
                            .title(issue.get("title").asText())
                            .description(issue.get("body") != null ? issue.get("body").asText() : "")
                            .status(mapStatus(issue.get("state").asText()))
                            .syncedAt(LocalDateTime.now())
                            .build();
                    tasks.add(task);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch GitHub issues", e);
        }
        return tasks;
    }

    @Override
    public Optional<TaskDTO> fetchTask(TaskSource source, String externalId) {
        try {
            JsonNode config = mapper.readTree(source.getConfig());
            String repo = config.get("repo").asText();
            String token = config.has("token") ? config.get("token").asText() : null;

            String url = "https://api.github.com/repos/" + repo + "/issues/" + externalId;
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github.v3+json");

            if (token != null && !token.isEmpty()) {
                requestBuilder.header("Authorization", "token " + token);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode issue = mapper.readTree(response.body());
                TaskDTO task = TaskDTO.builder()
                        .projectId(source.getProjectId())
                        .sourceId(source.getId())
                        .externalId(String.valueOf(issue.get("number").asInt()))
                        .title(issue.get("title").asText())
                        .description(issue.get("body") != null ? issue.get("body").asText() : "")
                        .status(mapStatus(issue.get("state").asText()))
                        .syncedAt(LocalDateTime.now())
                        .build();
                return Optional.of(task);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch GitHub issue", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean testConnection(TaskSource source) {
        try {
            JsonNode config = mapper.readTree(source.getConfig());
            String repo = config.get("repo").asText();
            String token = config.has("token") ? config.get("token").asText() : null;

            String url = "https://api.github.com/repos/" + repo;
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github.v3+json");

            if (token != null && !token.isEmpty()) {
                requestBuilder.header("Authorization", "token " + token);
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String mapStatus(String githubState) {
        if ("open".equals(githubState)) {
            return "TODO";
        }
        return "DONE";
    }
}
