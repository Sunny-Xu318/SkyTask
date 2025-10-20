import axios from 'axios';
import store from '../store';

const http = axios.create({
  baseURL: '/api',
  timeout: 8000
});

http.interceptors.request.use(
  (config) => {
    const token = store.getters['auth/token'];
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    const tenantCode = store.getters['auth/tenantCode'];
    if (tenantCode) {
      config.headers['X-SkyTask-Tenant'] = tenantCode;
    }
    const env = store.getters['auth/currentEnv'];
    if (env) {
      config.headers['X-SkyTask-Env'] = env;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response) {
      console.error('API Error:', error.response.status, error.response.data);
    }
    return Promise.reject(error);
  }
);

export default http;
