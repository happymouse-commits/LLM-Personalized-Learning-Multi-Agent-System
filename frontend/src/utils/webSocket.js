import { ElNotification } from 'element-plus'
import { useUserStore } from '../store/user'
import { ref, watch, onUnmounted } from 'vue'

const ws = ref(null)
const connected = ref(false)
const reconnectTimer = ref(null)
let refCount = 0 // 引用计数，避免一个组件卸载断开所有连接

/**
 * WebSocket 通知工具 — 连接后端推送服务
 * 收到 resources 或 profile 更新时弹通知
 */
export function useWebSocket() {
  const store = useUserStore()
  refCount++

  function connect() {
    if (ws.value && ws.value.readyState === WebSocket.OPEN) return
    // 需要有效 token 才能建立 WS 连接（后端会校验 token）
    const token = store.token
    if (!token) return
    // 演示模式使用的是伪造 token，后端会拒绝 WS 连接 — 直接跳过避免无谓重连
    const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
    if (isDemo) return

    // 走 Vite 代理（开发 `/ws` → localhost:8090），生产环境同域
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${window.location.host}/ws/student?token=${encodeURIComponent(token)}`

    try {
      const socket = new WebSocket(url)
      socket.onopen = () => {
        connected.value = true
      }

      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data)
          if (msg.type === 'resources') {
            // 如果有资源按钮数据，触发自定义事件传递数据
            if (msg.data && Array.isArray(msg.data)) {
              window.dispatchEvent(new CustomEvent('refresh-resources', { detail: { buttons: msg.data } }))
            } else {
              window.dispatchEvent(new CustomEvent('refresh-resources'))
            }
            // 静默刷新，不弹通知
          } else if (msg.type === 'profile') {
            window.dispatchEvent(new CustomEvent('refresh-profile'))
            // 静默刷新，不弹通知
          }
        } catch { /* ignore malformed messages */ }
      }

      socket.onclose = () => {
        connected.value = false
        // 自动重连（5秒后）
        reconnectTimer.value = setTimeout(() => {
          if (store.token) connect()
        }, 5000)
      }

      socket.onerror = () => {
        socket.close()
      }

      ws.value = socket
    } catch { /* connection failed, will retry */ }
  }

  function disconnect() {
    if (reconnectTimer.value) {
      clearTimeout(reconnectTimer.value)
      reconnectTimer.value = null
    }
    if (ws.value) {
      ws.value.close()
      ws.value = null
    }
    connected.value = false
  }

  // 监听登录状态变化
  const unwatch = watch(
    () => store.token,
    (token) => {
      if (token) connect()
      else disconnect()
    },
    { immediate: true }
  )

  // 组件销毁时减少引用计数，仅最后一个组件卸载时断开连接
  onUnmounted(() => {
    unwatch()
    refCount--
    if (refCount <= 0) {
      refCount = 0
      disconnect()
    }
  })

  return { connected, connect, disconnect }
}
