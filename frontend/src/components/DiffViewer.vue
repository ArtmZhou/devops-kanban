<template>
  <el-dialog
    :model-value="true"
    title="Code Changes"
    width="80%"
    top="5vh"
    :close-on-click-modal="false"
    @close="$emit('close')"
  >
    <template #header>
      <div class="dialog-header">
        <span>Code Changes</span>
        <div class="branch-info">
          <el-tag type="info">{{ sourceRef }}</el-tag>
          <el-icon><Right /></el-icon>
          <el-tag type="success">{{ targetRef }}</el-tag>
        </div>
      </div>
    </template>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>

    <el-empty v-else-if="!diffContent" description="No changes to display" />

    <div v-else class="diff-container">
      <div class="diff-stats">
        <el-tag type="success">
          <el-icon><Plus /></el-icon>
          {{ additions }} additions
        </el-tag>
        <el-tag type="danger">
          <el-icon><Minus /></el-icon>
          {{ deletions }} deletions
        </el-tag>
        <el-tag type="info">
          {{ changedFiles }} files changed
        </el-tag>
      </div>

      <el-scrollbar height="60vh">
        <pre class="diff-content">{{ diffContent }}</pre>
      </el-scrollbar>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="$emit('close')">Close</el-button>
        <el-button type="warning" @click="$emit('reject')">
          <el-icon><Close /></el-icon>
          Reject Changes
        </el-button>
        <el-button type="success" @click="$emit('accept')">
          <el-icon><Check /></el-icon>
          Accept Changes
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Right, Plus, Minus, Check, Close } from '@element-plus/icons-vue'
import axios from 'axios'

const props = defineProps({
  projectId: {
    type: Number,
    required: true
  },
  sourceRef: {
    type: String,
    required: true
  },
  targetRef: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['close', 'accept', 'reject'])

const loading = ref(true)
const diffContent = ref('')
const changedFiles = ref(0)

const additions = computed(() => {
  if (!diffContent.value) return 0
  const matches = diffContent.value.match(/^\+/gm)
  return matches ? matches.length : 0
})

const deletions = computed(() => {
  if (!diffContent.value) return 0
  const matches = diffContent.value.match(/^-/gm)
  return matches ? matches.length : 0
})

onMounted(async () => {
  await loadDiff()
})

const loadDiff = async () => {
  loading.value = true
  try {
    const response = await axios.get(`/api/git/diff`, {
      params: {
        projectId: props.projectId,
        source: props.sourceRef,
        target: props.targetRef
      }
    })
    if (response.data.success) {
      diffContent.value = response.data.data.content || ''
      changedFiles.value = response.data.data.filesChanged || 0
    }
  } catch (e) {
    console.error('Failed to load diff:', e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.branch-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-container {
  padding: 20px;
}

.diff-container {
  background: var(--el-bg-color-page);
  border-radius: 8px;
  overflow: hidden;
}

.diff-stats {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.diff-content {
  margin: 0;
  padding: 16px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--el-text-color-primary);
}

.diff-content :deep() {
  /* Style diff lines */
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
