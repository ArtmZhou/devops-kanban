# DevOps Kanban

一个现代化的 DevOps 看板系统，支持 AI 代理自动执行任务。参考 [vibe-kanban](https://github.com/BloopAI/vibe-kanban) 设计理念，使用 Java + Vue 3 实现。

## ✨ 功能特性

### 📋 项目管理
- 创建和管理多个项目
- 每个项目关联独立的 Git 仓库
- 自动验证仓库有效性

### 📊 看板任务管理
- Kanban 风格的任务看板
- 任务状态追踪：TODO → IN_PROGRESS → DONE
- 支持任务优先级设置
- 任务标签和分配

### 🔌 任务源集成
- **GitHub Issues** - 从 GitHub Issues 同步任务
- **Local** - 本地手动创建任务
- 可扩展 SPI 接口，支持添加 Jira、GitLab、Trello 等

### 🤖 AI 代理执行
- **Claude Code** - 使用 Claude CLI 执行任务
- **OpenAI Codex** - 使用 Codex 执行任务
- 可扩展 SPI 接口，支持添加更多 AI 代理

### 🌳 Git Worktree 隔离
- 每个任务在独立的 Git worktree 中执行
- 支持多个任务并行执行
- 自动创建和清理 worktree

## 🛠 技术栈

| 组件 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5 |
| Java 版本 | Java 17 |
| 前端框架 | Vue 3.4 |
| 构建工具 | Vite 5.0 |
| HTTP 客户端 | Axios |
| 数据存储 | 文件存储 (JSON) |
| 路由 | Vue Router 4 |

## 🚀 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.6+

### 启动后端

```bash
# 克隆项目
git clone <repository-url>
cd devops-kanban

# 启动 Spring Boot
mvn spring-boot:run
```

后端服务将在 http://localhost:8080 启动

### 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务将在 http://localhost:5173 启动

### 访问应用

打开浏览器访问 http://localhost:5173

## 📁 项目结构

```
devops-kanban/
├── src/main/java/com/devops/kanban/
│   ├── DevopsKanbanApplication.java    # 启动类
│   ├── config/                          # 配置类
│   ├── controller/                      # REST 控制器
│   │   ├── ProjectController.java
│   │   ├── TaskController.java
│   │   ├── TaskSourceController.java
│   │   ├── AgentController.java
│   │   └── ExecutionController.java
│   ├── service/                         # 业务逻辑层
│   │   ├── ProjectService.java
│   │   ├── TaskService.java
│   │   ├── GitService.java
│   │   ├── AgentService.java
│   │   └── AgentExecutionService.java
│   ├── repository/                      # 数据访问层
│   │   └── impl/                        # 文件存储实现
│   ├── entity/                          # 实体类
│   │   ├── Project.java
│   │   ├── Task.java
│   │   ├── TaskSource.java
│   │   ├── Agent.java
│   │   └── Execution.java
│   ├── dto/                             # 数据传输对象
│   ├── spi/                             # SPI 接口
│   │   ├── TaskSourceAdapter.java
│   │   └── AgentAdapter.java
│   └── adapter/                         # SPI 实现
│       ├── tasksource/
│       │   ├── GitHubIssuesAdapter.java
│       │   └── LocalTaskAdapter.java
│       └── agent/
│           ├── ClaudeCodeAdapter.java
│           └── CodexAdapter.java
├── src/main/resources/
│   └── application.yml                  # 应用配置
├── frontend/                            # Vue 3 前端
│   ├── src/
│   │   ├── views/                       # 页面组件
│   │   ├── components/                  # 通用组件
│   │   ├── api/                         # API 模块
│   │   └── router/                      # 路由配置
│   ├── package.json
│   └── vite.config.js
├── API.md                               # API 文档
├── PLAN.md                              # 项目规划
└── pom.xml                              # Maven 配置
```

## 📖 API 文档

详细的 API 文档请参阅 [API.md](API.md)

### 主要端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/projects` | GET, POST | 项目管理 |
| `/api/tasks` | GET, POST, PUT | 任务管理 |
| `/api/task-sources` | GET, POST | 任务源配置 |
| `/api/agents` | GET, POST | AI 代理配置 |
| `/api/executions` | POST, GET | 执行任务 |

## ⚙️ 配置说明

### 后端配置 (`application.yml`)

```yaml
server:
  port: 8080

app:
  storage:
    path: ./data           # 数据存储路径
  cors:
    origins: http://localhost:5173,http://localhost:3000
```

### 前端配置 (`vite.config.js`)

前端通过代理连接后端 API：

```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}
```

## 🔧 开发指南

### 添加新的任务源适配器

1. 实现 `TaskSourceAdapter` 接口
2. 添加 `@Component` 注解
3. 系统会自动发现并注册

```java
@Component
public class JiraAdapter implements TaskSourceAdapter {
    @Override
    public TaskSource.TaskSourceType getType() {
        return TaskSource.TaskSourceType.JIRA;
    }
    // ... 实现其他方法
}
```

### 添加新的 AI 代理适配器

1. 实现 `AgentAdapter` 接口
2. 添加 `@Component` 注解
3. 系统会自动发现并注册

```java
@Component
public class CursorAdapter implements AgentAdapter {
    @Override
    public Agent.AgentType getType() {
        return Agent.AgentType.CURSOR;
    }
    // ... 实现其他方法
}
```

### 运行测试

```bash
# 后端测试
mvn test

# 构建生产版本
mvn package

# 前端构建
cd frontend && npm run build
```

## 📊 数据模型

```
Project (项目)
├── id: Long
├── name: String
├── description: String
└── createdAt/updatedAt

Task (任务)
├── id: Long
├── projectId: Long
├── title: String
├── description: String
├── status: TODO | IN_PROGRESS | DONE | BLOCKED | CANCELLED
├── priority: LOW | MEDIUM | HIGH | CRITICAL
└── assignee, tags, dueDate...

TaskSource (任务源)
├── id: Long
├── projectId: Long
├── type: GITHUB | LOCAL | JIRA...
├── config: JSON
└── syncStatus, lastSyncAt...

Agent (AI代理)
├── id: Long
├── projectId: Long
├── type: CLAUDE | CODEX...
├── command: String
└── config: JSON

Execution (执行记录)
├── id: Long
├── taskId: Long
├── agentId: Long
├── status: PENDING | RUNNING | SUCCESS | FAILED | CANCELLED
├── output: String
└── worktreePath, branch...
```

## 📝 License

MIT License

## 🙏 致谢

- 灵感来源: [vibe-kanban](https://github.com/BloopAI/vibe-kanban)
- AI 代理: [Claude Code](https://claude.ai/code)
