import api from './index.js'

const getByProject = (projectId) => {
  return api.get(`/projects/${projectId}/task-sources`)
}

const create = (data) => {
  return api.post('/task-sources', data)
}

const sync = (id) => {
  return api.post(`/task-sources/${id}/sync`)
}

const testConnection = (id) => {
  return api.post(`/task-sources/${id}/test-connection`)
}

const delete = (id) => {
  return api.delete(`/task-sources/${id}`)
}

export default {
  getByProject,
  create,
  sync,
  testConnection,
  delete
}
