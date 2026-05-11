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
