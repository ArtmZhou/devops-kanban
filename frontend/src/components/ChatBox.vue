<template>
  <div class="chat-box">
    <!-- Header with status -->
    <div class="chat-header">
      <div class="status-indicator">
        <span
          class="status-dot"
          :class="statusClass"
        ></span>
        <span class="status-text">{{ statusText }}</span>
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
          @click="clearMessages"
        >
          Clear
        </el-button>
      </div>
    </div>

    <!-- Messages Area -->
    <div ref="messagesContainer" class="messages-area">
      <div v-if="!session" class="chat-placeholder">
        <el-empty description="No active session" :image-size="60">
          <template #description>
            <p>Select an agent and create a session to start</p>
          </template>
        </el-empty>
      </div>
      <div v-else-if="messages.length === 0" class="chat-placeholder">
        <p>Session ready. Click "Start" to begin...</p>
      </div>
      <div v-else class="messages-list">
        <div v-if="session && session.status === 'STOPPED'" class="history-indicator">
          <el-icon><Clock /></el-icon>
          Historical conversation - session stopped
        </div>
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="message-wrapper"
          :class="`message-${msg.role}`"
        >
          <div class="message-avatar">
            <el-avatar
              v-if="msg.role === 'user'"
              :size="32"
              class="avatar-user"
            >
              <el-icon><User /></el-icon>
            </el-avatar>
            <el-avatar
              v-else
              :size="32"
              class="avatar-assistant"
            >
              <el-icon><Monitor /></el-icon>
            </el-avatar>
          </div>
          <div class="message-bubble">
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatMessageTime(msg.timestamp) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input Area -->
    <div class="chat-input" v-if="session && (session.status === 'RUNNING' || session.status === 'IDLE')">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="1"
        :autosize="{ minRows: 1, maxRows: 4 }"
        placeholder="Type your message and press Enter to send..."
        :disabled="!isConnected"
        @keyup.enter.exact="sendMessage"
      />
      <el-button
        type="primary"
        :disabled="!inputText.trim()"
        @click="sendMessage"
      >
        <el-icon><Position /></el-icon>
        Send
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Monitor, Position, Clock } from '@element-plus/icons-vue'
import wsService from '../services/websocket'
import sessionApi from '../api/session'
import { createMessage } from '../types/chat'
import { parseOutputToMessages, parseWebSocketData } from '../utils/messageParser'

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
const messages = ref([])
const inputText = ref('')
const isStarting = ref(false)
const isStopping = ref(false)
const isConnected = ref(false)
const messagesContainer = ref(null)

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

// Methods
const formatMessageTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

