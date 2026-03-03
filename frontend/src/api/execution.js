import api from './index.js'

const start = (taskId, agentId) => {
  return api.post('/executions', { taskId, agentId })
}

const getById = (id) => {
  return api.get(`/executions/${id}`)
}

const getByTask = (taskId) => {
  return api.get(`/executions`, { params: { taskId } })
}

const stop = (id) => {
  return api.post(`/executions/${id}/stop`)
}

const getOutputStream = (id) => {
  return new EventSource(`/api/executions/${id}/output`)
}

export default {
  start,
  getById,
  getByTask,
  stop,
  getOutputStream
}
