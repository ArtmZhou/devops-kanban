<template>
  <div class="workspace-view">
    <!-- Left: Task List -->
    <div class="workspace-left" :style="{ width: leftWidth + 'px', minWidth: leftWidth + 'px' }">
      <div class="workspace-header">
        <h3>任务列表</h3>
        <span class="task-count">{{ tasks.length }}</span>
      </div>
      <div class="task-list">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="task-card"
          :class="{ 'selected': selectedTask?.id === task.id }"
          @click="selectTask(task)"
        >
          <div class="task-card-header">
            <span class="task-priority" :class="priorityClass(task.priority)">{{ task.priority }}</span>
            <span class="task-status" :class="statusClass(task.status)">{{ task.status }}</span>
          </div>
          <div class="task-card-title">{{ task.title }}</div>
        </div>
      </div>
    </div>

    <div class="resize-handle" @mousedown="startResize('left')"></div>

    <!-- Right: 2-row layout -->
    <div class="workspace-right">
      <!-- Upper: Workflow + Operations | File List -->
      <div class="workspace-upper">
        <div class="workspace-upper-left">
          <!-- Pipeline DAG overview (top) -->
          <div class="panel-header">
            <h4>工作流链路</h4>
          </div>
          <div class="pipeline-inline" v-if="selectedTask && dagLayers.length">
            <div class="pipeline-dag-dialog">
              <div class="dag-flow">
                <template v-for="(layer, layerIdx) in dagLayers" :key="layerIdx">
                  <template v-for="node in layer" :key="node.id">
                    <div class="dag-column">
                      <div class="dag-node-wrapper" :class="{ current: node.id === selectedTask?.id, 'is-done': node.status === 'DONE', running: node.status === 'IN_PROGRESS', waiting: node.status === 'WAITING', failed: node.status === 'BLOCKED' || node.status === 'FAILED' }">
                        <div class="dag-node" @click="selectedTask = node">
                          <span class="dag-node-status">{{ statusIcon(node.status) }}</span>
                          <span class="dag-node-title">{{ node.title }}</span>
                          <span v-if="node.id === selectedTask?.id" class="current-badge">当前</span>
                        </div>
                      </div>
                    </div>
                  </template>
                  <div v-if="layerIdx < dagLayers.length - 1" class="dag-arrow-h"></div>
                </template>
              </div>
            </div>
          </div>
          <!-- Current workflow section -->
          <div class="current-workflow-section">
            <div class="panel-header">
              <h4>当前工作流</h4>
              <span class="current-wf-badge">用户服务工作流</span>
            </div>
            <div class="workflow-timeline">
              <div class="workflow-steps">
                <div
                  v-for="(step, index) in workflowSteps"
                  :key="step.id"
                  class="workflow-step-h"
                >
                  <div class="step-connector-h" v-if="index > 0"></div>
                  <div class="step-node-h" :class="step.statusClass">
                    <div class="step-dot-h" :class="step.statusClass"></div>
                    <div class="step-content-h">
                      <span class="step-name-h">{{ step.name }}</span>
                      <span class="step-agent-h">{{ step.agent }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="quick-actions">
              <el-button size="small" type="primary">启动</el-button>
              <el-button size="small">重试</el-button>
              <el-button size="small">合入</el-button>
              <el-button size="small">取消</el-button>
              <el-button size="small">刷新</el-button>
            </div>
          </div>
        </div>

        <div class="workspace-upper-right">
          <div class="panel-header">
            <h4>文件查看</h4>
            <span class="file-path-display" v-if="selectedFile">{{ selectedFile }}</span>
          </div>
          <div class="file-viewer">
            <div class="file-viewer-content">
              <div class="code-line" v-for="line in currentFileContent" :key="line.num">
                <span class="line-num">{{ line.num }}</span>
                <span class="line-content" :class="line.type">{{ line.content }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="resize-handle resize-handle-h" @mousedown="startResize('right')"></div>

      <!-- Lower: AI Split | Chat | File List -->
      <div class="workspace-lower">
        <div class="workspace-lower-left">
          <!-- AI Split Suggestion Card -->
          <AiSplitCard
            :suggestion="splitStore.pendingByTask.get(selectedTask?.id)"
            :task-id="selectedTask?.id"
            @update="(suggestions) => selectedTask?.id && splitStore.updateSuggestions(selectedTask.id, suggestions)"
            @confirm="onConfirmSplit"
            @dismiss="onDismissSplit"
          />

          <!-- Chat Panel -->
          <div class="chat-panel">
            <div class="panel-header">
              <h4>聊天</h4>
              <span class="agent-badge">{{ currentAgent }}</span>
            </div>
          <div class="chat-messages">
            <div class="chat-message user">
              <div class="message-bubble">
                请按照设计方案实现用户服务的 CRUD 接口。
              </div>
            </div>
            <div class="chat-message assistant">
              <div class="message-bubble">
                <p>好的，我来实现用户服务的 CRUD 接口。根据设计方案，我需要：</p>
                <ol>
                  <li>创建 User 实体和数据库迁移</li>
                  <li>实现 CRUD 接口（Create, Read, Update, Delete）</li>
                  <li>添加输入验证和错误处理</li>
                  <li>编写单元测试</li>
                </ol>
                <p>开始执行...</p>
              </div>
            </div>
            <div class="chat-message assistant">
              <div class="message-bubble">
                <p>已完成以下工作：</p>
                <ul>
                  <li>创建了 User 实体（id, username, email, createdAt, updatedAt）</li>
                  <li>实现了 4 个 RESTful 接口</li>
                  <li>添加了 Zod 输入验证</li>
                  <li>编写了 8 个单元测试</li>
                </ul>
                <p>所有测试通过 ✅</p>
              </div>
            </div>
          </div>
          <div class="chat-input">
            <textarea
              v-model="chatInput"
              placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
              @keydown="handleChatKeydown"
              rows="2"
            ></textarea>
            <el-button size="small" type="primary" :disabled="!chatInput.trim()">发送</el-button>
          </div>
          </div>
        </div>

        <div class="workspace-lower-right">
          <div class="panel-header">
            <h4>改动文件</h4>
            <span class="file-count-badge">3 个文件</span>
          </div>
          <div class="file-operations">
            <el-button size="small" type="primary">提交全部</el-button>
            <el-button size="small">推送</el-button>
            <el-button size="small">创建 MR</el-button>
            <el-button size="small">暂存</el-button>
          </div>
          <div class="file-list">
            <div
              v-for="file in changedFiles"
              :key="file.path"
              class="file-item"
              :class="{ 'selected': selectedFile === file.path }"
              @click="selectedFile = file.path"
            >
              <div class="file-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                </svg>
              </div>
              <div class="file-info">
                <span class="file-name">{{ file.name }}</span>
                <span class="file-path">{{ file.path }}</span>
              </div>
              <div class="file-stats">
                <span class="additions" v-if="file.additions">+{{ file.additions }}</span>
                <span class="deletions" v-if="file.deletions">-{{ file.deletions }}</span>
              </div>
            </div>
          </div>
          <div class="worktree-summary">
            <div class="worktree-status-line">
              <span class="status-dot clean"></span>
              <span class="status-label">已创建</span>
            </div>
            <div class="worktree-detail">
              <span class="detail-label">分支：</span>
              <span class="detail-value">feature/user-service</span>
            </div>
            <div class="worktree-detail">
              <span class="detail-label">路径：</span>
              <span class="detail-value">/tmp/worktrees/user-service</span>
            </div>
            <el-button size="small" type="danger" plain>删除 worktree</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AiSplitCard from '../components/workspace/AiSplitCard.vue'
import { useSplitSuggestionsStore } from '../stores/splitSuggestions.js'
import { listTasks, getTaskPipeline } from '../api/task.js'

const splitStore = useSplitSuggestionsStore()
const route = useRoute()
const projectId = computed(() => route.params.projectId ? Number(route.params.projectId) : null)

const tasks = ref([])
const pipeline = ref({ root: null, nodes: [] })

async function loadTasks() {
  try {
    const resp = projectId.value ? await listTasks({ project_id: projectId.value }) : await listTasks()
    if (resp?.success) tasks.value = resp.data || []
  } catch (e) {
    console.error('Failed to load tasks:', e)
    tasks.value = []
  }
  if (tasks.value.length && !selectedTask.value) {
    selectedTask.value = tasks.value[0]
  }
}

async function loadPipeline(taskId) {
  try {
    const resp = await getTaskPipeline(taskId)
    if (resp?.success) pipeline.value = resp.data || { root: null, nodes: [] }
    else pipeline.value = { root: null, nodes: [] }
  } catch (e) {
    pipeline.value = { root: null, nodes: [] }
  }
}

const workflowSteps = ref([])

const changedFiles = [
  { name: 'UserService.ts', path: 'src/services/', additions: 156, deletions: 0 },
  { name: 'user.ts', path: 'src/controllers/', additions: 89, deletions: 12 },
  { name: 'user.test.ts', path: 'src/services/__tests__/', additions: 234, deletions: 0 }
]

const chatInput = ref('')
const selectedFile = ref('src/services/UserService.ts')
const selectedTask = ref(null)
const expandedDagNodes = ref(new Set()) // track which DAG nodes are expanded

const toggleDagNode = (name) => {
  if (expandedDagNodes.value.has(name)) {
    expandedDagNodes.value.delete(name)
  } else {
    expandedDagNodes.value.add(name)
  }
}

// Pipeline DAG inline styles

const leftWidth = ref(280)
const rightWidth = ref(380)
const LEFT_MIN = 220
const LEFT_MAX = 500
const RIGHT_MIN = 260
const RIGHT_MAX = 700

let resizing = null

const startResize = (side) => (e) => {
  resizing = side
  const startX = e.clientX
  const startWidth = side === 'left' ? leftWidth.value : rightWidth.value

  const onMove = (e) => {
    const delta = side === 'left' ? e.clientX - startX : startX - e.clientX
    const newWidth = Math.max(
      side === 'left' ? LEFT_MIN : RIGHT_MIN,
      Math.min(side === 'left' ? LEFT_MAX : RIGHT_MAX, startWidth + delta)
    )
    if (side === 'left') leftWidth.value = newWidth
    else rightWidth.value = newWidth
  }

  const onUp = () => {
    resizing = null
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

const currentAgent = computed(() => {
  return selectedTask.value ? '后端工程师' : '请选择任务'
})

const currentFileContent = computed(() => {
  return [
    { num: 1, content: 'import { z } from "zod";', type: '' },
    { num: 2, content: '', type: '' },
    { num: 3, content: 'const CreateUserSchema = z.object({', type: '' },
    { num: 4, content: '  username: z.string().min(3).max(32),', type: 'addition' },
    { num: 5, content: '  email: z.string().email(),', type: 'addition' },
    { num: 6, content: '  password: z.string().min(8),', type: 'addition' },
    { num: 7, content: '  role: z.enum(["user", "admin"]).default("user"),', type: 'addition' },
    { num: 8, content: '});', type: '' },
    { num: 9, content: '', type: '' },
    { num: 10, content: 'export class UserService {', type: '' },
    { num: 11, content: '  async createUser(data: z.infer<typeof CreateUserSchema>) {', type: 'addition' },
    { num: 12, content: '    const validated = CreateUserSchema.parse(data);', type: 'addition' },
    { num: 13, content: '    const hashedPassword = await hash(validated.password);', type: 'addition' },
    { num: 14, content: '    return this.db.users.create({', type: 'addition' },
    { num: 15, content: '      ...validated,', type: 'addition' },
    { num: 16, content: '      password: hashedPassword,', type: 'addition' },
    { num: 17, content: '    });', type: 'addition' },
    { num: 18, content: '  }', type: '' },
    { num: 19, content: '', type: '' },
    { num: 20, content: '  async getUserById(id: number) {', type: 'addition' },
    { num: 21, content: '    return this.db.users.findUnique({ where: { id } });', type: 'addition' },
    { num: 22, content: '  }', type: '' },
    { num: 23, content: '}', type: '' },
  ]
})

const selectTask = (task) => {
  selectedTask.value = task
}

function priorityClass(priority) {
  const map = { CRITICAL: 'high', HIGH: 'high', MEDIUM: 'medium', LOW: '' }
  return map[priority] || ''
}

function statusClass(status) {
  const map = { DONE: 'done', IN_PROGRESS: 'running', WAITING: 'waiting', BLOCKED: 'waiting', FAILED: 'waiting', TODO: 'waiting', CANCELLED: 'waiting' }
  return map[status] || 'waiting'
}

const handleChatKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (chatInput.value.trim()) {
      chatInput.value = ''
    }
  }
}

async function onConfirmSplit() {
  if (!selectedTask.value?.id) return
  await splitStore.doConfirm(selectedTask.value.id)
  if (selectedTask.value.id) await splitStore.load(selectedTask.value.id)
}

async function onDismissSplit() {
  if (!selectedTask.value?.id) return
  await splitStore.doDismiss(selectedTask.value.id)
  if (selectedTask.value.id) await splitStore.load(selectedTask.value.id)
}

function statusIcon(status) {
  const icons = { DONE: '✓', IN_PROGRESS: '▶', WAITING: '○', BLOCKED: '✗', FAILED: '✗', TODO: '○', CANCELLED: '✗' }
  return icons[status] || '○'
}

const dagLayers = computed(() => {
  const nodes = pipeline.value.nodes || []
  if (!nodes.length) return []
  const depth = new Map()
  for (const n of nodes) depth.set(n.id, 0)
  let changed = true
  // Guard: if a cycle slips past backend validation, bound the relaxation
  // iterations so we never infinite-loop. Worst-case DAG needs O(V*E) passes,
  // so V^2 + 1 is a safe upper bound.
  let maxIter = nodes.length * nodes.length + 1
  while (changed && maxIter-- > 0) {
    changed = false
    for (const n of nodes) {
      for (const depId of (n.depends_on || [])) {
        const d = (depth.get(depId) ?? 0) + 1
        if (d > (depth.get(n.id) ?? 0)) {
          depth.set(n.id, d)
          changed = true
        }
      }
    }
  }
  const layers = []
  for (const n of nodes) {
    const d = depth.get(n.id) ?? 0
    if (!layers[d]) layers[d] = []
    layers[d].push(n)
  }
  return layers
})

// Load split suggestions when selected task changes
watch(() => selectedTask.value?.id, async (taskId) => {
  if (taskId) await splitStore.load(taskId)
}, { immediate: true })

onMounted(loadTasks)
watch(() => selectedTask.value?.id, async (newId) => {
  if (newId) await loadPipeline(newId)
})
</script>

<style scoped>
.workspace-view {
  display: flex;
  height: 100%;
  background: var(--page-bg);
  font-size: var(--font-size-sm);
}

/* Left: Task List */
.workspace-left {
  border-right: 1px solid var(--border-color);
  background: var(--bg-primary);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

/* Resize handles */
.resize-handle {
  width: 6px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  position: relative;
  z-index: 10;
}

.resize-handle::after {
  content: '';
  position: absolute;
  left: 2px;
  top: 0;
  width: 2px;
  height: 100%;
  background: transparent;
  transition: background 0.15s;
}

.resize-handle:hover::after,
.resize-handle:active::after {
  background: var(--accent-color);
}

.resize-handle-h {
  width: 100%;
  height: 6px;
  cursor: row-resize;
}

.resize-handle-h::after {
  left: 0;
  top: 2px;
  width: 100%;
  height: 2px;
}

.workspace-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.workspace-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.task-count {
  font-size: 12px;
  color: var(--text-muted);
  background: var(--bg-tertiary);
  padding: 2px 8px;
  border-radius: 10px;
}

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.task-card {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s ease;
  margin-bottom: 4px;
  border: 1px solid transparent;
  background: var(--bg-secondary);
}

.task-card:hover {
  background: var(--bg-tertiary);
  border-color: var(--border-color);
}

.task-card.selected {
  background: var(--teal-accent-mid);
  border-color: var(--teal-active-border);
  box-shadow: var(--shadow-sm);
}

.task-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.task-priority {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  text-transform: uppercase;
}

.task-priority.high {
  background: var(--danger-soft);
  color: var(--danger-strong);
}

.task-priority.medium {
  background: var(--warning-soft);
  color: var(--warning-strong);
}

.task-status {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
}

.task-status.done {
  background: var(--done-soft);
  color: var(--done-strong);
}

.task-status.running {
  background: var(--in-progress-soft);
  color: var(--in-progress-strong);
}

.task-status.waiting {
  background: var(--neutral-soft);
  color: var(--neutral-strong);
}

.task-card-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.4;
  margin-bottom: 6px;
}

.task-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.dependency-tag,
.downstream-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
}

/* Right: 2-row layout */
.workspace-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 0;
}

