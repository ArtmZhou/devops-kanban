// 任务管家回复规则 - 关键词匹配
export const butlerResponses = {
  // 启动相关
  '启动': { action: 'start', response: '好的，我正在为您启动任务工作流...' },
  '开始': { action: 'start', response: '收到，任务工作流已开始执行。' },
  'start': { action: 'start', response: 'Starting the task workflow...' },

  // 暂停相关
  '暂停': { action: 'pause', response: '已暂停当前工作流。' },
  '停止': { action: 'stop', response: '工作流已停止。' },
  'pause': { action: 'pause', response: 'Workflow paused.' },
  'stop': { action: 'stop', response: 'Workflow stopped.' },

  // 状态查询 - 将由 getDetailedWorkflowStatus 处理
  '进度': { action: 'status', response: '__DETAILED_PROGRESS__' },
  '状态': { action: 'status', response: '__DETAILED_PROGRESS__' },
  '怎么样': { action: 'status', response: '__DETAILED_PROGRESS__' },
  'progress': { action: 'status', response: '__DETAILED_PROGRESS__' },
  'status': { action: 'status', response: '__DETAILED_PROGRESS__' },

  // 继续执行
  '继续': { action: 'continue', response: '好的，正在继续执行工作流...' },
  'continue': { action: 'continue', response: 'Continuing workflow execution...' },

  // 重试
  '重试': { action: 'retry', response: '好的，正在重试当前节点...' },
  'retry': { action: 'retry', response: 'Retrying current node...' },

  // 查看详情
  '详情': { action: 'details', response: '让我为您查看任务详情...\n\n任务名称: {taskTitle}\n状态: {status}\n进度: {progress}%\n当前阶段: {currentNode}' },
  'details': { action: 'details', response: 'Let me check the task details...\n\nTask: {taskTitle}\nStatus: {status}\nProgress: {progress}%\nCurrent Stage: {currentNode}' },

  // 帮助
  '帮助': { action: 'help', response: '我可以帮您：\n• 启动任务 - 说"启动"或"开始"\n• 暂停任务 - 说"暂停"\n• 查看进度 - 说"进度"或"状态"\n• 查看详情 - 说"详情"\n• 继续执行 - 说"继续"\n• 重试 - 说"重试"' },
  'help': { action: 'help', response: 'I can help you with:\n• Start task - say "start"\n• Pause task - say "pause"\n• View progress - say "progress" or "status"\n• View details - say "details"\n• Continue - say "continue"\n• Retry - say "retry"' },

  // 问候
  '你好': { action: 'greet', response: '您好！我是您的任务管家，有什么可以帮您的吗？' },
  'hello': { action: 'greet', response: 'Hello! I am your task butler, how can I help you?' },
  'hi': { action: 'greet', response: 'Hi! Ready to help you manage your task.' }
}

