<template>
  <div class="current-workflow-section">
    <div class="panel-header">
      <h4>当前工作流</h4>
      <span v-if="workflowName" class="current-wf-badge">{{ workflowName }}</span>
      <span v-else-if="loading" class="current-wf-badge">加载中...</span>
    </div>

    <div class="workflow-timeline">
      <div v-if="!taskId" class="workflow-empty">请选择任务</div>
      <div v-else-if="loading" class="workflow-empty">加载中...</div>
      <div v-else-if="error" class="workflow-empty">{{ error }}</div>
      <div v-else-if="!steps.length" class="workflow-empty">暂无工作流运行</div>
      <div v-else class="workflow-steps">
        <div
          v-for="(step, index) in steps"
          :key="step.id || index"
          class="workflow-step-h"
        >
          <div class="step-connector-h" v-if="index > 0"></div>
          <div class="step-node-h" :class="step.statusClass">
            <div class="step-dot-h" :class="step.statusClass"></div>
            <div class="step-content-h">
              <span class="step-name-h">{{ step.name }}</span>
              <span class="step-agent-h">{{ step.statusLabel }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="quick-actions">
      <el-tooltip :content="startTooltip" :disabled="!startDisabled" placement="top">
        <el-button
          size="small"
          type="primary"
          :disabled="startDisabled || actionLoading"
          @click="handleStart"
        >启动</el-button>
      </el-tooltip>
      <el-tooltip :content="retryTooltip" :disabled="!retryDisabled" placement="top">
        <el-button
          size="small"
          :disabled="retryDisabled || actionLoading"
          @click="handleRetry"
        >重试</el-button>
      </el-tooltip>
      <el-tooltip content="暂未实现" placement="top">
        <el-button size="small" disabled>合入</el-button>
      </el-tooltip>
      <el-tooltip :content="cancelTooltip" :disabled="!cancelDisabled" placement="top">
        <el-button
          size="small"
          :disabled="cancelDisabled || actionLoading"
          @click="handleCancel"
        >取消</el-button>
      </el-tooltip>
      <el-button size="small" :disabled="!taskId || actionLoading" @click="handleRefresh">刷新</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTask, startTask } from '../../api/task.js'
import { getWorkflowRun, cancelWorkflow, retryWorkflow } from '../../api/workflow.js'

const props = defineProps({
  taskId: { type: Number, default: null }
})

const emit = defineEmits(['refresh'])

const task = ref(null)
const run = ref(null)
const loading = ref(false)
const error = ref(null)
const actionLoading = ref(false)

const STATUS_CLASS = {
  DONE: 'done',
  COMPLETED: 'done',
  IN_PROGRESS: 'running',
  RUNNING: 'running',
  FAILED: 'failed',
  CANCELLED: 'failed',
  SUSPENDED: 'running',
  PENDING: 'pending'
}

const STATUS_LABEL = {
  DONE: '已完成',
  COMPLETED: '已完成',
  IN_PROGRESS: '执行中',
  RUNNING: '执行中',
  FAILED: '失败',
  CANCELLED: '已取消',
  SUSPENDED: '暂停',
  PENDING: '待执行'
}

const workflowName = computed(() => {
  return run.value?.workflow_template_snapshot?.name
    || run.value?.workflow_id
    || null
})

const steps = computed(() => {
  const list = run.value?.steps || []
  return list.map((step, index) => ({
    id: step.step_id || index,
    name: step.name || step.step_id || `步骤 ${index + 1}`,
    statusClass: STATUS_CLASS[step.status] || 'pending',
    statusLabel: STATUS_LABEL[step.status] || step.status || '待执行'
  }))
})

const runStatus = computed(() => run.value?.status || null)
const isTerminal = computed(() => {
  const s = runStatus.value
  return s === 'COMPLETED' || s === 'FAILED' || s === 'CANCELLED'
})

