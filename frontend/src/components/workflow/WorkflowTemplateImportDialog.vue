<template>
  <BaseDialog :model-value="modelValue" :title="$t('workflowTemplate.importTitle')" width="680px" @close="handleClose">
    <div class="import-dialog">
      <!-- Step 1: File Upload -->
      <div v-if="step === 'upload'" class="import-step">
        <div class="upload-zone" @click="triggerFileInput" @drop.prevent="handleDrop" @dragover.prevent>
          <input
            ref="fileInput"
            type="file"
            accept=".json"
            style="display: none"
            @change="handleFileSelect"
          />
          <el-icon :size="32" class="upload-icon"><Upload /></el-icon>
          <p class="upload-text">{{ $t('workflowTemplate.importDropHint') }}</p>
          <el-button type="primary" plain>{{ $t('workflowTemplate.importSelectFile') }}</el-button>
        </div>
      </div>

      <!-- Step 2: Preview & Configure -->
      <div v-else-if="step === 'preview'" class="import-step">
        <div class="preview-summary">
          {{ $t('workflowTemplate.importPreviewSummary', { count: previewData.templates.length }) }}
        </div>

        <!-- Template list -->
        <div class="preview-table">
          <div v-for="tpl in previewData.templates" :key="tpl.template_id" class="preview-template-row">
            <div class="preview-template-info">
              <span class="preview-template-name">{{ tpl.name }}</span>
              <span class="preview-template-id">{{ tpl.template_id }}</span>
            </div>
            <div class="preview-template-meta">
              <span class="preview-step-count">{{ $t('workflowTemplate.stepCount', { count: tpl.steps.length }) }}</span>
              <el-tag v-if="isExisting(tpl.template_id)" type="warning" size="small">
                {{ $t('workflowTemplate.importConflict') }}
              </el-tag>
            </div>
          </div>
        </div>

        <!-- Conflict strategy -->
        <div v-if="hasConflicts" class="import-section">
          <div class="import-section-title">{{ $t('workflowTemplate.importConflictStrategy') }}</div>
          <el-radio-group v-model="strategy">
            <el-radio value="skip">{{ $t('workflowTemplate.importStrategySkip') }}</el-radio>
            <el-radio value="overwrite">{{ $t('workflowTemplate.importStrategyOverwrite') }}</el-radio>
            <el-radio value="copy">{{ $t('workflowTemplate.importStrategyCopy') }}</el-radio>
          </el-radio-group>
        </div>

        <!-- Agent mapping -->
        <div v-if="previewData.unmatchedAgentNames.length > 0" class="import-section">
          <div class="import-section-title">{{ $t('workflowTemplate.importAgentMapping') }}</div>
          <div class="import-section-hint">{{ $t('workflowTemplate.importAgentMappingHint') }}</div>
          <div v-for="agentName in previewData.unmatchedAgentNames" :key="agentName" class="agent-mapping-row">
            <span class="agent-mapping-source">{{ agentName }}</span>
            <el-icon><Right /></el-icon>
            <el-select v-model="agentMappings[agentName]" :placeholder="$t('workflowTemplate.importSelectAgent')" clearable style="flex: 1">
              <el-option
                v-for="agent in agents"
                :key="agent.id"
                :label="agent.name"
                :value="agent.id"
              />
            </el-select>
          </div>
        </div>
      </div>

      <!-- Step 3: Result -->
      <div v-else-if="step === 'result'" class="import-step">
        <div class="import-result-summary">
          <el-tag type="success" size="large">
            {{ $t('workflowTemplate.importResultSuccess', { count: result.imported.length }) }}
          </el-tag>
          <el-tag v-if="result.skipped.length > 0" type="info" size="large" style="margin-left: 8px">
            {{ $t('workflowTemplate.importResultSkipped', { count: result.skipped.length }) }}
          </el-tag>
        </div>
        <div v-if="result.imported.length > 0" class="import-result-list">
          <div v-for="tpl in result.imported" :key="tpl.template_id" class="import-result-item">
            <el-icon color="#25C6C9"><Check /></el-icon>
            <span>{{ tpl.name }}</span>
            <span class="import-result-id">{{ tpl.template_id }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">{{ $t('common.cancel') }}</el-button>
        <el-button v-if="step === 'preview'" @click="resetToUpload">{{ $t('workflowTemplate.importBack') }}</el-button>
        <el-button
          v-if="step === 'preview'"
          type="primary"
          :disabled="importing || hasUnmappedAgents"
          @click="handleConfirmImport"
        >
          {{ importing ? $t('common.saving') : $t('workflowTemplate.importConfirm') }}
        </el-button>
        <el-button v-if="step === 'result'" type="primary" @click="handleClose">
          {{ $t('common.confirm') }}
        </el-button>
      </div>
    </template>
  </BaseDialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Check, Right, Upload } from '@element-plus/icons-vue'
