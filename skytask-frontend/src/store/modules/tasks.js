import {
  fetchTaskList,
  fetchTaskStatistics,
  createTask,
  updateTask,
  toggleTaskEnabled,
  deleteTask,
  runTaskNow,
  fetchTaskExecutions,
  fetchTaskDetail
} from '../../api/tasks';

const state = () => ({
  list: [],
  filters: {
    keyword: '',
    status: 'ALL',
    owner: null,
    tags: []
  },
  pagination: {
    page: 1,
    size: 10,
    total: 0
  },
  loading: false,
  metrics: {
    totalTasks: 0,
    inactiveTasks: 0,
    successRate: 0,
    failedToday: 0,
    backlog: 0
  },
  metricsError: null,
  listError: null,
  currentTask: null,
  executions: [],
  executionPagination: {
    page: 1,
    size: 10,
    total: 0
  },
  executionLoading: false
});

const mutations = {
  SET_LOADING(state, loading) {
    state.loading = loading;
  },
  SET_METRICS(state, metrics) {
    state.metrics = { ...state.metrics, ...metrics };
  },
  SET_METRICS_ERROR(state, error) {
    state.metricsError = error;
  },
  SET_TASK_LIST(state, payload) {
    const records = payload.records || payload.items || [];
    state.list = records;
    state.pagination.page = payload.page ?? payload.current ?? state.pagination.page;
    state.pagination.size = payload.size ?? payload.pageSize ?? state.pagination.size;
    state.pagination.total = payload.total ?? payload.totalCount ?? records.length;
  },
  SET_LIST_ERROR(state, error) {
    state.listError = error;
  },
  SET_PAGINATION(state, pagination) {
    state.pagination = { ...state.pagination, ...pagination };
  },
  SET_FILTERS(state, filters) {
    state.filters = { ...state.filters, ...filters };
  },
  SET_CURRENT_TASK(state, task) {
    state.currentTask = task;
  },
  SET_EXECUTIONS(state, payload) {
    const records = payload.records || payload.items || [];
    state.executions = records;
    state.executionPagination.page = payload.page ?? payload.current ?? state.executionPagination.page;
    state.executionPagination.size = payload.size ?? payload.pageSize ?? state.executionPagination.size;
    state.executionPagination.total =
      payload.total ?? payload.totalCount ?? records.length;
  },
  SET_EXECUTION_LOADING(state, loading) {
    state.executionLoading = loading;
  }
};

const actions = {
    async loadMetrics({ commit }, params = {}) {
    try {
      const metrics = await fetchTaskStatistics(params);
      commit('SET_METRICS', metrics || {});
      commit('SET_METRICS_ERROR', null);
      return metrics;
    } catch (error) {
      commit('SET_METRICS_ERROR', error);
      throw error;
    }
  },
    async loadTaskList({ commit, state }, overrides = {}) {
    commit('SET_LOADING', true);
    try {
      const query = {
        page: overrides.page || state.pagination.page,
        size: overrides.size || state.pagination.size,
        ...state.filters,
        ...overrides
      };
      if (Array.isArray(query.tags)) {
        query.tags = query.tags.join(',');
      }
      const data = await fetchTaskList(query);
      commit('SET_TASK_LIST', data || {});
      commit('SET_LIST_ERROR', null);
    } catch (error) {
      commit('SET_LIST_ERROR', error);
      throw error;
    } finally {
      commit('SET_LOADING', false);
    }
  },
  async loadTaskDetail({ commit }, taskId) {
    const detail = await fetchTaskDetail(taskId);
    commit('SET_CURRENT_TASK', detail);
    return detail;
  },
  async submitTask({ dispatch }, payload) {
    if (payload.id) {
      await updateTask(payload.id, payload);
    } else {
      await createTask(payload);
    }
    await dispatch('loadTaskList');
  },
  async changeTaskStatus({ dispatch }, { taskId, enabled }) {
    await toggleTaskEnabled(taskId, enabled);
    await dispatch('loadTaskList');
  },
  async removeTask({ dispatch }, taskId) {
    await deleteTask(taskId);
    await dispatch('loadTaskList');
  },
  async triggerTask({ dispatch }, { taskId, payload }) {
    await runTaskNow(taskId, payload);
    await dispatch('loadTaskExecutions', { taskId });
  },
  async loadTaskExecutions({ commit }, { taskId, page = 1, size = 10, range = null }) {
    commit('SET_EXECUTION_LOADING', true);
    try {
      const params = { page, size };
      if (range) {
        params.range = range;
      }
      const data = await fetchTaskExecutions(taskId, params);
      commit('SET_EXECUTIONS', data);
    } finally {
      commit('SET_EXECUTION_LOADING', false);
    }
  },
  setFilters({ commit, dispatch }, filters) {
    commit('SET_FILTERS', filters);
    commit('SET_PAGINATION', { page: 1 });
    dispatch('loadTaskList', { page: 1 });
  },
  setPagination({ dispatch }, pagination) {
    dispatch('updatePaginationAndLoad', pagination);
  },
  updatePaginationAndLoad({ commit, dispatch }, pagination) {
    commit('SET_PAGINATION', pagination);
    dispatch('loadTaskList', pagination);
  }
};

const getters = {
  taskMetrics: (state) => state.metrics,
  taskMetricsError: (state) => state.metricsError,
  taskList: (state) => state.list,
  taskListError: (state) => state.listError,
  taskFilters: (state) => state.filters,
  taskPagination: (state) => state.pagination,
  tasksLoading: (state) => state.loading,
  currentTask: (state) => state.currentTask,
  taskExecutions: (state) => state.executions,
  taskExecutionPagination: (state) => state.executionPagination,
  taskExecutionLoading: (state) => state.executionLoading
};

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters
};


