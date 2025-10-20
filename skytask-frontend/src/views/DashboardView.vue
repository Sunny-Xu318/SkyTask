<template>
  <div class="dashboard">
    <section class="dashboard__row">
      <el-card
        v-for="card in overviewCards"
        :key="card.key"
        shadow="hover"
        class="dashboard__card"
      >
        <div class="card__header">
          <span class="card__title">{{ card.title }}</span>
          <el-tag :type="card.tagType" size="small">{{ card.tagText }}</el-tag>
        </div>
        <div class="card__metrics">
          <span class="card__value">{{ card.value }}</span>
          <span
            v-if="card.trend !== null"
            :class="['card__trend', { 'card__trend--down': card.trend < 0 }]"
          >
            <el-icon v-if="card.trend >= 0"><CaretTop /></el-icon>
            <el-icon v-else><CaretBottom /></el-icon>
            {{ Math.abs(card.trend).toFixed(1) }}%
          </span>
        </div>
        <div class="card__subtitle">{{ card.subtitle }}</div>
      </el-card>
    </section>

    <section class="dashboard__row">
      <el-card shadow="hover" class="dashboard__panel" v-loading="loadingMetrics">
        <template #header>
          <div class="panel__header">
            <span>过去 24 小时任务执行趋势</span>
            <el-radio-group v-model="metricRange" size="small">
              <el-radio-button label="24h" />
              <el-radio-button label="7d" />
            </el-radio-group>
          </div>
        </template>
        <div v-if="metricError" class="panel__error">
          <el-result
            :status="metricError.forbidden ? 'warning' : 'error'"
            :title="metricError.forbidden ? '无访问权限' : '统计加载失败'"
            :sub-title="metricError.message"
          >
            <template #extra>
              <el-button type="primary" size="small" @click="reloadMetrics">重试</el-button>
            </template>
          </el-result>
        </div>
        <div v-else class="trend-panel">
          <BaseChart
            v-if="trendHasData"
            :options="trendChartOptions"
            class="trend-panel__chart"
          />
          <el-empty v-else description="暂无执行趋势数据" />
        </div>
      </el-card>

      <el-card shadow="hover" class="dashboard__panel" v-loading="loadingMetrics">
        <template #header>
          <div class="panel__header">
            <span>Top 告警任务</span>
            <el-button type="primary" link @click="navigateToTaskCenter">查看全部</el-button>
          </div>
        </template>
        <div v-if="metricError" class="panel__error">
          <el-result
            :status="metricError.forbidden ? 'warning' : 'error'"
            :title="metricError.forbidden ? '无访问权限' : '统计加载失败'"
            :sub-title="metricError.message"
          >
            <template #extra>
              <el-button type="primary" size="small" @click="reloadMetrics">重试</el-button>
            </template>
          </el-result>
        </div>
        <el-table v-else :data="topFailedTasks" size="small" height="300">
          <el-table-column prop="name" label="任务名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="owner" label="负责人" width="120" />
          <el-table-column label="失败次数" width="100">
            <template #default="{ row }">
              <el-tag type="danger" effect="dark">{{ row.failed }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后失败时间" width="160">
            <template #default="{ row }">{{ row.lastFailed }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无告警任务" />
          </template>
        </el-table>
      </el-card>
    </section>

    <section class="dashboard__row">
      <el-card
        shadow="hover"
        class="dashboard__panel"
        v-loading="nodeMetricsLoading || nodesLoading"
      >
        <template #header>
          <div class="panel__header">
            <span>执行器节点健康度</span>
            <div class="panel__header-actions">
              <el-tag :type="nodeHealthTag" size="small">{{ nodesStatusLabel }}</el-tag>
              <el-button type="primary" link size="small" @click="reloadNodes">刷新</el-button>
            </div>
          </div>
        </template>
        <div v-if="nodePanelError" class="panel__error">
          <el-result
            :status="nodePanelError.forbidden ? 'warning' : 'error'"
            :title="nodePanelError.forbidden ? '无访问权限' : '节点数据加载失败'"
            :sub-title="nodePanelError.message"
          >
            <template #extra>
              <el-button type="primary" size="small" @click="reloadNodes">重试</el-button>
            </template>
          </el-result>
        </div>
        <div v-else>
          <div class="nodes-chart-wrapper">
            <BaseChart
              v-if="nodeChartHasData"
              :options="nodeChartOptions"
              class="nodes-chart"
            />
            <el-empty v-else description="暂无节点资源数据" />
          </div>
          <div v-if="nodeList.length" class="nodes">
            <div v-for="node in nodeList" :key="node.id" class="nodes__item">
              <div class="nodes__header">
                <span>{{ node.name }}</span>
                <el-tag :type="node.status === 'ONLINE' ? 'success' : 'danger'" size="small">
                  {{ node.status === 'ONLINE' ? '在线' : '离线' }}
                </el-tag>
              </div>
              <div class="nodes__metrics">
                <div class="nodes__metric">
                  <span>CPU</span>
                  <el-progress :percentage="node.cpu" :status="node.cpu > 80 ? 'exception' : 'success'" />
                </div>
                <div class="nodes__metric">
                  <span>内存</span>
                  <el-progress
                    :percentage="node.memory"
                    :status="node.memory > 80 ? 'exception' : 'warning'"
                  />
                </div>
                <div class="nodes__meta">
                  <span>运行中 {{ node.runningTasks }}</span>
                  <span>延迟 {{ node.delay }}ms</span>
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无执行节点" />
        </div>
      </el-card>

      <el-card shadow="hover" class="dashboard__panel" v-loading="loadingMetrics">
        <template #header>
          <div class="panel__header">
            <span>最新调度事件</span>
          </div>
        </template>
        <div v-if="metricError" class="panel__error">
          <el-result
            :status="metricError.forbidden ? 'warning' : 'error'"
            :title="metricError.forbidden ? '无访问权限' : '统计加载失败'"
            :sub-title="metricError.message"
          >
            <template #extra>
              <el-button type="primary" size="small" @click="reloadMetrics">重试</el-button>
            </template>
          </el-result>
        </div>
        <el-timeline v-else-if="recentEventsHasData">
          <el-timeline-item
            v-for="event in recentEvents"
            :key="event.id"
            :timestamp="event.time"
            :type="event.type"
          >
            <div class="event">
              <div class="event__title">
                <span>{{ event.title }}</span>
                <el-tag :type="event.tagType" size="small">{{ event.tagLabel }}</el-tag>
              </div>
              <div class="event__meta">{{ event.desc }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无调度事件" />
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import { ElMessage } from "element-plus";
import { CaretBottom, CaretTop } from "@element-plus/icons-vue";
import BaseChart from "../components/charts/BaseChart.vue";

const router = useRouter();
const store = useStore();

const metricRange = ref("24h");
const loadingMetrics = ref(false);
const metricError = ref(null);
const nodeMetricsLoading = ref(false);
const nodeMetricsError = ref(null);
const nodeListError = ref(null);

const parseError = (error) => error?.response?.data?.message || error?.message || "请求失败";
const isForbidden = (error) => Number(error?.response?.status) === 403;

const loadTaskMetrics = async (params = {}) => {
  loadingMetrics.value = true;
  try {
    await store.dispatch("tasks/loadMetrics", params);
    metricError.value = null;
  } catch (error) {
    const forbidden = isForbidden(error);
    metricError.value = {
      message: forbidden ? "当前账号无权限查看任务统计" : parseError(error),
      forbidden
    };
    ElMessage[forbidden ? "warning" : "error"](metricError.value.message);
  } finally {
    loadingMetrics.value = false;
  }
};

const loadNodeMetricsData = async () => {
  nodeMetricsLoading.value = true;
  try {
    await store.dispatch("nodes/loadNodeMetrics");
    nodeMetricsError.value = null;
  } catch (error) {
    const forbidden = isForbidden(error);
    nodeMetricsError.value = {
      message: forbidden ? "当前账号无权限查看节点指标" : parseError(error),
      forbidden
    };
    ElMessage[forbidden ? "warning" : "error"](nodeMetricsError.value.message);
  } finally {
    nodeMetricsLoading.value = false;
  }
};

const loadNodesData = async () => {
  try {
    await store.dispatch("nodes/loadNodes");
    nodeListError.value = null;
  } catch (error) {
    const forbidden = isForbidden(error);
    nodeListError.value = {
      message: forbidden ? "当前账号无权限查看节点列表" : parseError(error),
      forbidden
    };
    ElMessage[forbidden ? "warning" : "error"](nodeListError.value.message);
  }
};

const reloadMetrics = () => loadTaskMetrics({ range: metricRange.value });
const reloadNodes = () => Promise.all([loadNodeMetricsData(), loadNodesData()]);

onMounted(async () => {
  await Promise.all([
    loadTaskMetrics({ range: metricRange.value }),
    loadNodeMetricsData(),
    loadNodesData()
  ]);
});

watch(metricRange, (range) => {
  loadTaskMetrics({ range });
});

const metrics = computed(() => store.getters["tasks/taskMetrics"] || {});
const nodeMetrics = computed(() => store.getters["nodes/nodeMetrics"] || {});
const nodeList = computed(() => store.getters["nodes/nodeList"] || []);
const nodesLoading = computed(() => store.getters["nodes/nodesLoading"]);

const normalizedTrend = computed(() =>
  (metrics.value.trend || [])
    .map((item) => ({
      time: item.time ?? item.timestamp ?? "",
      successRate: Number(item.successRate ?? item.success_rate ?? 0),
      failedRate: Number(item.failedRate ?? item.failed_rate ?? 0)
    }))
    .filter((item) => item.time)
);

const trendHasData = computed(() => normalizedTrend.value.length > 0);

const trendChartOptions = computed(() => {
  if (!trendHasData.value) {
    return {};
  }
  const categories = normalizedTrend.value.map((item) => item.time);
  const successSeries = normalizedTrend.value.map((item) => Number(item.successRate.toFixed(2)));
  const failedSeries = normalizedTrend.value.map((item) => Number(item.failedRate.toFixed(2)));
  return {
    color: ["#22c55e", "#ef4444"],
    tooltip: {
      trigger: "axis",
      formatter: (params) =>
        params.map((p) => `${p.marker}${p.seriesName}: ${Number(p.value).toFixed(2)}%`).join("<br/>")
    },
    legend: {
      data: ["成功率", "失败率"]
    },
    grid: { left: 40, right: 24, top: 30, bottom: 40 },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: categories
    },
    yAxis: {
      type: "value",
      min: 0,
      max: 100,
      axisLabel: { formatter: "{value}%" }
    },
    series: [
      {
        name: "成功率",
        type: "line",
        smooth: true,
        areaStyle: { opacity: 0.12 },
        data: successSeries
      },
      {
        name: "失败率",
        type: "line",
        smooth: true,
        areaStyle: { opacity: 0.08 },
        data: failedSeries
      }
    ]
  };
});

const nodeChartHasData = computed(() => nodeList.value.length > 0);

const nodeChartOptions = computed(() => {
  if (!nodeChartHasData.value) {
    return {};
  }
  const names = nodeList.value.map((node) => node.name || node.id);
  return {
    color: ["#38bdf8", "#f97316"],
    tooltip: { trigger: "axis" },
    legend: { data: ["CPU", "内存"] },
    grid: { left: 40, right: 24, top: 30, bottom: 50 },
    xAxis: { type: "category", data: names },
    yAxis: {
      type: "value",
      min: 0,
      max: 100,
      axisLabel: { formatter: "{value}%" }
    },
    series: [
      {
        name: "CPU",
        type: "bar",
        barMaxWidth: 28,
        data: nodeList.value.map((node) => Number(node.cpu ?? 0))
      },
      {
        name: "内存",
        type: "bar",
        barMaxWidth: 28,
        data: nodeList.value.map((node) => Number(node.memory ?? 0))
      }
    ]
  };
});

const successTrendDelta = computed(() => {
  if (!trendHasData.value || normalizedTrend.value.length < 2) {
    return 0;
  }
  const values = normalizedTrend.value.map((item) => item.successRate);
  return Number((values[values.length - 1] - values[0]).toFixed(1));
});

const failureTrendDelta = computed(() => {
  if (!trendHasData.value || normalizedTrend.value.length < 2) {
    return 0;
  }
  const values = normalizedTrend.value.map((item) => item.failedRate);
  return Number((values[values.length - 1] - values[0]).toFixed(1));
});

const overviewCards = computed(() => {
  const totalTasks = metrics.value.totalTasks ?? 0;
  const inactiveTasks = metrics.value.inactiveTasks ?? 0;
  const successRate = Number(metrics.value.successRate ?? 0).toFixed(1);
  const failedToday = metrics.value.failedToday ?? 0;
  const backlog = metrics.value.backlog ?? 0;

  return [
    {
      key: "total",
      title: "任务总数",
      value: totalTasks,
      tagType: "primary",
      tagText: `停用 ${inactiveTasks}`,
      trend: null,
      subtitle: "当前租户下所有任务数量"
    },
    {
      key: "success",
      title: "成功率",
      value: `${successRate}%`,
      tagType: "success",
      tagText: metricRange.value === "7d" ? "最近 7 天" : "最近 24 小时",
      trend: successTrendDelta.value,
      subtitle: successTrendDelta.value === 0 ? "暂无波动" : "相较起始点的变化"
    },
    {
      key: "failed",
      title: "今日失败",
      value: failedToday,
      tagType: "danger",
      tagText: "累计",
      trend: failureTrendDelta.value,
      subtitle: "自动降级会在阈值达到时触发"
    },
    {
      key: "backlog",
      title: "积压任务",
      value: backlog,
      tagType: "warning",
      tagText: "待处理",
      trend: null,
      subtitle: "需要关注的等待或重试任务"
    }
  ];
});

const topFailedTasks = computed(() =>
  (metrics.value.topFailed || []).map((item) => ({
    id: item.id ?? `${item.name}-${item.taskId ?? ''}`,
    name: item.name ?? "-",
    owner: item.owner ?? "-",
    failed: item.failed ?? 0,
    lastFailed: item.lastFailed ?? "-"
  }))
);

const recentEvents = computed(() =>
  (metrics.value.recentEvents || []).map((event) => ({
    id: event.id ?? `${event.title ?? 'event'}-${event.time ?? ''}`,
    time: event.time ?? "-",
    type: event.type ?? "info",
    title: event.title ?? "调度事件",
    tagLabel: event.tagLabel ?? "",
    tagType: event.tagType ?? "info",
    desc: event.desc ?? ""
  }))
);

const recentEventsHasData = computed(() => recentEvents.value.length > 0);

const nodesStatusLabel = computed(() => {
  const online = nodeMetrics.value.onlineNodes ?? 0;
  const total = nodeMetrics.value.totalNodes ?? 0;
  const offline = nodeMetrics.value.offlineNodes ?? 0;
  return `在线 ${online}/${total} | 离线 ${offline}`;
});

const nodeHealthTag = computed(() => (nodeMetrics.value.offlineNodes > 0 ? "danger" : "success"));
const nodePanelError = computed(() => nodeMetricsError.value || nodeListError.value);

const navigateToTaskCenter = () => {
  router.push("/tasks");
};
</script>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;

  &__row {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px;
  }

  &__card {
    border-radius: 16px;

    .card__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }

    .card__title {
      font-size: 16px;
      font-weight: 600;
      color: #0f172a;
    }

    .card__metrics {
      display: flex;
      align-items: baseline;
      gap: 12px;
    }

    .card__value {
      font-size: 28px;
      font-weight: 700;
      color: #111827;
    }

    .card__trend {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: #16a34a;
      font-weight: 500;

      &--down {
        color: #ef4444;
      }
    }

    .card__subtitle {
      margin-top: 6px;
      font-size: 12px;
      color: #64748b;
    }
  }
}

.dashboard__panel {
  border-radius: 16px;
  min-height: 320px;
}

.panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel__header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel__error {
  padding: 32px 0;
}

.trend-panel {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;

  &__chart {
    width: 100%;
    height: 260px;
  }
}

.nodes-chart-wrapper {
  height: 260px;
  margin-bottom: 16px;
}

.nodes-chart {
  width: 100%;
  height: 100%;
}

.nodes {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__item {
    padding: 16px;
    border: 1px solid rgba(15, 23, 42, 0.08);
    border-radius: 12px;
    background: rgba(15, 23, 42, 0.02);
  }

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    font-weight: 600;
    color: #1e293b;
  }

  &__metrics {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  &__metric {
    display: flex;
    flex-direction: column;
    gap: 6px;
    font-size: 13px;
    color: #475569;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    color: #64748b;
    font-size: 13px;
  }
}

.event {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
  }

  &__meta {
    font-size: 12px;
    color: #64748b;
  }
}
</style>

