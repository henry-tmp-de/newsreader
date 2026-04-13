<template>
  <el-container class="layout-root">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <el-icon size="28" color="#409EFF"><Reading /></el-icon>
        <span>NewsReader</span>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="transparent"
        text-color="#d8dee6"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/">
          <el-icon><House /></el-icon>
          <span>新闻首页</span>
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>学习看板</span>
        </el-menu-item>
        <el-menu-item index="/vocabulary">
          <el-icon><Collection /></el-icon>
          <span>我的词汇</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header paper">
        <div class="header-left">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-tag :type="levelTagType" size="small" class="level-tag">
            {{ userStore.level }}
          </el-tag>
          <el-dropdown @command="handleCommand">
            <div class="avatar-wrap">
              <el-avatar size="small" :src="userStore.userInfo?.avatar">
                {{ userStore.userInfo?.username?.[0]?.toUpperCase() }}
              </el-avatar>
              <span class="username">{{ userStore.userInfo?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容 -->
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pageTitleMap = {
  '/': '新闻列表',
  '/dashboard': '学习看板',
  '/vocabulary': '我的词汇',
}
const pageTitle = computed(() => {
  if (route.name === 'Article') return '文章阅读'
  if (route.name === 'Exercise') return '练习测验'
  return pageTitleMap[route.path] || 'NewsReader'
})

const levelTagType = computed(() => {
  const map = { BEGINNER: 'info', INTERMEDIATE: 'warning', ADVANCED: 'success' }
  return map[userStore.level] || 'info'
})

function handleCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.sidebar {
  background: linear-gradient(180deg, #1a232d, #131b24);
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 24px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  border-bottom: 1px solid #ffffff20;
}
.el-menu {
  border-right: none;
  flex: 1;
  padding-top: 12px;
}
.el-menu-item {
  margin: 4px 10px;
  border-radius: 10px;
}
.el-menu-item.is-active {
  background: rgba(255, 255, 255, 0.16) !important;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  margin: 14px 16px 0;
}
.paper {
  border: 1px solid rgba(24, 34, 45, 0.1);
  border-radius: 18px;
  background: rgba(250, 246, 237, 0.92);
  backdrop-filter: blur(8px);
  box-shadow: 0 16px 40px rgba(20, 27, 34, 0.06);
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.level-tag {
  cursor: default;
}
.avatar-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.username {
  font-size: 14px;
  color: #606266;
}
.main-content {
  background:
    radial-gradient(circle at top right, rgba(163, 58, 43, 0.12), transparent 26%),
    linear-gradient(180deg, #f7f2e7, #f0e7d8);
  padding: 20px;
  overflow-y: auto;
}
</style>