/* Upper row */
.workspace-upper {
  flex-shrink: 0;
  display: flex;
  border-bottom: 1px solid var(--border-color);
  max-height: 40vh;
}

.workspace-upper-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  min-width: 0;
}

.workspace-upper-right {
  width: v-bind(rightWidth + 'px');
  min-width: 320px;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
}

/* Lower row */
.workspace-lower {
  flex: 1;
  display: flex;
  min-height: 0;
}

.workspace-lower-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  min-width: 0;
}

/* Chat Panel */
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-top: 1px solid var(--border-color);
}

.workspace-lower-left .panel-header {
  border-top: none;
}

.workspace-lower-right {
  width: v-bind(rightWidth + 'px');
  min-width: 320px;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
}

/* Panel Header */
.panel-header {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  background: var(--bg-primary);
}

.panel-header h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0.02em;
}

.file-count-badge {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: auto;
}

.agent-badge {
  font-size: 11px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: auto;
}

.file-path-display {
  font-size: 11px;
  color: var(--text-secondary);
  margin-left: auto;
  font-family: monospace;
}

/* Workflow Timeline (horizontal, at bottom of upper-left) */
.workflow-timeline {
  padding: 12px 16px 16px;
  flex-shrink: 0;
  background: var(--bg-primary);
}