const startDisabled = computed(() => {
  if (!task.value) return true
  // Task not yet started - no run attached
  return Boolean(task.value.workflow_run_id) && !isTerminal.value
})
const startTooltip = computed(() => {
  if (!task.value) return '请选择任务'
  if (task.value.workflow_run_id && !isTerminal.value) return '工作流已在运行'
  return ''
})

const retryDisabled = computed(() => {
  if (!run.value) return true
  return runStatus.value !== 'FAILED' && runStatus.value !== 'CANCELLED'
})
const retryTooltip = computed(() => {
  if (!run.value) return '暂无工作流运行'
  if (!retryDisabled.value) return ''
  return '仅失败或已取消的工作流可重试'
})

const cancelDisabled = computed(() => {
  if (!run.value) return true
  return isTerminal.value
})
const cancelTooltip = computed(() => {
  if (!run.value) return '暂无工作流运行'
  if (isTerminal.value) return '工作流已结束'
  return ''
})

async function loadTask(id) {
  if (!id) {
    task.value = null
    return null
  }
  try {
    const resp = await getTask(id)
    if (resp?.success) {
      task.value = resp.data || null
      return task.value
    }
  } catch (e) {
    // swallow; error surfaces via run load
  }
  return null
}

async function loadRun(runId) {
  if (!runId) {
    run.value = null
    return
  }
  try {
    const resp = await getWorkflowRun(runId)
    if (resp?.success) {
      run.value = resp.data || null
    } else {
      run.value = null
      error.value = resp?.message || '加载工作流失败'
    }
  } catch (e) {
    run.value = null
    error.value = e?.message || '加载工作流失败'
  }
}

async function load() {
  error.value = null
  run.value = null
  if (!props.taskId) {
    task.value = null
    return
  }
  loading.value = true
  try {
    const t = await loadTask(props.taskId)
    if (t?.workflow_run_id) {
      await loadRun(t.workflow_run_id)
    }
  } finally {
    loading.value = false
  }
}

async function handleStart() {
  if (!props.taskId) return
  actionLoading.value = true
  try {
    const resp = await startTask(props.taskId)
    if (resp?.success) {
      ElMessage.success('任务已启动')
      await load()
      emit('refresh')
    } else {
      ElMessage.error(resp?.message || '启动失败')
    }
  } catch (e) {
    ElMessage.error(e?.message || '启动失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleRetry() {
  const runId = task.value?.workflow_run_id
  if (!runId) return
  actionLoading.value = true
  try {
    const resp = await retryWorkflow(runId)
    if (resp?.success) {
      ElMessage.success('已发起重试')
      await load()
      emit('refresh')
    } else {
      ElMessage.error(resp?.message || '重试失败')
    }
  } catch (e) {
    ElMessage.error(e?.message || '重试失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleCancel() {
  const runId = task.value?.workflow_run_id
  if (!runId) return
  actionLoading.value = true
  try {
    const resp = await cancelWorkflow(runId)
    if (resp?.success) {
      ElMessage.success('工作流已取消')
      await load()
      emit('refresh')
    } else {
      ElMessage.error(resp?.message || '取消失败')
    }
  } catch (e) {
    ElMessage.error(e?.message || '取消失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleRefresh() {
  await load()
  emit('refresh')
}

watch(() => props.taskId, () => {
  load()
}, { immediate: true })
</script>

<style scoped>
.current-workflow-section {
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  flex-shrink: 0;
}

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

.current-wf-badge {
  font-size: 11px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: auto;
}

.workflow-timeline {
  padding: 8px 16px 6px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-color);
}

.workflow-empty {
  padding: 12px 4px;
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
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
  background: var(--border-color);
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

.step-node-h.failed {
  background: var(--danger-soft);
  border-color: var(--danger-strong);
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

.step-dot-h.failed {
  background: var(--danger-strong);
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

.quick-actions {
  display: flex;
  gap: 6px;
  padding: 6px 16px 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
</style>
