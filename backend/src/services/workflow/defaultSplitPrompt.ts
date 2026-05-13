export const DEFAULT_SPLIT_PROMPT = `你是一个任务拆解助手。根据当前任务和上游工作流的产出，将其拆分为若干子任务。

## 上下文
- 任务：{{task_title}} — {{task_description}}
- 项目：{{project_name}}（仓库：{{project_repo_url}}）
- 上游产出：{{last_step_output}}
- 可选项目列表：{{available_projects}}

## 要求
输出一个 \`\`\`json 代码块，数组每项遵循以下 Suggestion schema：

\`\`\`json
[
  {
    "title": "子任务标题",
    "description": "简要描述",
    "template_id": null,
    "linked_project_id": null,
    "target_repo_url": null,
    "depends_on_indices": [],
    "enabled": true
  }
]
\`\`\`

字段说明：
- \`linked_project_id\`：匹配到可选项目则填 id，否则 null
- \`target_repo_url\`：未匹配到项目时填外部仓库 URL，匹配到则 null
- \`depends_on_indices\`：依赖的子任务下标（从 0 开始），无依赖填 []
- \`template_id\`：工作流模板 id，不确定时填 null

只输出 JSON 代码块（用 \`\`\`json 包裹），不要任何解释、分析或其他文字。

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
