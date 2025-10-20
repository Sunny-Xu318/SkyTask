<template>
  <el-tag :type="typeMap[status] || 'info'" effect="dark" size="small">
    <slot>{{ labelMap[status] || status }}</slot>
  </el-tag>
</template>

<script setup>
defineProps({
  status: {
    type: String,
    default: 'UNKNOWN'
  }
});

const typeMap = {
  ENABLED: 'success',
  DISABLED: 'info',
  FAILED: 'danger',
  RUNNING: 'warning',
  SUCCESS: 'success',
  SCHEDULED: 'primary',
  DEGRADED: 'warning',
  UNKNOWN: 'info'
};

const labelMap = {
  ENABLED: '启用',
  DISABLED: '禁用',
  FAILED: '失败',
  RUNNING: '执行中',
  SUCCESS: '成功',
  SCHEDULED: '待调度',
  DEGRADED: '异常',
  UNKNOWN: '未知'
};
</script>
