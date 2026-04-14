<template>
  <div class="login-page">
    <div class="login-box">
      <div class="brand">
        <el-icon size="40" color="#409EFF"><Reading /></el-icon>
        <h1>NewsReader</h1>
        <p>AI 驱动的英语新闻学习平台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-button
          type="primary"
          style="width: 100%; margin-top: 8px"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <div class="footer-links">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    // error handled in interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(163, 58, 43, 0.2), transparent 30%),
    radial-gradient(circle at top right, rgba(24, 34, 45, 0.16), transparent 32%),
    linear-gradient(180deg, #f5efe4, #ede3d2);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.login-box {
  background: rgba(255, 253, 247, 0.94);
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 20px;
  padding: 40px 48px;
  width: 420px;
  max-width: 100%;
  box-shadow: 0 20px 60px rgba(20, 27, 34, 0.12);
}
.brand {
  text-align: center;
  margin-bottom: 32px;
}
.brand h1 {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin: 8px 0 4px;
}
.brand p {
  color: #909399;
  font-size: 14px;
}
.footer-links {
  text-align: center;
  margin-top: 20px;
  color: #909399;
  font-size: 14px;
}
.footer-links a {
  color: #a33a2b;
  text-decoration: none;
}

@media (max-width: 768px) {
  .login-page {
    align-items: stretch;
    padding: 14px;
  }

  .login-box {
    width: 100%;
    padding: 24px 18px;
    border-radius: 16px;
  }

  .brand {
    margin-bottom: 20px;
  }

  .brand h1 {
    font-size: 24px;
  }
}
</style>
