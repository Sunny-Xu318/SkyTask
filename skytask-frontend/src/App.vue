<template>
  <router-view v-if="route.meta?.public"></router-view>
  <el-config-provider v-else :size="layoutSize">
    <div class="layout">
      <el-container>
        <el-aside width="220px" class="sidebar">
          <div class="brand">
            <span class="brand__name">SkyTask 控制台</span>
            <span class="brand__env">{{ activeEnvLabel }}</span>
          </div>
          <el-menu
            :default-active="activeMenu"
            class="menu"
            router
            background-color="#0f172a"
            text-color="#f1f5f9"
            active-text-color="#22d3ee"
          >
            <el-menu-item v-if="canViewDashboard" index="/dashboard">
              <el-icon><monitor /></el-icon>
              <span>总览监控</span>
            </el-menu-item>
            <el-menu-item v-if="canViewTasks" index="/tasks">
              <el-icon><collection /></el-icon>
              <span>任务中心</span>
            </el-menu-item>
            <el-menu-item v-if="canViewNodes" index="/nodes">
              <el-icon><cpu /></el-icon>
              <span>执行器节点</span>
            </el-menu-item>
            <el-menu-item v-if="canViewConfig" index="/config">
              <el-icon><setting /></el-icon>
              <span>配置与告警</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        <el-container>
          <el-header class="header">
            <div class="header__left">
              <el-breadcrumb separator="/">
                <el-breadcrumb-item v-for="item in breadcrumb" :key="item.path">
                  <router-link :to="item.path">{{ item.title }}</router-link>
                </el-breadcrumb-item>
              </el-breadcrumb>
            </div>
            <div class="header__right">
              <el-select
                v-model="activeEnv"
                placeholder="选择环境"
                size="small"
                class="env-selector"
              >
                <el-option
                  v-for="env in environments"
                  :key="env.value"
                  :label="env.label"
                  :value="env.value"
                />
              </el-select>
              <el-dropdown>
                <span class="user">
                  <el-avatar size="small">{{ avatarInitials }}</el-avatar>
                  {{ profile?.displayName || profile?.username || '未登录' }}
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item>{{ profile?.tenantName || '-' }}</el-dropdown-item>
                    <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </el-header>
          <el-main class="main">
            <router-view />
          </el-main>
        </el-container>
      </el-container>
    </div>
  </el-config-provider>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { Setting, Monitor, Collection, Cpu } from '@element-plus/icons-vue';

const router = useRouter();
const route = useRoute();
const store = useStore();

const layoutSize = ref('default');
const activeEnv = ref(store.getters['auth/currentEnv']);
const environments = computed(() => store.getters['auth/environments']);
const activeMenu = computed(() => route.path);
const breadcrumb = computed(() => store.getters['ui/breadcrumbs']);
const profile = computed(() => store.getters['auth/profile']);

const canViewDashboard = computed(() => store.getters['auth/hasPermission']('task:read'));
const canViewTasks = computed(() => store.getters['auth/hasPermission']('task:read'));
const canViewNodes = computed(() => store.getters['auth/hasPermission']('node:read'));
const canViewConfig = computed(() => store.getters['auth/hasPermission']('config:write'));

const activeEnvLabel = computed(() => {
  const env = environments.value.find((item) => item.value === activeEnv.value);
  return env ? env.label : '未知环境';
});

const avatarInitials = computed(() => {
  if (!profile.value?.displayName && !profile.value?.username) {
    return 'NA';
  }
  const name = profile.value.displayName || profile.value.username;
  return name.slice(0, 2).toUpperCase();
});

watch(
  () => route.meta?.breadcrumbs,
  (crumbs) => {
    store.dispatch('ui/setBreadcrumbs', crumbs || []);
  },
  { immediate: true }
);

watch(activeEnv, (env) => {
  store.dispatch('auth/changeEnv', env);
  router.replace({ query: { ...route.query, env } });
});

const logout = () => {
  store.dispatch('auth/logout');
  router.replace({ path: '/login', query: { redirect: route.fullPath !== '/login' ? route.fullPath : undefined } });
};

const setting = Setting;
const monitor = Monitor;
const collection = Collection;
const cpu = Cpu;
</script>

<style scoped lang="scss">
.layout {
  min-height: 100vh;
  background: #0f172a;

  .sidebar {
    background: #0f172a;
    box-shadow: inset -1px 0 0 rgba(148, 163, 184, 0.1);
    display: flex;
    flex-direction: column;
    padding-bottom: 16px;
  }

  .brand {
    display: flex;
    flex-direction: column;
    padding: 24px 16px;
    color: #e2e8f0;
    border-bottom: 1px solid rgba(148, 163, 184, 0.15);

    &__name {
      font-size: 18px;
      font-weight: 600;
      letter-spacing: 0.5px;
    }

    &__env {
      font-size: 12px;
      color: #38bdf8;
      margin-top: 4px;
    }
  }

  .menu {
    border-right: none;
    flex: 1;
    background: transparent;
  }

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 24px;
    background: linear-gradient(90deg, rgba(15, 23, 42, 0.9), rgba(30, 41, 59, 0.85));
    color: #f1f5f9;
    box-shadow: 0 1px 4px rgba(15, 23, 42, 0.3);

    .header__left {
      display: flex;
      align-items: center;

      .el-breadcrumb {
        font-size: 14px;

        a {
          color: #bae6fd;
        }
      }
    }

    .header__right {
      display: flex;
      align-items: center;
      gap: 12px;

      .env-selector {
        width: 160px;
      }

      .user {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        cursor: pointer;
        color: #e2e8f0;
      }
    }
  }

  .main {
    padding: 24px;
    background: #e2e8f0;
    min-height: calc(100vh - 64px);
  }
}
</style>
