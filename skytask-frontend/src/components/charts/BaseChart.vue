<template>
  <div ref="chartRef" class="base-chart"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';

const props = defineProps({
  options: {
    type: Object,
    required: true
  },
  theme: {
    type: [String, Object],
    default: null
  },
  autoResize: {
    type: Boolean,
    default: true
  }
});

const chartRef = ref(null);
let chartInstance = null;

const renderChart = (opts) => {
  if (!chartRef.value) {
    return;
  }
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value, props.theme || undefined);
  }
  if (opts) {
    chartInstance.setOption(opts, true);
  }
};

const resizeChart = () => {
  if (chartInstance) {
    chartInstance.resize();
  }
};

onMounted(() => {
  renderChart(props.options);
  if (props.autoResize) {
    window.addEventListener('resize', resizeChart);
  }
});

watch(
  () => props.options,
  (opts) => {
    renderChart(opts);
  },
  { deep: true }
);

onBeforeUnmount(() => {
  if (props.autoResize) {
    window.removeEventListener('resize', resizeChart);
  }
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }
});

defineExpose({
  resize: resizeChart,
  getInstance: () => chartInstance
});
</script>

<style scoped>
.base-chart {
  width: 100%;
  height: 100%;
}
</style>
