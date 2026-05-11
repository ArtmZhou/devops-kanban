# 任务依赖 DAG + AI 自动拆分 设计方案

## Context

Coplat 当前一个任务只能跑一条工作流，无法表达"设计完需要并行开发多个微服务，最后做集成测试"这种多阶段协同。本次改动引入：
1. **任务级 DAG 依赖** — 任务之间可以有依赖关系，上游完成自动触发下游
2. **AI 自动拆分** — 架构设计类任务完成后，AI 产出子任务建议，用户审核确认后批量创建
3. **工作台视图 `/workspace`** — 统一展示当前工作流、流水线链路、聊天、文件改动，让开发者在一个页面完成审核与协作

## UI 概览（已完成 demo）

### 架构图 — 组件与数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                       /workspace 页面                            │
├──────────────┬──────────────────────────────┬───────────────────┤
│              │   ┌──── 工作流链路 (DAG) ────┐│                  │
│              │   │ 设计 ─▶ [用户,订单,前端] ─▶ 集成测试         │
│              │   │ ↑ 可折叠 step 详情        ││                  │
│   任务列表    │   └──────────────────────────┘│   文件查看       │
│              │   ┌──── 当前工作流 ──────────┐│  (diff 视图)     │
│   T1 设计   ◀─┤ │ API→建模→实现→测试 (运行) ││                  │
│   T2 用户   │  │   [启动][重试][合入]...    ││                  │
│   T3 订单   │  └──────────────────────────┘├───────────────────┤
│   T4 前端   │  ┌─── AI 拆分卡片 (可折叠) ──┐│                  │
│   T5 测试   │  │ ☑ 用户服务开发  后端模板 ││   改动文件        │
│              │  │ ☑ 订单服务开发  后端模板 ││   - UserService.ts│
│              │  │ ☑ 前端开发      前端模板 ││   - user.ts       │
│              │  │ ☑ 集成测试      测试模板 ││   worktree 信息   │
│              │  │    [+添加] [取消][确认] ││   feature/xxx     │
│              │  ├──────── 聊天 ────────────┤│                  │
│              │  │ messages + input        ││                  │
└──────────────┴──────────────────────────────┴───────────────────┘
        ▲                       ▲                       ▲
        │                       │                       │
        │     GET /tasks        │  PATCH /split-sugg    │  GET/POST files
        │     GET /pipeline     │  POST .../confirm     │  (worktree API)
        │                       │  GET /split-sugg      │
        ▼                       ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                          后端服务                                │
├───────────────┬────────────────┬────────────────┬───────────────┤
│ TaskService   │SplitSuggestion │ WorkflowService │ ExecutionSvc │
│ - batchCreate │Service         │ + SplitTask    │              │
│ - deps 级联   │ - 解析/匹配    │   Executor     │              │
│ - pipeline    │ - CRUD/confirm │                │              │
└───────┬───────┴───────┬────────┴───────┬────────┴───────┬──────┘
        │               │                │                │
        ▼               ▼                ▼                ▼
