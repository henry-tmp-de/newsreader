import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { guest: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { guest: true },
  },
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
      },
      {
        path: 'article/:id',
        name: 'Article',
        component: () => import('@/views/Article.vue'),
      },
      {
        path: 'exercise/:articleId',
        name: 'Exercise',
        component: () => import('@/views/Exercise.vue'),
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
      },
      {
        path: 'progress',
        name: 'Progress',
        component: () => import('@/views/Progress.vue'),
      },
      {
        path: 'vocabulary',
        name: 'Vocabulary',
        component: () => import('@/views/Vocabulary.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.token) {
    next('/login')
  } else if (to.meta.guest && userStore.token) {
    next('/')
  } else {
    next()
  }
})

export default router
