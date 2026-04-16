import * as fs from 'node:fs/promises';
import * as path from 'node:path';
import { TaskSourceAdapter, type TaskSourceLike } from './base.js';
import type { ImportedTask, SourceDefinition } from '../types/sources.ts';
import { SessionEventRepository } from '../repositories/sessionEventRepository.js';
import { SessionSegmentRepository } from '../repositories/sessionSegmentRepository.js';
import { AgentRepository } from '../repositories/agentRepository.js';
import { ClaudeStepRunner } from '../services/workflow/executors/claudeStepRunner.js';
import { logger } from '../utils/logger.js';

type FileInfo = {
  filename: string;
  filepath: string;
  size: number;
  modified: string;
};

const TEXT_EXTENSIONS = new Set([
  'txt', 'md', 'csv', 'json', 'xml', 'yaml', 'yml', 'log',
  'ini', 'html', 'css', 'js', 'ts', 'py', 'java', 'c', 'cpp',
  'h', 'sh', 'sql', 'go', 'rs', 'swift', 'rb', 'php', 'conf',
]);

class LocalDirectoryAdapter extends TaskSourceAdapter {
  static override type = 'LOCAL_DIRECTORY';

  static override metadata: SourceDefinition = {
    type: 'LOCAL_DIRECTORY',
    name: '本地目录文件',
    description: '从本地目录读取文件作为任务',
    configFields: {
      directoryPath: {
        type: 'string',
        required: true,
        description: '服务器本地目录的绝对路径',
      },
      fileExtensions: {
        type: 'string',
        required: false,
        description: '文件扩展名过滤，逗号分隔，如 txt,md,pdf',
      },
      descriptionMode: {
        type: 'string',
        required: false,
        description: '描述生成模式',
        options: [
          { label: '固定模板', value: 'fixed' },
          { label: 'AI 生成', value: 'ai' },
        ],
      },
      descriptionTemplate: {
        type: 'string',
        required: false,
        description: '固定模式的描述模板，支持 {filename}、{filepath}、{size}、{modified} 变量',
      },
      agentId: {
        type: 'number',
        required: false,
        description: 'AI 模式下使用的 Agent ID',
      },
    },
  };

  directoryPath: string;
  fileExtensions: string[];
  descriptionMode: 'fixed' | 'ai';
  descriptionTemplate: string;
  agentId: number | undefined;

  constructor(source: TaskSourceLike) {
    super(source);
    const config = source.config as Record<string, unknown>;
    this.directoryPath = typeof config.directoryPath === 'string' ? config.directoryPath : '';
    const extRaw = typeof config.fileExtensions === 'string' ? config.fileExtensions : '';
    this.fileExtensions = extRaw
      ? extRaw.split(',').map((e) => e.trim().toLowerCase().replace(/^\./, '')).filter(Boolean)
      : [];
    this.descriptionMode = config.descriptionMode === 'ai' ? 'ai' : 'fixed';
    this.descriptionTemplate = typeof config.descriptionTemplate === 'string' && config.descriptionTemplate
      ? config.descriptionTemplate
      : '处理文件: {filename}';
    this.agentId = typeof config.agentId === 'number' ? config.agentId : undefined;
  }

  async _scanFiles(): Promise<FileInfo[]> {
    if (!this.directoryPath) {
      throw new Error('directoryPath is not configured');
    }

    const entries = await fs.readdir(this.directoryPath, { withFileTypes: true });
    const files: FileInfo[] = [];

    for (const entry of entries) {
      if (!entry.isFile()) continue;
      if (entry.name.startsWith('.')) continue;

      const ext = path.extname(entry.name).slice(1).toLowerCase();
      if (this.fileExtensions.length > 0 && !this.fileExtensions.includes(ext)) continue;

      const filepath = path.join(this.directoryPath, entry.name);
      try {
        const stat = await fs.stat(filepath);
        files.push({
          filename: entry.name,
          filepath,
          size: stat.size,
          modified: stat.mtime.toISOString(),
        });
      } catch (err) {
        logger.warn('LocalDirectoryAdapter', `Failed to stat file ${filepath}: ${err}`);
      }
    }

    return files.sort((a, b) => a.filename.localeCompare(b.filename));
  }

  _substituteTemplate(template: string, file: FileInfo): string {
    return template
      .replace(/\{filename\}/g, file.filename)
      .replace(/\{filepath\}/g, file.filepath)
      .replace(/\{size\}/g, String(file.size))
      .replace(/\{modified\}/g, file.modified);
  }

  override async fetch(): Promise<ImportedTask[]> {
    const files = await this._scanFiles();

    return files.map((file) => ({
      external_id: file.filename,
      title: file.filename,
      description: this._substituteTemplate(this.descriptionTemplate, file),
      external_url: `file://${file.filepath}`,
      labels: [],
    }));
  }

  override async testConnection(): Promise<boolean> {
    try {
      await fs.access(this.directoryPath, fs.constants.R_OK);
      return true;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      logger.error('LocalDirectoryAdapter', `Connection test failed: ${message}`);
      return false;
    }
  }

  override convertToTask(item: unknown): ImportedTask {
    const record = item as Record<string, unknown>;
    return {
      title: typeof record.title === 'string' ? record.title : 'Untitled',
      description: typeof record.description === 'string' ? record.description : '',
      external_id: typeof record.external_id === 'string' ? record.external_id : '',
      external_url: typeof record.external_url === 'string' ? record.external_url : '',
      labels: [],
    };
  }

  isTextFile(filename: string): boolean {
    const ext = path.extname(filename).slice(1).toLowerCase();
    return TEXT_EXTENSIONS.has(ext);
  }

