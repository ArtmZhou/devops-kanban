# Task Dependency DAG + AI Auto-Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable a task's workflow to auto-split into multiple dependent child tasks across projects, forming a DAG pipeline that self-triggers as upstream tasks complete.

**Architecture:** Add `parent_task_id`, `depends_on`, `target_repo_url` columns to tasks. Introduce a `SPLIT_TASK` workflow step that calls an Agent, parses JSON suggestions, and persists them to a new `split_suggestions` table. Users review/edit suggestions in the Workspace view, then batch-create child tasks. A Task event listener auto-transitions `WAITING` tasks to `RUNNING` when all dependencies are `DONE`.

**Tech Stack:** Fastify + Zod + LibSQL (backend), Vue 3 + Element Plus + Pinia (frontend), Mastra workflows.

**Spec reference:** `docs/superpowers/specs/2026-05-11-task-dependency-dag-design.md`

**Important discovered constraint:** Data uses LibSQL (SQLite) via `BaseRepository`, NOT JSON files. All schema changes go in `backend/src/db/schema.sql`.

---

## File Structure

### Backend (`backend/src/`)

| File | Responsibility |
|------|----------------|
| `db/schema.sql` | Add 3 columns to `tasks`, 1 column to `projects`, create `split_suggestions` table |
| `types/entities.ts` | Add `parent_task_id`, `depends_on`, `target_repo_url` to `TaskEntity`; add `default_template_id` to `ProjectEntity`; add `SplitSuggestionEntity` |
| `repositories/taskRepository.ts` | Parse `depends_on` JSON field; add `findDependents`, `findWaitingTasks` methods |
| `repositories/projectRepository.ts` | No code changes needed (BaseRepository handles new column automatically) |
| `repositories/splitSuggestionRepository.ts` | NEW — CRUD for split_suggestions, parse `suggestions` JSON field |
| `services/taskService.ts` | Add `batchCreate`, `getDependents`, `getPipeline`, `onTaskStatusChange` (dep-trigger & cascade-fail) |
| `services/splitSuggestionService.ts` | NEW — create/update/confirm/dismiss logic, project matching, cycle detection |
| `services/workflow/executors/splitTaskExecutor.ts` | NEW — SPLIT_TASK step handler: recursion guard, prompt rendering, Agent call, JSON extraction, project matching, persist suggestion |
| `services/workflow/defaultSplitPrompt.ts` | NEW — built-in system prompt template with placeholders |
| `services/workflow/workflows.ts` | Dispatch on `stepType === 'SPLIT_TASK'` to splitTaskExecutor |
| `routes/tasks.ts` | Add `POST /batch-create`, `GET /:id/dependents`, `GET /:id/pipeline`, `POST /:id/regenerate-split` |
| `routes/splitSuggestions.ts` | NEW — `GET /tasks/:id/split-suggestions`, `PATCH /:id`, `POST /:id/confirm`, `POST /:id/dismiss` |
| `routes/index.ts` | Register splitSuggestions routes |

### Frontend (`frontend/src/`)

| File | Responsibility |
|------|----------------|
| `api/splitSuggestions.js` | NEW — Axios client |
| `api/task.js` | Add `batchCreate`, `getPipeline`, `getDependents`, `regenerateSplit` |
| `views/WorkspaceView.vue` | Wire real data: task list → API, AI split card → real suggestions, DAG → real pipeline |
| `components/workspace/AiSplitCard.vue` | NEW — extracted reusable card |
| `stores/splitSuggestions.js` | NEW — Pinia store for pending suggestions + debounced PATCH |
| `locales/zh.js`, `locales/en.js` | New i18n keys for workspace + split card |

---

## Task 1: Schema migration — task DAG columns + projects default_template_id

**Files:**
- Modify: `backend/src/db/schema.sql`
- Test: manual via `npm run build && node dist/src/db/migrate.js`

The migrate script (`backend/src/db/migrate.ts`) auto-applies ADD COLUMN for new columns found in schema.sql. Just add the columns.

- [ ] **Step 1: Add columns to tasks and projects in schema.sql**

Locate the `CREATE TABLE IF NOT EXISTS tasks` block and add 3 columns before `created_at`:

```sql
  parent_task_id INTEGER,
  depends_on TEXT NOT NULL DEFAULT '[]',
  target_repo_url TEXT,
```

Add index after the existing tasks indexes:

```sql
CREATE INDEX IF NOT EXISTS idx_tasks_parent_task_id ON tasks(parent_task_id);
```

Locate the `CREATE TABLE IF NOT EXISTS projects` block and add 1 column before `created_at`:

```sql
  default_template_id TEXT,
```

- [ ] **Step 2: Add split_suggestions table to schema.sql**

Append after the tasks table and its indexes:

```sql
-- split_suggestions: AI 拆分建议表
CREATE TABLE IF NOT EXISTS split_suggestions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  parent_task_id INTEGER NOT NULL,
  workflow_run_id INTEGER,
  status TEXT NOT NULL DEFAULT 'PENDING',
  suggestions TEXT NOT NULL DEFAULT '[]',
  confirmed_at TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_split_suggestions_parent_task_id ON split_suggestions(parent_task_id);
CREATE INDEX IF NOT EXISTS idx_split_suggestions_status ON split_suggestions(status);
```

- [ ] **Step 3: Run migration to verify schema applies**

Run:
```bash
cd backend && npm run build && node dist/src/db/migrate.js
```

Expected: Output includes lines like `Added column tasks.parent_task_id`, `Added column tasks.depends_on`, `Added column tasks.target_repo_url`, `Added column projects.default_template_id`, `Created table split_suggestions`. No errors.

- [ ] **Step 4: Commit**

```bash
git add backend/src/db/schema.sql
git commit -m "feat(db): add task DAG columns and split_suggestions table"
```

---

## Task 2: Extend TaskEntity, ProjectEntity, add SplitSuggestionEntity

**Files:**
- Modify: `backend/src/types/entities.ts`

- [ ] **Step 1: Add DAG fields to TaskEntity**

Inside the `TaskEntity` interface, add before `created_at`:

```typescript
  parent_task_id?: number | null;
  depends_on: number[];
  target_repo_url?: string | null;
```

- [ ] **Step 2: Add default_template_id to ProjectEntity**

Inside the `ProjectEntity` interface, add before `created_at`:

```typescript
  default_template_id?: string | null;
```

- [ ] **Step 3: Add SplitSuggestionEntity and Suggestion types**

Append at the end of the file:

```typescript
export interface Suggestion {
  title: string;
  description: string;
  template_id: string | null;
  linked_project_id: number | null;
  target_repo_url: string | null;
  depends_on_indices: number[];
  enabled: boolean;
}

export type SplitSuggestionStatus = 'PENDING' | 'CONFIRMED' | 'DISMISSED';

export interface SplitSuggestionEntity {
  id: number;
  parent_task_id: number;
  workflow_run_id: number | null;
  status: SplitSuggestionStatus;
  suggestions: Suggestion[];
  confirmed_at: string | null;
  created_at: string;
  updated_at: string;
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/types/entities.ts
git commit -m "feat(types): add DAG fields to TaskEntity and SplitSuggestionEntity"
```

---

## Task 3: TaskRepository — parse depends_on, add query methods

**Files:**
- Modify: `backend/src/repositories/taskRepository.ts`

- [ ] **Step 1: Update parseRow to parse depends_on JSON**

Replace the existing `parseRow` method with:

```typescript
protected override parseRow(row: Record<string, unknown>): TaskEntity {
  return {
    ...row,
    labels: row.labels ? JSON.parse(row.labels as string) : undefined,
    depends_on: row.depends_on ? JSON.parse(row.depends_on as string) : [],
  } as TaskEntity;
}
```

