import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  { path: '/', redirect: '/login.html' },
  { path: '/login', redirect: '/login.html' },

  // 学生端
  { path: '/student/questionnaire', component: () => import('../views/student/Questionnaire.vue'), meta: { role: 'student', requiresOnboarding: true } },
  { path: '/student/fullscreen-practice/:knowledgePoint', component: () => import('../views/student/PracticeRoom.vue'), meta: { role: 'student', fullscreen: true } },
  { path: '/student/code-workbench/:resourceId', component: () => import('../views/student/CodeWorkbench.vue'), meta: { role: 'student', fullscreen: true } },
  { path: '/student', component: () => import('../views/student/Layout.vue'), meta: { role: 'student' },
    children: [
      { path: 'home', component: () => import('../views/student/Home.vue') },
      { path: 'chat', component: () => import('../views/student/AIGuide.vue') },
      { path: 'today-recommend', component: () => import('../views/student/TodayRecommend.vue') },
      { path: 'resource-detail', component: () => import('../views/student/ResourceDetail.vue') },
      { path: 'practice/:knowledgePoint', component: () => import('../views/student/PracticeRoom.vue') },
      { path: 'learning-center', component: () => import('../views/student/LearningResources.vue') },
      { path: 'resources', redirect: '/student/learning-center' },
      { path: 'resources/chapter', component: () => import('../views/student/ChapterResources.vue') },
      { path: 'learning-path', component: () => import('../views/student/LearningPath.vue') },
      { path: 'my-info', component: () => import('../views/student/PersonalInfo.vue') },
    ]
  },

  // 管理员端
  { path: '/admin', component: () => import('../views/admin/Layout.vue'), meta: { role: 'admin' },
    redirect: '/admin/rag-monitor',
    children: [
      { path: 'rag-monitor', component: () => import('../views/admin/RAGMonitor.vue') },
      { path: 'knowledge-base', component: () => import('../views/admin/KnowledgeBase.vue') },
    ]
  },

  // 404 放最后
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFound.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    return { top: 0, behavior: 'smooth' }
  }
})

// 演示模式读取localStorage的辅助函数
function demoGet(key) {
  return localStorage.getItem('demo_' + key)
}
function demoSet(key, value) {
  localStorage.setItem('demo_' + key, value)
}
function demoRemove(key) {
  localStorage.removeItem('demo_' + key)
}

// 学生侧边栏页面
const STUDENT_PAGES = [
  '/student/home',
  '/student/chat',
  '/student/today-recommend',
  '/student/learning-center',
  '/student/learning-path',
  '/student/my-info',
  '/student/resource-detail',
  '/student/code-workbench',
  '/student/resources',
  '/student/practice',
]

router.beforeEach((to, from, next) => {
  const store = useUserStore()
  const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'

  // ============ 演示模式 ============
  if (isDemo) {
    // 演示模式使用独立用户数据，ID=0 表示演示用户
    store.setUser({
      id: 0, token: 'demo_token',
      username: 'demo_student', nickname: '演示用户',
      role: 'student', className: '演示班级', studentId: 'DEMO001'
    })

    // 登录页在演示模式下正常显示，不做自动跳转
    if (to.path.startsWith('/admin')) return next()
    if (to.path === '/student' || to.path === '/student/') {
      return next('/student/questionnaire')
    }
    if (!to.path.startsWith('/student/')) return next()

    // 演示模式：已完成能力检测 → 所有页面放行
    if (demoGet('ability_tested') === 'true') return next()

    // 演示模式：未完成能力检测 → 允许访问的页面
    const demoOnboarded = demoGet('onboarded') === 'true'
    const demoAllowed = [
      '/student/questionnaire',
      '/student/fullscreen-practice',
      '/student/today-recommend',
      '/student/home',
      '/student/chat',
      '/student/learning-center',
      '/student/learning-path',
      '/student/my-info',
      '/student/resource-detail',
      '/student/code-workbench',
      '/student/resources',
      '/student/practice',
    ]

    if (demoAllowed.some(p => to.path.startsWith(p))) return next()

    // 其他页面 → 根据进度跳转
    if (!demoOnboarded) return next('/student/questionnaire')
    return next('/student/today-recommend')
  }

  // ============ 正常模式 ============
  const token = localStorage.getItem('cryd_token')
  const role = localStorage.getItem('cryd_role')

  if (!token) {
    window.location.href = '/login.html'
    return
  }

  if (store.role !== role) {
    store.setUser({
      id: Number(localStorage.getItem('cryd_id')) || 0,
      token: token,
      username: localStorage.getItem('cryd_username') || '',
      nickname: localStorage.getItem('cryd_nickname') || '',
      role: role || 'student',
      className: localStorage.getItem('cryd_class') || '',
      studentId: localStorage.getItem('cryd_studentId') || ''
    })
  }

  if (role === 'admin') return next()

  // 学生流程检查
  if (role === 'student') {
    const hasOnboarded = localStorage.getItem('cryd_onboarded') === 'true'
    const hasTested = localStorage.getItem('cryd_ability_tested') === 'true'

    if (hasOnboarded && hasTested) return next()

    if (!hasOnboarded && !to.path.includes('/student/questionnaire')) {
      return next('/student/questionnaire')
    }

    if (hasOnboarded && !hasTested) {
      if (STUDENT_PAGES.some(p => to.path.startsWith(p))) return next()
      if (to.path.includes('/student/fullscreen-practice')) return next()
      if (to.path.startsWith('/student/code-workbench')) return next()
      return next('/student/today-recommend')
    }
  }

  next()
})

router.afterEach((to) => {
  if (to.query.demo === 'true') {
    const { demo, ...otherQuery } = to.query
    const qs = new URLSearchParams(otherQuery).toString()
    const cleanUrl = to.path + (qs ? '?' + qs : '')
    window.history.replaceState({}, '', cleanUrl)
  }
})

export default router
