import { describe, expect, it } from 'vitest'

import {
  SESSION_INPUT_STATUSES,
  SESSION_BUSY_STATUSES
} from '../src/constants/session'

describe('session constants', () => {
  describe('SESSION_INPUT_STATUSES', () => {
    it('contains interactive and resumable statuses', () => {
      expect(SESSION_INPUT_STATUSES).toEqual([
        'RUNNING', 'STOPPED', 'SUSPENDED', 'ASK_USER', 'COMPLETED', 'FAILED', 'CANCELLED'
      ])
    })
  })

  describe('SESSION_BUSY_STATUSES', () => {
    it('contains only RUNNING', () => {
      expect(SESSION_BUSY_STATUSES).toEqual(['RUNNING'])
    })
  })
})