- [ ] **Step 2: Update serializeRow to stringify depends_on**

Replace the existing `serializeRow` method with:

```typescript
protected override serializeRow(entity: Partial<TaskEntity>): Record<string, unknown> {
  const result: Record<string, unknown> = { ...entity };
  if (entity.labels !== undefined) {
    result.labels = JSON.stringify(entity.labels);
  }
  if (entity.depends_on !== undefined) {
    result.depends_on = JSON.stringify(entity.depends_on);
  }
  return result;
}
```

- [ ] **Step 3: Add findDependents — tasks that depend on a given task**

Add inside the TaskRepository class:

```typescript
async findDependents(taskId: number): Promise<TaskEntity[]> {
  const result = await this.client.execute({
    sql: `SELECT * FROM tasks WHERE depends_on LIKE ?`,
    args: [`%${taskId}%`],
  });
  return result.rows
    .map(row => this.parseRow(row as Record<string, unknown>))
    .filter(task => task.depends_on.includes(taskId));
}
```

Note: The SQL `LIKE` is a cheap pre-filter; the `.filter()` eliminates false positives from substring matches (e.g., `12` in `[123]`).

- [ ] **Step 4: Add findChildren — tasks whose parent_task_id matches**

```typescript
async findChildren(parentTaskId: number): Promise<TaskEntity[]> {
  const result = await this.client.execute({
    sql: 'SELECT * FROM tasks WHERE parent_task_id = ?',
    args: [parentTaskId],
  });
  return result.rows.map(row => this.parseRow(row as Record<string, unknown>));
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/repositories/taskRepository.ts
git commit -m "feat(repo): task dependents/children queries and depends_on JSON parsing"
```

---

## Task 4: SplitSuggestionRepository

**Files:**
- Create: `backend/src/repositories/splitSuggestionRepository.ts`

- [ ] **Step 1: Create the repository file**

```typescript
import { BaseRepository } from './base.js';
import type { SplitSuggestionEntity } from '../types/entities.ts';

class SplitSuggestionRepository extends BaseRepository<SplitSuggestionEntity> {
  constructor() {
    super('split_suggestions');
  }

  protected override parseRow(row: Record<string, unknown>): SplitSuggestionEntity {
    return {
      ...row,
      suggestions: row.suggestions ? JSON.parse(row.suggestions as string) : [],
    } as SplitSuggestionEntity;
  }

  protected override serializeRow(
    entity: Partial<SplitSuggestionEntity>,
  ): Record<string, unknown> {
    const result: Record<string, unknown> = { ...entity };
    if (entity.suggestions !== undefined) {
      result.suggestions = JSON.stringify(entity.suggestions);
    }
    return result;
  }

  async findByParentTask(parentTaskId: number): Promise<SplitSuggestionEntity[]> {
    const result = await this.client.execute({
      sql: 'SELECT * FROM split_suggestions WHERE parent_task_id = ? ORDER BY created_at DESC',
      args: [parentTaskId],
    });
    return result.rows.map(row => this.parseRow(row as Record<string, unknown>));
  }

  async findPendingByParentTask(parentTaskId: number): Promise<SplitSuggestionEntity | null> {
    const result = await this.client.execute({
      sql: `SELECT * FROM split_suggestions WHERE parent_task_id = ? AND status = 'PENDING' ORDER BY created_at DESC LIMIT 1`,
      args: [parentTaskId],
    });
    const row = result.rows[0];
    return row ? this.parseRow(row as Record<string, unknown>) : null;
  }
}

export const splitSuggestionRepository = new SplitSuggestionRepository();
export type { SplitSuggestionRepository };
```

- [ ] **Step 2: Verify it builds**

```bash
cd backend && npx tsc --noEmit
```

Expected: no TypeScript errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/repositories/splitSuggestionRepository.ts
git commit -m "feat(repo): add SplitSuggestionRepository"
```

---

## Task 5: Default split prompt template

**Files:**
- Create: `backend/src/services/workflow/defaultSplitPrompt.ts`

- [ ] **Step 1: Create the prompt module**

```typescript
export const DEFAULT_SPLIT_PROMPT = `你是一个项目任务拆解助手。你的任务是根据上游工作流的产出，把当前任务拆分成若干可以并行或有依赖的子任务。

## 任务上下文
- 任务标题：{{task_title}}
- 任务描述：{{task_description}}
- 当前项目：{{project_name}}
- 当前仓库：{{project_repo_url}}

## 上游工作流产出
{{last_step_output}}

## 可用的 Coplat 项目（按仓库 URL 精确匹配，找不到时设为 null）
{{available_projects}}

## 输出要求
请把你的拆分方案放在一个 \`\`\`json 代码块 里，数组中每一项遵循下面的 Suggestion schema：

\`\`\`json
[
  {
    "title": "子任务标题",
    "description": "子任务描述，1-3 句",
    "template_id": null,
    "linked_project_id": null,
    "target_repo_url": "git@github.com:org/repo.git 或 null",
    "depends_on_indices": [],
    "enabled": true
  }
]
\`\`\`

字段说明：
- \`linked_project_id\`：若能在"可用的 Coplat 项目"里匹配到目标仓库，填该项目 id；否则填 null。
- \`target_repo_url\`：未匹配到 Coplat 项目时填外部仓库 URL，匹配到则填 null。
- \`depends_on_indices\`：依赖的其他子任务在此数组中的下标（从 0 开始）。无依赖时为空数组。
- \`template_id\`：工作流模板 id，不确定时填 null，由用户手动选。
- \`enabled\`：默认 true。

只输出 JSON 代码块，不要其他解释。`;

export function renderSplitPrompt(
  template: string,
  vars: {
    task_title: string;
    task_description: string;
    project_name: string;
    project_repo_url: string;
    last_step_output: string;
    available_projects: string;
  },
): string {
  return template
    .replace(/\{\{task_title\}\}/g, vars.task_title)
    .replace(/\{\{task_description\}\}/g, vars.task_description)
    .replace(/\{\{project_name\}\}/g, vars.project_name)
    .replace(/\{\{project_repo_url\}\}/g, vars.project_repo_url)
    .replace(/\{\{last_step_output\}\}/g, vars.last_step_output)
    .replace(/\{\{available_projects\}\}/g, vars.available_projects);
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/services/workflow/defaultSplitPrompt.ts
git commit -m "feat(workflow): default split task prompt template"
```

---

## Task 6: JSON code block extractor (with tests)

**Files:**
- Create: `backend/src/services/workflow/parseJsonBlock.ts`
- Create: `backend/test/parseJsonBlock.test.ts`

- [ ] **Step 1: Write failing tests**

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractJsonBlock } from '../src/services/workflow/parseJsonBlock.js';

test('extracts fenced ```json block', () => {
  const text = 'here is the plan:\n```json\n[{"title":"a"}]\n```\nend';
  assert.deepEqual(extractJsonBlock(text), [{ title: 'a' }]);
});

test('extracts fenced ``` block without json tag', () => {
  const text = '```\n[{"title":"b"}]\n```';
  assert.deepEqual(extractJsonBlock(text), [{ title: 'b' }]);
});

test('extracts raw JSON array when no fences', () => {
  const text = 'result: [{"title":"c"}]';
  assert.deepEqual(extractJsonBlock(text), [{ title: 'c' }]);
});

test('picks the first ```json block when multiple', () => {
  const text = '```json\n[{"title":"first"}]\n```\n```json\n[{"title":"second"}]\n```';
  assert.deepEqual(extractJsonBlock(text), [{ title: 'first' }]);
});

test('throws when no JSON array found', () => {
  assert.throws(() => extractJsonBlock('no json here'), /no JSON/i);
});

