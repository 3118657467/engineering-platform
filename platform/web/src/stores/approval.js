import { defineStore } from 'pinia';
import api from '../api/client';

export const useApprovalStore = defineStore('approvals', {
  state: () => ({
    items: [],
    loading: false
  }),
  getters: {
    count: (state) => state.items.length
  },
  actions: {
    async refresh() {
      if (!localStorage.getItem('platform.token')) {
        this.items = [];
        return;
      }
      this.loading = true;
      try {
        const { data } = await api.get('/approvals');
        this.items = data;
      } catch {
        this.items = [];
      } finally {
        this.loading = false;
      }
    }
  }
});
