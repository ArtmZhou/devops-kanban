package com.devops.kanban.service;

import com.devops.kanban.dto.AgentDTO;
import com.devops.kanban.entity.Agent;
import com.devops.kanban.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentRepository agentRepository;

    public List<AgentDTO> findByProjectId(Long projectId) {
        System.out.println("[AgentService] findByProjectId called with projectId: " + projectId);
        List<Agent> agents = agentRepository.findByProjectId(projectId);
        System.out.println("[AgentService] Found " + agents.size() + " agents in repository");
        return agents.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public AgentDTO findById(Long id) {
        return agentRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    public AgentDTO create(AgentDTO dto) {
        Agent agent = toEntity(dto);
        agent = agentRepository.save(agent);
        return toDTO(agent);
    }

    public AgentDTO update(Long id, AgentDTO dto) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        agent.setName(dto.getName());
        agent.setType(Agent.AgentType.valueOf(dto.getType()));
        agent.setCommand(dto.getCommand());
        agent.setConfig(dto.getConfig());
        agent.setEnabled(dto.isEnabled());

        agent = agentRepository.save(agent);
        return toDTO(agent);
    }

    public void delete(Long id) {
        agentRepository.deleteById(id);
    }

    private AgentDTO toDTO(Agent agent) {
        return AgentDTO.builder()
                .id(agent.getId())
                .projectId(agent.getProjectId())
                .name(agent.getName())
                .type(agent.getType() != null ? agent.getType().name() : Agent.AgentType.CLAUDE.name())
                .command(agent.getCommand())
                .config(agent.getConfig())
                .enabled(agent.isEnabled())
                .createdAt(agent.getCreatedAt())
                .build();
    }

    private Agent toEntity(AgentDTO dto) {
        return Agent.builder()
                .id(dto.getId())
                .projectId(dto.getProjectId())
                .name(dto.getName())
                .type(dto.getType() != null ? Agent.AgentType.valueOf(dto.getType()) : Agent.AgentType.CLAUDE)
                .command(dto.getCommand())
                .config(dto.getConfig())
                .enabled(dto.isEnabled())
                .build();
    }
}