// 获取工作流详细执行状态
export const getDetailedWorkflowStatus = (workflow, task, locale = 'zh') => {
  if (!workflow || !workflow.stages || workflow.stages.length === 0) {
    if (locale === 'en') {
      return 'No workflow configured for this task yet. Please assign a workflow first.'
    }
    return '该任务尚未配置工作流，请先分配工作流。'
  }

  const progress = getWorkflowProgress(workflow)
  const currentNode = getCurrentNodeName(workflow)

  // 收集各阶段和节点的执行情况
  const stageDetails = []
  let completedCount = 0
  let totalCount = 0
  let inProgressCount = 0
  let pendingCount = 0

  workflow.stages.forEach((stage, stageIndex) => {
    const stageInfo = {
      name: stage.name || (locale === 'en' ? `Stage ${stageIndex + 1}` : `阶段 ${stageIndex + 1}`),
      nodes: []
    }

    if (stage.nodes) {
      stage.nodes.forEach(node => {
        // 处理并行节点（父节点）
        if (node.isParent && node.childNodes) {
          node.childNodes.forEach(childNode => {
            totalCount++
            const statusIcon = getNodeStatusIcon(childNode.status)
            if (childNode.status === 'DONE') completedCount++
            else if (childNode.status === 'IN_PROGRESS') inProgressCount++
            else pendingCount++

            stageInfo.nodes.push({
              name: childNode.name,
              status: childNode.status,
              agentName: childNode.agentName,
              icon: statusIcon
            })
          })
        } else {
          totalCount++
          const statusIcon = getNodeStatusIcon(node.status)
          if (node.status === 'DONE') completedCount++
          else if (node.status === 'IN_PROGRESS') inProgressCount++
          else pendingCount++

          stageInfo.nodes.push({
            name: node.name,
            status: node.status,
            agentName: node.agentName,
            icon: statusIcon
          })
        }
      })
    }

    stageDetails.push(stageInfo)
  })

  // 构建详细的状态报告
  if (locale === 'en') {
    let report = `📊 **Workflow Execution Status**\n\n`
    report += `**Task:** ${task?.title || 'Unknown'}\n`
    report += `**Overall Progress:** ${progress}% (${completedCount}/${totalCount} nodes completed)\n`
    report += `**Status:** ${completedCount === totalCount ? '✅ Completed' : inProgressCount > 0 ? '🔄 In Progress' : '⏳ Pending'}\n\n`

    report += `---\n\n`
    report += `**Execution Details:**\n\n`

    stageDetails.forEach((stage, index) => {
      report += `**${stage.name}:**\n`
      stage.nodes.forEach(node => {
        const statusText = node.status === 'DONE' ? 'Completed' :
                          node.status === 'IN_PROGRESS' ? 'In Progress' :
                          node.status === 'FAILED' ? 'Failed' :
                          node.status === 'REJECTED' ? 'Rejected' : 'Pending'
        report += `  ${node.icon} ${node.name} (${node.agentName}) - ${statusText}\n`
      })
      if (index < stageDetails.length - 1) report += `\n`
    })

    if (inProgressCount > 0) {
      report += `\n📍 **Currently executing:** ${currentNode}`
    }

    return report
  }

  // 中文报告
  let report = `📊 **工作流执行状态**\n\n`
  report += `**任务:** ${task?.title || '未知任务'}\n`
  report += `**整体进度:** ${progress}% (${completedCount}/${totalCount} 个节点已完成)\n`
  report += `**状态:** ${completedCount === totalCount ? '✅ 已完成' : inProgressCount > 0 ? '🔄 执行中' : '⏳ 待执行'}\n\n`

  report += `---\n\n`
  report += `**执行详情:**\n\n`

  stageDetails.forEach((stage, index) => {
    report += `**${stage.name}:**\n`
    stage.nodes.forEach(node => {
      const statusText = node.status === 'DONE' ? '已完成' :
                        node.status === 'IN_PROGRESS' ? '执行中' :
                        node.status === 'FAILED' ? '失败' :
                        node.status === 'REJECTED' ? '已打回' : '待执行'
      report += `  ${node.icon} ${node.name} (${node.agentName}) - ${statusText}\n`
    })
    if (index < stageDetails.length - 1) report += `\n`
  })

  if (inProgressCount > 0) {
    report += `\n📍 **当前正在执行:** ${currentNode}`
  }

  return report
}

// 获取节点状态图标
const getNodeStatusIcon = (status) => {
  const icons = {
    'DONE': '✅',
    'IN_PROGRESS': '🔄',
    'PENDING': '⏳',
    'TODO': '⏳',
    'FAILED': '❌',
    'REJECTED': '↩️'
  }
  return icons[status] || '⏳'
}

// 管家欢迎消息
export const getButlerWelcomeMessage = (taskTitle, locale = 'zh') => {
  if (locale === 'en') {
    return {
      id: 'welcome',
      role: 'assistant',
      content: `Hello! I am the task butler, here to help you manage the execution of "${taskTitle}".

You can tell me:
• "start" - Start the workflow
• "pause" - Pause current execution
• "progress" - Check current progress
• "help" - View more commands`,
      timestamp: new Date().toISOString()
    }
  }

  return {
    id: 'welcome',
    role: 'assistant',
    content: `您好！我是任务管家，负责协助您管理「${taskTitle}」的执行。

您可以对我说：
• "启动" - 开始执行工作流
• "暂停" - 暂停当前执行
• "进度" - 查看当前进度
• "帮助" - 查看更多指令`,
    timestamp: new Date().toISOString()
  }
}

// 计算工作流进度
export const getWorkflowProgress = (workflow) => {
  if (!workflow || !workflow.stages) return 0

  let totalNodes = 0
  let completedNodes = 0

  workflow.stages.forEach(stage => {
    if (stage.nodes) {
      stage.nodes.forEach(node => {
        // 处理并行节点（父节点）
        if (node.isParent && node.childNodes) {
          node.childNodes.forEach(childNode => {
            totalNodes++
            if (childNode.status === 'DONE') completedNodes++
          })
        } else {
          totalNodes++
          if (node.status === 'DONE') completedNodes++
        }
      })
    }
  })

  if (totalNodes === 0) return 0
  return Math.round((completedNodes / totalNodes) * 100)
}

