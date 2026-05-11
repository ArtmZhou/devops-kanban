import { test, before, after } from 'node:test';
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
