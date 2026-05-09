# External Requirement Number in Workflow Prompt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display the task's `external_id` (e.g., "RR-12345") in the workflow step prompt so AI agents can see and reference external requirement numbers.

**Architecture:** Pass `task.external_id` through the existing Mastra workflow state (`initialState`/`inputData`), extend Zod schemas, and conditionally render it in the assembled prompt string.

**Tech Stack:** TypeScript, Zod, Mastra workflows, Node test runner

---

### Task 1: Extend Mastra schemas with `taskExternalId`

**Files:**
- Modify: `backend/src/services/workflow/workflows.ts:12-17` (sharedStateSchema) and `backend/src/services/workflow/workflows.ts:25-31` (firstStepInputSchema)

- [ ] **Step 1: Add `taskExternalId` to `sharedStateSchema`**

```typescript
const sharedStateSchema = z.object({
  taskTitle: z.string(),
  taskDescription: z.string(),
  worktreePath: z.string(),
  projectEnv: z.record(z.string()).optional(),
  taskExternalId: z.string().optional(),
});
```

- [ ] **Step 2: Add `taskExternalId` to `firstStepInputSchema`**

```typescript
const firstStepInputSchema = z.object({
  taskId: z.number(),
  taskTitle: z.string(),
  taskDescription: z.string(),
  worktreePath: z.string(),
  projectEnv: z.record(z.string()).optional(),
  taskExternalId: z.string().optional(),
});
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/services/workflow/workflows.ts
git commit -m "feat: add taskExternalId to workflow schemas for external requirement tracking"
```

---

### Task 2: Pass `external_id` from task to Mastra workflow state

**Files:**
- Modify: `backend/src/services/workflow/workflowService.ts:284-298` (mastraRun.startAsync call)

- [ ] **Step 1: Add `taskExternalId` to `inputData` and `initialState` in `executeWorkflow`**

Current code at lines 284-298:

```typescript
await mastraRun.startAsync({
  inputData: {
    taskId: task.id,
    taskTitle: task.title || 'Untitled Task',
    taskDescription: task.description || '',
    worktreePath: task.execution_path,
    projectEnv: task.project_env,
  },
  initialState: {
    taskTitle: task.title || 'Untitled Task',
    taskDescription: task.description || '',
    worktreePath: task.execution_path,
    projectEnv: task.project_env,
  },
});
```

Change to:

```typescript
await mastraRun.startAsync({
  inputData: {
    taskId: task.id,
    taskTitle: task.title || 'Untitled Task',
    taskDescription: task.description || '',
    worktreePath: task.execution_path,
    projectEnv: task.project_env,
    taskExternalId: task.external_id || '',
  },
  initialState: {
    taskTitle: task.title || 'Untitled Task',
    taskDescription: task.description || '',
    worktreePath: task.execution_path,
    projectEnv: task.project_env,
    taskExternalId: task.external_id || '',
  },
});
```

Note: The `task` parameter is typed as `WorkflowTaskRecord & { execution_path: string; project_env: Record<string, string> }`. The underlying `TaskEntity` from `taskRepo.findById()` has `external_id?: string | null`, so the property is available at runtime. The TypeScript type will narrow correctly since `TaskEntity` is what the repo returns.

- [ ] **Step 2: Commit**

```bash
git add backend/src/services/workflow/workflowService.ts
git commit -m "feat: pass task external_id into Mastra workflow state"
```

---

### Task 3: Write test for prompt assembler with external_id

**Files:**
- Create: `backend/test/workflowPromptAssembler.test.ts`
- Reference: `backend/src/services/workflow/workflowPromptAssembler.ts`

- [ ] **Step 1: Write the test file**

```typescript
import * as test from 'node:test';
import * as assert from 'node:assert/strict';
import { assembleWorkflowPrompt } from '../src/services/workflow/workflowPromptAssembler.js';

test('assembleWorkflowPrompt includes external_id when present', async () => {
  const prompt = await assembleWorkflowPrompt({
    step: { name: '开发', instructionPrompt: '实现这个功能' },
    state: {
      taskTitle: '用户登录',
      taskDescription: '需要实现用户登录功能',
      taskExternalId: 'RR-12345',
    },
    inputData: {},
    upstreamStepIds: [],
    isFirstStep: true,
  });

  assert.ok(prompt.includes('外部需求单号：RR-12345'), 'prompt should include external_id');
  // Verify it appears after taskTitle and before taskDescription
  const titleIdx = prompt.indexOf('原始需求标题');
  const externalIdx = prompt.indexOf('外部需求单号');
  const descIdx = prompt.indexOf('原始需求内容');
  assert.ok(titleIdx < externalIdx && externalIdx < descIdx, 'external_id should be between title and description');
});

test('assembleWorkflowPrompt omits external_id section when empty', async () => {
  const prompt = await assembleWorkflowPrompt({
    step: { name: '开发', instructionPrompt: '实现这个功能' },
    state: {
      taskTitle: '用户登录',
      taskDescription: '需要实现用户登录功能',
      taskExternalId: '',
    },
    inputData: {},
    upstreamStepIds: [],
    isFirstStep: true,
  });

  assert.ok(!prompt.includes('外部需求单号'), 'prompt should not include external_id section when empty');
});

test('assembleWorkflowPrompt omits external_id section when undefined', async () => {
  const prompt = await assembleWorkflowPrompt({
    step: { name: '开发', instructionPrompt: '实现这个功能' },
    state: {
      taskTitle: '用户登录',
      taskDescription: '需要实现用户登录功能',
    },
    inputData: {},
    upstreamStepIds: [],
    isFirstStep: true,
  });

  assert.ok(!prompt.includes('外部需求单号'), 'prompt should not include external_id section when undefined');
});
```

