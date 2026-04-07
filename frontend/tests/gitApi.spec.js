import { describe, expect, it } from 'vitest'

import api from '../src/api/index.js'
import * as gitApi from '../src/api/git.js'

function captureRequest() {
  const seen = []
  const interceptor = api.interceptors.request.use((config) => {
    seen.push({ url: config.url, method: config.method, data: config.data, params: config.params })
    return Promise.reject(new Error('stop'))
  })
  return { seen, cleanup: () => api.interceptors.request.eject(interceptor) }
}

describe('git API', () => {
  it('commit sends POST with message and params', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(gitApi.commit(1, 5, { message: 'fix: bug', addAll: true })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({
      url: '/git/worktrees/5/commit',
      method: 'post',
      data: { message: 'fix: bug', addAll: true, files: [], authorName: undefined, authorEmail: undefined },
      params: { projectId: 1 }
    })
  })

  it('commit with files array', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(gitApi.commit(1, 5, { message: 'm', files: ['a.js', 'b.js'] })).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].data.files).toEqual(['a.js', 'b.js'])
  })

  it('getUncommittedChanges sends GET with params', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(gitApi.getUncommittedChanges(1, 5)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({
      url: '/git/worktrees/5/changes',
      method: 'get',
      data: undefined,
      params: { projectId: 1 }
    })
  })

  it('getDiff sends GET with params', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(gitApi.getDiff(1, 5)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({
      url: '/git/worktrees/5/diff',
      method: 'get',
      data: undefined,
      params: { projectId: 1 }
    })
  })

  it('listBranches sends GET with projectId', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(gitApi.listBranches(42)).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0]).toEqual({
      url: '/git/branches',
      method: 'get',
      data: undefined,
      params: { projectId: 42 }
    })
  })

  it('mergeBranch encodes branch names', async () => {
    const { seen, cleanup } = captureRequest()
    try {
      await expect(gitApi.mergeBranch(1, 'feature/test', 'main')).rejects.toThrow('stop')
    } finally { cleanup() }
    expect(seen[0].url).toBe('/git/branches/feature%2Ftest/merge/main')
    expect(seen[0].method).toBe('post')
    expect(seen[0].params).toEqual({ projectId: 1 })
  })
})
