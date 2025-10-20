import http from './http';

export const fetchAlertRules = () => http.get('/alerts/rules');

export const updateAlertRule = (ruleId, payload) => http.put(`/alerts/rules/${ruleId}`, payload);

export const createAlertRule = (payload) => http.post('/alerts/rules', payload);

export const deleteAlertRule = (ruleId) => http.delete(`/alerts/rules/${ruleId}`);

export const testAlertChannel = (payload) => http.post('/alerts/test', payload);
