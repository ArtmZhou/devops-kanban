export const DEFAULT_SPLIT_PROMPT = `使用 task-splitter Skill 将当前任务拆分为若干子任务。

## 上下文
- 任务：{{task_title}} — {{task_description}}
- 项目：{{project_name}}（仓库：{{project_repo_url}}）
- 上游产出：{{last_step_output}}
- 可选项目列表：{{available_projects}}

按 Skill 约定的 JSON schema 输出结果。`;

export function renderSplitPrompt(
  template: string,
  vars: {
    task_title: string;
    task_description: string;
    project_name: string;
    project_repo_url: string;
    last_step_output: string;
    available_projects: string;
  },
): string {
  return template
    .replace(/\{\{task_title\}\}/g, vars.task_title)
    .replace(/\{\{task_description\}\}/g, vars.task_description)
    .replace(/\{\{project_name\}\}/g, vars.project_name)
    .replace(/\{\{project_repo_url\}\}/g, vars.project_repo_url)
    .replace(/\{\{last_step_output\}\}/g, vars.last_step_output)
    .replace(/\{\{available_projects\}\}/g, vars.available_projects);
}