┌──────────────────────────────────────────────────────────────────┐
│            JSON 存储 (data/*.json) + Mastra (data/mastra.db)    │
│  tasks / projects / split_suggestions / workflow_runs / ...     │
└──────────────────────────────────────────────────────────────────┘
```

---

路径：`/workspace`，三列布局，中/右列支持拖拽调整宽度。

### 左列 — 任务列表
只显示基本信息：优先级、状态、标题。不显示依赖关系标签。

### 中列 — 上半（工作流区）
**上：工作流链路**（DAG 总览）
- 水平布局，按拓扑顺序展示所有相关工作流
- 并行分支用虚线框 + "并行" 标签圈出
- 每个节点（一个工作流 = 一条子任务）可点击展开，内部显示该工作流的 step 列表
- "当前节点"有青色呼吸边框 + "当前"徽章
- 节点按状态着色：DONE（青）/ RUNNING（亮青）/ WAITING（灰）/ FAILED（红）

**下：当前工作流**
- 标题栏显示当前工作流名（如"用户服务工作流"）
- 水平步骤时间线（API 设计 → 实体建模 → 接口实现 → 单元测试）
- RUNNING 步骤有外发光
- 下方操作按钮：启动 / 重试 / 合入 / 取消 / 刷新

### 中列 — 下半（聊天 + AI 拆分卡片）
**AI 拆分建议卡片**（可折叠）
- 头部：AI 图标 + 标题 + 子任务数 + 折叠箭头，点击整行切换
- 每条建议：复选框 + 标题 + 模板 badge + 描述 + 编辑/删除
- Meta 标签：仓库 URL（等宽字体）+ 依赖关系（有依赖=橙色，无=绿色）
- 底部：添加任务 / 取消 / 确认创建
- 默认展开，用户可手动折叠

**聊天**：消息列表 + 输入框。

### 右列
- 上：文件查看（diff 视图）
- 下：改动文件列表 + worktree 信息（分支 / 路径 / 删除按钮）

---

## 数据模型

### 依赖 DAG 示例

```
     T1 设计 (parent=null, depends_on=[])
         │ 拆分
         ├─── T2 用户服务 (parent=1, depends_on=[])
         ├─── T3 订单服务 (parent=1, depends_on=[])
         ├─── T4 前端     (parent=1, depends_on=[])
         └─── T5 集成测试 (parent=1, depends_on=[2,3,4])
                          ↑ 依赖前三个都 DONE 才能跑

  跨项目分布：
     T1 → 知识仓项目 (project_id=10)
     T2 → 用户服务项目 (project_id=11)
     T3 → 订单服务项目 (project_id=12)
     T4 → 前端项目 (project_id=13)
     T5 → 集成测试项目 (project_id=14)
```

### Task 实体扩展

| 字段 | 类型 | 说明 |
|------|------|------|
| `parent_task_id` | `number \| null` | 拆分它的起始任务；`null` 表示根任务 |
| `depends_on` | `number[]` | 依赖的上游任务 ID 列表 |
| `target_repo_url` | `string \| null` | 未关联 Coplat 项目时的外部仓库 URL；关联了则为 `null` |
| `project_id` | `number` | 已有字段，子任务可能指向与父任务不同的项目 |

### Task 状态机

新增状态 `WAITING`：依赖未满足，等待上游。

状态流转：
```
CREATED → RUNNING（无依赖）
CREATED → WAITING（有依赖）
WAITING → RUNNING（所有依赖 DONE，自动启动）
WAITING → FAILED（任一依赖 FAILED/BLOCKED，级联失败）
RUNNING → DONE / FAILED / BLOCKED
```

### Project 实体扩展

| 字段 | 类型 | 说明 |
|------|------|------|
| `default_template_id` | `string \| null` | 子任务创建到该项目时使用的默认工作流模板 |

### WorkflowTemplate Step 新类型

**`SPLIT_TASK`** — 特殊 step 类型，由 `SplitTaskExecutor` 处理。

step 配置（用户在模板编辑器中设置）：
- `agent_id` — **必填**，选择执行拆分推理的 Agent（从现有 Agent 列表中选）
- `prompt_override` — **可选**，用户自定义 prompt，留空时使用系统默认 prompt

**系统默认 Prompt**（内置在 `SplitTaskExecutor` 中）：
- 包含完整的 JSON 输出格式约束：字段名、类型、示例
- 包含上下文变量占位符：`{{task_title}}`、`{{task_description}}`、`{{project_name}}`、`{{project_repo_url}}`、`{{last_step_output}}`、`{{available_projects}}`（列出当前系统中的 Coplat 项目名称 + git_url，供 AI 匹配）
- 指导 AI 从任务描述与上游 step 产出中提炼可并行的子任务
- 要求 AI 在 ```json 代码块中返回 `Suggestion[]`

**用户覆盖时**：`prompt_override` 支持相同的占位符，但用户需自行保证 JSON 输出约束（系统不会再自动追加）。UI 在模板编辑器提供"恢复默认"按钮。

**防递归**：`SPLIT_TASK` step 执行前检查 `task.parent_task_id`，若非 `null` 则跳过（直接 DONE，不产生建议）。

### SplitSuggestion 新实体

存储文件：`data/split_suggestions.json`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `string` | |
| `parent_task_id` | `number` | 关联的起始任务 |
| `workflow_run_id` | `string` | 产出这批建议的工作流运行 |
| `status` | `'PENDING' \| 'CONFIRMED' \| 'DISMISSED'` | |
| `suggestions` | `Suggestion[]` | JSON 数组，含用户编辑后的状态 |
| `created_at` | `string` | |
| `confirmed_at` | `string \| null` | |

`Suggestion` 结构：
```typescript
{
  title: string;
  description: string;
  template_id: string | null;       // 工作流模板
  linked_project_id: number | null; // 关联的 Coplat 项目
  target_repo_url: string | null;   // 外部仓库 URL（未关联项目时使用）
  depends_on_indices: number[];     // 依赖的其他建议在数组中的下标（创建时转换为 task_id）
  enabled: boolean;                 // 用户是否勾选
}
```

---

## 拆分流程

### 流程图 — 整体时序

```
┌──────────────┐   跑工作流   ┌───────────────┐
│  起始任务    │ ───────────▶ │ 常规 step 1..N│
│  (root task) │              │ (设计/架构产出)│
└──────────────┘              └───────┬───────┘
                                      │
                                      ▼
                              ┌───────────────┐
                              │ SPLIT_TASK    │
                              │ step          │
                              └───────┬───────┘
                                      │
                      parent_task_id != null ?
                           │                     │
                         是 │                   否 │
                           ▼                     ▼
                     ┌──────────┐         ┌───────────────┐
                     │ 跳过     │         │ 调用 Agent    │
                     │ step DONE│         │ + Prompt      │
                     └──────────┘         └───────┬───────┘
                                                  │
                                       提取 json code block
                                                  │
                                   ┌──────────────┴──────────┐
                                   │                         │
                           解析失败 │                  解析成功│
                                   ▼                         ▼
                           ┌──────────────┐         ┌────────────────┐
                           │ step FAILED  │         │ 项目匹配       │
                           │ UI: 重试/跳过│         │ (URL → 标题)   │
                           └──────────────┘         └───────┬────────┘
                                                            ▼
                                                  ┌────────────────┐
                                                  │ 保存为         │
                                                  │ split_suggestions│
                                                  │ PENDING        │
                                                  └───────┬────────┘
                                                          ▼
                                                  ┌────────────────┐
                                                  │ step DONE      │
                                                  │ 工作流结束     │
                                                  └───────┬────────┘
                                                          │
                                              前端轮询/事件通知
                                                          │
                                                          ▼
                                                  ┌────────────────┐
                                                  │ 工作台显示     │
                                                  │ AI 拆分卡片    │
                                                  └───────┬────────┘
                                                          ▼
                                                  ┌────────────────┐
                                                  │ 用户审核+编辑  │
                                                  │ (实时 PATCH)   │
                                                  └───────┬────────┘
                                              ┌───────────┴───────────┐
                                              ▼                       ▼
                                      ┌───────────────┐       ┌──────────────┐
                                      │ 确认创建      │       │ 取消         │
                                      │ batch-create  │       │ DISMISSED    │
                                      └───────┬───────┘       └──────────────┘
                                              ▼
                                      ┌───────────────┐
                                      │ 创建子任务    │
                                      │ 无依赖→RUNNING │
                                      │ 有依赖→WAITING │
                                      └───────────────┘
```

### 流程图 — 依赖完成触发

```
 任务 X 状态变更
      │
      ├─ DONE ────────────┐
      │                   ▼
      │        查找 depends_on 包含 X.id
      │        且 status=WAITING 的下游
      │                   │
      │                   ▼
      │        对每个下游：检查其所有 depends_on
      │                   │
      │                   ▼
      │        全部 DONE ?  ── 否 ──▶ 保持 WAITING
      │                   │是
      │                   ▼
      │           WAITING → RUNNING
      │              启动工作流
      │
      └─ FAILED/BLOCKED ──┐
                          ▼
            查找 depends_on 包含 X.id 的下游
                          │
                          ▼
            下游全部 FAILED（级联）
                          │
                          ▼
            递归处理它们的下游
```

### 流程图 — 状态机

```
          ┌────────┐
          │CREATED │
          └───┬────┘
    无依赖    │    有依赖
   ┌──────────┴──────────┐
   ▼                     ▼
┌──────┐             ┌──────┐
│RUNNING│◀─ 依赖满足──│WAITING│
└──┬───┘             └──┬───┘
   │                    │
   │                    │ 任一依赖失败
   │                    ▼
   │             ┌──────────┐
   │             │ FAILED   │
   │             └──────────┘
   │
   ├──▶ DONE
   ├──▶ FAILED  (工作流异常)
   └──▶ BLOCKED (用户介入)
```

---

## 拆分流程（详述）

### 1. 触发
任务工作流执行到 `SPLIT_TASK` step：
- 如果 `task.parent_task_id !== null` → 跳过（防递归），step 直接 DONE
- 否则调用配置的 Agent，传入 Prompt 模板（含当前项目上下文、任务描述、产出物）

### 2. 解析 Agent 输出
- 从 Agent 最终输出文本提取第一个 ```json 代码块
- 解析失败 → step 标记 **FAILED**，error message: "AI 输出未包含有效的 JSON 代码块"
  - 前端显示错误，允许用户点"重试"（重新跑 step）或"跳过"（手动标记 step 完成，自己添加子任务）
- 解析成功 → 进入第 3 步

### 3. 项目匹配
对每条建议：
- 按 `target_repo_url` 在 Coplat 项目里精确匹配 `git_url` → 命中则填 `linked_project_id`
- 未命中时按任务标题 / 项目名称模糊匹配 → 命中则填 `linked_project_id`
- 都没命中 → `linked_project_id = null`，保留 `target_repo_url`

匹配到项目后，**默认模板 = 该项目的 `default_template_id`**；未匹配到项目时，模板 = AI 推荐 或 null（用户手动选）。

### 4. 保存
写入 `split_suggestions` 表，`status = PENDING`。step 标记 DONE。

### 5. 用户审核（前端）
工作台视图轮询或订阅事件，检测到该任务有 `PENDING` 的拆分建议 → 显示 AI 拆分卡片（默认展开）。

用户可：
- 勾选/取消单条建议（修改 `enabled`）
- 编辑标题、描述、模板、关联项目、仓库 URL、依赖关系
- 手动添加一条空白建议
- 删除某条建议

**编辑实时保存**：所有修改经 500ms debounce 后 PATCH 到 `split_suggestions` 记录（覆盖 `suggestions` 字段）。

### 6. 确认创建
点击"确认创建"：
- 后端遍历 `enabled === true` 的建议
- 为每条建议创建 Task：
  - `project_id` = `linked_project_id ?? current_project_id`
  - `target_repo_url` = `linked_project_id === null ? target_repo_url : null`
  - `workflow_template_id` = 建议里的 `template_id`
  - `parent_task_id` = 当前起始任务 ID
  - `depends_on` = 将 `depends_on_indices` 转换为实际的 task_id（使用本次批量创建的映射）
- 依赖检查：
  - 无依赖 → 状态 RUNNING，启动工作流
  - 有依赖 → 状态 WAITING
- 标记 `split_suggestions.status = CONFIRMED`，记录 `confirmed_at`

### 7. 取消 / 重新生成
- 点"取消" → `split_suggestions.status = DISMISSED`，UI 隐藏卡片（记录保留用于审计）
- 任务详情页提供"重新生成拆分建议"按钮 → 重跑 SPLIT_TASK step，产生新的 PENDING 记录（旧的保持 DISMISSED）

---

## 依赖检查 & 自动启动

监听 Task 状态变更事件：

**当任务 X 完成（DONE）时：**
```
查询所有 depends_on 包含 X.id 且 status=WAITING 的下游任务
对每个下游：
  检查其所有 depends_on 任务是否全部 DONE
  全部 DONE → status: WAITING → RUNNING，启动工作流
```

**当任务 X 失败（FAILED/BLOCKED）时：**
```
递归级联失败：
  所有 depends_on 包含 X.id 的下游任务 → FAILED
  继续对这些任务的下游做同样处理
```

---

## worktree 管理

**沿用现有逻辑**，按 `task.project_id` 和 `task.target_repo_url` 分流：

```
子任务启动时：
  if task.target_repo_url == null:
    # 关联了 Coplat 项目（或未拆分的普通任务）
    使用 project.git_url 创建 worktree 和分支
  else:
    # 外部仓库
    本地是否有该仓库的克隆（data/repos/<hash(url)>）？
    否 → git clone
    在该目录下创建 worktree 和分支
```

---

## API

### 新增

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/tasks/batch-create` | 批量创建子任务（拆分确认） |
| `GET` | `/api/tasks/:id/dependents` | 获取某任务的下游列表 |
| `GET` | `/api/tasks/:id/pipeline` | 获取完整 DAG（跨项目聚合） |
| `GET` | `/api/tasks/:id/split-suggestions` | 获取某任务的所有拆分建议（PENDING / DISMISSED） |
| `PATCH` | `/api/split-suggestions/:id` | 更新拆分建议（用户编辑） |
| `POST` | `/api/split-suggestions/:id/confirm` | 确认创建子任务 |
| `POST` | `/api/split-suggestions/:id/dismiss` | 取消 |
| `POST` | `/api/tasks/:id/regenerate-split` | 重新生成拆分建议 |

### pipeline 接口返回结构
```json
{
  "root": { "id": 1, "title": "设计用户中心", "project_id": 10, ... },
  "nodes": [
    { "id": 1, "title": "...", "status": "DONE", "depends_on": [], "parent_task_id": null, "project_id": 10 },
    { "id": 2, "title": "...", "status": "DONE", "depends_on": [1], "parent_task_id": 1, "project_id": 11 }
  ]
}
```

从任何任务节点发起都会沿 `parent_task_id` 上溯到根，然后下钻所有子孙，跨项目聚合。

---

## 关键修改路径

### 前端（`frontend/src/`）
- **新建** `views/WorkspaceView.vue` ✅ 已完成 demo
- **新建** `components/workspace/PipelineDag.vue`（从 WorkspaceView 抽离工作流链路）
- **新建** `components/workspace/CurrentWorkflow.vue`（抽离当前工作流 + 操作按钮）
- **新建** `components/workspace/AiSplitCard.vue`（抽离 AI 拆分卡片）
- **新建** `api/splitSuggestions.js`
- **扩展** `api/tasks.js`：batch-create / pipeline / dependents / regenerate-split
- **扩展** `router/index.js`：添加 `/workspace/:projectId?` 路由 ✅ 已完成
- **扩展** `App.vue`：侧边栏导航 ✅ 已完成
- **扩展** `locales/zh.js`, `locales/en.js`：新增 UI 文案

### 后端（`backend/src/`）
- **新建** `services/workflow/executors/SplitTaskExecutor.ts`（或 `splitTask.ts`） — 处理 SPLIT_TASK step：调用 Agent、解析 JSON、项目匹配、写 split_suggestions
- **新建** `services/splitSuggestionService.ts` — CRUD + confirm 批量创建逻辑
- **新建** `repositories/splitSuggestionRepository.ts`（继承 `BaseRepository`）
- **新建** `routes/splitSuggestions.ts`
- **扩展** `services/taskService.ts`：`batchCreate`、`getDependents`、`getPipeline`、依赖完成事件监听
- **扩展** `services/workflow/workflows.ts`：注册 `SPLIT_TASK` step 类型到工厂
- **扩展** `repositories/projectRepository.ts`：`default_template_id` 字段
- **扩展** `routes/tasks.ts`、`routes/projects.ts`
- **新建** `data/split_suggestions.json`

### 可复用的现有能力
- `BaseRepository` (`backend/src/repositories/base.js`) — 提供 JSON CRUD，`SplitSuggestionRepository` 直接继承
- `WorkflowLifecycle` (`backend/src/services/workflow/workflowLifecycle.ts`) — step 生命周期钩子可挂载拆分 step 的错误处理
- `WorkflowRunRepository._serializeMutation` — 若拆分后更新 run 状态需要走这个队列
- `utils/git.js` — worktree 创建/删除；外部仓库 clone 可新增 helper 复用其中的分支管理
- Mastra `createStep` — `SplitTaskExecutor` 按现有 executor 模式实现
- `AgentConfig` / executor 体系 — 拆分 step 直接复用 Agent 概念，不引入新的"AI 配置"

---

## 限制

- DAG 无环校验（创建子任务时校验 `depends_on` 不形成环）
- 不支持运行时修改依赖关系（只能在拆分建议阶段改）
- 不支持条件分支（不存在"任务 A 成功则跑 B、失败则跑 C"）
- 子任务一旦创建不能回滚为 `split_suggestions`（需要用户手动删除）

---

## 验证方式

实施完成后按以下步骤端到端验证：

1. **启动服务** `./start.sh`
2. **创建项目 A**（知识仓），绑定含 `SPLIT_TASK` step 的工作流模板
3. **创建项目 B**（用户服务），设置 `default_template_id` 为后端模板
4. **创建起始任务**到项目 A，启动工作流，观察：
   - 工作流走到 `SPLIT_TASK` step
   - AI 调用产出 JSON code block
   - `split_suggestions.json` 出现 PENDING 记录
5. **打开 `/workspace` 页面**，选中起始任务，验证：
   - AI 拆分卡片显示，4 条建议渲染正确
   - 编辑某条建议（改标题 / 换关联项目），刷新后改动保留
   - 折叠/展开卡片
6. **点"确认创建"**，验证：
   - 用户服务任务创建到项目 B，状态 RUNNING（立即启动 worktree）
   - 集成测试任务创建，状态 WAITING
   - `split_suggestions` 标记 CONFIRMED
7. **用户服务任务完成**后验证：
   - WAITING 任务自动流转状态
   - 流水线视图 `/workspace` 上能看到跨项目 DAG
8. **构造 AI 输出错误场景**：step FAILED，前端能点"重试"
9. **单元测试**：
   - `SplitSuggestionService` 的 confirm 流程（依赖转换、无环校验）
   - 依赖完成事件（级联启动、级联失败）
   - JSON 解析器（兼容带/不带 ```json、含多个代码块等）
10. **自动化测试**：`npm run test`（前端 + 后端）全通过