import BaseDialog from '../BaseDialog.vue'
import { getAgents } from '../../api/agent.js'
import { previewImportWorkflowTemplates, confirmImportWorkflowTemplates } from '../../api/workflowTemplate.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  agents: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'imported'])

const { t } = useI18n()

const step = ref('upload')
const fileInput = ref(null)
const previewData = ref({ templates: [], existingTemplateIds: [], unmatchedAgentNames: [] })
const strategy = ref('copy')
const agentMappings = ref({})
const importing = ref(false)
const result = ref({ imported: [], skipped: [] })

const hasConflicts = computed(() => previewData.value.existingTemplateIds.length > 0)
const hasUnmappedAgents = computed(() => {
  return previewData.value.unmatchedAgentNames.some(name => agentMappings.value[name] === undefined || agentMappings.value[name] === null)
})

const isExisting = (templateId) => previewData.value.existingTemplateIds.includes(templateId)

const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleFileSelect = (event) => {
  const file = event.target.files?.[0]
  if (file) parseFile(file)
}

const handleDrop = (event) => {
  const file = event.dataTransfer?.files?.[0]
  if (file) parseFile(file)
}

const parseFile = async (file) => {
  try {
    const text = await file.text()
    const data = JSON.parse(text)
    if (!data.templates || !Array.isArray(data.templates)) {
      ElMessage.error(t('workflowTemplate.importInvalidFile'))
      return
    }
    await doPreview(data)
  } catch (e) {
    ElMessage.error(t('workflowTemplate.importInvalidFile'))
  }
}

const doPreview = async (exportData) => {
  try {
    const response = await previewImportWorkflowTemplates(exportData)
    if (!response?.success) {
      ElMessage.error(response?.message || t('workflowTemplate.importPreviewFailed'))
      return
    }
    previewData.value = response.data
    agentMappings.value = {}
    step.value = 'preview'
  } catch (error) {
    ElMessage.error(error?.message || t('workflowTemplate.importPreviewFailed'))
  }
}

const handleConfirmImport = async () => {
  importing.value = true
  try {
    const response = await confirmImportWorkflowTemplates({
      templates: previewData.value.templates,
      strategy: strategy.value,
      agentMappings: { ...agentMappings.value }
    })
    if (!response?.success) {
      ElMessage.error(response?.message || t('workflowTemplate.importFailed'))
      return
    }
    result.value = response.data
    step.value = 'result'
    emit('imported', result.value)
  } catch (error) {
    ElMessage.error(error?.message || t('workflowTemplate.importFailed'))
  } finally {
    importing.value = false
  }
}

const resetToUpload = () => {
  step.value = 'upload'
  previewData.value = { templates: [], existingTemplateIds: [], unmatchedAgentNames: [] }
  agentMappings.value = {}
  if (fileInput.value) fileInput.value.value = ''
}

const handleClose = () => {
  step.value = 'upload'
  previewData.value = { templates: [], existingTemplateIds: [], unmatchedAgentNames: [] }
  agentMappings.value = {}
  result.value = { imported: [], skipped: [] }
  importing.value = false
  emit('update:modelValue', false)
}
</script>

<style scoped>
.import-dialog {
  min-height: 120px;
}

.import-step {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  border: 2px dashed var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.upload-zone:hover {
  border-color: var(--accent-color);
  background: rgba(37, 198, 201, 0.03);
}

.upload-icon {
  color: var(--text-secondary);
}

.upload-text {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.preview-summary {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  padding: 8px 0;
}

.preview-table {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
}

.preview-template-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-primary);
}

.preview-template-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.preview-template-name {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
}

.preview-template-id {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.preview-template-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-step-count {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.import-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.import-section-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.import-section-hint {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
}

.agent-mapping-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}

.agent-mapping-source {
  min-width: 120px;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  font-weight: 500;
}

.import-result-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.import-result-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.import-result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
}

.import-result-id {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  margin-left: auto;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
