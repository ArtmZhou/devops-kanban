<template>
  <div class="conclusion-card">
    <div class="conclusion-header">
      <div class="header-left">
        <span class="header-icon">📋</span>
        <h4 class="header-title">综合结论</h4>
      </div>
      <el-tag type="success" size="small">
        <el-icon><Check /></el-icon>
        讨论完成
      </el-tag>
    </div>

    <div class="conclusion-body">
      <div class="markdown-content" v-html="renderedConclusion"></div>
    </div>

    <div class="conclusion-footer">
      <div class="action-buttons">
        <el-button size="small" @click="handleCopy">
          <el-icon><DocumentCopy /></el-icon>
          复制结论
        </el-button>
        <el-button size="small" type="primary" @click="handleAddToTask">
          <el-icon><DocumentAdd /></el-icon>
          添加到任务
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, DocumentCopy, DocumentAdd } from '@element-plus/icons-vue'
import { marked } from 'marked'

const props = defineProps({
  conclusion: {
    type: String,
    required: true
  },
  topic: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['add-to-task'])

const renderedConclusion = computed(() => {
  return marked(props.conclusion || '')
})

const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.conclusion)
    ElMessage.success('结论已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败')
  }
}

const handleAddToTask = () => {
  emit('add-to-task', {
    topic: props.topic,
    conclusion: props.conclusion
  })
}
</script>

<style scoped>
.conclusion-card {
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  border: 2px solid #86efac;
  border-radius: 12px;
  padding: 20px;
  margin-top: 16px;
  animation: slideIn 0.5s ease;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.conclusion-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid rgba(134, 239, 172, 0.5);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon {
  font-size: 24px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #166534;
  margin: 0;
}

.conclusion-body {
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.markdown-content :deep(*) {
  margin: 0;
}

.markdown-content :deep(p) {
  margin-bottom: 12px;
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(strong) {
  color: #166534;
  font-weight: 600;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 24px;
  margin: 12px 0;
}

.markdown-content :deep(li) {
  margin-bottom: 6px;
  line-height: 1.6;
}

.markdown-content :deep(code) {
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #059669;
}

.markdown-content :deep(pre) {
  background: #1e293b;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #e2e8f0;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #86efac;
  padding: 10px;
  text-align: left;
}

.markdown-content :deep(th) {
  background: rgba(134, 239, 172, 0.3);
  font-weight: 600;
  color: #166534;
}

.markdown-content :deep(tr:nth-child(even)) {
  background: rgba(134, 239, 172, 0.1);
}

.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  color: #166534;
  font-size: 14px;
  font-weight: 600;
  margin: 16px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(134, 239, 172, 0.5);
}

.markdown-content :deep(h3:first-child),
.markdown-content :deep(h4:first-child) {
  margin-top: 0;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid #166534;
  padding-left: 16px;
  margin: 12px 0;
  color: #374151;
  background: rgba(134, 239, 172, 0.2);
  padding: 12px 16px;
  border-radius: 0 8px 8px 0;
}

.conclusion-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

/* Scrollbar styling */
.conclusion-body::-webkit-scrollbar {
  width: 6px;
}

.conclusion-body::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.05);
  border-radius: 3px;
}

.conclusion-body::-webkit-scrollbar-thumb {
  background: #86efac;
  border-radius: 3px;
}

.conclusion-body::-webkit-scrollbar-thumb:hover {
  background: #4ade80;
}
</style>
