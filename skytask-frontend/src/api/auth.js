import http from './http';

const overrideBase = { baseURL: '/' };

export const login = (payload) => http.post('/auth/login', payload, overrideBase);

export const refreshToken = (refreshToken) =>
  http.post('/auth/refresh', { refreshToken }, overrideBase);

export const fetchProfile = () => http.get('/auth/profile', overrideBase);

export default {
  login,
  refreshToken,
  fetchProfile
};
