import request from '@/utils/request'

export const workCalendarApi = {
  list: () => request.get('/work-calendars'),
  get: id => request.get(`/work-calendars/${id}`),
  create: data => request.post('/work-calendars', data),
  update: (id, data) => request.post(`/work-calendars/${id}/update`, data),
  publish: id => request.post(`/work-calendars/${id}/publish`),
  disable: id => request.post(`/work-calendars/${id}/disable`),
  simulate: (id, data) => request.post(`/work-calendars/${id}/simulate`, data)
}

export const taskSlaPolicyApi = {
  list: () => request.get('/task-sla-policies'),
  published: () => request.get('/task-sla-policies/published'),
  get: id => request.get(`/task-sla-policies/${id}`),
  create: data => request.post('/task-sla-policies', data),
  update: (id, data) => request.post(`/task-sla-policies/${id}/update`, data),
  publish: id => request.post(`/task-sla-policies/${id}/publish`)
}

export const taskSlaApi = {
  detail: taskId => request.get(`/tasks/${taskId}/sla`),
  acknowledge: taskId => request.post(`/tasks/${taskId}/acknowledge`),
  pause: (taskId, data) => request.post(`/tasks/${taskId}/sla/pause`, data),
  resume: taskId => request.post(`/tasks/${taskId}/sla/resume`),
  monitor: params => request.get('/task-sla/monitor', { params }),
  statistics: () => request.get('/task-sla/monitor/statistics')
}
