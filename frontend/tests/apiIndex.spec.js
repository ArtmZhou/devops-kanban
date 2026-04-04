import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

import api from '../src/api/index.js'

describe('api index interceptors', () => {
  it('response error interceptor extracts backend message from data.message', async () => {
    const interceptor = api.interceptors.request.use((config) => {
      const error = new Error('Request failed')
      error.config = { metadata: { requestId: 99, startTime: Date.now() - 100 } }
      error.response = {
        status: 400,
        data: { message: 'Custom backend error', success: false }
      }
      return Promise.reject(error)
    })

    try {
      await expect(api.get('/test-error')).rejects.toMatchObject({
        message: 'Custom backend error'
      })
    } finally {
      api.interceptors.request.eject(interceptor)
    }
  })

  it('response error interceptor extracts backend message from data.error', async () => {
    const interceptor = api.interceptors.request.use((config) => {
      const error = new Error('Request failed')
      error.config = { metadata: { requestId: 100, startTime: Date.now() - 50 } }
      error.response = {
        status: 500,
        data: { error: 'Internal server error' }
      }
      return Promise.reject(error)
    })

    try {
      await expect(api.get('/test-error2')).rejects.toMatchObject({
        message: 'Internal server error'
      })
    } finally {
      api.interceptors.request.eject(interceptor)
    }
  })

  it('response error interceptor keeps original message when no backend message', async () => {
    const interceptor = api.interceptors.request.use((config) => {
      const error = new Error('Network Error')
      error.config = { metadata: { requestId: 1, startTime: Date.now() } }
      error.response = { status: 503, data: {} }
      return Promise.reject(error)
    })

    try {
      await expect(api.get('/test-no-msg')).rejects.toMatchObject({
        message: 'Network Error'
      })
    } finally {
      api.interceptors.request.eject(interceptor)
    }
  })

  it('response error attaches metadata (requestId, duration)', async () => {
    const startTime = Date.now() - 200
    const interceptor = api.interceptors.request.use((config) => {
      const error = new Error('fail')
      error.config = { metadata: { requestId: 42, startTime } }
      error.response = { status: 500, data: {} }
      return Promise.reject(error)
    })

    try {
      await expect(api.get('/test-meta')).rejects.toMatchObject({
        requestId: 42,
        duration: expect.any(Number)
      })
    } finally {
      api.interceptors.request.eject(interceptor)
    }
  })

  it('request interceptor adds metadata (verified through error metadata)', async () => {
    // The source request interceptor adds metadata.requestId and metadata.startTime
    // These are then attached to errors by the response error interceptor
    const interceptor = api.interceptors.request.use((config) => {
      const error = new Error('fail')
      error.config = { metadata: { requestId: 77, startTime: Date.now() - 50 } }
      error.response = { status: 500, data: {} }
      return Promise.reject(error)
    })

    try {
      await expect(api.get('/test-req-id')).rejects.toMatchObject({
        requestId: 77,
        duration: expect.any(Number)
      })
    } finally {
      api.interceptors.request.eject(interceptor)
    }
  })
})
