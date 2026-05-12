# Workspace 任务列表看板视图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an expand/collapse toggle to WorkspaceView's left task list that shows a 4-column kanban board (TODO/IN_PROGRESS/DONE/BLOCKED) with drag-to-change-status.

**Architecture:** Extract a generic `KanbanColumn` component from the existing `TaskColumn.vue`, create a `WorkspaceKanbanBoard` container, wire it into WorkspaceView with expand/collapse state.

**Tech Stack:** Vue 3 Composition API, vuedraggable, Element Plus

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `frontend/src/components/kanban/KanbanColumn.vue` | **Rename** from TaskColumn.vue | Generic kanban column with header + draggable + TaskListItem |
| `frontend/src/components/kanban/WorkspaceKanbanBoard.vue` | **Create** | 4-column board container, accepts tasks array, emits drag-end + select-task |
| `frontend/src/components/kanban/index.js` | **Create** | Re-export KanbanColumn and WorkspaceKanbanBoard |
| `frontend/src/views/KanbanView.vue` | **Modify** | Update import: `TaskColumn` → `KanbanColumn` |
| `frontend/src/views/WorkspaceView.vue` | **Modify** | Add `taskListViewMode` toggle, expand button, import WorkspaceKanbanBoard, useTaskTimer for running tasks, handle drag status change |

---

### Task 1: Rename TaskColumn.vue → KanbanColumn.vue

**Files:**
- Rename: `frontend/src/components/kanban/TaskColumn.vue` → `frontend/src/components/kanban/KanbanColumn.vue`
- Modify: `frontend/src/views/KanbanView.vue:725` (update import)

- [ ] **Step 1: Rename the file**

```bash
cd frontend/src/components/kanban
git mv TaskColumn.vue KanbanColumn.vue
```

- [ ] **Step 2: Update KanbanView.vue import**

Change line 725 from:
```js
import KanbanColumn from '../components/kanban/TaskColumn.vue'
```
To:
```js
import KanbanColumn from '../components/kanban/KanbanColumn.vue'
```

- [ ] **Step 3: Verify no other references to TaskColumn.vue**

```bash
grep -r "TaskColumn" frontend/src/
```
Expected: no results (if there are, update them too).

- [ ] **Step 4: Run frontend dev server to verify no import errors**

```bash
cd frontend && npm run build 2>&1 | head -30
```
Expected: no import/module resolution errors related to KanbanColumn.

- [ ] **Step 5: Commit**

```bash
cd /Users/taowenpeng/IdeaProjects/devops-kanban
git add frontend/src/components/kanban/KanbanColumn.vue frontend/src/views/KanbanView.vue
git commit -m "refactor: rename TaskColumn to KanbanColumn for shared usage"
```

---

### Task 2: Create index.js barrel export

**Files:**
- Create: `frontend/src/components/kanban/index.js`

- [ ] **Step 1: Write barrel export**

```js
export { default as KanbanColumn } from './KanbanColumn.vue'
export { default as WorkspaceKanbanBoard } from './WorkspaceKanbanBoard.vue'
export { default as KanbanListView } from './KanbanListView.vue'
```

- [ ] **Step 2: Commit**

```bash
cd /Users/taowenpeng/IdeaProjects/devops-kanban
git add frontend/src/components/kanban/index.js
git commit -m "feat: add barrel exports for kanban components"
```

---

### Task 3: Create WorkspaceKanbanBoard.vue

**Files:**
- Create: `frontend/src/components/kanban/WorkspaceKanbanBoard.vue`

- [ ] **Step 1: Write the component**

```vue
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
```

- [ ] **Step 2: Commit**

```bash
cd /Users/taowenpeng/IdeaProjects/devops-kanban
git add frontend/src/components/kanban/WorkspaceKanbanBoard.vue
git commit -m "feat: add WorkspaceKanbanBoard 4-column kanban container"
```

---

### Task 4: Wire WorkspaceKanbanBoard into WorkspaceView.vue

**Files:**
- Modify: `frontend/src/views/WorkspaceView.vue`

- [ ] **Step 1: Add import**

In the import section (around line 281), add:
```js
import WorkspaceKanbanBoard from '../components/kanban/WorkspaceKanbanBoard.vue'
```

- [ ] **Step 2: Add useTaskTimer composable**

After existing imports (around line 291), add:
```js
import { useTaskTimer } from '../composables/kanban/useTaskTimer.js'
```

In the setup block (after `const agentStore = useAgentStore()`), add:
```js
const { runningTasks, formatTaskElapsedTime } = useTaskTimer()
```

- [ ] **Step 3: Add taskListViewMode state**

After `const selectedStatus = ref(null)` (around line 504), add:
```js
const taskListViewMode = ref('list') // 'list' | 'kanban'
```

- [ ] **Step 4: Add expand button to the task list template**

