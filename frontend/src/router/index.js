import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'ProjectList',
    component: () => import('../views/ProjectListView.vue')
  },
  {
    path: '/workspace/:projectId?',
    name: 'WorkspaceView',
    component: () => import('../views/WorkspaceView.vue')
  },
  {
    path: '/agents',
    name: 'AgentConfig',
    component: () => import('../views/AgentConfig.vue')
  },
  {
    path: '/workflow-template',
    name: 'WorkflowTemplateConfig',
    component: () => import('../views/WorkflowTemplateConfig.vue')
  },
  {
    path: '/skills',
    name: 'SkillConfig',
    component: () => import('../views/SkillConfig.vue')
  },
  {
    path: '/mcp-servers',
    name: 'McpServerConfig',
    component: () => import('../views/McpServerConfig.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
