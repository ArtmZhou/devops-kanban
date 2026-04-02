import { readFile } from 'fs/promises';
import { join } from 'path';
import { getDbClient } from './client.js';
import { migrateSchema } from './migrate.js';

/**
 * Initialize all database tables.
 * Creates tables if they don't exist by reading schema.sql,
 * then auto-migrates any schema drift (new columns, new indexes).
 * Throws on destructive drift (removed columns, type changes).
 */
export async function initDatabase(): Promise<void> {
  const client = getDbClient();
  await client.execute('PRAGMA busy_timeout = 5000');

  // 1. Create missing tables/indexes
  const schemaPath = join(import.meta.dirname, 'schema.sql');
  const schemaSql = await readFile(schemaPath, 'utf-8');
  await client.executeMultiple(schemaSql);

  // 2. Auto-migrate: new columns, new indexes, detect destructive changes
  const report = await migrateSchema(client, schemaSql);
  if (report.errors.length > 0) {
    throw new Error(
      `[DB Migration] Destructive schema drift detected. Run 'npm run db:reset'.\n${report.errors.join('\n')}`,
    );
  }
}
