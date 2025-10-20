<template>
  <div class="dependency-graph">
    <BaseChart
      v-if="hasGraph"
      :options="chartOptions"
      class="dependency-graph__chart"
    />
    <el-empty v-else description="暂无依赖信息" />
  </div>
</template>

<script setup>
import { computed } from 'vue';
import BaseChart from '../charts/BaseChart.vue';

const props = defineProps({
  taskName: {
    type: String,
    default: ''
  },
  dependencies: {
    type: Array,
    default: () => []
  }
});

const hasGraph = computed(() => Array.isArray(props.dependencies) && props.dependencies.length > 0);

const chartOptions = computed(() => {
  if (!hasGraph.value) {
    return {};
  }

  const nodes = [];
  const links = [];
  const rootId = 'root';

  nodes.push({
    id: rootId,
    name: props.taskName || '当前任务',
    symbolSize: 64,
    itemStyle: { color: '#2563eb' },
    label: { fontWeight: 'bold' }
  });

  props.dependencies.forEach((dep, index) => {
    const nodeId = String(dep.id ?? index);
    const status = (dep.status || 'UNKNOWN').toUpperCase();
    let color = '#94a3b8';
    if (status === 'SUCCESS') color = '#22c55e';
    else if (status === 'FAILED') color = '#ef4444';
    else if (status === 'RUNNING') color = '#f97316';

    nodes.push({
      id: nodeId,
      name: dep.name || `任务 ${index + 1}`,
      symbolSize: 48,
      itemStyle: { color },
      value: status,
      tooltip: {
        formatter: () => {
          const parts = [
            `<strong>${dep.name || `任务 ${index + 1}`}</strong>`,
            `状态: ${status}`,
            dep.cronExpr ? `Cron: ${dep.cronExpr}` : null,
            dep.node ? `节点: ${dep.node}` : null
          ].filter(Boolean);
          return parts.join('<br/>');
        }
      }
    });
    links.push({ source: rootId, target: nodeId });
  });

  return {
    tooltip: { trigger: 'item', enterable: true },
    legend: { show: false },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        focusNodeAdjacency: true,
        draggable: true,
        data: nodes,
        links,
        force: {
          repulsion: 1400,
          edgeLength: 140,
          gravity: 0.2
        },
        lineStyle: {
          color: '#94a3b8',
          width: 2,
          curveness: 0.2
        },
        label: {
          color: '#0f172a'
        }
      }
    ]
  };
});
</script>

<style scoped>
.dependency-graph {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

.dependency-graph__chart {
  width: 100%;
  height: 320px;
}
</style>
