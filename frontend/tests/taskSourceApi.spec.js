import { describe, expect, it } from 'vitest'

import api from '../src/api/index.js'
import * as taskSourceApi from '../src/api/taskSource.js'

function captureRequest() {
  const seen = []
  const interceptor = api.interceptors.request.use((config) => {
    seen.push({ url: config.url, method: config.method, data: config.data, params: config.params })
    return Promise.reject(new Error('stop'))
  })
  return { seen, cleanup: () => api.interceptors.request.eject(interceptor) }
}

describe('taskSource API', () => {
  it('getTaskSources sends GET with project_id param', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.getTaskSources(42)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({ url: '/task-sources', method: 'get', data: undefined, params: { project_id: 42 } })
  })

  it('getTaskSource sends GET by id', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.getTaskSource(1)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/task-sources/1')
    expect(seen[0].method).toBe('get')
  })

  it('getAvailableTaskSourceTypes sends GET', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.getAvailableTaskSourceTypes()).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({ url: '/task-sources/types/available', method: 'get', data: undefined, params: undefined })
  })

  it('createTaskSource sends POST with data', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.createTaskSource({ name: 'GitHub' })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({ url: '/task-sources', method: 'post', data: { name: 'GitHub' }, params: undefined })
  })

  it('updateTaskSource sends PUT with data', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.updateTaskSource(1, { name: 'Updated' })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/task-sources/1')
    expect(seen[0].method).toBe('put')
    expect(seen[0].data).toEqual({ name: 'Updated' })
  })

  it('deleteTaskSource sends DELETE', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.deleteTaskSource(1)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({ url: '/task-sources/1', method: 'delete', data: undefined, params: undefined })
  })

  it('syncTaskSource sends POST', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.syncTaskSource(3)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/task-sources/3/sync')
    expect(seen[0].method).toBe('post')
  })

  it('previewSync with empty params sends null', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.previewSync(3, {})).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].data).toBeNull()
  })

  it('previewSync with params sends params as body', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.previewSync(3, { status: 'open' })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].data).toEqual({ status: 'open' })
  })

  it('importIssues sends POST with items', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.importIssues(3, { items: [1, 2] })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/task-sources/3/sync/import')
    expect(seen[0].data).toEqual({ items: [1, 2] })
  })

  it('testTaskSource sends GET', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(taskSourceApi.testTaskSource(3)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({ url: '/task-sources/3/test', method: 'get', data: undefined, params: undefined })
  })
})
