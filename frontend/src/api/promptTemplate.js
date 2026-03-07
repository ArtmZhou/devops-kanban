import api from './index.js'

/**
 * API module for Prompt Template operations.
 */

// Get all prompt templates
export const getPromptTemplates = () => api.get('/prompt-templates')

// Get prompt template by phase
export const getPromptTemplateByPhase = (phase) => api.get(`/prompt-templates/phase/${phase}`)

// Update prompt template
export const updatePromptTemplate = (id, data) => api.put(`/prompt-templates/${id}`, data)

// Reset prompt template to default
export const resetPromptTemplate = (id) => api.post(`/prompt-templates/${id}/reset`)

// Initialize default prompt templates
export const initializeDefaults = () => api.post('/prompt-templates/initialize')
