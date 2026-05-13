import { BaseRepository } from './base.js';
import type { SplitSuggestionEntity } from '../types/entities.js';

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
