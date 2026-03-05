<template>
  <div class="session-terminal">
    <!-- Header with status -->
    <div class="terminal-header">
      <div class="status-indicator">
        <span
          class="status-dot"
          :class="statusClass"
        ></span>
        <span class="status-text">{{ statusText }}</span>
        <span v-if="session?.branch" class="branch-info">
          <Folder />
          {{ session.branch }}
        </span>
      </div>
      <div class="control-buttons">
        <el-button
          v-if="!session || session.status === 'CREATED' || session.status === 'STOPPED'"
          type="success"
          size="small"
          :loading="isStarting"
          :disabled="!session"
          @click="startSession"
        >
          Start
        </el-button>
        <el-button
          v-if="session && (session.status === 'RUNNING' || session.status === 'IDLE')"
          type="danger"
          size="small"
          :loading="isStopping"
          @click="stopSession"
        >
          Stop
        </el-button>
        <el-button
          v-if="session"
          type="info"
          size="small"
          :disabled="session.status === 'RUNNING' || session.status === 'IDLE'"
          @click="clearOutput"
        >
          Clear
        </el-button>
      </div>
    </div>

    <!-- Terminal output -->
    <div ref="outputContainer" class="terminal-output">
      <div v-if="!session" class="terminal-placeholder">
        <el-empty description="No active session" :image-size="60">
          <template #description>
            <p>Select an agent and create a session to start</p>
          </template>
        </el-empty>
      </div>
      <div v-else-if="outputLines.length === 0" class="terminal-placeholder">
        <p>Session ready. Click "Start" to begin...</p>
      </div>
      <div v-else>
        <div v-if="session && session.status === 'STOPPED'" class="history-indicator">
          [Historical output - session stopped]
        </div>
        <div
          v-for="(line, index) in outputLines"
          :key="index"
          class="output-line"
          :class="line.stream"
        >
          <span v-if="line.stream === 'stdin'" class="prompt">$ </span>
          <span class="line-content">{{ line.data }}</span>
        </div>
      </div>
    </div>

    <!-- Input area -->
    <div class="terminal-input" v-if="session && (session.status === 'RUNNING' || session.status === 'IDLE')">
      <el-input
        v-model="inputText"
        placeholder="Type your message and press Enter..."
        :disabled="!isConnected"
        @keyup.enter="sendInput"
      >
        <template #prefix>
          <span class="input-prompt">&gt;</span>
        </template>
        <template #append>
          <el-button :disabled="!inputText.trim()" @click="sendInput">
            Send
          </el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Folder } from '@element-plus/icons-vue'
import wsService from '../services/websocket'
import sessionApi from '../api/session'

