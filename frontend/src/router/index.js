import { createRouter, createWebHistory } from 'vue-router'
import KanbanView from '../views/KanbanView.vue'
import TaskSourceConfig from '../views/TaskSourceConfig.vue'
import AgentConfig from '../views/AgentConfig.vue'

const routes = [
  {
    path: '/',
    name: 'KanbanView',
    component: KanbanView
  },
  {
    path: '/task-sources',
    name: 'TaskSourceConfig',
    component: TaskSourceConfig
  },
  {
    path: '/agents',
    name: 'AgentConfig',
    component: AgentConfig
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
