<template>
  <div
    class="workflow-node"
    :class="[
      `status-${node.status.toLowerCase()}`,
      { 'is-current': isCurrent, 'is-selected': isSelected }
    ]"
    @click="$emit('select', node)"
  >
    <!-- 状态图标 -->
    <div class="node-status-icon" :style="{ backgroundColor: statusColor }">
      <span v-if="node.status === 'DONE'">✓</span>
      <span v-else-if="node.status === 'IN_PROGRESS'" class="pulse">▶</span>
      <span v-else>○</span>
    </div>

    <!-- 节点内容 -->
    <div class="node-content">
      <div class="node-name">{{ node.name }}</div>
      <div class="node-role">{{ node.role }}</div>

      <!-- Agent 信息 -->
      <div class="node-agent" :style="{ color: agentColor }">
        <span class="agent-icon">{{ agentIcon }}</span>
        <span class="agent-name">{{ node.agentName }}</span>
      </div>

      <!-- 耗时（已完成节点显示） -->
      <div v-if="node.status === 'DONE' && node.duration" class="node-duration">
        {{ node.duration }}min
      </div>
    </div>

    <!-- 当前进行中标记 -->
    <div v-if="isCurrent" class="current-badge">
      <span class="pulse-dot"></span>
      当前
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { agentConfig, nodeStatusConfig } from '@/mock/workflowData'

const props = defineProps({
  node: {
    type: Object,
    required: true
  },
  isCurrent: {
    type: Boolean,
    default: false
  },
  isSelected: {
    type: Boolean,
    default: false
  }
})

defineEmits(['select'])

const statusColor = computed(() => {
  return nodeStatusConfig[props.node.status]?.color || '#6B7280'
})

const agentColor = computed(() => {
  return agentConfig[props.node.agentType]?.color || '#6B7280'
})

const agentIcon = computed(() => {
  return agentConfig[props.node.agentType]?.icon || '🤖'
})
</script>

<style scoped>
.workflow-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 16px;
  min-width: 140px;
  background: #fff;
  border-radius: 12px;
  border: 2px solid #e5e7eb;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
}

.workflow-node:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
  transform: translateY(-2px);
}

.workflow-node.is-selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

/* 状态样式 */
.workflow-node.status-done {
  border-color: #10b981;
  background: linear-gradient(to bottom, #f0fdf4, #fff);
}

.workflow-node.status-in_progress {
  border-color: #3b82f6;
  background: linear-gradient(to bottom, #eff6ff, #fff);
}

.workflow-node.status-pending {
  opacity: 0.7;
  border-color: #d1d5db;
}

/* 状态图标 */
.node-status-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: bold;
  margin-bottom: 8px;
}

.node-status-icon .pulse {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 节点内容 */
.node-content {
  text-align: center;
}

.node-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.node-role {
  font-size: 11px;
  color: #6b7280;
  margin-bottom: 6px;
}

.node-agent {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
}

.agent-icon {
  font-size: 12px;
}

.node-duration {
  font-size: 10px;
  color: #10b981;
  margin-top: 6px;
  padding: 2px 6px;
  background: #d1fae5;
  border-radius: 4px;
}

/* 当前标记 */
.current-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: #3b82f6;
  color: #fff;
  font-size: 10px;
  font-weight: 500;
  border-radius: 10px;
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background: #fff;
  border-radius: 50%;
  animation: pulse-dot 1.5s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.5); opacity: 0.5; }
}
</style>
