import { describe, expect, it } from 'vitest'
import {
  TASK_STATUS,
  TASK_PRIORITY,
  TASK_PRIORITY_ORDER,
  TASK_DEFAULTS,
  getPriorityClass,
  compareByPriority
} from '../src/constants/task'

describe('task constants', () => {
  describe('getPriorityClass', () => {
    it('returns correct class for each priority', () => {
      expect(getPriorityClass('CRITICAL')).toBe('priority-critical')
      expect(getPriorityClass('HIGH')).toBe('priority-high')
      expect(getPriorityClass('MEDIUM')).toBe('priority-medium')
      expect(getPriorityClass('LOW')).toBe('priority-low')
    })

    it('defaults to MEDIUM for null/undefined', () => {
      expect(getPriorityClass(null)).toBe('priority-medium')
      expect(getPriorityClass(undefined)).toBe('priority-medium')
    })

    it('handles lowercase priority', () => {
      expect(getPriorityClass('high')).toBe('priority-high')
    })
  })

  describe('compareByPriority', () => {
    it('sorts CRITICAL before HIGH', () => {
      expect(compareByPriority({ priority: 'CRITICAL' }, { priority: 'HIGH' })).toBeLessThan(0)
    })

    it('sorts HIGH before MEDIUM', () => {
      expect(compareByPriority({ priority: 'HIGH' }, { priority: 'MEDIUM' })).toBeLessThan(0)
    })

    it('sorts MEDIUM before LOW', () => {
      expect(compareByPriority({ priority: 'MEDIUM' }, { priority: 'LOW' })).toBeLessThan(0)
    })

    it('returns 0 for same priority', () => {
      expect(compareByPriority({ priority: 'HIGH' }, { priority: 'HIGH' })).toBe(0)
    })

    it('defaults missing priority to MEDIUM', () => {
      expect(compareByPriority({}, {})).toBe(0)
      expect(compareByPriority({ priority: 'HIGH' }, {})).toBeLessThan(0)
    })
  })

  describe('TASK_PRIORITY_ORDER', () => {
    it('has correct order values', () => {
      expect(TASK_PRIORITY_ORDER.CRITICAL).toBeLessThan(TASK_PRIORITY_ORDER.HIGH)
      expect(TASK_PRIORITY_ORDER.HIGH).toBeLessThan(TASK_PRIORITY_ORDER.MEDIUM)
      expect(TASK_PRIORITY_ORDER.MEDIUM).toBeLessThan(TASK_PRIORITY_ORDER.LOW)
    })
  })

  describe('TASK_DEFAULTS', () => {
    it('has correct default values', () => {
      expect(TASK_DEFAULTS.status).toBe('TODO')
      expect(TASK_DEFAULTS.priority).toBe('MEDIUM')
    })
  })

  describe('TASK_STATUS', () => {
    it('contains all expected statuses', () => {
      expect(TASK_STATUS.TODO).toBe('TODO')
      expect(TASK_STATUS.IN_PROGRESS).toBe('IN_PROGRESS')
      expect(TASK_STATUS.DONE).toBe('DONE')
      expect(TASK_STATUS.BLOCKED).toBe('BLOCKED')
    })
  })

  describe('TASK_PRIORITY', () => {
    it('contains all expected priorities', () => {
      expect(TASK_PRIORITY.CRITICAL).toBe('CRITICAL')
      expect(TASK_PRIORITY.HIGH).toBe('HIGH')
      expect(TASK_PRIORITY.MEDIUM).toBe('MEDIUM')
      expect(TASK_PRIORITY.LOW).toBe('LOW')
    })
  })
})
