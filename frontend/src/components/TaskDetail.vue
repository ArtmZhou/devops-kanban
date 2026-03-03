<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-content">
      <div class="modal-header">
        <h3>{{ isNew ? 'Create Task' : 'Task Details' }}</h3>
        <button class="close-btn" @click="$emit('close')">&times;</button>
      </div>

      <div class="modal-body">
        <div class="form-group">
          <label>Title *</label>
          <input v-model="form.title" type="text" placeholder="Enter task title" />
        </div>

        <div class="form-group">
          <label>Description</label>
          <textarea v-model="form.description" rows="4" placeholder="Enter task description"></textarea>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Status</label>
            <select v-model="form.status">
              <option value="TODO">To Do</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="IN_REVIEW">In Review</option>
              <option value="DONE">Done</option>
              <option value="BLOCKED">Blocked</option>
            </select>
          </div>

          <div class="form-group">
            <label>Priority</label>
            <select v-model="form.priority">
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label>Assignee</label>
          <input v-model="form.assignee" type="text" placeholder="Enter assignee name" />
        </div>

        <!-- Agent Execution Section -->
        <div v-if="!isNew && agents.length > 0" class="execution-section">
          <h4>Execute with Agent</h4>
          <div class="agent-select">
            <select v-model="selectedAgentId">
              <option :value="null">Select an agent...</option>
              <option v-for="agent in agents" :key="agent.id" :value="agent.id">
                {{ agent.name }} ({{ agent.type }})
              </option>
            </select>
            <button
              class="execute-btn"
              :disabled="!selectedAgentId || isExecuting"
              @click="executeTask"
            >
              {{ isExecuting ? 'Starting...' : 'Execute' }}
            </button>
          </div>

          <!-- Execution Status -->
          <div v-if="currentExecution" class="execution-status" :class="currentExecution.status.toLowerCase()">
            <strong>Status:</strong> {{ currentExecution.status }}
            <div v-if="currentExecution.output" class="execution-output">
              <pre>{{ currentExecution.output }}</pre>
            </div>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button v-if="!isNew" class="delete-btn" @click="handleDelete">Delete</button>
        <div class="spacer"></div>
        <button class="cancel-btn" @click="$emit('close')">Cancel</button>
        <button class="save-btn" @click="handleSave">{{ isNew ? 'Create' : 'Save' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { taskApi } from '../api/task'
import { agentApi } from '../api/agent'
import { executionApi } from '../api/execution'

const props = defineProps({
  task: {
    type: Object,
    default: null
  },
  projectId: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['close', 'saved', 'deleted'])

const isNew = computed(() => !props.task?.id)

const form = ref({
  title: '',
  description: '',
  status: 'TODO',
  priority: 'MEDIUM',
  assignee: ''
})

const agents = ref([])
const selectedAgentId = ref(null)
const isExecuting = ref(false)
const currentExecution = ref(null)
let eventSource = null

watch(() => props.task, (newTask) => {
  if (newTask) {
    form.value = {
      title: newTask.title || '',
      description: newTask.description || '',
      status: newTask.status || 'TODO',
      priority: newTask.priority || 'MEDIUM',
      assignee: newTask.assignee || ''
    }
  }
}, { immediate: true })

onMounted(async () => {
  if (!isNew.value) {
    try {
      const response = await agentApi.getByProject(props.projectId)
      agents.value = response.data || []
    } catch (e) {
      console.error('Failed to load agents:', e)
    }
  }
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
  }
})

const handleSave = async () => {
  if (!form.value.title.trim()) {
    alert('Title is required')
    return
  }

  const data = {
    ...form.value,
    projectId: props.projectId
  }

  try {
    if (isNew.value) {
      await taskApi.create(data)
    } else {
      await taskApi.update(props.task.id, data)
    }
    emit('saved')
    emit('close')
  } catch (e) {
    console.error('Failed to save task:', e)
    alert('Failed to save task')
  }
}

const handleDelete = async () => {
  if (!confirm('Are you sure you want to delete this task?')) return

  try {
    await taskApi.delete(props.task.id)
    emit('deleted')
    emit('close')
  } catch (e) {
    console.error('Failed to delete task:', e)
    alert('Failed to delete task')
  }
}

const executeTask = async () => {
  if (!selectedAgentId.value) return

  isExecuting.value = true
  try {
    const response = await executionApi.start(props.task.id, selectedAgentId.value)
    currentExecution.value = response.data

    // Setup SSE for real-time updates
    eventSource = executionApi.getOutputStream(currentExecution.value.id)
    eventSource.addEventListener('status', (e) => {
      if (currentExecution.value) {
        currentExecution.value.status = e.data
      }
    })
    eventSource.addEventListener('output', (e) => {
      if (currentExecution.value) {
        currentExecution.value.output = e.data
      }
    })
    eventSource.onerror = () => {
      eventSource.close()
    }
  } catch (e) {
    console.error('Failed to execute task:', e)
    alert('Failed to start execution')
  } finally {
    isExecuting.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e9ecef;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #6c757d;
}

.modal-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-weight: 500;
  font-size: 14px;
  color: #495057;
}

.form-group input,
.form-group textarea,
.form-group select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ced4da;
  border-radius: 6px;
  font-size: 14px;
}

.form-group input:focus,
.form-group textarea:focus,
.form-group select:focus {
  outline: none;
  border-color: #0d6efd;
  box-shadow: 0 0 0 3px rgba(13, 110, 253, 0.15);
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row .form-group {
  flex: 1;
}

.execution-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e9ecef;
}

.execution-section h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #495057;
}

.agent-select {
  display: flex;
  gap: 8px;
}

.agent-select select {
  flex: 1;
}

.execute-btn {
  padding: 8px 16px;
  background: #198754;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.execute-btn:disabled {
  background: #6c757d;
  cursor: not-allowed;
}

.execution-status {
  margin-top: 12px;
  padding: 12px;
  border-radius: 6px;
  background: #f8f9fa;
}

.execution-status.running {
  background: #cfe2ff;
}

.execution-status.success {
  background: #d1e7dd;
}

.execution-status.failed {
  background: #f8d7da;
}

.execution-output {
  margin-top: 8px;
}

.execution-output pre {
  margin: 0;
  padding: 8px;
  background: #212529;
  color: #f8f9fa;
  border-radius: 4px;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
}

.modal-footer {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #e9ecef;
}

.spacer {
  flex: 1;
}

.delete-btn {
  padding: 8px 16px;
  background: #dc3545;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.cancel-btn {
  padding: 8px 16px;
  background: #6c757d;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  margin-right: 8px;
}

.save-btn {
  padding: 8px 16px;
  background: #0d6efd;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
</style>
