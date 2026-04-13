import { describe, expect, it } from 'vitest'

import api from '../src/api/index.js'
import * as workflowTemplateApi from '../src/api/workflowTemplate.js'

describe('workflow template export api helpers', () => {
  it('exports a single template by id', async () => {
    expect(typeof workflowTemplateApi.exportWorkflowTemplate).toBe('function')

    const seen = []
    const interceptor = api.interceptors.request.use((config) => {
      seen.push({
        url: config.url,
        method: config.method,
        responseType: config.responseType
      })
      return Promise.reject(new Error('stop request'))
    })

    try {
      await expect(workflowTemplateApi.exportWorkflowTemplate('my-template')).rejects.toThrow('stop request')
    } finally {
      api.interceptors.request.eject(interceptor)
    }

    expect(seen).toEqual([
      {
        url: '/workflow-template/export/my-template',
        method: 'get',
        responseType: 'json'
      }
    ])
  })

  it('batch exports templates by ids', async () => {
    expect(typeof workflowTemplateApi.exportWorkflowTemplates).toBe('function')

    const seen = []
    const interceptor = api.interceptors.request.use((config) => {
      seen.push({
        url: config.url,
        method: config.method,
        data: config.data,
        responseType: config.responseType
      })
      return Promise.reject(new Error('stop request'))
    })

    const ids = ['template-a', 'template-b']

    try {
      await expect(workflowTemplateApi.exportWorkflowTemplates(ids)).rejects.toThrow('stop request')
    } finally {
      api.interceptors.request.eject(interceptor)
    }

    expect(seen).toEqual([
      {
        url: '/workflow-template/export',
        method: 'post',
        data: { templateIds: ids },
        responseType: 'json'
      }
    ])
  })

  it('sends preview import request with export data', async () => {
    expect(typeof workflowTemplateApi.previewImportWorkflowTemplates).toBe('function')

    const seen = []
    const interceptor = api.interceptors.request.use((config) => {
      seen.push({
        url: config.url,
        method: config.method,
        data: config.data
      })
      return Promise.reject(new Error('stop request'))
    })

    const exportData = {
      version: '1.0',
      exportedAt: '2026-04-13T00:00:00Z',
      templates: [{
        template_id: 'test',
        name: 'Test',
        steps: [{ id: 's1', name: 'Step', instructionPrompt: 'Do it', agentName: 'Agent' }]
      }]
    }

    try {
      await expect(workflowTemplateApi.previewImportWorkflowTemplates(exportData)).rejects.toThrow('stop request')
    } finally {
      api.interceptors.request.eject(interceptor)
    }

    expect(seen).toEqual([
      {
        url: '/workflow-template/import',
        method: 'post',
        data: exportData
      }
    ])
  })

  it('sends confirm import request with strategy and agent mappings', async () => {
    expect(typeof workflowTemplateApi.confirmImportWorkflowTemplates).toBe('function')

    const seen = []
    const interceptor = api.interceptors.request.use((config) => {
      seen.push({
        url: config.url,
        method: config.method,
        data: config.data
      })
      return Promise.reject(new Error('stop request'))
    })

    const inputData = {
      templates: [{ template_id: 'test', name: 'Test', steps: [] }],
      strategy: 'copy',
      agentMappings: { 'Agent A': 1 }
    }

    try {
      await expect(workflowTemplateApi.confirmImportWorkflowTemplates(inputData)).rejects.toThrow('stop request')
    } finally {
      api.interceptors.request.eject(interceptor)
    }

    expect(seen).toEqual([
      {
        url: '/workflow-template/import/confirm',
        method: 'post',
        data: inputData
      }
    ])
  })
})
