<template>
  <div class="workspace-view">
    <!-- Left: Task List -->
    <div class="workspace-left" :style="{ width: leftWidth + 'px', minWidth: leftWidth + 'px' }">
      <div class="workspace-header">
        <h3>任务列表</h3>
        <span class="task-count">{{ tasks.length }}</span>
      </div>
      <div class="project-filter-bar">
        <select
          v-model="selectedProjectId"
          @change="handleProjectChange"
          :disabled="projectStore.loading"
        >
          <option :value="null">{{ $t('project.selectProject') }}</option>
          <option
            v-for="project in projects"
            :key="project.id"
            :value="project.id"
          >
            {{ project.name }}
          </option>
        </select>
      </div>
      <div class="task-list">
        <div class="task-status-filter">
          <button
            class="status-chip"
            :class="{ active: selectedStatus === null }"
            @click="selectedStatus = null"
          >
            全部
          </button>
          <button
            v-for="s in TASK_STATUSES"
            :key="s"
            class="status-chip"
            :class="{ active: selectedStatus === s }"
            @click="selectedStatus = s"
          >
            {{ statusText(s) }}
          </button>
        </div>
        <div class="task-list-actions">
          <el-button size="small" text type="primary" @click="openCreateTask">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            新建
          </el-button>
        </div>
        <template v-if="taskListViewMode === 'list'">
          <div
            v-for="task in tasks"
            :key="task.id"
            class="task-card"
            :class="{ 'selected': selectedTask?.id === task.id, 'is-mock': task.id < 0 }"
            @click="selectTask(task)"
          >
            <div class="task-card-header">
              <span class="task-priority" :class="priorityClass(task.priority)">{{ priorityText(task.priority) }}</span>
              <span class="task-status" :class="statusClass(task.status)">{{ statusText(task.status) }}</span>
            </div>
            <div class="task-card-title">{{ task.title }}</div>
            <div v-if="task.description" class="task-card-desc">{{ task.description }}</div>
            <div v-if="task.id > 0" class="task-card-actions" @click.stop>
              <el-button size="small" text @click="openEditTask(task)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <path d="M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"></path>
                </svg>
              </el-button>
              <el-button size="small" text type="danger" @click="handleDeleteTask(task)">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                  <polyline points="3 6 5 6 21 6"></polyline>
                  <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path>
                  <path d="M10 11v6"></path>
                  <path d="M14 11v6"></path>
                </svg>
              </el-button>
            </div>
          </div>
        </template>
        <div v-if="taskListViewMode === 'kanban'" class="task-kanban-board">
          <div
            v-for="col in kanbanColumns"
            :key="col.status"
            class="task-kanban-column"
            :data-status="col.status"
          >
            <div class="task-kanban-column-header">
              <span class="task-kanban-column-title">{{ col.title }}</span>
              <span class="task-kanban-column-count">{{ col.tasks.length }}</span>
            </div>
            <draggable
              :list="col.tasks"
              group="workspace-tasks"
              :animation="200"
              ghost-class="ghost-card"
              :data-status="col.status"
              item-key="id"
              @end="onKanbanDragEnd"
            >
              <template #item="{ element: task }">
                <div
                  :data-id="task.id"
                  class="task-card"
                  :class="{ 'selected': selectedTask?.id === task.id, 'is-mock': task.id < 0 }"
                  @click="selectTask(task)"
                >
                  <div class="task-card-header">
                    <span class="task-priority" :class="priorityClass(task.priority)">{{ priorityText(task.priority) }}</span>
                    <span class="task-status" :class="statusClass(task.status)">{{ statusText(task.status) }}</span>
                  </div>
                  <div class="task-card-title">{{ task.title }}</div>
                  <div v-if="task.description" class="task-card-desc">{{ task.description }}</div>
                  <div v-if="task.id > 0" class="task-card-actions" @click.stop>
                    <el-button size="small" text @click="openEditTask(task)">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                        <path d="M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"></path>
                      </svg>
                    </el-button>
                    <el-button size="small" text type="danger" @click="handleDeleteTask(task)">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                        <polyline points="3 6 5 6 21 6"></polyline>
                        <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"></path>
                        <path d="M10 11v6"></path>
                        <path d="M14 11v6"></path>
                      </svg>
                    </el-button>
                  </div>
                </div>
              </template>
            </draggable>
          </div>
        </div>
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
      </div>
    </div>

    <div class="resize-handle" @mousedown="(e) => handleMouseDown(e, 'left')"></div>

    <!-- Right: 2-row layout -->
    <div class="workspace-right">
      <!-- Middle column: workflow header (top, fixed) / workflow content + chat (bottom, flex) -->
      <div class="workspace-col workspace-mid-col">
        <!-- Workflow header — always on top -->
        <div class="workflow-unified-header">
          <div class="workflow-unified-title">
            <span class="panel-icon">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="4" cy="4" r="2"></circle>
                <circle cx="20" cy="4" r="2"></circle>
                <circle cx="4" cy="20" r="2"></circle>
                <circle cx="20" cy="20" r="2"></circle>
                <polyline points="4 6 4 4 20 4 20 20 4 20 4 18"></polyline>
              </svg>
            </span>
            <h4>工作流</h4>
            <span v-if="workflowDisplayName" class="workflow-name-badge">{{ workflowDisplayName }}</span>
          </div>
          <button class="collapse-btn" @click="midCollapsed = !midCollapsed" :title="midCollapsed ? '展开链路' : '折叠链路'">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline v-if="midCollapsed" points="6 9 12 15 18 9"></polyline>
              <polyline v-else points="18 15 12 9 6 15"></polyline>
            </svg>
          </button>
        </div>

        <div class="workspace-section workspace-mid-top" :class="{ collapsed: midCollapsed }">
          <!-- DAG (hidden when collapsed) -->
          <template v-if="!midCollapsed">
            <PipelineDag
              v-if="selectedTask"
              :nodes="pipeline.nodes"
              :current-task-id="focusedTaskId"
              @select="onDagSelect"
            />
            <div v-else class="panel-placeholder">请选择任务</div>
          </template>

          <!-- Current workflow steps -->
          <CurrentWorkflow
            :task-id="focusedTaskId"
            :mock-run="mockRunForSelected"
            :embedded="true"
            :collapsed="midCollapsed"
            @refresh="onWorkflowRefresh"
            @run-update="onRunUpdate"
            @step-select="onStepSelect"
          />
        </div>

        <div class="workspace-section workspace-mid-bottom">
          <div class="chat-panel">
            <!-- Session info header -->
            <div v-if="activeSession" class="chat-session-header">
              <span class="chat-session-step-name">{{ activeSession.step_name }}</span>
              <span class="chat-session-meta">
                <span v-if="activeSession.status" class="chat-session-status" :class="'status-' + (activeSession.status || '').toLowerCase()">
                  {{ statusText(activeSession.status) }}
                </span>
                <span v-if="activeAgent?.role" class="chat-session-sep">·</span>
                <span v-if="activeAgent?.role" class="chat-session-role-tag">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 2a7 7 0 0 1 7 7c0 3-2 5.5-3.5 7L12 22l-3.5-6C7 14.5 5 12 5 9a7 7 0 0 1 7-7z"/>
                    <circle cx="12" cy="9" r="2.5"/>
                  </svg>
                  {{ roleDisplayName(activeAgent.role) }}
                </span>
                <span v-if="displaySessionId" class="chat-session-sep">·</span>
                <span v-if="displaySessionId" class="chat-session-id" :title="'Session: ' + displaySessionId">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                  </svg>
                  {{ displaySessionId }}
                </span>
              </span>
            </div>
            <StepSessionPanel
              :session-id="activeSession?.session_id ?? null"
              :step-name="activeSession?.step_name || ''"
              :assembled-prompt="activeSession?.assembled_prompt || ''"
              :show-header="false"
            />
          </div>
        </div>
      </div>

      <div v-if="taskListViewMode === 'list'" class="resize-handle" @mousedown="(e) => handleMouseDown(e, 'right')"></div>

      <!-- Right column: file viewer (top) / changed files (bottom), independent vertical split -->
      <div v-if="taskListViewMode === 'list'" class="workspace-col workspace-right-col" :style="{ width: rightWidth + 'px' }">
        <div class="workspace-section workspace-right-top" :style="{ height: rightTopHeight + 'px' }">
          <div class="panel-header">
            <h4>文件查看</h4>
          </div>
          <TaskFileViewer
            :task-id="selectedTask?.id ?? null"
            :project-id="selectedProjectId ?? null"
            :task="selectedTask"
            @refresh="onWorkflowRefresh"
          />
        </div>

        <div class="resize-handle resize-handle-h" @mousedown="(e) => handleMouseDown(e, 'right-vertical')"></div>

        <div class="workspace-section workspace-right-bottom">
          <div class="panel-header">
            <h4>改动文件</h4>
          </div>
          <ChangedFilesPanel
            :task-id="selectedTask?.id ?? null"
            :project-id="selectedProjectId ?? null"
            :task="selectedTask"
            @refresh="onWorkflowRefresh"
          />
        </div>
      </div>
    </div>

    <!-- Task edit/create dialog -->
    <el-dialog
      v-model="showTaskDialog"
      :title="isEditingTask ? '编辑任务' : '新建任务'"
      width="560px"
      align-center
    >
      <el-form label-position="top" label-width="80px">
        <el-form-item label="任务标题">
          <el-input
            v-model="taskForm.title"
            placeholder="输入任务标题"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="taskForm.description"
            type="textarea"
            :rows="4"
            placeholder="任务描述（可选）"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="状态">
            <el-select v-model="taskForm.status" class="full-width">
              <el-option value="TODO" label="待处理" />
              <el-option value="IN_PROGRESS" label="处理中" />
              <el-option value="DONE" label="已完成" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="taskForm.priority" class="full-width">
              <el-option value="LOW" label="LOW" />
              <el-option value="MEDIUM" label="MEDIUM" />
              <el-option value="HIGH" label="HIGH" />
              <el-option value="CRITICAL" label="CRITICAL" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="所属项目">
          <el-select v-model="taskForm.project_id" class="full-width" :disabled="isEditingTask">
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTaskDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingTask" @click="handleSaveTask">
          {{ isEditingTask ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PipelineDag from '../components/workspace/PipelineDag.vue'
import CurrentWorkflow from '../components/workspace/CurrentWorkflow.vue'
import TaskFileViewer from '../components/workspace/TaskFileViewer.vue'
import ChangedFilesPanel from '../components/workspace/ChangedFilesPanel.vue'
import StepSessionPanel from '../components/workflow/StepSessionPanel.vue'
import { useProjectStore } from '../stores/projectStore.js'
import { useAgentStore } from '../stores/agentStore.js'
import { getRoleConfig } from '../constants/agent.js'
import { listTasks, getTaskPipeline, createTask, updateTask, deleteTask as deleteTaskApi } from '../api/task.js'
import draggable from 'vuedraggable'
import { useTaskTimer } from '../composables/kanban/useTaskTimer.js'

const projectStore = useProjectStore()
const agentStore = useAgentStore()
const { runningTasks } = useTaskTimer()
const route = useRoute()
const router = useRouter()

const projects = computed(() => projectStore.projects)
const selectedProjectId = ref(route.params.projectId ? Number(route.params.projectId) : null)

// Active step session info from the current workflow run
const activeSession = ref(null) // { session_id, step_name, assembled_prompt }
const currentRun = ref(null)

// When user manually clicks a step in CurrentWorkflow, show that step's session
const userSelectedStep = ref(null) // { step_id, name, session_id, assembled_prompt }

// ─── Task CRUD ───────────────────────────────────────────────────────────────
const showTaskDialog = ref(false)
const isEditingTask = ref(false)
const savingTask = ref(false)
const taskForm = reactive({
  id: null,
  title: '',
  description: '',
  status: 'TODO',
  priority: 'MEDIUM',
  project_id: null
})

function openCreateTask() {
  isEditingTask.value = false
  taskForm.id = null
  taskForm.title = ''
  taskForm.description = ''
  taskForm.status = 'TODO'
  taskForm.priority = 'MEDIUM'
  taskForm.project_id = selectedProjectId.value || (projects.value.length ? projects.value[0].id : null)
  showTaskDialog.value = true
}

function openEditTask(task) {
  isEditingTask.value = true
  taskForm.id = task.id
  taskForm.title = task.title
  taskForm.description = task.description || ''
  taskForm.status = task.status
  taskForm.priority = task.priority
  taskForm.project_id = task.project_id
  showTaskDialog.value = true
}

async function handleSaveTask() {
  if (!taskForm.title.trim()) {
    ElMessage.warning('任务标题不能为空')
    return
  }
  savingTask.value = true
  try {
    if (isEditingTask.value) {
      const resp = await updateTask(taskForm.id, {
        title: taskForm.title,
        description: taskForm.description,
        status: taskForm.status,
        priority: taskForm.priority
      })
      if (resp?.success) {
        ElMessage.success('任务已更新')
        await loadTasks()
      }
    } else {
      const resp = await createTask({
        title: taskForm.title,
        description: taskForm.description,
        status: taskForm.status,
        priority: taskForm.priority,
        project_id: taskForm.project_id
      })
      if (resp?.success) {
        ElMessage.success('任务已创建')
        await loadTasks()
      }
    }
    showTaskDialog.value = false
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    savingTask.value = false
  }
}

async function handleDeleteTask(task) {
  if (task.id <= 0) return
  try {
    await ElMessageBox.confirm(`确定删除任务「${task.title}」吗？`, '删除任务', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    const resp = await deleteTaskApi(task.id)
    if (resp?.success) {
      ElMessage.success('任务已删除')
      if (selectedTask.value?.id === task.id) selectedTask.value = null
      await loadTasks()
    }
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

async function onKanbanDragEnd(event) {
  const newStatus = event.to?.dataset?.status
  if (!newStatus) return
  const taskId = event.item?.getAttribute('data-id')
  if (!taskId) return
  try {
    const resp = await updateTask(Number(taskId), { status: newStatus })
    if (resp?.success) {
      await loadTasks()
      ElMessage.success('任务状态已更新')
    }
  } catch (e) {
    ElMessage.error(e?.message || '状态更新失败')
  }
}

// --- Mock cross-project pipeline demo ---
// Shows a root task in "DevOps 看板系统" that fans out into children living in other projects.
const MOCK_ROOT_ID = -1001
const MOCK_TASK = {
  id: MOCK_ROOT_ID,
  title: '设计任务依赖 DAG 架构（示例）',
  status: 'DONE',
  priority: 'HIGH',
  project_id: null, // filled in at load time with the current project id
  parent_task_id: null,
  depends_on: []
}
const MOCK_PIPELINE_NODES = [
  { id: -1001, title: '设计任务依赖 DAG 架构', status: 'DONE', workflow_run_status: 'COMPLETED', priority: 'HIGH', parent_task_id: null, depends_on: [], project_name: 'DevOps 看板系统' },
  { id: -1002, title: '用户服务开发', status: 'IN_PROGRESS', workflow_run_status: 'RUNNING', priority: 'HIGH', parent_task_id: -1001, depends_on: [-1001], project_name: '用户服务' },
  { id: -1003, title: '订单服务开发', status: 'IN_PROGRESS', workflow_run_status: 'RUNNING', priority: 'HIGH', parent_task_id: -1001, depends_on: [-1001], project_name: '订单服务' },
  { id: -1004, title: '前端集成', status: 'TODO', workflow_run_status: null, priority: 'MEDIUM', parent_task_id: -1001, depends_on: [-1001], project_name: '前端项目' },
  { id: -1005, title: '集成测试', status: 'TODO', workflow_run_status: null, priority: 'MEDIUM', parent_task_id: -1001, depends_on: [-1002, -1003, -1004], project_name: '集成测试' }
]

// Per-node mock workflow runs — shown when user clicks a DAG node
const MOCK_RUNS = {
  [-1001]: {
    workflow_template_snapshot: { name: '架构设计工作流' },
    status: 'COMPLETED',
    created_at: '2026-05-11T09:00:00+08:00',
    updated_at: '2026-05-11T11:30:00+08:00',
    steps: [
      { step_id: 's1', name: '需求分析', status: 'DONE', started_at: '2026-05-11T09:00:00+08:00', completed_at: '2026-05-11T09:45:00+08:00' },
      { step_id: 's2', name: '架构设计', status: 'DONE', started_at: '2026-05-11T09:45:00+08:00', completed_at: '2026-05-11T10:55:00+08:00' },
      { step_id: 's3', name: 'AI 拆分子任务', status: 'DONE', started_at: '2026-05-11T10:55:00+08:00', completed_at: '2026-05-11T11:30:00+08:00' }
    ]
  },
  [-1002]: {
    workflow_template_snapshot: { name: '用户服务开发工作流' },
    status: 'RUNNING',
    created_at: '2026-05-11T12:00:00+08:00',
    updated_at: '2026-05-12T09:30:00+08:00',
    steps: [
      { step_id: 's1', name: 'API 设计', status: 'DONE', started_at: '2026-05-11T12:00:00+08:00', completed_at: '2026-05-11T13:15:00+08:00' },
      { step_id: 's2', name: '实体建模', status: 'DONE', started_at: '2026-05-11T13:15:00+08:00', completed_at: '2026-05-11T15:00:00+08:00' },
      { step_id: 's3', name: '接口实现', status: 'IN_PROGRESS', started_at: '2026-05-11T15:00:00+08:00', completed_at: null },
      { step_id: 's4', name: '单元测试', status: 'PENDING', started_at: null, completed_at: null }
    ]
  },
  [-1003]: {
    workflow_template_snapshot: { name: '订单服务开发工作流' },
    status: 'RUNNING',
    created_at: '2026-05-11T12:00:00+08:00',
    updated_at: '2026-05-12T09:30:00+08:00',
    steps: [
      { step_id: 's1', name: 'API 设计', status: 'DONE', started_at: '2026-05-11T12:00:00+08:00', completed_at: '2026-05-11T14:00:00+08:00' },
      { step_id: 's2', name: '实体建模', status: 'IN_PROGRESS', started_at: '2026-05-11T14:00:00+08:00', completed_at: null },
      { step_id: 's3', name: '接口实现', status: 'PENDING', started_at: null, completed_at: null },
      { step_id: 's4', name: '单元测试', status: 'PENDING', started_at: null, completed_at: null }
    ]
  },
  [-1004]: {
    workflow_template_snapshot: { name: '前端开发工作流' },
    status: 'PENDING',
    created_at: '2026-05-11T12:00:00+08:00',
    updated_at: '2026-05-11T12:00:00+08:00',
    steps: [
      { step_id: 's1', name: '页面设计', status: 'PENDING', started_at: null, completed_at: null },
      { step_id: 's2', name: '组件开发', status: 'PENDING', started_at: null, completed_at: null },
      { step_id: 's3', name: '联调', status: 'PENDING', started_at: null, completed_at: null }
    ]
  },
  [-1005]: {
    workflow_template_snapshot: { name: '集成测试工作流' },
    status: 'PENDING',
    created_at: '2026-05-11T12:00:00+08:00',
    updated_at: '2026-05-11T12:00:00+08:00',
    steps: [
      { step_id: 's1', name: '环境准备', status: 'PENDING', started_at: null, completed_at: null },
      { step_id: 's2', name: '端到端测试', status: 'PENDING', started_at: null, completed_at: null },
      { step_id: 's3', name: '发布验收', status: 'PENDING', started_at: null, completed_at: null }
    ]
  }
}

const realTasks = ref([])
const pipeline = ref({ root: null, nodes: [] })
const selectedTask = ref(null)
// Currently "focused" DAG node id — what the CurrentWorkflow panel displays.
// Defaults to selectedTask.id but can be switched by clicking any node in the DAG.
const focusedNodeId = ref(null)

const mockRunForSelected = computed(() => {
  const id = focusedNodeId.value ?? selectedTask.value?.id
  if (id != null && id < 0) return MOCK_RUNS[id] || null
  return null
})

// The task id whose workflow is shown below — follows DAG clicks, falls back to selectedTask
const focusedTaskId = computed(() => focusedNodeId.value ?? selectedTask.value?.id ?? null)

const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'DONE']

const selectedStatus = ref(null)
const taskListViewMode = ref('list') // 'list' | 'kanban'
const tasks = computed(() => {
  // Prepend the mock root task so users can preview the cross-project DAG
  const mockForCurrent = { ...MOCK_TASK, project_id: selectedProjectId.value ?? MOCK_TASK.project_id }
  const all = [mockForCurrent, ...realTasks.value]
  if (!selectedStatus.value) return all
  return all.filter(t => t.status === selectedStatus.value)
})

const KANBAN_COLUMNS = [
  { status: 'TODO', title: '待处理' },
  { status: 'IN_PROGRESS', title: '处理中' },
  { status: 'DONE', title: '已完成' },
  { status: 'BLOCKED', title: '阻塞' }
]

const kanbanColumns = computed(() =>
  KANBAN_COLUMNS.map(col => ({
    ...col,
    tasks: tasks.value.filter(t => t.status === col.status)
  }))
)

async function loadTasks() {
  try {
    const resp = selectedProjectId.value
      ? await listTasks({ project_id: selectedProjectId.value })
      : await listTasks()
    if (resp?.success) realTasks.value = resp.data || []
  } catch (e) {
    console.error('Failed to load tasks:', e)
    realTasks.value = []
  }
  if (tasks.value.length && !selectedTask.value) {
    selectedTask.value = tasks.value[0]
  } else if (!tasks.value.length) {
    selectedTask.value = null
  }
}

async function handleProjectChange() {
  selectedStatus.value = null
  selectedTask.value = null
  const path = selectedProjectId.value ? `/workspace/${selectedProjectId.value}` : '/workspace'
  if (route.path !== path) router.replace(path)
  await loadTasks()
}

async function loadPipeline(taskId) {
  if (taskId < 0) {
    // Mock pipeline for demo task
    pipeline.value = { root: MOCK_PIPELINE_NODES[0], nodes: MOCK_PIPELINE_NODES }
    return
  }
  try {
    const resp = await getTaskPipeline(taskId)
    if (resp?.success) {
      const data = resp.data || { root: null, nodes: [] }
      const projectMap = new Map(projects.value.map(p => [p.id, p.name]))
      const nodes = (data.nodes || []).map(n => ({
        ...n,
        project_name: n.project_name || projectMap.get(n.project_id) || ''
      }))
      pipeline.value = { root: data.root, nodes }
    } else {
      pipeline.value = { root: null, nodes: [] }
    }
  } catch (e) {
    pipeline.value = { root: null, nodes: [] }
  }
}

const leftWidth = ref(310)
const rightWidth = ref(380)
const rightTopHeight = ref(380)
const midCollapsed = ref(false)   // middle upper section folded (only header + steps + actions visible)
const LEFT_MIN = 220
const LEFT_MAX = 500
const RIGHT_MIN = 260
const RIGHT_MAX = 700
const SECTION_MIN = 120
const SECTION_MAX = 1200

function handleMouseDown(e, side) {
  e.preventDefault()
  const startX = e.clientX
  const startY = e.clientY
  const startLeft = leftWidth.value
  const startRight = rightWidth.value
  const startRightTop = rightTopHeight.value

  function onMove(ev) {
    if (side === 'left') {
      const delta = ev.clientX - startX
      leftWidth.value = Math.max(LEFT_MIN, Math.min(LEFT_MAX, startLeft + delta))
    } else if (side === 'right') {
      const delta = startX - ev.clientX
      rightWidth.value = Math.max(RIGHT_MIN, Math.min(RIGHT_MAX, startRight + delta))
    } else if (side === 'right-vertical') {
      const delta = ev.clientY - startY
      rightTopHeight.value = Math.max(SECTION_MIN, Math.min(SECTION_MAX, startRightTop + delta))
    }
  }

  function onUp() {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.body.style.cursor = side === 'right-vertical' ? 'row-resize' : 'col-resize'
  document.body.style.userSelect = 'none'
}

const selectTask = (task) => {
  selectedTask.value = task
  focusedNodeId.value = task?.id ?? null
  userSelectedStep.value = null
}

// Clicking a node in the DAG just shifts which workflow is shown below.
// It does NOT re-select the task (that would reload the whole DAG).
function onDagSelect(node) {
  if (!node) return
  focusedNodeId.value = node.id
}

const PRIORITY_TEXT = { CRITICAL: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }

function priorityText(priority) {
  return PRIORITY_TEXT[priority] || priority || ''
}

function priorityClass(priority) {
  const map = { CRITICAL: 'high', HIGH: 'high', MEDIUM: 'medium', LOW: '' }
  return map[priority] || ''
}

function statusClass(status) {
  const map = { DONE: 'done', IN_PROGRESS: 'running', TODO: 'waiting' }
  return map[status] || 'waiting'
}

async function onWorkflowRefresh() {
  if (selectedTask.value?.id) {
    await loadPipeline(selectedTask.value.id)
  }
}

// Extract the active step's session info from a workflow run
function onRunUpdate(run) {
  currentRun.value = run || null
  // Clear manual step selection when run data changes
  userSelectedStep.value = null
  if (!run?.steps) {
    activeSession.value = null
    return
  }
  // Priority: running step first, then suspended, then last step with session
  const running = run.steps.find(s => s.session_id && (s.status === 'RUNNING' || s.status === 'IN_PROGRESS'))
  const suspended = run.steps.find(s => s.session_id && s.status === 'SUSPENDED')
  const lastWithSession = [...run.steps].reverse().find(s => s.session_id)
  const active = running || suspended || lastWithSession
  if (active) {
    activeSession.value = {
      session_id: active.session_id,
      provider_session_id: active.provider_session_id || null,
      step_name: active.name || active.step_id || '',
      assembled_prompt: active.assembled_prompt || '',
      status: active.status,
      agent_id: active.agent_id || null
    }
  } else {
    activeSession.value = null
  }
}

// User clicked a step in CurrentWorkflow — show that step's session
function onStepSelect(step) {
  userSelectedStep.value = step
  if (step.session_id) {
    activeSession.value = {
      session_id: step.session_id,
      provider_session_id: step.provider_session_id || null,
      step_name: step.name,
      assembled_prompt: step.assembled_prompt || '',
      status: step.status,
      agent_id: step.agent_id || null
    }
  } else {
    activeSession.value = null
  }
}

// --- Active session enrichment ---
const activeAgent = computed(() => {
  const agentId = activeSession.value?.agent_id
  if (!agentId) return null
  return agentStore.agents.find(a => a.id === agentId || String(a.id) === String(agentId)) || null
})

// Display session ID: prefer provider_session_id (agent's session), fallback to internal session_id
const displaySessionId = computed(() => {
  return activeSession.value?.provider_session_id
    || (activeSession.value?.session_id ? `#${activeSession.value.session_id}` : null)
})

const STEP_STATUS_TEXT = {
  DONE: '已完成',
  COMPLETED: '已完成',
  IN_PROGRESS: '处理中',
  RUNNING: '执行中',
  FAILED: '失败',
  CANCELLED: '已取消',
  SUSPENDED: '挂起',
  PENDING: '待执行',
  TODO: '待处理',
  BLOCKED: '阻塞'
}

function statusText(status) {
  return STEP_STATUS_TEXT[status] || status || '未知'
}

function roleDisplayName(role) {
  return getRoleConfig(role)?.name || role
}

const workflowDisplayName = computed(() => {
  return currentRun.value?.workflow_template_snapshot?.name
    || currentRun.value?.workflow_id
    || null
})

const RUN_STATUS_LABEL = { COMPLETED: '已完成', RUNNING: '运行中', FAILED: '失败', CANCELLED: '已取消', PENDING: '待启动', SUSPENDED: '已暂停' }
const RUN_STATUS_CLASS = { COMPLETED: 'done', RUNNING: 'running', FAILED: 'failed', CANCELLED: 'failed', PENDING: 'pending', SUSPENDED: 'running' }

const runStatusLabel = computed(() => {
  const s = currentRun.value?.status
  return s ? (RUN_STATUS_LABEL[s] || s) : null
})
const runStatusClass = computed(() => {
  const s = currentRun.value?.status
  return s ? (RUN_STATUS_CLASS[s] || 'pending') : 'pending'
})

onMounted(async () => {
  await Promise.all([
    projectStore.fetchProjects(),
    agentStore.fetchAgents()
  ])
  if (!selectedProjectId.value && projects.value.length) {
    selectedProjectId.value = projects.value[0].id
    router.replace(`/workspace/${selectedProjectId.value}`)
  }
  await loadTasks()
})
watch(() => selectedTask.value?.id, async (newId) => {
  if (newId) await loadPipeline(newId)
})
watch(taskListViewMode, (mode) => {
  if (mode === 'kanban') {
    leftWidth.value = Math.max(leftWidth.value, 900)
  } else {
    leftWidth.value = Math.min(leftWidth.value, 360)
  }
})
</script>

<style scoped>
.workspace-view {
  display: flex;
  height: 100%;
  background: var(--page-bg);
  font-size: var(--font-size-sm);
}

/* Left: Task List */
.workspace-left {
  border-right: 1px solid var(--border-color);
  background: var(--bg-primary);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

/* Resize handles */
.resize-handle {
  width: 6px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  position: relative;
  z-index: 10;
}

.resize-handle::after {
  content: '';
  position: absolute;
  left: 2px;
  top: 0;
  width: 2px;
  height: 100%;
  background: transparent;
  transition: background 0.15s;
}

.resize-handle:hover::after,
.resize-handle:active::after {
  background: var(--accent-color);
}

.resize-handle-h {
  width: 100%;
  height: 8px;
  cursor: row-resize;
  flex-shrink: 0;
  background: var(--border-color);
  position: relative;
  z-index: 10;
  transition: background 0.15s;
}

.resize-handle-h:hover,
.resize-handle-h:active {
  background: var(--accent-color);
}

.resize-handle-h::after {
  display: none;
}

.resize-handle-h::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 40px;
  height: 2px;
  background: var(--text-muted);
  border-radius: 1px;
  opacity: 0.5;
  pointer-events: none;
}

.workspace-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.workspace-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.task-count {
  font-size: 12px;
  color: var(--text-muted);
  background: var(--bg-tertiary);
  padding: 2px 8px;
  border-radius: 10px;
}

.project-filter-bar {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
}

.project-filter-bar select {
  width: 100%;
  padding: 8px 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23888' stroke-width='2'%3E%3Cpolyline points='6,9 12,15 18,9'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  padding-right: 32px;
}

.project-filter-bar select:hover {
  border-color: var(--accent-color);
}

.project-filter-bar select:focus {
  outline: none;
  border-color: var(--accent-color);
  box-shadow: 0 0 0 3px var(--accent-color-soft);
}

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 8px;
}

.task-status-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 8px 10px;
}

.status-chip {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.status-chip:hover {
  border-color: var(--accent-color);
  color: var(--text-primary);
}

.status-chip.active {
  background: var(--accent-color-soft);
  border-color: var(--accent-color);
  color: var(--accent-color-strong);
}

.task-list-actions {
  padding: 4px 4px 8px;
  border-bottom: 1px solid var(--border-color);
}

.task-list-actions .el-button {
  font-size: 12px;
}

.task-card {
  position: relative;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s ease;
  margin-bottom: 4px;
  border: 1px solid transparent;
  background: var(--bg-secondary);
}

.task-card.is-mock {
  border-style: dashed;
  border-color: rgba(37, 198, 201, 0.25);
  background: rgba(37, 198, 201, 0.02);
}

.task-card:hover {
  background: var(--bg-tertiary);
  border-color: var(--border-color);
}

.task-card.selected {
  background: var(--teal-accent-mid);
  border-color: var(--teal-active-border);
  box-shadow: var(--shadow-sm);
}

.task-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.task-priority {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  text-transform: uppercase;
}

.task-priority.high {
  background: var(--danger-soft);
  color: var(--danger-strong);
}

.task-priority.medium {
  background: var(--warning-soft);
  color: var(--warning-strong);
}

.task-status {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 4px;
}

.task-status.done {
  background: var(--done-soft);
  color: var(--done-strong);
}

.task-status.running {
  background: var(--in-progress-soft);
  color: var(--in-progress-strong);
}

.task-status.waiting {
  background: var(--neutral-soft);
  color: var(--neutral-strong);
}

.task-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
  margin-bottom: 6px;
  padding-right: 40px;
  letter-spacing: -0.1px;
}

.task-card-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
  margin-bottom: 6px;
}

.task-card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.task-card:hover .task-card-actions {
  opacity: 1;
}

.task-card-actions .el-button {
  padding: 2px 4px;
  min-height: auto;
}

/* Task dialog */
.form-row {
  display: flex;
  gap: 16px;
}

.form-row .el-form-item {
  flex: 1;
}

.full-width {
  width: 100%;
}

/* Right side: two independent columns */
.workspace-right {
  flex: 1;
  display: flex;
  flex-direction: row;
  min-width: 0;
  min-height: 0;
}

.workspace-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg-primary);
}

