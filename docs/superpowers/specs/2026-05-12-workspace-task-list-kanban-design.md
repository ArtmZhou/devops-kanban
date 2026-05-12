# Workspace 任务列表展开为看板视图设计

## 背景

WorkspaceView 左侧当前是单列任务列表。用户希望添加一个展开按钮，展开后以看板视图展示任务，按状态分列（待处理/处理中/已完成/阻塞），与 KanbanView 的看板交互一致。

## 需求汇总

1. 展开按钮位于任务列表底部
2. 展开后左侧面板自动加宽，容纳 4 列
3. 4 列：待处理 / 处理中 / 已完成 / 阻塞
4. 每列独立纵向滚动
5. 支持拖拽改变任务状态
6. 提取通用 KanbanColumn 组件，WorkspaceView 和 KanbanView 共用

## 架构设计

### 组件层次

```
WorkspaceView
├── workspace-left (可加宽的面板)
│   ├── workspace-header (任务列表标题)
│   ├── project-filter-bar
│   ├── task-status-filter (现有筛选芯片)
│   ├── task-list-actions (新建按钮)
│   ├── TaskListKanbanBoard (NEW - 看板容器)
│   │   └── KanbanColumn × 4 (EXTRACTED - 通用列组件)
│   │       └── TaskListItem (EXISTING)
│   └── expand/collapse button (底部)
```

### 数据流

- WorkspaceView 将 `tasks` 按 `status` 分组，传入 4 个 KanbanColumn
- 拖拽完成后触发 `drag-end` 事件 → WorkspaceView 调用 `updateTask(id, { status })` 更新状态
- 点击任务卡片 → WorkspaceView 的 `selectTask`（与现有行为一致）

### 文件变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `frontend/src/components/kanban/TaskColumn.vue` | 保留现有 | KanbanView 已引用，需提取其列级样式和逻辑 |
| `frontend/src/components/kanban/KanbanColumn.vue` | 新建 | 通用列组件，封装 header + draggable + 空状态 + 添加按钮 |
| `frontend/src/components/kanban/WorkspaceKanbanBoard.vue` | 新建 | 看板容器，包含 4 列的 flex 布局 |
| `frontend/src/views/WorkspaceView.vue` | 修改 | 添加展开/收起状态，引入看板组件 |
| `frontend/src/views/KanbanView.vue` | 修改 | 将现有 TaskColumn 引用替换为 KanbanColumn |

## KanbanColumn 设计

通用列组件，封装以下功能：
- 列标题 + 状态点 + 计数
- vuedraggable 包裹的 TaskListItem 列表
- 空状态提示
- 添加任务按钮
- 拖拽事件传递
- 展开/收起按钮

Props: `status`, `title`, `tasks`, `selectedTask`, `runningTaskIds`, `emptyText`, `showAddButton`, `collapsed`
Events: `drag-end`, `select-task`, `edit-task`, `delete-task`, `add-task`, `toggle-collapse`

## WorkspaceKanbanBoard 设计

容器组件，包含 4 列 + 列间 resize handle：

Props: `tasks`, `selectedTask`, `runningTaskIds`
Events: `select-task`, `drag-end` (触发状态更新)

## WorkspaceView 展开/收起逻辑

- `taskListViewMode` ref: `'list' | 'kanban'`，默认 `'list'`
- 展开时：`leftWidth` 设为 `Math.max(leftWidth.value, 900)`
- 收起时：`leftWidth` 恢复为 `310`（或 localStorage 中的值）
- 面板宽度边界：LEFT_MIN=220, LEFT_MAX=1500(展开时放宽)

## CSS 要点

- 看板容器：`display: flex; gap: 8px;`
- 每列：`flex: 1; min-width: 200px; overflow: hidden; display: flex; flex-direction: column`
- 列内容：`flex: 1; overflow-y: auto`（独立滚动）
- 拖拽 ghost/drag 样式沿用 KanbanView 的 `.ghost-card` / `.drag-card`
