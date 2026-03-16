# DevOps Kanban 实施计划

> 本文档详细描述项目各功能模块的实现步骤、测试用例和验证方法。

---

## 目录

- [阶段一：项目基础架构](#阶段一项目基础架构)
- [阶段二：项目管理](#阶段二项目管理)
- [阶段三：看板系统](#阶段三看板系统)
- [阶段四：AI 智能体集成](#阶段四ai-智能体集成)
- [阶段五：工作流管理](#阶段五工作流管理)
- [阶段六：任务来源集成](#阶段六任务来源集成)
- [阶段七：Git 集成](#阶段七git-集成)
- [阶段八：AI 任务管家](#阶段八ai-任务管家)

---

## 阶段一：项目基础架构

### 1.1 后端项目初始化

**实现内容**：
- 创建 FastAPI 项目结构
- 配置 uv 包管理
- 设置环境变量和配置文件

**文件结构**：
```
backend/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI 入口
│   ├── config.py            # 配置管理
│   ├── routers/             # 路由模块
│   ├── models/              # 数据模型
│   ├── services/            # 业务逻辑
│   └── repositories/        # 数据存储
├── tests/
│   ├── __init__.py
│   └── conftest.py          # 测试配置
├── pyproject.toml           # 项目配置
└── uv.lock                  # 依赖锁定
```

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 1.1.1 | FastAPI 应用启动 | `uv run uvicorn app.main:app` 启动无报错 |
| 1.1.2 | 健康检查端点 | `GET /health` 返回 `{"status": "ok"}` |
| 1.1.3 | CORS 配置 | 前端可跨域访问后端 API |

**验证命令**：
```bash
# 启动后端
uv run uvicorn app.main:app --reload --port 8080

# 测试健康检查
curl http://localhost:8080/health
# 预期输出: {"status": "ok"}
```

---

### 1.2 数据存储层

**实现内容**：
- JSON 文件存储实现
- 通用 Repository 基类
- 数据文件路径配置

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 1.2.1 | 创建数据文件 | 调用 save() 后文件存在 |
| 1.2.2 | 读取数据文件 | 调用 find_all() 返回正确数据 |
| 1.2.3 | 更新数据文件 | 调用 update() 后数据已更新 |
| 1.2.4 | 删除数据 | 调用 delete() 后数据不存在 |

**验证命令**：
```bash
# 运行存储层测试
uv run pytest tests/test_repositories.py -v
```

---

### 1.3 前后端联调

**实现内容**：
- 更新前端 API 基础路径
- 配置 Vite 代理
- 统一 API 响应格式

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 1.3.1 | API 代理配置 | 前端请求 `/api/*` 转发到后端 |
| 1.3.2 | 响应格式统一 | 所有 API 返回 `{success, message, data}` |

**验证命令**：
```bash
# 启动前端
cd frontend && npm run dev

# 浏览器打开 http://localhost:5173
# 检查 Network 面板，API 请求正常返回
```

---

## 阶段二：项目管理

### 2.1 Project 数据模型

**实现内容**：
- Project 实体定义
- ProjectDTO 数据传输对象
- ProjectRepository 存储实现

**数据模型**：
```python
class Project:
    id: int
    name: str
    description: str
    git_url: str | None
    local_path: str | None
    created_at: datetime
    updated_at: datetime
```

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 2.1.1 | 创建项目 | `POST /api/projects` 返回新项目 |
| 2.1.2 | 获取项目列表 | `GET /api/projects` 返回项目数组 |
| 2.1.3 | 获取单个项目 | `GET /api/projects/{id}` 返回项目详情 |
| 2.1.4 | 更新项目 | `PUT /api/projects/{id}` 返回更新后项目 |
| 2.1.5 | 删除项目 | `DELETE /api/projects/{id}` 返回成功 |

**验证命令**：
```bash
# 创建项目
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{"name": "测试项目", "description": "项目描述"}'
# 预期: {"success": true, "data": {"id": 1, "name": "测试项目", ...}}

# 获取列表
curl http://localhost:8080/api/projects
# 预期: {"success": true, "data": [...]}

# 运行测试
uv run pytest tests/test_project_api.py -v
```

---

### 2.2 前端项目管理页面

**实现内容**：
- 项目列表页面（已存在，需对接 API）
- 项目创建/编辑对话框
- 项目删除确认

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 2.2.1 | 显示项目列表 | 页面加载后显示项目卡片 |
| 2.2.2 | 创建项目 | 点击创建按钮，填写表单，项目出现在列表 |
| 2.2.3 | 编辑项目 | 修改项目名称，保存后列表更新 |
| 2.2.4 | 删除项目 | 确认删除后，项目从列表消失 |
| 2.2.5 | 项目切换 | 下拉选择项目后，看板显示对应任务 |

**验证步骤**：
1. 打开 `http://localhost:5173`
2. 点击「新建项目」按钮
3. 填写项目名称和描述，点击保存
4. 验证项目出现在列表中
5. 点击编辑，修改名称，保存
6. 验证修改生效
7. 点击删除，确认
8. 验证项目已移除

---

## 阶段三：看板系统

### 3.1 Task 数据模型

**实现内容**：
- Task 实体定义
- TaskDTO 数据传输对象
- TaskRepository 存储实现（按项目分文件）

**数据模型**：
```python
class Task:
    id: int
    project_id: int
    title: str
    description: str
    status: TaskStatus  # REQUIREMENTS, TODO, IN_PROGRESS, DONE, BLOCKED, CANCELLED
    priority: Priority  # HIGH, MEDIUM, LOW
    assignee: str | None
    tags: list[str]
    created_at: datetime
    updated_at: datetime
```

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 3.1.1 | 创建任务 | `POST /api/projects/{projectId}/tasks` |
| 3.1.2 | 获取项目任务 | `GET /api/projects/{projectId}/tasks` |
| 3.1.3 | 更新任务状态 | `PATCH /api/tasks/{id}/status` |
| 3.1.4 | 更新任务优先级 | `PATCH /api/tasks/{id}/priority` |
| 3.1.5 | 删除任务 | `DELETE /api/tasks/{id}` |
| 3.1.6 | 任务按状态分组 | 返回数据按状态分类 |

**验证命令**：
```bash
# 创建任务
curl -X POST http://localhost:8080/api/projects/1/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "实现登录功能", "status": "TODO", "priority": "HIGH"}'

# 更新状态
curl -X PATCH http://localhost:8080/api/tasks/1/status \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'

# 运行测试
uv run pytest tests/test_task_api.py -v
```

---

### 3.2 前端看板视图

**实现内容**：
- 看板列组件（按状态分组）
- 任务卡片组件（支持拖拽）
- 任务详情对话框
- 任务创建/编辑表单

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 3.2.1 | 显示看板列 | 6 个状态列正确显示 |
| 3.2.2 | 任务卡片显示 | 任务标题、优先级标签正确 |
| 3.2.3 | 拖拽任务 | 拖拽任务到新列，状态更新 |
| 3.2.4 | 创建任务 | 点击列底部「+」，创建任务 |
| 3.2.5 | 查看详情 | 点击任务卡片，显示详情弹窗 |
| 3.2.6 | 编辑任务 | 在详情弹窗中修改，保存成功 |
| 3.2.7 | 删除任务 | 在详情弹窗中删除，任务消失 |

**验证步骤**：
1. 选择一个项目
2. 验证看板显示 6 列
3. 在 TODO 列点击「+」，创建任务
4. 验证任务出现在 TODO 列
5. 拖拽任务到 IN_PROGRESS 列
6. 验证任务状态已更新
7. 点击任务卡片，查看详情
8. 修改任务描述，保存
9. 验证修改生效

---

### 3.3 列表视图

**实现内容**：
- 表格形式显示任务
- 状态筛选
- 优先级筛选
- 搜索功能

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 3.3.1 | 显示任务表格 | 所有任务以表格形式显示 |
| 3.3.2 | 状态筛选 | 选择状态后只显示对应任务 |
| 3.3.3 | 优先级筛选 | 选择优先级后只显示对应任务 |
| 3.3.4 | 搜索功能 | 输入关键词，结果匹配 |

**验证步骤**：
1. 切换到列表视图
2. 验证任务以表格显示
3. 使用状态筛选，验证结果正确
4. 使用搜索框搜索任务标题
5. 验证搜索结果匹配

---

## 阶段四：AI 智能体集成

### 4.1 Agent 数据模型

**实现内容**：
- Agent 实体定义
- AgentRepository 存储
- AgentService 业务逻辑

**数据模型**：
```python
class Agent:
    id: int
    project_id: int
    name: str
    role: AgentRole  # FRONTEND, BACKEND, DEVOPS, DBA, QA, FULLSTACK
    agent_type: str  # claude, codex, etc.
    config: dict     # 智能体配置
    created_at: datetime
    updated_at: datetime
```

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 4.1.1 | 创建智能体 | `POST /api/projects/{projectId}/agents` |
| 4.1.2 | 获取项目智能体 | `GET /api/projects/{projectId}/agents` |
| 4.1.3 | 更新智能体配置 | `PUT /api/agents/{id}` |
| 4.1.4 | 删除智能体 | `DELETE /api/agents/{id}` |

**验证命令**：
```bash
# 创建智能体
curl -X POST http://localhost:8080/api/projects/1/agents \
  -H "Content-Type: application/json" \
  -d '{"name": "前端开发", "role": "FRONTEND", "agent_type": "claude"}'

# 运行测试
uv run pytest tests/test_agent_api.py -v
```

---

### 4.2 会话管理

**实现内容**：
- Session 实体定义
- SessionService 会话管理
- WebSocket 连接管理

**数据模型**：
```python
class Session:
    id: int
    task_id: int
    agent_id: int
    status: SessionStatus  # CREATED, RUNNING, IDLE, STOPPED, ERROR
    worktree_path: str
    created_at: datetime
    updated_at: datetime
```

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 4.2.1 | 创建会话 | `POST /api/tasks/{taskId}/sessions` |
| 4.2.2 | 获取任务会话 | `GET /api/tasks/{taskId}/sessions` |
| 4.2.3 | 启动会话 | `POST /api/sessions/{id}/start` |
| 4.2.4 | 停止会话 | `POST /api/sessions/{id}/stop` |
| 4.2.5 | 获取会话状态 | `GET /api/sessions/{id}` |

**验证命令**：
```bash
# 创建会话
curl -X POST http://localhost:8080/api/tasks/1/sessions \
  -H "Content-Type: application/json" \
  -d '{"agent_id": 1}'

# 运行测试
uv run pytest tests/test_session_api.py -v
```

---

### 4.3 WebSocket 终端

**实现内容**：
- FastAPI WebSocket 端点
- 实时消息推送
- 终端输出流

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 4.3.1 | WebSocket 连接 | 前端成功连接 WebSocket |
| 4.3.2 | 发送消息 | 发送命令，收到响应 |
| 4.3.3 | 接收输出 | 智能体输出实时显示 |
| 4.3.4 | 断开连接 | 关闭后状态正确更新 |

**验证步骤**：
1. 在任务详情中点击「启动会话」
2. 验证终端组件显示
3. 验证 WebSocket 连接状态为已连接
4. 观察智能体输出实时显示
5. 在终端输入命令，验证响应

**验证脚本**：
```python
# tests/test_websocket.py
import pytest
from fastapi.testclient import TestClient

def test_websocket_connection():
    client = TestClient(app)
    with client.websocket_connect("/ws/session/1") as websocket:
        # 发送消息
        websocket.send_json({"type": "command", "data": "echo hello"})
        # 接收响应
        response = websocket.receive_json()
        assert "hello" in response["data"]
```

---

### 4.4 前端智能体配置页面

**实现内容**：
- 智能体列表（对接 API）
- 智能体创建/编辑表单
- 智能体角色选择
- 智能体类型选择

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 4.4.1 | 显示智能体列表 | 页面显示已配置智能体 |
| 4.4.2 | 创建智能体 | 选择角色、类型，保存成功 |
| 4.4.3 | 编辑智能体 | 修改配置，保存成功 |
| 4.4.4 | 删除智能体 | 删除后智能体消失 |

**验证步骤**：
1. 进入智能体配置页面
2. 点击「添加智能体」
3. 选择角色（如：前端开发）
4. 选择类型（如：Claude）
5. 保存，验证智能体出现在列表
6. 点击编辑，修改名称
7. 保存，验证修改生效

---

## 阶段五：工作流管理

### 5.1 工作流数据模型

**实现内容**：
- PhaseTransitionRule 实体
- 工作流配置存储

**数据模型**：
```python
class PhaseTransitionRule:
    id: int
    project_id: int
    from_status: TaskStatus
    to_status: TaskStatus
    condition: dict | None  # 触发条件
    auto_trigger: bool
    created_at: datetime
```

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 5.1.1 | 创建流转规则 | `POST /api/projects/{projectId}/rules` |
| 5.1.2 | 获取项目规则 | `GET /api/projects/{projectId}/rules` |
| 5.1.3 | 删除规则 | `DELETE /api/rules/{id}` |

**验证命令**：
```bash
# 创建规则
curl -X POST http://localhost:8080/api/projects/1/rules \
  -H "Content-Type: application/json" \
  -d '{"from_status": "TODO", "to_status": "IN_PROGRESS", "auto_trigger": false}'

# 运行测试
uv run pytest tests/test_workflow_api.py -v
```

---

### 5.2 前端工作流可视化

**实现内容**：
- 工作流时间线组件（对接 API）
- 节点点击交互
- 工作流配置对话框

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 5.2.1 | 显示工作流图 | 节点和连线正确显示 |
| 5.2.2 | 节点高亮 | 当前状态节点高亮 |
| 5.2.3 | 添加流转规则 | 配置新规则，保存成功 |
| 5.2.4 | 删除流转规则 | 删除后连线消失 |

**验证步骤**：
1. 打开任务详情
2. 查看工作流时间线
3. 验证当前状态节点高亮
4. 点击「配置工作流」
5. 添加新的流转规则
6. 保存，验证新连线出现

---

## 阶段六：任务来源集成

### 6.1 TaskSource 数据模型

**实现内容**：
- TaskSource 实体定义
- TaskSourceAdapter SPI 接口
- 本地任务源适配器

**数据模型**：
```python
class TaskSource:
    id: int
    project_id: int
    name: str
    source_type: str  # local, github, jira
    config: dict      # 源配置
    sync_interval: int | None  # 同步间隔(分钟)
    last_sync: datetime | None
    created_at: datetime
```

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 6.1.1 | 创建任务源 | `POST /api/projects/{projectId}/sources` |
| 6.1.2 | 获取任务源 | `GET /api/projects/{projectId}/sources` |
| 6.1.3 | 测试连接 | `POST /api/sources/{id}/test` |
| 6.1.4 | 手动同步 | `POST /api/sources/{id}/sync` |
| 6.1.5 | 删除任务源 | `DELETE /api/sources/{id}` |

**验证命令**：
```bash
# 创建本地任务源
curl -X POST http://localhost:8080/api/projects/1/sources \
  -H "Content-Type: application/json" \
  -d '{"name": "本地任务", "source_type": "local", "config": {}}'

# 测试连接
curl -X POST http://localhost:8080/api/sources/1/test
# 预期: {"success": true, "message": "连接成功"}

# 运行测试
uv run pytest tests/test_tasksource_api.py -v
```

---

### 6.2 GitHub 集成

**实现内容**：
- GitHub Issues 适配器
- OAuth 认证流程
- Issue 同步逻辑

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 6.2.1 | GitHub OAuth | 授权后获取 access_token |
| 6.2.2 | 获取 Issues | 同步 GitHub Issues 到任务 |
| 6.2.3 | 状态映射 | Issue 状态映射到任务状态 |

**验证步骤**：
1. 配置 GitHub 任务源
2. 点击「授权 GitHub」
3. 完成 OAuth 授权
4. 点击「同步」
5. 验证 GitHub Issues 出现在任务列表

---

### 6.3 前端任务源配置

**实现内容**：
- 任务源列表（对接 API）
- 任务源创建向导
- 连接测试按钮
- 同步状态显示

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 6.3.1 | 显示任务源列表 | 页面显示已配置源 |
| 6.3.2 | 创建本地源 | 填写表单，保存成功 |
| 6.3.3 | 测试连接 | 点击按钮，显示成功/失败 |
| 6.3.4 | 手动同步 | 点击同步，任务更新 |

**验证步骤**：
1. 进入任务源配置页面
2. 点击「添加任务源」
3. 选择类型（本地/GitHub/Jira）
4. 填写配置
5. 点击「测试连接」
6. 验证连接成功
7. 保存，点击「同步」
8. 验证任务已同步

---

## 阶段七：Git 集成

### 7.1 Git 服务

**实现内容**：
- GitService 封装 GitPython
- Worktree 创建/删除
- 分支管理
- 提交操作

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 7.1.1 | Clone 仓库 | 克隆到指定目录 |
| 7.1.2 | 创建 Worktree | 创建隔离工作目录 |
| 7.1.3 | 列出分支 | 返回分支列表 |
| 7.1.4 | 切换分支 | Worktree 切换到指定分支 |
| 7.1.5 | 获取状态 | 返回 Git 状态 |
| 7.1.6 | 提交更改 | 创建 commit |
| 7.1.7 | 删除 Worktree | 清理工作目录 |

**验证命令**：
```bash
# 运行 Git 服务测试
uv run pytest tests/test_git_service.py -v

# 手动测试
# 1. 创建 worktree
curl -X POST http://localhost:8080/api/tasks/1/worktree \
  -H "Content-Type: application/json" \
  -d '{"branch": "feature/login"}'

# 2. 获取状态
curl http://localhost:8080/api/tasks/1/git-status
```

---

### 7.2 前端 Git 操作

**实现内容**：
- 分支选择器
- Worktree 创建对话框
- Git 状态显示
- Diff 查看器
- 提交对话框

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 7.2.1 | 选择分支 | 下拉显示所有分支 |
| 7.2.2 | 创建 Worktree | 点击创建，目录生成 |
| 7.2.3 | 查看 Diff | 显示代码差异 |
| 7.2.4 | 提交更改 | 填写消息，提交成功 |

**验证步骤**：
1. 在任务详情中点击「Git」标签
2. 选择分支
3. 点击「创建工作目录」
4. 验证目录创建成功
5. 修改代码后查看 Diff
6. 填写提交消息
7. 点击提交，验证成功

---

## 阶段八：AI 任务管家

### 8.1 管家服务

**实现内容**：
- ButlerService 管家逻辑
- 任务分析功能
- 进度计算
- 建议生成

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 8.1.1 | 获取项目概览 | `GET /api/projects/{id}/butler/overview` |
| 8.1.2 | 获取进度统计 | `GET /api/projects/{id}/butler/progress` |
| 8.1.3 | 获取建议 | `GET /api/projects/{id}/butler/suggestions` |
| 8.1.4 | 执行快捷操作 | `POST /api/butler/actions` |

**验证命令**：
```bash
# 获取项目概览
curl http://localhost:8080/api/projects/1/butler/overview
# 预期: {"tasks_total": 10, "tasks_done": 3, "progress": 30, ...}

# 运行测试
uv run pytest tests/test_butler_api.py -v
```

---

### 8.2 前端管家界面

**实现内容**：
- 聊天框组件（对接 API）
- 快捷操作按钮
- 进度显示
- 建议卡片

**测试用例**：
| ID | 测试内容 | 验证方法 |
|----|----------|----------|
| 8.2.1 | 显示聊天框 | 管家图标点击后显示 |
| 8.2.2 | 发送消息 | 输入问题，收到回复 |
| 8.2.3 | 快捷操作 | 点击按钮，执行操作 |
| 8.2.4 | 进度显示 | 显示项目进度百分比 |

**验证步骤**：
1. 点击右下角管家图标
2. 验证聊天框弹出
3. 输入「项目进度如何？」
4. 验证收到包含进度的回复
5. 点击快捷按钮「创建任务」
6. 验证任务创建对话框打开

---

## 测试覆盖率目标

| 模块 | 目标覆盖率 |
|------|-----------|
| 数据模型 | 100% |
| API 端点 | 90% |
| 业务服务 | 80% |
| 前端组件 | 70% |

## 持续集成

```yaml
# .github/workflows/test.yml
name: Test
on: [push, pull_request]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: astral-sh/setup-uv@v4
      - run: uv sync
      - run: uv run pytest --cov=app

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
      - run: cd frontend && npm ci
      - run: cd frontend && npm run test:run
```

---

## 里程碑

| 阶段 | 预计完成 | 交付物 |
|------|----------|--------|
| 阶段一 | - | 项目骨架、存储层 |
| 阶段二 | - | 项目管理完整功能 |
| 阶段三 | - | 看板系统完整功能 |
| 阶段四 | - | AI 智能体集成 |
| 阶段五 | - | 工作流管理 |
| 阶段六 | - | 外部任务源集成 |
| 阶段七 | - | Git 集成 |
| 阶段八 | - | AI 任务管家 |
