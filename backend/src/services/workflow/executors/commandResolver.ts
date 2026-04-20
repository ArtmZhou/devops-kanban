import { EXECUTOR_NPM_CACHE } from '../../../config/index.js';

function splitCommand(commandString: string) {
  return commandString.trim().split(/\s+/).filter(Boolean);
}

export function resolveCommand({
  defaultCommand,
  executorConfig,
  processEnv = process.env,
}: {
  defaultCommand: string[];
  executorConfig?: {
    commandOverride?: string | null;
    args?: string[];
    env?: Record<string, string> | undefined;
  };
  processEnv?: NodeJS.ProcessEnv;
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

  // Override npm cache path to avoid EPERM errors on Windows
  if (EXECUTOR_NPM_CACHE && !env.npm_config_cache) {
    env.npm_config_cache = EXECUTOR_NPM_CACHE;
  }

  return {
    command,
    args: [...args, ...(executorConfig?.args || [])],
    env,
  };
}
