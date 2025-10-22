<template>
  <div class="task-detail">
    <el-page-header @back="goBack">
      <template #content>
        <div class="header">
          <span class="header__title">{{ detail?.name || '任务详情' }}</span>
          <StatusTag :status="detail?.enabled ? 'SUCCESS' : 'DISABLED'" />
          <el-tag v-if="detail?.group" type="info" effect="plain">{{ detail.group }}</el-tag>
        </div>
      </template>
      <template #extra>
        <el-space>
          <el-button type="primary" :loading="triggering" @click="triggerTask">立即运行</el-button>
          <el-button @click="openEdit">编辑任务</el-button>
          <el-popconfirm
            title="确定删除此任务吗？"
            confirm-button-text="确认"
            cancel-button-text="取消"
            @confirm="confirmDelete"
          >
            <template #reference>
              <el-button type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </el-space>
      </template>
    </el-page-header>

    <div v-loading="detailLoading" class="content">
      <section class="card detail-card">
        <div class="detail-card__section">
          <h3>基本信息</h3>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="任务ID">{{ detail?.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="任务类型">{{ typeLabel(detail?.type) }}</el-descriptions-item>
            <el-descriptions-item label="执行器">{{ executorLabel(detail?.executorType) }}</el-descriptions-item>
            <el-descriptions-item label="Cron表达式">
              <el-tag v-if="detail?.cronExpr" type="success" effect="plain">{{ detail.cronExpr }}</el-tag>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail?.owner || '-' }}</el-descriptions-item>
            <el-descriptions-item label="超时时间">{{ detail?.timeout || 300 }} 秒</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail?.createdAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ detail?.updatedAt || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-card__section">
          <h3>调度策略</h3>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="路由策略">{{ routeLabel(detail?.routeStrategy) }}</el-descriptions-item>
            <el-descriptions-item label="最大重试次数">{{ detail?.maxRetry ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="重试策略">{{ retryLabel(detail?.retryPolicy) }}</el-descriptions-item>
            <el-descriptions-item label="分片数">{{ detail?.shardCount ?? 1 }}</el-descriptions-item>
            <el-descriptions-item label="幂等键">{{ detail?.idempotentKey || '-' }}</el-descriptions-item>
            <el-descriptions-item label="并发策略">{{ concurrencyLabel(detail?.concurrencyPolicy) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-card__section">
          <h3>任务参数</h3>
          <el-input
            v-model="parameterText"
            type="textarea"
            :rows="5"
            readonly
            placeholder="未配置参数"
          />
        </div>
      </section>

      <section class="card chart-card">
        <div class="chart-card__header">
          <h3>执行成功趋势</h3>
          <el-radio-group v-model="chartRange" size="small" @change="loadExecutions">
            <el-radio-button label="24h">24小时</el-radio-button>
            <el-radio-button label="7d">7天</el-radio-button>
            <el-radio-button label="30d">30天</el-radio-button>
          </el-radio-group>
        </div>
        <div v-loading="executionLoading" class="chart-content">
          <BaseChart v-if="hasExecutionData" :options="successTrendOptions" class="chart" />
          <el-empty v-else description="暂无执行数据" />
        </div>
      </section>

      <section class="card chart-card">
        <div class="chart-card__header">
          <h3>执行耗时（最近20次）</h3>
          <el-tag size="small" type="info">最近20次执行</el-tag>
        </div>
        <div v-loading="executionLoading" class="chart-content">
          <BaseChart v-if="hasExecutionData" :options="durationChartOptions" class="chart" />
          <el-empty v-else description="暂无执行数据" />
        </div>
      </section>

      <section class="card execution-card">
        <div class="execution-card__header">
          <h3>最近执行记录</h3>
          <el-space>
            <el-tag size="small">
              成功: {{ executionStats.success }} | 失败: {{ executionStats.failed }}
            </el-tag>
            <el-button size="small" @click="loadExecutions">刷新</el-button>
          </el-space>
        </div>
        <el-table :data="executions" height="320" v-loading="executionLoading">
          <el-table-column prop="triggerTime" label="触发时间" width="160" />
          <el-table-column prop="node" label="执行节点" width="140" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.status || 'UNKNOWN'" />
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时" width="110">
            <template #default="{ row }">
              <el-tag :type="getDurationTagType(row.duration)" size="small">
                {{ row.duration || 0 }} ms
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="retry" label="重试" width="80" />
          <el-table-column prop="traceId" label="追踪ID" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openLog(row)">查看日志</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无执行记录" />
          </template>
        </el-table>
      </section>

      <section class="card dependency-card">
        <div class="dependency-card__header">
          <h3>上游依赖</h3>
          <el-tag v-if="detail?.dependencies?.length" size="small" type="info">
            {{ detail.dependencies.length }} 个任务
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
            :timestamp="dep.triggerType || '手动'"
            :type="dep.status === 'SUCCESS' ? 'success' : dep.status === 'FAILED' ? 'danger' : 'info'"
          >
            <div class="dependency-item">
              <strong>{{ dep.name }}</strong>
              <span>节点: {{ dep.node || '-' }}</span>
              <span>状态: <StatusTag :status="dep.status || 'UNKNOWN'" /></span>
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
      title="执行日志"
      size="40%"
    >
      <template #default>
        <pre class="log">{{ activeLog || '暂无日志内容' }}</pre>
      </template>
      <template #footer>
        <el-space>
          <el-button type="primary" @click="copyLog" :disabled="!activeLog">复制</el-button>
          <el-button @click="logVisible = false">关闭</el-button>
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
import dayjs from 'dayjs';
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
  error?.response?.data?.message || error?.message || '操作失败，请稍后重试';

// 统一的日期转换函数
const parseTriggerTime = (triggerTime) => {
  if (!triggerTime) {
    return null;
  }
  
  // 如果已经是字符串，直接返回
  if (typeof triggerTime === 'string') {
    return triggerTime;
  }
  
  // 如果是对象（OffsetDateTime 序列化结果），尝试转换
  if (typeof triggerTime === 'object') {
    const { year, monthValue, month, dayOfMonth, day, hour, minute, second } = triggerTime;
    
    const y = year;
    let m = monthValue || month;
    const d = dayOfMonth || day;
    const h = hour || 0;
    const min = minute || 0;
    const s = second || 0;
    
    // 如果 month 是字符串（如"OCTOBER"），转换为数字
    if (typeof m === 'string') {
      const monthMap = {
        'JANUARY': 1, 'FEBRUARY': 2, 'MARCH': 3, 'APRIL': 4,
        'MAY': 5, 'JUNE': 6, 'JULY': 7, 'AUGUST': 8,
        'SEPTEMBER': 9, 'OCTOBER': 10, 'NOVEMBER': 11, 'DECEMBER': 12
      };
      m = monthMap[m.toUpperCase()] || parseInt(m);
    }
    
    if (y && m && d) {
      try {
        // 使用 JavaScript Date 对象构建日期，确保兼容性
        const date = dayjs(new Date(y, m - 1, d, h, min, s));
        
        if (date.isValid()) {
          return date.format('YYYY-MM-DDTHH:mm:ss');
        }
      } catch (error) {
        console.error('日期构建异常:', error);
      }
    }
  }
  
  return String(triggerTime);
};

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
  if (!params) return '未配置参数';
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

  // 将所有执行记录的 triggerTime 转换为标准字符串格式
  const normalizedExecs = executions.value.map(exec => ({
    ...exec,
    triggerTime: parseTriggerTime(exec.triggerTime)
  }));

  // 按时间排序
  const sortedExecs = [...normalizedExecs].sort((a, b) => {
    if (!a.triggerTime) return 1;
    if (!b.triggerTime) return -1;
    return a.triggerTime.localeCompare(b.triggerTime);
  });
  
  // 根据时间范围动态格式化横坐标
  const formatTimeLabel = (timeString) => {
    if (!timeString) return '无时间';
    
    try {
      const date = dayjs(timeString);
      
      if (!date.isValid()) {
        // 降级处理：尝试提取日期部分
        if (typeof timeString === 'string' && timeString.length >= 10) {
          return timeString.substring(5, 10); // MM-DD
        }
        return '无效';
      }
      
      // 根据时间范围选择格式
        switch (chartRange.value) {
          case '24h':
          return date.format('HH:mm');
          case '7d':
          return date.format('MM-DD HH:mm');
          case '30d':
          return date.format('MM-DD');
          default:
          return date.format('MM-DD HH:mm');
      }
    } catch (error) {
      console.error('日期格式化错误:', error);
      return '错误';
    }
  };
  
  const categories = sortedExecs.map(e => formatTimeLabel(e.triggerTime));
  const successData = sortedExecs.map((e) => (e.status === 'SUCCESS' ? 100 : 0));
  const failedData = sortedExecs.map((e) => (e.status === 'FAILED' ? 100 : 0));

  // 根据时间范围调整标签旋转角度
  const labelRotation = chartRange.value === '30d' ? 45 : 0;

  return {
    color: ['#22c55e', '#ef4444'],
    tooltip: {
      trigger: 'axis',
      formatter: (params) =>
        params.map((p) => `${p.marker}${p.seriesName}: ${p.value}%`).join('<br/>')
    },
    legend: {
      data: ['成功', '失败']
    },
    grid: { left: 50, right: 24, top: 40, bottom: 50 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { 
        rotate: labelRotation, 
        fontSize: 11,
        interval: chartRange.value === '30d' ? 'auto' : 0
      }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%' }
    },
    series: [
      {
        name: '成功',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: successData
      },
      {
        name: '失败',
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

  // 转换并排序
  const normalizedExecs = executions.value
    .map(exec => ({
      ...exec,
      triggerTime: parseTriggerTime(exec.triggerTime)
    }))
    .filter(e => e.triggerTime)
    .sort((a, b) => a.triggerTime.localeCompare(b.triggerTime))
    .slice(-20);

  const categories = normalizedExecs.map((e) => dayjs(e.triggerTime).format('HH:mm'));
  const durations = normalizedExecs.map((e) => Number(e.duration || 0));

  return {
    color: ['#f97316'],
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0];
        return `${p.axisValue}<br/>${p.marker}耗时: ${p.value} ms`;
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
        name: '耗时',
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
    ElMessage.success('任务已更新');
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
    ElMessage.success('执行已触发');
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
    ElMessage.success('任务已删除');
    router.push('/tasks');
  } catch (error) {
    ElMessage.error(parseError(error));
  }
};

const openLog = (record) => {
  logVisible.value = true;
  
  // 构建详细的日志信息
  const logInfo = {
    '执行记录ID': record.id || '-',
    '任务ID': record.taskId || '-',
    '触发时间': record.triggerTime || '-',
    '执行节点': record.node || '-',
    '执行状态': record.status || '-',
    '执行耗时': record.duration ? `${record.duration}ms` : '-',
    '重试次数': record.retry || 0,
    'Trace ID': record.traceId || '-',
    '执行结果': record.log || 'No execution result',
    '返回参数': record.parameters ? JSON.stringify(record.parameters, null, 2) : 'No parameters returned'
  };
  
  // 格式化日志显示
  const formattedLog = Object.entries(logInfo)
    .map(([key, value]) => `【${key}】\n${value}`)
    .join('\n\n' + '='.repeat(50) + '\n\n');
  
  activeLog.value = formattedLog;
};

const copyLog = () => {
  if (!activeLog.value) {
    return;
  }
  navigator.clipboard
    .writeText(activeLog.value)
    .then(() => ElMessage.success('已复制到剪贴板'))
    .catch(() => ElMessage.error('复制失败'));
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
    CRON: 'Cron定时',
    ONE_TIME: '一次性',
    FIXED_RATE: '固定频率'
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
    ROUND_ROBIN: '轮询',
    CONSISTENT_HASH: '一致性哈希',
    SHARDING: '分片',
    FIXED_NODE: '固定节点'
  };
  return map[route] || route || '-';
};

const retryLabel = (retry) => {
  const map = {
    NONE: '不重试',
    FIXED_INTERVAL: '固定间隔',
    EXP_BACKOFF: '指数退避'
  };
  return map[retry] || retry || '固定间隔';
};

const concurrencyLabel = (policy) => {
  const map = {
    PARALLEL: '并行',
    SERIAL: '串行',
    DISCARD: '丢弃'
  };
  return map[policy] || policy || '并行';
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
