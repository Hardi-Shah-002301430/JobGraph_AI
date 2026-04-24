import { api } from './api.js'

export const jobService = {
  matches:   (userId = 1, page = 0, size = 20) => api.get(`/jobs/matches?userId=${userId}&page=${page}&size=${size}`),
  dashboard: (userId = 1)                      => api.get(`/jobs/dashboard?userId=${userId}`),
}

export const resumeService = {
  list:       (userId = 1) => api.get(`/resumes?userId=${userId}`),
  latest:     (userId = 1) => api.get(`/resumes/latest?userId=${userId}`),
  uploadFile: (file, userId = 1) => {
    const form = new FormData()
    form.append('file', file)
    return api.upload(`/resumes?userId=${userId}`, form)
  },
  uploadText: (rawText, userId = 1) => api.post(`/resumes?userId=${userId}`, { rawText }),
}

export const companyService = {
  list:   ()        => api.get('/companies'),
  create: (body)    => api.post('/companies', body),
  toggle: (id)      => api.patch(`/companies/${id}/toggle`),
  delete: (id)      => api.delete(`/companies/${id}`),
}

export const trackingService = {
  list:   (userId = 1)       => api.get(`/tracking?userId=${userId}`),
  start:  (jobId, resumeId)  => api.post(`/tracking?jobId=${jobId}&resumeId=${resumeId}`),
  update: (id, status, notes) => api.patch(`/tracking/${id}`, { status, notes }),
}

export const clusterService = {
  status: () => api.get('/cluster/status'),
}
