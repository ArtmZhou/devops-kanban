import * as test from 'node:test';
import * as assert from 'node:assert/strict';

import { resolveCommand } from '../src/services/workflow/executors/commandResolver.js';

test.test('resolveCommand uses executor default command when no override is set', () => {
  assert.deepEqual(resolveCommand({
    defaultCommand: ['npx', '-y', '@anthropic-ai/claude-code@2.1.62'],
    executorConfig: { commandOverride: null, args: [], env: {} },
    processEnv: { PATH: 'x' },
    npmCacheOverride: '',
  }), {
    command: 'npx',
    args: ['-y', '@anthropic-ai/claude-code@2.1.62'],
    env: { PATH: 'x' },
  });
});

test.test('resolveCommand merges command override args and env', () => {
  const resolved = resolveCommand({
    defaultCommand: ['npx', '-y', '@anthropic-ai/claude-code@2.1.62'],
    executorConfig: {
      commandOverride: 'node custom-cli.js',
      args: ['--foo'],
      env: { DEBUG: '1' },
    },
    processEnv: { PATH: 'x' },
    npmCacheOverride: '',
  });

  assert.equal(resolved.command, 'node');
  assert.deepEqual(resolved.args, ['custom-cli.js', '--foo']);
  assert.equal(resolved.env.DEBUG, '1');
  assert.equal(resolved.env.PATH, 'x');
});

test.test('resolveCommand injects npm_config_cache when npmCacheOverride is set', () => {
  const resolved = resolveCommand({
    defaultCommand: ['npx', '-y', '@anthropic-ai/claude-code@2.1.62'],
    executorConfig: { commandOverride: null, args: [], env: {} },
    processEnv: { PATH: '/usr/bin' },
    npmCacheOverride: '/custom/npm-cache',
  });

  assert.equal(resolved.env.npm_config_cache, '/custom/npm-cache');
  assert.equal(resolved.env.PATH, '/usr/bin');
});

test.test('resolveCommand does not inject npm_config_cache when npmCacheOverride is empty', () => {
  const resolved = resolveCommand({
    defaultCommand: ['npx', '-y', '@anthropic-ai/claude-code@2.1.62'],
    executorConfig: { commandOverride: null, args: [], env: {} },
    processEnv: { PATH: '/usr/bin' },
    npmCacheOverride: '',
  });

  assert.equal(resolved.env.npm_config_cache, undefined);
});

test.test('resolveCommand does not override npm_config_cache if already set in processEnv', () => {
  const resolved = resolveCommand({
    defaultCommand: ['npx', '-y', '@anthropic-ai/claude-code@2.1.62'],
    executorConfig: { commandOverride: null, args: [], env: {} },
    processEnv: { PATH: '/usr/bin', npm_config_cache: '/existing/cache' },
    npmCacheOverride: '/custom/npm-cache',
  });

  assert.equal(resolved.env.npm_config_cache, '/existing/cache');
});

test.test('resolveCommand does not override npm_config_cache if set via executorConfig env', () => {
  const resolved = resolveCommand({
    defaultCommand: ['npx', '-y', '@anthropic-ai/claude-code@2.1.62'],
    executorConfig: { commandOverride: null, args: [], env: { npm_config_cache: '/agent/cache' } },
    processEnv: { PATH: '/usr/bin' },
    npmCacheOverride: '/custom/npm-cache',
  });

  assert.equal(resolved.env.npm_config_cache, '/agent/cache');
});