In the `<template>`, after the last task card div (inside `.task-list`, around line 81), add:
```vue
        <div class="task-list-expand-toggle" @click="taskListViewMode = taskListViewMode === 'list' ? 'kanban' : 'list'">
          <svg v-if="taskListViewMode === 'list'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15 3 21 3 21 9"></polyline>
            <polyline points="9 21 3 21 3 15"></polyline>
            <line x1="21" y1="3" x2="14" y2="10"></line>
            <line x1="3" y1="21" x2="10" y2="14"></line>
          </svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="4 14 10 14 10 20"></polyline>
            <polyline points="20 10 14 10 14 4"></polyline>
            <line x1="14" y1="10" x2="21" y2="3"></line>
            <line x1="3" y1="21" x2="10" y2="14"></line>
          </svg>
          <span>{{ taskListViewMode === 'list' ? '看板视图' : '列表视图' }}</span>
        </div>
```

- [ ] **Step 5: Add kanban board alongside list in the template**

Replace the current `.task-list` content area to conditionally render:
```vue
      <div class="task-list">
        <!-- Status filter chips (always visible) -->
        <div class="task-status-filter">
          ...existing chips...
        </div>
        <div class="task-list-actions">
          ...existing actions...
        </div>

        <!-- List view -->
        <template v-if="taskListViewMode === 'list'">
          <div v-for="task in tasks" ...>...existing task cards...</div>
        </template>

        <!-- Kanban view -->
        <WorkspaceKanbanBoard
          v-else
          :tasks="tasks"
          :selected-task="selectedTask"
          :running-task-ids="runningTasks"
          @select-task="selectTask"
          @drag-end="onKanbanDragEnd"
        />
      </div>
```

Note: The existing task card `v-for` loop should be wrapped in `<template v-if="taskListViewMode === 'list'">` to conditionally render it.

- [ ] **Step 6: Add onKanbanDragEnd handler**

In the `<script setup>` section, add:
```js
async function onKanbanDragEnd({ taskId, newStatus }) {
  try {
    const resp = await updateTask(taskId, { status: newStatus })
    if (resp?.success) {
      await loadTasks()
      ElMessage.success('任务状态已更新')
    }
  } catch (e) {
    ElMessage.error(e?.message || '状态更新失败')
  }
}
```

- [ ] **Step 7: Update leftWidth on mode toggle**

Add a watcher after the existing watch blocks:
```js
watch(taskListViewMode, (mode) => {
  if (mode === 'kanban') {
    leftWidth.value = Math.max(leftWidth.value, 900)
  } else {
    leftWidth.value = Math.min(leftWidth.value, 360)
  }
})
```

- [ ] **Step 8: Add CSS for the expand toggle**

Add to the `<style scoped>` section:
```css
.task-list-expand-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 12px;
  margin: 8px;
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--panel-bg);
  cursor: pointer;
  transition: all 0.2s;
}

.task-list-expand-toggle:hover {
  border-color: var(--accent-color);
  color: var(--accent-color);
  background: var(--hover-bg);
}

.task-list-expand-toggle svg {
  flex-shrink: 0;
}
```

- [ ] **Step 9: Commit**

```bash
cd /Users/taowenpeng/IdeaProjects/devops-kanban
git add frontend/src/views/WorkspaceView.vue
git commit -m "feat: add kanban view toggle to workspace task list"
```

---

### Task 5: Fix KanbanColumn columnStyle prop for workspace usage

**Files:**
- Modify: `frontend/src/components/kanban/KanbanColumn.vue`

The existing `columnStyle` computed uses `customWidth` which is fine. But we need to ensure the column flex works in the workspace board context.

- [ ] **Step 1: Verify KanbanColumn CSS works in flex container**

The existing CSS has:
```css
.kanban-column {
  flex: 1 1 0;
  min-width: 340px;
  ...
}
```

For workspace usage where columns should be narrower (200px min), ensure the component respects the `customWidth` prop when set. No code change needed since `customWidth` already sets `flexBasis` and `flexShrink: 0`.

- [ ] **Step 2: Verify and commit if changes were needed**

If no changes needed, this task is complete. If changes were needed:
```bash
cd /Users/taowenpeng/IdeaProjects/devops-kanban
git add frontend/src/components/kanban/KanbanColumn.vue
git commit -m "fix: adjust KanbanColumn flex behavior for workspace usage"
```

---

### Task 6: Integration test — run dev server and verify

**Files:**
- No file changes

- [ ] **Step 1: Start dev servers**

```bash
cd /Users/taowenpeng/IdeaProjects/devops-kanban && ./start.sh
```

- [ ] **Step 2: Verify in browser**

1. Open http://localhost:3000/workspace
2. Select a project
3. Verify the task list shows normally (list view)
4. Click the "看板视图" button at the bottom
5. Verify the left panel widens to show 4 columns
6. Verify tasks are distributed across columns by status
7. Drag a task from "待处理" to "处理中"
8. Verify the task status updates and the column re-renders
9. Click "列表视图" to collapse back
10. Verify the panel narrows and list view returns

- [ ] **Step 3: Verify KanbanView still works**

1. Open http://localhost:3000/kanban/:projectId
2. Verify the kanban board still renders correctly
3. Verify drag-and-drop still works
