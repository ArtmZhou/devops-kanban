<template>
  <div class="brainstorming-panel">
    <!-- Header -->
    <div class="brainstorming-header">
      <div class="header-left">
        <span class="header-icon">🧠</span>
        <h4 class="header-title">{{ $t('brainstorming.title', '头脑风暴') }}</h4>
        <span v-if="currentTopic" class="topic-tag">{{ currentTopic }}</span>
      </div>
      <el-button
        type="primary"
        :disabled="isRunning || isCompleted"
        @click="startBrainstorming"
      >
        <el-icon><Lightning /></el-icon>
        {{ isRunning ? $t('brainstorming.inProgress', '讨论中...') : isCompleted ? $t('brainstorming.completed', '已完成') : $t('brainstorming.start', '开始讨论') }}
      </el-button>
    </div>

    <!-- Participants Bar -->
    <div v-if="isRunning || isCompleted" class="participants-bar">
      <div class="participants-label">
        <el-icon><User /></el-icon>
        {{ $t('brainstorming.participants', '参与者') }}:
      </div>
      <div class="participants-list">
        <span
          v-for="(role, index) in currentParticipants"
          :key="role"
          class="participant"
          :class="{
            'active': currentIndex === index,
            'completed': currentIndex > index
          }"
        >
          <span class="participant-icon">
            {{ roleConfig[role]?.icon || '👤' }}
          </span>
          <span class="participant-name">{{ role }}</span>
          <el-icon v-if="currentIndex > index" class="check-icon"><Check /></el-icon>
        </span>
      </div>
    </div>

    <!-- Messages Container -->
    <div ref="messagesContainer" class="messages-container">
      <div v-if="!isStarted && !isCompleted" class="empty-state">
        <el-empty :description="$t('brainstorming.emptyHint', '点击「开始讨论」按钮，召唤多位角色进行头脑风暴')" :image-size="80">
          <template #image>
            <div class="empty-image">🎯</div>
          </template>
        </el-empty>
      </div>

      <template v-else>
        <BrainstormingMessage
          v-for="(msg, index) in visibleMessages"
          :key="index"
          :message="msg"
          :delay="getMessageDelay(index)"
          :typing-speed="typingSpeed"
        />
      </template>
    </div>

    <!-- Conclusion Card -->
    <ConclusionCard
      v-if="isCompleted && currentConclusion"
      :conclusion="currentConclusion"
      :topic="currentTopic"
      @add-to-task="handleAddToTask"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Lightning, User, Check } from '@element-plus/icons-vue'
import { roleConfig } from '@/mock/workflowData'
import {
  getScriptByTaskType,
  matchTaskType
} from '@/mock/brainstormingData'
import BrainstormingMessage from './BrainstormingMessage.vue'
import ConclusionCard from './ConclusionCard.vue'

const props = defineProps({
  task: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['add-to-task'])

// State
const isStarted = ref(false)
const isRunning = ref(false)
const isCompleted = ref(false)
const currentIndex = ref(-1)
const visibleMessages = ref([])
const currentTopic = ref('')
const currentParticipants = ref([])
const currentConclusion = ref('')
const currentScript = ref(null)

const messagesContainer = ref(null)
const typingSpeed = ref(30) // ms per character

// Get task type for script selection
const taskType = computed(() => {
  if (!props.task) return 'default'
  return matchTaskType(props.task.title || '', props.task.description || '')
})

// Initialize and start brainstorming
const startBrainstorming = async () => {
  if (isRunning.value || isCompleted.value) return

  // Reset state
  isStarted.value = true
  isRunning.value = true
  isCompleted.value = false
  currentIndex.value = -1
  visibleMessages.value = []
  currentConclusion.value = ''

  // Get script based on task type
  const type = taskType.value
  currentScript.value = getScriptByTaskType(type)
  currentTopic.value = currentScript.value.topic
  currentParticipants.value = currentScript.value.participants

  // Play dialogues sequentially
  await playDialogues()
}

const playDialogues = async () => {
  const dialogues = currentScript.value.dialogues

  for (let i = 0; i < dialogues.length; i++) {
    if (!isRunning.value) break

    currentIndex.value = i
    visibleMessages.value.push(dialogues[i])

    // Calculate message duration based on content length
    const contentLength = dialogues[i].content.length
    const duration = Math.max(dialogues[i].duration || 4000, contentLength * typingSpeed.value)

    // Wait for message to complete typing
    await new Promise(resolve => setTimeout(resolve, duration + 500))

    // Scroll to bottom
    scrollToBottom()
  }

  // Show conclusion
  finishBrainstorming()
}

const finishBrainstorming = () => {
  isRunning.value = false
  isCompleted.value = true
  currentIndex.value = -1
  currentConclusion.value = currentScript.value.conclusion
  scrollToBottom()
}

const getMessageDelay = (index) => {
  // First message has no delay, subsequent messages wait for previous to complete
  if (index === 0) return 0
  if (index === 1) return 500
  return 1000
}

const scrollToBottom = () => {
  setTimeout(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  }, 100)
}

const handleAddToTask = ({ topic, conclusion }) => {
  emit('add-to-task', { topic, conclusion })
  ElMessage.success('结论已添加到任务')
}

// Watch task changes to reset state
watch(() => props.task, () => {
  resetState()
}, { immediate: false })

const resetState = () => {
  isStarted.value = false
  isRunning.value = false
  isCompleted.value = false
  currentIndex.value = -1
  visibleMessages.value = []
  currentTopic.value = ''
  currentParticipants.value = []
  currentConclusion.value = ''
  currentScript.value = null
}

// Expose method for external control
defineExpose({
  startBrainstorming,
  resetState
})
</script>

<style scoped>
.brainstorming-panel {
  margin-top: 16px;
  background: var(--el-bg-color);
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.brainstorming-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 24px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0;
}

.topic-tag {
  font-size: 12px;
  color: var(--el-color-primary);
  background: rgba(102, 126, 234, 0.1);
  padding: 4px 12px;
  border-radius: 12px;
  font-weight: 500;
}

/* Participants Bar */
.participants-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: var(--el-bg-color-page);
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
}

.participants-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.participants-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.participant {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--el-fill-color);
  border-radius: 16px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  transition: all 0.3s ease;
  border: 1px solid transparent;
}

.participant.active {
  background: rgba(102, 126, 234, 0.15);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  animation: pulse 1.5s infinite;
}

.participant.completed {
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
}

.participant-icon {
  font-size: 14px;
}

.participant-name {
  font-weight: 500;
}

.check-icon {
  font-size: 14px;
  color: #059669;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(102, 126, 234, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(102, 126, 234, 0); }
}

/* Messages Container */
.messages-container {
  padding: 20px;
  max-height: 400px;
  overflow-y: auto;
  background: var(--el-bg-color-page);
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.empty-image {
  font-size: 64px;
  line-height: 1;
}

/* Scrollbar styling */
.messages-container::-webkit-scrollbar {
  width: 6px;
}

.messages-container::-webkit-scrollbar-track {
  background: var(--el-fill-color);
  border-radius: 3px;
}

.messages-container::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 3px;
}

.messages-container::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-dark);
}
</style>