.current-workflow-section {
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  flex-shrink: 0;
}

.current-wf-badge {
  font-size: 11px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: auto;
}

/* Current workflow timeline (below header) */
.workflow-timeline {
  padding: 8px 16px 6px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-color);
}

.quick-actions {
  display: flex;
  gap: 6px;
  padding: 6px 16px 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.worktree-summary {
  padding: 10px 16px;
  flex-shrink: 0;
}

.worktree-status-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.clean {
  background: var(--done-strong);
}

.status-dot.dirty {
  background: var(--warning-strong);
}

.status-label {
  font-size: 12px;
  color: var(--text-primary);
}

.worktree-detail {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 2px;
}

.detail-label {
  color: var(--text-muted);
}

.detail-value {
  font-family: monospace;
  color: var(--text-secondary);
}

.worktree-summary .el-button {
  margin-top: 6px;
}

.workflow-steps {
  display: flex;
  align-items: center;
  gap: 0;
  overflow-x: auto;
  padding: 2px 0;
}

.workflow-step-h {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.step-connector-h {
  width: 28px;
  height: 2px;
  background: linear-gradient(90deg, var(--border-color), var(--border-color));
  margin: 0 2px;
  flex-shrink: 0;
  position: relative;
}

.step-connector-h::after {
  content: '';
  position: absolute;
  right: -3px;
  top: 50%;
  transform: translateY(-50%);
  border-left: 5px solid var(--border-color);
  border-top: 3px solid transparent;
  border-bottom: 3px solid transparent;
}

.step-node-h {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  background: var(--bg-secondary);
  white-space: nowrap;
  border: 1px solid transparent;
  transition: all 0.2s;
}

.step-node-h.done {
  background: var(--done-soft);
  border-color: var(--teal-border-soft);
}

.step-node-h.running {
  background: var(--in-progress-soft);
  border-color: var(--teal-active-border);
  box-shadow: 0 0 0 3px var(--teal-active-shadow);
}

.step-node-h.pending {
  background: var(--bg-secondary);
  border-color: var(--border-color);
  opacity: 0.7;
}

.step-dot-h {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.step-dot-h.done {
  background: var(--done-strong);
}

.step-dot-h.running {
  background: var(--in-progress-strong);
  box-shadow: 0 0 6px var(--green-shadow-soft);
}

.step-dot-h.pending {
  background: var(--text-muted);
  opacity: 0.4;
}

.step-content-h {
  display: flex;
  flex-direction: column;
}

.step-name-h {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
}

.step-agent-h {
  font-size: 10px;
  color: var(--text-muted);
}

/* Pipeline inline (DAG overview at top) */
.pipeline-inline {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 16px;
  min-height: 60px;
  overflow: auto;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}

.pipeline-inline .pipeline-dag-dialog {
  padding: 0;
}

.pipeline-inline .dag-row-horizontal {
  gap: 12px;
}

.pipeline-inline .dag-node {
  padding: 5px 10px;
}

.pipeline-inline .dag-node span {
  font-size: 11px;
}

.pipeline-inline .dag-steps-list {
  min-width: 80px;
}

.pipeline-inline .dag-step-item {
  font-size: 10px;
  padding: 1px 6px;
}

/* DAG flow layout with parallel groups */
.dag-flow {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
  padding: 4px 0;
}

.dag-column {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  flex-shrink: 0;
  width: 140px;
  gap: 4px;
}

.dag-parallel-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
  position: relative;
  padding: 4px 8px;
  background: var(--surface-tint);
  border-radius: var(--radius-sm);
  border: 1px dashed var(--teal-border-soft);
}

.dag-parallel-group::before {
  content: '并行';
  position: absolute;
  top: -9px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 9px;
  color: var(--accent-color);
  background: var(--bg-primary);
  padding: 0 6px;
  letter-spacing: 0.1em;
  font-weight: 600;
}

.dag-parallel-group .dag-column {
  width: 140px;
}

/* Pipeline DAG Dialog (keep for reference, can remove if not needed) */
.pipeline-dag-dialog {
  padding: 24px 16px;
  overflow-x: auto;
}

.dag-row-horizontal {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: nowrap;
}

.dag-node-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  position: relative;
}

.dag-node-wrapper.current::before {
  content: '';
  position: absolute;
  top: -3px;
  left: -3px;
  right: -3px;
  bottom: -3px;
  border: 2px solid var(--accent-color);
  border-radius: 8px;
  pointer-events: none;
  animation: current-pulse 2s ease-in-out infinite;
}

@keyframes current-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(37, 198, 201, 0.4);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(37, 198, 201, 0);
  }
}

