<template>
  <div class="workflow-timeline">
    <!-- 工作流标题区 -->
    <div class="workflow-header">
      <div class="workflow-title">
        <span class="workflow-icon">🔄</span>
        <h3>{{ workflow?.name || '团队协作流程' }}</h3>
      </div>
      <div class="workflow-progress">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progress.percent + '%' }"></div>
        </div>
        <span class="progress-text">{{ progress.completed }}/{{ progress.total }} 完成</span>
      </div>
    </div>

    <!-- 节点时间线 - 按阶段展示 -->
    <div class="timeline-container">
      <div class="timeline-scroll">
        <div class="timeline-stages">
          <template v-for="(stage, stageIndex) in sortedStages" :key="stage.id">
            <!-- 阶段容器 -->
            <div class="stage-container" :class="{ 'is-parallel': stage.parallel }">
              <!-- 阶段标签 -->
              <div class="stage-label" v-if="stage.parallel">
                <span class="parallel-icon">⚡</span>
                <span>并行</span>
              </div>

              <!-- 分支线（并行阶段开始） -->
              <div v-if="stage.parallel && stage.nodes.length > 1" class="branch-lines branch-start">
                <svg width="100%" height="20" viewBox="0 0 100 20">
                  <path d="M50 0 L50 10" stroke="#3b82f6" stroke-width="2" fill="none"/>
                  <path v-for="(node, idx) in stage.nodes" :key="'branch-'+idx"
                    :d="getBranchPath(idx, stage.nodes.length)"
                    :stroke="getNodeColor(node)"
                    stroke-width="2"
                    fill="none"/>
                </svg>
              </div>

              <!-- 节点组 -->
              <div class="stage-nodes" :class="{ 'parallel-nodes': stage.parallel && stage.nodes.length > 1 }">
                <template v-for="node in stage.nodes" :key="node.id">
                  <WorkflowNode
                    :node="node"
                    :is-current="node.id === workflow?.currentNodeId"
                    :is-selected="node.id === selectedNodeId"
                    @select="$emit('select-node', $event)"
                  />
                </template>
              </div>

              <!-- 合并线（并行阶段结束） -->
              <div v-if="stage.parallel && stage.nodes.length > 1" class="branch-lines branch-end">
                <svg width="100%" height="20" viewBox="0 0 100 20">
                  <path v-for="(node, idx) in stage.nodes" :key="'merge-'+idx"
                    :d="getMergePath(idx, stage.nodes.length)"
                    :stroke="getNodeColor(node)"
                    stroke-width="2"
                    fill="none"/>
                  <path d="M50 10 L50 20" stroke="#3b82f6" stroke-width="2" fill="none"/>
                </svg>
              </div>

              <!-- 阶段间连接线 -->
              <div v-if="stageIndex < sortedStages.length - 1" class="stage-connector">
                <div class="connector-arrow">→</div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- Agent 图例 -->
    <div class="agent-legend">
      <span class="legend-item parallel-legend">
        <span class="legend-icon">⚡</span>
        <span class="legend-name">并行执行</span>
      </span>
      <span
        v-for="(config, type) in agentConfig"
        :key="type"
        class="legend-item"
      >
        <span class="legend-icon">{{ config.icon }}</span>
        <span class="legend-name">{{ config.name }}</span>
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import WorkflowNode from './WorkflowNode.vue'
import { agentConfig, nodeStatusConfig, getWorkflowProgress } from '@/mock/workflowData'

const props = defineProps({
  workflow: {
    type: Object,
    default: null
  },
  selectedNodeId: {
    type: Number,
    default: null
  }
})

defineEmits(['select-node'])

const sortedStages = computed(() => {
  if (!props.workflow?.stages) return []
  return [...props.workflow.stages].sort((a, b) => a.order - b.order)
})

const progress = computed(() => {
  return getWorkflowProgress(props.workflow)
})

const getNodeColor = (node) => {
  return nodeStatusConfig[node.status]?.color || '#6B7280'
}

// 计算分支线路径
const getBranchPath = (index, total) => {
  if (total <= 1) return ''
  const spacing = 80 / (total - 1)
  const x = 10 + index * spacing
  return `M50 10 Q ${x} 15 ${x} 20`
}

// 计算合并线路径
const getMergePath = (index, total) => {
  if (total <= 1) return ''
  const spacing = 80 / (total - 1)
  const x = 10 + index * spacing
  return `M${x} 0 Q ${x} 5 50 10`
}
</script>

<style scoped>
.workflow-timeline {
  background: #f9fafb;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
}

/* 标题区 */
.workflow-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.workflow-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-icon {
  font-size: 20px;
}

.workflow-title h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

/* 进度条 */
.workflow-progress {
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-bar {
  width: 120px;
  height: 8px;
  background: #e5e7eb;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(to right, #10b981, #34d399);
  border-radius: 4px;
  transition: width 0.5s ease;
}

.progress-text {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}

/* 时间线容器 */
.timeline-container {
  overflow-x: auto;
  margin-bottom: 12px;
}

.timeline-scroll {
  min-width: max-content;
}

.timeline-stages {
  display: flex;
  align-items: flex-start;
  gap: 0;
}

/* 阶段容器 */
.stage-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  padding: 0 8px;
}

.stage-container.is-parallel {
  background: linear-gradient(to bottom, rgba(59, 130, 246, 0.05), transparent);
  border-radius: 12px;
  padding: 8px 16px;
  border: 1px dashed rgba(59, 130, 246, 0.3);
}

/* 阶段标签 */
.stage-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #3b82f6;
  margin-bottom: 8px;
  padding: 2px 8px;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 4px;
}

.parallel-icon {
  font-size: 12px;
}

/* 分支线 */
.branch-lines {
  width: 100%;
  min-width: 200px;
}

.branch-lines svg {
  display: block;
}

/* 节点组 */
.stage-nodes {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.stage-nodes.parallel-nodes {
  flex-direction: row;
  gap: 16px;
}

/* 阶段连接器 */
.stage-connector {
  display: flex;
  align-items: center;
  padding: 0 8px;
  align-self: center;
}

.connector-arrow {
  font-size: 18px;
  color: #9ca3af;
}

/* Agent 图例 */
.agent-legend {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #6b7280;
}

.legend-icon {
  font-size: 14px;
}

.legend-name {
  font-weight: 500;
}

.parallel-legend {
  color: #3b82f6;
}

/* 滚动条样式 */
.timeline-container::-webkit-scrollbar {
  height: 6px;
}

.timeline-container::-webkit-scrollbar-track {
  background: #e5e7eb;
  border-radius: 3px;
}

.timeline-container::-webkit-scrollbar-thumb {
  background: #9ca3af;
  border-radius: 3px;
}

.timeline-container::-webkit-scrollbar-thumb:hover {
  background: #6b7280;
}
</style>
