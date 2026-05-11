import { logger } from '../../../utils/logger.js';
import { TaskRepository } from '../../../repositories/taskRepository.js';
import { ProjectRepository } from '../../../repositories/projectRepository.js';
import { splitSuggestionRepository } from '../../../repositories/splitSuggestionRepository.js';
import { extractJsonBlock } from '../parseJsonBlock.js';
import { matchProject } from '../projectMatcher.js';
import { DEFAULT_SPLIT_PROMPT, renderSplitPrompt } from '../defaultSplitPrompt.js';
import type { Suggestion } from '../../../types/entities.js';

const taskRepo = new TaskRepository();
const projectRepo = new ProjectRepository();

interface SplitStepConfig {
  agentId: number;
  promptOverride?: string;
}

interface SplitStepContext {
  runId: number;
  taskId: number;
  lastStepOutput: string;
  callAgent: (agentId: number, prompt: string) => Promise<string>;
}

interface SplitStepResult {
  summary: string;
  skipped?: boolean;
  suggestionId?: number;
}

export async function executeSplitTaskStep(
  config: SplitStepConfig,
  ctx: SplitStepContext,
): Promise<SplitStepResult> {
  const task = await taskRepo.findById(ctx.taskId);
  if (!task) throw new Error(`Task ${ctx.taskId} not found`);

  if (task.parent_task_id != null) {
    logger.info('split', `skip: task ${ctx.taskId} is a child task (parent=${task.parent_task_id})`);
    return { summary: 'Skipped: this is a child task, not splitting further.', skipped: true };
  }

  const project = await projectRepo.findById(task.project_id);
  if (!project) throw new Error(`Project ${task.project_id} not found`);

  const allProjects = await projectRepo.findAll();
  const availableProjectsBlock = allProjects
    .filter(p => p.id !== project.id)
    .map(p => `- ${p.name} (id=${p.id}) → ${p.git_url ?? '(no git_url)'}`)
    .join('\n');

  const prompt = renderSplitPrompt(config.promptOverride || DEFAULT_SPLIT_PROMPT, {
    task_title: task.title,
    task_description: task.description ?? '',
    project_name: project.name,
    project_repo_url: project.git_url ?? '',
    last_step_output: ctx.lastStepOutput,
    available_projects: availableProjectsBlock || '(no other projects)',
  });

  logger.info('split', `calling agent ${config.agentId} for task ${ctx.taskId}`);
  const output = await ctx.callAgent(config.agentId, prompt);

  let rawSuggestions: unknown;
  try {
    rawSuggestions = extractJsonBlock(output);
  } catch (e) {
    logger.error('split', `JSON extraction failed: ${(e as Error).message}`);
    throw new Error(`AI 输出未包含有效的 JSON 代码块: ${(e as Error).message}`);
  }

  if (!Array.isArray(rawSuggestions)) {
    throw new Error('AI 输出必须是 JSON 数组');
  }

  const suggestions: Suggestion[] = rawSuggestions.map((raw: any) => {
    const linkedId = raw.linked_project_id ?? matchProject(
      { title: raw.title ?? '', target_repo_url: raw.target_repo_url ?? null },
      allProjects,
    );

    const matchedProject = linkedId ? allProjects.find(p => p.id === linkedId) : null;
    const templateId = raw.template_id ?? matchedProject?.default_template_id ?? null;

    return {
      title: String(raw.title ?? '未命名任务'),
      description: String(raw.description ?? ''),
      template_id: templateId,
      linked_project_id: linkedId,
      target_repo_url: linkedId ? null : (raw.target_repo_url ?? null),
      depends_on_indices: Array.isArray(raw.depends_on_indices) ? raw.depends_on_indices : [],
      enabled: raw.enabled !== false,
    };
  });

  const saved = await splitSuggestionRepository.create({
    parent_task_id: ctx.taskId,
    workflow_run_id: ctx.runId,
    status: 'PENDING',
    suggestions,
    confirmed_at: null,
  } as any);

  logger.info('split', `saved suggestion ${saved.id} with ${suggestions.length} items`);

  return {
    summary: `已生成 ${suggestions.length} 条拆分建议，等待用户审核。`,
    suggestionId: saved.id,
  };
}
