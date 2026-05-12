<template>
  <div class="pipeline-inline" v-if="dagLayers.length">
    <div class="pipeline-dag-dialog">
      <div class="dag-flow">
        <template v-for="(layer, layerIdx) in dagLayers" :key="layerIdx">
          <template v-for="node in layer" :key="node.id">
            <div class="dag-column">
              <div
                class="dag-node-wrapper"
                :class="{
                  current: node.id === currentTaskId,
                  'is-done': node.status === 'DONE',
                  running: node.status === 'IN_PROGRESS',
                  waiting: node.status === 'WAITING',
                  failed: node.status === 'BLOCKED' || node.status === 'FAILED'
                }"
              >
                <!-- Animated arrow above the current node -->
                <div v-if="node.id === currentTaskId" class="dag-current-arrow" aria-hidden="true">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="#111827" stroke="#ffffff" stroke-width="2" stroke-linejoin="round">
                    <path d="M12 20 L4 8 L9 8 L9 2 L15 2 L15 8 L20 8 Z"></path>
                  </svg>
                </div>
                <div class="dag-node" @click="emit('select', node)">
                  <span class="dag-node-status">{{ statusIcon(node.status) }}</span>
                  <span class="dag-node-title">{{ node.title }}</span>
                  <span v-if="node.id === currentTaskId" class="current-badge">当前</span>
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

function statusIcon(status) {
  const icons = { DONE: '✓', IN_PROGRESS: '▶', WAITING: '○', BLOCKED: '✗', FAILED: '✗', TODO: '○', CANCELLED: '✗' }
  return icons[status] || '○'
}

const dagLayers = computed(() => {
  const nodes = props.nodes || []
  if (!nodes.length) return []
  const depth = new Map()
  for (const n of nodes) depth.set(n.id, 0)
  let changed = true
  // Guard: if a cycle slips past backend validation, bound the relaxation
  // iterations so we never infinite-loop. Worst-case DAG needs O(V*E) passes,
  // so V^2 + 1 is a safe upper bound.
  let maxIter = nodes.length * nodes.length + 1
  while (changed && maxIter-- > 0) {
    changed = false
    for (const n of nodes) {
      for (const depId of (n.depends_on || [])) {
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
  padding: 5px 10px;
}

.pipeline-inline .dag-node span {
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
  padding: 4px 0;
}

.dag-column {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  flex-shrink: 0;
  width: 140px;
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

.dag-node-wrapper.current::before {
  content: '';
  position: absolute;
  top: -3px;
  left: -3px;
  right: -3px;
  bottom: -3px;
  border: 2px solid var(--accent-color);
  border-radius: 8px;
  pointer-events: none;
  animation: current-pulse 2s ease-in-out infinite;
}

@keyframes current-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(37, 198, 201, 0.4);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(37, 198, 201, 0);
  }
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
  gap: 4px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
  border: 1px solid;
  cursor: pointer;
  user-select: none;
  transition: opacity 0.15s;
  width: 100%;
  justify-content: center;
  white-space: nowrap;
}

.dag-node:hover {
  opacity: 0.85;
}

.dag-node-wrapper.is-done .dag-node {
  background: var(--done-soft);
  border-color: var(--teal-border-soft);
  color: var(--done-strong);
}

.dag-node-wrapper.running .dag-node {
  background: var(--in-progress-soft);
  border-color: var(--teal-active-border);
  color: var(--in-progress-strong);
}

.dag-node-wrapper.waiting .dag-node {
  background: var(--neutral-soft);
  border-color: var(--border-color);
  color: var(--neutral-strong);
}

.dag-node-wrapper.failed .dag-node {
  background: var(--danger-soft);
  border-color: var(--danger-strong);
  color: var(--danger-strong);
}

.dag-node-status {
  font-size: 12px;
  line-height: 1;
}

.dag-node-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.current-badge {
  font-size: 9px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: 600;
  flex-shrink: 0;
}
</style>
