import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as crypto from 'node:crypto';
import * as path from 'node:path';

import { getExternalRepoPath } from '../src/utils/git.js';

test('external repo hash is deterministic', () => {
  const url = 'git@github.com:test/repo.git';
  const h1 = crypto.createHash('sha256').update(url).digest('hex').slice(0, 16);
  const h2 = crypto.createHash('sha256').update(url).digest('hex').slice(0, 16);
  assert.equal(h1, h2);
});

test('getExternalRepoPath returns deterministic path under data/repos', () => {
  const url = 'git@github.com:test/repo.git';
  const p1 = getExternalRepoPath(url);
  const p2 = getExternalRepoPath(url);
  assert.equal(p1, p2);
  // Final segment must be the 16-char sha256 prefix, parent segment must be 'repos'
  const hash = crypto.createHash('sha256').update(url).digest('hex').slice(0, 16);
  assert.equal(path.basename(p1), hash);
  assert.equal(path.basename(path.dirname(p1)), 'repos');
});

test('different urls produce different external repo paths', () => {
  const a = getExternalRepoPath('git@github.com:a/b.git');
  const b = getExternalRepoPath('git@github.com:c/d.git');
  assert.notEqual(a, b);
});
