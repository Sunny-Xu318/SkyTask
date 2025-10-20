<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="login-card__header">
          <h2>SkyTask Platform</h2>
          <p>Please enter tenant, username and password to continue.</p>
        </div>
      </template>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="80px"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="Tenant" prop="tenantCode">
          <el-input
            v-model="loginForm.tenantCode"
            placeholder="e.g. tenant-alpha"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item label="Username" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="Enter username"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="Password" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="Enter password"
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
            {{ loading ? 'Signing in...' : 'Sign In' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-card__footer">
        <p>Demo tenant: tenant-alpha | demo account: ops-admin / admin123</p>
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
    { required: true, message: 'Please enter tenant code', trigger: 'blur' }
  ],
  username: [
    { required: true, message: 'Please enter username', trigger: 'blur' },
    { min: 3, max: 32, message: 'Username must be 3-32 characters', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please enter password', trigger: 'blur' },
    { min: 6, message: 'Password must be at least 6 characters', trigger: 'blur' }
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
    ElMessage.success(`Welcome back, ${response.displayName || response.username}`);

    const redirect = route.query.redirect || '/dashboard';
    router.push(redirect);
  } catch (error) {
    const status = error.response?.status;
    let message = error.response?.data?.message || error.message;
    if (status === 401) {
      message = 'Incorrect username or password';
    } else if (status === 404) {
      message = 'Tenant does not exist';
    } else if (!message) {
      message = 'Sign-in failed, please try again later';
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




