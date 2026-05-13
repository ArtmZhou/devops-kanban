export const DEFAULT_SPLIT_PROMPT = `Use the task-splitter skill to split this task into sub-tasks.

## Context
- Task: {{task_title}} — {{task_description}}
- Project: {{project_name}} (repo: {{project_repo_url}})
- Upstream output: {{last_step_output}}
- Available projects: {{available_projects}}

Output a JSON array wrapped in a \`\`\`json code block.`;

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
