---
name: workflow-prompt-external-id
description: Add task external_id to workflow prompt so AI agents can see external requirement numbers
type: project
---

# Design: External Requirement Number in Workflow Prompt

## Problem

Tasks with external references (CloudDevOps RR numbers, GitHub issues) have `external_id` stored in the database, but this information is not visible to AI agents executing workflow steps.

## Solution

Pass `task.external_id` through the workflow state and include it in the assembled prompt.

## Data Flow

```
task.external_id → workflowService.executeWorkflow() → Mastra initialState/inputData
→ workflowStepExecutor.assembleWorkflowPrompt() → prompt string
```

## Changes

### 1. `backend/src/services/workflow/workflows.ts`

Add `taskExternalId: z.string().optional()` to:
- `sharedStateSchema` (line ~12)
- `firstStepInputSchema` (line ~25)

### 2. `backend/src/services/workflow/workflowService.ts`

In `executeWorkflow()` `inputData` and `initialState` (lines ~285-297), add:
```typescript
taskExternalId: task.external_id || '',
```

### 3. `backend/src/services/workflow/workflowPromptAssembler.ts`

- Update `state` type to include `taskExternalId?: string`
- After "原始需求标题" line, conditionally insert external ID section (only when value exists):

```
外部需求单号：RR-12345
```

## Prompt Example

With external_id:
```
当前步骤：开发

原始需求标题：
实现用户登录功能

外部需求单号：RR-12345

原始需求内容：
...
```

Without external_id: prompt unchanged (no line added).

## Backward Compatibility

- `taskExternalId` uses `.optional()` — existing workflows unaffected
- `task.external_id` is `string | null | undefined` — handled with `|| ''`