const loadActiveSession = async () => {
  try {
    const response = await sessionApi.getActiveByTask(props.task.id)
    if (response.success && response.data) {
      session.value = response.data
      // Load existing output as messages
      if (response.data.output) {
        messages.value = parseOutputToMessages(response.data.output)
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
    const response = await sessionApi.create(props.task.id, props.agentId)
    if (response.success && response.data) {
      session.value = response.data
      emit('session-created', session.value)
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
  // Add system message
  messages.value.push(createMessage('system', 'Starting session...'))

  try {
    const response = await sessionApi.start(session.value.id)
    if (response.success && response.data) {
      session.value = response.data
      emit('status-change', session.value.status)

      // Remove the "starting" message
      messages.value = messages.value.filter(m => m.role !== 'system')

      // Load existing output from the response
      if (response.data.output) {
        messages.value = parseOutputToMessages(response.data.output)
        scrollToBottom()
      }

      // Connect WebSocket after session starts (to avoid duplicate subscriptions)
      await connectWebSocket()

      // If no messages yet, show waiting message
      if (messages.value.length === 0) {
        messages.value.push(createMessage('assistant', 'Session started. Waiting for output...'))
      }
    } else {
      messages.value.push(createMessage('system', 'Error: ' + (response.message || 'Failed to start session')))
      ElMessage.error(response.message || 'Failed to start session')
    }
  } catch (e) {
    console.error('Failed to start session:', e)
    messages.value.push(createMessage('system', 'Error: ' + (e.response?.data?.message || e.message || 'Failed to start session')))
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

const sendMessage = async () => {
  if (!inputText.value.trim() || !session.value) return

  const input = inputText.value.trim()
  inputText.value = ''

  // Add user message to chat
  messages.value.push(createMessage('user', input))
  scrollToBottom()

  try {
    if (isConnected.value) {
      wsService.sendInput(session.value.id, input)
    } else {
      await sessionApi.sendInput(session.value.id, input)
    }
  } catch (e) {
    console.error('Failed to send message:', e)
    ElMessage.error('Failed to send message')
  }
}

const clearMessages = () => {
  messages.value = []
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
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
      await wsService.connect()
    }

    isConnected.value = true

    // Subscribe to output
    wsService.subscribeToOutput(session.value.id, (data) => {
      console.log('Received output:', data)
      // Handle chunk message type from backend
      if (data.type === 'chunk') {
        const role = data.stream === 'stdin' ? 'user' : 'assistant'
        // For user messages from stdin, we already added them in sendMessage
        // So only add non-user messages
        if (role !== 'user') {
          messages.value.push({
            id: data.timestamp || Date.now(),
            role,
            content: data.content,
            timestamp: data.timestamp
          })
          scrollToBottom()
        }
      }
    })

    // Subscribe to status
    wsService.subscribeToStatus(session.value.id, (data) => {
      if (data.type === 'status' && session.value) {
        session.value.status = data.status
        emit('status-change', data.status)
      }
      if (data.type === 'exit') {
        if (session.value) {
          session.value.status = data.status
        }
        emit('status-change', data.status)
      }
    })
  } catch (e) {
    console.error('Failed to connect WebSocket:', e)
    isConnected.value = false
  }
}

const disconnectWebSocket = () => {
  if (session.value) {
    wsService.unsubscribeFromSession(session.value.id)
  }
}

// Lifecycle
onMounted(() => {
  if (props.initialSession) {
    session.value = props.initialSession
    if (props.initialSession.output) {
      messages.value = parseOutputToMessages(props.initialSession.output)
      scrollToBottom()
    }
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
    await stopSession()
    session.value = null
    messages.value = []
  }
})

// Watch for initialSession changes from parent
// Only load output if session has output and messages are empty
// Note: No immediate: true to avoid duplicate calls with onMounted
watch(() => props.initialSession, (newSession) => {
  if (newSession) {
    session.value = newSession
    // Only load output if messages are empty (avoid duplicate loading)
    if (newSession.output && messages.value.length === 0) {
      messages.value = parseOutputToMessages(newSession.output)
      scrollToBottom()
    }
    // Connect WebSocket if session is running and not already connected
    if (['RUNNING', 'IDLE'].includes(newSession.status) && !isConnected.value) {
      connectWebSocket()
    }
  }
}, { deep: true })

// Auto-scroll when messages change
watch(messages, () => {
  scrollToBottom()
}, { deep: true })

// Expose methods for parent component
defineExpose({
  createSession,
  startSession,
  stopSession,
  clearMessages,
  session
})
</script>

<style scoped>
.chat-box {
  display: flex;
  flex-direction: column;
  height: 400px;
  background: #f5f7fa;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e4e7ed;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
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

.status-none { background: #c0c4cc; }
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
  color: #606266;
  font-size: 12px;
  font-weight: 500;
}

.control-buttons {
  display: flex;
  gap: 8px;
}

.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f5f7fa;
}

.chat-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #909399;
}

.chat-placeholder :deep(.el-empty__description p) {
  color: #909399;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.history-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #909399;
  font-size: 12px;
  font-style: italic;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: rgba(144, 147, 153, 0.1);
  border-radius: 4px;
}

.message-wrapper {
  display: flex;
  gap: 8px;
  max-width: 85%;
}

.message-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-assistant {
  align-self: flex-start;
}

.message-system {
  align-self: center;
  background: #e6a23c;
  color: white;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-user {
  background: #409eff;
}

.avatar-assistant {
  background: #67c23a;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  max-width: 100%;
  word-break: break-word;
}

.message-user .message-bubble {
  background: #409eff;
  color: white;
  border-radius: 16px 16px 4px 16px;
}

.message-assistant .message-bubble {
  background: #fff;
  color: #303133;
  border-radius: 16px 16px 16px 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.message-content {
  font-size: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.message-time {
  font-size: 10px;
  margin-top: 4px;
  opacity: 0.7;
}

.message-user .message-time {
  text-align: right;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}

.chat-input .el-textarea {
  flex: 1;
}

.chat-input .el-button {
  align-self: flex-end;
}

/* Scrollbar styling */
.messages-area::-webkit-scrollbar {
  width: 6px;
}

.messages-area::-webkit-scrollbar-track {
  background: transparent;
}

.messages-area::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}

.messages-area::-webkit-scrollbar-thumb:hover {
  background: #909399;
}
</style>
