<template>
  <div class="admin-layout">
    <!-- 侧边栏：悬浮毛玻璃 + 渐变胶囊 -->
    <aside :class="['sidebar', { collapsed: isCollapse }]">
      <div class="logo-box" @click="isCollapse = !isCollapse">
        <div class="logo-grad">⚙️</div>
        <transition name="fade">
          <span v-show="!isCollapse" class="logo-text">管理端</span>
        </transition>
      </div>

      <nav class="nav-list">
        <router-link v-for="item in menuItems" :key="item.path"
          :to="item.path"
          :class="['nav-item', { active: isMenuActive(item.path) }]"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer" v-show="!isCollapse">
        <div class="user-mini">
          <span class="user-avatar">👤</span>
          <span class="user-name">{{ store.nickname || store.username }}</span>
        </div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <button class="collapse-btn" @click="isCollapse = !isCollapse">
            <span class="hamburger"><i></i><i></i><i></i></span>
          </button>
          <div class="breadcrumb">
            <span class="bread-home">🏠</span>
            <span class="bread-sep">/</span>
            <span class="bread-current">{{ pageTitle }}</span>
          </div>
        </div>
        <div class="topbar-right">
          <button class="topbar-btn" @click="toggleFullscreen" title="全屏">
            <el-icon :size="17"><FullScreen /></el-icon>
          </button>
          <el-dropdown trigger="click">
            <div class="user-area">
              <el-avatar :size="32" icon="UserFilled" />
              <span class="nickname">{{ store.nickname || store.username }}</span>
              <el-icon :size="14"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>⚙️ 管理员</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">🚪 退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../../store/user'
import { FullScreen, ArrowDown, UserFilled } from '@element-plus/icons-vue'

const store = useUserStore()
const route = useRoute()
const isCollapse = ref(false)

const menuItems = [
  { path: '/admin/rag-monitor', icon: '🛡️', label: 'RAG 监控' },
  { path: '/admin/knowledge-base', icon: '📚', label: '知识库管理' },
]

const pageTitle = computed(() => {
  const item = menuItems.find(m => isMenuActive(m.path))
  return item ? item.label : '管理后台'
})

// 菜单高亮：前缀匹配，兼容末尾斜杠和子路由
function isMenuActive(menuPath) {
  const current = route.path
  return current === menuPath || current.startsWith(menuPath + '/') || current === menuPath + '/'
}

function handleLogout() { store.logout(); window.location.href = '/login.html' }
function toggleFullscreen() {
  if (!document.fullscreenElement) document.documentElement.requestFullscreen()
  else document.exitFullscreen()
}
</script>

<style scoped>
.admin-layout {
  height: 100vh; display: flex; gap: 10px;
  padding: 10px;
  background: linear-gradient(135deg, #e8ecf1 0%, #f0f2f5 40%, #f8fafc 70%, #e8ecf1 100%);
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 230px; flex-shrink: 0;
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 18px;
  border: 1px solid rgba(255,255,255,0.6);
  box-shadow: 0 0 0 1px rgba(0,0,0,0.03), 0 4px 24px rgba(0,0,0,0.05);
  display: flex; flex-direction: column;
  transition: width 0.3s ease;
  overflow: hidden;
}
.sidebar.collapsed { width: 66px; }

.logo-box {
  display: flex; align-items: center; gap: 10px;
  padding: 18px 16px 10px; cursor: pointer;
}
.logo-grad {
  width: 34px; height: 34px; border-radius: 10px; flex-shrink: 0;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex; align-items: center; justify-content: center;
  font-size: 17px;
}
.logo-text { font-size: 16px; font-weight: 800; color: #0f172a; letter-spacing: -0.3px; }
.collapsed .logo-box { justify-content: center; padding: 18px 0 10px; }

.nav-list {
  flex: 1; display: flex; flex-direction: column; gap: 2px;
  padding: 4px 10px; overflow-y: auto;
}
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border-radius: 999px;
  font-size: 13.5px; font-weight: 500; color: #64748b;
  text-decoration: none;
  transition: all 0.2s ease;
}
.nav-icon { font-size: 17px; flex-shrink: 0; width: 22px; text-align: center; }
.nav-label { white-space: nowrap; }
.nav-item:hover { background: #f1f5f9; color: #334155; transform: scale(1.03); }
.nav-item.active {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; font-weight: 600;
  box-shadow: 0 4px 16px rgba(139,92,246,0.3);
  transform: scale(1.03);
}
.collapsed .nav-item { justify-content: center; padding: 10px 0; }
.collapsed .nav-label { display: none; }

.sidebar-footer { padding: 12px 14px 14px; border-top: 1px solid rgba(0,0,0,0.05); }
.user-mini {
  display: flex; align-items: center; gap: 10px; padding: 8px 10px;
  border-radius: 12px;
}
.user-avatar { width: 30px; height: 30px; border-radius: 50%; background: #e2e8f0; display: flex; align-items: center; justify-content: center; font-size: 15px; }
.user-name { font-size: 13px; font-weight: 500; color: #334155; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ===== 主区域 ===== */
.main-area { flex: 1; display: flex; flex-direction: column; min-width: 0; gap: 6px; }

.topbar {
  display: flex; align-items: center; justify-content: space-between;
  height: 48px; padding: 0 16px; flex-shrink: 0;
  background: rgba(255,255,255,0.7); backdrop-filter: blur(16px);
  border-radius: 14px;
  border: 1px solid rgba(255,255,255,0.5);
  box-shadow: 0 1px 3px rgba(0,0,0,0.03);
}
.topbar-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn {
  width: 30px; height: 30px; border-radius: 8px; border: none;
  background: transparent; cursor: pointer; display: flex;
  align-items: center; justify-content: center; transition: background 0.2s;
}
.collapse-btn:hover { background: #f1f5f9; }
.hamburger { display: flex; flex-direction: column; gap: 4px; }
.hamburger i { display: block; width: 15px; height: 2px; background: #94a3b8; border-radius: 1px; }
.breadcrumb { display: flex; align-items: center; gap: 7px; font-size: 13px; color: #94a3b8; }
.bread-sep { color: #cbd5e1; }
.bread-current { color: #334155; font-weight: 500; }

.topbar-right { display: flex; align-items: center; gap: 8px; }
.topbar-btn {
  width: 32px; height: 32px; border-radius: 8px; border: none;
  background: transparent; color: #94a3b8; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: all 0.2s;
}
.topbar-btn:hover { background: #f1f5f9; color: #6366f1; }
.user-area {
  display: flex; align-items: center; gap: 7px;
  padding: 3px 8px 3px 3px; border-radius: 24px;
  cursor: pointer; transition: background 0.2s;
}
.user-area:hover { background: #f1f5f9; }
.nickname { font-size: 13px; color: #334155; font-weight: 500; }

.content {
  flex: 1; overflow-y: auto; min-height: 0;
  padding: 16px;
  border-radius: 14px;
  background: rgba(255,255,255,0.35);
}
.content::-webkit-scrollbar { width: 4px; }
.content::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 2px; }

.page-fade-enter-active, .page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-fade-enter-from { opacity: 0; transform: translateY(6px); }
.page-fade-leave-to { opacity: 0; transform: translateY(-6px); }
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 768px) {
  .sidebar { width: 66px; }
  .sidebar .nav-label, .sidebar .logo-text, .sidebar .sidebar-footer { display: none; }
  .sidebar .nav-item { justify-content: center; padding: 10px 0; }
  .sidebar .logo-box { justify-content: center; }
}
</style>