.workspace-mid-col {
  flex: 1;
  min-width: 0;
  border-right: 1px solid var(--border-color);
}

.workspace-right-col {
  flex-shrink: 0;
  min-width: 320px;
  overflow: hidden;
}

.workspace-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg-primary);
  overflow: hidden;
}

.workspace-mid-top {
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.workspace-mid-top.collapsed {
  max-height: unset;
}

.workspace-right-top {
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-color);
  overflow-y: auto;
}

.workspace-right-top {
  height: v-bind(rightTopHeight + 'px');
}

.workspace-mid-bottom,
.workspace-right-bottom {
  flex: 1;
  min-height: 0;
}

.workspace-section > .panel-header {
  flex-shrink: 0;
}

.workspace-mid-bottom > *:not(.panel-header),
.workspace-right-top > *:not(.panel-header),
.workspace-right-bottom > *:not(.panel-header) {
  flex: 1;
  min-height: 0;
}

.workspace-mid-top > * {
  flex-shrink: 0;
}

.workspace-mid-bottom {
  display: flex;
  flex-direction: column;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-top: 1px solid var(--border-color);
}

/* Chat session info header */
.chat-session-header {
  flex-shrink: 0;
  padding: 8px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 40px;
}

.chat-session-step-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-session-meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  white-space: nowrap;
}

