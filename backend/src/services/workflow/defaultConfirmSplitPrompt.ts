export const DEFAULT_CONFIRM_SPLIT_PROMPT = `你是一个任务拆分审核助手。上一步 SPLIT_TASK 已经为当前任务生成了一组子任务拆分建议，你的职责是对这些建议做最终复核，输出一段简短的总结说明。

## 任务上下文
- 任务标题：{{task_title}}
- 任务描述：{{task_description}}

## 待确认的拆分建议
{{suggestions_block}}

## 输出要求
以中文输出一段 3-6 句的总结，涵盖：
1. 这批建议是否合理、能否覆盖原任务
2. 是否存在明显遗漏、重复、或依赖关系错误
3. 是否值得直接确认创建子任务（如果有明显问题，指出即可，但系统仍会自动确认）

请直接输出总结文本，不需要 JSON 或代码块。`;

export function renderConfirmSplitPrompt(
  template: string,
  vars: {
    task_title: string;
    task_description: string;
    suggestions_block: string;
  },
): string {
  return template
    .replace(/\{\{task_title\}\}/g, vars.task_title)
    .replace(/\{\{task_description\}\}/g, vars.task_description)
    .replace(/\{\{suggestions_block\}\}/g, vars.suggestions_block);
}
