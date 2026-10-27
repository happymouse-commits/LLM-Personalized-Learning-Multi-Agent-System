import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import router from '../router'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

api.interceptors.request.use(config => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = 'Bearer ' + store.token
  }
  return config
})

api.interceptors.response.use(
  response => response.data,
  error => {
    // 调用方可通过 config.skipErrorMessage 自行处理错误提示（如重试场景）
    const skipToast = error.config?.skipErrorMessage

    if (error.response) {
      const status = error.response.status
      const data = error.response.data
      const msg = data?.message || error.message

      if (status === 401 || status === 403) {
        // 演示模式下不跳转登录页（demo_token 后端不认）
        const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
        // /api/chat/send 在演示模式下始终允许（后端已配置 permitAll）
        const isChatSend = error.config?.url?.includes('/chat/send')
        if (!isDemo && !isChatSend) {
          const store = useUserStore()
          store.logout()
          window.location.href = '/login.html'
        }
        return Promise.reject(error)
      }

      // 非 401 错误展示 Toast
      if (!skipToast) ElMessage.error(msg || '请求失败，请稍后重试')
    } else if (error.request) {
      // 超时错误单独提示，避免误报为"网络异常"
      const isTimeout = error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')
      if (!skipToast) {
        ElMessage.error(isTimeout ? '请求超时，请稍后重试' : '网络异常，请检查连接')
      }
    }
    return Promise.reject(error)
  }
)

export default api

// ===== 知识库 API =====
export const knowledgeApi = {
  create: (data) => api.post('/knowledge', data),
  getById: (kbId) => api.get(`/knowledge/${kbId}`),
  getByCourse: (courseId) => api.get(`/knowledge/course/${courseId}`),
  uploadDocument: (kbId, formData) => api.post(`/knowledge/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  uploadText: (kbId, data) => api.post(`/knowledge/${kbId}/documents/text`, data),
  getDocuments: (kbId) => api.get(`/knowledge/${kbId}/documents`),
  deleteDocument: (kbId, docId) => api.delete(`/knowledge/${kbId}/documents/${docId}`),
  search: (kbId, query) => api.get(`/knowledge/${kbId}/search`, { params: { q: query } }),
  getTags: (kbId) => api.get(`/knowledge/${kbId}/tags`),
}

// ===== 学情分析 API =====
export const analyticsApi = {
  getClassOverview: (className) => api.get(`/analytics/class/${className}/overview`),
  getStudentProfile: (studentId) => api.get(`/analytics/student/${studentId}/profile`),
  getComparison: (params) => api.get('/analytics/comparison', { params }),
  getAiAnalysis: (className) => api.get(`/analytics/class/${className}/ai-analysis`),
  exportReport: (className) => api.get(`/analytics/class/${className}/export`, { responseType: 'blob' }),
}

// ===== 学习路径 API =====
export const pathApi = {
  getPaths: (studentId) => api.get(`/learning-path/student/${studentId}`),
  getActivePath: (studentId) => api.get(`/learning-path/student/${studentId}/active`),
  updatePath: (pathId, data) => api.put(`/learning-path/${pathId}`, data),
  checkin: (data) => api.post('/learning-path/checkin', data),
  getCalendar: (studentId, month) => api.get(`/learning-path/checkin/calendar/${studentId}`, { params: { month } }),
  todayCheckin: (studentId) => api.get(`/learning-path/checkin/today/${studentId}`),
}

// ===== 资源中心 API =====
export const resourceApi = {
  getByStudent: (studentId) => api.get(`/resources/student/${studentId}`),
  getByType: (studentId, type) => api.get(`/resources/student/${studentId}/type/${type}`),
  getById: (id) => api.get(`/resources/${id}`),
  export: (id) => api.get(`/resources/${id}/export`),
  // 艾宾浩斯复习相关
  getReviewToday: (sysUserId) => api.get(`/resources/review-today/${sysUserId}`),
  getTodayRecommend: (sysUserId) => api.get(`/resources/today-recommend/${sysUserId}`),
  // 资源生成（同步接口，涉及多智能体生成，耗时较长，使用5分钟超时）
  // extraConfig 可传入 { skipErrorMessage: true } 等以供调用方自定义错误处理
  generate: (studentId, data, extraConfig = {}) => api.post(`/resources/generate/${studentId}`, data, { timeout: 300000, ...extraConfig }),
  generateAllChapters: (studentId) => api.post(`/resources/generate-all-chapters/${studentId}`),
  generatePpt: (data) => api.post('/ppt/generate', data),
  generateOneOfEach: (sysUserId) => api.post(`/resources/generate-one-of-each/${sysUserId}`, null, { timeout: 300000 }),
}

// ===== 学习画像 API =====
export const profileApi = {
  get: (sysUserId) => api.get(`/profile/${sysUserId}`),
  update: (sysUserId, data) => api.put(`/profile/${sysUserId}`, data),
  analyze: (sysUserId) => api.post(`/profile/${sysUserId}/analyze`),
}

// ===== 管理员 API =====
export const adminApi = {
  getRagMonitor: () => api.get('/admin/rag-monitor'),
  resetRagMonitor: () => api.post('/admin/rag-monitor/reset'),
  getKbOverview: () => api.get('/admin/knowledge-overview'),
}

// ===== 代码运行 API =====
export const codeApi = {
  run: (payload) => api.post('/code/run', payload, { timeout: 30000 }),
}

export const evaluationApi = {
  generateTest: (data) => api.post('/evaluation/test/generate', data),
  submitTest: (testId, data) => api.post(`/evaluation/test/${testId}/submit`, data),
  getTests: (studentId) => api.get(`/evaluation/test/student/${studentId}`),
  getErrors: (studentId) => api.get(`/evaluation/errors/student/${studentId}`),
  addError: (data) => api.post('/evaluation/errors', data),
  analyzeError: (errorId) => api.post(`/evaluation/errors/${errorId}/analyze`),
  getWeakness: (studentId) => api.get(`/evaluation/weakness/${studentId}`),
}

// ===== 学习建议 & 知识图谱 API =====
export const adviceApi = {
  getLearningAdvice: (sysUserId) => api.get(`/advice/learning/${sysUserId}`),
  getLearningAdviceWithQuestion: (sysUserId, question) => api.post(`/advice/learning/${sysUserId}`, { question }),
  getKnowledgeGraph: (sysUserId) => api.get(`/advice/knowledge-graph/${sysUserId}`),
}