- [ ] **Step 2: Run tests to verify they FAIL** (implementation not yet done)

```bash
cd backend && npm test -- --test-name-pattern "external_id"
```

Expected: Tests fail with assertion errors because `外部需求单号` is not yet in the prompt output.

- [ ] **Step 3: Commit**

```bash
git add backend/test/workflowPromptAssembler.test.ts
git commit -m "test: add tests for external_id in workflow prompt assembler"
```

---

### Task 4: Implement external_id rendering in prompt assembler

**Files:**
- Modify: `backend/src/services/workflow/workflowPromptAssembler.ts`

- [ ] **Step 1: Update the `state` type in the function signature**

Change the `state` parameter type from:

```typescript
state: { taskTitle: string; taskDescription: string };
```

To:

```typescript
state: { taskTitle: string; taskDescription: string; taskExternalId?: string };
```

- [ ] **Step 2: Add external_id section to the prompt array**

In the return array at line 121-136, insert a conditional line after the "原始需求标题" line (line 123). Change:

```typescript
return [
  `当前步骤：${step.name}`,
  `原始需求标题：\n${state.taskTitle}`,
  `原始需求内容：\n${state.taskDescription}`,
  upstreamSummaries.length > 0
    ? ['上游步骤摘要：', ...upstreamSummaries.map((item) => `- ${item.stepId}:\n${item.summary}`)].join('\n')
    : '',
  agentIdentitySection,
  repoAnalysisContext,
  `本步骤要求：\n${renderedInstruction}`,
  '执行完成后，只输出最后结果总结。',
  summaryInstruction,
  canEarlyExit
    ? '智能终止说明：如果你认为原始需求已经完成或无法继续执行后续步骤，请在总结末尾以JSON格式输出终止决定。\n\n三个选项的含义：\n- CONTINUE：需要继续执行后续步骤（默认选项，如果不输出JSON则视为CONTINUE）。\n- SUCCESS_EXIT：原始需求已达成，后续所有步骤无需执行。这不是指当前步骤执行成功，而是指可以根据原始需求的标题和内容，判断整个任务可以提前成功结束。\n- FAIL_EXIT：无法继续执行后续步骤（遇到无法解决的问题），任务需要终止。\n\nJSON格式示例：\n{"decision": "CONTINUE"}\n或\n{"decision": "SUCCESS_EXIT", "reason": "根据原始需求的标题和内容，目标已达成，因为..."}\n或\n{"decision": "FAIL_EXIT", "reason": "无法继续，因为..."}'
    : '',
].filter(Boolean).join('\n\n').replaceAll('\n', '\\n');
```

To:

```typescript
return [
  `当前步骤：${step.name}`,
  `原始需求标题：\n${state.taskTitle}`,
  state.taskExternalId ? `外部需求单号：${state.taskExternalId}` : '',
  `原始需求内容：\n${state.taskDescription}`,
  upstreamSummaries.length > 0
    ? ['上游步骤摘要：', ...upstreamSummaries.map((item) => `- ${item.stepId}:\n${item.summary}`)].join('\n')
    : '',
  agentIdentitySection,
  repoAnalysisContext,
  `本步骤要求：\n${renderedInstruction}`,
  '执行完成后，只输出最后结果总结。',
  summaryInstruction,
  canEarlyExit
    ? '智能终止说明：如果你认为原始需求已经完成或无法继续执行后续步骤，请在总结末尾以JSON格式输出终止决定。\n\n三个选项的含义：\n- CONTINUE：需要继续执行后续步骤（默认选项，如果不输出JSON则视为CONTINUE）。\n- SUCCESS_EXIT：原始需求已达成，后续所有步骤无需执行。这不是指当前步骤执行成功，而是指可以根据原始需求的标题和内容，判断整个任务可以提前成功结束。\n- FAIL_EXIT：无法继续执行后续步骤（遇到无法解决的问题），任务需要终止。\n\nJSON格式示例：\n{"decision": "CONTINUE"}\n或\n{"decision": "SUCCESS_EXIT", "reason": "根据原始需求的标题和内容，目标已达成，因为..."}\n或\n{"decision": "FAIL_EXIT", "reason": "无法继续，因为..."}'
    : '',
].filter(Boolean).join('\n\n').replaceAll('\n', '\\n');
```

- [ ] **Step 3: Run tests to verify they PASS**

```bash
cd backend && npm test -- --test-name-pattern "external_id"
```

Expected: All 3 tests pass.

- [ ] **Step 4: Run full test suite**

```bash
cd backend && npm test
```

Expected: All existing tests still pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/services/workflow/workflowPromptAssembler.ts
git commit -m "feat: include external requirement number in workflow prompt"
```

---
