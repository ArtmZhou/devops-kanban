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