const props = defineProps({
  task: {
    type: Object,
    required: true
  },
  agentId: {
    type: Number,
    default: null
  },
  initialSession: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['session-created', 'session-stopped', 'status-change'])

// State
const session = ref(null)
const outputLines = ref([])
const inputText = ref('')
const isStarting = ref(false)
const isStopping = ref(false)
const isConnected = ref(false)
const outputContainer = ref(null)

// Store initial prompt for filtering
const initialPrompt = ref(null)

// Computed
const statusClass = computed(() => {
  if (!session.value) return 'status-none'
  const status = session.value.status?.toLowerCase()
  if (status === 'running') return 'status-running'
  if (status === 'idle') return 'status-idle'
  if (status === 'stopped') return 'status-stopped'
  if (status === 'error') return 'status-error'
  return 'status-created'
})

const statusText = computed(() => {
  if (!session.value) return 'No Session'
  return session.value.status || 'Unknown'
})

// Track whether initial prompt has been filtered (only filter once)
const initialPromptFiltered = ref(false)

// Filter out initial prompt from output line
const shouldFilterLine = (line) => {
  if (!initialPrompt.value) return false
  if (initialPromptFiltered.value) return false

  // Check if line contains the initial prompt header
  // The initial prompt starts with "Task: {title}"
  const promptFirstLine = initialPrompt.value.split('\n')[0]?.trim()
  if (!promptFirstLine) return false

  // Only filter if the line starts with or exactly matches the prompt header
  const isFiltered = line.trim().startsWith(promptFirstLine)
  if (isFiltered) {
    initialPromptFiltered.value = true
  }
  return isFiltered
}

// Add output line with filtering
const addOutputLine = (data, stream, timestamp) => {
  // Split by newlines and filter each line
  const lines = data.split('\n')
  lines.forEach(line => {
    if (line.trim() && !shouldFilterLine(line)) {
      outputLines.value.push({
        data: line,
        stream,
        timestamp
      })
    }
  })
}

// Methods
const loadActiveSession = async () => {
  try {
    const response = await sessionApi.getActiveByTask(props.task.id)
    console.log('Load active session response:', response)
    if (response.success && response.data) {
      session.value = response.data
      // Store initial prompt for filtering
      if (response.data.initialPrompt) {
        initialPrompt.value = response.data.initialPrompt
        console.log('Initial prompt set for filtering:', initialPrompt.value.substring(0, 50) + '...')
      }
      // Load existing output (already filtered on server side if needed)
      if (response.data.output) {
        const lines = response.data.output.split('\n').filter(l => l.trim())
        outputLines.value = lines.map((line, i) => ({
          data: line,
          stream: 'stdout',
          timestamp: Date.now() + i
        }))
        scrollToBottom()
      }
      // Connect WebSocket if session is running
      if (['RUNNING', 'IDLE'].includes(response.data.status)) {
        connectWebSocket()
      }
    }
  } catch (e) {
    console.error('Failed to load active session:', e)
  }
}

const createSession = async () => {
  if (!props.agentId) {
    ElMessage.warning('Please select an agent first')
    return null
  }

  try {
    console.log('Creating session for task:', props.task.id, 'agent:', props.agentId)
    const response = await sessionApi.create(props.task.id, props.agentId)
    console.log('Create session response:', response)

    if (response.success && response.data) {
      session.value = response.data
      // Store initial prompt for filtering
      if (response.data.initialPrompt) {
        initialPrompt.value = response.data.initialPrompt
        initialPromptFiltered.value = false
      }
      emit('session-created', session.value)
      // Connect WebSocket
      connectWebSocket()
      return session.value
    } else {
      ElMessage.error(response.message || 'Failed to create session')
      return null
    }
  } catch (e) {
    console.error('Failed to create session:', e)
    ElMessage.error(e.response?.data?.message || e.message || 'Failed to create session')
    return null
  }
}

const startSession = async () => {
  if (!session.value) {
    session.value = await createSession()
    if (!session.value) return
  }

  // Prevent duplicate start
  if (isStarting.value) {
    console.warn('Session is already starting')
    return
  }

  isStarting.value = true
  // Show starting message
  outputLines.value.push({
    data: 'Starting session...',
    stream: 'stdout',
    timestamp: Date.now()
  })

  try {
    const response = await sessionApi.start(session.value.id)
    console.log('Start session response:', response)
    if (response.success && response.data) {
      session.value = response.data
      emit('status-change', session.value.status)

      // Store initial prompt for filtering
      if (response.data.initialPrompt) {
        initialPrompt.value = response.data.initialPrompt
        initialPromptFiltered.value = false
        console.log('Initial prompt set for filtering:', initialPrompt.value.substring(0, 50) + '...')
      }

      // Remove the "starting" message and load actual output
      outputLines.value = []

      // Load existing output from the response
      if (response.data.output) {
        const lines = response.data.output.split('\n').filter(l => l.trim())
        lines.forEach((line, i) => {
          if (!shouldFilterLine(line)) {
            outputLines.value.push({
              data: line,
              stream: 'stdout',
              timestamp: Date.now() + i
            })
          }
        })
        scrollToBottom()
      } else {
        // No output yet, mark that we're ready to filter initial prompt when it appears
        initialPromptFiltered.value = false
      }

      // Connect WebSocket after session starts (to avoid duplicate subscriptions)
      await connectWebSocket()

      // If no output yet, show waiting message
      if (outputLines.value.length === 0) {
        outputLines.value.push({
          data: 'Session started. Waiting for output...',
          stream: 'stdout',
          timestamp: Date.now()
        })
      }
    } else {
      outputLines.value.push({
        data: 'Error: ' + (response.message || 'Failed to start session'),
        stream: 'stderr',
        timestamp: Date.now()
      })
      ElMessage.error(response.message || 'Failed to start session')
    }
  } catch (e) {
    console.error('Failed to start session:', e)
    outputLines.value.push({
      data: 'Error: ' + (e.response?.data?.message || e.message || 'Failed to start session'),
      stream: 'stderr',
      timestamp: Date.now()
    })
    ElMessage.error(e.response?.data?.message || e.message || 'Failed to start session')
  } finally {
    isStarting.value = false
  }
}

const stopSession = async () => {
  if (!session.value) return

  isStopping.value = true
  try {
    const response = await sessionApi.stop(session.value.id)
    session.value = response.data
    emit('status-change', session.value.status)
    emit('session-stopped')
  } catch (e) {
    console.error('Failed to stop session:', e)
    ElMessage.error('Failed to stop session')
  } finally {
    isStopping.value = false
  }
}

const sendInput = async () => {
  if (!inputText.value.trim() || !session.value) return

  const input = inputText.value.trim()
  inputText.value = ''

  try {
    // Try WebSocket first
    if (isConnected.value) {
      wsService.sendInput(session.value.id, input)
    } else {
      // Fallback to REST API
      await sessionApi.sendInput(session.value.id, input)
    }
  } catch (e) {
    console.error('Failed to send input:', e)
    ElMessage.error('Failed to send input')
  }
}

const clearOutput = () => {
  outputLines.value = []
  initialPromptFiltered.value = false
}

const scrollToBottom = () => {
  nextTick(() => {
    if (outputContainer.value) {
      outputContainer.value.scrollTop = outputContainer.value.scrollHeight
    }
  })
}

const connectWebSocket = async () => {
  if (!session.value) return

  // Prevent duplicate connection
  if (isConnected.value) {
    console.log('WebSocket already connected for session', session.value.id)
    return
  }

  try {
    if (!wsService.isConnected()) {
      console.log('Connecting to WebSocket...')
      await wsService.connect()
      console.log('WebSocket connected successfully')
    }

    isConnected.value = true

    // Subscribe to output with filtering
    wsService.subscribeToOutput(session.value.id, (data) => {
      console.log('Received output:', data)
      if (data.type === 'chunk') {
        // Filter out initial prompt from the chunk
        addOutputLine(data.content, data.stream, data.timestamp)
        scrollToBottom()
      }
    })

    // Subscribe to status
    wsService.subscribeToStatus(session.value.id, (data) => {
      console.log('Received status:', data)
      if (data.type === 'status' && session.value) {
        // Only update RUNNING and IDLE states to keep input area visible
        if (['RUNNING', 'IDLE'].includes(data.status)) {
          session.value.status = data.status
          emit('status-change', data.status)
        }
      }
      if (data.type === 'exit') {
        // Only update status when process truly exits (non-zero exit code or user-initiated stop)
        if (session.value && data.exitCode !== undefined) {
          session.value.status = data.status
          emit('status-change', data.status)
        }
      }
    })
  } catch (e) {
    console.error('Failed to connect WebSocket:', e)
    isConnected.value = false
    // Will fall back to REST API for input
  }
}

// Poll for output as fallback
let outputPollInterval = null
const startOutputPolling = () => {
  if (outputPollInterval) return

  outputPollInterval = setInterval(async () => {
    if (!session.value || !session.value.id) return

    try {
      const response = await sessionApi.getOutput(session.value.id)
      if (response.success && response.data) {
        const currentOutput = outputLines.value.map(l => l.data).join('\n')
        if (response.data && response.data !== currentOutput) {
          // New output available, parse and add with filtering
          const newLines = response.data.split('\n').filter(l => l.trim())
          const existingLines = new Set(outputLines.value.map(l => l.data))
          newLines.forEach((line, i) => {
            if (!existingLines.has(line) && !shouldFilterLine(line)) {
              outputLines.value.push({
                data: line,
                stream: 'stdout',
                timestamp: Date.now() + i
              })
            }
          })
          scrollToBottom()
        }
      }
    } catch (e) {
      console.error('Failed to poll output:', e)
    }
  }, 2000) // Poll every 2 seconds
}

const stopOutputPolling = () => {
  if (outputPollInterval) {
    clearInterval(outputPollInterval)
    outputPollInterval = null
  }
}

const disconnectWebSocket = () => {
  if (session.value) {
    wsService.unsubscribeFromSession(session.value.id)
  }
}

// Lifecycle
onMounted(() => {
  // Use initialSession from parent if provided, otherwise load from API
  if (props.initialSession) {
    session.value = props.initialSession
    // Store initial prompt for filtering
    if (props.initialSession.initialPrompt) {
      initialPrompt.value = props.initialSession.initialPrompt
      initialPromptFiltered.value = false
    }
    // Load existing output from session
    if (props.initialSession.output) {
      const lines = props.initialSession.output.split('\n').filter(l => l.trim())
      outputLines.value = lines.map((line, i) => ({
        data: line,
        stream: 'stdout',
        timestamp: Date.now() + i
      })).filter(line => !shouldFilterLine(line.data))
      scrollToBottom()
    }
    // Connect WebSocket if session is running
    if (['RUNNING', 'IDLE'].includes(props.initialSession.status)) {
      connectWebSocket()
    }
  } else {
    loadActiveSession()
  }
})

onUnmounted(() => {
  disconnectWebSocket()
})

// Watch for agent changes
watch(() => props.agentId, async (newAgentId, oldAgentId) => {
  if (oldAgentId && newAgentId !== oldAgentId && session.value) {
    // Agent changed, stop current session
    await stopSession()
    session.value = null
    outputLines.value = []
    initialPrompt.value = null
    initialPromptFiltered.value = false
  }
})

// Watch for initialSession changes from parent
// Only load output if session has output and outputLines are empty
// Note: No immediate: true to avoid duplicate calls with onMounted
watch(() => props.initialSession, (newSession) => {
  if (newSession) {
    session.value = newSession
    // Store initial prompt for filtering
    if (newSession.initialPrompt) {
      initialPrompt.value = newSession.initialPrompt
      initialPromptFiltered.value = false
    }
    // Only load output if outputLines are empty (avoid duplicate loading)
    if (newSession.output && outputLines.value.length === 0) {
      const lines = newSession.output.split('\n').filter(l => l.trim())
      outputLines.value = lines.map((line, i) => ({
        data: line,
        stream: 'stdout',
        timestamp: Date.now() + i
      })).filter(line => !shouldFilterLine(line.data))
      scrollToBottom()
    }
    // Connect WebSocket if session is running and not already connected
    if (['RUNNING', 'IDLE'].includes(newSession.status) && !isConnected.value) {
      connectWebSocket()
    }
  }
}, { deep: true })

// Auto-scroll when output changes
watch(outputLines, () => {
  scrollToBottom()
}, { deep: true })

// Expose methods for parent component
defineExpose({
  createSession,
  startSession,
  stopSession,
  clearOutput,
  session
})
</script>

<style scoped>
.session-terminal {
  display: flex;
  flex-direction: column;
  height: 400px;
  background: #1e1e1e;
  border-radius: 8px;
  overflow: hidden;
}

.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #2d2d2d;
  border-bottom: 1px solid #3d3d3d;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.status-none { background: #666; }
.status-created { background: #409eff; }
.status-running { background: #67c23a; animation: pulse 1s infinite; }
.status-idle { background: #e6a23c; }
.status-stopped { background: #909399; }
.status-error { background: #f56c6c; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-text {
  color: #ccc;
  font-size: 12px;
  font-family: monospace;
}

.branch-info {
  color: #67c23a;
  font-size: 11px;
  font-family: monospace;
  margin-left: 12px;
  padding: 2px 6px;
  background: rgba(103, 194, 58, 0.1);
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.branch-info :deep(.el-icon) {
  font-size: 12px;
}

.control-buttons {
  display: flex;
  gap: 8px;
}

.terminal-output {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.terminal-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #666;
}

.terminal-placeholder :deep(.el-empty__description p) {
  color: #666;
}

.output-line {
  margin-bottom: 2px;
  word-break: break-all;
}

.output-line.stdout {
  color: #d4d4d4;
}

.output-line.stderr {
  color: #f56c6c;
}

.output-line.stdin {
  color: #67c23a;
}

.history-indicator {
  color: #909399;
  font-style: italic;
  margin-bottom: 8px;
  padding: 4px 8px;
  background: rgba(144, 147, 153, 0.1);
  border-radius: 4px;
}

.prompt {
  color: #409eff;
  margin-right: 4px;
}

.line-content {
  white-space: pre-wrap;
}

.terminal-input {
  padding: 8px 12px;
  background: #2d2d2d;
  border-top: 1px solid #3d3d3d;
}

.terminal-input :deep(.el-input__wrapper) {
  background: #1e1e1e;
  box-shadow: none;
  border: 1px solid #3d3d3d;
}

.terminal-input :deep(.el-input__inner) {
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', monospace;
}

.terminal-input :deep(.el-input-group__append) {
  background: #3d3d3d;
  border-color: #3d3d3d;
}

.input-prompt {
  color: #67c23a;
  font-family: monospace;
  font-weight: bold;
}

/* Scrollbar styling */
.terminal-output::-webkit-scrollbar {
  width: 8px;
}

.terminal-output::-webkit-scrollbar-track {
  background: #1e1e1e;
}

.terminal-output::-webkit-scrollbar-thumb {
  background: #3d3d3d;
  border-radius: 4px;
}

.terminal-output::-webkit-scrollbar-thumb:hover {
  background: #4d4d4d;
}
</style>
