<template>
  <div class="login-page">
    <div class="login-box">
      <div class="brand">
        <el-icon size="40" color="#409EFF"><Reading /></el-icon>
        <h1>创建账号</h1>
        <p>开始你的 AI 英语学习之旅</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3-20位字母数字" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="英语水平" prop="level">
          <el-select v-model="form.level" style="width: 100%">
            <el-option label="初级 (Beginner)" value="BEGINNER" />
            <el-option label="中级 (Intermediate)" value="INTERMEDIATE" />
            <el-option label="高级 (Advanced)" value="ADVANCED" />
          </el-select>
        </el-form-item>
        <el-form-item label="兴趣领域">
          <el-checkbox-group v-model="selectedInterests">
            <el-checkbox v-for="item in interests" :key="item.value" :label="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-button
          type="primary"
          style="width: 100%; margin-top: 8px"
          :loading="loading"
          @click="handleRegister"
        >
          注册
        </el-button>
      </el-form>

      <div class="footer-links">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const selectedInterests = ref([])

const interests = [
  { label: '科技', value: 'technology' },
  { label: '科学', value: 'science' },
  { label: '健康', value: 'health' },
  { label: '商业', value: 'business' },
  { label: '体育', value: 'sports' },
]

const form = reactive({ username: '', password: '', email: '', level: 'BEGINNER', interests: '' })
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
  ],
  level: [{ required: true, message: '请选择英语水平', trigger: 'change' }],
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  form.interests = selectedInterests.value.join(',')
  try {
    await userStore.register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    // handled in interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.login-box {
  background: #fff;
  border-radius: 16px;
  padding: 40px 48px;
  width: 460px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}
.brand {
  text-align: center;
  margin-bottom: 32px;
}
.brand h1 { font-size: 24px; font-weight: 700; color: #303133; margin: 8px 0 4px; }
.brand p { color: #909399; font-size: 14px; }
.footer-links { text-align: center; margin-top: 20px; color: #909399; font-size: 14px; }
.footer-links a { color: #409EFF; text-decoration: none; }
</style>