.dag-current-badge {
  font-size: 9px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: 600;
  margin-left: 4px;
}

.dag-steps-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 100px;
}

.dag-step-item {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  text-align: center;
}

.dag-step-item.done {
  background: var(--done-soft);
  color: var(--done-strong);
}

.dag-step-item.running {
  background: var(--in-progress-soft);
  color: var(--in-progress-strong);
}

.dag-step-item.pending {
  background: var(--neutral-soft);
  color: var(--neutral-strong);
}

.dag-arrow-h {
  font-size: 14px;
  color: var(--text-muted);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 2px;
  background: var(--border-color);
  position: relative;
}

.dag-arrow-h::after {
  content: '';
  position: absolute;
  right: -4px;
  top: 50%;
  transform: translateY(-50%);
  border-left: 6px solid var(--border-color);
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}

.dag-row {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.dag-arrow {
  font-size: 14px;
  color: var(--text-muted);
  line-height: 1;
}

.dag-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  border: 1px solid;
  cursor: pointer;
  user-select: none;
  transition: opacity 0.15s;
  width: 100%;
  justify-content: center;
  white-space: nowrap;
}

.dag-node:hover {
  opacity: 0.85;
}

.dag-node-wrapper.is-done .dag-node {
  background: var(--done-soft);
  border-color: var(--teal-border-soft);
  color: var(--done-strong);
}

