<template>
  <el-container class="layout-root">
    <el-aside v-if="!isMobile" width="220px" class="sidebar">
      <div class="logo">
        <el-icon size="28" color="#409EFF"><Reading /></el-icon>
        <span>NewsReader</span>
      </div>
      <el-menu
        :default-active="activeMenuPath"
        router
        background-color="transparent"
        text-color="#d8dee6"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header paper">
        <div class="header-left">
          <el-button
            v-if="isMobile"
            class="menu-btn"
            text
            :icon="Menu"
            @click="mobileMenuVisible = true"
          />
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
              <span v-if="!isMobile" class="username">{{ userStore.userInfo?.username }}</span>
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

      <el-main :class="['main-content', { 'with-bottom-nav': isMobile }]">
        <router-view v-slot="{ Component }">
          <Suspense>
            <component :is="Component" />
            <template #fallback>
              <div class="route-fallback paper-lite">
                <el-skeleton :rows="10" animated />
              </div>
            </template>
          </Suspense>
        </router-view>
      </el-main>

      <nav v-if="isMobile" class="mobile-tabbar paper">
        <button
          v-for="item in menuItems"
          :key="item.path"
          type="button"
          :class="['tab-item', { active: activeMenuPath === item.path }]"
          @click="goTo(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.mobileLabel || item.label }}</span>
        </button>
      </nav>
    </el-container>

    <el-drawer
      v-model="mobileMenuVisible"
      direction="ltr"
      size="76%"
      class="mobile-drawer"
      :with-header="false"
    >
      <div class="logo mobile-logo">
        <el-icon size="28" color="#409EFF"><Reading /></el-icon>
        <span>NewsReader</span>
      </div>
      <el-menu
        :default-active="activeMenuPath"
        background-color="transparent"
        text-color="#d8dee6"
        active-text-color="#ffffff"
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
          @click="goTo(item.path)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  ArrowDown,
  Collection,
  DataAnalysis,
  House,
  Menu,
  Opportunity,
  Reading,
  Setting,
  TrendCharts,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isMobile = ref(false)
const mobileMenuVisible = ref(false)

const menuItems = [
  { path: '/', label: '新闻首页', mobileLabel: '首页', icon: House },
  { path: '/dashboard', label: '学习看板', mobileLabel: '看板', icon: DataAnalysis },
  { path: '/news-manage', label: '新闻管理', mobileLabel: '管理', icon: Setting },
  { path: '/recommend', label: '智能推荐', mobileLabel: '推荐', icon: Opportunity },
  { path: '/progress', label: '学习进度', mobileLabel: '进度', icon: TrendCharts },
  { path: '/vocabulary', label: '我的词句', mobileLabel: '词句', icon: Collection },
]

const pageTitleMap = {
  '/': '新闻列表',
  '/dashboard': '学习看板',
  '/news-manage': '新闻管理',
  '/recommend': '智能推荐',
  '/progress': '学习进度',
  '/vocabulary': '我的词句',
}
const pageTitle = computed(() => {
  if (route.name === 'Article') return '文章阅读'
  if (route.name === 'Exercise') return '练习测验'
  return pageTitleMap[route.path] || 'NewsReader'
})

const activeMenuPath = computed(() => {
  if (route.path.startsWith('/article/')) return '/'
  if (route.path.startsWith('/exercise/')) return '/dashboard'
  return route.path
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

function goTo(path) {
  mobileMenuVisible.value = false
  if (route.path !== path) router.push(path)
}

function updateViewport() {
  isMobile.value = window.innerWidth <= 992
}

watch(
  () => route.path,
  () => {
    mobileMenuVisible.value = false
  }
)

onMounted(() => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateViewport)
})
</script>

<style scoped>
.layout-root {
  height: 100vh;
}

.sidebar {
  background:
    radial-gradient(circle at top left, rgba(222, 143, 85, 0.22), transparent 35%),
    linear-gradient(180deg, #3a2a24, #2a201c);
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(255, 255, 255, 0.12);
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
.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
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
  min-height: 0;
}
.paper-lite {
  border: 1px solid rgba(24, 34, 45, 0.08);
  border-radius: 18px;
  background: rgba(255, 253, 247, 0.82);
  padding: 18px;
}
.route-fallback {
  min-height: calc(100vh - 130px);
}

.mobile-tabbar {
  position: fixed;
  left: 12px;
  right: 12px;
  bottom: 10px;
  z-index: 20;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  align-items: center;
  padding: 6px;
  border-radius: 16px;
}

.tab-item {
  border: none;
  background: transparent;
  color: #606266;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 11px;
  line-height: 1;
  padding: 7px 2px;
  border-radius: 11px;
  cursor: pointer;
}

.tab-item.active {
  color: #a33a2b;
  background: rgba(163, 58, 43, 0.12);
}

:deep(.mobile-drawer .el-drawer) {
  background:
    radial-gradient(circle at top left, rgba(222, 143, 85, 0.2), transparent 35%),
    linear-gradient(180deg, #3a2a24, #2a201c);
}

:deep(.mobile-drawer .el-drawer__body) {
  padding: 0;
  display: flex;
  flex-direction: column;
}

.mobile-logo {
  margin-bottom: 8px;
}

@media (max-width: 992px) {
  .header {
    margin: 10px 10px 0;
    padding: 0 10px;
    border-radius: 14px;
  }

  .main-content {
    padding: 14px;
  }

  .with-bottom-nav {
    padding-bottom: 94px;
  }

  .level-tag {
    display: none;
  }
}

@media (max-width: 560px) {
  .page-title {
    font-size: 15px;
  }

  .header-right {
    gap: 6px;
  }

  .avatar-wrap {
    gap: 4px;
  }
}
</style>
