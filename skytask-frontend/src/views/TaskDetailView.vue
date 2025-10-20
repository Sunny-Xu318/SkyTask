<template>
  <div class="task-detail">
    <el-page-header @back="goBack">
      <template #content>
        <div class="header">
          <span class="header__title">{{ detail?.name || 'Task Detail' }}</span>
          <StatusTag :status="detail?.enabled ? 'SUCCESS' : 'DISABLED'" />
          <el-tag v-if="detail?.group" type="info" effect="plain">{{ detail.group }}</el-tag>
        </div>
      </template>
      <template #extra>
        <el-space>
          <el-button type="primary" :loading="triggering" @click="triggerTask">Run Now</el-button>
          <el-button @click="openEdit">Edit Task</el-button>
          <el-popconfirm
            title="Delete this task?"
            confirm-button-text="Confirm"
            cancel-button-text="Cancel"
            @confirm="confirmDelete"
          >
            <template #reference>
              <el-button type="danger">Delete</el-button>
            </template>
          </el-popconfirm>
        </el-space>
      </template>
    </el-page-header>

    <div v-loading="detailLoading" class="content">
      <section class="card detail-card">
        <div class="detail-card__section">
          <h3>Basic Information</h3>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="Task ID">{{ detail?.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Task Type">{{ typeLabel(detail?.type) }}</el-descriptions-item>
            <el-descriptions-item label="Executor">{{ executorLabel(detail?.executorType) }}</el-descriptions-item>
            <el-descriptions-item label="Cron Expression">
              <el-tag v-if="detail?.cronExpr" type="success" effect="plain">{{ detail.cronExpr }}</el-tag>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="Owner">{{ detail?.owner || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Timeout">{{ detail?.timeout || 300 }} s</el-descriptions-item>
            <el-descriptions-item label="Created At">{{ detail?.createdAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Updated At">{{ detail?.updatedAt || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-card__section">
          <h3>Scheduling Strategy</h3>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="Route Strategy">{{ routeLabel(detail?.routeStrategy) }}</el-descriptions-item>
            <el-descriptions-item label="Max Retry">{{ detail?.maxRetry ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="Retry Policy">{{ retryLabel(detail?.retryPolicy) }}</el-descriptions-item>
            <el-descriptions-item label="Shards">{{ detail?.shardCount ?? 1 }}</el-descriptions-item>
            <el-descriptions-item label="Idempotent Key">{{ detail?.idempotentKey || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Concurrency">{{ concurrencyLabel(detail?.concurrencyPolicy) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-card__section">
          <h3>Task Parameters</h3>
          <el-input
            v-model="parameterText"
            type="textarea"
            :rows="5"
            readonly
            placeholder="No parameters configured"
          />
        </div>
      </section>

      <section class="card chart-card">
        <div class="chart-card__header">
          <h3>Execution Success Trend</h3>
          <el-radio-group v-model="chartRange" size="small" @change="loadExecutions">
            <el-radio-button label="24h">24h</el-radio-button>
            <el-radio-button label="7d">7d</el-radio-button>
            <el-radio-button label="30d">30d</el-radio-button>
          </el-radio-group>
        </div>
        <div v-loading="executionLoading" class="chart-content">
          <BaseChart v-if="hasExecutionData" :options="successTrendOptions" class="chart" />
          <el-empty v-else description="No execution data yet" />
        </div>
      </section>

      <section class="card chart-card">
        <div class="chart-card__header">
          <h3>Execution Duration (latest 20)</h3>
          <el-tag size="small" type="info">Latest 20 executions</el-tag>
        </div>
        <div v-loading="executionLoading" class="chart-content">
          <BaseChart v-if="hasExecutionData" :options="durationChartOptions" class="chart" />
          <el-empty v-else description="No execution data yet" />
        </div>
      </section>

      <section class="card execution-card">
        <div class="execution-card__header">
          <h3>Recent Executions</h3>
          <el-space>
            <el-tag size="small">
              Success: {{ executionStats.success }} | Failed: {{ executionStats.failed }}
            </el-tag>
            <el-button size="small" @click="loadExecutions">Refresh</el-button>
          </el-space>
        </div>
        <el-table :data="executions" height="320" v-loading="executionLoading">
          <el-table-column prop="triggerTime" label="Trigger Time" width="160" />
          <el-table-column prop="node" label="Node" width="140" show-overflow-tooltip />
          <el-table-column label="Status" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.status || 'UNKNOWN'" />
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="Duration" width="110">
            <template #default="{ row }">
              <el-tag :type="getDurationTagType(row.duration)" size="small">
                {{ row.duration || 0 }} ms
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="retry" label="Retry" width="80" />
          <el-table-column prop="traceId" label="Trace ID" min-width="160" show-overflow-tooltip />
          <el-table-column label="Action" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openLog(row)">View Log</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="No execution records" />
          </template>
        </el-table>
      </section>

      <section class="card dependency-card">
        <div class="dependency-card__header">
          <h3>Upstream Dependencies</h3>
          <el-tag v-if="detail?.dependencies?.length" size="small" type="info">
            {{ detail.dependencies.length }} tasks
          </el-tag>
        </div>
        <TaskDependencyGraph
          :task-name="detail?.name"
          :dependencies="detail?.dependencies || []"
        />
        <el-timeline v-if="detail?.dependencies?.length" class="dependency-timeline">
          <el-timeline-item
            v-for="dep in detail.dependencies"
            :key="dep.id"
            :timestamp="dep.triggerType || 'Manual'"
            :type="dep.status === 'SUCCESS' ? 'success' : dep.status === 'FAILED' ? 'danger' : 'info'"
          >
            <div class="dependency-item">
              <strong>{{ dep.name }}</strong>
              <span>Node: {{ dep.node || '-' }}</span>
              <span>Status: <StatusTag :status="dep.status || 'UNKNOWN'" /></span>
              <el-tag v-if="dep.cronExpr" size="small" effect="plain">{{ dep.cronExpr }}</el-tag>
            </div>
          </el-timeline-item>
        </el-timeline>
      </section>
    </div>

    <TaskFormDrawer
      v-model:visible="drawerVisible"
      :model-value="detail"
      :group-options="groupOptions"
      :owner-options="ownerOptions"
      :tag-options="tagOptions"
      :submitting="submitting"
      @submit="submitTask"
      @update:visible="onDrawerVisible"
      @cron-helper="openCronHelper"
    />

    <el-drawer
      v-model="logVisible"
      title="Execution Log"
      size="40%"
    >
      <template #default>
        <pre class="log">{{ activeLog || 'No log content' }}</pre>
      </template>
      <template #footer>
        <el-space>
          <el-button type="primary" @click="copyLog" :disabled="!activeLog">Copy</el-button>
          <el-button @click="logVisible = false">Close</el-button>
        </el-space>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { ElMessage } from 'element-plus';
import StatusTag from '@/components/common/StatusTag.vue';
import TaskFormDrawer from '@/components/task/TaskFormDrawer.vue';
import TaskDependencyGraph from '@/components/task/TaskDependencyGraph.vue';
import BaseChart from '@/components/charts/BaseChart.vue';

const store = useStore();
const route = useRoute();
const router = useRouter();

const taskId = route.params.taskId;
const drawerVisible = ref(false);
const submitting = ref(false);
const logVisible = ref(false);
const activeLog = ref('');
const triggering = ref(false);
const detailLoading = ref(false);
const chartRange = ref('24h');

const groupOptions = [
  { label: 'Data Report', value: 'DATA_REPORT' },
  { label: 'Realtime Risk', value: 'RISK_CONTROL' },
  { label: 'Marketing', value: 'MARKETING' },
  { label: 'Recommendation', value: 'RECOMMEND' }
];

const ownerOptions = [
  { label: 'Alice - Data Platform', value: 'alice' },
  { label: 'Bob - Marketing', value: 'bob' },
  { label: 'Carol - Settlement', value: 'carol' },
  { label: 'David - Recommendation', value: 'david' }
];

const tagOptions = ['report', 'risk', 'cache', 'recommend', 'marketing', 'settlement'];

const parseError = (error) =>
  error?.response?.data?.message || error?.message || 'Operation failed, please try again later.';

onMounted(async () => {
  detailLoading.value = true;
  try {
    await store.dispatch('tasks/loadTaskDetail', taskId);
    await loadExecutions();
  } catch (error) {
    ElMessage.error(parseError(error));
  } finally {
    detailLoading.value = false;
  }
});

const detail = computed(() => store.getters['tasks/currentTask'] || {});
const executions = computed(() => store.getters['tasks/taskExecutions'] || []);
const executionLoading = computed(() => store.getters['tasks/taskExecutionLoading']);

const parameterText = computed(() => {
  const params = detail.value?.parameters;
  if (!params) return 'No parameters configured';
  if (typeof params === 'string') return params;
  try {
    return JSON.stringify(params, null, 2);
  } catch (error) {
    return String(params);
  }
});

const hasExecutionData = computed(
  () => Array.isArray(executions.value) && executions.value.length > 0
);

const executionStats = computed(() => {
  if (!hasExecutionData.value) return { success: 0, failed: 0 };
  const success = executions.value.filter((e) => e.status === 'SUCCESS').length;
  const failed = executions.value.filter((e) => e.status === 'FAILED').length;
  return { success, failed };
});

const successTrendOptions = computed(() => {
  if (!hasExecutionData.value) return {};

  const sortedExecs = [...executions.value].sort(
          (a, b) => new Date(a.triggerTime) - new Date(b.triggerTime));
  const categories = sortedExecs.map((e) => e.triggerTime?.substring(5, 16) || '');
  const successData = sortedExecs.map((e) => (e.status === 'SUCCESS' ? 100 : 0));
  const failedData = sortedExecs.map((e) => (e.status === 'FAILED' ? 100 : 0));

  return {
    color: ['#22c55e', '#ef4444'],
    tooltip: {
      trigger: 'axis',
      formatter: (params) =>
        params.map((p) => `${p.marker}${p.seriesName}: ${p.value}%`).join('<br/>')
    },
    legend: {
      data: ['Success', 'Failed']
    },
    grid: { left: 50, right: 24, top: 40, bottom: 50 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { rotate: 45, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%' }
    },
    series: [
      {
        name: 'Success',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: successData
      },
      {
        name: 'Failed',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: failedData
      }
    ]
  };
});

const durationChartOptions = computed(() => {
  if (!hasExecutionData.value) return {};

  const sortedExecs = [...executions.value]
    .sort((a, b) => new Date(a.triggerTime) - new Date(b.triggerTime))
    .slice(-20);

  const categories = sortedExecs.map((e) => e.triggerTime?.substring(11, 16) || '');
  const durations = sortedExecs.map((e) => Number(e.duration || 0));

  return {
    color: ['#f97316'],
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0];
        return `${p.axisValue}<br/>${p.marker}Duration: ${p.value} ms`;
      }
    },
    grid: { left: 60, right: 24, top: 30, bottom: 60 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { rotate: 45, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { formatter: '{value} ms' }
    },
    series: [
      {
        name: 'Duration',
        type: 'bar',
        barMaxWidth: 30,
        data: durations,
        itemStyle: {
          color: (params) => {
            const val = params.value;
            if (val < 1000) return '#22c55e';
            if (val < 5000) return '#f59e0b';
            return '#ef4444';
          }
        }
      }
    ]
  };
});

const loadExecutions = async () => {
  try {
    await store.dispatch('tasks/loadTaskExecutions', {
      taskId,
      page: 1,
      size: 50,
      range: chartRange.value
    });
  } catch (error) {
    ElMessage.error(parseError(error));
  }
};

const goBack = () => {
  router.back();
};

const openEdit = () => {
  drawerVisible.value = true;
};

const onDrawerVisible = (visible) => {
  drawerVisible.value = visible;
  if (!visible) {
    submitting.value = false;
  }
};

const submitTask = async (payload) => {
  submitting.value = true;
  try {
    await store.dispatch('tasks/submitTask', payload);
    await store.dispatch('tasks/loadTaskDetail', taskId);
    drawerVisible.value = false;
    ElMessage.success('Task updated');
  } catch (error) {
    ElMessage.error(parseError(error));
  } finally {
    submitting.value = false;
  }
};

const triggerTask = async () => {
  if (!detail.value?.id || triggering.value) {
    return;
  }
  triggering.value = true;
  try {
    await store.dispatch('tasks/triggerTask', {
      taskId,
      payload: {
        manual: true,
        operator: store.getters['auth/profile']?.username || 'anonymous'
      }
    });
    ElMessage.success('Execution triggered');
    setTimeout(loadExecutions, 2000);
  } catch (error) {
    ElMessage.error(parseError(error));
  } finally {
    triggering.value = false;
  }
};

const confirmDelete = async () => {
  try {
    await store.dispatch('tasks/removeTask', taskId);
    ElMessage.success('Task deleted');
    router.push('/tasks');
  } catch (error) {
    ElMessage.error(parseError(error));
  }
};

const openLog = (record) => {
  logVisible.value = true;
  activeLog.value = record.log || 'No log content';
};

const copyLog = () => {
  if (!activeLog.value) {
    return;
  }
  navigator.clipboard
    .writeText(activeLog.value)
    .then(() => ElMessage.success('Copied to clipboard'))
    .catch(() => ElMessage.error('Copy failed'));
};

const openCronHelper = () => {
  router.push({ path: '/config', query: { tab: 'cron-tool' } });
};

const getDurationTagType = (duration) => {
  const value = Number(duration || 0);
  if (value < 1000) return 'success';
  if (value < 5000) return 'warning';
  return 'danger';
};

const typeLabel = (type) => {
  const map = {
    CRON: 'Cron',
    ONE_TIME: 'One-time',
    FIXED_RATE: 'Fixed Rate'
  };
  return map[type] || type || '-';
};

const executorLabel = (executor) => {
  const map = {
    HTTP: 'HTTP',
    GRPC: 'gRPC',
    SPRING_BEAN: 'Spring Bean',
    SHELL: 'Shell'
  };
  return map[executor] || executor || '-';
};

const routeLabel = (route) => {
  const map = {
    ROUND_ROBIN: 'Round Robin',
    CONSISTENT_HASH: 'Consistent Hash',
    SHARDING: 'Sharding',
    FIXED_NODE: 'Fixed Node'
  };
  return map[route] || route || '-';
};

const retryLabel = (retry) => {
  const map = {
    NONE: 'None',
    FIXED_INTERVAL: 'Fixed Interval',
    EXP_BACKOFF: 'Exponential Backoff'
  };
  return map[retry] || retry || 'Fixed Interval';
};

const concurrencyLabel = (policy) => {
  const map = {
    PARALLEL: 'Parallel',
    SERIAL: 'Serial',
    DISCARD: 'Discard'
  };
  return map[policy] || policy || 'Parallel';
};
</script>

<style scoped lang="scss">
.task-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.header {
  display: flex;
  align-items: center;
  gap: 12px;

  &__title {
    font-size: 20px;
    font-weight: 600;
  }
}

.content {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.detail-card {
  grid-column: span 2;
  display: flex;
  flex-direction: column;
  gap: 24px;

  &__section {
    h3 {
      margin: 0 0 16px;
      font-size: 16px;
      font-weight: 600;
      color: #0f172a;
    }
  }
}

.chart-card,
.execution-card,
.dependency-card {
  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #0f172a;
  }
}

.chart-card__header,
.execution-card__header,
.dependency-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.chart-content {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart {
  width: 100%;
  height: 280px;
}

.execution-card {
  grid-column: span 2;
}

.dependency-card {
  grid-column: span 2;
}

.dependency-timeline {
  margin-top: 16px;
}

.dependency-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #475569;
  flex-wrap: wrap;

  strong {
    font-weight: 600;
    color: #1e293b;
  }
}

.log {
  margin: 0;
  font-family: 'Fira Code', 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  background: #0f172a;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 8px;
}
</style>
