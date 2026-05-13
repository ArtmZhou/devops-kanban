import * as test from 'node:test';
import * as assert from 'node:assert/strict';
import { WorkflowLifecycle } from '../src/services/workflow/workflowLifecycle.js';

interface SplitCalls {
  getPendingByTask: number[];
  confirm: number[];
}

function createHarness(opts: {
  autoConfirmSplit: boolean;
  pendingSuggestion: { id: number } | null;
  confirmThrows?: boolean;
  taskId?: number;
}) {
  const taskId = opts.taskId ?? 42;
  const run = {
    id: 7,
    task_id: taskId,
    workflow_id: 'test-wf',
    workflow_template_id: 'test-wf',
    workflow_template_snapshot: { steps: [] },
    workflow_instance_id: 'inst-1',
    status: 'RUNNING',
    mastra_run_id: null,
    current_step: 'step-1',
    steps: [],
    worktree_path: null,
    branch: 'task/42',
    context: {},
  };

  const calls: SplitCalls = { getPendingByTask: [], confirm: [] };

  const fakeService = {
    async getPendingByTask(id: number) {
      calls.getPendingByTask.push(id);
      return opts.pendingSuggestion;
    },
    async confirm(id: number) {
      calls.confirm.push(id);
      if (opts.confirmThrows) throw new Error('boom');
      return { id, status: 'CONFIRMED' };
    },
  };

  const lifecycle = new WorkflowLifecycle({
    workflowRunRepo: {
      async findById() { return run; },
      async update(_runId: number, data: Record<string, unknown>) {
        Object.assign(run, data);
        return run;
      },
      async updateStep() { return run; },
    } as never,
    taskRepo: {
      async findById() { return { id: taskId, title: 'Test Task' }; },
      async update() {},
    } as never,
    agentRepo: { async findById() { return null; } } as never,
    instanceRepo: {
      async findByInstanceId() {
        return { steps: [], auto_confirm_split: opts.autoConfirmSplit };
      },
    } as never,
    sessionRepo: {
      async findById() { return null; },
      async update() { return {}; },
    } as never,
    sessionSegmentRepo: {
      async findLatestBySessionId() { return null; },
    } as never,
    sessionEventRepo: { async append() {} } as never,
    splitSuggestionLoader: async () => ({ splitSuggestionService: fakeService }),
  });

  return { lifecycle, run, calls };
}

test.test('auto_confirm_split=true with pending suggestion → confirm called', async () => {
  const { lifecycle, run, calls } = createHarness({
    autoConfirmSplit: true,
    pendingSuggestion: { id: 99 },
  });

  await lifecycle.onWorkflowComplete(7, { result: 'success' });

  assert.deepEqual(calls.getPendingByTask, [42]);
  assert.deepEqual(calls.confirm, [99]);
  assert.equal(run.status, 'COMPLETED');
});

test.test('auto_confirm_split=true with no pending suggestion → silently skip', async () => {
  const { lifecycle, run, calls } = createHarness({
    autoConfirmSplit: true,
    pendingSuggestion: null,
  });

  await lifecycle.onWorkflowComplete(7, {});

  assert.deepEqual(calls.getPendingByTask, [42]);
  assert.deepEqual(calls.confirm, []);
  assert.equal(run.status, 'COMPLETED');
});

test.test('auto_confirm_split=false → loader not invoked', async () => {
  const { lifecycle, run, calls } = createHarness({
    autoConfirmSplit: false,
    pendingSuggestion: { id: 99 },
  });

  await lifecycle.onWorkflowComplete(7, {});

  assert.deepEqual(calls.getPendingByTask, []);
  assert.deepEqual(calls.confirm, []);
  assert.equal(run.status, 'COMPLETED');
});

test.test('confirm throws → workflow still completes, no propagation', async () => {
  const { lifecycle, run, calls } = createHarness({
    autoConfirmSplit: true,
    pendingSuggestion: { id: 99 },
    confirmThrows: true,
  });

  await lifecycle.onWorkflowComplete(7, {});

  assert.deepEqual(calls.confirm, [99]);
  assert.equal(run.status, 'COMPLETED');
});

test.test('no task_id → auto-confirm skipped', async () => {
  const { lifecycle, calls } = createHarness({
    autoConfirmSplit: true,
    pendingSuggestion: { id: 99 },
    taskId: 0,
  });

  await lifecycle.onWorkflowComplete(7, {});

  assert.deepEqual(calls.getPendingByTask, []);
  assert.deepEqual(calls.confirm, []);
});
