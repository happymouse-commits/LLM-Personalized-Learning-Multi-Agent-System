import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 彻底清理演示模式相关的所有 localStorage 持久化数据。
 * 覆盖：demo_* 前缀 + 演示用户 ID (0/-1/default) 相关的 cryd_* 会话键 + 泛会话/缓存键。
 */
function clearAllDemoStorage() {
  const demoIdVariants = ['0', '-1', 'default', '']
  const removeKeys = new Set()

  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i)
    if (!k) continue
    if (k.startsWith('demo_')) { removeKeys.add(k); continue }
    if (k.startsWith('cryd_session_practice_')) { removeKeys.add(k); continue }
    if (k.startsWith('cryd_pipeline_run_')) { removeKeys.add(k); continue }
    if (k === 'demo_today_recommend_resources_v2' || k === 'demo_today_recommend_resources_v3') { removeKeys.add(k); continue }
    // 带演示用户ID的聊天 / 导学相关键
    if (k.startsWith('cryd_chat_messages_') || k.startsWith('cryd_onboarding_')) {
      const suffix = k.replace(/^cryd_(chat_messages|onboarding_active|onboarding_step)_/, '')
      if (demoIdVariants.includes(suffix)) { removeKeys.add(k); continue }
    }
  }

  // 显式兜底清单（上面正则万一没覆盖到的固定键名）
  const fixedKeys = [
    'demo_onboarded', 'demo_profile', 'demo_resources', 'demo_practice',
    'demo_heatmap', 'demo_practice_history', 'demo_completed_practice',
    'demo_ability_tested', 'demo_today_recommend_resources_v2',
    'cryd_onboarded', 'cryd_ability_tested',
  ]
  for (const k of fixedKeys) removeKeys.add(k)

  removeKeys.forEach(k => { try { localStorage.removeItem(k) } catch {} })
  // 二次遍历：再次清理可能的漏网之鱼（避免迭代删除顺序问题导致漏删）
  for (let i = localStorage.length - 1; i >= 0; i--) {
    const k = localStorage.key(i)
    if (k && (k.startsWith('demo_') || k.startsWith('cryd_session_practice_'))) {
      try { localStorage.removeItem(k) } catch {}
    }
  }
}

export const useUserStore = defineStore('user', () => {
  const id = ref(Number(localStorage.getItem('cryd_id')) || 0)
  const token = ref(localStorage.getItem('cryd_token') || '')
  const username = ref(localStorage.getItem('cryd_username') || '')
  const nickname = ref(localStorage.getItem('cryd_nickname') || '')
  const role = ref(localStorage.getItem('cryd_role') || '')
  const className = ref(localStorage.getItem('cryd_class') || '')
  const studentId = ref(localStorage.getItem('cryd_studentId') || '')

  const isLoggedIn = computed(() => !!token.value)
  const homePath = computed(() => {
    const map = { student: '/student/home', admin: '/admin/rag-monitor' }
    return map[role.value] || '/login.html'
  })

  function setUser(data) {
    id.value = data.id || 0
    token.value = data.token || ''
    username.value = data.username || ''
    nickname.value = data.nickname || ''
    role.value = data.role || ''
    className.value = data.className || ''
    studentId.value = data.studentId || ''
    localStorage.setItem('cryd_id', String(id.value))
    localStorage.setItem('cryd_token', token.value)
    localStorage.setItem('cryd_username', username.value)
    localStorage.setItem('cryd_nickname', nickname.value)
    localStorage.setItem('cryd_role', role.value)
    localStorage.setItem('cryd_class', className.value)
    localStorage.setItem('cryd_studentId', studentId.value)
  }

  function logout() {
    const rawId = localStorage.getItem('cryd_id') || '0'
    // 清除管线动画标记
    localStorage.removeItem('cryd_pipeline_run_' + rawId)

    const wasDemo = localStorage.getItem('cryd_demo_mode') === 'true'

    id.value = 0
    token.value = ''
    username.value = ''
    nickname.value = ''
    role.value = ''
    className.value = ''
    studentId.value = ''

    // 如果是演示模式退出，先彻底清理所有 demo 相关持久化（必须在删 cryd_demo_mode 之前判定）
    if (wasDemo) clearAllDemoStorage()

    localStorage.removeItem('cryd_demo_mode')
    localStorage.removeItem('cryd_id')
    localStorage.removeItem('cryd_token')
    localStorage.removeItem('cryd_username')
    localStorage.removeItem('cryd_nickname')
    localStorage.removeItem('cryd_role')
    localStorage.removeItem('cryd_class')
    localStorage.removeItem('cryd_studentId')
    localStorage.removeItem('cryd_onboarded')
    localStorage.removeItem('cryd_ability_tested')

    // 通知所有组件清理：今日推荐恢复预设、聊天记录清空、练习房间重置等
    window.dispatchEvent(new CustomEvent('demo-mode-exit', { detail: { fromLogout: true, wasDemo } }))

    // 演示模式下：异步调用后端清理接口（忽略 401/404，仅尽力而为）
    if (wasDemo) {
      try {
        fetch('/api/ppt/demo-cleanup', { method: 'DELETE', credentials: 'same-origin' }).catch(() => {})
      } catch {}
    }
  }

  return { id, token, username, nickname, role, className, studentId, isLoggedIn, homePath, setUser, logout }
})