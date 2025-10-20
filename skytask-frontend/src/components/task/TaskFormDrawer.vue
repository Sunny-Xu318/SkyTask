<template>
  <el-drawer
    :model-value="visible"
    :title="formTitle"
    size="45%"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formState"
      :rules="rules"
      label-width="110px"
      status-icon
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="任务名称" prop="name">
            <el-input v-model="formState.name" placeholder="例如 nightly-report-generate" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属分组" prop="group">
            <el-select v-model="formState.group" placeholder="选择任务分组">
              <el-option
                v-for="item in groupOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="任务类型" prop="type">
            <el-select v-model="formState.type" placeholder="选择任务类型">
              <el-option label="Cron 定时任务" value="CRON" />
              <el-option label="一次性任务" value="ONE_TIME" />
              <el-option label="固定间隔任务" value="FIXED_RATE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="执行方式" prop="executorType">
            <el-select v-model="formState.executorType" placeholder="选择执行方式">
              <el-option label="HTTP 回调" value="HTTP" />
              <el-option label="gRPC 执行" value="GRPC" />
              <el-option label="Spring Bean" value="SPRING_BEAN" />
              <el-option label="Shell 脚本" value="SHELL" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16" v-if="formState.type === 'CRON'">
        <el-col :span="16">
          <el-form-item label="Cron 表达式" prop="cronExpr">
            <el-input
              v-model="formState.cronExpr"
              placeholder="0 0 2 * * ?"
              @blur="emitCronPreview"
            >
              <template #append>
                <el-link type="primary" @click.prevent="handleCronHelper">表达式助手</el-link>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="时区" prop="timeZone">
            <el-select v-model="formState.timeZone" placeholder="选择时区">
              <el-option label="系统默认" value="SYSTEM" />
              <el-option label="UTC" value="UTC" />
              <el-option label="Asia/Shanghai" value="Asia/Shanghai" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="路由策略" prop="routeStrategy">
            <el-select v-model="formState.routeStrategy">
              <el-option label="自动均衡" value="ROUND_ROBIN" />
              <el-option label="一致性哈希" value="CONSISTENT_HASH" />
              <el-option label="分片广播" value="SHARDING" />
              <el-option label="指定节点" value="FIXED_NODE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="重试策略" prop="retryPolicy">
            <el-select v-model="formState.retryPolicy">
              <el-option label="不重试" value="NONE" />
              <el-option label="固定间隔" value="FIXED_INTERVAL" />
              <el-option label="指数退避" value="EXP_BACKOFF" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="最大重试次数" prop="maxRetry">
            <el-input-number v-model="formState.maxRetry" :min="0" :max="10" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="超时时间 (s)" prop="timeout">
            <el-input-number v-model="formState.timeout" :min="10" :max="3600" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="负责人" prop="owner">
            <el-select v-model="formState.owner" placeholder="选择负责人">
              <el-option
                v-for="user in ownerOptions"
                :key="user.value"
                :label="user.label"
                :value="user.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="标签" prop="tags">
            <el-select v-model="formState.tags" multiple collapse-tags placeholder="业务标签">
              <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="幂等 Key 生成器" prop="idempotentKey">
        <el-input v-model="formState.idempotentKey" placeholder="例如 ${bizId}-${yyyyMMddHHmm}" />
      </el-form-item>

      <el-form-item label="执行参数" prop="parameters">
        <el-input
          v-model="formState.parameters"
          type="textarea"
          :rows="4"
          placeholder='JSON 参数，例如 {"reportType":"DAILY"}'
        />
      </el-form-item>

      <el-form-item label="任务描述" prop="description">
        <el-input v-model="formState.description" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer__footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  modelValue: {
    type: Object,
    default: () => ({})
  },
  groupOptions: {
    type: Array,
    default: () => []
  },
  ownerOptions: {
    type: Array,
    default: () => []
  },
  tagOptions: {
    type: Array,
    default: () => []
  },
  submitting: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:visible', 'submit', 'cron-helper', 'cron-preview']);

const formRef = ref(null);

const createInitialState = () => ({
  id: null,
  name: '',
  group: '',
  type: 'CRON',
  executorType: 'GRPC',
  cronExpr: '',
  timeZone: 'Asia/Shanghai',
  routeStrategy: 'ROUND_ROBIN',
  retryPolicy: 'EXP_BACKOFF',
  maxRetry: 3,
  timeout: 300,
  owner: '',
  tags: [],
  idempotentKey: '',
  parameters: '',
  description: ''
});

const formState = reactive(createInitialState());

const normalizeIncomingValue = (value) => {
  if (!value) {
    return {};
  }
  const normalized = { ...value };
  normalized.tags = Array.isArray(normalized.tags) ? [...normalized.tags] : [];
  if (normalized.parameters && typeof normalized.parameters === 'object') {
    normalized.parameters = JSON.stringify(normalized.parameters, null, 2);
  } else if (!normalized.parameters) {
    normalized.parameters = '';
  }
  return normalized;
};

function applyModelValue(value) {
  Object.assign(formState, createInitialState(), normalizeIncomingValue(value));
}

function resetForm() {
  nextTick(() => {
    formRef.value?.resetFields();
    Object.assign(formState, createInitialState());
  });
}

watch(
  () => props.modelValue,
  (val) => {
    if (val && Object.keys(val).length) {
      applyModelValue(val);
    } else {
      resetForm();
    }
  },
  { immediate: true, deep: true }
);

const formTitle = computed(() => (formState.id ? '编辑任务' : '新建任务'));

const rules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  group: [{ required: true, message: '请选择任务分组', trigger: 'change' }],
  type: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  executorType: [{ required: true, message: '请选择执行方式', trigger: 'change' }],
  cronExpr: [
    {
      validator: (_, value, callback) => {
        if (formState.type === 'CRON' && !value) {
          callback(new Error('请输入 Cron 表达式'));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ],
  owner: [{ required: true, message: '请选择负责人', trigger: 'change' }]
};

function handleClose() {
  emit('update:visible', false);
  resetForm();
}

function buildSubmitPayload() {
  const payload = {
    ...formState,
    tags: Array.isArray(formState.tags) ? [...formState.tags] : []
  };
  if (typeof formState.parameters === 'string' && formState.parameters.trim()) {
    try {
      payload.parameters = JSON.parse(formState.parameters);
    } catch (error) {
      ElMessage.error('执行参数必须是合法的 JSON 字符串');
      return null;
    }
  } else {
    payload.parameters = null;
  }
  return payload;
}

function handleSubmit() {
  formRef.value?.validate((valid) => {
    if (!valid) {
      return;
    }
    const payload = buildSubmitPayload();
    if (!payload) {
      return;
    }
    emit('submit', payload);
  });
}

function handleCronHelper() {
  emit('cron-helper', formState.cronExpr);
}

function emitCronPreview() {
  if (formState.cronExpr) {
    emit('cron-preview', formState.cronExpr);
  }
}
</script>

<style scoped lang="scss">
.drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 12px;
}
</style>