test('throws when JSON is malformed', () => {
  assert.throws(() => extractJsonBlock('```json\n[not valid\n```'), /parse/i);
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && npx tsx --test test/parseJsonBlock.test.ts
```

Expected: FAIL — "Cannot find module '../src/services/workflow/parseJsonBlock.js'".

- [ ] **Step 3: Implement the extractor**

Create `backend/src/services/workflow/parseJsonBlock.ts`:

```typescript
export function extractJsonBlock(text: string): unknown {
  const fencedJson = /```json\s*\n([\s\S]*?)\n```/i.exec(text);
  if (fencedJson && fencedJson[1]) {
    return parseOrThrow(fencedJson[1]);
  }

  const fencedPlain = /```\s*\n([\s\S]*?)\n```/.exec(text);
  if (fencedPlain && fencedPlain[1]) {
    const body = fencedPlain[1].trim();
    if (body.startsWith('[') || body.startsWith('{')) {
      return parseOrThrow(body);
    }
  }

  const arrayMatch = /\[[\s\S]*\]/.exec(text);
  if (arrayMatch) {
    return parseOrThrow(arrayMatch[0]);
  }

  throw new Error('no JSON block or array found in output');
}

function parseOrThrow(raw: string): unknown {
  try {
    return JSON.parse(raw.trim());
  } catch (e) {
    throw new Error(`failed to parse JSON block: ${(e as Error).message}`);
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && npx tsx --test test/parseJsonBlock.test.ts
```

Expected: PASS — all 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/services/workflow/parseJsonBlock.ts backend/test/parseJsonBlock.test.ts
git commit -m "feat(workflow): JSON code block extractor with tests"
```

---

## Task 7: Project matcher (with tests)

**Files:**
- Create: `backend/src/services/workflow/projectMatcher.ts`
- Create: `backend/test/projectMatcher.test.ts`

Matches a Suggestion's `target_repo_url` / `title` against existing Coplat projects and fills `linked_project_id`.

- [ ] **Step 1: Write failing tests**

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matchProject } from '../src/services/workflow/projectMatcher.js';

const projects = [
  { id: 1, name: 'user-service', git_url: 'git@github.com:org/user-service.git' },
  { id: 2, name: 'order-service', git_url: 'git@github.com:org/order-service.git' },
  { id: 3, name: 'frontend', git_url: 'https://github.com/org/frontend.git' },
] as any;

test('exact URL match returns project id', () => {
  const r = matchProject({ title: 'x', target_repo_url: 'git@github.com:org/user-service.git' }, projects);
  assert.equal(r, 1);
});

test('title fuzzy matches project name', () => {
  const r = matchProject({ title: '前端页面开发', target_repo_url: null }, projects);
  assert.equal(r, 3);
});

test('returns null when nothing matches', () => {
  const r = matchProject({ title: '完全无关任务', target_repo_url: 'git@github.com:other/new-repo.git' }, projects);
  assert.equal(r, null);
});

test('URL match takes priority over title', () => {
  const r = matchProject(
    { title: 'frontend', target_repo_url: 'git@github.com:org/user-service.git' },
    projects,
  );
  assert.equal(r, 1);
});
```

- [ ] **Step 2: Run test to verify fail**

```bash
cd backend && npx tsx --test test/projectMatcher.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement matcher**

```typescript
interface ProjectLike {
  id: number;
  name: string;
  git_url: string | null | undefined;
}

interface SuggestionMatchInput {
  title: string;
  target_repo_url: string | null;
}

export function matchProject(
  suggestion: SuggestionMatchInput,
  projects: ProjectLike[],
): number | null {
  if (suggestion.target_repo_url) {
    const normUrl = normalizeUrl(suggestion.target_repo_url);
    for (const p of projects) {
      if (p.git_url && normalizeUrl(p.git_url) === normUrl) {
        return p.id;
      }
    }
  }

  const titleLower = suggestion.title.toLowerCase();
  for (const p of projects) {
    const nameLower = p.name.toLowerCase();
    if (titleLower.includes(nameLower) || nameLower.includes(titleLower)) {
      return p.id;
    }
  }

  return null;
}

function normalizeUrl(url: string): string {
  return url.trim().toLowerCase().replace(/\.git$/, '').replace(/\/$/, '');
}
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd backend && npx tsx --test test/projectMatcher.test.ts
```

Expected: PASS — all 4 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/services/workflow/projectMatcher.ts backend/test/projectMatcher.test.ts
git commit -m "feat(workflow): project matcher for split suggestions"
```

---

## Task 8: Cycle detector for depends_on (with tests)

**Files:**
- Create: `backend/src/services/workflow/dependencyValidator.ts`
- Create: `backend/test/dependencyValidator.test.ts`

Validates that `depends_on_indices` across suggestions don't form a cycle.

- [ ] **Step 1: Write failing tests**

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hasCycle } from '../src/services/workflow/dependencyValidator.js';

test('no cycle in linear dependency', () => {
  const sugg = [
    { depends_on_indices: [] },
    { depends_on_indices: [0] },
    { depends_on_indices: [1] },
  ] as any;
  assert.equal(hasCycle(sugg), false);
});

test('no cycle in parallel then join', () => {
  const sugg = [
    { depends_on_indices: [] },
    { depends_on_indices: [] },
    { depends_on_indices: [0, 1] },
  ] as any;
  assert.equal(hasCycle(sugg), false);
});

test('self-cycle detected', () => {
  const sugg = [{ depends_on_indices: [0] }] as any;
  assert.equal(hasCycle(sugg), true);
});

test('two-node cycle detected', () => {
  const sugg = [
    { depends_on_indices: [1] },
    { depends_on_indices: [0] },
  ] as any;
  assert.equal(hasCycle(sugg), true);
});

test('three-node cycle detected', () => {
  const sugg = [
    { depends_on_indices: [2] },
    { depends_on_indices: [0] },
    { depends_on_indices: [1] },
  ] as any;
  assert.equal(hasCycle(sugg), true);
});
```

- [ ] **Step 2: Run to verify fail**

```bash
cd backend && npx tsx --test test/dependencyValidator.test.ts
```

Expected: FAIL.

- [ ] **Step 3: Implement cycle detector**

```typescript
interface NodeWithDeps {
  depends_on_indices: number[];
}

export function hasCycle(nodes: NodeWithDeps[]): boolean {
  const WHITE = 0, GRAY = 1, BLACK = 2;
  const color = new Array(nodes.length).fill(WHITE);

  function dfs(i: number): boolean {
    if (color[i] === GRAY) return true;
    if (color[i] === BLACK) return false;
    color[i] = GRAY;
    for (const dep of nodes[i]!.depends_on_indices) {
      if (dep < 0 || dep >= nodes.length) continue;
      if (dfs(dep)) return true;
    }
    color[i] = BLACK;
    return false;
  }

  for (let i = 0; i < nodes.length; i++) {
    if (color[i] === WHITE && dfs(i)) return true;
  }
  return false;
}
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd backend && npx tsx --test test/dependencyValidator.test.ts
```

Expected: PASS — all 5 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/services/workflow/dependencyValidator.ts backend/test/dependencyValidator.test.ts
git commit -m "feat(workflow): cycle detector for split dependency graph"
```

---

## Task 9: SPLIT_TASK executor

**Files:**
- Create: `backend/src/services/workflow/executors/splitTaskExecutor.ts`

Handles the SPLIT_TASK step: recursion guard → render prompt → call Agent → extract JSON → match projects → persist suggestion.

- [ ] **Step 1: Create the executor file**

```typescript
import { logger } from '../../../utils/logger.js';
import { taskRepository } from '../../../repositories/taskRepository.js';
import { projectRepository } from '../../../repositories/projectRepository.js';
import { splitSuggestionRepository } from '../../../repositories/splitSuggestionRepository.js';
import { extractJsonBlock } from '../parseJsonBlock.js';
import { matchProject } from '../projectMatcher.js';
import { DEFAULT_SPLIT_PROMPT, renderSplitPrompt } from '../defaultSplitPrompt.js';
import type { Suggestion } from '../../../types/entities.ts';

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
  const task = await taskRepository.findById(ctx.taskId);
  if (!task) throw new Error(`Task ${ctx.taskId} not found`);

  if (task.parent_task_id != null) {
    logger.info(`[split] skip: task ${ctx.taskId} is a child task (parent=${task.parent_task_id})`);
    return { summary: 'Skipped: this is a child task, not splitting further.', skipped: true };
  }

  const project = await projectRepository.findById(task.project_id);
  if (!project) throw new Error(`Project ${task.project_id} not found`);

  const allProjects = await projectRepository.findAll();
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

  logger.info(`[split] calling agent ${config.agentId} for task ${ctx.taskId}`);
  const output = await ctx.callAgent(config.agentId, prompt);

  let rawSuggestions: unknown;
  try {
    rawSuggestions = extractJsonBlock(output);
  } catch (e) {
    logger.error(`[split] JSON extraction failed: ${(e as Error).message}`);
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

  logger.info(`[split] saved suggestion ${saved.id} with ${suggestions.length} items`);

  return {
    summary: `已生成 ${suggestions.length} 条拆分建议，等待用户审核。`,
    suggestionId: saved.id,
  };
}
```

- [ ] **Step 2: Verify build**

```bash
cd backend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/services/workflow/executors/splitTaskExecutor.ts
git commit -m "feat(workflow): SPLIT_TASK step executor"
```

---

## Task 10: Wire SPLIT_TASK dispatch into workflow factory

**Files:**
- Modify: `backend/src/services/workflow/workflows.ts`
- Modify: `backend/src/services/workflow/workflowStepExecutor.ts` (if step dispatch lives here)

Before editing, inspect how the existing executor types (CLAUDE_CODE, OPEN_CODE) are dispatched.

- [ ] **Step 1: Find the current step type dispatch**

```bash
grep -rn "executor_type\|stepType\|step.type" backend/src/services/workflow/workflowStepExecutor.ts backend/src/services/workflow/workflows.ts | head -30
```

Locate the switch/if block that selects which executor to run based on step type. Note its file/line.

- [ ] **Step 2: Add SPLIT_TASK branch**

In the dispatch function (likely `executeWorkflowStep` in `workflowStepExecutor.ts`), add a branch BEFORE the existing executor branches:

```typescript
if (step.type === 'SPLIT_TASK' || step.executor_type === 'SPLIT_TASK') {
  const { executeSplitTaskStep } = await import('./executors/splitTaskExecutor.js');
  const previousStep = workflowRun.steps[workflowRun.steps.indexOf(step) - 1];
  const lastStepOutput = previousStep?.summary ?? '';

  const result = await executeSplitTaskStep(
    {
      agentId: step.agent_id!,
      promptOverride: step.instructionPrompt || undefined,
    },
    {
      runId: workflowRun.id,
      taskId: task.id,
      lastStepOutput,
      callAgent: async (agentId, prompt) => {
        // Delegate to the Claude Code executor in one-shot mode.
        // Reuse existing Agent chat service or spawn a short-lived session.
        const { runOneShotAgent } = await import('./executors/claudeStepRunner.js');
        return runOneShotAgent({ agentId, prompt, cwd: task.worktree_path });
      },
    },
  );

  return { summary: result.summary, earlyExitDecision: 'CONTINUE' };
}
```

NOTE: The exact variable names (`workflowRun`, `task`, `step`) and the `runOneShotAgent` helper depend on the existing code in `workflowStepExecutor.ts`. Adjust to match. If `runOneShotAgent` doesn't exist yet, implement a thin wrapper that calls the Claude Code CLI once with the prompt and captures stdout.

- [ ] **Step 3: Verify workflow step type 'SPLIT_TASK' is recognized in template schema**

```bash
grep -rn "workflow_template_steps\|executor_type" backend/src/repositories/workflowTemplateRepository.ts backend/src/db/schema.sql | head
```

If there's a CHECK constraint on executor_type column values, add `'SPLIT_TASK'` to the allowed list in `schema.sql`, then re-run migrate.

- [ ] **Step 4: Build & lint**

```bash
cd backend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add backend/src/services/workflow/workflowStepExecutor.ts backend/src/db/schema.sql
git commit -m "feat(workflow): dispatch SPLIT_TASK step type to executor"
```

---

## Task 11: TaskService.batchCreate (with tests)

**Files:**
- Modify: `backend/src/services/taskService.ts`
- Create: `backend/test/taskBatchCreate.test.ts`

- [ ] **Step 1: Write failing tests**

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { taskService } from '../src/services/taskService.js';
import { taskRepository } from '../src/repositories/taskRepository.js';
import { projectRepository } from '../src/repositories/projectRepository.js';

test('batchCreate assigns depends_on from indices', async () => {
  const project = await projectRepository.create({ name: 'test-proj-batch', env: {} } as any);
  const parent = await taskRepository.create({
    title: 'root', project_id: project.id, status: 'IN_PROGRESS', priority: 'MEDIUM', source: 'internal', depends_on: [],
  } as any);

  const result = await taskService.batchCreate({
    parent_task_id: parent.id,
    suggestions: [
      { title: 'A', description: '', template_id: null, linked_project_id: project.id, target_repo_url: null, depends_on_indices: [], enabled: true },
      { title: 'B', description: '', template_id: null, linked_project_id: project.id, target_repo_url: null, depends_on_indices: [0], enabled: true },
    ],
  });

  assert.equal(result.length, 2);
  assert.deepEqual(result[0]!.depends_on, []);
  assert.deepEqual(result[1]!.depends_on, [result[0]!.id]);
  assert.equal(result[0]!.status, 'TODO');
  assert.equal(result[1]!.status, 'WAITING');

  for (const t of result) await taskRepository.delete(t.id);
  await taskRepository.delete(parent.id);
  await projectRepository.delete(project.id);
});

test('batchCreate rejects disabled suggestions', async () => {
  const project = await projectRepository.create({ name: 'test-proj-disabled', env: {} } as any);
  const parent = await taskRepository.create({
    title: 'root', project_id: project.id, status: 'IN_PROGRESS', priority: 'MEDIUM', source: 'internal', depends_on: [],
  } as any);

  const result = await taskService.batchCreate({
    parent_task_id: parent.id,
    suggestions: [
      { title: 'A', description: '', template_id: null, linked_project_id: project.id, target_repo_url: null, depends_on_indices: [], enabled: false },
      { title: 'B', description: '', template_id: null, linked_project_id: project.id, target_repo_url: null, depends_on_indices: [], enabled: true },
    ],
  });

  assert.equal(result.length, 1);
  assert.equal(result[0]!.title, 'B');

  for (const t of result) await taskRepository.delete(t.id);
  await taskRepository.delete(parent.id);
  await projectRepository.delete(project.id);
});

test('batchCreate rejects cyclic dependencies', async () => {
  const project = await projectRepository.create({ name: 'test-proj-cycle', env: {} } as any);
  const parent = await taskRepository.create({
    title: 'root', project_id: project.id, status: 'IN_PROGRESS', priority: 'MEDIUM', source: 'internal', depends_on: [],
  } as any);

  await assert.rejects(async () => {
    await taskService.batchCreate({
      parent_task_id: parent.id,
      suggestions: [
        { title: 'A', description: '', template_id: null, linked_project_id: project.id, target_repo_url: null, depends_on_indices: [1], enabled: true },
        { title: 'B', description: '', template_id: null, linked_project_id: project.id, target_repo_url: null, depends_on_indices: [0], enabled: true },
      ],
    });
  }, /cycle/i);

  await taskRepository.delete(parent.id);
  await projectRepository.delete(project.id);
});
```

- [ ] **Step 2: Run tests to verify fail**

```bash
cd backend && npx tsx --test test/taskBatchCreate.test.ts
```

Expected: FAIL — `taskService.batchCreate is not a function`.

- [ ] **Step 3: Add batchCreate to taskService**

Open `backend/src/services/taskService.ts`. Find the exported `taskService` object (or class) and add:

```typescript
import { hasCycle } from './workflow/dependencyValidator.js';
import type { Suggestion } from '../types/entities.ts';

// ... existing imports and service ...

async function batchCreate(input: {
  parent_task_id: number;
  suggestions: Suggestion[];
}): Promise<TaskEntity[]> {
  const enabled = input.suggestions
    .map((s, originalIdx) => ({ s, originalIdx }))
    .filter(({ s }) => s.enabled);

  const indexMap = new Map<number, number>(); // original index → position in enabled list
  enabled.forEach((e, i) => indexMap.set(e.originalIdx, i));

  const remappedDeps = enabled.map(({ s }) =>
    s.depends_on_indices
      .map(oi => indexMap.get(oi))
      .filter((i): i is number => i !== undefined),
  );

  if (hasCycle(remappedDeps.map(d => ({ depends_on_indices: d })))) {
    throw new Error('suggestions contain a dependency cycle');
  }

  const parent = await taskRepository.findById(input.parent_task_id);
  if (!parent) throw new Error(`parent task ${input.parent_task_id} not found`);

  const created: TaskEntity[] = [];
  for (let i = 0; i < enabled.length; i++) {
    const { s } = enabled[i]!;
    const deps = remappedDeps[i]!.map(pos => created[pos]?.id).filter((id): id is number => id != null);

    const task = await taskRepository.create({
      title: s.title,
      description: s.description,
      project_id: s.linked_project_id ?? parent.project_id,
      status: deps.length === 0 ? 'TODO' : 'WAITING',
      priority: 'MEDIUM',
      source: 'internal',
      parent_task_id: input.parent_task_id,
      depends_on: deps,
      target_repo_url: s.linked_project_id == null ? s.target_repo_url : null,
      auto_execute_template_id: s.template_id,
      labels: [],
    } as any);

    created.push(task);
  }

  // Caller (splitSuggestionService.confirm) will trigger auto-start for TODO tasks.
  return created;
}

// Add `batchCreate` to the exported service object.
```

In the exported service object (likely `export const taskService = { ... }`), add `batchCreate,`.

- [ ] **Step 4: Run tests to verify pass**

```bash
cd backend && npx tsx --test test/taskBatchCreate.test.ts
```

Expected: PASS — all 3 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/services/taskService.ts backend/test/taskBatchCreate.test.ts
git commit -m "feat(task): batchCreate with cycle detection and dependency remapping"
```

---

## Task 12: Task status event listener — auto-start & cascade-fail

**Files:**
- Modify: `backend/src/services/taskService.ts`
- Create: `backend/test/taskDependencyTrigger.test.ts`

When a task transitions to `DONE`, find WAITING dependents whose deps are all done and move them to `TODO` (or RUNNING if auto-execute). When task transitions to `FAILED`/`BLOCKED`, cascade-fail its dependents.

- [ ] **Step 1: Write failing tests**

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { taskService } from '../src/services/taskService.js';
import { taskRepository } from '../src/repositories/taskRepository.js';
import { projectRepository } from '../src/repositories/projectRepository.js';

test('onTaskStatusChange promotes WAITING dependent to TODO when all deps DONE', async () => {
  const project = await projectRepository.create({ name: 'test-deps-promote', env: {} } as any);
  const a = await taskRepository.create({ title: 'A', project_id: project.id, status: 'DONE', priority: 'MEDIUM', source: 'internal', depends_on: [] } as any);
  const b = await taskRepository.create({ title: 'B', project_id: project.id, status: 'WAITING', priority: 'MEDIUM', source: 'internal', depends_on: [a.id] } as any);

  await taskService.onTaskStatusChange(a.id, 'DONE');

  const bAfter = await taskRepository.findById(b.id);
  assert.equal(bAfter!.status, 'TODO');

  await taskRepository.delete(b.id);
  await taskRepository.delete(a.id);
  await projectRepository.delete(project.id);
});

test('onTaskStatusChange keeps WAITING when other deps still pending', async () => {
  const project = await projectRepository.create({ name: 'test-deps-partial', env: {} } as any);
  const a = await taskRepository.create({ title: 'A', project_id: project.id, status: 'DONE', priority: 'MEDIUM', source: 'internal', depends_on: [] } as any);
  const b = await taskRepository.create({ title: 'B', project_id: project.id, status: 'IN_PROGRESS', priority: 'MEDIUM', source: 'internal', depends_on: [] } as any);
  const c = await taskRepository.create({ title: 'C', project_id: project.id, status: 'WAITING', priority: 'MEDIUM', source: 'internal', depends_on: [a.id, b.id] } as any);

  await taskService.onTaskStatusChange(a.id, 'DONE');

  const cAfter = await taskRepository.findById(c.id);
  assert.equal(cAfter!.status, 'WAITING');

  await taskRepository.delete(c.id);
  await taskRepository.delete(b.id);
  await taskRepository.delete(a.id);
  await projectRepository.delete(project.id);
});

test('onTaskStatusChange cascade-fails dependents when upstream BLOCKED', async () => {
  const project = await projectRepository.create({ name: 'test-cascade-fail', env: {} } as any);
  const a = await taskRepository.create({ title: 'A', project_id: project.id, status: 'BLOCKED', priority: 'MEDIUM', source: 'internal', depends_on: [] } as any);
  const b = await taskRepository.create({ title: 'B', project_id: project.id, status: 'WAITING', priority: 'MEDIUM', source: 'internal', depends_on: [a.id] } as any);
  const c = await taskRepository.create({ title: 'C', project_id: project.id, status: 'WAITING', priority: 'MEDIUM', source: 'internal', depends_on: [b.id] } as any);

  await taskService.onTaskStatusChange(a.id, 'BLOCKED');

  const bAfter = await taskRepository.findById(b.id);
  const cAfter = await taskRepository.findById(c.id);
  assert.equal(bAfter!.status, 'BLOCKED');
  assert.equal(cAfter!.status, 'BLOCKED');

  await taskRepository.delete(c.id);
  await taskRepository.delete(b.id);
  await taskRepository.delete(a.id);
  await projectRepository.delete(project.id);
});
```

- [ ] **Step 2: Run tests to verify fail**

```bash
cd backend && npx tsx --test test/taskDependencyTrigger.test.ts
```

Expected: FAIL — `onTaskStatusChange is not a function`.

- [ ] **Step 3: Implement onTaskStatusChange**

Add to `taskService.ts`:

```typescript
async function onTaskStatusChange(taskId: number, newStatus: string): Promise<void> {
  if (newStatus === 'DONE') {
    const dependents = await taskRepository.findDependents(taskId);
    for (const dep of dependents) {
      if (dep.status !== 'WAITING') continue;
      const upstreams = await Promise.all(dep.depends_on.map(id => taskRepository.findById(id)));
      const allDone = upstreams.every(u => u?.status === 'DONE');
      if (allDone) {
        await taskRepository.update(dep.id, { status: 'TODO' } as any);
      }
    }
  } else if (newStatus === 'BLOCKED' || newStatus === 'CANCELLED') {
    const dependents = await taskRepository.findDependents(taskId);
    for (const dep of dependents) {
      if (dep.status === 'DONE' || dep.status === 'BLOCKED' || dep.status === 'CANCELLED') continue;
      await taskRepository.update(dep.id, { status: 'BLOCKED' } as any);
      await onTaskStatusChange(dep.id, 'BLOCKED'); // recurse
    }
  }
}
```

Add `onTaskStatusChange` to the exported service object.

Also update the existing status-change pathway (e.g., `taskService.updateStatus` or the status PATCH route) to call `onTaskStatusChange(id, newStatus)` after committing the status update. Find it:

```bash
grep -n "status" backend/src/services/taskService.ts | head -20
```

- [ ] **Step 4: Run tests to verify pass**

```bash
cd backend && npx tsx --test test/taskDependencyTrigger.test.ts
```

Expected: PASS — all 3 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/services/taskService.ts backend/test/taskDependencyTrigger.test.ts
git commit -m "feat(task): dependency auto-trigger and cascade failure"
```

---

## Task 13: TaskService.getPipeline — cross-project DAG

**Files:**
- Modify: `backend/src/services/taskService.ts`

Returns the full DAG from any node by walking to root via `parent_task_id`, then collecting all descendants.

- [ ] **Step 1: Add getPipeline method**

```typescript
async function getPipeline(taskId: number): Promise<{ root: TaskEntity; nodes: TaskEntity[] }> {
  // Walk up to root
  let current = await taskRepository.findById(taskId);
  if (!current) throw new Error(`task ${taskId} not found`);
  while (current.parent_task_id != null) {
    const parent = await taskRepository.findById(current.parent_task_id);
    if (!parent) break;
    current = parent;
  }
  const root = current;

  // BFS descendants
  const nodes: TaskEntity[] = [root];
  const queue = [root.id];
  const seen = new Set<number>([root.id]);
  while (queue.length > 0) {
    const id = queue.shift()!;
    const children = await taskRepository.findChildren(id);
    for (const c of children) {
      if (seen.has(c.id)) continue;
      seen.add(c.id);
      nodes.push(c);
      queue.push(c.id);
    }
  }

  return { root, nodes };
}
```

Export `getPipeline` from the service.

- [ ] **Step 2: Build check**

```bash
cd backend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/services/taskService.ts
git commit -m "feat(task): getPipeline for cross-project DAG traversal"
```

---

## Task 14: SplitSuggestionService — update, confirm, dismiss, regenerate

**Files:**
- Create: `backend/src/services/splitSuggestionService.ts`

- [ ] **Step 1: Create the service file**

```typescript
import { splitSuggestionRepository } from '../repositories/splitSuggestionRepository.js';
import { taskRepository } from '../repositories/taskRepository.js';
import { taskService } from './taskService.js';
import type { Suggestion, SplitSuggestionEntity } from '../types/entities.ts';

async function getByTask(taskId: number): Promise<SplitSuggestionEntity[]> {
  return splitSuggestionRepository.findByParentTask(taskId);
}

async function getPendingByTask(taskId: number): Promise<SplitSuggestionEntity | null> {
  return splitSuggestionRepository.findPendingByParentTask(taskId);
}

async function updateSuggestions(
  id: number,
  suggestions: Suggestion[],
): Promise<SplitSuggestionEntity> {
  const existing = await splitSuggestionRepository.findById(id);
  if (!existing) throw new Error(`split suggestion ${id} not found`);
  if (existing.status !== 'PENDING') {
    throw new Error(`cannot edit suggestion in status ${existing.status}`);
  }
  await splitSuggestionRepository.update(id, { suggestions } as any);
  return (await splitSuggestionRepository.findById(id))!;
}

async function confirm(id: number): Promise<{ tasks: number[]; suggestion: SplitSuggestionEntity }> {
  const existing = await splitSuggestionRepository.findById(id);
  if (!existing) throw new Error(`split suggestion ${id} not found`);
  if (existing.status !== 'PENDING') {
    throw new Error(`cannot confirm suggestion in status ${existing.status}`);
  }

  const tasks = await taskService.batchCreate({
    parent_task_id: existing.parent_task_id,
    suggestions: existing.suggestions,
  });

  await splitSuggestionRepository.update(id, {
    status: 'CONFIRMED',
    confirmed_at: new Date().toISOString(),
  } as any);

  const updated = (await splitSuggestionRepository.findById(id))!;
  return { tasks: tasks.map(t => t.id), suggestion: updated };
}

async function dismiss(id: number): Promise<SplitSuggestionEntity> {
  const existing = await splitSuggestionRepository.findById(id);
  if (!existing) throw new Error(`split suggestion ${id} not found`);
  if (existing.status !== 'PENDING') {
    throw new Error(`cannot dismiss suggestion in status ${existing.status}`);
  }
  await splitSuggestionRepository.update(id, { status: 'DISMISSED' } as any);
  return (await splitSuggestionRepository.findById(id))!;
}

export const splitSuggestionService = {
  getByTask,
  getPendingByTask,
  updateSuggestions,
  confirm,
  dismiss,
};
```

- [ ] **Step 2: Build check**

```bash
cd backend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/services/splitSuggestionService.ts
git commit -m "feat(service): split suggestion lifecycle (update/confirm/dismiss)"
```

---

## Task 15: Routes — split suggestions

**Files:**
- Create: `backend/src/routes/splitSuggestions.ts`
- Modify: `backend/src/routes/index.ts`

- [ ] **Step 1: Create route handlers**

```typescript
import type { FastifyPluginAsync } from 'fastify';
import { z } from 'zod';
import { splitSuggestionService } from '../services/splitSuggestionService.js';
import { ok, fail } from '../utils/response.js';

const suggestionSchema = z.object({
  title: z.string(),
  description: z.string(),
  template_id: z.string().nullable(),
  linked_project_id: z.number().nullable(),
  target_repo_url: z.string().nullable(),
  depends_on_indices: z.array(z.number()),
  enabled: z.boolean(),
});

const splitSuggestionsRoutes: FastifyPluginAsync = async (fastify) => {
  fastify.get<{ Params: { taskId: string } }>(
    '/api/tasks/:taskId/split-suggestions',
    async (req, reply) => {
      const taskId = Number(req.params.taskId);
      const list = await splitSuggestionService.getByTask(taskId);
      return reply.send(ok(list));
    },
  );

  fastify.patch<{ Params: { id: string }; Body: { suggestions: unknown[] } }>(
    '/api/split-suggestions/:id',
    async (req, reply) => {
      const id = Number(req.params.id);
      const parsed = z.array(suggestionSchema).safeParse(req.body?.suggestions);
      if (!parsed.success) {
        return reply.status(400).send(fail(`invalid suggestions: ${parsed.error.message}`));
      }
      try {
        const updated = await splitSuggestionService.updateSuggestions(id, parsed.data);
        return reply.send(ok(updated));
      } catch (e) {
        return reply.status(400).send(fail((e as Error).message));
      }
    },
  );

  fastify.post<{ Params: { id: string } }>(
    '/api/split-suggestions/:id/confirm',
    async (req, reply) => {
      try {
        const result = await splitSuggestionService.confirm(Number(req.params.id));
        return reply.send(ok(result));
      } catch (e) {
        return reply.status(400).send(fail((e as Error).message));
      }
    },
  );

  fastify.post<{ Params: { id: string } }>(
    '/api/split-suggestions/:id/dismiss',
    async (req, reply) => {
      try {
        const result = await splitSuggestionService.dismiss(Number(req.params.id));
        return reply.send(ok(result));
      } catch (e) {
        return reply.status(400).send(fail((e as Error).message));
      }
    },
  );
};

export default splitSuggestionsRoutes;
```

- [ ] **Step 2: Register routes in routes/index.ts**

Open `backend/src/routes/index.ts` and add:

```typescript
import splitSuggestionsRoutes from './splitSuggestions.js';
// ... inside the register function, alongside other routes:
await fastify.register(splitSuggestionsRoutes);
```

- [ ] **Step 3: Smoke test via curl**

Run `./start.sh` in a separate terminal, then:

```bash
curl http://localhost:8000/api/tasks/1/split-suggestions
```

Expected: `{"success":true,"data":[]}` (empty list if no suggestions yet).

- [ ] **Step 4: Commit**

```bash
git add backend/src/routes/splitSuggestions.ts backend/src/routes/index.ts
git commit -m "feat(routes): split suggestions CRUD + confirm/dismiss"
```

---

## Task 16: Routes — tasks: batch-create, pipeline, dependents, regenerate-split

**Files:**
- Modify: `backend/src/routes/tasks.ts`

- [ ] **Step 1: Add endpoints**

Inside the tasks route plugin, add:

```typescript
fastify.post<{ Body: { parent_task_id: number; suggestions: any[] } }>(
  '/api/tasks/batch-create',
  async (req, reply) => {
    try {
      const tasks = await taskService.batchCreate(req.body);
      return reply.send(ok(tasks));
    } catch (e) {
      return reply.status(400).send(fail((e as Error).message));
    }
  },
);

fastify.get<{ Params: { id: string } }>(
  '/api/tasks/:id/dependents',
  async (req, reply) => {
    const dependents = await taskRepository.findDependents(Number(req.params.id));
    return reply.send(ok(dependents));
  },
);

fastify.get<{ Params: { id: string } }>(
  '/api/tasks/:id/pipeline',
  async (req, reply) => {
    try {
      const pipeline = await taskService.getPipeline(Number(req.params.id));
      return reply.send(ok(pipeline));
    } catch (e) {
      return reply.status(404).send(fail((e as Error).message));
    }
  },
);

fastify.post<{ Params: { id: string } }>(
  '/api/tasks/:id/regenerate-split',
  async (req, reply) => {
    // Dismiss any existing PENDING suggestion, then re-run the SPLIT_TASK step.
    const existing = await splitSuggestionService.getPendingByTask(Number(req.params.id));
    if (existing) await splitSuggestionService.dismiss(existing.id);
    // Trigger workflow to re-execute its SPLIT_TASK step. Delegate to workflowService:
    await workflowService.retryLastSplitStep(Number(req.params.id));
    return reply.send(ok({ regenerated: true }));
  },
);
```

Add imports at top of file:

```typescript
import { taskRepository } from '../repositories/taskRepository.js';
import { splitSuggestionService } from '../services/splitSuggestionService.js';
import { workflowService } from './workflowService.js'; // adjust to actual path
```

Note: `workflowService.retryLastSplitStep` may need a thin wrapper around existing workflow retry logic — inspect `backend/src/services/workflow/workflowService.ts` and add a method that finds the most recent SPLIT_TASK step in the task's workflow run and re-executes just that step.

- [ ] **Step 2: Build check**

```bash
cd backend && npx tsc --noEmit
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/routes/tasks.ts backend/src/services/workflow/workflowService.ts
git commit -m "feat(routes): tasks batch-create, pipeline, dependents, regenerate-split"
```

---

## Task 17: Frontend API clients

**Files:**
- Create: `frontend/src/api/splitSuggestions.js`
- Modify: `frontend/src/api/task.js`

- [ ] **Step 1: Create split-suggestions client**

```javascript
import { api } from './index.js'

export const splitSuggestionsApi = {
  listByTask: (taskId) => api.get(`/api/tasks/${taskId}/split-suggestions`),
  update: (id, suggestions) => api.patch(`/api/split-suggestions/${id}`, { suggestions }),
  confirm: (id) => api.post(`/api/split-suggestions/${id}/confirm`),
  dismiss: (id) => api.post(`/api/split-suggestions/${id}/dismiss`),
}
```

- [ ] **Step 2: Extend task API client**

Add to `frontend/src/api/task.js`:

```javascript
batchCreate: (payload) => api.post('/api/tasks/batch-create', payload),
getPipeline: (taskId) => api.get(`/api/tasks/${taskId}/pipeline`),
getDependents: (taskId) => api.get(`/api/tasks/${taskId}/dependents`),
regenerateSplit: (taskId) => api.post(`/api/tasks/${taskId}/regenerate-split`),
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/splitSuggestions.js frontend/src/api/task.js
git commit -m "feat(api): frontend clients for split suggestions and pipeline"
```

---

## Task 18: Pinia store for split suggestions with debounced PATCH

**Files:**
- Create: `frontend/src/stores/splitSuggestions.js`

- [ ] **Step 1: Create the store**

```javascript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { splitSuggestionsApi } from '../api/splitSuggestions.js'

function debounce(fn, wait) {
  let timer
  return (...args) => {
    clearTimeout(timer)
    timer = setTimeout(() => fn(...args), wait)
  }
}

export const useSplitSuggestionsStore = defineStore('splitSuggestions', () => {
  const pendingByTask = ref(new Map())
  const loading = ref(false)

  async function load(taskId) {
    loading.value = true
    try {
      const resp = await splitSuggestionsApi.listByTask(taskId)
      if (resp.success) {
        const pending = resp.data.find(s => s.status === 'PENDING')
        if (pending) pendingByTask.value.set(taskId, pending)
        else pendingByTask.value.delete(taskId)
      }
    } finally {
      loading.value = false
    }
  }

  const debouncedPatch = debounce(async (id, suggestions) => {
    await splitSuggestionsApi.update(id, suggestions)
  }, 500)

  function updateSuggestions(taskId, suggestions) {
    const record = pendingByTask.value.get(taskId)
    if (!record) return
    record.suggestions = suggestions
    pendingByTask.value.set(taskId, { ...record })
    debouncedPatch(record.id, suggestions)
  }

  async function confirm(taskId) {
    const record = pendingByTask.value.get(taskId)
    if (!record) return null
    const resp = await splitSuggestionsApi.confirm(record.id)
    if (resp.success) pendingByTask.value.delete(taskId)
    return resp
  }

  async function dismiss(taskId) {
    const record = pendingByTask.value.get(taskId)
    if (!record) return null
    const resp = await splitSuggestionsApi.dismiss(record.id)
    if (resp.success) pendingByTask.value.delete(taskId)
    return resp
  }

  return { pendingByTask, loading, load, updateSuggestions, confirm, dismiss }
})
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/stores/splitSuggestions.js
git commit -m "feat(store): split suggestions store with debounced PATCH"
```

---

## Task 19: Extract AiSplitCard component

**Files:**
- Create: `frontend/src/components/workspace/AiSplitCard.vue`
- Modify: `frontend/src/views/WorkspaceView.vue` (replace inline card with component)

- [ ] **Step 1: Create the component**

Move the AI split card markup, script block, and styles from `WorkspaceView.vue` into a new SFC that accepts props and emits events:

```vue
<template>
  <div class="ai-split-card" v-if="suggestion">
    <div class="split-card-header" @click="expanded = !expanded">
      <span class="split-card-title">AI 拆分建议</span>
      <span class="split-card-count">{{ suggestion.suggestions.length }} 个子任务</span>
      <span class="split-toggle" :class="{ rotated: expanded }">›</span>
    </div>
    <div v-show="expanded" class="split-card-body">
      <div class="split-suggestions-list">
        <div v-for="(s, i) in suggestion.suggestions" :key="i" class="split-suggestion-item">
          <input type="checkbox" v-model="s.enabled" @change="emitUpdate" />
          <div class="suggestion-main">
            <input class="suggestion-title" v-model="s.title" @input="emitUpdate" />
            <div class="suggestion-desc">{{ s.description }}</div>
            <div class="suggestion-meta">
              <span class="repo">{{ s.target_repo_url || '当前项目' }}</span>
              <span class="deps" :class="{ 'has-deps': s.depends_on_indices.length > 0 }">
                依赖: {{ s.depends_on_indices.length ? s.depends_on_indices.map(i => suggestion.suggestions[i]?.title).join(', ') : '无' }}
              </span>
            </div>
          </div>
          <button class="btn-delete" @click="removeItem(i)">删除</button>
        </div>
      </div>
      <div class="split-footer">
        <button class="btn-add" @click="addItem">+ 添加任务</button>
        <div class="split-actions">
          <button class="btn-cancel" @click="$emit('dismiss')">取消</button>
          <button class="btn-confirm" @click="$emit('confirm')">确认创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  suggestion: { type: Object, default: null },
})

const emit = defineEmits(['update', 'confirm', 'dismiss'])

const expanded = ref(true)

function emitUpdate() {
  emit('update', props.suggestion.suggestions)
}

function addItem() {
  props.suggestion.suggestions.push({
    title: '新任务',
    description: '',
    template_id: null,
    linked_project_id: null,
    target_repo_url: null,
    depends_on_indices: [],
    enabled: true,
  })
  emitUpdate()
}

function removeItem(i) {
  props.suggestion.suggestions.splice(i, 1)
  emitUpdate()
}
</script>

<style scoped>
/* Copy the .ai-split-card, .split-card-header, etc. styles from WorkspaceView.vue */
</style>
```

- [ ] **Step 2: Wire the component into WorkspaceView.vue**

In `WorkspaceView.vue`:
- Remove the inline AI split card markup.
- Import `AiSplitCard`, `useSplitSuggestionsStore`.
- Add data loading: `onMounted(() => store.load(selectedTask.value.id))`, and reload on task change.
- Replace with:

```vue
<AiSplitCard
  :suggestion="store.pendingByTask.get(selectedTask.id)"
  @update="(s) => store.updateSuggestions(selectedTask.id, s)"
  @confirm="onConfirmSplit"
  @dismiss="onDismissSplit"
/>
```

Where `onConfirmSplit` and `onDismissSplit` call the store methods and reload the task list.

- [ ] **Step 3: Run frontend dev server and verify UI doesn't regress**

```bash
cd frontend && npm run dev
```

Open `http://localhost:3000/workspace`. Expected: page renders; AI split card section renders the same as before (mocked data still shows during development — the actual suggestion data will appear once end-to-end is wired).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/workspace/AiSplitCard.vue frontend/src/views/WorkspaceView.vue
git commit -m "refactor(workspace): extract AiSplitCard component wired to store"
```

---

## Task 20: Wire real pipeline + task data into WorkspaceView

**Files:**
- Modify: `frontend/src/views/WorkspaceView.vue`

- [ ] **Step 1: Replace mock tasks with live fetch**

In `<script setup>`:

```javascript
import { ref, onMounted, watch } from 'vue'
import { taskApi } from '../api/task.js'

const tasks = ref([])
const selectedTask = ref(null)
const pipeline = ref({ root: null, nodes: [] })

async function loadTasks() {
  const resp = await taskApi.list() // existing endpoint
  if (resp.success) tasks.value = resp.data
  if (tasks.value.length && !selectedTask.value) {
    selectedTask.value = tasks.value[0]
  }
}

async function loadPipeline(taskId) {
  const resp = await taskApi.getPipeline(taskId)
  if (resp.success) pipeline.value = resp.data
}

onMounted(loadTasks)
watch(selectedTask, async (t) => {
  if (t) {
    await loadPipeline(t.id)
    await splitStore.load(t.id)
  }
})
```

- [ ] **Step 2: Build DAG rendering from pipeline.nodes**

Replace the hardcoded DAG node list with a computed that topologically sorts `pipeline.value.nodes` by `depends_on`. Render nodes in layers (depth from root). Highlight `selectedTask.id`.

A minimal layer computation:

```javascript
const dagLayers = computed(() => {
  const nodes = pipeline.value.nodes
  if (!nodes.length) return []
  const depth = new Map()
  for (const n of nodes) depth.set(n.id, 0)
  let changed = true
  while (changed) {
    changed = false
    for (const n of nodes) {
      for (const depId of n.depends_on) {
        const d = (depth.get(depId) ?? 0) + 1
        if (d > (depth.get(n.id) ?? 0)) {
          depth.set(n.id, d)
          changed = true
        }
      }
    }
  }
  const layers = []
  for (const n of nodes) {
    const d = depth.get(n.id) ?? 0
    if (!layers[d]) layers[d] = []
    layers[d].push(n)
  }
  return layers
})
```

Then in template, loop `dagLayers` horizontally and render `.dag-node` per layer.

- [ ] **Step 3: Verify in browser**

Start backend + frontend (`./start.sh`). Create a test task via the kanban page. Navigate to `/workspace`. Expected: task list shows real tasks; DAG shows a single node for the task (since no children yet); selecting a different task reloads the pipeline.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/WorkspaceView.vue
git commit -m "feat(workspace): load real tasks and pipeline data"
```

---

## Task 21: End-to-end manual verification

- [ ] **Step 1: Start services**

```bash
./start.sh
```

Verify:
- Backend: `curl http://localhost:8000/health` → `{"success":true}`
- Frontend: `http://localhost:3000` loads

- [ ] **Step 2: Set up test scenario**

In UI:
1. Create project A "知识仓" (git_url: `git@github.com:test/knowledge.git`).
2. Create project B "用户服务" (git_url: `git@github.com:test/user-svc.git`), set its default_template_id to an existing backend template.
3. Create a workflow template containing a SPLIT_TASK step (use the agent you normally use for reasoning).
4. Attach this template to project A.
5. Create a task in project A: "设计用户中心架构".

- [ ] **Step 3: Run the workflow**

Start the task's workflow. Observe:
- Steps run in order.
- SPLIT_TASK step invokes the Agent and produces a JSON block.
- `split_suggestions` table gets a PENDING row (`select * from split_suggestions;` in the sqlite shell).

- [ ] **Step 4: Review via Workspace**

Open `/workspace`, select the task. Expected:
- AI split card appears with AI-generated suggestions.
- Edit a title; within ~500ms, DB row reflects the change (refresh page and verify persistence).
- Click "确认创建". Expected: child tasks appear in the kanban, WAITING ones stay WAITING, no-dependency ones go to TODO.

- [ ] **Step 5: Exercise dependency trigger**

Manually advance a parent task to DONE (via kanban status dropdown). Expected: its WAITING dependents flip to TODO automatically. Refresh `/workspace` pipeline — reflected immediately.

- [ ] **Step 6: Exercise failure cascade**

Move an upstream task to BLOCKED. Expected: its downstream WAITING tasks flip to BLOCKED.

- [ ] **Step 7: Exercise retry path**

Temporarily break the Agent config so it produces no JSON block. Run a new split task. Expected: SPLIT_TASK step marked FAILED; UI surfaces an error. Click "重新生成拆分建议". Expected: step re-runs.

- [ ] **Step 8: Run all tests**

```bash
cd backend && npm test
cd ../frontend && npm run test:run
```

Expected: all tests pass.

- [ ] **Step 9: Final commit + push**

```bash
git status  # confirm clean
git push origin demo-workspace-view
```

---

## Limitations (from spec)

- DAG cycles rejected at batch-create time (not at runtime dependency modification — runtime modification unsupported)
- No conditional branches ("A succeeds → B, A fails → C")
- Once confirmed, child tasks can't be rolled back into a PENDING suggestion — user must delete tasks manually

## Verification Summary

- Unit tests: parseJsonBlock, projectMatcher, dependencyValidator, taskService.batchCreate, taskService.onTaskStatusChange
- Integration smoke tests: curl endpoints; dev server UI; end-to-end workflow described in Task 21
- No new environment variables or config changes required
