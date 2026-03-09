// Agent类型配置
export const agentConfig = {
  CLAUDE: { color: "#8B5CF6", icon: "🤖", name: "Claude" },
  CODEX: { color: "#10B981", icon: "💻", name: "Codex" },
  CURSOR: { color: "#F59E0B", icon: "✨", name: "Cursor" },
  GEMINI: { color: "#3B82F6", icon: "🔮", name: "Gemini" }
}

// 节点状态配置
export const nodeStatusConfig = {
  DONE: { color: "#10B981", icon: "✓" },
  IN_PROGRESS: { color: "#3B82F6", icon: "▶" },
  PENDING: { color: "#6B7280", icon: "○" }
}

// Mock Workflow数据 - 支持并行结构
export const mockWorkflows = [
  {
    id: 1,
    name: "用户认证功能开发",
    projectId: 1,
    currentNodeId: 6,  // 当前进行到的节点
    // 使用 stage 结构，每个 stage 可以包含多个并行节点
    stages: [
      {
        id: "stage-1",
        name: "需求与设计",
        order: 1,
        nodes: [
          {
            id: 1,
            name: "需求分析",
            role: "业务分析师",
            agentType: "CLAUDE",
            agentName: "Claude Architect",
            status: "DONE",
            duration: 15,
            task: {
              id: 101,
              title: "认证需求分析",
              description: "分析用户认证系统的功能需求，输出需求文档",
              status: "DONE"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "请分析用户认证需求，我们需要实现一个安全的登录系统。"
              },
              {
                id: 2,
                from: "assistant",
                content: "基于项目需求，我建议采用JWT方案。以下是需求分析结果：\n\n1. **登录方式**\n   - 用户名/密码登录\n   - 邮箱验证码登录\n   - 第三方OAuth登录\n\n2. **安全要求**\n   - 密码加密存储（bcrypt）\n   - Token过期机制\n   - 防暴力破解\n\n3. **性能要求**\n   - 响应时间 < 500ms\n   - 支持高并发\n\n需求文档已生成，可以进入下一阶段。"
              }
            ]
          }
        ]
      },
      {
        id: "stage-2",
        name: "并行开发",
        order: 2,
        parallel: true,  // 标记为并行阶段
        nodes: [
          {
            id: 2,
            name: "数据库设计",
            role: "数据库工程师",
            agentType: "GEMINI",
            agentName: "Gemini DB",
            status: "DONE",
            duration: 20,
            task: {
              id: 102,
              title: "用户表设计",
              description: "设计用户相关数据表结构",
              status: "DONE"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "根据需求分析结果，请设计用户认证相关的数据库表结构。"
              },
              {
                id: 2,
                from: "assistant",
                content: "好的，基于需求分析，我设计了以下表结构：\n\n```sql\nCREATE TABLE users (\n  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n  username VARCHAR(50) UNIQUE NOT NULL,\n  email VARCHAR(100) UNIQUE NOT NULL,\n  password_hash VARCHAR(255) NOT NULL,\n  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n);\n```\n\n表结构已优化索引，支持高并发查询。"
              }
            ]
          },
          {
            id: 3,
            name: "API设计",
            role: "后端架构师",
            agentType: "CLAUDE",
            agentName: "Claude API",
            status: "DONE",
            duration: 18,
            task: {
              id: 103,
              title: "API接口设计",
              description: "设计RESTful API接口规范",
              status: "DONE"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "请设计认证模块的API接口规范。"
              },
              {
                id: 2,
                from: "assistant",
                content: "已设计以下API接口：\n\n**认证接口**\n- POST /api/auth/register - 用户注册\n- POST /api/auth/login - 用户登录\n- POST /api/auth/refresh - 刷新Token\n- POST /api/auth/logout - 登出\n\n**用户接口**\n- GET /api/users/me - 获取当前用户\n- PUT /api/users/me - 更新用户信息\n\n接口文档已生成，遵循RESTful规范。"
              }
            ]
          },
          {
            id: 4,
            name: "前端框架",
            role: "前端开发",
            agentType: "CURSOR",
            agentName: "Cursor FE",
            status: "DONE",
            duration: 12,
            task: {
              id: 104,
              title: "前端框架搭建",
              description: "搭建登录模块前端框架",
              status: "DONE"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "请搭建登录模块的前端框架。"
              },
              {
                id: 2,
                from: "assistant",
                content: "前端框架已搭建完成：\n\n✅ 创建登录页面组件\n✅ 配置路由守卫\n✅ 封装认证API\n✅ 状态管理配置\n\n可以开始具体开发了。"
              }
            ]
          }
        ]
      },
      {
        id: "stage-3",
        name: "实现阶段",
        order: 3,
        parallel: true,  // 并行开发
        nodes: [
          {
            id: 5,
            name: "后端开发",
            role: "后端开发",
            agentType: "CODEX",
            agentName: "Codex Backend",
            status: "DONE",
            duration: 45,
            task: {
              id: 105,
              title: "认证API实现",
              description: "实现登录、注册、Token验证等API",
              status: "DONE"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "请根据API设计实现后端接口。"
              },
              {
                id: 2,
                from: "assistant",
                content: "后端API已全部实现：\n\n✅ 用户注册接口\n✅ 用户登录接口\n✅ Token刷新接口\n✅ Token验证中间件\n✅ 参数校验\n✅ 异常处理\n\n单元测试覆盖率 85%。"
              }
            ]
          },
          {
            id: 6,
            name: "前端开发",
            role: "前端开发",
            agentType: "CURSOR",
            agentName: "Cursor Frontend",
            status: "IN_PROGRESS",
            task: {
              id: 106,
              title: "登录页面开发",
              description: "开发登录、注册页面，对接后端API",
              status: "IN_PROGRESS"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "请开发登录和注册页面。"
              },
              {
                id: 2,
                from: "assistant",
                content: "正在开发登录页面：\n\n✅ 登录表单组件\n✅ 表单验证\n🔄 API对接中...\n⏳ 注册页面\n⏳ 找回密码\n\n预计还需 15 分钟完成。"
              },
              {
                id: 3,
                from: "user",
                content: "请确保有良好的错误提示和加载状态。"
              },
              {
                id: 4,
                from: "assistant",
                content: "已添加：\n\n✅ 登录失败错误提示\n✅ 网络错误处理\n✅ 按钮加载状态\n✅ 表单提交防抖\n\n继续完善中..."
              }
            ]
          }
        ]
      },
      {
        id: "stage-4",
        name: "测试与部署",
        order: 4,
        nodes: [
          {
            id: 7,
            name: "集成测试",
            role: "测试工程师",
            agentType: "CLAUDE",
            agentName: "Claude QA",
            status: "PENDING",
            task: {
              id: 107,
              title: "功能集成测试",
              description: "完成功能测试",
              status: "TODO"
            },
            messages: []
          },
          {
            id: 8,
            name: "部署上线",
            role: "DevOps",
            agentType: "GEMINI",
            agentName: "Gemini DevOps",
            status: "PENDING",
            task: {
              id: 108,
              title: "CI/CD部署",
              description: "配置CI/CD流水线并部署",
              status: "TODO"
            },
            messages: []
          }
        ]
      }
    ]
  },
  {
    id: 2,
    name: "报表统计功能",
    projectId: 2,
    currentNodeId: 11,
    stages: [
      {
        id: "stage-a",
        name: "需求调研",
        order: 1,
        nodes: [
          {
            id: 9,
            name: "需求调研",
            role: "业务分析师",
            agentType: "CLAUDE",
            agentName: "Claude Analyst",
            status: "DONE",
            duration: 10,
            task: {
              id: 201,
              title: "报表需求调研",
              description: "调研用户需要的报表类型",
              status: "DONE"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "请调研报表统计功能的需求。"
              },
              {
                id: 2,
                from: "assistant",
                content: "经过调研，确定以下报表需求：\n\n1. 任务完成统计\n2. 工时分析报表\n3. 项目进度报表\n4. 团队效率指标"
              }
            ]
          }
        ]
      },
      {
        id: "stage-b",
        name: "并行开发",
        order: 2,
        parallel: true,
        nodes: [
          {
            id: 10,
            name: "图表组件",
            role: "前端开发",
            agentType: "CURSOR",
            agentName: "Cursor Charts",
            status: "IN_PROGRESS",
            task: {
              id: 202,
              title: "图表组件开发",
              description: "使用ECharts开发图表组件",
              status: "IN_PROGRESS"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "请开发报表所需的图表组件。"
              },
              {
                id: 2,
                from: "assistant",
                content: "正在开发：\n\n✅ 柱状图组件\n✅ 折线图组件\n🔄 饼图组件（进行中）"
              }
            ]
          },
          {
            id: 11,
            name: "数据服务",
            role: "后端开发",
            agentType: "CODEX",
            agentName: "Codex Analytics",
            status: "IN_PROGRESS",
            task: {
              id: 203,
              title: "数据聚合API",
              description: "实现报表数据聚合逻辑",
              status: "IN_PROGRESS"
            },
            messages: [
              {
                id: 1,
                from: "user",
                content: "请实现报表数据聚合API。"
              },
              {
                id: 2,
                from: "assistant",
                content: "正在实现：\n\n✅ 任务统计聚合\n🔄 工时计算（进行中）\n⏳ 趋势数据"
              }
            ]
          }
        ]
      }
    ]
  }
]

// 扁平化获取所有节点（用于查找）
export function getAllNodes(workflow) {
  if (!workflow?.stages) return []
  return workflow.stages.flatMap(stage => stage.nodes)
}

// 获取指定项目的workflow
export function getWorkflowByProject(projectId) {
  return mockWorkflows.find(w => w.projectId === projectId)
}

// 获取指定workflow
export function getWorkflowById(workflowId) {
  return mockWorkflows.find(w => w.id === workflowId)
}

// 获取节点详情
export function getNodeById(workflowId, nodeId) {
  const workflow = getWorkflowById(workflowId)
  if (!workflow) return null
  const allNodes = getAllNodes(workflow)
  return allNodes.find(n => n.id === nodeId)
}

// 计算工作流进度
export function getWorkflowProgress(workflow) {
  const allNodes = getAllNodes(workflow)
  if (allNodes.length === 0) return { completed: 0, total: 0, percent: 0 }
  const completed = allNodes.filter(n => n.status === 'DONE').length
  return {
    completed,
    total: allNodes.length,
    percent: Math.round((completed / allNodes.length) * 100)
  }
}
