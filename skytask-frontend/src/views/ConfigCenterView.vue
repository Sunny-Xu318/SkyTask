<template>
  <div class="config-center-wrapper">
    <div v-if="canManageConfig" class="config-center">
      <section class="card config-center__section">
        <div class="section__header">
          <h3>全局调度配置</h3>
          <el-button type="primary" size="small" :loading="saving" @click="saveGlobalConfig">
            保存
          </el-button>
        </div>
        <el-form :model="globalConfig" label-width="140px">
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="默认重试次数">
                <el-input-number v-model="globalConfig.defaultRetry" :min="0" :max="10" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="重试间隔（秒）">
                <el-input-number v-model="globalConfig.retryInterval" :min="5" :max="600" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="调度集群模式">
                <el-select v-model="globalConfig.schedulerMode">
                  <el-option label="自动主备（Leader 选举）" value="LEADER_ELECTION" />
                  <el-option label="多主（分布式锁）" value="MULTI_MASTER" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="最大并发任务数">
                <el-input-number v-model="globalConfig.maxConcurrentTasks" :min="10" :max="5000" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="任务幂等保障">
            <el-checkbox-group v-model="globalConfig.idempotentGuards">
              <el-checkbox label="数据库状态机" />
              <el-checkbox label="Redis 幂等锁" />
              <el-checkbox label="执行日志校验" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="执行超时自愈">
            <el-switch v-model="globalConfig.autoRecover" />
            <span class="form-tip">启用后，超时任务会自动中断并重新调度。</span>
          </el-form-item>
        </el-form>
      </section>

      <section class="card config-center__section">
        <div class="section__header">
          <h3>告警策略</h3>
          <el-button type="primary" size="small" @click="openRuleDrawer">新增规则</el-button>
        </div>
        <el-table :data="alertRules" size="small" v-loading="loading">
          <el-table-column prop="name" label="规则名称" min-width="160" />
          <el-table-column prop="metric" label="监控指标" width="140" />
          <el-table-column prop="threshold" label="阈值" width="120" />
          <el-table-column label="告警渠道" width="160">
            <template #default="{ row }">
              <el-tag v-for="channel in row.channels" :key="channel" size="small" effect="plain">
                {{ channelLabel(channel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="启用状态" width="120">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" @change="toggleRule(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" type="primary" @click="editRule(row)">编辑</el-button>
              <el-popconfirm title="确认删除该规则？" @confirm="removeRule(row)">
                <template #reference>
                  <el-button link size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="card config-center__section">
        <div class="section__header">
          <h3>环境与租户说明</h3>
        </div>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header>
                <span>环境隔离</span>
              </template>
              <el-timeline>
                <el-timeline-item timestamp="dev" type="primary">
                  使用 dev 数据源，允许调试与 Mock。
                </el-timeline-item>
                <el-timeline-item timestamp="test" type="warning">
                  使用 test 数据源，开启审计与回放。
                </el-timeline-item>
                <el-timeline-item timestamp="prod" type="success">
                  使用 prod 数据源，开启幂等锁、告警。
                </el-timeline-item>
              </el-timeline>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header>
                <span>租户配置示例</span>
              </template>
              <ul class="tenant-list">
                <li><strong>默认租户：</strong>共享任务模板，限流 500 QPS。</li>
                <li><strong>营销团队：</strong>Kafka Topic 独立，执行节点 10 台。</li>
                <li><strong>风控团队：</strong>开启策略隔离，消息加密。</li>
              </ul>
            </el-card>
          </el-col>
        </el-row>
      </section>
    </div>

    <section v-else class="card config-center__empty">
      <el-empty description="当前账号没有查看或管理配置的权限" />
    </section>

    <el-drawer v-model="ruleDrawerVisible" title="告警规则" size="30%">
      <el-form :model="currentRule" label-width="110px">
        <el-form-item label="规则名称">
          <el-input v-model="currentRule.name" />
        </el-form-item>
        <el-form-item label="监控指标">
          <el-select v-model="currentRule.metric">
            <el-option label="失败率" value="FAILURE_RATE" />
            <el-option label="延迟" value="LATENCY" />
            <el-option label="积压任务" value="BACKLOG" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发阈值">
          <el-input-number v-model="currentRule.threshold" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="告警渠道">
          <el-checkbox-group v-model="currentRule.channels">
            <el-checkbox label="EMAIL" />
            <el-checkbox label="DINGTALK" />
            <el-checkbox label="SMS" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="通知人">
          <el-select v-model="currentRule.subscribers" multiple filterable>
            <el-option label="张强" value="zhangqiang@corp.com" />
            <el-option label="李娜" value="lina@corp.com" />
            <el-option label="王伟" value="wangwei@corp.com" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="currentRule.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="ruleDrawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingRule" @click="saveRule">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useStore } from 'vuex';
import { ElMessage } from 'element-plus';
import {
  fetchAlertRules,
  updateAlertRule,
  deleteAlertRule,
  createAlertRule,
  testAlertChannel
} from '@/api/alerts';

const store = useStore();

const alertRules = ref([]);
const loading = ref(false);
const saving = ref(false);
const savingRule = ref(false);
const ruleDrawerVisible = ref(false);

const canManageConfig = computed(() => store.getters['auth/hasPermission']('config:write'));

const globalConfig = reactive({
  defaultRetry: 3,
  retryInterval: 60,
  schedulerMode: 'LEADER_ELECTION',
  maxConcurrentTasks: 2000,
  idempotentGuards: ['数据库状态机', 'Redis 幂等锁'],
  autoRecover: true
});

const currentRule = reactive({
  id: null,
  name: '',
  metric: 'FAILURE_RATE',
  threshold: 3,
  channels: ['EMAIL'],
  subscribers: [],
  enabled: true
});

const loadRules = async () => {
  if (!canManageConfig.value) {
    alertRules.value = [];
    return;
  }
  loading.value = true;
  try {
    alertRules.value = await fetchAlertRules();
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadRules();
});

watch(
  canManageConfig,
  (allowed) => {
    if (allowed) {
      loadRules();
    } else {
      alertRules.value = [];
    }
  }
);

const saveGlobalConfig = async () => {
  if (!canManageConfig.value) {
    ElMessage.warning('没有权限执行此操作');
    return;
  }
  saving.value = true;
  try {
    await testAlertChannel({ ping: true });
    ElMessage.success('配置保存成功（示例）');
  } finally {
    saving.value = false;
  }
};

const openRuleDrawer = () => {
  if (!canManageConfig.value) {
    ElMessage.warning('没有权限执行此操作');
    return;
  }
  Object.assign(currentRule, {
    id: null,
    name: '',
    metric: 'FAILURE_RATE',
    threshold: 3,
    channels: ['EMAIL'],
    subscribers: [],
    enabled: true
  });
  ruleDrawerVisible.value = true;
};

const editRule = (rule) => {
  if (!canManageConfig.value) {
    ElMessage.warning('没有权限执行此操作');
    return;
  }
  Object.assign(currentRule, rule);
  ruleDrawerVisible.value = true;
};

const toggleRule = async (rule) => {
  if (!canManageConfig.value) {
    ElMessage.warning('没有权限执行此操作');
    return;
  }
  await updateAlertRule(rule.id, { enabled: rule.enabled });
  ElMessage.success('规则已更新');
};

const removeRule = async (rule) => {
  if (!canManageConfig.value) {
    ElMessage.warning('没有权限执行此操作');
    return;
  }
  await deleteAlertRule(rule.id);
  await loadRules();
  ElMessage.success('规则已删除');
};

const saveRule = async () => {
  if (!canManageConfig.value) {
    ElMessage.warning('没有权限执行此操作');
    return;
  }
  savingRule.value = true;
  try {
    if (currentRule.id) {
      await updateAlertRule(currentRule.id, currentRule);
    } else {
      await createAlertRule(currentRule);
    }
    await loadRules();
    ruleDrawerVisible.value = false;
    ElMessage.success('规则保存成功');
  } finally {
    savingRule.value = false;
  }
};

const channelLabel = (channel) => {
  const map = {
    EMAIL: '邮件',
    DINGTALK: '钉钉',
    SMS: '短信'
  };
  return map[channel] || channel;
};
</script>

<style scoped lang="scss">
.config-center-wrapper {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-center {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-center__section {
  padding: 20px 24px;

  .section__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
    }
  }
}

.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #64748b;
}

.tenant-list {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: #475569;

  strong {
    color: #0f172a;
  }
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.config-center__empty {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}
</style>
