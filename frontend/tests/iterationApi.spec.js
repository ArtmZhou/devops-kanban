import { describe, expect, it } from 'vitest'

import api from '../src/api/index.js'
import * as iterationApi from '../src/api/iteration.js'

function captureRequest() {
  const seen = []
  const interceptor = api.interceptors.request.use((config) => {
    seen.push({ url: config.url, method: config.method, data: config.data, params: config.params })
    return Promise.reject(new Error('stop'))
  })
  return { seen, cleanup: () => api.interceptors.request.eject(interceptor) }
}

describe('iteration API', () => {
  it('getIterations sends GET with project_id param', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.getIterations(42)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({ url: '/iterations', method: 'get', data: undefined, params: { project_id: 42 } })
  })

  it('getIteration sends GET by id', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.getIteration(1)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/iterations/1')
    expect(seen[0].method).toBe('get')
  })

  it('getIterationWithStats sends GET by id', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.getIterationWithStats(1)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/iterations/1')
  })

  it('getIterationTasks sends GET by id', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.getIterationTasks(1)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/iterations/1/tasks')
  })

  it('createIteration normalizes project_id to Number', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.createIteration({ name: 'Sprint 1', project_id: '42' })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].data.project_id).toBe(42)
    expect(seen[0].method).toBe('post')
    expect(seen[0].url).toBe('/iterations')
  })

  it('updateIteration normalizes project_id to Number', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.updateIteration(1, { name: 'Updated', project_id: '5' })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].data.project_id).toBe(5)
    expect(seen[0].url).toBe('/iterations/1')
    expect(seen[0].method).toBe('put')
  })

  it('updateIterationStatus sends PATCH with status', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.updateIterationStatus(1, 'ACTIVE')).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({ url: '/iterations/1/status', method: 'patch', data: { status: 'ACTIVE' }, params: undefined })
  })

  it('deleteIteration sends DELETE with delete_tasks param', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.deleteIteration(1, { deleteTasks: true })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/iterations/1')
    expect(seen[0].method).toBe('delete')
    expect(seen[0].params).toEqual({ delete_tasks: true })
  })

  it('deleteIteration defaults delete_tasks to false', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(iterationApi.deleteIteration(1)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].params).toEqual({ delete_tasks: false })
  })
})
