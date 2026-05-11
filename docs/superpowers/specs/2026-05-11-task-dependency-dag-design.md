# 任务依赖 DAG 设计

## 目标

允许一个任务的工作流完成后，AI 自动建议拆分成多个下游任务（如微服务开发），用户确认后创建。下游任务可并行执行，依赖全部满足后自动启动后续任务（如集成测试），形成完整的 DAG 流水线。

## 核心概念

- **每个任务仍然是单条工作流模板**，执行逻辑不变
- 新增的是**任务间的依赖关系**（DAG），不是模板间的
- 起始任务在知识仓，子任务在各自的微服务/前端仓库
- 所有依赖关系在拆分确认时**一次性创建**

## 数据模型

### Task 实体新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `parent_task_id` | `number \| null` | 指向拆分它的父任务，`null` 表示起始任务 |
| `depends_on` | `number[]` | 依赖的上游任务 ID 列表。空数组表示无依赖，可立即启动 |
| `target_repo_url` | `string \| null` | 目标代码仓库 URL。`null` 表示使用当前项目的 `git_url` |

### Task 状态机新增状态

| 状态 | 说明 |
|------|------|
| `WAITING` | 依赖未满足，等待上游任务完成 |

状态流转：
```
CREATED → RUNNING（无依赖，直接启动）
CREATED → WAITING（有依赖，等待上游）
WAITING → RUNNING（所有依赖完成，自动启动）
WAITING → FAILED（任一依赖失败，自动失败）
RUNNING → DONE / FAILED / BLOCKED
```

## 拆分流程

### 1. AI 产出拆分建议

起始任务工作流跑完后，系统调用 AI 产出拆分建议。AI 从知识仓读取微服务信息，产出：

```json
[
  {
    "title": "用户服务开发",
    "description": "实现用户服务的 CRUD 和认证接口",
    "target_repo_url": "git@github.com:org/user-service.git",
    "template_id": "backend-tpl",
    "depends_on": []
  },
  {
    "title": "订单服务开发",
    "description": "实现订单服务的创建和查询接口",
    "target_repo_url": "git@github.com:org/order-service.git",
    "template_id": "backend-tpl",
    "depends_on": []
  },
  {
    "title": "前端开发",
    "description": "实现用户中心和订单管理页面",
    "target_repo_url": "git@github.com:org/frontend.git",
    "template_id": "frontend-tpl",
    "depends_on": []
  },
  {
    "title": "集成测试",
    "description": "验证用户服务和订单服务的集成",
    "target_repo_url": "git@github.com:org/integration-test.git",
    "template_id": "test-tpl",
    "depends_on": ["待创建任务的用户服务ID", "待创建任务的订单服务ID", "待创建任务的前端ID"]
  }
]
```

- AI 自动决定拆几条、用什么模板、依赖谁、跑哪个仓库
- 用户可在 UI 上编辑每条建议（改标题、描述、换模板、换仓库、调依赖）
- 用户可手动添加额外任务
- 用户**一次确认**，系统一次性创建所有子任务

### 2. 创建子任务

```
POST /api/tasks/batch-create
{
  "parent_task_id": 1,
  "children": [
    { "title": "用户服务开发", "description": "...", "target_repo_url": "...", "template_id": "backend-tpl", "depends_on": [] },
    ...
  ]
}
```

系统：
1. 为每条建议创建一个 Task
2. 设置 `parent_task_id`、`depends_on`、`target_repo_url`
3. 检查每个任务的依赖：无依赖的立即自动启动，有依赖的设为 `WAITING`

### 3. 依赖检查 & 自动启动

监听任务完成事件：

```
当任务 X 完成（DONE）时：
  查询所有 depends_on 包含 X 的 WAITING 任务
  对每个下游任务，检查其所有 depends_on 是否都已 DONE
  如果全部满足 → 状态从 WAITING → RUNNING，启动工作流

当任务 X 失败（FAILED/BLOCKED）时：
  查询所有 depends_on 包含 X 的 WAITING 任务
  下游任务状态从 WAITING → FAILED，不启动
```

## worktree 管理

