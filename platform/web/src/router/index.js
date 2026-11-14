import { createRouter, createWebHashHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import AppLayout from '../layout/AppLayout.vue';
import LoginView from '../views/LoginView.vue';
import RequirementsView from '../views/RequirementsView.vue';
import DeployView from '../views/DeployView.vue';
import ApprovalsView from '../views/ApprovalsView.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true, title: '登录' } },
    {
      path: '/',
      component: AppLayout,
      redirect: '/requirements',
      children: [
        {
          path: 'requirements',
          component: RequirementsView,
          meta: { title: '需求管理' }
        },
        {
          path: 'deploy/api',
          component: DeployView,
          props: { service: 'api' },
          meta: { title: '后端部署', service: 'api' }
        },
        {
          path: 'deploy/web',
          component: DeployView,
          props: { service: 'web' },
          meta: { title: '前端部署', service: 'web' }
        },
        {
          path: 'approvals',
          component: ApprovalsView,
          meta: { title: '审批待办' }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/requirements' }
  ]
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    return auth.token ? '/requirements' : true;
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (!auth.user) {
    await auth.restore();
    if (!auth.token) {
      return { path: '/login', query: { redirect: to.fullPath } };
    }
  }
  return true;
});

export default router;
