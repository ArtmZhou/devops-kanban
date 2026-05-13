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
