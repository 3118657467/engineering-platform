import { defineStore } from 'pinia';
import api from '../api/client';

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('platform.user') || 'null');
  } catch {
    return null;
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('platform.token') || '',
    user: readUser()
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isCrAdmin: (state) => state.user?.role === 'CR_ADMIN',
    displayName: (state) => state.user?.displayName || ''
  },
  actions: {
    async login(username, password) {
      const { data } = await api.post('/auth/login', { username, password });
      this.token = data.token;
      this.user = data.user;
      localStorage.setItem('platform.token', data.token);
      localStorage.setItem('platform.user', JSON.stringify(data.user));
    },
    async restore() {
      if (!this.token) return;
      try {
        const { data } = await api.get('/auth/me');
        this.user = data;
        localStorage.setItem('platform.user', JSON.stringify(data));
      } catch {
        this.clear();
      }
    },
    async logout() {
      try {
        await api.post('/auth/logout');
      } catch {
        // 会话已失效时忽略
      }
      this.clear();
    },
    clear() {
      this.token = '';
      this.user = null;
      localStorage.removeItem('platform.token');
      localStorage.removeItem('platform.user');
    }
  }
});
