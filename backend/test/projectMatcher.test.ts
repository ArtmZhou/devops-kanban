import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matchProject } from '../src/services/workflow/projectMatcher.js';

const projects = [
  { id: 1, name: 'user-service', git_url: 'git@github.com:org/user-service.git' },
  { id: 2, name: 'order-service', git_url: 'git@github.com:org/order-service.git' },
  { id: 3, name: 'frontend', git_url: 'https://github.com/org/frontend.git' },
] as any;

test('exact URL match returns project id', () => {
  const r = matchProject({ title: 'x', target_repo_url: 'git@github.com:org/user-service.git' }, projects);
  assert.equal(r, 1);
});

test('title fuzzy matches project name', () => {
  const r = matchProject({ title: 'frontend page development', target_repo_url: null }, projects);
  assert.equal(r, 3);
});

test('returns null when nothing matches', () => {
  const r = matchProject({ title: '完全无关任务', target_repo_url: 'git@github.com:other/new-repo.git' }, projects);
  assert.equal(r, null);
});

test('URL match takes priority over title', () => {
  const r = matchProject(
    { title: 'frontend', target_repo_url: 'git@github.com:org/user-service.git' },
    projects,
  );
  assert.equal(r, 1);
});
