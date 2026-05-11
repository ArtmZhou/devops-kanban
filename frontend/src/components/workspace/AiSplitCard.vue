<template>
  <div class="ai-split-card" :class="{ collapsed: !expanded }">
    <div class="split-card-header" @click="expanded = !expanded">
      <div class="split-header-left">
        <svg class="split-ai-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 2a4 4 0 0 1 4 4c0 1.95-1.4 3.58-3.25 3.93L12 22"></path>
          <path d="M8 6a4 4 0 0 1 4-4"></path>
          <circle cx="18" cy="18" r="3"></circle>
          <path d="M18 15v-3l2-2"></path>
        </svg>
        <h4>AI 拆分建议</h4>
        <span class="split-subtitle">工作流已完成，建议拆分为以下子任务</span>
      </div>
      <div class="split-header-right">
        <span class="split-task-count">{{ suggestions.length }} 个子任务</span>
        <span class="split-toggle" :class="{ open: expanded }">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
        </span>
      </div>
    </div>

    <div class="split-card-body" v-show="expanded">
      <div class="split-suggestions-list">
        <div
          v-for="(item, index) in suggestions"
          :key="index"
          class="split-suggestion-item"
        >
          <div class="suggestion-header">
            <div class="suggestion-check">
              <input type="checkbox" :checked="enabledIndices.has(index)" @change="toggleEnabled(index)" />
            </div>
            <div class="suggestion-main">
              <div class="suggestion-title">
                {{ item.title }}
                <span class="suggestion-template-badge">{{ item.template }}</span>
              </div>
              <div class="suggestion-desc">{{ item.description }}</div>
            </div>
            <div class="suggestion-actions">
              <el-button size="small" text @click.stop="onEdit(index)">编辑</el-button>
              <el-button size="small" text type="danger" @click.stop="onDelete(index)">删除</el-button>
            </div>
          </div>
          <div class="suggestion-meta">
            <span class="meta-tag repo">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 19c-5 1.5-5-2.5-7-3m14 6v-3.87a3.37 3.37 0 0 0-.94-2.61c3.14-.35 6.44-1.54 6.44-7A5.44 5.44 0 0 0 20 4.77 5.07 5.07 0 0 0 19.91 1S18.73.65 16 2.48a13.38 13.38 0 0 0-7 0C6.27.65 5.09 1 5.09 1A5.07 5.07 0 0 0 5 4.77a5.44 5.44 0 0 0-1.5 3.78c0 5.42 3.3 6.61 6.44 7A3.37 3.37 0 0 0 9 18.13V22"></path>
              </svg>
              {{ item.repo }}
            </span>
            <span class="meta-tag dep" v-if="item.dependsOn && item.dependsOn.length">
              依赖: {{ item.dependsOn.join(', ') }}
            </span>
            <span class="meta-tag dep-none" v-else>
              依赖: 无
            </span>
          </div>
        </div>
      </div>

      <div class="split-footer">
        <el-button size="small" plain @click="onAddTask">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          添加任务
        </el-button>
        <div class="split-footer-actions">
          <el-button size="small" @click="onDismiss">取消</el-button>
          <el-button size="small" type="primary" @click="onConfirm">确认创建</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  suggestion: { type: Object, default: null },
  taskId: { type: [String, Number], default: null }
})

const emit = defineEmits(['update', 'confirm', 'dismiss'])

const expanded = ref(true)

const suggestions = computed(() => {
  return props.suggestion?.suggestions ?? []
})

const enabledIndices = ref(new Set())

// Initialize enabledIndices when suggestion changes
import { watch } from 'vue'
watch(() => props.suggestion, (val) => {
  if (val?.suggestions) {
    enabledIndices.value = new Set(val.suggestions.map((_, i) => i))
  } else {
    enabledIndices.value = new Set()
  }
}, { immediate: true })

function toggleEnabled(index) {
  const next = new Set(enabledIndices.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  enabledIndices.value = next
  emitUpdate()
}

function emitUpdate() {
  if (!props.suggestion) return
  const updated = suggestions.value.map((item, i) => ({
    ...item,
    enabled: enabledIndices.value.has(i)
  }))
  emit('update', updated)
}

function onEdit(index) {
  // TODO: open edit dialog
}

function onDelete(index) {
  if (!props.suggestion) return
  const updated = suggestions.value.filter((_, i) => i !== index)
  emit('update', updated)
}

function onAddTask() {
  // TODO: open add task dialog
}

function onConfirm() {
  emit('confirm')
}

function onDismiss() {
  emit('dismiss')
}
</script>

<style scoped>
.ai-split-card {
  margin: 0;
  border: none;
  border-bottom: 1px solid var(--border-color);
  border-radius: 0;
  background: var(--bg-primary);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  max-height: 320px;
  transition: max-height 0.2s ease;
}

.ai-split-card.collapsed {
  max-height: none;
}

.split-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
  background: linear-gradient(135deg, var(--teal-accent-weak), var(--teal-accent-mid));
  position: relative;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.split-card-header:hover {
  background: linear-gradient(135deg, var(--teal-accent-mid), var(--teal-accent-strong));
}

.ai-split-card.collapsed .split-card-header {
  border-bottom: none;
}

.split-card-header::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--accent-color);
}

.split-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.split-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.split-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: transform 0.2s ease;
}

.split-toggle.open {
  transform: rotate(180deg);
}

.split-card-body {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  max-height: 280px;
}

.split-ai-icon {
  width: 16px;
  height: 16px;
  color: var(--accent-color);
  flex-shrink: 0;
}

.split-card-header h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.split-subtitle {
  font-size: 11px;
  color: var(--text-secondary);
}

.split-task-count {
  font-size: 11px;
  color: var(--accent-color);
  background: var(--bg-primary);
  border: 1px solid var(--teal-border-soft);
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 600;
}

.split-suggestions-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  min-height: 0;
}

.split-suggestion-item {
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  margin-bottom: 6px;
  background: var(--bg-secondary);
  transition: all 0.15s;
}

.split-suggestion-item:hover {
  border-color: var(--teal-border-soft);
  background: var(--bg-primary);
  box-shadow: var(--shadow-sm);
}

.split-suggestion-item:last-child {
  margin-bottom: 0;
}

.suggestion-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 8px;
}

.suggestion-check input {
  margin: 2px 0 0;
  accent-color: var(--accent-color);
  width: 14px;
  height: 14px;
  cursor: pointer;
}

.suggestion-main {
  flex: 1;
  min-width: 0;
}

.suggestion-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
}

.suggestion-template-badge {
  font-size: 10px;
  color: var(--accent-color);
  background: var(--accent-color-soft);
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.suggestion-desc {
  font-size: 11px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.suggestion-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.suggestion-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-left: 24px;
}

.meta-tag {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
}

.meta-tag.repo {
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
}

.meta-tag.dep {
  color: var(--warning-strong);
  background: var(--warning-soft);
  font-family: inherit;
}

.meta-tag.dep-none {
  color: var(--done-strong);
  background: var(--done-soft);
  font-family: inherit;
}

.split-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.split-footer-actions {
  display: flex;
  gap: 6px;
}
</style>
