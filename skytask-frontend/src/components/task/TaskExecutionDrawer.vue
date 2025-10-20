<template>
  <el-drawer
    :model-value="visible"
    title="执行记录"
    size="40%"
    @close="emit('update:visible', false)"
  >
    <div class="execution">
      <el-table :data="executions" height="360" size="small" v-loading="loading">
        <el-table-column prop="triggerTime" label="调度时间" width="160" />
        <el-table-column prop="node" label="执行节点" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时 (ms)" width="100" />
        <el-table-column prop="retry" label="重试次数" width="100" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="emit('view-log', row)">
              查看日志
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="execution__pagination">
        <el-pagination
          background
          layout="prev, pager, next, ->, jumper"
          :current-page="pagination.page"
          :page-size="pagination.size"
          :total="pagination.total"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import StatusTag from '../common/StatusTag.vue';

defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  executions: {
    type: Array,
    default: () => []
  },
  pagination: {
    type: Object,
    default: () => ({ page: 1, size: 10, total: 0 })
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:visible', 'page-change', 'view-log']);

const handlePageChange = (page) => {
  emit('page-change', page);
};
</script>

<style scoped lang="scss">
.execution {
  display: flex;
  flex-direction: column;
  height: 100%;

  &__pagination {
    display: flex;
    justify-content: flex-end;
    padding-top: 12px;
  }
}
</style>
