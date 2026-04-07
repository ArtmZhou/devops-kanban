import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTaskSourceStore } from '../src/stores/taskSourceStore'
import * as taskSourceApi from '../src/api/taskSource'

vi.mock('../src/api/taskSource', async () => {
  const actual = await vi.importActual('../src/api/taskSource')
  return {
    ...actual,
    getAvailableTaskSourceTypes: vi.fn()
  }
})

describe('taskSourceStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('fetchTaskSources', () => {
    it('passes projectId to API and sets items', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({
        success: true,
        data: [{ id: 1, name: 'GitHub', type: 'GITHUB', project_id: 42 }]
      })

      const store = useTaskSourceStore()
      await store.fetchTaskSources(42)

      expect(taskSourceApi.getTaskSources).toHaveBeenCalledWith(42)
      expect(store.taskSources).toHaveLength(1)
    })

    it('sets currentProjectId', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [] })

      const store = useTaskSourceStore()
      await store.fetchTaskSources(42)

      expect(store.currentProjectId).toBe(42)
    })

    it('sets error on failure', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({
        success: false,
        message: 'Server error'
      })

      const store = useTaskSourceStore()
      await expect(store.fetchTaskSources(1)).rejects.toThrow('Server error')
      expect(store.error).toBe('Server error')
    })
  })

  describe('loadAvailableTypes', () => {
    it('loads available task source types from the backend', async () => {
      taskSourceApi.getAvailableTaskSourceTypes.mockResolvedValue({
        success: true,
        data: [
          { key: 'REQUIREMENT', name: '需求池', description: 'desc', config: {} },
          { key: 'TICKET', name: '工单系统', description: 'desc', config: {} }
        ]
      })

      const store = useTaskSourceStore()
      const result = await store.loadAvailableTypes()

      expect(taskSourceApi.getAvailableTaskSourceTypes).toHaveBeenCalledTimes(1)
      expect(result).toEqual([
        { key: 'REQUIREMENT', name: '需求池', description: 'desc', config: {} },
        { key: 'TICKET', name: '工单系统', description: 'desc', config: {} }
      ])
      expect(store.availableTypes).toEqual(result)
    })

    it('normalizes task source types returned as an object map', async () => {
      taskSourceApi.getAvailableTaskSourceTypes.mockResolvedValue({
        success: true,
        data: {
          REQUIREMENT: { name: '需求池', description: 'desc', config: {} },
          TICKET: { key: 'CUSTOM_TICKET', name: '工单系统', description: 'desc', config: {} }
        }
      })

      const store = useTaskSourceStore()
      const result = await store.loadAvailableTypes()

      expect(result).toEqual([
        { key: 'REQUIREMENT', name: '需求池', description: 'desc', config: {} },
        { key: 'CUSTOM_TICKET', name: '工单系统', description: 'desc', config: {} }
      ])
      expect(store.availableTypes).toEqual(result)
    })

    it('returns empty array for null data', async () => {
      taskSourceApi.getAvailableTaskSourceTypes.mockResolvedValue({
        success: true,
        data: null
      })

      const store = useTaskSourceStore()
      const result = await store.loadAvailableTypes()

      expect(result).toEqual([])
    })
  })

  describe('createTaskSource', () => {
    it('creates source and refreshes list', async () => {
      taskSourceApi.createTaskSource = vi.fn().mockResolvedValue({ success: true, data: { id: 1 } })
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [{ id: 1 }] })

      const store = useTaskSourceStore()
      store.currentProjectId = 42
      await store.createTaskSource({ name: 'New Source' })

      expect(taskSourceApi.createTaskSource).toHaveBeenCalledWith({ name: 'New Source' })
      expect(taskSourceApi.getTaskSources).toHaveBeenCalledWith(42)
    })
  })

  describe('updateTaskSource', () => {
    it('updates source and refreshes list', async () => {
      taskSourceApi.updateTaskSource = vi.fn().mockResolvedValue({ success: true, data: { id: 1 } })
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [{ id: 1 }] })

      const store = useTaskSourceStore()
      store.currentProjectId = 42
      await store.updateTaskSource(1, { name: 'Updated' })

      expect(taskSourceApi.updateTaskSource).toHaveBeenCalledWith(1, { name: 'Updated' })
    })
  })

  describe('deleteTaskSource', () => {
    it('deletes source and refreshes list', async () => {
      taskSourceApi.deleteTaskSource = vi.fn().mockResolvedValue({ success: true })
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [] })

      const store = useTaskSourceStore()
      store.currentProjectId = 42
      await store.deleteTaskSource(1)

      expect(taskSourceApi.deleteTaskSource).toHaveBeenCalledWith(1)
    })
  })

  describe('syncTaskSource', () => {
    it('calls sync API and manages syncing state', async () => {
      taskSourceApi.syncTaskSource = vi.fn().mockResolvedValue({ success: true, data: {} })

      const store = useTaskSourceStore()
      const promise = store.syncTaskSource(1)
      expect(store.syncing).toBe(true)
      await promise
      expect(store.syncing).toBe(false)
      expect(taskSourceApi.syncTaskSource).toHaveBeenCalledWith(1)
    })
  })

  describe('testTaskSource', () => {
    it('calls test API and manages testing state', async () => {
      taskSourceApi.testTaskSource = vi.fn().mockResolvedValue({ success: true, data: {} })

      const store = useTaskSourceStore()
      const promise = store.testTaskSource(1)
      expect(store.testing).toBe(true)
      await promise
      expect(store.testing).toBe(false)
    })
  })

  describe('enabledSources', () => {
    it('filters enabled sources', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({
        success: true,
        data: [
          { id: 1, name: 'A', enabled: true },
          { id: 2, name: 'B', enabled: false }
        ]
      })

      const store = useTaskSourceStore()
      await store.fetchTaskSources(1)

      expect(store.enabledSources).toHaveLength(1)
      expect(store.enabledSources[0].id).toBe(1)
    })
  })

  describe('sourcesByType', () => {
    it('groups sources by type', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({
        success: true,
        data: [
          { id: 1, type: 'GITHUB' },
          { id: 2, type: 'JIRA' },
          { id: 3, type: 'GITHUB' }
        ]
      })

      const store = useTaskSourceStore()
      await store.fetchTaskSources(1)

      expect(Object.keys(store.sourcesByType)).toHaveLength(2)
      expect(store.sourcesByType.GITHUB).toHaveLength(2)
      expect(store.sourcesByType.JIRA).toHaveLength(1)
    })

    it('groups sources without type as OTHER', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({
        success: true,
        data: [{ id: 1 }]
      })

      const store = useTaskSourceStore()
      await store.fetchTaskSources(1)

      expect(store.sourcesByType.OTHER).toHaveLength(1)
    })
  })

  describe('preview and sync', () => {
    it('previewSync fetches items and opens dialog', async () => {
      taskSourceApi.previewSync = vi.fn().mockResolvedValue({
        success: true,
        data: [{ external_id: 'ext-1', title: 'Issue 1' }]
      })

      const store = useTaskSourceStore()
      const items = await store.previewSync(1)

      expect(items).toHaveLength(1)
      expect(store.showPreviewDialog).toBe(true)
      expect(store.previewItems).toHaveLength(1)
    })

    it('previewSync closes dialog when no items', async () => {
      taskSourceApi.previewSync = vi.fn().mockResolvedValue({ success: true, data: [] })

      const store = useTaskSourceStore()
      await store.previewSync(1)

      expect(store.showPreviewDialog).toBe(false)
    })

    it('toggleSyncTask toggles selection', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [] })
      const store = useTaskSourceStore()

      store.toggleSyncTask({ external_id: 'ext-1' })
      expect(store.selectedSyncTasks.has('ext-1')).toBe(true)

      store.toggleSyncTask({ external_id: 'ext-1' })
      expect(store.selectedSyncTasks.has('ext-1')).toBe(false)
    })

    it('selectAllSyncTasks selects non-imported tasks', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [] })
      const store = useTaskSourceStore()

      store.syncPreviewTasks = [
        { external_id: 'ext-1', imported: false },
        { external_id: 'ext-2', imported: true },
        { external_id: 'ext-3', imported: false }
      ]

      store.selectAllSyncTasks()
      expect(store.selectedSyncTasks.has('ext-1')).toBe(true)
      expect(store.selectedSyncTasks.has('ext-2')).toBe(false)
      expect(store.selectedSyncTasks.has('ext-3')).toBe(true)
    })

    it('deselectAllSyncTasks clears selection', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [] })
      const store = useTaskSourceStore()

      store.toggleSyncTask({ external_id: 'ext-1' })
      store.deselectAllSyncTasks()
      expect(store.selectedSyncTasks.size).toBe(0)
    })

    it('closePreviewDialog resets preview state', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [] })
      const store = useTaskSourceStore()

      store.showPreviewDialog = true
      store.previewItems = [{ id: 1 }]
      store.syncPreviewTasks = [{ id: 1 }]

      store.closePreviewDialog()

      expect(store.showPreviewDialog).toBe(false)
      expect(store.previewItems).toHaveLength(0)
      expect(store.syncPreviewTasks).toHaveLength(0)
      expect(store.selectedSyncTasks.size).toBe(0)
    })
  })

  describe('importSelectedPreviewTasks', () => {
    it('imports selected tasks grouped by source', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({ success: true, data: [] })
      taskSourceApi.importIssues = vi.fn().mockResolvedValue({ success: true, data: { created: 2 } })

      const store = useTaskSourceStore()
      store.syncPreviewTasks = [
        { external_id: 'ext-1', sourceId: 1, imported: false },
        { external_id: 'ext-2', sourceId: 1, imported: false },
        { external_id: 'ext-3', sourceId: 2, imported: true }
      ]
      store.selectedSyncTasks = new Set(['ext-1', 'ext-2'])

      const result = await store.importSelectedPreviewTasks(42)

      expect(result).toBe(2)
      expect(taskSourceApi.importIssues).toHaveBeenCalledWith(1, expect.objectContaining({
        items: expect.arrayContaining([
          expect.objectContaining({ external_id: 'ext-1' }),
          expect.objectContaining({ external_id: 'ext-2' })
        ]),
        project_id: 42
      }))
    })
  })

  describe('clearTaskSources', () => {
    it('clears items and projectId', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({
        success: true,
        data: [{ id: 1 }]
      })

      const store = useTaskSourceStore()
      await store.fetchTaskSources(42)
      store.clearTaskSources()

      expect(store.taskSources).toHaveLength(0)
      expect(store.currentProjectId).toBeNull()
    })
  })

  describe('clearError', () => {
    it('resets error state', async () => {
      taskSourceApi.getTaskSources = vi.fn().mockResolvedValue({
        success: false,
        message: 'Failed'
      })

      const store = useTaskSourceStore()
      try { await store.fetchTaskSources(1) } catch (e) { /* expected */ }
      expect(store.error).toBeTruthy()

      store.clearError()
      expect(store.error).toBeNull()
    })
  })
})
