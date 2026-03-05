package com.devops.kanban.controller;

import com.devops.kanban.dto.AgentDTO;
import com.devops.kanban.dto.ApiResponse;
import com.devops.kanban.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.origins:http://localhost:5173}")
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AgentDTO>>> getAgents(
            @RequestParam(required = false) Long projectId) {
        System.out.println("[AgentController] getAgents called with projectId: " + projectId);
        if (projectId == null) {
            System.out.println("[AgentController] projectId is null, returning error");
            return ResponseEntity.ok(ApiResponse.error("projectId is required"));
        }
        List<AgentDTO> agents = agentService.findByProjectId(projectId);
        System.out.println("[AgentController] Found " + agents.size() + " agents for project " + projectId);
        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentDTO>> getAgent(@PathVariable Long id) {
        AgentDTO agent = agentService.findById(id);
        if (agent == null) {
            return ResponseEntity.ok(ApiResponse.error("Agent not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(agent));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AgentDTO>> createAgent(@Valid @RequestBody AgentDTO dto) {
        AgentDTO created = agentService.create(dto);
        return ResponseEntity.ok(ApiResponse.success("Agent created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AgentDTO>> updateAgent(
            @PathVariable Long id,
            @Valid @RequestBody AgentDTO dto) {
        AgentDTO updated = agentService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Agent updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAgent(@PathVariable Long id) {
        agentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Agent deleted successfully", null));
    }
}
