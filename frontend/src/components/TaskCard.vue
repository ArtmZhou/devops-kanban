<template>
  <div class="task-card" :class="priorityClass" @click="$emit('click')">
    <div class="task-header">
      <span class="priority-badge" :class="priorityClass">{{ priorityLabel }}</span>
      <span v-if="task.externalId" class="external-id">#{{ task.externalId }}</span>
    </div>
    <h4 class="task-title">{{ task.title }}</h4>
    <p v-if="task.description" class="task-description">{{ truncatedDescription }}</p>
    <div class="task-footer">
      <span v-if="task.assignee" class="assignee">
        <span class="avatar">{{ task.assignee.charAt(0).toUpperCase() }}</span>
        {{ task.assignee }}
      </span>
      <span v-if="task.syncedAt" class="sync-info">
        Synced: {{ formatTime(task.syncedAt) }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  task: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])

const priorityClass = computed(() => {
  return `priority-${(props.task.priority || 'MEDIUM').toLowerCase()}`
})

const priorityLabel = computed(() => {
  const labels = {
    LOW: 'Low',
    MEDIUM: 'Med',
    HIGH: 'High',
    CRITICAL: 'Crit'
  }
  return labels[props.task.priority] || 'Med'
})

const truncatedDescription = computed(() => {
  if (!props.task.description) return ''
  return props.task.description.length > 100
    ? props.task.description.substring(0, 100) + '...'
    : props.task.description
})

const formatTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString().substring(0, 5)
}
</script>

<style scoped>
.task-card {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
  border-left: 3px solid #ccc;
}

.task-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
}

.task-card.priority-critical {
  border-left-color: #dc3545;
}

.task-card.priority-high {
  border-left-color: #fd7e14;
}

.task-card.priority-medium {
  border-left-color: #0d6efd;
}

.task-card.priority-low {
  border-left-color: #6c757d;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.priority-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
  text-transform: uppercase;
}

.priority-badge.priority-critical {
  background: #dc3545;
  color: #fff;
}

.priority-badge.priority-high {
  background: #fd7e14;
  color: #fff;
}

.priority-badge.priority-medium {
  background: #0d6efd;
  color: #fff;
}

.priority-badge.priority-low {
  background: #e9ecef;
  color: #6c757d;
}

.external-id {
  font-size: 12px;
  color: #6c757d;
}

.task-title {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
  color: #212529;
}

.task-description {
  margin: 0 0 8px 0;
  font-size: 12px;
  color: #6c757d;
  line-height: 1.4;
}

.task-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #6c757d;
}

.assignee {
  display: flex;
  align-items: center;
  gap: 4px;
}

.avatar {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #6c757d;
  color: #fff;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sync-info {
  font-style: italic;
}
</style>
