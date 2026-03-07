<template>
  <div class="github-config">
    <div class="form-group">
      <label>{{ $t('taskSource.github.repo') }}</label>
      <input
        v-model="localConfig.repo"
        type="text"
        :placeholder="$t('taskSource.github.repoHint')"
        @input="updateConfig"
      />
      <span class="hint">{{ $t('taskSource.github.repoHint') }}</span>
    </div>

    <div class="form-group">
      <label>{{ $t('taskSource.github.token') }}</label>
      <input
        v-model="localConfig.token"
        type="password"
        :placeholder="$t('taskSource.github.tokenPlaceholder')"
        @input="updateConfig"
      />
      <span class="hint">{{ $t('taskSource.github.tokenHint') }}</span>
    </div>

    <div class="form-group">
      <label>{{ $t('taskSource.github.state') }}</label>
      <select v-model="localConfig.state" @change="updateConfig">
        <option value="open">{{ $t('taskSource.github.stateOpen') }}</option>
        <option value="closed">{{ $t('taskSource.github.stateClosed') }}</option>
        <option value="all">{{ $t('taskSource.github.stateAll') }}</option>
      </select>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: '{}'
  }
})

const emit = defineEmits(['update:modelValue'])

const localConfig = ref({
  repo: '',
  token: '',
  state: 'open'
})

// Parse config from modelValue
const parseConfig = (configStr) => {
  try {
    const parsed = JSON.parse(configStr || '{}')
    return {
      repo: parsed.repo || '',
      token: parsed.token || '',
      state: parsed.state || 'open'
    }
  } catch (e) {
    return { repo: '', token: '', state: 'open' }
  }
}

// Watch for external changes (immediate: true handles initial load)
watch(() => props.modelValue, (newVal) => {
  localConfig.value = parseConfig(newVal)
}, { immediate: true })

// Emit changes
const updateConfig = () => {
  emit('update:modelValue', JSON.stringify(localConfig.value))
}
</script>

<style scoped>
.github-config {
  width: 100%;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
  font-size: 0.875rem;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #cbd5e0;
  border-radius: 4px;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #4299e1;
  box-shadow: 0 0 0 3px rgba(66, 153, 225, 0.15);
}

.hint {
  display: block;
  font-size: 0.75rem;
  color: #718096;
  margin-top: 0.25rem;
}
</style>
