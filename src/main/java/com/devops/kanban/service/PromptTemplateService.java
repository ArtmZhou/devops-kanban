package com.devops.kanban.service;

import com.devops.kanban.converter.EntityDTOConverter;
import com.devops.kanban.dto.PromptTemplateDTO;
import com.devops.kanban.entity.PromptTemplate;
import com.devops.kanban.entity.Task;
import com.devops.kanban.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing prompt templates.
 * Handles business logic for template CRUD operations and default template initialization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;
    private final EntityDTOConverter converter;

    /**
     * Default templates for each task phase.
     * These are used as fallback when no user-defined template exists.
     */
    private static final Map<String, String> DEFAULT_TEMPLATES = new HashMap<>();

    static {
        DEFAULT_TEMPLATES.put("TODO", "分析任务需求，准备实现方案，考虑依赖关系和潜在风险。识别所需资源和相关人员，清晰记录需求文档。");
        DEFAULT_TEMPLATES.put("DESIGN", "聚焦架构设计和技术选型，考虑可扩展性、可维护性和设计模式。记录设计决策、API契约和数据模型。");
        DEFAULT_TEMPLATES.put("DEVELOPMENT", "遵循编码规范和最佳实践实现代码。优雅处理错误，添加适当的日志，编写清晰可维护的代码，使用规范的注释和变量命名。");
        DEFAULT_TEMPLATES.put("TESTING", "编写全面的测试用例，覆盖正向、负向和边界情况。确保良好的测试覆盖率，验证所有输入输出。使用适当的测试框架，模拟外部依赖。");
        DEFAULT_TEMPLATES.put("RELEASE", "准备发布说明，更新部署配置，验证环境变量。制定回滚方案，清晰记录部署步骤。确保向后兼容性。");
        DEFAULT_TEMPLATES.put("DONE", "审查所有变更，更新文档，清理临时文件，关闭连接，释放资源。创建总结报告，记录经验教训和改进建议。");
        DEFAULT_TEMPLATES.put("BLOCKED", "分析阻塞原因，识别根本问题，提出解决方案。记录问题和可用的临时解决方案。");
        DEFAULT_TEMPLATES.put("CANCELLED", "审查部分完成的工作，记录取消原因。归档可能被复用的代码。");
    }

    /**
     * Initialize default templates if they don't exist.
     * Called on application startup.
     */
    public void initializeDefaultTemplates() {
        for (Task.TaskStatus phase : Task.TaskStatus.values()) {
            String phaseName = phase.name();
            String defaultInstruction = DEFAULT_TEMPLATES.getOrDefault(phaseName, "完成此任务。");

            Optional<PromptTemplate> existing = promptTemplateRepository.findByPhase(phaseName);
            if (existing.isEmpty()) {
                // 不存在则创建新模板
                PromptTemplate template = new PromptTemplate();
                template.setPhase(phaseName);
                template.setInstruction(defaultInstruction);
                template.setDefault(true);
                template.setCreatedAt(LocalDateTime.now());
                template.setUpdatedAt(LocalDateTime.now());
                promptTemplateRepository.save(template);
                log.info("Created default prompt template for phase: {}", phaseName);
            } else {
                // 已存在：如果是默认模板，则更新内容；如果是用户自定义模板，则跳过
                PromptTemplate template = existing.get();
                if (template.isDefault()) {
                    template.setInstruction(defaultInstruction);
                    template.setUpdatedAt(LocalDateTime.now());
                    promptTemplateRepository.save(template);
                    log.info("Updated default prompt template for phase: {}", phaseName);
                } else {
                    log.info("Skipped custom prompt template for phase: {}", phaseName);
                }
            }
        }
        log.info("Prompt templates initialization completed");
    }

    /**
     * Get all prompt templates.
     */
    public List<PromptTemplateDTO> findAll() {
        return promptTemplateRepository.findAll().stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get a prompt template by phase.
     */
    public PromptTemplateDTO findByPhase(String phase) {
        return promptTemplateRepository.findByPhase(phase)
                .map(converter::toDTO)
                .orElse(null);
    }

    /**
     * Get a prompt template by ID.
     */
    public PromptTemplateDTO findById(Long id) {
        return promptTemplateRepository.findById(id)
                .map(converter::toDTO)
                .orElse(null);
    }

    /**
     * Get the instruction for a task phase.
     * Returns custom template if exists, otherwise default instruction.
     */
    public String getInstructionForPhase(String status) {
        if (status == null) {
            return DEFAULT_TEMPLATES.get("TODO");
        }

        // First try to find custom template
        Optional<PromptTemplate> template = promptTemplateRepository.findByPhase(status);
        if (template.isPresent() && !template.get().isDefault()) {
            return template.get().getInstruction();
        }

        // Fall back to default templates
        return DEFAULT_TEMPLATES.getOrDefault(status, DEFAULT_TEMPLATES.get("TODO"));
    }

    /**
     * Update a prompt template.
     */
    public PromptTemplateDTO update(Long id, PromptTemplateDTO dto) {
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PromptTemplate not found with id: " + id));

        template.setPhase(dto.getPhase());
        template.setInstruction(dto.getInstruction());
        template.setDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false);
        template.setUpdatedAt(LocalDateTime.now());

        template = promptTemplateRepository.save(template);
        return converter.toDTO(template);
    }

    /**
     * Reset a template to default values.
     */
    public PromptTemplateDTO resetToDefault(Long id) {
        PromptTemplate template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PromptTemplate not found with id: " + id));

        String phase = template.getPhase();
        String defaultInstruction = DEFAULT_TEMPLATES.getOrDefault(phase, "完成此任务。");

        template.setInstruction(defaultInstruction);
        template.setDefault(true);
        template.setUpdatedAt(LocalDateTime.now());

        template = promptTemplateRepository.save(template);
        return converter.toDTO(template);
    }
}
