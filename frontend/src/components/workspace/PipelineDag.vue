<template>
  <div class="pipeline-inline" v-if="dagLayers.length">
    <div class="pipeline-dag-dialog">
      <div class="dag-flow">
        <template v-for="(layer, layerIdx) in dagLayers" :key="layerIdx">
          <template v-for="node in layer" :key="node.id">
            <div class="dag-column">
              <div
                class="dag-node-wrapper"
                :class="nodeStatusClass(node)"
              >
                <!-- Animated arrow above the current node -->
                <div v-if="node.id === currentTaskId" class="dag-current-arrow" aria-hidden="true">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="#111827" stroke="#ffffff" stroke-width="2" stroke-linejoin="round">
                    <path d="M12 20 L4 8 L9 8 L9 2 L15 2 L15 8 L20 8 Z"></path>
                  </svg>
                </div>
                <div class="dag-node" @click="emit('select', node)">
                  <span class="dag-node-status">{{ statusIcon(node) }}</span>
                  <div class="dag-node-texts">
                    <span class="dag-node-title">{{ node.title }}</span>
                    <span v-if="node.project_name" class="dag-node-project">{{ node.project_name }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <div v-if="layerIdx < dagLayers.length - 1" class="dag-arrow-h"></div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  nodes: { type: Array, required: true },
  currentTaskId: { type: Number, default: null }
})

const emit = defineEmits(['select'])

// Map a node to its workflow-status class.
// task.status takes priority for terminal states (DONE / BLOCKED / CANCELLED)
// because those represent the user's definitive view; for active states we
// consult workflow_run_status first, then fall back to task status.
function nodeStatusClass(node) {
  if (node.status === 'DONE') return 'is-done'
  if (node.status === 'CANCELLED') return 'failed'

  const wf = node.workflow_run_status
  if (wf === 'COMPLETED' || wf === 'DONE') return 'is-done'
  if (wf === 'RUNNING' || wf === 'IN_PROGRESS' || wf === 'SUSPENDED') return 'running'
  if (wf === 'FAILED' || wf === 'CANCELLED') return 'failed'
  if (wf === 'PENDING') return 'pending'
  if (!wf) {
    if (node.status === 'IN_PROGRESS') return 'running'
    if (node.status === 'BLOCKED' || node.status === 'FAILED') return 'failed'
    return 'pending'
  }
  return 'pending'
}

function statusIcon(node) {
  const cls = nodeStatusClass(node)
  const icons = { 'is-done': '✓', running: '▶', failed: '✗', pending: '○' }
  return icons[cls] || '○'
}

const dagLayers = computed(() => {
  const nodes = props.nodes || []
  if (!nodes.length) return []
  const idSet = new Set(nodes.map((n) => n.id))
  const depth = new Map()
  for (const n of nodes) depth.set(n.id, 0)
  let changed = true
  let maxIter = nodes.length * nodes.length + 1
  while (changed && maxIter-- > 0) {
    changed = false
    for (const n of nodes) {
      // Treat both explicit depends_on and parent_task_id as layering constraints
      // so parent → child always shows an arrow even when the child has no deps.
      const predecessors = [...(n.depends_on || [])]
      if (n.parent_task_id && idSet.has(n.parent_task_id)) {
        predecessors.push(n.parent_task_id)
      }
      for (const depId of predecessors) {
        const d = (depth.get(depId) ?? 0) + 1
        if (d > (depth.get(n.id) ?? 0)) {
          depth.set(n.id, d)
          changed = true
        }
      }
    }
  }
  const layers = []
  for (const n of nodes) {
    const d = depth.get(n.id) ?? 0
    if (!layers[d]) layers[d] = []
    layers[d].push(n)
  }
  return layers
})
</script>

<style scoped>
.pipeline-inline {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 26px 16px 12px;
  min-height: 60px;
  overflow: auto;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}

.pipeline-inline .pipeline-dag-dialog {
  padding: 0;
}

.pipeline-inline .dag-node {
  padding: 6px 12px;
}

.pipeline-inline .dag-node-title {
  font-size: 13px;
}

.pipeline-inline .dag-node-project {
  font-size: 11px;
}

.dag-current-arrow {
  position: absolute;
  top: -20px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: dag-arrow-bounce 1.2s ease-in-out infinite;
  pointer-events: none;
  z-index: 3;
}

.dag-current-arrow svg {
  filter: drop-shadow(0 2px 3px rgba(0, 0, 0, 0.25));
}

@keyframes dag-arrow-bounce {
  0%, 100% { transform: translate(-50%, 0); }
  50% { transform: translate(-50%, -4px); }
}

.pipeline-dag-dialog {
  padding: 24px 16px;
  overflow-x: auto;
}

.dag-flow {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
  padding: 24px 0 4px;
}

.dag-column {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  flex-shrink: 0;
  width: 160px;
  gap: 4px;
}

.dag-node-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  position: relative;
}

.dag-arrow-h {
  font-size: 14px;
  color: var(--text-muted);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 2px;
  background: var(--border-color);
  position: relative;
}

.dag-arrow-h::after {
  content: '';
  position: absolute;
  right: -4px;
  top: 50%;
  transform: translateY(-50%);
  border-left: 6px solid var(--border-color);
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}

.dag-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  border: 1px solid;
  cursor: pointer;
  user-select: none;
  transition: opacity 0.15s;
  width: 100%;
  justify-content: flex-start;
  white-space: nowrap;
  text-align: left;
}

.dag-node:hover {
  opacity: 0.85;
}

.dag-node-texts {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.25;
  min-width: 0;
  flex: 1;
}

.dag-node-wrapper.is-done .dag-node {
  background: rgba(37, 198, 201, 0.12);
  border-color: rgba(37, 198, 201, 0.5);
  color: #0891a4;
}

.dag-node-wrapper.running .dag-node {
  background: rgba(245, 158, 11, 0.14);
  border-color: rgba(245, 158, 11, 0.55);
  color: #b45309;
}

.dag-node-wrapper.pending .dag-node,
.dag-node-wrapper.waiting .dag-node {
  background: #ffffff;
  border-color: var(--border-color);
  color: var(--text-secondary);
}

.dag-node-wrapper.failed .dag-node {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.55);
  color: #b91c1c;
}

.dag-node-status {
  font-size: 12px;
  line-height: 1;
  flex-shrink: 0;
}

.dag-node-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  width: 100%;
}

.dag-node-project {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 400;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
}
</style>
