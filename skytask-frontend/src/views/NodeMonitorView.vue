<template>
  <div class="node-monitor">
        <section v-if="canViewNodes" class="card node-monitor__summary">
      <el-skeleton v-if="metricsLoading && !metricsError" :rows="2" animated />
      <div v-else-if="metricsError" class="summary__error">
        <el-result
          :status="metricsError.forbidden ? 'warning' : 'error'"
          :title="metricsError.forbidden ? '无访问权限' : '指标加载失败'"
          :sub-title="metricsError.message"
        >
          <template #extra>
            <el-button size="small" type="primary" @click="refresh">重试</el-button>
          </template>
        </el-result>
      </div>
      <div v-else class="summary__grid">
        <div class="summary__item">
          <span class="summary__label">Total Nodes</span>
          <span class="summary__value">{{ metrics.totalNodes }}</span>
        </div>
        <div class="summary__item">
          <span class="summary__label">Online</span>
          <span class="summary__value summary__value--success">{{ metrics.onlineNodes }}</span>
        </div>
        <div class="summary__item">
          <span class="summary__label">Offline</span>
          <span class="summary__value summary__value--danger">{{ metrics.offlineNodes }}</span>
        </div>
        <div class="summary__item">
          <span class="summary__label">Avg CPU</span>
          <span class="summary__value">{{ metrics.avgCpu }}%</span>
        </div>
        <div class="summary__item">
          <span class="summary__label">Avg Memory</span>
          <span class="summary__value">{{ metrics.avgMemory }}%</span>
        </div>
      </div>
    </section>

        <section v-if="canViewNodes" class="card node-monitor__table">
      <div class="table__header">
        <h3>Worker Nodes</h3>
        <el-button type="primary" size="small" @click="refresh">Refresh</el-button>
      </div>
      <div v-if="nodesError" class="table__error">
        <el-result
          :status="nodesError.forbidden ? 'warning' : 'error'"
          :title="nodesError.forbidden ? '无访问权限' : '节点列表加载失败'"
          :sub-title="nodesError.message"
        >
          <template #extra>
            <el-button type="primary" size="small" @click="refresh">重试</el-button>
          </template>
        </el-result>
      </div>
      <el-table v-else :data="nodes" v-loading="loading" height="520">
        <el-table-column label="Node" min-width="180">
          <template #default="{ row }">
            <div class="node-name">
              <el-tag size="small" effect="plain">{{ row.cluster }}</el-tag>
              <strong>{{ row.name }}</strong>
            </div>
            <div class="node-meta">
              <span>Host: {{ row.host }}</span>
              <span>Registered: {{ row.registerTime }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status === 'ONLINE' ? 'SUCCESS' : 'FAILED'">
              {{ row.status === 'ONLINE' ? 'Online' : 'Offline' }}
            </StatusTag>
          </template>
        </el-table-column>
        <el-table-column label="Resource" width="240">
          <template #default="{ row }">
            <div class="resource">
              <span>CPU</span>
              <el-progress :percentage="row.cpu" :status="row.cpu > 80 ? 'exception' : 'success'" />
            </div>
            <div class="resource">
              <span>Memory</span>
              <el-progress :percentage="row.memory" :status="row.memory > 80 ? 'exception' : 'warning'" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Runtime" width="220">
          <template #default="{ row }">
            <div class="metrics">
              <span>Running: {{ row.runningTasks }}</span>
              <span>Backlog: {{ row.backlog }}</span>
              <span>Delay: {{ row.delay }} ms</span>
              <span>Alert: {{ row.alertLevel }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canManageNodes"
              link
              type="primary"
              size="small"
              @click="rebalance(row)"
            >
              Rebalance
            </el-button>
            <el-button link type="warning" size="small" @click="openHeartbeat(row)">
              Heartbeat
            </el-button>
            <el-popconfirm
              v-if="canManageNodes"
              title="Offline this node?"
              @confirm="offline(row)"
            >
              <template #reference>
                <el-button link type="danger" size="small">Offline</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无执行节点" />
        </template>
      </el-table>
    </section>

    <section v-else class="card node-monitor__empty">
      <el-empty description="You do not have permission to view nodes" />
    </section>

    <el-drawer v-model="heartbeatVisible" title="Node Heartbeat" size="30%">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="Node">{{ heartbeatDetail.name }}</el-descriptions-item>
        <el-descriptions-item label="Latest">{{ heartbeatDetail.latest }}</el-descriptions-item>
        <el-descriptions-item label="Avg Latency">{{ heartbeatDetail.avgLatency }} ms</el-descriptions-item>
        <el-descriptions-item label="Last Alert">{{ heartbeatDetail.lastAlert || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-timeline class="heartbeat-timeline">
        <el-timeline-item
          v-for="item in heartbeatDetail.logs"
          :key="item.time"
          :timestamp="item.time"
          :type="item.status"
        >
          {{ item.message }}
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useStore } from "vuex";
import { ElMessage } from "element-plus";
import StatusTag from "@/components/common/StatusTag.vue";
import { fetchNodeHeartbeat } from "@/api/nodes";

const store = useStore();

const heartbeatVisible = ref(false);
const heartbeatDetail = ref({
  name: "",
  latest: "",
  avgLatency: 0,
  lastAlert: "",
  logs: []
});

const metricsLoading = ref(false);
const metricsError = ref(null);
const nodesError = ref(null);

const canViewNodes = computed(() => store.getters['auth/hasPermission']('node:read'));
const canManageNodes = computed(() => store.getters['auth/hasPermission']('config:write'));
const metrics = computed(() => store.getters['nodes/nodeMetrics']);
const nodes = computed(() => store.getters['nodes/nodeList']);
const loading = computed(() => store.getters['nodes/nodesLoading']);

const parseError = (error) => error?.response?.data?.message || error?.message || '请求失败';
const isForbidden = (error) => Number(error?.response?.status) === 403;

const loadMetrics = async () => {
  if (!canViewNodes.value) {
    return;
  }
  metricsLoading.value = true;
  try {
    await store.dispatch('nodes/loadNodeMetrics');
    metricsError.value = null;
  } catch (error) {
    const forbidden = isForbidden(error);
    metricsError.value = {
      message: forbidden ? '当前账号无权限查看节点指标' : parseError(error),
      forbidden
    };
    ElMessage[forbidden ? 'warning' : 'error'](metricsError.value.message);
  } finally {
    metricsLoading.value = false;
  }
};

const loadNodes = async () => {
  if (!canViewNodes.value) {
    return;
  }
  try {
    await store.dispatch('nodes/loadNodes');
    nodesError.value = null;
  } catch (error) {
    const forbidden = isForbidden(error);
    nodesError.value = {
      message: forbidden ? '当前账号无权限查看节点列表' : parseError(error),
      forbidden
    };
    ElMessage[forbidden ? 'warning' : 'error'](nodesError.value.message);
  }
};

const refresh = async () => {
  if (!canViewNodes.value) {
    return;
  }
  await Promise.all([loadMetrics(), loadNodes()]);
};

watch(canViewNodes, (allowed) => {
  if (allowed) {
    refresh();
  }
}, { immediate: true });

onMounted(() => {
  if (canViewNodes.value) {
    refresh();
  }
});

const offline = async (node) => {
  if (!canManageNodes.value) {
    ElMessage.warning('没有权限执行下线操作');
    return;
  }
  try {
    await store.dispatch('nodes/decommissionNode', node.id);
    ElMessage.success('节点已下线');
    await refresh();
  } catch (error) {
    ElMessage.error(parseError(error));
  }
};

const rebalance = async (node) => {
  if (!canManageNodes.value) {
    ElMessage.warning('没有权限执行再均衡操作');
    return;
  }
  try {
    await store.dispatch('nodes/rebalance', node.id);
    ElMessage.success('已触发再均衡');
    await refresh();
  } catch (error) {
    ElMessage.error(parseError(error));
  }
};

const openHeartbeat = async (node) => {
  if (!canViewNodes.value) {
    ElMessage.warning('没有权限查看心跳详情');
    return;
  }
  try {
    const detail = await fetchNodeHeartbeat(node.id);
    heartbeatDetail.value = detail;
    heartbeatVisible.value = true;
  } catch (error) {
    ElMessage.error(parseError(error));
  }
};
</script>

<style scoped lang="scss">
.node-monitor {
  display: flex;
  flex-direction: column;
  gap: 20px;

  &__summary {
    padding: 20px 24px;
  }

  &__table {
    padding: 20px;

    .table__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;

      h3 {
        margin: 0;
      }
    }
  }

  &__empty {
    display: flex;
    justify-content: center;
    padding: 48px 0;
  }
}

.summary__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}

.summary__error {
  padding: 16px 0;
}

.summary__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #475569;

  .summary__label {
    font-size: 12px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  .summary__value {
    font-size: 24px;
    font-weight: 700;
    color: #0f172a;

    &--success {
      color: #10b981;
    }

    &--danger {
      color: #ef4444;
    }
  }
}

.table__error {
  padding: 32px 0;
}

.node-name {
  display: flex;
  align-items: center;
  gap: 8px;

  strong {
    color: #0f172a;
  }
}

.node-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #64748b;
  margin-top: 6px;
}

.resource {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #475569;
  margin-bottom: 8px;
}

.metrics {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #475569;
}

.heartbeat-timeline {
  margin-top: 16px;
}
</style>







