import { splitSuggestionRepository } from '../repositories/splitSuggestionRepository.js';
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
