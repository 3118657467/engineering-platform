<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="side-brand">
        <span class="brand-mark small">EP</span>
        <span class="side-name">工程平台</span>
      </div>

      <nav class="nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          :to="item.path"
        >
          <span class="nav-dot"></span>{{ item.label }}
          <span v-if="item.tag" class="nav-tag">{{ item.tag }}</span>
          <em v-if="item.badge" class="badge" :class="{ zero: approvalStore.count === 0 }">
            {{ approvalStore.count }}
          </em>
        </RouterLink>
      </nav>

      <div class="side-foot">
        <div class="rule-card">
          <strong>部署门禁</strong>
          <p>dev → test → uat → prod</p>
          <p>逐级解锁，SHA 一致</p>
          <p>test 首部署需 CR</p>
        </div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="crumbs">{{ route.meta.title || '工程平台' }}</div>
        <div class="top-actions">
          <span class="role-pill">{{ auth.isCrAdmin ? 'CR 管理员' : '开发' }}</span>
          <div class="user">
            <span class="avatar">{{ avatar }}</span>
            <span>{{ auth.displayName }}</span>
          </div>
          <button class="ghost-btn" @click="logout">退出</button>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue';
import { useRoute, useRouter, RouterLink } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useApprovalStore } from '../stores/approval';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const approvalStore = useApprovalStore();

const navItems = [
  { path: '/requirements', label: '需求管理' },
  { path: '/deploy/api', label: '后端部署', tag: 'api' },
  { path: '/deploy/web', label: '前端部署', tag: 'web' },
  { path: '/approvals', label: '审批待办', badge: true }
];

const avatar = computed(() => (auth.displayName || '?').slice(0, 1));

function isActive(path) {
  return route.path === path;
}

async function logout() {
  await auth.logout();
  approvalStore.items = [];
  router.push('/login');
}

onMounted(() => approvalStore.refresh());
watch(() => route.path, () => approvalStore.refresh());
</script>
