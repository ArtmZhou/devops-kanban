import api from './index.js'

const getByProject = (projectId) => {
  return api.get(`/tasks`, { params: { projectId } })
}

const getById = (id) => {
  return api.get(`/tasks/${id}`)
}

const create = (data) => {
  return api.post('/tasks', data)
}

const update = (id, data) => {
  return api.put(`/tasks/${id}`, data)
}

const updateStatus = (id, status) => {
  return api.patch(`/tasks/${id}/status`, { status })
}

const deleteTask = (id) => {
  return api.delete(`/tasks/${id}`)
}

export default {
  getByProject,
  getById,
  create,
  update,
  updateStatus,
  delete: deleteTask
}