```
任务启动时：
  如果 target_repo_url == null（或当前项目仓库）：
    按现有逻辑在当前项目仓库创建 worktree 和分支
  如果 target_repo_url != 当前项目仓库：
    1. 检查本地是否有该仓库的裸仓库/克隆（按 target_repo_url 的 hash 命名）
    2. 如果没有 → git clone 到 data/repos/<hash>/
    3. 在目标仓库目录下创建 worktree 和分支
```

## UI 设计

### 拆分确认面板

任务工作流完成后，弹出拆分确认面板：

```
┌─────────────────────────────────────────────────────┐
│ AI 建议将工作拆分为以下任务：                         │
│                                                     │
│  [✓] 用户服务开发    │ 仓库: user-service           │
│      模板: 后端模板   │ 依赖: 无                     │
│      [编辑] [删除]                                  │
│                                                     │
│  [✓] 订单服务开发    │ 仓库: order-service          │
│      模板: 后端模板   │ 依赖: 无                     │
│      [编辑] [删除]                                  │
│                                                     │
│  [✓] 前端开发        │ 仓库: frontend               │
│      模板: 前端模板   │ 依赖: 无                     │
│      [编辑] [删除]                                  │
│                                                     │
│  [✓] 集成测试        │ 仓库: integration-test       │
│      模板: 测试模板   │ 依赖: 用户服务, 订单服务, ... │
│      [编辑] [删除]                                  │
│                                                     │
│           [+ 添加任务]                               │
│                                                     │
│        [确认创建]        [取消]                      │
└─────────────────────────────────────────────────────┘
```

### 看板卡片 - 依赖标签

每张任务卡片显示依赖关系：

```
┌─────────────────────────────┐
│ 🟢 微服务A开发              │
│ 状态: DONE                  │
│ 模板: 后端模板               │
│                             │
│ ↑ 依赖: 设计任务              │
│ ↓ 下游: 集成测试              │
└─────────────────────────────┘
```

- 依赖标签可点击跳转到对应任务
- 只有有依赖/下游的卡片才显示标签

### 任务详情页 - 流水线视图

任务详情页新增区域，显示整条链路的 DAG：

```
┌──────────────────────────────────────────────────────┐
│ 流水线视图                                             │
│                                                      │
│  ┌─────────┐                                         │
│  │ 🟢 设计  │  知识仓 · DONE                         │
│  └────┬────┘                                         │
│       │                                              │
│       ├────────────┬────────────┐                    │
│       ▼            ▼            ▼                    │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐              │
│  │ 🟢 用户  │  │ 🟢 订单  │  │ 🟡 前端  │              │
│  │  服务    │  │  服务    │  │  开发    │              │
│  └────┬────┘  └────┬────┘  └────┬────┘              │
│       │            │            │                    │
│       └────────────┼────────────┘                    │
│                    ▼                                 │
│               ┌─────────┐                            │
│               │ ⚪ 集成  │  等待中                    │
│               │  测试    │                            │
│               └─────────┘                            │
│                                                      │
└──────────────────────────────────────────────────────┘
```

- 状态颜色：🟢 DONE，🟡 RUNNING，⚪ WAITING，🔴 FAILED
- 点击节点可跳转到对应任务详情页
- 只在起始任务（parent_task_id 为 null 的任务）的详情页显示全图
- 子任务详情页只显示自己在链路中的位置

## API 设计

### 新增/修改接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/tasks/batch-create` | 批量创建子任务（拆分确认） |
| `GET` | `/api/tasks/:id/dependents` | 获取某任务的下游任务列表 |
| `GET` | `/api/tasks/:id/pipeline` | 获取某任务所在完整流水线的 DAG 数据 |

### Task 实体扩展

`GET /api/tasks` 和 `GET /api/tasks/:id` 返回的 Task 对象新增字段：
- `parent_task_id`
- `depends_on`
- `target_repo_url`

## 错误处理

- 上游任务失败 → 所有依赖它的下游任务自动标记 FAILED
- 子任务 worktree 创建失败（如仓库不存在）→ 任务标记 FAILED，用户可手动重试
- 拆分确认后创建子任务部分失败 → 回滚已创建的子任务，提示用户重新拆分

## 限制

- DAG 不能有环（创建时校验）
- 不支持运行时动态修改依赖关系
- 不支持条件分支（如"任务A成功则跑测试B，失败则跑测试C"）
