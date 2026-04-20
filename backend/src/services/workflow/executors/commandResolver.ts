import { EXECUTOR_NPM_CACHE } from '../../../config/index.js';

function splitCommand(commandString: string) {
  return commandString.trim().split(/\s+/).filter(Boolean);
}

export function resolveCommand({
  defaultCommand,
  executorConfig,
  processEnv = process.env,
  npmCacheOverride = EXECUTOR_NPM_CACHE,
}: {
  defaultCommand: string[];
  executorConfig?: {
    commandOverride?: string | null;
    args?: string[];
    env?: Record<string, string> | undefined;
  };
  processEnv?: NodeJS.ProcessEnv;
  npmCacheOverride?: string;
}) {
  const baseParts = executorConfig?.commandOverride
    ? splitCommand(executorConfig.commandOverride)
    : defaultCommand;

  if (!Array.isArray(baseParts) || baseParts.length === 0) {
    throw new Error('Command must contain at least one part');
  }

  const [command, ...args] = baseParts;

  const env = {
    ...processEnv,
    ...(executorConfig?.env || {}),
  };

  // Remove env vars that cause "nested session" detection
  delete env.CLAUDECODE;
  delete env.CLAUDE_CODE_ENTRYPOINT;

  // Override npm cache path to avoid EPERM errors on Windows.
  // When EXECUTOR_NPM_CACHE is explicitly configured, always override npm_config_cache
  // UNLESS the agent-level executorConfig.env has explicitly set it (highest priority).
  if (npmCacheOverride && !executorConfig?.env?.npm_config_cache) {
    env.npm_config_cache = npmCacheOverride;
  }

  return {
    command,
    args: [...args, ...(executorConfig?.args || [])],
    env,
  };
}
