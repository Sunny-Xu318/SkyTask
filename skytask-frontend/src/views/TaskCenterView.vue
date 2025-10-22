<template>
  <div class="task-center">
    <section class="task-center__toolbar card">
      <div class="toolbar__left">
        <el-form :inline="true" :model="filters">
          <el-form-item>
            <el-input
              v-model="filters.keyword"
              placeholder="搜索任务名称 / 负责人 / 标签"
              clearable
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-select
              v-model="filters.status"
              placeholder="任务状态"
              clearable
              @change="handleSearch"
            >
              <el-option label="全部" value="ALL" />
              <el-option label="已启用" value="ENABLED" />
              <el-option label="已禁用" value="DISABLED" />
              <el-option label="失败告警" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select
              v-model="filters.owner"
              placeholder="负责人"
              clearable
              filterable
              @change="handleSearch"
            >
              <el-option
                v-for="owner in ownerOptions"
                :key="owner.value"
                :label="owner.label"
                :value="owner.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select
              v-model="filters.tags"
              multiple
              collapse-tags
              placeholder="标签"
              @change="handleSearch"
            >
              <el-option
                v-for="tag in tagOptions"
                :key="tag"
                :label="tag"
                :value="tag"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <div class="toolbar__right">
        <el-button v-if="canReadTasks" :icon="Download" @click="exportTasks">导出</el-button>
        <el-button
          v-if="canManageTasks"
          type="primary"
          :icon="Plus"
          @click="openCreate"
        >
          新建任务
        </el-button>
      </div>
    </section>
    <el-alert
      v-if="metricsErrorState"
      class="task-center__alert"
      :type="metricsErrorState.forbidden ? 'warning' : 'error'"
      :title="metricsErrorState.forbidden ? '无权查看任务统计' : '任务指标加载失败'"
      :description="metricsErrorState.message"
      show-icon
      :closable="false"
    />

    <section v-if="canReadTasks" class="task-center__content card">
      <div v-if="listErrorState" class="task-center__error">
        <el-result
          :status="listErrorState.forbidden ? 'warning' : 'error'"
          :title="listErrorState.forbidden ? '无访问权限' : '任务列表加载失败'"
          :sub-title="listErrorState.message"
        >
          <template #extra>
            <el-button type="primary" size="small" @click="requestTaskData">重试</el-button>
          </template>
        </el-result>
      </div>
      <el-table v-else :data="taskList" height="500" v-loading="loading">
        <el-table-column label="任务" min-width="220">
          <template #default="{ row }">
            <div class="task-name">
              <el-link type="primary" @click="openDetail(row)">{{ row.name }}</el-link>
              <el-tag v-if="row.group" size="small" class="task-name__tag">{{ row.group }}</el-tag>
            </div>
            <div class="task-meta">
              <span>最后运行: {{ row.lastTrigger || '-' }}</span>
              <span>最后节点: {{ row.lastNode || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ formatType(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpr" label="调度配置" width="160" show-overflow-tooltip />
        <el-table-column label="负责人" width="140">
          <template #default="{ row }">
            <el-avatar size="small" class="owner-avatar">{{ getOwnerInitials(row.owner) }}</el-avatar>
            <span>{{ row.owner }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="告警" width="120">
          <template #default="{ row }">
            <el-tag :type="row.alertEnabled ? 'success' : 'info'" size="small">
              {{ row.alertEnabled ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canReadTasks"
              link
              type="primary"
              size="small"
              @click="openExecution(row)"
            >
              记录
            </el-button>
            <el-button
              v-if="canManageTasks"
              link
              type="primary"
              size="small"
              @click="editTask(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canManageTasks"
              link
              type="warning"
              size="small"
              @click="toggleTask(row)"
            >
              {{ row.status === 'ENABLED' ? '禁用' : '启用' }}
            </el-button>
            <el-popconfirm
              v-if="canManageTasks"
              title="确定删除此任务吗？"
              @confirm="removeTask(row)"
            >
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
            <el-button
              v-if="canTriggerTasks"
              link
              type="success"
              size="small"
              @click="triggerTask(row)"
            >
              触发
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无任务" />
        </template>
      </el-table>
    </section>

    <section v-else class="card task-center__empty">
      <el-empty description="您没有权限查看任务" />
    </section>
    <div v-if="canReadTasks" class="task-center__pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="pagination.total"
        :current-page="pagination.page"
        :page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="changePage"
        @size-change="changeSize"
      />
    </div>
    <TaskFormDrawer
      v-model:visible="drawerVisible"
      :model-value="currentTaskForm"
      :group-options="groupOptions"
      :owner-options="ownerOptions"
      :tag-options="tagOptions"
      :submitting="submitting"
      @submit="submitTask"
      @update:visible="handleDrawerVisible"
      @cron-helper="openCronHelper"
    />

    <TaskExecutionDrawer
      v-model:visible="executionVisible"
      :executions="executions"
      :pagination="executionPagination"
      :loading="executionsLoading"
      @page-change="handleExecutionPage"
      @view-log="viewLog"
    />
  </div>
</template>

<script setup>
/* eslint-disable no-unused-vars */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { Search, Plus, Download } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import StatusTag from '@/components/common/StatusTag.vue';
import TaskFormDrawer from '@/components/task/TaskFormDrawer.vue';
import TaskExecutionDrawer from '@/components/task/TaskExecutionDrawer.vue';

const store = useStore();
const route = useRoute();
const router = useRouter();

const canReadTasks = computed(() => store.getters['auth/hasPermission']('task:read'));
const canManageTasks = computed(() => store.getters['auth/hasPermission']('task:write'));
const canTriggerTasks = computed(() => store.getters['auth/hasPermission']('task:trigger'));

const drawerVisible = ref(false);
const executionVisible = ref(false);
const submitting = ref(false);
const currentTaskForm = ref({});
const currentTask = ref(null);
let refreshTimer = null;
let stopPermissionWatcher = null;

const filters = reactive({
  keyword: '',
  status: 'ALL',
  owner: null,
  tags: []
});

const ownerOptions = [
  { label: 'Zhang Qiang - Data Platform', value: 'zhangqiang' },
  { label: 'Li Na - Marketing', value: 'lina' },
  { label: 'Wang Wei - Settlement', value: 'wangwei' },
  { label: 'Zhou Kai - Recommendation', value: 'zhoukai' }
];

const tagOptions = ['Report', 'Risk', 'Cache', 'Recommend', 'Marketing', 'Settlement'];

const groupOptions = [
  { label: 'Data Report', value: 'DATA_REPORT' },
  { label: 'Realtime Risk', value: 'RISK_CONTROL' },
  { label: 'Marketing Center', value: 'MARKETING' },
  { label: 'Recommendation', value: 'RECOMMEND' }
];

const buildErrorState = (error) => {
  if (!error) {
    return null;
  }
  if (error.message && typeof error.forbidden === 'boolean') {
    return { forbidden: error.forbidden, message: error.message };
  }
  const status = Number(error?.response?.status);
  const forbidden = status === 403;
  const message =
    error?.response?.data?.message ||
    error?.message ||
    (forbidden ? 'You do not have permission to perform this action' : 'Request failed, please retry');
  return { forbidden, message };
};

const taskList = computed(() => store.getters['tasks/taskList']);
const pagination = computed(() => store.getters['tasks/taskPagination']);
const executions = computed(() => store.getters['tasks/taskExecutions']);
const executionPagination = computed(() => store.getters['tasks/taskExecutionPagination']);
const loading = computed(() => store.getters['tasks/tasksLoading']);
const executionsLoading = computed(() => store.getters['tasks/taskExecutionLoading']);
const listError = computed(() => store.getters['tasks/taskListError']);
const metricsError = computed(() => store.getters['tasks/taskMetricsError']);
const listErrorState = computed(() => buildErrorState(listError.value));
const metricsErrorState = computed(() => buildErrorState(metricsError.value));

const ensureReadPermission = (toast = true) => {
  if (canReadTasks.value) {
    return true;
  }
  if (toast) {
    ElMessage.warning('您没有权限查看任务');
  }
  return false;
};

const ensureManagePermission = () => {
  if (canManageTasks.value) {
    return true;
  }
  ElMessage.warning('您没有权限管理任务');
  return false;
};

const ensureTriggerPermission = () => {
  if (canTriggerTasks.value) {
    return true;
  }
  ElMessage.warning('您没有权限触发任务');
  return false;
};

const requestTaskData = async () => {
  if (!ensureReadPermission(false)) {
    return;
  }
  try {
    await store.dispatch('tasks/loadTaskList');
  } catch (error) {
    // errors handled via store state
  }
  try {
    await store.dispatch('tasks/loadMetrics');
  } catch (error) {
    // errors handled via store state
  }
};

const startAutoRefresh = () => {
  if (refreshTimer) {
    return;
  }
  refreshTimer = window.setInterval(requestTaskData, 10000);
};

const stopAutoRefresh = () => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer);
    refreshTimer = null;
  }
};

const applyQueryFilters = () => {
  if (typeof route.query.keyword === 'string') {
    filters.keyword = route.query.keyword;
  }
  if (typeof route.query.status === 'string') {
    filters.status = route.query.status;
  }
  if (typeof route.query.owner === 'string') {
    filters.owner = route.query.owner;
  }
  if (route.query.tags) {
    if (Array.isArray(route.query.tags)) {
      filters.tags = route.query.tags;
    } else if (typeof route.query.tags === 'string') {
      filters.tags = route.query.tags.split(',');
    }
  }
};

onMounted(() => {
  applyQueryFilters();
  stopPermissionWatcher = watch(
    canReadTasks,
    (allowed) => {
      if (!allowed) {
        stopAutoRefresh();
        return;
      }
      handleSearch();
      startAutoRefresh();
    },
    { immediate: true }
  );
});

onBeforeUnmount(() => {
  if (stopPermissionWatcher) {
    stopPermissionWatcher();
  }
  stopAutoRefresh();
});

const handleSearch = async () => {
  if (!ensureReadPermission()) {
    return;
  }
  await store.dispatch('tasks/setFilters', { ...filters });
  try {
    await store.dispatch('tasks/loadMetrics');
  } catch (error) {
    // errors handled via store state
  }

  const query = {
    keyword: filters.keyword || undefined,
    status: filters.status && filters.status !== 'ALL' ? filters.status : undefined,
    owner: filters.owner || undefined,
    tags: filters.tags.length ? filters.tags.join(',') : undefined
  };
  router.replace({ query: { ...route.query, ...query } });
};

const changePage = async (page) => {
  if (!ensureReadPermission(false)) {
    return;
  }
  await store.dispatch('tasks/setPagination', { page });
};

const changeSize = async (size) => {
  if (!ensureReadPermission(false)) {
    return;
  }
  await store.dispatch('tasks/setPagination', { size, page: 1 });
};

const handleDrawerVisible = (visible) => {
  drawerVisible.value = visible;
  if (!visible) {
    currentTaskForm.value = {};
  }
};

const openCreate = () => {
  if (!ensureManagePermission()) {
    return;
  }
  currentTaskForm.value = {};
  drawerVisible.value = true;
};

const editTask = (row) => {
  if (!ensureManagePermission()) {
    return;
  }
  currentTaskForm.value = { ...row };
  drawerVisible.value = true;
};

const submitTask = async (payload) => {
  if (!ensureManagePermission()) {
    return;
  }
  submitting.value = true;
  try {
    await store.dispatch('tasks/submitTask', payload);
    drawerVisible.value = false;
    currentTaskForm.value = {};
    handleSearch();
  } finally {
    submitting.value = false;
  }
};

const toggleTask = async (row) => {
  if (!ensureManagePermission()) {
    return;
  }
  await store.dispatch('tasks/changeTaskStatus', { taskId: row.id, enabled: !row.enabled });
  handleSearch();
};

const removeTask = async (row) => {
  if (!ensureManagePermission()) {
    return;
  }
  await store.dispatch('tasks/removeTask', row.id);
  handleSearch();
};

const triggerTask = async (row) => {
  if (!ensureTriggerPermission()) {
    return;
  }
  await store.dispatch('tasks/triggerTask', { taskId: row.id, payload: {} });
  ElMessage.success('任务执行已请求');
};

const openExecution = async (row) => {
  if (!ensureReadPermission()) {
    return;
  }
  await store.dispatch('tasks/loadTaskExecutions', { taskId: row.id });
  currentTask.value = row;
  executionVisible.value = true;
};

const handleExecutionPage = async (page) => {
  if (!ensureReadPermission(false) || !currentTask.value) {
    return;
  }
  await store.dispatch('tasks/loadTaskExecutions', {
    taskId: currentTask.value.id,
    page,
    size: executionPagination.value.size
  });
};

const openDetail = (row) => {
  if (!ensureReadPermission()) {
    return;
  }
  router.push(`/tasks/${row.id}`);
};

const viewLog = (record) => {
  if (!ensureReadPermission()) {
    return;
  }
  router.push({
    path: `/tasks/${currentTask.value?.id}`,
    query: { recordId: record.id }
  });
};

const openCronHelper = () => {
  router.push({ path: '/config', query: { tab: 'cron-tool' } });
};

const exportTasks = () => {
  if (!ensureReadPermission()) {
    return;
  }
  window.open('/api/tasks/export');
};

const formatType = (type) => {
  const map = {
    CRON: 'Cron定时',
    ONE_TIME: '一次性',
    FIXED_RATE: '固定频率'
  };
  return map[type] || type;
};

const getOwnerInitials = (owner) => {
  if (!owner) {
    return 'NA';
  }
  return owner.slice(0, 2).toUpperCase();
};
</script>

<style scoped lang="scss">
.task-center {
  display: flex;
  flex-direction: column;
  gap: 20px;

  &__alert {
    margin-bottom: 12px;
  }

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 20px;

    .toolbar__left {
      flex: 1;
    }

    .toolbar__right {
      display: flex;
      gap: 12px;
    }
  }

  &__error {
    padding: 32px 0;
  }

  &__content {
    .task-name {
      display: flex;
      align-items: center;
      gap: 6px;

      &__tag {
        background: rgba(34, 211, 238, 0.1);
        color: #0891b2;
      }
    }

    .task-meta {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: #64748b;
      margin-top: 4px;
    }
  }

  &__pagination {
    display: flex;
    justify-content: flex-end;
    padding-top: 16px;
  }

  &__empty {
    display: flex;
    justify-content: center;
    padding: 48px 0;
  }
}

.owner-avatar {
  background-color: #0891b2;
  color: #fff;
  margin-right: 8px;
}
</style>











