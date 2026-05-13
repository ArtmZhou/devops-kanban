<template>
  <!-- Directory node -->
  <div v-if="node.type === 'directory' && (visibleChildren.length || !search)" class="tree-folder">
    <div
      class="file-tree-item folder-header"
      :class="{ expanded: isOpen }"
      @click="onToggle"
    >
      <span class="chevron">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="6 9 12 15 18 9"></polyline>
        </svg>
      </span>
      <span class="file-icon folder">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
        </svg>
      </span>
      <span class="file-name">{{ node.name }}</span>
    </div>
    <div v-show="isOpen" class="tree-folder-children">
      <TaskFileViewerTreeNode
        v-for="child in visibleChildren"
        :key="child.path"
        :node="child"
        :search="search"
        :current-file="currentFile"
        @select="$emit('select', $event)"
      />
    </div>
  </div>

  <!-- File node -->
  <div v-else-if="!search || nameMatches" class="file-tree-item file-node" :class="{ active: isActive }" @click="$emit('select', node)">
    <span class="file-indent"></span>
    <span class="file-icon file">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
        <polyline points="14 2 14 8 20 8"></polyline>
      </svg>
    </span>
    <span class="file-name">{{ node.name }}</span>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  node: { type: Object, required: true },
  search: { type: String, default: '' },
  currentFile: { type: String, default: '' }
})

defineEmits(['select'])

const isOpen = ref(false)

const children = computed(() => {
  if (props.node.type !== 'directory') return []
  return props.node.children || []
})

const visibleChildren = computed(() => {
  if (!props.search) return children.value
  const q = props.search.toLowerCase()
  return children.value.filter(child => {
    if (child.type === 'file') return child.path.toLowerCase().includes(q)
    if (child.type === 'directory') return directoryHasMatch(child, q)
    return false
  })
})

function directoryHasMatch(dirNode, query) {
  if (!dirNode.children) return false
  for (const child of dirNode.children) {
    if (child.type === 'file' && child.path.toLowerCase().includes(query)) return true
    if (child.type === 'directory' && directoryHasMatch(child, query)) return true
  }
  return false
}

const nameMatches = computed(() => {
  if (!props.search) return true
  return props.node.name.toLowerCase().includes(props.search.toLowerCase())
})

const isActive = computed(() => {
  return props.node.type === 'file' && props.currentFile === props.node.path
})

function onToggle() {
  isOpen.value = !isOpen.value
}

watch(() => props.search, (newVal) => {
  if (newVal && visibleChildren.value.length > 0) {
    isOpen.value = true
  }
})
</script>

<style scoped>
.tree-folder {
  display: flex;
  flex-direction: column;
}

.tree-folder-children {
  padding-left: 16px;
  border-left: 1px solid var(--border-color);
  margin-left: 6px;
}

.file-tree-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  margin: 1px 4px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: var(--text-secondary);
  transition: all 0.12s;
  white-space: nowrap;
  user-select: none;
}

.file-tree-item:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.file-tree-item.active {
  background: rgba(37, 198, 201, 0.12);
  color: var(--accent-color);
  font-weight: 500;
  border-left: 2px solid var(--accent-color);
  padding-left: 6px;
}

.file-tree-item.active .file-icon {
  opacity: 1;
}

.folder-header {
  font-weight: 500;
  color: var(--text-primary);
}

.folder-header:hover {
  background: var(--bg-tertiary);
}

.chevron {
  display: flex;
  align-items: center;
  transition: transform 0.15s;
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  opacity: 0.5;
}

.chevron svg {
  transform: rotate(-90deg);
  transition: transform 0.15s;
}

.folder-header.expanded .chevron svg {
  transform: rotate(0deg);
}

.file-indent {
  display: inline-block;
  width: 14px;
  flex-shrink: 0;
}

.file-icon {
  display: flex;
  flex-shrink: 0;
  opacity: 0.6;
}

.file-icon.file { color: var(--accent-color); }
.file-icon.folder { color: var(--warning-strong); }

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