// 获取当前执行的节点名称
export const getCurrentNodeName = (workflow) => {
  if (!workflow || !workflow.stages) return '无'

  for (const stage of workflow.stages) {
    if (stage.nodes) {
      for (const node of stage.nodes) {
        // 处理并行节点
        if (node.isParent && node.childNodes) {
          for (const childNode of node.childNodes) {
            if (childNode.status === 'IN_PROGRESS') {
              return childNode.name
            }
          }
        } else if (node.status === 'IN_PROGRESS') {
          return node.name
        }
      }
    }
  }

  return '未开始'
}

// 模拟管家处理用户输入
export const processButlerInput = (input, task, workflow, locale = 'zh') => {
  const lowerInput = input.toLowerCase().trim()

  // 匹配关键词并返回对应操作
  for (const [keyword, config] of Object.entries(butlerResponses)) {
    if (lowerInput.includes(keyword.toLowerCase())) {
      // 特殊处理进度查询，返回详细信息
      if (config.response === '__DETAILED_PROGRESS__') {
        return {
          action: config.action,
          response: getDetailedWorkflowStatus(workflow, task, locale)
        }
      }

      const progress = getWorkflowProgress(workflow)
      const currentNode = getCurrentNodeName(workflow)

      return {
        action: config.action,
        response: config.response
          .replace('{progress}', progress)
          .replace('{status}', task?.status || 'TODO')
          .replace('{currentNode}', currentNode)
          .replace('{taskTitle}', task?.title || '未知任务')
      }
    }
  }

  // 默认回复
  if (locale === 'en') {
    return {
      action: 'unknown',
      response: "Sorry, I didn't understand that. Say 'help' to see what I can do."
    }
  }

  return {
    action: 'unknown',
    response: '抱歉，我没有理解您的意思。请说"帮助"查看我能做什么。'
  }
}

// 获取快捷操作按钮配置
export const getQuickActions = (task, workflow, locale = 'zh') => {
  const progress = getWorkflowProgress(workflow)
  const isRunning = task?.status === 'IN_PROGRESS'
  const isCompleted = task?.status === 'DONE'
  const isPending = task?.status === 'TODO'

  if (locale === 'en') {
    return [
      { id: 'start', label: 'Start', icon: 'play', disabled: isRunning || isCompleted, action: 'start' },
      { id: 'pause', label: 'Pause', icon: 'pause', disabled: !isRunning, action: 'pause' },
      { id: 'progress', label: 'Progress', icon: 'chart', disabled: false, action: 'status' },
      { id: 'help', label: 'Help', icon: 'help', disabled: false, action: 'help' }
    ]
  }

  return [
    { id: 'start', label: '启动', icon: 'play', disabled: isRunning || isCompleted, action: 'start' },
    { id: 'pause', label: '暂停', icon: 'pause', disabled: !isRunning, action: 'pause' },
    { id: 'progress', label: '进度', icon: 'chart', disabled: false, action: 'status' },
    { id: 'help', label: '帮助', icon: 'help', disabled: false, action: 'help' }
  ]
}

// 根据操作获取响应
export const getResponseForAction = (action, task, workflow, locale = 'zh') => {
  // 特殊处理进度查询
  if (action === 'status') {
    return {
      action: 'status',
      response: getDetailedWorkflowStatus(workflow, task, locale)
    }
  }

  // 找到对应的响应模板
  const keywordMap = {
    'start': ['启动', 'start'],
    'pause': ['暂停', 'pause'],
    'help': ['帮助', 'help'],
    'continue': ['继续', 'continue'],
    'retry': ['重试', 'retry'],
    'details': ['详情', 'details']
  }

  for (const keywords of Object.values(keywordMap)) {
    if (keywords.includes(action)) {
      // 使用第一个关键词查找响应
      const response = butlerResponses[keywords[0]]
      if (response) {
        const progress = getWorkflowProgress(workflow)
        const currentNode = getCurrentNodeName(workflow)

        return {
          action: response.action,
          response: response.response
            .replace('{progress}', progress)
            .replace('{status}', task?.status || 'TODO')
            .replace('{currentNode}', currentNode)
            .replace('{taskTitle}', task?.title || '未知任务')
        }
      }
    }
  }

  // 默认响应
  return processButlerInput(action, task, workflow, locale)
}
