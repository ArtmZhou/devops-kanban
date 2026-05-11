import { defineStore } from 'pinia'
import { ref } from 'vue'
import { splitSuggestionsApi } from '../api/splitSuggestions.js'

function debounce(fn, wait) {
  let timer
  return (...args) => {
    clearTimeout(timer)
    timer = setTimeout(() => fn(...args), wait)
  }
}

export const useSplitSuggestionsStore = defineStore('splitSuggestions', () => {
  const pendingByTask = ref(new Map())
  const loading = ref(false)

  async function load(taskId) {
    loading.value = true
    try {
      const resp = await splitSuggestionsApi.listByTask(taskId)
      if (resp?.success) {
        const pending = resp.data.find(s => s.status === 'PENDING')
        if (pending) pendingByTask.value.set(taskId, pending)
        else pendingByTask.value.delete(taskId)
      }
    } finally {
      loading.value = false
    }
  }

  const debouncedPatch = debounce(async (id, suggestions) => {
    await splitSuggestionsApi.update(id, suggestions)
  }, 500)

  function updateSuggestions(taskId, suggestions) {
    const record = pendingByTask.value.get(taskId)
    if (!record) return
    record.suggestions = suggestions
    pendingByTask.value.set(taskId, { ...record })
    debouncedPatch(record.id, suggestions)
  }

  async function doConfirm(taskId) {
    const record = pendingByTask.value.get(taskId)
    if (!record) return null
    const resp = await splitSuggestionsApi.confirm(record.id)
    if (resp?.success) pendingByTask.value.delete(taskId)
    return resp
  }

  async function doDismiss(taskId) {
    const record = pendingByTask.value.get(taskId)
    if (!record) return null
    const resp = await splitSuggestionsApi.dismiss(record.id)
    if (resp?.success) pendingByTask.value.delete(taskId)
    return resp
  }

  return { pendingByTask, loading, load, updateSuggestions, doConfirm, doDismiss }
})
