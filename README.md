# DevOps Kanban

<p align="center">
  <strong>让 AI Agent 成为可靠的生产力</strong>
</p>

<p align="center">
  单个 Agent 跑一次 demo 很惊艳，但一旦放到生产环境——<br>
  想要保证AI的产出可用，比让它干活难十倍。<br>
  DevOps Kanban 为 AI Agent 构建工程纪律——让不可控变得可控，让复杂回归简单。
</p>

---

## 为什么需要 DevOps-Kanban

### AI-assisted 不是 AI First

目前很多团队都引入了AI辅助研发实践，大多数实际是：工程师开一个Code Agent(Claude Code、Cursor等）、产品经理用 ChatGPT 写 PRD、QA 试试 AI 生成测试用例——流程没变、Sprint 没变、评审没变，老流程里塞进新工具。还是 **AI-assisted**，效率可能提升 10-20%，但结构本身没有变化。

**真正的 AI First 不是"AI 怎么帮助工程师"，而是"如果默认 AI 是主要建设者，流程、架构、组织该怎么重构"**。

### 编码不再是瓶颈

当 AI 两小时就能实现一个功能，拖慢团队的就不再是开发，而是别的东西：

- **PM 瓶颈**：Agent 两小时写完功能，但产品规划和设计规划要花几周——反而成了最大约束
- **QA 瓶颈**：AI 实现的代码，验证速度跟不上实现速度——三天测边界情况，卡在两小时之后
- **人头瓶颈**：团队规模不可能靠招人追平对手——只能靠系统设计弥补

三条链路——产品设计、功能实现、测试验证——只要有一条还是手工的，就会卡住整个系统。

### 瓶颈转移：AI First 的工程现实

当执行不再是瓶颈，瓶颈会转移到其他环节：

| 研发阶段 | 瓶颈描述 |
| --- | --- |
| 需求阶段 | 需求进来了没人管——没有结构化入口，需求散落在邮件、文档、聊天里 |
| 任务拆分 | 需求拆不成可执行的任务——人花几天拆任务，AI 几分钟就能写完 |
| 调度阶段 | 任务来了等人工分配和启动——排期、选模板、启动 Workflow，全靠手工 |
| 开发阶段 | 多个任务同时跑互相冲突——多个 Agent 改同一个文件，互相覆盖 |
| 测试阶段 | 做完没人验证质量——AI 两小时写完功能，人花三天测边界情况 |
| 审查阶段 | 产出太多审查看不过来——一天部署 3-8 次，靠人逐个看 PR 根本不现实 |
| 运维阶段 | 上线后问题发现到修复太慢——告警响了没人接，排查靠翻日志 |

## 用 AI 重塑交付流程

### 为什么单 Agent 不够

Agent 有 subagent 能力，能 fork 子任务、并行执行——但子 Agent 解决的是能力问题，不是工程纪律问题：

- **异构执行器**：subagent 本质上是同一个执行器的不同 prompt 变体，能力边界相似。而 Workflow 可以在不同步骤用完全不同的 Agent——Claude Code 编码、OpenCode 验证、Codex 审查，各展所长
- **谁在什么节点做什么**：subagent 由主 Agent 自己决定，流程嵌在 prompt 里，改了逻辑就要改 prompt
- **做到什么标准、不通过怎么办**：subagent 没有质量门禁，它不知道 CI 要过、覆盖率要达标、代码审查要 pass
- **人工在哪介入、怎么反馈**：subagent 挂了就是挂了，它不会自动挂起等人确认，确认完也不会跳过已完成步骤接着跑

这些不是 Agent 能力问题，是流程规范问题。

当前 Agent 的 subagent 能力还做不到像人一样顺畅流转——写完代码不知道怎么交给测试，测试完了不知道怎么通知部署。每一步的交接、上下文传递、异常处理，依然需要人在中间做"胶水"。端到端的交付链是断的。

### 执行者和交接方式都变了

软件工程交付的流程是固定的——需求分析、方案设计、编码、测试、审查、部署，这些环节经过几十年实践，已经形成了成熟的模式。但 AI-First 改变了两个核心问题：

**执行者变了**：过去每个环节靠人——PM 拆需求、开发写代码、QA 测边界、运维盯监控。现在 AI 成为每个环节的主要执行者，人从执行者变成设计者——设计流程、定义标准、在关键节点确认。

**交接方式变了**：过去不同角色之间靠人交接——PRD 文档传给开发、开发传给 QA、QA 传给运维，每一步都有信息损耗和等待时间。Workflow 把这些人工交接点变成自动化流转：原来需要 PM、开发、QA、运维分段负责的作业，现在被自动串联、无缝流转：

- **需求分析流程**：自动拉取外部需求 → AI 拆解为可执行任务 → 人工确认 → 自动分发
- **代码开发到上线**：方案设计 → 编码实现 → CI/CD 流水线验证 → AI 代码审查 → 人工确认 → 部署
- **Bug 修复流程**：自动分诊告警 → AI 根因分析 → 修复 → 回归测试 → 验证关闭

一个任务从进来到交付，每一步由最合适的 Agent 执行，上下文自动传递，人工确认点自动暂停——交接不再靠人。

### DevOps-Kanban 的 Harness engineering实践

#### ① 上下文架构（Context Architecture）——让 AI 知道该做什么

需求进来了，不是丢一句"实现这个功能"就完事，而是给 AI 完整的上下文：

- 外部任务源（GitHub Issues、企业 DevOps 平台）自动同步，需求进来了自动建卡
- 任务卡片承载完整上下文——需求描述、验收标准、相关依赖，不需要人反复解释
- Workflow 模板预置每一步的指令和上下文传递规则——上游步骤的摘要自动传递给下游
- MCP/Skill 系统为 Agent 注入专业能力——代码仓库探索、数据库操作、部署脚本等

#### ② Agent 专业化（Agent Specialization）——让最合适的 Agent 做最合适的事

不同环节需要不同的能力，Workflow 按步骤分配最合适的执行者：

- 编码阶段：Claude Code 或 OpenCode 作为主执行器
- 验证阶段：对接六段 CI/CD 流水线（Verify → Build/Deploy → Test Dev → Deploy Prod → Test Prod → Release），跑类型检查、单元测试、Playwright E2E
- 审查阶段：AI 三轮并行审查（质量逻辑、安全风险、依赖/许可证），作为 review gate 不通过不允许合并
- 所有验证环节做成确定性规则，让 AI 能预测结果、理解失败

#### ③ 持久化记忆（Persistent Memory）——让每个阶段都有交付件，每次执行都有积累

软件工程的可控性来自每一步都有明确的交付物：需求阶段产出结构化任务、设计阶段产出方案文档、编码阶段产出 Feature 分支、测试阶段产出质量报告。Kanban 不仅保留这些交付件，还让它们在 AI 之间自动传递：

- **阶段交付件自动沉淀**：每个 Workflow Step 完成后，执行摘要、产出物、代码变更自动归档——下游 Agent 基于这些交付件继续，不需要人重新解释上下文
- **Session / Segment / Event 三层执行链**——Session 关联具体步骤，Segment 记录每次交互，Event 按序记录原子事件。完整的执行过程可回放
- **Segment 链式 Retry**——执行断了不用重头再来，仅重跑失败步骤，上下文链完整保留
- **Template Snapshot 冻结运行时状态**——模板后续修改不影响运行中的流程，历史可精确复现

#### ④ 结构化执行（Structured Execution）——给 AI 划定边界

AI 在野环境里横冲直撞是灾难。Kanban 确保每一步都在可控范围内：

- Git Worktree 隔离——每任务独立分支和目录，多 Agent 并行互不干扰，失败不影响主分支
- Workflow 步骤编排——把"跑一次 Agent"变成结构化的工程流程，有开始有结束有验证
- Suspend/Resume——关键节点人工确认后继续，恢复时跳过已完成步骤，不重复执行
- 自愈闭环——监控告警 → AI 自动分诊 → 创建工单 → 根因分析 → 自动修复 → 验证关闭，MTTR 从天级缩短至小时级

DevOps-Kanban 的核心不是"让 AI 帮你写代码"，而是"让 AI 成为交付的主要执行者"。当四个核心实践串联在一起，一条由 AI 驱动的端到端交付链就形成了——人从执行者变成设计者，从"自己干"变成"定义规则、确认结果"。这是我们对于 AI-First 在软件工程中如何落地的一次探索。

## 快速开始

### 环境要求

- Node.js 22.x（`>=22 <23`）
- Git

### 一键启动

**macOS / Linux：**

```bash
./start.sh
```

**Windows：**

```cmd
start.bat
```

启动后访问：
- 前端：http://localhost:3000
- 后端：http://localhost:8000

启动脚本会自动处理依赖安装、端口占用和 Node.js 版本校验（要求 22+），运行日志存储到 `log/` 目录。

### 手动启动

**后端（Node.js Fastify）：**

```bash
cd backend
npm install
npm run dev          # 开发服务器（端口 8000）
npm run build        # TypeScript 编译
npm test             # 运行测试
```

**前端（Vue 3）：**

```bash
cd frontend
npm install
npm run dev          # 开发服务器（端口 3000）
npm run build        # 生产构建
npm run test:run     # 运行测试（单次）
```

## 最近更新

### 自动调度引擎

新增完整的自动化调度能力，让 Kanban 从"手动触发"升级为"自动运转"：

- **自动同步**：定时从外部任务源（GitHub Issues、CloudDevOps RR 等）拉取最新任务，无需人工导入
- **自动匹配 Workflow**：新任务到达后，根据任务类型、标签、来源自动匹配最合适的 Workflow 模板
- **自动调度**：按预设规则自动启动 Workflow 执行，无需人工点击——任务来了就自动跑
- **自动通知**：Workflow 执行完成、失败、挂起等待人工确认时，自动通过 Webhook 通知相关人员

> 目标：从"任务进来到交付出去"全链路无人工干预，架构师定义规则，操作员处理异常和确认节点。

### 多 Agent 执行器支持

除了内置的 Claude Code 执行器，现已支持更多 AI Coding Agent：

| 执行器 | 状态 | 说明 |
| --- | --- | --- |
| **Claude Code** | 稳定 | Anthropic 官方 CLI，支持 Session 续接 |
| **OpenCode** | 可用 | 开源 AI 编程助手，支持 Session 续接和事件流 |
| **Codex** | 可用 | OpenAI Codex CLI |

每个 Workflow Step 可以分配不同的 Agent 执行，充分利用各 Agent 的优势。

### 统一错误处理与结构化日志

后端引入了统一的错误处理框架和结构化日志系统：

- **自定义错误类型**：`ValidationError`、`NotFoundError`、`ConflictError`、`BusinessError`、`InternalError`，每种错误携带状态码、用户消息、内部消息和上下文信息
- **全局错误中间件**：Fastify 错误处理器自动将异常转换为一致的 API 响应格式
- **结构化日志**：按组件分类的日志输出，支持上下文数据，格式为 `timestamp [LEVEL] [COMPONENT] message {context}`

### CloudDevOps RR 任务源适配器

新增 CloudDevOps RR（需求请求）适配器，支持从企业内部 DevOps 平台导入需求：

- 支持分页获取 RR 列表（最多 20 页安全限制）
- 解析复杂的 RR 描述结构
- 按用户状态和当前负责人过滤
- 支持自定义 API 路径（列表和详情端点）
- 提供连接测试功能

### 任务源内嵌面板

任务源配置从独立页面重构为项目看板内的可折叠面板：

- 与项目视图无缝集成，无需切换页面
- 支持内联添加、编辑、删除任务源
- 同步预览可选择要导入的任务
- 实时状态指示器和最近同步时间

### 统一主题与 UI 设计

- **统一主题色**：全局使用 Teal 色 `#25C6C9` 作为主题色，替换了之前分散的 indigo/purple 色系
- **BaseDialog 组件**：统一的对话框基础组件，一致的圆角、阴影、渐变头部和操作按钮样式
- **聊天时间戳**：消息气泡上方显示发送时间（精确到毫秒）
- **工具消息过滤**：可一键隐藏 `tool_call` 和 `tool_result` 事件，聚焦 Agent 的核心输出

### 数据库与可靠性改进

- **SQLite WAL 模式**：Workflow 状态数据库启用 WAL（Write-Ahead Logging），显著提升并发读写性能
- **Busy Timeout 自动重试**：30 秒忙等超时，避免 `SQLITE_BUSY` 导致的数据库锁死
- **WorkflowRunRepository 写队列**：所有变更操作通过 `_serializeMutation` 队列串行化，防止竞态条件

### Workflow 模板改进

- 最小步骤数从 2 降为 1，支持单步骤的简单 Workflow
- Workflow 步骤节点显示 Session ID，方便定位具体执行会话

### 启动脚本增强

- 日志按日期时间戳存储到 `log/` 目录（`kanban-frontend-YYYYMMDD-HHMMSS.log`）
- 自动校验 Node.js 版本（要求 22+）
- 前后端健康检查轮询，确保服务就绪
- 支持 Ctrl+C 优雅关闭

## 典型使用流程

DevOps Kanban 的核心不是一个"自动生成代码"的工具，而是一个 **Harness Loop**——以 Workflow 为驱动的人机协作工程循环：

```text
┌───────────────────────────────────────────────────────────────────┐
│                        Harness Loop                                │
│                                                                    │
│  ┌──────────┐   ┌──────────────┐   ┌──────────────┐              │
│  │ 任务建模  │──→│ 选择 Workflow │──→│ 模板 → 流程   │              │
│  └──────────┘   └──────────────┘   └──────┬───────┘              │
│                                            │                      │
│                              ┌─────────────┴─────────────┐        │
│                              │     Workflow Engine        │        │
│                              │                            │        │
│                              │  Step 1 ──→ Step 2 ──→ ... │        │
│                              │    │           │           │        │
│                              │  Worktree   Suspend?       │        │
│                              │  隔离执行    人工确认?      │        │
│                              └─────────────┬─────────────┘        │
│                                            │                      │
│       ┌─────────────┐    ┌────────────┐    │                      │
│       │ 继续执行     │←───│ 人工反馈    │←───┘                      │
│       └──────┬──────┘    └────────────┘                           │
│              │                                                     │
│       ┌──────┴──────┐                                              │
│       │ 审查合并     │                                              │
│       └─────────────┘                                              │
└───────────────────────────────────────────────────────────────────┘
```

1. **创建项目并关联仓库**：在项目列表中创建项目，填写本地仓库路径或远程仓库信息。
2. **配置 Agent**：在 Agent 管理页面配置 Claude Code 等执行器。
3. **创建需求和任务**：将要交付的工作组织成需求和任务卡片，设置优先级与状态。
4. **启动 Workflow 执行**：选择任务和 Workflow 模板，开始在隔离环境中执行。
5. **实时观察**：通过 WebSocket 查看实时输出——Agent 的思考、工具调用、代码变更即时可见。
6. **人工反馈**：在确认节点审查结果，给出反馈继续对话，或直接批准进入下一步。
7. **审查与合并**：检查 Worktree 中的代码变更，必要时继续对话修改，再决定是否合并。

## 架构深度解析

DevOps Kanban 的架构不是随意拼凑的——每一个模块都对应 Harness Engineering 的一个原则。

### 竞争力与爆点

| 竞争力 | 价值 |
| --- | --- |
| **AI 原生任务执行** | 编码工作量减少 50-70%，需求→交付周期从天级缩短至小时级 |
| **多 Agent 编排引擎** | 复杂任务自动拆解多步骤执行，消除串行等待，端到端耗时缩短 60%+ |
| **Git Worktree 并行** | 多任务同时开发互不干扰，团队吞吐量线性提升 |
| **人机协同中断** | AI 自动执行 + 关键决策人工确认，返工率降低，一次通过率提升 |
| **工作流即代码** | 对比手动配 Skill/MCP，Kanban 模板预置一切，开箱即用 |
| **外部系统对接** | 需求从外部系统同步到任务执行零人工搬运，需求漏单率降低 |
| **CVE 一键修复** | 安全漏洞响应时间从天级缩短至小时级，告警→验证全自动化 |
| **Time Travel 重试** | 失败重试仅重跑失败步骤，Token 消耗节省 50%+ |
| **全链路可视化** | AI 执行过程完全透明，问题定位时间从小时级缩短至分钟级 |
| **自愈反馈闭环** | 生产问题从发现到修复形成自动化闭环，MTTR 从天级降至小时级 |
| **瓶颈转移突破** | 当执行不再是瓶颈，持续识别并重构下一个瓶颈（验证/审查/部署） |

### 整体架构

```text
┌──────────────────────────────────────────────────────────────┐
│                      Frontend (Vue 3)                        │
│  Kanban Board │ Workflow Timeline │ Session Panel │ Diff View │
└───────────────────────────┬──────────────────────────────────┘
                            │ REST API + WebSocket
┌───────────────────────────┴──────────────────────────────────┐
│                      Backend (Fastify 4.x)                    │
│                                                               │
│  Routes ──→ Services ──→ Repositories ──→ JSON Files          │
│                            │                                  │
│         ┌──────────────────┴──────────────────┐              │
│         │        Workflow Engine (Mastra)       │              │
│         │  ┌──────────────────────────────┐    │              │
│         │  │ WorkflowService (运行管理)      │    │              │
│         │  │ WorkflowLifecycle (步骤钩子)    │    │              │
│         │  │ WorkflowTemplate (模板工厂)     │    │              │
│         │  └──────────────┬───────────────┘    │              │
│         │          Step Execution               │              │
│         │  ┌──────────────┴───────────────┐    │              │
│         │  │ Executors (Claude Code / Codex / OpenCode)  │    │
│         │  └──────────────┬───────────────┘    │              │
│         └──────────────────┼──────────────────┘              │
│                     Git Worktree (per-task isolation)          │
└──────────────────────────────────────────────────────────────┘
```

### 关键设计决策

#### 为什么用 Git Worktree 而不是 Docker？

Docker 提供了更彻底的隔离，但也带来了镜像构建、环境同步、调试困难等额外复杂度。对于 AI Coding Agent 来说，Git Worktree 是更合适的选择：

- Agent 需要操作的是**代码仓库**，不是运行时环境——Worktree 天然提供代码级隔离
- 每个任务创建独立分支（`task/{project}/{taskId}`），失败不影响主分支
- 多个 Agent 可以安全并行——各自在独立的 Worktree 和分支上工作
- 合并、冲突处理直接用 Git 原生能力，不需要额外工具

#### 为什么用 Mastra 作为 Workflow 引擎？

Workflow 的核心需求不是"编排步骤"，而是**状态持久化和恢复**（详见 [Workflow：Harness 的执行骨架](#workflowharness-的执行骨架)）。Mastra 提供了关键的 Suspend/Resume 能力：

- 执行到需要人工确认的步骤时，Workflow 挂起（suspend），状态持久化到 LibSQL
- 用户审查后恢复（resume），从挂起点继续——且不会重复执行已完成的 Agent 调用
- 内置的状态机（PENDING → RUNNING → COMPLETED/FAILED/CANCELLED/SUSPENDED）省去了自己维护的复杂度
- 模板在运行时动态构建为 Mastra Workflow，每个步骤有独立的 Schema 校验

#### 为什么用 Session / Segment / Event 三层模型？

这是可恢复性和可追溯性的基础：

- **Session**：一次完整的 Agent 交互会话，关联到具体的 Workflow Step
- **Segment**：会话中的每一次交互段（START / CONTINUE / RESUME / RETRY），通过 `parent_segment_id` 形成链式结构
- **Event**：原子事件（message / tool_call / tool_result / status / error），按序号递增存储

这三层设计让系统可以：
1. 从任意断点恢复——找到最后一个成功的 Segment，以 RETRY 触发类型创建新 Segment
2. 完整回放执行过程——按 Session → Segment → Event 层级加载，精确重现每一步
3. 追踪重试历史——Segment 链记录了完整的重试路径

#### 为什么用全局写队列而不是数据库？

`BaseRepository` 使用静态 Promise 链序列化所有 JSON 文件写入操作。`WorkflowRunRepository` 同样通过 `_serializeMutation` 队列串行化所有变更操作，防止 Workflow Step 并发执行时的竞态条件：

```text
Write A ──→ Write B ──→ Write C ──→ ...
（所有 Repository 共享同一个写队列）
```

这个选择的原因是：单团队场景下，JSON 文件的部署成本为零（不需要数据库服务），而全局写队列解决了并发写入导致的文件损坏问题。这是一种务实的选择——用最简单的方式获得并发安全。如果未来需要多实例部署，可以替换 Repository 实现为数据库版本，上层代码无需改动。

#### 为什么 WorkflowRun 要快照模板？

WorkflowRun 存储了完整的 `template_snapshot` 而不是 `template_id` 引用。原因是模板会被修改，但运行中的流程不应该受影响。快照确保了：

- 模板变更不会破坏正在执行的 Workflow
- 历史运行记录可以准确回溯当时的步骤定义
- 这是一个"不可变性"的设计决策——运行时数据和定义数据解耦

### 技术栈

| 层级 | 技术选型 |
| --- | --- |
| 后端框架 | Fastify 4.x + TypeScript |
| 参数校验 | Zod |
| Workflow 引擎 | Mastra（状态持久化至 LibSQL） |
| 实时通信 | @fastify/websocket |
| 前端框架 | Vue 3 + Vite 5 |
| UI 组件 | Element Plus |
| 状态管理 | Pinia |
| 数据存储 | JSON 文件 + LibSQL |
| Agent 执行器 | Claude Code / Codex / OpenCode |
| 国际化 | Vue I18n（zh-CN / en） |
| 任务源适配器 | GitHub Issues / CloudDevOps RR |
| 错误处理 | 自定义错误类型 + 全局错误中间件 |
| 日志 | 结构化日志（按组件分类，支持上下文） |

### 后端模块结构

```text
backend/src/
├── main.ts                    # 应用入口
├── routes/                    # 资源级路由处理
├── services/
│   ├── projectService.ts      # 项目管理
│   ├── taskService.ts         # 任务管理
│   ├── sessionService.ts      # 会话与事件管理
│   ├── workflow/
│   │   ├── workflows.ts       # 动态 Workflow 工厂
│   │   ├── workflowService.ts # Workflow 运行与取消管理
│   │   ├── workflowLifecycle.ts # 步骤生命周期钩子
│   │   ├── workflowTemplateService.ts # 模板管理
│   │   └── executors/         # Claude Code / Codex / OpenCode 执行器
│   └── ...
├── sources/                   # 任务源适配器（GitHub / CloudDevOps RR）
├── middleware/
│   └── errorHandler.ts        # 全局错误处理中间件
├── repositories/              # 数据访问层（BaseRepository + JSON 文件）
├── types/                     # 类型定义
└── utils/
    ├── response.ts            # 统一响应格式
    ├── errors.ts              # 自定义错误类型
    ├── logger.ts              # 结构化日志
    └── git.ts                 # Git Worktree 管理
```

### 前端模块结构

```text
frontend/src/
├── views/
│   ├── ProjectListView.vue    # 项目列表
│   ├── KanbanView.vue         # 看板主视图
│   ├── AgentConfig.vue        # Agent 配置
│   └── WorkflowTemplateConfig.vue  # Workflow 模板配置
├── components/
│   ├── BaseDialog.vue         # 统一对话框基础组件
│   ├── taskSource/
│   │   └── TaskSourcePanel.vue # 任务源内嵌面板
│   ├── workflow/
│   │   ├── WorkflowTimeline.vue    # Workflow 时间线
│   │   └── StepSessionPanel.vue    # 步骤会话面板（含工具消息过滤）
│   └── session/
│       └── SessionEventRenderer.vue # 会话事件渲染（含时间戳）
├── stores/                    # Pinia 状态管理
├── api/                       # Axios API 客户端
├── services/
│   └── websocket.js           # WebSocket 客户端
└── locales/                   # 中英文国际化资源
```

### 数据存储

项目使用 JSON 文件存储业务数据（`data/` 目录），Workflow 状态存储在 `data/mastra.db`（LibSQL，已启用 WAL 模式）。

```
data/
├── projects.json          # 项目
├── requirements.json      # 需求
├── tasks.json             # 任务
├── agents.json            # Agent 配置
├── sessions.json          # 会话
├── executions.json        # 执行记录
├── task_sources.json      # 外部任务源
└── mastra.db              # Workflow 状态（LibSQL）
```

## 可扩展设计

Harness Engineering 的框架应该是开放的——任何人都可以扩展：

- **执行器（Executor）**：实现新的 Agent 类型（如 GPT Code Interpreter），只需实现 Executor 接口
- **Workflow 模板**：通过模板定义新的多步骤执行流，配置确认节点和步骤编排（最小 1 步，最大不限）
- **任务源（Task Sources）**：通过适配器接入外部任务系统（已支持 GitHub Issues、CloudDevOps RR）
- **技能系统（Skills）**：为 Agent 配置可复用的技能，执行前同步到 Worktree

### 添加新的 Agent

```bash
curl -X POST http://localhost:8000/api/agents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Claude Code",
    "type": "CLAUDE",
    "command": "claude",
    "config": {}
  }'
```

### API 参考

所有接口均位于 `/api/` 下，统一返回格式 `{ success, message, data, error }`。

| 资源 | 关键接口 |
| --- | --- |
| Projects | `GET/POST/PUT/DELETE /api/projects` |
| Requirements | `GET/POST/PUT/DELETE /api/requirements` |
| Tasks | `GET/POST/PUT/DELETE /api/tasks`、`PATCH /api/tasks/{id}/status` |
| Sessions | `GET/POST/DELETE /api/sessions` |
| Task Sources | `GET/POST/PUT/DELETE /api/task-sources` |
| Executions | `GET/POST/PUT/DELETE /api/executions` |
| Agents | `GET/POST/PUT/DELETE /api/agents` |
| Health | `GET /health`、`GET /` |

### 任务状态流转

```text
TODO ──→ IN_PROGRESS ──→ DONE
              │
              ├──→ BLOCKED
              ├──→ CANCELLED
              └──→ REQUIREMENTS

优先级：LOW → MEDIUM → HIGH → CRITICAL
```

## 常见注意事项

1. **API 响应格式**：后端统一返回 `{ success, message, data, error }`，前端使用前先判断 `response.success`。
2. **数据目录**：后端默认通过 `STORAGE_PATH=../data` 读取项目根目录下的数据文件。建议使用启动脚本（macOS/Linux: `./start.sh`，Windows: `start.bat`）。
3. **端口约定**：前端默认 `3000`，后端默认 `8000`。启动脚本会自动处理端口冲突。
4. **Git Worktree 路径**：Worktree 存储在仓库的 `.worktrees/` 目录下（已自动添加到 `.gitignore`）。

## 贡献与许可

欢迎提交 Issue、改进建议和代码贡献。

### License

MIT License

### 致谢

- 灵感来源：[vibe-kanban](https://github.com/BloopAI/vibe-kanban)
- AI 代理：[Claude Code](https://claude.ai/code)
- Workflow 引擎：[Mastra](https://mastra.ai)