.chat-session-sep {
  color: var(--text-muted);
  font-size: 12px;
  opacity: 0.5;
  user-select: none;
}

.chat-session-status {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}

.chat-session-status.status-done,
.chat-session-status.status-completed {
  background: var(--done-soft);
  color: var(--done-strong);
}

.chat-session-status.status-in_progress,
.chat-session-status.status-running {
  background: var(--in-progress-soft);
  color: var(--in-progress-strong);
}

.chat-session-status.status-failed,
.chat-session-status.status-cancelled {
  background: var(--danger-soft);
  color: var(--danger-strong);
}

.chat-session-status.status-suspended {
  background: var(--warning-soft);
  color: var(--warning-strong);
}

.chat-session-status.status-pending {
  background: var(--neutral-soft);
  color: var(--neutral-strong);
}

.chat-session-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  color: var(--text-secondary);
}

.chat-session-badge svg {
  color: var(--accent-color);
  flex-shrink: 0;
}

.chat-session-role {
  font-size: 10px;
  color: var(--text-muted);
  font-weight: 400;
  margin-left: 2px;
}

.chat-session-role-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.chat-session-id {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
}

.chat-session-id svg {
  flex-shrink: 0;
  opacity: 0.6;
}

/* Panel Header */
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

/* Unified workflow header (merges 工作流链路 + 当前工作流) */
.workflow-unified-header {
  padding: 8px 14px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  background: linear-gradient(180deg, var(--bg-primary) 0%, rgba(37, 198, 201, 0.02) 100%);
}

