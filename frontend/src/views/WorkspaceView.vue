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
      <!-- Upper: Workflow + Operations | File placeholder -->
      <div class="workspace-upper">
        <div class="workspace-upper-left">
          <!-- Pipeline DAG overview (top) -->
          <div class="panel-header">
            <h4>工作流链路</h4>
          </div>
          <PipelineDag
            v-if="selectedTask"
            :nodes="pipeline.nodes"
            :current-task-id="selectedTask?.id"
            @select="(task) => selectedTask = task"
          />
          <div v-else class="panel-placeholder">请选择任务</div>

          <!-- Current workflow section -->
          <CurrentWorkflow
            :task-id="selectedTask?.id ?? null"
            @refresh="onWorkflowRefresh"
          />
        </div>

        <div class="workspace-upper-right">
          <div class="panel-header">
            <h4>文件查看</h4>
          </div>
          <div class="panel-placeholder">
            <span>文件查看面板（即将上线）</span>
          </div>
        </div>
      </div>

      <div class="resize-handle resize-handle-h" @mousedown="startResize('right')"></div>

      <!-- Lower: AI Split + Chat placeholder | File list placeholder -->
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

          <div class="chat-panel">
            <div class="panel-header">
              <h4>聊天</h4>
            </div>
            <div class="panel-placeholder">
              <span>聊天面板（即将上线）</span>
            </div>
          </div>
        </div>

        <div class="workspace-lower-right">
          <div class="panel-header">
            <h4>改动文件</h4>
          </div>
          <div class="panel-placeholder">
            <span>改动文件面板（即将上线）</span>
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
import PipelineDag from '../components/workspace/PipelineDag.vue'
import CurrentWorkflow from '../components/workspace/CurrentWorkflow.vue'
import { useSplitSuggestionsStore } from '../stores/splitSuggestions.js'
import { listTasks, getTaskPipeline } from '../api/task.js'

const splitStore = useSplitSuggestionsStore()
const route = useRoute()
const projectId = computed(() => route.params.projectId ? Number(route.params.projectId) : null)

const tasks = ref([])
const pipeline = ref({ root: null, nodes: [] })
const selectedTask = ref(null)

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

const leftWidth = ref(280)
const rightWidth = ref(380)
const LEFT_MIN = 220
const LEFT_MAX = 500
const RIGHT_MIN = 260
const RIGHT_MAX = 700

const startResize = (side) => (e) => {
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

async function onWorkflowRefresh() {
  if (selectedTask.value?.id) {
    await loadPipeline(selectedTask.value.id)
  }
}

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

.panel-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  color: var(--text-muted);
  font-size: 12px;
  min-height: 60px;
}
</style>