.dag-node-wrapper.failed .dag-node {
  background: var(--danger-soft);
  border-color: var(--danger-strong);
  color: var(--danger-strong);
}

.dag-node-status {
  font-size: 12px;
  line-height: 1;
}

.dag-node-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-badge {
  font-size: 9px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: 600;
  flex-shrink: 0;
}

.dag-toggle {
  font-size: 10px;
  color: var(--text-muted);
  transition: transform 0.15s;
  flex-shrink: 0;
}

.dag-toggle.open {
  transform: rotate(180deg);
}

.dag-node.done {
  background: var(--done-soft);
  border-color: var(--teal-border-soft);
  color: var(--done-strong);
}

.dag-node.running {
  background: var(--in-progress-soft);
  border-color: var(--teal-active-border);
  color: var(--in-progress-strong);
}

.dag-node.waiting {
  background: var(--neutral-soft);
  border-color: var(--border-color);
  color: var(--neutral-strong);
}

.dag-node-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.dag-node-sub {
  font-size: 10px;
  opacity: 0.6;
}

/* File List */
.file-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s ease;
  margin-bottom: 2px;
}

.file-item:hover {
  background: var(--bg-tertiary);
}

.file-item.selected {
  background: var(--teal-accent-mid);
}

.file-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-path {
  display: block;
  font-size: 10px;
  color: var(--text-muted);
  font-family: monospace;
}

