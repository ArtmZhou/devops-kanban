<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    :top="top"
    :close-on-click-modal="closeOnClickModal"
    :append-to-body="appendToBody"
    :class="['base-dialog', customClass]"
    v-bind="$attrs"
    @update:model-value="$emit('update:modelValue', $event)"
    @close="$emit('close')"
    @opened="$emit('opened')"
  >
    <template #default>
      <div :class="['base-dialog__body', { 'base-dialog__body--no-padding': !bodyPadding }]">
        <slot />
      </div>
    </template>
    <template #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>

<script setup>
defineOptions({
  inheritAttrs: false
})

defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  width: {
    type: String,
    default: '500px'
  },
  top: {
    type: String,
    default: '15vh'
  },
  closeOnClickModal: {
    type: Boolean,
    default: false
  },
  bodyPadding: {
    type: Boolean,
    default: true
  },
  appendToBody: {
    type: Boolean,
    default: false
  },
  customClass: {
    type: String,
    default: ''
  }
})

defineEmits(['update:modelValue', 'close', 'opened'])
</script>

<style scoped>
.base-dialog :deep(.el-dialog) {
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  border: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.base-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-color);
  background: linear-gradient(135deg, #f8fafc 0%, #ffffff 100%);
  flex-shrink: 0;
}

.base-dialog :deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 10px;
}

.base-dialog :deep(.el-dialog__title)::before {
  content: '';
  display: block;
  width: 6px;
  height: 20px;
  background: var(--accent-color);
  border-radius: 3px;
}

.base-dialog :deep(.el-dialog__headerbtn) {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.base-dialog :deep(.el-dialog__headerbtn:hover) {
  background: var(--bg-secondary);
}

.base-dialog :deep(.el-dialog__body) {
  background: var(--bg-primary);
}

.base-dialog__body {
  padding: 16px 20px;
}

.base-dialog__body--no-padding {
  padding: 0;
}

.base-dialog :deep(.el-dialog__footer) {
  padding: 12px 20px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
