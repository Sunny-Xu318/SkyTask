import http from './http';

export const fetchTaskStatistics = (params) => http.get('/tasks/metrics', { params });

export const fetchTaskList = (params) => http.get('/tasks', { params });

export const fetchTaskDetail = (taskId) => http.get(`/tasks/${taskId}`);

export const createTask = (payload) => http.post('/tasks', payload);

export const updateTask = (taskId, payload) => http.put(`/tasks/${taskId}`, payload);

export const toggleTaskEnabled = (taskId, enabled) =>
  http.patch(`/tasks/${taskId}/status`, { enabled });

export const deleteTask = (taskId) => http.delete(`/tasks/${taskId}`);

export const runTaskNow = (taskId, payload) => http.post(`/tasks/${taskId}/trigger`, payload);

export const fetchTaskExecutions = (taskId, params) =>
  http.get(`/tasks/${taskId}/records`, { params });

export const fetchCronSuggestions = (keyword) =>
  http.get('/tasks/cron/suggestions', { params: { keyword } });
