import { beforeEach, describe, expect, it, vi } from 'vitest'

// Mock useToast before importing the module under test
vi.mock('../src/composables/ui/useToast', () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    apiError: vi.fn(),
    notify: vi.fn(),
    fromResponse: vi.fn()
  })
}))

import { useApiErrorHandler } from '../src/composables/useApiErrorHandler'

describe('useApiErrorHandler', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('with default options', () => {
    it('createResponseError creates error with response message', () => {
      const handler = useApiErrorHandler()
      const error = handler.createResponseError({ message: 'Bad request' })
      expect(error.message).toBe('Bad request')
    })

    it('createResponseError falls back to error field', () => {
      const handler = useApiErrorHandler()
      const error = handler.createResponseError({ error: 'Server error' })
      expect(error.message).toBe('Server error')
    })

    it('createResponseError uses defaultMessage when no message', () => {
      const handler = useApiErrorHandler({ defaultMessage: 'Custom default' })
      const error = handler.createResponseError({})
      expect(error.message).toBe('Custom default')
    })

    it('unwrapResponse returns data on success', () => {
      const handler = useApiErrorHandler({ showMessage: false })
      const result = handler.unwrapResponse({ success: true, data: { id: 1 } })
      expect(result).toEqual({ id: 1 })
    })

    it('unwrapResponse throws on failure', () => {
      const handler = useApiErrorHandler({ showMessage: false })
      expect(() => handler.unwrapResponse({ success: false, message: 'Failed' })).toThrow('Failed')
    })

    it('unwrapResponse uses fallback message', () => {
      const handler = useApiErrorHandler({ showMessage: false })
      expect(() => handler.unwrapResponse({ success: false }, 'Custom fallback')).toThrow('Custom fallback')
    })

    it('handleResponse returns true for successful response', () => {
      const handler = useApiErrorHandler({ showMessage: false })
      const result = handler.handleResponse({ success: true, data: { id: 1 } })
      expect(result).toBe(true)
    })

    it('handleResponse returns false for failed response', () => {
      const handler = useApiErrorHandler({ showMessage: false })
      const result = handler.handleResponse({ success: false, message: 'Error' })
      expect(result).toBe(false)
    })

    it('handleResponse returns false for null response', () => {
      const handler = useApiErrorHandler({ showMessage: false })
      const result = handler.handleResponse(null)
      expect(result).toBe(false)
    })

    it('withErrorHandling wraps async function', async () => {
      const handler = useApiErrorHandler({ showMessage: false })
      const fn = vi.fn().mockResolvedValue('result')

      const result = await handler.withErrorHandling(fn, 'Custom error')
      expect(result).toBe('result')
    })

    it('withErrorHandling catches and rethrows', async () => {
      const handler = useApiErrorHandler({ showMessage: false })
      const fn = vi.fn().mockRejectedValue(new Error('Async fail'))

      await expect(handler.withErrorHandling(fn, 'Custom error')).rejects.toThrow('Async fail')
    })
  })

  describe('with custom callbacks', () => {
    it('calls onError callback on failure', () => {
      const onError = vi.fn()
      const handler = useApiErrorHandler({ showMessage: false, onError })

      try {
        handler.unwrapResponse({ success: false, message: 'Fail' })
      } catch (e) {
        // expected
      }

      expect(onError).toHaveBeenCalled()
    })

    it('calls onSuccess callback on success response', () => {
      const onSuccess = vi.fn()
      const handler = useApiErrorHandler({ showMessage: false })

      handler.handleResponse(
        { success: true, data: { id: 1 } },
        { onSuccess }
      )

      expect(onSuccess).toHaveBeenCalledWith({ id: 1 })
    })
  })
})
