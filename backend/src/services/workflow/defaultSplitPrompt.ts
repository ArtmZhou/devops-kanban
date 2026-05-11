export const DEFAULT_SPLIT_PROMPT = `你是一个项目任务拆解助手。你的任务是根据上游工作流的产出，把当前任务拆分成若干可以并行或有依赖的子任务。

## 任务上下文
- 任务标题：{{task_title}}
- 任务描述：{{task_description}}
- 当前项目：{{project_name}}
- 当前仓库：{{project_repo_url}}

## 上游工作流产出
{{last_step_output}}

## 可用的 Coplat 项目（按仓库 URL 精确匹配，找不到时设为 null）
{{available_projects}}

## 输出要求
请把你的拆分方案放在一个 \`\`\`json 代码块 里，数组中每一项遵循下面的 Suggestion schema：

\`\`\`json
[
  {
    "title": "子任务标题",
    "description": "子任务描述，1-3 句",
    "template_id": null,
    "linked_project_id": null,
    "target_repo_url": "git@github.com:org/repo.git 或 null",
    "depends_on_indices": [],
    "enabled": true
  }
]
\`\`\`

字段说明：
- \`linked_project_id\`：若能在"可用的 Coplat 项目"里匹配到目标仓库，填该项目 id；否则填 null。
- \`target_repo_url\`：未匹配到 Coplat 项目时填外部仓库 URL，匹配到则填 null。
- \`depends_on_indices\`：依赖的其他子任务在此数组中的下标（从 0 开始）。无依赖时为空数组。
- \`template_id\`：工作流模板 id，不确定时填 null，由用户手动选。
- \`enabled\`：默认 true。

只输出 JSON 代码块，不要其他解释。`;

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
