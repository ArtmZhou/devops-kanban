import { SkillRepository } from '../../repositories/skillRepository.js';
import { AgentRepository } from '../../repositories/agentRepository.js';
import { SkillService } from '../skillService.js';
import { ExecutorType } from '../../types/executors.js';
import { STORAGE_PATH } from '../../config/index.js';
import { copyFileSync, mkdirSync, readdirSync, statSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const BUILTIN_SKILL_NAME = 'task-splitter';
export const TASK_SPLITTER_AGENT_ROLE = 'TASK_SPLITTER';
const LEGACY_TYPO_ROLE = 'TASK_SPLITPER';
const BUILTIN_AGENT_NAME = '任务拆分助手';
const BUILTIN_AGENT_DESCRIPTION = '专用于将任务拆分为子任务的AI代理';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SOURCE_SKILL_DIR = resolve(__dirname, '..', '..', 'resources', 'skills', BUILTIN_SKILL_NAME);

export async function bootstrapBuiltinTaskSplitAgent(): Promise<void> {
  const skillRepo = new SkillRepository();
  const agentRepo = new AgentRepository();
  const skillService = new SkillService({ storagePath: STORAGE_PATH, skillRepo });

  // 1. Ensure the builtin skill exists, and always sync files from source to storage
  // so SKILL.md updates from new releases take effect.
  const existingSkills = await skillRepo.findAll();
  let skillId: number;
  const targetDir = skillService.getSkillDir(BUILTIN_SKILL_NAME);

  const existingSkill = existingSkills.find(s => s.name === BUILTIN_SKILL_NAME || s.identifier === BUILTIN_SKILL_NAME);
  if (existingSkill) {
    skillId = existingSkill.id;
    mkdirSync(targetDir, { recursive: true });
    copySkillFiles(SOURCE_SKILL_DIR, targetDir);
    console.log(`[TaskSplitAgent] Builtin skill "${BUILTIN_SKILL_NAME}" exists (id=${skillId}); synced source files.`);
  } else {
    const created = await skillService.createSkill(BUILTIN_SKILL_NAME, 'Built-in skill for splitting tasks into sub-tasks with project matching and dependency mapping.');
    skillId = created.id;
    mkdirSync(targetDir, { recursive: true });
    copySkillFiles(SOURCE_SKILL_DIR, targetDir);
    console.log(`[TaskSplitAgent] Builtin skill "${BUILTIN_SKILL_NAME}" created (id=${skillId}).`);
  }

  // 2. Ensure the builtin agent exists
  const existingAgents = await agentRepo.findAll();
  const legacyAgent = existingAgents.find(a => a.role === LEGACY_TYPO_ROLE);
  if (legacyAgent) {
    await agentRepo.update(legacyAgent.id, { role: TASK_SPLITTER_AGENT_ROLE });
    console.log(`[TaskSplitAgent] Migrated legacy role "${LEGACY_TYPO_ROLE}" → "${TASK_SPLITTER_AGENT_ROLE}" on agent id=${legacyAgent.id}.`);
  }

  const existingAgent = existingAgents.find(a => a.role === TASK_SPLITTER_AGENT_ROLE) || legacyAgent;
  if (existingAgent) {
    console.log(`[TaskSplitAgent] Builtin agent "${BUILTIN_AGENT_NAME}" already exists (id=${existingAgent.id}).`);
  } else {
    const agentData: {
      name: string;
      executorType: ExecutorType;
      role: string;
      description: string;
      enabled: boolean;
      skills: number[];
      mcpServers: number[];
      env: Record<string, string>;
    } = {
      name: BUILTIN_AGENT_NAME,
      executorType: ExecutorType.CLAUDE_CODE,
      role: TASK_SPLITTER_AGENT_ROLE,
      description: BUILTIN_AGENT_DESCRIPTION,
      enabled: true,
      skills: [skillId],
      mcpServers: [],
      env: {},
    };
    const created = await agentRepo.create(agentData);
    console.log(`[TaskSplitAgent] Builtin agent "${BUILTIN_AGENT_NAME}" created (id=${created.id}).`);
  }
}

function copySkillFiles(srcDir: string, destDir: string): void {
  if (!readdirSync(srcDir).length) return;
  for (const entry of readdirSync(srcDir)) {
    const src = resolve(srcDir, entry);
    const dest = resolve(destDir, entry);
    if (statSync(src).isFile()) {
      copyFileSync(src, dest);
    }
  }
}