.file-stats {
  display: flex;
  gap: 6px;
  font-size: 11px;
  font-family: monospace;
  flex-shrink: 0;
}

.additions {
  color: #22c55e;
}

.deletions {
  color: #ef4444;
}

/* File operations */
.file-operations {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  flex-wrap: wrap;
}

/* Chat */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.chat-message {
  margin-bottom: 12px;
}

.chat-message.user {
  display: flex;
  justify-content: flex-end;
}

.chat-message.assistant {
  display: flex;
  justify-content: flex-start;
}

.message-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.5;
}

.chat-message.user .message-bubble {
  background: var(--accent-color);
  color: white;
  border-bottom-right-radius: 2px;
}

.chat-message.assistant .message-bubble {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-bottom-left-radius: 2px;
}

.message-bubble p {
  margin: 0 0 8px 0;
}

.message-bubble p:last-child {
  margin-bottom: 0;
}

.message-bubble ul,
.message-bubble ol {
  margin: 4px 0;
  padding-left: 20px;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-primary);
}

.chat-input textarea {
  flex: 1;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  font-size: 13px;
  font-family: inherit;
  resize: none;
  background: var(--input-bg);
  color: var(--input-text);
  outline: none;
  min-height: 36px;
  max-height: 80px;
}

.chat-input textarea:focus {
  border-color: var(--accent-color);
}

/* File Viewer */
.file-viewer {
  flex: 1;
  overflow: auto;
  background: var(--bg-secondary);
}

.file-viewer-content {
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
  padding: 8px 0;
}

.code-line {
  display: flex;
  align-items: flex-start;
  padding: 0 16px;
  white-space: pre;
}

.code-line:hover {
  background: var(--bg-tertiary);
}

.line-num {
  color: var(--text-muted);
  width: 32px;
  text-align: right;
  flex-shrink: 0;
  padding-right: 16px;
  user-select: none;
}

.line-content {
  flex: 1;
  color: var(--text-primary);
}

.line-content.addition {
  background: rgba(34, 197, 94, 0.08);
  color: #16a34a;
}
</style>
