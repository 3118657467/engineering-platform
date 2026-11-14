import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 20000
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('platform.token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('platform.token');
      localStorage.removeItem('platform.user');
      if (!window.location.hash.startsWith('#/login')) {
        window.location.hash = '#/login';
      }
    }
    const message = error.response?.data?.message || error.message || '请求失败';
    return Promise.reject(new Error(message));
  }
);

export default api;
