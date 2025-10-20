import { createRouter, createWebHashHistory } from 'vue-router';
import { ElMessage } from 'element-plus';
import store from '../store';

const LoginView = () => import('../views/LoginView.vue');
const DashboardView = () => import('../views/DashboardView.vue');
const TaskCenterView = () => import('../views/TaskCenterView.vue');
const TaskDetailView = () => import('../views/TaskDetailView.vue');
const NodeMonitorView = () => import('../views/NodeMonitorView.vue');
const ConfigCenterView = () => import('../views/ConfigCenterView.vue');

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { public: true }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: DashboardView,
    meta: {
      requiresAuth: true,
      permissions: ['task:read'],
      breadcrumbs: [{ title: '总览监控', path: '/dashboard' }]
    }
  },
  {
    path: '/tasks',
    name: 'TaskCenter',
    component: TaskCenterView,
    meta: {
      requiresAuth: true,
      permissions: ['task:read'],
      breadcrumbs: [{ title: '任务中心', path: '/tasks' }]
    }
  },
  {
    path: '/tasks/:taskId',
    name: 'TaskDetail',
    component: TaskDetailView,
    meta: {
      requiresAuth: true,
      permissions: ['task:read'],
      breadcrumbs: [
        { title: '任务中心', path: '/tasks' },
        { title: '任务详情', path: '/tasks/:taskId' }
      ]
    }
  },
  {
    path: '/nodes',
    name: 'NodeMonitor',
    component: NodeMonitorView,
    meta: {
      requiresAuth: true,
      permissions: ['node:read'],
      breadcrumbs: [{ title: '执行器节点', path: '/nodes' }]
    }
  },
  {
    path: '/config',
    name: 'ConfigCenter',
    component: ConfigCenterView,
    meta: {
      requiresAuth: true,
      permissions: ['config:write'],
      breadcrumbs: [{ title: '配置与告警', path: '/config' }]
    }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  const isAuthenticated = store.getters['auth/isAuthenticated'];

  if (to.meta.public) {
    if (isAuthenticated && to.path === '/login') {
      return next({ path: '/dashboard' });
    }
    return next();
  }

  if (to.meta.requiresAuth && !isAuthenticated) {
    return next({
      path: '/login',
      query: { redirect: to.fullPath }
    });
  }

  if (to.meta.permissions && to.meta.permissions.length > 0) {
    const allowed = store.getters['auth/hasAnyPermission'](to.meta.permissions);
    if (!allowed) {
      ElMessage.warning('当前账号没有访问该页面的权限');
      return next(from.fullPath === '/login' ? '/dashboard' : from.fullPath || '/dashboard');
    }
  }

  return next();
});

export default router;
