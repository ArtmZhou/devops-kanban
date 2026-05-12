<template>
  <div class="workspace-kanban-board">
    <KanbanColumn
      v-for="col in columns"
      :key="col.status"
      :status="col.status"
      :title="col.title"
      :tasks="col.tasks"
      :selected-task="selectedTask"
      :running-task-ids="runningTaskIds"
      :empty-text="col.emptyText"
      :show-add-button="false"
      @drag-end="onDragEnd"
      @select-task="$emit('select-task', $event)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import KanbanColumn from './KanbanColumn.vue'

const props = defineProps({
  tasks: { type: Array, required: true },
  selectedTask: { type: Object, default: null },
  runningTaskIds: { type: Set, default: () => new Set() }
})

const emit = defineEmits(['select-task', 'drag-end'])

const COLUMNS = [
  { status: 'TODO', title: '待处理', emptyText: '暂无待处理任务' },
  { status: 'IN_PROGRESS', title: '处理中', emptyText: '暂无处理中任务' },
  { status: 'DONE', title: '已完成', emptyText: '暂无已完成任务' },
  { status: 'BLOCKED', title: '阻塞', emptyText: '暂无阻塞任务' }
]

const columns = computed(() =>
  COLUMNS.map(col => ({
    ...col,
    tasks: props.tasks.filter(t => t.status === col.status)
  }))
)

function onDragEnd(event) {
  // event.to is the destination <draggable> element, which has data-status
  // from KanbanColumn's draggable :data-status="status"
  const newStatus = event.to?.dataset?.status
  if (!newStatus) return
  // The dragged item id is in event.item
  const taskId = event.item?.__draggable_context?.element?.id
  if (!taskId) return
  emit('drag-end', { taskId, newStatus })
}
</script>

<style scoped>
.workspace-kanban-board {
  display: flex;
  gap: 8px;
  padding: 8px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.workspace-kanban-board :deep(.kanban-column) {
  min-width: 200px;
  overflow: hidden;
}
</style>
