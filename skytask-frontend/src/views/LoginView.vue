<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-card__header">
          <h2>SkyTask 平台</h2>
          <p>请输入租户、用户名和密码登录</p>
        </div>
      </template>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="80px"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="租户" prop="tenantCode">
          <el-input
            v-model="loginForm.tenantCode"
            placeholder="例如: tenant-alpha"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            style="width: 100%"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-card__footer">
        <p>演示租户: tenant-alpha | 演示账号: ops-admin / admin123</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useStore } from 'vuex';
import { ElMessage } from 'element-plus';

const store = useStore();
const router = useRouter();
const route = useRoute();

const loginFormRef = ref(null);
const loading = ref(false);
const loginForm = reactive({
  tenantCode: 'tenant-alpha',
  username: '',
  password: ''
});

const loginRules = {
  tenantCode: [
    { required: true, message: '请输入租户代码', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度为3-32个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6个字符', trigger: 'blur' }
  ]
};

const handleLogin = async () => {
  if (!loginFormRef.value) {
    return;
  }
  try {
    const valid = await loginFormRef.value.validate();
    if (!valid) {
      return;
    }
  } catch {
    return;
  }

  loading.value = true;
  try {
    const payload = {
      tenantCode: loginForm.tenantCode,
      username: loginForm.username,
      password: loginForm.password
    };
    const response = await store.dispatch('auth/login', payload);
    ElMessage.success(`欢迎回来，${response.displayName || response.username}`);

    const redirect = route.query.redirect || '/dashboard';
    router.push(redirect);
  } catch (error) {
    const status = error.response?.status;
    let message = error.response?.data?.message || error.message;
    if (status === 401) {
      message = '用户名或密码错误';
    } else if (status === 404) {
      message = '租户不存在';
    } else if (!message) {
      message = '登录失败，请稍后重试';
    }
    ElMessage.error(message);
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped lang="scss">
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #0f172a, #1e293b);
  padding: 32px 16px;
}

.login-card {
  width: 420px;
  border-radius: 16px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.35);

  &__header {
    text-align: center;

    h2 {
      margin: 0 0 8px;
      font-size: 24px;
      color: #0f172a;
    }

    p {
      margin: 0;
      font-size: 14px;
      color: #64748b;
    }
  }

  &__footer {
    margin-top: 16px;
    text-align: center;
    font-size: 12px;
    color: #94a3b8;
  }
}
</style>