.workflow-unified-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.workflow-unified-title h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.1px;
  flex-shrink: 0;
}

.workflow-unified-title .panel-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--accent-color-soft);
  color: var(--accent-color);
  flex-shrink: 0;
}

.workflow-name-badge {
  font-size: 11px;
  color: var(--accent-color-strong);
  background: var(--accent-color-soft);
  padding: 3px 10px;
  border-radius: 999px;
  font-weight: 600;
  border: 1px solid rgba(37, 198, 201, 0.2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
  padding: 0;
}

.collapse-btn:hover {
  background: var(--accent-color-soft);
  border-color: var(--accent-color);
  color: var(--accent-color);
}

.run-status-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
  flex-shrink: 0;
  border: 1px solid;
}

.run-status-chip.done {
  color: var(--done-strong);
  background: var(--done-soft);
  border-color: rgba(37, 198, 201, 0.2);
}
.run-status-chip.running {
  color: var(--in-progress-strong);
  background: rgba(37, 198, 201, 0.08);
  border-color: rgba(37, 198, 201, 0.25);
}
.run-status-chip.failed {
  color: var(--danger-strong);
  background: var(--danger-soft);
  border-color: rgba(239, 68, 68, 0.2);
}
.run-status-chip.pending {
  color: var(--text-muted);
  background: var(--bg-tertiary);
  border-color: var(--border-color);
}

.run-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.run-status-chip.running .run-status-dot {
  animation: status-dot-pulse 1.4s ease-in-out infinite;
  box-shadow: 0 0 6px currentColor;
}

@keyframes status-dot-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}

.panel-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  color: var(--text-muted);
  font-size: 12px;
  min-height: 60px;
}

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

/* Kanban board */
.task-kanban-board {
  display: flex;
  gap: 8px;
  padding: 8px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.task-kanban-column {
  flex: 1;
  min-width: 180px;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.task-kanban-column-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.task-kanban-column-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.task-kanban-column-count {
  font-size: 11px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
}

.task-kanban-column [draggable] {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

.task-kanban-column .task-card {
  padding: 10px 12px;
  margin-bottom: 4px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
}

.task-kanban-column .task-card:hover {
  background: var(--bg-tertiary);
  border-color: var(--border-color);
}

.task-kanban-column .task-card.selected {
  background: var(--teal-accent-mid);
  border-color: var(--teal-active-border);
  box-shadow: var(--shadow-sm);
}

.task-kanban-column .task-card:last-child {
  margin-bottom: 0;
}
</style>