  async readFileContent(filepath: string, maxBytes = 10240): Promise<string | null> {
    try {
      const buffer = await fs.readFile(filepath);
      const truncated = buffer.subarray(0, maxBytes);
      return truncated.toString('utf-8');
    } catch {
      return null;
    }
  }

  async fetchWithAiDescriptions(
    sessionId: number,
    files?: FileInfo[],
  ): Promise<ImportedTask[]> {
    const filelist = files ?? await this._scanFiles();

    if (filelist.length === 0) {
      return [];
    }

    if (!this.agentId) {
      return filelist.map((file) => ({
        external_id: file.filename,
        title: file.filename,
        description: this._substituteTemplate(this.descriptionTemplate, file),
        external_url: `file://${file.filepath}`,
        labels: [],
      }));
    }

    const agentRepo = new AgentRepository();
    const agent = await agentRepo.findById(this.agentId);
    if (!agent) {
      logger.error('LocalDirectoryAdapter', `Agent ${this.agentId} not found, falling back to fixed mode`);
      return filelist.map((file) => ({
        external_id: file.filename,
        title: file.filename,
        description: this._substituteTemplate(this.descriptionTemplate, file),
        external_url: `file://${file.filepath}`,
        labels: [],
      }));
    }

    const segmentRepo = new SessionSegmentRepository();
    const eventRepo = new SessionEventRepository();

    const segment = await segmentRepo.create({
      session_id: sessionId,
      status: 'RUNNING',
      executor_type: agent.executorType,
      agent_id: agent.id,
      trigger_type: 'START',
    });

    const runner = new ClaudeStepRunner();

    // Build single prompt for all files
    let fileContents = '';
    for (let i = 0; i < filelist.length; i++) {
      const file = filelist[i];
      const content = this.isTextFile(file.filename)
        ? await this.readFileContent(file.filepath)
        : null;

      fileContents += `\n=== 文件${i + 1}: ${file.filename} ===\n`;
      if (content !== null) {
        fileContents += `${content}\n`;
      } else {
        fileContents += `(二进制文件，请使用工具读取: ${file.filepath})\n`;
      }
    }

    const prompt = `分析以下文件内容，为每个文件生成任务标题和描述。格式：
=== 文件1: <文件名> ===
标题: <生成的标题>
描述: <生成的描述>

文件内容:
${fileContents}`;

    try {
      const result = await runner.runStep({
        prompt,
        worktreePath: this.directoryPath,
        onEvent: async (event) => {
          await eventRepo.create({
            session_id: sessionId,
            segment_id: segment.id,
            kind: event.kind,
            role: event.role,
            content: event.content,
            payload: event.payload ?? {},
          });
        },
      });

      const output = result.stdout || result.stderr || '';
      const results = this._parseMultiFileAiOutput(output, filelist);

      await segmentRepo.update(segment.id, { status: 'COMPLETED' });
      return results;
    } catch (err) {
      logger.error('LocalDirectoryAdapter', `AI batch analysis failed: ${err}`);
      await segmentRepo.update(segment.id, { status: 'FAILED' });
      return filelist.map((file) => ({
        external_id: file.filename,
        title: file.filename,
        description: this._substituteTemplate(this.descriptionTemplate, file),
        external_url: `file://${file.filepath}`,
        labels: [],
      }));
    }
  }

  _parseAiOutput(output: string, fallbackFile: FileInfo): ImportedTask {
    const titleMatch = output.match(/标题[：:]\s*(.+)/);
    const descMatch = output.match(/描述[：:]\s*([\s\S]*?)(?:\n\n|$)/);

    return {
      external_id: fallbackFile.filename,
      title: titleMatch?.[1]?.trim() || fallbackFile.filename,
      description: descMatch?.[1]?.trim() || this._substituteTemplate(this.descriptionTemplate, fallbackFile),
      external_url: `file://${fallbackFile.filepath}`,
      labels: [],
    };
  }

  _parseMultiFileAiOutput(output: string, fallbackFiles: FileInfo[]): ImportedTask[] {
    if (!output || !output.trim()) {
      return fallbackFiles.map((file) => ({
        external_id: file.filename,
        title: file.filename,
        description: this._substituteTemplate(this.descriptionTemplate, file),
        external_url: `file://${file.filepath}`,
        labels: [],
      }));
    }

    // Split by file section markers
    const sections = output.split(/=== 文件\d+: (.+?) ===/).filter(Boolean);

    const results: ImportedTask[] = [];
    // sections alternate: [filename1, content1, filename2, content2, ...]
    for (let i = 0; i < sections.length - 1; i += 2) {
      const filename = sections[i].trim();
      const content = sections[i + 1];

      const fallback = fallbackFiles.find((f) => f.filename === filename) || fallbackFiles[results.length] || {
        filename,
        filepath: `/tmp/${filename}`,
        size: 0,
        modified: new Date().toISOString(),
      };

      const titleMatch = content.match(/标题[：:]\s*(.+)/);
      const descMatch = content.match(/描述[：:]\s*([\s\S]*?)(?:\n\n|$)/);

      results.push({
        external_id: filename,
        title: titleMatch?.[1]?.trim() || filename,
        description: descMatch?.[1]?.trim() || this._substituteTemplate(this.descriptionTemplate, fallback),
        external_url: `file://${fallback.filepath}`,
        labels: [],
      });
    }

    // If parsing produced no results, fall back for all files
    if (results.length === 0) {
      return fallbackFiles.map((file) => ({
        external_id: file.filename,
        title: file.filename,
        description: this._substituteTemplate(this.descriptionTemplate, file),
        external_url: `file://${file.filepath}`,
        labels: [],
      }));
    }

    return results;
  }
}

export { LocalDirectoryAdapter };
