<template>
  <div class="student-home">
    <!-- ===== 加载骨架屏 ===== -->
    <div v-if="loading" class="skeleton-wrapper">
      <div class="skeleton-hero"></div>
      <div class="skeleton-stats">
        <div v-for="i in 4" :key="'sk'+i" class="skeleton-card-item"></div>
      </div>
      <div class="skeleton-grid">
        <div class="skeleton-block tall"></div>
        <div class="skeleton-block tall"></div>
      </div>
    </div>

    <!-- ===== 真实内容 ===== -->
    <div v-else class="dashboard-content">

      <!-- ════════════════════════════════════════ -->
      <!-- 主区域：AI 多智能体协作中心 + Agent监控  -->
      <!-- ════════════════════════════════════════ -->
      <div class="main-grid">

        <!-- ===== 左栏 (3/4)：AI 多智能体协作中心 ===== -->
        <div class="agent-center-card">

          <!-- 顶部标题栏 + 总体进度 -->
          <div class="agent-header">
            <div class="agent-header-left">
              <h2 class="agent-title">
                <span class="title-icon-mini">⚡</span>
                AI 多智能体协作中心
              </h2>
              <p class="agent-subtitle">6 个智能体协同工作 · 实时生成个性化学习内容</p>
            </div>
            <div class="agent-header-right">
              <div class="run-pill">
                <span class="run-dot"></span>
                <span class="run-text">运行中</span>
              </div>
              <div class="top-progress">
                <div class="top-progress-bar"><div class="top-progress-fill" :style="{ width: overallProgress + '%' }"></div></div>
                <span class="top-progress-text">{{ overallProgress }}%</span>
              </div>
            </div>
          </div>

          <!-- 6个Agent卡片网格（简洁版） -->
          <div class="agent-grid-v3">
            <div
              v-for="(ag, idx) in agents"
              :key="ag.id"
              :class="['agent-card-v3', statusClass(ag.status), `agent-color-${ag.id}`]"
            >
              <div class="acv3-header">
                <div class="acv3-icon-box">
                  <span class="acv3-icon">{{ ag.icon }}</span>
                  <span v-if="ag.status === 'done'" class="acv3-check">✓</span>
                  <span v-if="ag.status === 'running'" class="acv3-pulse"></span>
                </div>
                <span :class="['acv3-tag', `tag-${ag.status}`]">{{ statusLabel(ag.status) }}</span>
              </div>
              <h3 class="acv3-name">{{ ag.name }}</h3>
              <p class="acv3-desc">{{ ag.desc }}</p>
              <div class="acv3-progress">
                <div class="acv3-bar"><div class="acv3-fill" :style="{ width: ag.progress + '%', background: progressColor(ag.id) }"></div></div>
                <span class="acv3-pct">{{ ag.progress }}%</span>
              </div>
              <div class="acv3-stats">
                <div v-for="st in ag.stats" :key="st.label" class="acv3-stat">
                  <span class="acv3-stat-label">{{ st.label }}</span>
                  <span class="acv3-stat-value">{{ st.value }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 资源生成中状态 -->
          <div v-if="isGenerating" class="generating-banner">
            <div class="generating-header">
              <span class="generating-icon">⚡</span>
              <span class="generating-title">正在为你生成个性化学习资源</span>
            </div>
            <div class="generating-progress">
              <div class="generating-bar">
                <div class="generating-fill" :style="{ width: generationProgress + '%' }"></div>
              </div>
              <span class="generating-pct">{{ generationProgress }}%</span>
            </div>
            <p class="generating-step">{{ generationStep }}</p>
          </div>

          <!-- 🧠 艾宾浩斯智能复习（横向） -->
          <div class="ebbinghaus-horizontal">
            <div class="ebbinghaus-h-left">
              <div class="ebbinghaus-h-title-row">
                <span class="ebbinghaus-h-icon">🧠</span>
                <span class="ebbinghaus-h-title">艾宾浩斯智能复习</span>
                <span v-if="reviewData.hasReview" class="ebbinghaus-h-badge">第{{ reviewData.reviewDay }}天</span>
              </div>
              <div v-if="reviewData.hasReview" class="ebbinghaus-h-body">
                <div class="ebbinghaus-h-tags">
                  <span v-for="kp in reviewData.knowledgePoints" :key="kp" class="kp-tag">{{ kp }}</span>
                </div>
                <div class="ebbinghaus-h-progress-row">
                  <div class="progress-bar-bg">
                    <div class="progress-bar-fill" :style="{ width: reviewData.progress + '%' }"></div>
                  </div>
                  <span class="ebbinghaus-h-pct">{{ reviewData.progress }}%</span>
                </div>
                <div class="ebbinghaus-h-timeline-row">
                  <div class="ebbinghaus-h-timeline">
                    <span class="tl-dot done"></span>
                    <span class="tl-line done"></span>
                    <span class="tl-dot done"></span>
                    <span class="tl-line active"></span>
                    <span class="tl-dot active"></span>
                    <span class="tl-line"></span>
                    <span class="tl-dot"></span>
                    <span class="tl-line"></span>
                    <span class="tl-dot"></span>
                  </div>
                  <span class="ebbinghaus-h-next">下次复习: 第{{ reviewData.nextReviewDay }}天</span>
                </div>
              </div>
              <div v-else class="ebbinghaus-h-empty">
                <span>继续学习，系统将自动生成复习计划</span>
              </div>
            </div>
            <div class="ebbinghaus-h-right">
              <div class="ebbinghaus-h-stats">
                <div class="eh-stat-item">
                  <span class="eh-stat-num">{{ reviewData.completedNodes }}</span>
                  <span class="eh-stat-label">已复习</span>
                </div>
                <div class="eh-stat-item">
                  <span class="eh-stat-num">{{ reviewData.totalNodes - reviewData.completedNodes }}</span>
                  <span class="eh-stat-label">待复习</span>
                </div>
              </div>
              <button class="ebbinghaus-h-btn" @click="$router.push({ path: '/student/fullscreen-practice/艾宾浩斯复习', query: { from: 'today-recommend' } })">
                📖 开始复习
              </button>
            </div>
          </div>

          <!-- 底部汇总 -->
          <div class="agent-summary-v3">
            <span class="summary-item">✅ 已完成 {{ agents.filter(a => a.status === 'done').length }}/{{ agents.length }}</span>
            <span class="summary-sep">·</span>
            <span class="summary-item">管线进度 {{ overallProgress }}%</span>
            <span class="summary-sep">·</span>
            <span class="summary-item">{{ pipelineStatus }}</span>
          </div>
        </div>

      </div>

      <!-- ════════════════════════════════════════ -->
      <!-- 底部内容区域（Agent管线完成后才显示）      -->
      <!-- ════════════════════════════════════════ -->
      <div :class="['content-reveal', { 'content-visible': contentVisible }]">

      <!-- ════════════════════════════════════════ -->
      <!-- 底部网格：雷达图 + 趋势图 + 词云         -->
      <!-- ════════════════════════════════════════ -->
      <div class="bottom-grid-v2">

        <!-- ═══ 列 1：学习能力多维立体雷达图 ═══ -->
        <div class="bottom-col-v2">
          <div class="glass-card profile-card-square">
            <div class="gc-header">
              <h3 class="gc-title">🕸️ 学习能力多维立体雷达图</h3>
            </div>
            <div ref="radarChartRef" class="chart-container-square"></div>
          </div>
        </div>

        <!-- ═══ 列 2：认知能力双周跨周期发展趋势 ═══ -->
        <div class="bottom-col-v2">
          <div class="glass-card profile-card-square">
            <div class="gc-header">
              <h3 class="gc-title">📈 认知能力双周跨周期发展趋势</h3>
            </div>
            <div ref="trendChartRef" class="chart-container-square"></div>
          </div>
        </div>

        <!-- ═══ 列 3：C语言薄弱点词云 ═══ -->
        <div class="bottom-col-v2">
          <div class="glass-card wordcloud-card">
            <div class="gc-header">
              <h3 class="gc-title">☁️ C语言薄弱点</h3>
              <span class="gc-subtitle" v-if="weakAreas.length">薄弱知识点一览</span>
              <span class="gc-subtitle" v-else>C语言知识体系</span>
            </div>
            <div class="wordcloud-wrapper" id="homeWordcloudWrapper">
              <canvas ref="wordcloudCanvas" class="wordcloud-canvas"></canvas>
              <div v-if="wordcloudHovered && wordcloudHovered.info" class="wordcloud-hover-card" :style="wordcardStyle">
                <div class="wc-title">{{ wordcloudHovered.info.name }}</div>
                <div class="wc-desc">{{ wordcloudHovered.info.desc }}</div>
                <div class="wc-tag">{{ wordcloudHovered.info.tag }}</div>
              </div>
            </div>
          </div>
        </div>

      </div>

      <!-- ════════════════════════════════════════ -->
      <!-- 底部全宽：学习资源 + 学习任务              -->
      <!-- ════════════════════════════════════════ -->
      <LearningResourceSection :resources="lrResources" :task="lrTask" />

      </div><!-- /content-reveal -->

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { useDemoStore } from '../../store/demo'
import api, { profileApi, resourceApi } from '../../api'
import LearningResourceSection from '../../components/LearningResourceSection.vue'

const router = useRouter()
const store = useUserStore()
const demoStore = useDemoStore()
const loading = ref(true)
const profile = ref({})
const resources = ref([])
const weakAreas = ref([])

// 艾宾浩斯复习数据
const reviewData = ref({
  hasReview: false,
  reviewDay: 0,
  nextReviewDay: 1,
  knowledgePoints: [],
  progress: 0,
  completedNodes: 0,
  totalNodes: 5,
})

// 路径规划Agent数据
const pathplanData = ref({
  paths: 0,
  chapters: '0/7',
  progress: 0,
})

// 图表 refs
const radarChartRef = ref(null)
const trendChartRef = ref(null)
let radarInstance = null
let trendInstance = null

// 词云相关
const wordcloudCanvas = ref(null)
const wordcloudHovered = ref(null)
const wordcardStyle = ref({})
let wordcloudCtx = null
let wordcloudDpr = 1
let wordcloudW = 0, wordcloudH = 0
let wordcloudNodes = []
let wordcloudAnimId = null
let wordcloudStartTime = Date.now()
let wordcloudMX = -1000, wordcloudMY = -1000




const weakPoints = computed(() => {
  const w = profile.value.weakAreas
  if (!w) return []
  return w.split(/[、，,]/).map(s => s.trim()).filter(Boolean)
})

// ===== B2: AI今日建议 - 动态生成 =====
const aiSuggestion = computed(() => {
  const weaks = weakPoints.value
  const level = profile.value.knowledgeLevel || 30

  // 无薄弱点
  if (!weaks.length) {
    return {
      text: '🎉 暂无薄弱环节，继续保持！',
      improvement: '持续巩固',
      duration: '30 分钟',
      isEmpty: true
    }
  }

  const primary = weaks[0]
  const secondary = weaks.length > 1 ? weaks[1] : null

  // 根据知识水平和薄弱点数量生成不同建议
  let suggestion = ''
  let improvement = ''
  let duration = ''

  if (level < 30) {
    // 初学者：建议从基础开始
    suggestion = `发现你的 <span class="sug-highlight">${primary}</span> 基础较薄弱，建议从基础概念开始学习，打牢根基。`
    improvement = '20% 掌握度'
    duration = '60 分钟'
  } else if (level < 60) {
    // 中等水平：建议专项突破
    if (secondary) {
      suggestion = `发现你的 <span class="sug-highlight">${primary}</span> 和 <span class="sug-highlight">${secondary}</span> 需要加强，建议优先突破 ${primary}，再巩固 ${secondary}。`
    } else {
      suggestion = `发现你的 <span class="sug-highlight">${primary}</span> 能力相对薄弱，建议通过专项练习来提升。`
    }
    improvement = '15% 掌握度'
    duration = '45 分钟'
  } else {
    // 高水平：建议深度学习
    suggestion = `你的整体水平不错，但 <span class="sug-highlight">${primary}</span> 仍有提升空间，建议深入学习高级用法。`
    improvement = '10% 掌握度'
    duration = '30 分钟'
  }

  return { text: suggestion, improvement, duration, isEmpty: false }
})

const recentResources = computed(() => resources.value.slice(0, 5))

// ===== 学习资源横卡片数据 =====
const DEFAULT_RESOURCES = [
  { id: 1, title: 'C语言指针完全指南', duration: '25:30', level: '进阶', topic: '指针', gradient: 'linear-gradient(135deg, #1e1b4b, #1e293b)' },
  { id: 2, title: '结构体与共用体实战', duration: '21:30', level: '基础', topic: '结构体', gradient: 'linear-gradient(135deg, #3b0764, #1e1b4b)' },
  { id: 3, title: '文件操作全解析', duration: '16:20', level: '基础', topic: '文件操作', gradient: 'linear-gradient(135deg, #0c4a6e, #0f172a)' },
  { id: 4, title: '动态内存管理精讲', duration: '23:15', level: '进阶', topic: '动态内存', gradient: 'linear-gradient(135deg, #134e4a, #1e293b)' },
  { id: 5, title: '函数高级应用技巧', duration: '19:40', level: '进阶', topic: '函数', gradient: 'linear-gradient(135deg, #2e1065, #1e293b)' },
]
const lrResources = computed(() => {
  if (resources.value.length === 0) return DEFAULT_RESOURCES
  const gradients = [
    'linear-gradient(135deg, #1e1b4b, #1e293b)',
    'linear-gradient(135deg, #3b0764, #1e1b4b)',
    'linear-gradient(135deg, #0c4a6e, #0f172a)',
    'linear-gradient(135deg, #134e4a, #1e293b)',
    'linear-gradient(135deg, #2e1065, #1e293b)',
  ]
  return resources.value.slice(0, 5).map((r, i) => ({
    id: r.id,
    title: r.title,
    duration: '15:00',
    level: r.difficulty === 'hard' ? '进阶' : '基础',
    topic: r.knowledgePoint || 'C语言',
    gradient: gradients[i % gradients.length],
  }))
})
const lrTask = computed(() => {
  const weak = weakPoints.value
  if (weak.length === 0) return { name: '复习巩固', description: '完成10道练习题，预计20分钟', duration: '20分钟', difficulty: '基础', resourceCount: 5, topic: 'C语言基础' }
  return {
    name: `${weak[0]}专项训练`,
    description: `完成10道${weak[0]}练习题，预计20分钟`,
    duration: '20分钟',
    difficulty: '进阶',
    resourceCount: 5,
    topic: weak[0]
  }
})

// ===== AI 多智能体协作管线 =====
const agents = ref([])
const generatedTypes = ref([])
const overallProgress = ref(0)
const pipelineStatus = ref('加载中...')
const pipelineEta = ref('--')

// 入场动画状态
const pipelineRunning = ref(false)
const pipelineComplete = ref(false)
const contentVisible = ref(false)
// 生成状态
const isGenerating = ref(false)
const generationProgress = ref(0)
const generationStep = ref('')
let pollTimer = null

const agentMeta = {
  rag: { desc: '从知识库检索相关知识片段', color: '#22c55e', connector: 'conn-emerald', spinner: '', statColor: 'as-green' },
  tutor: { desc: '基于检索结果生成教学内容', color: '#3b82f6', connector: 'conn-blue', spinner: '', statColor: 'as-blue' },
  hallucination: { desc: '校验内容真实性，拦截AI幻觉', color: '#f59e0b', connector: 'conn-amber', spinner: 'ac-spinner-amber', statColor: 'as-amber' },
  profile: { desc: '分析对话行为动态更新8维画像', color: '#8b5cf6', connector: 'conn-purple', spinner: '', statColor: 'as-purple' },
  recommend: { desc: '根据画像个性化定制资源', color: '#06b6d4', connector: 'conn-cyan', spinner: '', statColor: 'as-cyan' },
  pathplan: { desc: '根据画像推荐个性化学习路径', color: '#f97316', connector: 'conn-orange', spinner: '', statColor: 'as-orange' },
}

const resourceChips = computed(() => {
  const allChips = [
    { key: 'article', icon: '📄', label: '学习笔记' },
    { key: 'mindmap', icon: '🗺️', label: '思维导图' },
    { key: 'explanation', icon: '❓', label: '练习题' },
    { key: 'code', icon: '💻', label: '代码演示' },
    { key: 'summary', icon: '💡', label: '学习总结' },
    { key: 'course-ppt', icon: '📑', label: '课程PPT' },
    { key: 'course-video', icon: '🎬', label: '教学视频' },
  ]
  const genSet = new Set(generatedTypes.value)
  return allChips.map(c => ({ ...c, generated: genSet.has(c.key) || genSet.has(c.label) }))
})

function statusClass(s) {
  return s === 'done' ? 'ac-done' : s === 'running' ? 'ac-running' : 'ac-pending'
}
function statusColor(id, s) {
  if (s === 'done') return 'ac-emerald'
  const m = { rag: 'ac-emerald', tutor: 'ac-blue', hallucination: 'ac-amber', profile: 'ac-purple', recommend: 'ac-cyan', pathplan: 'ac-orange' }
  return m[id] || 'ac-blue'
}
function statusTagClass(s) {
  return s === 'done' ? 'tag-done' : s === 'running' ? 'tag-active' : 'tag-pending'
}
function statusLabel(s) {
  return s === 'done' ? '已完成' : s === 'running' ? '生成中' : '待处理'
}
function spinnerColor(id) {
  return agentMeta[id]?.spinner || ''
}
function progressColor(id) {
  return agentMeta[id]?.color || '#3b82f6'
}
function connectorColor(id) {
  return agentMeta[id]?.connector || 'conn-blue'
}
function statValueClass(id, label) {
  const m = { rag: 'as-green', tutor: 'as-blue', hallucination: 'as-amber', profile: 'as-purple', recommend: 'as-cyan', pathplan: 'as-orange' }
  if (label.includes('可信度') || label.includes('命中')) return m[id] || ''
  return ''
}
function padNum(n) { return String(n).padStart(2, '0') }

// 入场动画：使用真实后端数据
async function runEntryAnimation(realData) {
  pipelineRunning.value = true
  pipelineComplete.value = false
  contentVisible.value = false

  // 演示模式假数据
  const DEMO_STATS = {
    rag: [{ label: '命中片段', value: '12' }, { label: '可信度', value: '92%' }],
    tutor: [{ label: '对话次数', value: '8' }, { label: '学习时长', value: '16min' }],
    hallucination: [{ label: '拦截次数', value: '2' }, { label: '一致性', value: '88%' }],
    profile: [{ label: '画像更新', value: '6 维度' }, { label: '知识水平', value: '45分' }],
    recommend: [{ label: '生成资源', value: '5 种' }, { label: '推荐数量', value: '23' }],
    pathplan: [{ label: '规划路径', value: '3 条' }, { label: '覆盖章节', value: '4/7' }],
  }

  // 定义Agent顺序
  const agentSequence = [
    { id: 'rag', name: 'RAG检索Agent', icon: '🔍', desc: agentMeta.rag.desc, stats: realData?.rag || DEMO_STATS.rag },
    { id: 'tutor', name: 'AI导师Agent', icon: '🎓', desc: agentMeta.tutor.desc, stats: realData?.tutor || DEMO_STATS.tutor },
    { id: 'hallucination', name: '幻觉校验Agent', icon: '🛡️', desc: agentMeta.hallucination.desc, stats: realData?.hallucination || DEMO_STATS.hallucination },
    { id: 'profile', name: '画像分析Agent', icon: '👤', desc: agentMeta.profile.desc, stats: realData?.profile || DEMO_STATS.profile },
    { id: 'recommend', name: '资源推荐Agent', icon: '🎁', desc: agentMeta.recommend.desc, stats: realData?.recommend || DEMO_STATS.recommend },
    { id: 'pathplan', name: '路径规划Agent', icon: '🗺️', desc: agentMeta.pathplan.desc, stats: realData?.pathplan || DEMO_STATS.pathplan },
  ]

  // 初始化所有Agent
  agents.value = agentSequence.map(a => ({
    id: a.id, name: a.name, icon: a.icon, status: 'pending', progress: 0, desc: a.desc,
    stats: a.stats,
  }))

  // 顺序执行每个Agent（根据真实状态决定是否完成）
  for (let i = 0; i < agentSequence.length; i++) {
    const seq = agentSequence[i]
    const agent = agents.value.find(a => a.id === seq.id)
    if (!agent) continue

    agent.status = 'running'
    pipelineStatus.value = `正在运行 ${agent.name}...`
    overallProgress.value = Math.round(((i) / agentSequence.length) * 100)

    // 短暂延迟模拟执行
    await new Promise(r => setTimeout(r, 400))
    agent.progress = 50

    await new Promise(r => setTimeout(r, 300))

    // 完成：显示真实stats
    agent.status = 'done'
    agent.progress = 100
    agent.stats = seq.stats
    overallProgress.value = Math.round(((i + 1) / agentSequence.length) * 100)
  }

  pipelineStatus.value = '管线执行完成 ✓'
  pipelineEta.value = '0s'
  pipelineRunning.value = false
  pipelineComplete.value = true

  await new Promise(r => setTimeout(r, 300))
  contentVisible.value = true
}

// 轮询资源生成状态
function startGenerationPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    try {
      const res = await api.get('/agent/generating-status/' + store.id)
      const data = res.data || {}
      isGenerating.value = data.generating || false
      generationProgress.value = data.progress || 0
      generationStep.value = data.step || ''

      if (!data.generating) {
        clearInterval(pollTimer)
        pollTimer = null
        // 生成完成，刷新数据并显示最终状态
        await loadFinalState()
      }
    } catch (e) {
      console.warn('轮询生成状态失败:', e)
    }
  }, 2000)
}

async function loadPipelineStatus() {
  try {
    // 直接从localStorage读取ID构建key，避免store.id时序问题
    const rawId = localStorage.getItem('cryd_id') || '0'
    const pipelineKey = 'cryd_pipeline_run_' + rawId
    const hasPipelineRun = localStorage.getItem(pipelineKey)

    // 如果已经运行过，直接加载最终状态（跳过动画）
    if (hasPipelineRun) {
      await loadFinalState()
      return
    }

    if (!store.id) {
      // 无用户ID时也初始化Agent卡片（显示默认状态）
      await runEntryAnimation(null)
      // 标记管线已运行
      localStorage.setItem(pipelineKey, Date.now().toString())
      return
    }

    // 检查是否正在生成资源
    try {
      const genRes = await api.get('/agent/generating-status/' + store.id)
      const genData = genRes.data || {}
      if (genData.generating) {
        isGenerating.value = true
        generationProgress.value = genData.progress || 0
        generationStep.value = genData.step || '正在生成资源...'
        pipelineStatus.value = '资源生成中...'
        contentVisible.value = true
        startGenerationPolling()
        return
      }
    } catch (e) {
      // 忽略，继续加载
    }

    // 获取真实管线数据
    let realData = null
    try {
      const data = await api.get('/agent/pipeline-status/' + store.id) || {}
      if (data.agents && data.agents.length) {
        realData = {}
        data.agents.forEach(a => {
          realData[a.id] = a.stats || []
        })
      }
      generatedTypes.value = data.generatedResourceTypes || []
    } catch (e) {
      console.warn('获取管线数据失败:', e)
    }

    // 运行入场动画
    await runEntryAnimation(realData)

    // 标记管线已运行（同一会话内不再重复运行）
    localStorage.setItem(pipelineKey, Date.now().toString())
  } catch (e) {
    console.error('加载管线状态失败:', e)
    pipelineStatus.value = '管线状态暂不可用'
    contentVisible.value = true
  }
}

async function loadFinalState() {
  try {
    // Demo模式：使用默认agent数据
    if (!store.id) {
      const defaultAgents = [
        { id: 'rag', name: 'RAG检索Agent', icon: '🔍', status: 'done', progress: 100, desc: agentMeta.rag.desc, stats: [{ label: '命中片段', value: '12' }, { label: '可信度', value: '92%' }] },
        { id: 'tutor', name: 'AI导师Agent', icon: '🎓', status: 'done', progress: 100, desc: agentMeta.tutor.desc, stats: [{ label: '对话次数', value: '8' }, { label: '学习时长', value: '16min' }] },
        { id: 'hallucination', name: '幻觉校验Agent', icon: '🛡️', status: 'done', progress: 100, desc: agentMeta.hallucination.desc, stats: [{ label: '拦截次数', value: '2' }, { label: '一致性', value: '88%' }] },
        { id: 'profile', name: '画像分析Agent', icon: '👤', status: 'done', progress: 100, desc: agentMeta.profile.desc, stats: [{ label: '画像更新', value: '6 维度' }, { label: '知识水平', value: '45分' }] },
        { id: 'recommend', name: '资源推荐Agent', icon: '🎁', status: 'done', progress: 100, desc: agentMeta.recommend.desc, stats: [{ label: '生成资源', value: '5 种' }, { label: '推荐数量', value: '23' }] },
        { id: 'pathplan', name: '路径规划Agent', icon: '🗺️', status: 'done', progress: 100, desc: agentMeta.pathplan.desc, stats: [{ label: '规划路径', value: '3 条' }, { label: '覆盖章节', value: '4/7' }] },
      ]
      agents.value = defaultAgents
      overallProgress.value = 100
      pipelineStatus.value = '管线执行完成 ✓'
      contentVisible.value = true
      return
    }

    const data = await api.get('/agent/pipeline-status/' + store.id) || {}

    if (data.agents && data.agents.length) {
      agents.value = data.agents.map(a => ({
        ...a,
        desc: agentMeta[a.id]?.desc || '',
        stats: a.stats || [],
        status: 'done',
        progress: 100,
      }))
    }
    generatedTypes.value = data.generatedResourceTypes || []
    overallProgress.value = data.overallProgress || 100
    pipelineStatus.value = '管线执行完成 ✓'
    pipelineEta.value = '0s'
    isGenerating.value = false
    contentVisible.value = true
  } catch (e) {
    console.error('加载最终状态失败:', e)
    contentVisible.value = true
  }
}

// ===== 知识点→章节映射 =====
const knowledgePointChapterMap = {
  '基础语法': { chapter: '第1章', points: ['数据类型', '变量声明', '运算符', '输入输出', '注释'] },
  '流程控制': { chapter: '第2章', points: ['if-else', 'switch', 'for循环', 'while循环', 'break/continue'] },
  '函数': { chapter: '第3章', points: ['函数定义', '参数传递', '递归', '作用域'] },
  '数组与指针': { chapter: '第4-5章', points: ['一维数组', '二维数组', '指针变量', '指针运算', '动态内存'] },
  '结构体': { chapter: '第6章', points: ['结构体定义', '结构体指针', '链表'] },
  '文件操作': { chapter: '第7章', points: ['文件打开关闭', '读写函数', '文本读写'] },
}

// ===== 词云数据 =====
// B5: 词云内容随 weakAreas 变化，但效果不变
const knowledgePointBank = {
  '基础语法': [
    { text: '数据类型', desc: '整型、浮点型、字符型等基本数据类型' },
    { text: '变量声明', desc: '变量的命名规则、初始化方式和作用域' },
    { text: '运算符', desc: '算术、关系、逻辑运算符的使用' },
    { text: '输入输出', desc: 'printf / scanf 格式化输入输出' },
    { text: '注释', desc: '单行注释和多行注释' },
    { text: '常量定义', desc: '#define 和 const 定义常量' },
  ],
  '流程控制': [
    { text: 'if-else', desc: '条件判断，程序逻辑的核心' },
    { text: 'switch', desc: '多分支选择结构' },
    { text: 'for循环', desc: '已知循环次数时使用' },
    { text: 'while循环', desc: '条件满足时持续循环' },
    { text: 'break/continue', desc: '循环控制语句' },
    { text: 'do-while', desc: '至少执行一次的循环' },
  ],
  '函数': [
    { text: '函数定义', desc: '函数的声明、定义和调用' },
    { text: '参数传递', desc: '值传递 vs 指针传递' },
    { text: '递归', desc: '函数调用自身' },
    { text: '作用域', desc: '局部 vs 全局变量' },
    { text: '返回值', desc: 'return 语句与函数返回类型' },
    { text: '函数声明', desc: '函数原型声明与头文件' },
  ],
  '数组与指针': [
    { text: '一维数组', desc: '相同类型元素的集合' },
    { text: '二维数组', desc: '矩阵形式的数据结构' },
    { text: '指针变量', desc: '存储内存地址的变量' },
    { text: '指针运算', desc: '指针的算术运算' },
    { text: '动态内存', desc: 'malloc / free 动态内存管理' },
    { text: '指针与数组', desc: '数组名作为指针使用' },
  ],
  '结构体': [
    { text: '结构体定义', desc: '自定义数据类型' },
    { text: '结构体指针', desc: '指向结构体的指针' },
    { text: '链表', desc: '动态数据结构' },
    { text: '共用体', desc: 'union 共享内存空间' },
    { text: '枚举类型', desc: 'enum 定义命名常量集合' },
  ],
}

const wordCloudData = computed(() => {
  const weakNames = weakAreas.value.map(w => w.name || w)
  const isWeak = (name) => weakNames.some(w => name.includes(w) || w.includes(name))

  // 中心节点
  const data = [
    { text: 'C语言', x: 0.5, y: 0.5, fontSize: 38, fontWeight: 'bold', color: '#1f2937', isCenter: true, floatAmplitude: 0, breatheSpeed: 0.3 },
  ]

  // 主分类节点 - 5个均匀分布，位置固定
  const mainCategories = [
    { text: '基础语法', x: 0.25, y: 0.28 },
    { text: '流程控制', x: 0.75, y: 0.28 },
    { text: '函数', x: 0.82, y: 0.58 },
    { text: '数组与指针', x: 0.18, y: 0.62 },
    { text: '结构体', x: 0.72, y: 0.82 },
  ]

  // 主分类位置偏移表（固定布局，不随 weakAreas 变化）
  const mainCategoryPositions = {
    '基础语法': { x: 0.25, y: 0.28 },
    '流程控制': { x: 0.75, y: 0.28 },
    '函数': { x: 0.82, y: 0.58 },
    '数组与指针': { x: 0.18, y: 0.62 },
    '结构体': { x: 0.72, y: 0.82 },
  }

  // 子知识点位置偏移表（固定布局，每个分类3个位置轮换）
  const subPointSlots = {
    '基础语法': [
      { x: 0.12, y: 0.15 }, { x: 0.32, y: 0.12 }, { x: 0.12, y: 0.42 },
      { x: 0.22, y: 0.18 }, { x: 0.08, y: 0.28 }, { x: 0.18, y: 0.1 },
    ],
    '流程控制': [
      { x: 0.62, y: 0.12 }, { x: 0.82, y: 0.15 }, { x: 0.92, y: 0.28 },
      { x: 0.88, y: 0.18 }, { x: 0.72, y: 0.18 }, { x: 0.78, y: 0.1 },
    ],
    '函数': [
      { x: 0.92, y: 0.52 }, { x: 0.92, y: 0.68 }, { x: 0.85, y: 0.72 },
      { x: 0.92, y: 0.42 }, { x: 0.88, y: 0.62 }, { x: 0.95, y: 0.58 },
    ],
    '数组与指针': [
      { x: 0.08, y: 0.55 }, { x: 0.22, y: 0.72 }, { x: 0.12, y: 0.78 },
      { x: 0.32, y: 0.52 }, { x: 0.08, y: 0.88 }, { x: 0.18, y: 0.82 },
    ],
    '结构体': [
      { x: 0.62, y: 0.88 }, { x: 0.82, y: 0.92 }, { x: 0.72, y: 0.95 },
      { x: 0.68, y: 0.78 }, { x: 0.78, y: 0.85 },
    ],
  }

  mainCategories.forEach((cat, i) => {
    const weak = isWeak(cat.text)
    const pos = mainCategoryPositions[cat.text]
    data.push({
      text: cat.text,
      x: pos.x,
      y: pos.y,
      fontSize: weak ? 20 : 18,
      fontWeight: 'bold',
      color: weak ? '#4b5563' : '#9ca3af',
      isMainCategory: true,
      floatAmplitude: 3,
      breatheSpeed: 0.28 + i * 0.02,
    })
  })

  // B5: 子知识点 - 从知识库中动态选取，薄弱分类的知识点优先显示
  let allSubPoints = []

  mainCategories.forEach((cat, catIdx) => {
    const points = knowledgePointBank[cat.text] || []
    const slots = subPointSlots[cat.text] || []
    const catWeak = isWeak(cat.text)

    // 根据 weakAreas 决定显示哪些知识点
    let selectedPoints
    if (catWeak) {
      // 薄弱分类：显示更多知识点（4-5个）
      selectedPoints = points.slice(0, Math.min(5, points.length))
    } else {
      // 非薄弱分类：显示较少知识点（2-3个）
      selectedPoints = points.slice(0, Math.min(3, points.length))
    }

    // 额外检查：如果某个具体知识点在 weakAreas 中，也优先显示
    const extraPoints = points.filter(p => isWeak(p.text) && !selectedPoints.includes(p))
    if (extraPoints.length > 0 && selectedPoints.length < 5) {
      selectedPoints = [...selectedPoints, ...extraPoints].slice(0, 5)
    }

    selectedPoints.forEach((point, idx) => {
      const slot = slots[idx % slots.length]
      const pointWeak = isWeak(point.text) || catWeak
      allSubPoints.push({
        text: point.text,
        x: slot.x,
        y: slot.y,
        category: cat.text,
        desc: point.desc,
        isWeak: pointWeak,
      })
    })
  })

  allSubPoints.forEach((point, idx) => {
    const seed = ((idx * 7 + 13) % 17) / 17
    data.push({
      text: point.text,
      x: point.x,
      y: point.y,
      fontSize: point.isWeak ? 12 : 10,
      color: point.isWeak ? '#6b7280' : '#d1d5db',
      floatAmplitude: point.isWeak ? 3 : 2,
      breatheSpeed: 0.4 + seed * 0.15,
      info: { name: point.text, desc: point.desc, tag: knowledgePointChapterMap[point.category]?.chapter || 'C语言' },
    })
  })

  return data
})

// ===== 词云节点类 =====
class WordNode {
  constructor(data) {
    this.text = data.text
    this.baseX = data.x; this.baseY = data.y
    this.x = data.x; this.y = data.y
    this.fontSize = data.fontSize
    this.fontWeight = data.fontWeight || 'normal'
    this.color = data.color || '#888'
    this.isCenter = data.isCenter || false
    this.isMainCategory = data.isMainCategory || false
    this.info = data.info || null
    this.floatAmplitude = data.floatAmplitude || 5
    this.breatheSpeed = data.breatheSpeed || 0.5
    this.breatheOffset = Math.random() * Math.PI * 2
    this.floatOffsetX = Math.random() * Math.PI * 2
    this.floatOffsetY = Math.random() * Math.PI * 2
    this.isHovered = false; this.hoverScale = 1; this.currentScale = 1
    this.drawX = 0; this.drawY = 0; this.width = 0; this.height = 0
  }
  update(time) {
    const breathe = Math.sin(time * this.breatheSpeed + this.breatheOffset) * 0.02
    this.currentScale = 1 + breathe
    if (!this.isCenter && this.floatAmplitude > 0) {
      const amplitude = this.isMainCategory ? this.floatAmplitude * 0.6 : this.floatAmplitude
      this.x = this.baseX + Math.sin(time * 0.4 + this.floatOffsetX) * amplitude / 100
      this.y = this.baseY + Math.cos(time * 0.4 + this.floatOffsetY) * amplitude / 100
    }
    this.hoverScale = this.isHovered ? Math.min(this.hoverScale + 0.05, 1.15) : Math.max(this.hoverScale - 0.05, 1)
  }
  draw(ctx, width, height) {
    const x = this.x * width, y = this.y * height
    const fontSize = this.fontSize * this.currentScale * this.hoverScale
    ctx.save()
    let fontWeight = this.fontWeight, color = this.color
    if (this.isHovered && !this.isCenter) { fontWeight = 'bold'; color = '#1f2937' }
    ctx.font = `${fontWeight} ${fontSize}px -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif`
    ctx.fillStyle = color; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'
    if (this.isHovered) { ctx.shadowColor = 'rgba(0,0,0,0.1)'; ctx.shadowBlur = 8 }
    ctx.fillText(this.text, x, y); ctx.restore()
    const metrics = ctx.measureText(this.text)
    this.width = metrics.width; this.height = fontSize
    this.drawX = x - metrics.width / 2; this.drawY = y - fontSize / 2
  }
  hitTest(mx, my) {
    if (this.isCenter || this.isMainCategory || !this.info) return false
    return mx >= this.drawX - 8 && mx <= this.drawX + this.width + 8 && my >= this.drawY - 4 && my <= this.drawY + this.height + 4
  }
}

// ===== 词云绘制函数 =====
function drawWordcloudConnections(ctx, width, height) {
  const center = wordcloudNodes.find(n => n.isCenter)
  if (!center) return
  ctx.save()
  const mainCategories = wordcloudNodes.filter(n => n.isMainCategory)
  mainCategories.forEach(cat => {
    ctx.beginPath(); ctx.moveTo(center.x * width, center.y * height); ctx.lineTo(cat.x * width, cat.y * height)
    ctx.strokeStyle = 'rgba(156,163,175,0.2)'
    ctx.lineWidth = 1.5
    ctx.stroke()
    wordcloudNodes.filter(n => !n.isCenter && !n.isMainCategory && n.info).forEach(child => {
      if (Math.sqrt(Math.pow(child.baseX - cat.baseX, 2) + Math.pow(child.baseY - cat.baseY, 2)) < 0.3) {
        ctx.beginPath(); ctx.moveTo(cat.x * width, cat.y * height); ctx.lineTo(child.x * width, child.y * height)
        ctx.strokeStyle = 'rgba(156,163,175,0.1)'
        ctx.lineWidth = 0.8
        ctx.stroke()
      }
    })
  })
  ctx.restore()
}

function initWordcloud() {
  if (!wordcloudCanvas.value) return
  const wrapper = document.getElementById('homeWordcloudWrapper')
  if (!wrapper) return
  // 取消上一次动画循环，避免多次调用产生并行动画
  if (wordcloudAnimId) { cancelAnimationFrame(wordcloudAnimId); wordcloudAnimId = null }
  // 移除旧事件监听，避免重复绑定
  wordcloudCanvas.value.removeEventListener('mousemove', handleWordcloudMouseMove)
  wordcloudCanvas.value.removeEventListener('mouseleave', handleWordcloudMouseLeave)
  wordcloudCtx = wordcloudCanvas.value.getContext('2d')
  wordcloudDpr = window.devicePixelRatio || 1
  wordcloudW = wrapper.offsetWidth; wordcloudH = wrapper.offsetHeight
  if (wordcloudW === 0 || wordcloudH === 0) return
  wordcloudCanvas.value.width = wordcloudW * wordcloudDpr
  wordcloudCanvas.value.height = wordcloudH * wordcloudDpr
  wordcloudCanvas.value.style.width = wordcloudW + 'px'
  wordcloudCanvas.value.style.height = wordcloudH + 'px'
  wordcloudNodes = wordCloudData.value.map(data => new WordNode(data))
  // 添加事件监听
  wordcloudCanvas.value.addEventListener('mousemove', handleWordcloudMouseMove)
  wordcloudCanvas.value.addEventListener('mouseleave', handleWordcloudMouseLeave)
  animateWordcloud()
}

function animateWordcloud() {
  if (!wordcloudCtx || !wordcloudCanvas.value) return
  const time = (Date.now() - wordcloudStartTime) / 1000
  wordcloudCtx.clearRect(0, 0, wordcloudCanvas.value.width, wordcloudCanvas.value.height)
  wordcloudCtx.save(); wordcloudCtx.scale(wordcloudDpr, wordcloudDpr)
  drawWordcloudConnections(wordcloudCtx, wordcloudW, wordcloudH)
  wordcloudCtx.restore()
  let newHovered = null
  wordcloudNodes.forEach(node => {
    node.update(time)
    wordcloudCtx.save(); wordcloudCtx.scale(wordcloudDpr, wordcloudDpr)
    node.draw(wordcloudCtx, wordcloudW, wordcloudH); wordcloudCtx.restore()
    if (node.hitTest(wordcloudMX, wordcloudMY)) newHovered = node
  })
  if (wordcloudHovered.value !== newHovered) {
    if (wordcloudHovered.value) wordcloudHovered.value.isHovered = false
    if (newHovered && newHovered.info) { newHovered.isHovered = true; wordcloudHovered.value = newHovered }
    else { wordcloudHovered.value = null }
  }
  if (wordcloudHovered.value && wordcloudHovered.value.info) {
    const rect = wordcloudCanvas.value.getBoundingClientRect()
    let cardX = wordcloudMX + 20, cardY = wordcloudMY - 30
    if (cardX + 340 > rect.width) cardX = wordcloudMX - 350
    if (cardY + 180 > rect.height) cardY = rect.height - 200
    if (cardY < 10) cardY = 10
    wordcardStyle.value = { left: cardX + 'px', top: cardY + 'px' }
  }
  wordcloudAnimId = requestAnimationFrame(animateWordcloud)
}

const handleWordcloudMouseMove = (e) => {
  if (!wordcloudCanvas.value) return
  const rect = wordcloudCanvas.value.getBoundingClientRect()
  wordcloudMX = e.clientX - rect.left; wordcloudMY = e.clientY - rect.top
}

const handleWordcloudMouseLeave = () => {
  wordcloudMX = -1000; wordcloudMY = -1000
  if (wordcloudHovered.value) wordcloudHovered.value.isHovered = false
  wordcloudHovered.value = null
}

// ===== 雷达图（8维能力）=====
const categories = ['知识基础', '认知风格', '学习偏好', '学习节奏', '兴趣方向', '薄弱环节', '学习动机', '专注力']
const thisWeekScores = ref([])
const lastWeekScores = ref([])

async function initRadarChart() {
  if (!radarChartRef.value) return
  radarInstance?.dispose()
  let echarts
  try {
    echarts = (await import('echarts')).default || (await import('echarts'))
  } catch (e) {
    console.warn('ECharts 加载失败:', e)
    return
  }
  radarInstance = echarts.init(radarChartRef.value)

  // 从 profile 计算分数
  const p = profile.value
  const base = p.knowledgeLevel || 30
  const weakSet = new Set()
  if (p.weakAreas) p.weakAreas.split(/[,，、]/).forEach(w => weakSet.add(w.trim()))

  // 各维度与薄弱点的映射关系
  const weakMapping = {
    '薄弱环节': ['指针', '内存管理', '动态内存', '结构体', '文件操作', '循环', '数组', '函数'],
    '知识基础': ['基础语法', '数据类型', '变量', '运算符'],
    '认知风格': [],  // 由 cognitiveStyle 决定
    '学习偏好': [],  // 由 learningPreference 决定
    '学习节奏': [],  // 由 learningPace 决定
    '兴趣方向': [],  // 由 interestDirection 决定
    '学习动机': [],  // 由 studyMotivation 决定
    '专注力': [],    // 由 focusLevel 决定
  }

  const scores = categories.map((cat, i) => {
    let s = base

    // 根据维度类型调整分数
    if (cat === '知识基础') {
      // 知识基础直接用 knowledgeLevel
      s = base
    } else if (cat === '认知风格') {
      // 有认知风格数据加分
      s = p.cognitiveStyle ? base + 15 : base - 10
    } else if (cat === '学习偏好') {
      s = p.learningPreference ? base + 10 : base - 5
    } else if (cat === '学习节奏') {
      s = p.learningPace ? base + 10 : base - 5
    } else if (cat === '兴趣方向') {
      s = p.interestDirection ? base + 12 : base - 8
    } else if (cat === '学习动机') {
      s = p.studyMotivation ? base + 10 : base - 5
    } else if (cat === '专注力') {
      s = p.focusLevel ? base + 10 : base - 5
    } else if (cat === '薄弱环节') {
      // 薄弱点越多，分数越低
      s = Math.max(20, base - weakSet.size * 8)
    }

    // 检查是否有相关的薄弱点
    const relatedWeak = weakMapping[cat] || []
    for (const wk of weakSet) {
      if (relatedWeak.some(rw => wk.includes(rw) || rw.includes(wk))) {
        s -= 15
        break
      }
    }

    // 用索引做确定性偏移，避免图表完全对称
    const deterministicOffset = ((i * 7 + 3) % 11) - 5
    s += deterministicOffset

    return Math.max(5, Math.min(100, s))
  })

  thisWeekScores.value = scores

  // B4: 趋势图 - 上周数据根据 progress 和 knowledgeLevel 动态计算
  const progress = p.progress || 0
  const decayFactor = Math.max(0.6, 1 - progress * 0.005) // 进步越大，上周越低
  lastWeekScores.value = scores.map((s, i) => {
    const deterministicOffset = ((i * 3 + 7) % 10) - 5
    const lastScore = Math.round(s * decayFactor + deterministicOffset)
    return Math.max(5, Math.min(100, lastScore))
  })

  const maxVal = Math.max(...scores, 80)
  radarInstance.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: '0', data: ['本周表现', '上周表现'], textStyle: { color: '#666', fontSize: 11 } },
    radar: {
      indicator: categories.map(c => ({ name: c, max: maxVal })),
      center: ['50%', '46%'],
      radius: '65%',
      axisName: { color: '#333', fontSize: 11, fontWeight: 500 },
      splitArea: { areaStyle: { color: ['#fff', '#f5f7fa', '#fff', '#f5f7fa', '#fff'] } },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      splitLine: { lineStyle: { color: '#e4e7ed' } },
    },
    series: [{
      type: 'radar',
      data: [
        { value: scores, name: '本周表现', itemStyle: { color: '#4A90E2' }, lineStyle: { width: 2 }, areaStyle: { color: 'rgba(74,144,226,0.2)' } },
        { value: lastWeekScores.value, name: '上周表现', itemStyle: { color: '#52C41A' }, lineStyle: { type: 'dashed', width: 2 }, areaStyle: { color: 'rgba(82,196,26,0.02)' } },
      ],
    }],
  })
}

// ===== 趋势图 =====
async function initTrendChart() {
  if (!trendChartRef.value) return
  trendInstance?.dispose()
  let echarts
  try {
    echarts = (await import('echarts')).default || (await import('echarts'))
  } catch (e) {
    console.warn('ECharts 加载失败:', e)
    return
  }
  trendInstance = echarts.init(trendChartRef.value)
  const scores = thisWeekScores.value.length ? thisWeekScores.value : [50, 50, 50, 50, 50, 50, 50, 50]
  const last = lastWeekScores.value.length ? lastWeekScores.value : scores.map(s => Math.round(s * 0.8))
  trendInstance.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: '0', right: '0', data: ['本周', '上周'], textStyle: { fontSize: 11 } },
    grid: { left: '3%', right: '4%', bottom: '10%', top: '15%', containLabel: true },
    xAxis: { type: 'category', data: categories, axisLabel: { interval: 0, fontSize: 10, color: '#666' } },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { color: '#666' } },
    series: [
      { name: '本周', type: 'line', data: scores, itemStyle: { color: '#4A90E2' }, lineStyle: { width: 2.5 }, symbol: 'circle', symbolSize: 6 },
      { name: '上周', type: 'line', data: last, itemStyle: { color: '#52C41A' }, lineStyle: { type: 'dashed', width: 2 }, symbol: 'emptyCircle', symbolSize: 5 },
    ],
  })
}

// ===== 艾宾浩斯复习数据加载 =====
async function loadReviewData() {
  if (demoStore.enabled) {
    // 演示模式使用模拟数据
    reviewData.value = {
      hasReview: true,
      reviewDay: 2,
      nextReviewDay: 4,
      knowledgePoints: ['指针', '数组', '函数'],
      progress: 40,
      completedNodes: 2,
      totalNodes: 5,
    }
    return
  }
  if (!store.id) {
    reviewData.value = { hasReview: false, reviewDay: 0, nextReviewDay: 0, knowledgePoints: [], progress: 0, completedNodes: 0, totalNodes: 0 }
    return
  }
  try {
    const res = await resourceApi.getReviewToday(store.id)
    if (res.data) {
      reviewData.value = res.data
    } else {
      reviewData.value = { hasReview: false, reviewDay: 0, nextReviewDay: 0, knowledgePoints: [], progress: 0, completedNodes: 0, totalNodes: 0 }
    }
  } catch (e) {
    console.warn('加载复习数据失败:', e)
    reviewData.value = { hasReview: false, reviewDay: 0, nextReviewDay: 0, knowledgePoints: [], progress: 0, completedNodes: 0, totalNodes: 0 }
  }
}

// ===== 刷新画像数据（WebSocket触发） =====
async function refreshProfileData() {
  if (demoStore.enabled || !store.id) return
  try {
    const p = await profileApi.get(store.id)
    profile.value = p.data || {}
    if (p.data?.weakAreas) {
      weakAreas.value = p.data.weakAreas.split(/[,，、]/).map(w => ({ name: w.trim() })).filter(w => w.name)
    }
    // 重新初始化图表
    await nextTick()
    initRadarChart()
    initTrendChart()
    initWordcloud()
    loadReviewData()
  } catch (e) {
    console.warn('刷新画像数据失败:', e)
  }
}

// ===== 刷新资源数据（WebSocket触发） =====
async function refreshResourceData() {
  if (demoStore.enabled || !store.id) return
  try {
    const r = await resourceApi.getByStudent(store.id)
    resources.value = r.data || []
  } catch (e) {
    console.warn('刷新资源数据失败:', e)
  }
}

// ===== 首页初始化 =====
onMounted(async () => {
  // 先加载画像和资源数据（但不初始化图表）
  if (demoStore.enabled) {
    profile.value = await demoStore.getProfile()
    resources.value = await demoStore.getResources()
    weakAreas.value = await demoStore.getWeakAreas()
  } else if (store.id) {
    try {
      const p = await profileApi.get(store.id)
      profile.value = p.data || {}
      if (p.data?.weakAreas) {
        weakAreas.value = p.data.weakAreas.split(/[,，、]/).map(w => ({ name: w.trim() })).filter(w => w.name)
      }
    } catch (e) {
      console.warn('加载学习画像失败:', e)
    }
    try {
      const r = await resourceApi.getByStudent(store.id)
      resources.value = r.data || []
    } catch (e) {
      console.warn('加载学习资源失败:', e)
    }
  }

  // 先隐藏骨架屏，显示Agent管线区域
  loading.value = false
  await nextTick()

  // 运行Agent管线动画（会设置contentVisible = true）
  await loadPipelineStatus()

  // 等待内容区域可见后再初始化图表
  await nextTick()
  // 给CSS动画一点时间完成
  setTimeout(() => {
    initRadarChart()
    initTrendChart()
    setTimeout(initWordcloud, 200)
    loadReviewData()
  }, 100)

  // 监听WebSocket推送的刷新事件
  window.addEventListener('refresh-profile', refreshProfileData)
  window.addEventListener('refresh-resources', refreshResourceData)
})

onBeforeUnmount(() => {
  if (radarInstance) { radarInstance.dispose(); radarInstance = null }
  if (trendInstance) { trendInstance.dispose(); trendInstance = null }
  if (wordcloudAnimId) cancelAnimationFrame(wordcloudAnimId)
  if (wordcloudCanvas.value) {
    wordcloudCanvas.value.removeEventListener('mousemove', handleWordcloudMouseMove)
    wordcloudCanvas.value.removeEventListener('mouseleave', handleWordcloudMouseLeave)
  }
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  window.removeEventListener('refresh-profile', refreshProfileData)
  window.removeEventListener('refresh-resources', refreshResourceData)
})

// 监听窗口resize
const handleResize = () => {
  radarInstance?.resize()
  trendInstance?.resize()
  if (wordcloudCanvas.value && wordcloudW > 0) {
    initWordcloud()
  }
}
onMounted(() => window.addEventListener('resize', handleResize))
onBeforeUnmount(() => window.removeEventListener('resize', handleResize))
</script>

<style scoped>
/* ============================================================
   师承智导 首页 — 亮色主题，参考 gemini-code 设计
   背景 #f8f7f4，白色玻璃卡片，无滚动条
   ============================================================ */

/* ─── 基础 ─── */
.student-home {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px 32px 48px;
  min-height: 100vh;
  background: #f8f7f4;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Inter', Roboto, sans-serif;
  color: #1a1a2e;
}
.dashboard-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 隐藏所有滚动条 */
::-webkit-scrollbar { display: none; }
* { -ms-overflow-style: none; scrollbar-width: none; }

/* ============================================================
   顶部栏
   ============================================================ */
.top-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(0,0,0,0.05);
  border-radius: 16px;
  padding: 16px 24px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.03);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.logo-box {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 4px 12px rgba(59,130,246,0.2);
  flex-shrink: 0;
}
.logo-text-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.logo-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.logo-title {
  font-size: 22px;
  font-weight: 800;
  background: linear-gradient(135deg, #1e293b, #3b82f6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.02em;
}
.logo-badge {
  font-size: 10px;
  font-weight: 700;
  color: #3b82f6;
  background: rgba(59,130,246,0.08);
  border: 1px solid rgba(59,130,246,0.15);
  padding: 2px 10px;
  border-radius: 20px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.header-subtitle {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}
.highlight-text {
  color: #3b82f6;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.header-stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255,255,255,0.7);
  border: 1px solid rgba(0,0,0,0.04);
  border-radius: 10px;
  padding: 8px 14px;
  white-space: nowrap;
}
.hs-icon { font-size: 14px; }
.hs-label { font-size: 11px; color: #94a3b8; font-weight: 500; }
.hs-value { font-size: 13px; font-weight: 700; color: #1e293b; }

.subject-select-wrapper {
  position: relative;
}
.subject-select {
  appearance: none;
  background: #fff;
  border: 1px solid rgba(0,0,0,0.08);
  color: #1e293b;
  padding: 8px 32px 8px 14px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  outline: none;
  font-family: inherit;
}
.subject-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59,130,246,0.1);
}
.subject-arrow {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 8px;
  color: #94a3b8;
  pointer-events: none;
}

/* ============================================================
   主区域
   ============================================================ */
.main-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ============================================================
   AI 多智能体协作中心 — 新版流水线布局
   ============================================================ */
.agent-center-card {
  position: relative;
  background: #fff;
  border-radius: 18px;
  padding: 16px 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
}

/* --- 顶部标题栏 --- */
.agent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 16px;
}
.agent-header-left { display: flex; flex-direction: column; gap: 4px; }
.agent-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}
.title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  border-radius: 8px;
  font-size: 14px;
  box-shadow: 0 2px 8px rgba(59,130,246,0.25);
}
.agent-subtitle {
  font-size: 11px;
  color: #94a3b8;
  margin: 0;
}
.agent-header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.meta-status-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(34,197,94,0.08);
  border: 1px solid rgba(34,197,94,0.2);
  border-radius: 20px;
}
.meta-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  animation: pulse 1.5s ease-in-out infinite;
}
.meta-status-text { font-size: 11px; font-weight: 600; color: #16a34a; }
.meta-progress-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 120px;
}
.meta-progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(0,0,0,0.05);
  border-radius: 10px;
  overflow: hidden;
}
.meta-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  border-radius: 10px;
  transition: width 0.6s ease;
}
.meta-progress-text {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
  min-width: 34px;
  text-align: right;
}

/* --- 新版顶部标题栏（更小巧） --- */
.agent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 16px;
}
.title-icon-mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: #fff;
  border-radius: 6px;
  font-size: 12px;
  margin-right: 6px;
}
.agent-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  margin: 0;
}
.agent-subtitle {
  font-size: 11px;
  color: #94a3b8;
  margin: 2px 0 0 0;
}
.agent-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.run-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  background: rgba(34,197,94,0.1);
  border: 1px solid rgba(34,197,94,0.2);
  border-radius: 20px;
  font-size: 10px;
  font-weight: 600;
  color: #16a34a;
}
.run-dot {
  width: 6px;
  height: 6px;
  background: #22c55e;
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}
.top-progress {
  display: flex;
  align-items: center;
  gap: 6px;
}
.top-progress-bar {
  width: 80px;
  height: 5px;
  background: rgba(0,0,0,0.06);
  border-radius: 10px;
  overflow: hidden;
}
.top-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  border-radius: 10px;
}
.top-progress-text {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
}

/* --- 新版 Agent 卡片网格（简洁版） --- */
.agent-grid-v3 {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 10px;
}
.agent-grid-v3 .agent-card-v3 {
  min-width: 0;
}
.agent-card-v3 {
  background: #fff;
  border: 1px solid rgba(0,0,0,0.06);
  border-radius: 12px;
  padding: 14px 12px 12px;
  display: flex;
  flex-direction: column;
  transition: all 0.3s cubic-bezier(.22,1,.36,1);
  position: relative;
  overflow: hidden;
}
.agent-card-v3::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: 14px 14px 0 0;
}
.agent-color-rag::before { background: linear-gradient(90deg, #22c55e, #4ade80); }
.agent-color-tutor::before { background: linear-gradient(90deg, #3b82f6, #60a5fa); }
.agent-color-hallucination::before { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.agent-color-profile::before { background: linear-gradient(90deg, #8b5cf6, #a78bfa); }
.agent-color-recommend::before { background: linear-gradient(90deg, #06b6d4, #22d3ee); }
.agent-color-pathplan::before { background: linear-gradient(90deg, #f97316, #fb923c); }

.agent-card-v3.ac-done {
  background: linear-gradient(180deg, rgba(34,197,94,0.04) 0%, #fff 50%);
}
.agent-card-v3.ac-running {
  background: linear-gradient(180deg, rgba(59,130,246,0.04) 0%, #fff 50%);
  animation: cardBreathe 3s ease-in-out infinite;
}
.agent-card-v3.ac-pending {
  opacity: 0.7;
}
@keyframes cardBreathe {
  0%, 100% { box-shadow: 0 2px 12px rgba(0,0,0,0.03); }
  50% { box-shadow: 0 4px 20px rgba(59,130,246,0.08); }
}

/* --- 内容区域入场动画 --- */
.content-reveal {
  opacity: 0;
  transform: translateY(30px);
  transition: opacity 0.6s cubic-bezier(.22,1,.36,1), transform 0.6s cubic-bezier(.22,1,.36,1);
  pointer-events: none;
  margin-top: 4px;
}
.content-reveal.content-visible {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}

.acv3-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}
.acv3-icon-box {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  position: relative;
}
.agent-color-rag .acv3-icon-box { background: rgba(34,197,94,0.1); }
.agent-color-tutor .acv3-icon-box { background: rgba(59,130,246,0.1); }
.agent-color-hallucination .acv3-icon-box { background: rgba(245,158,11,0.1); }
.agent-color-profile .acv3-icon-box { background: rgba(139,92,246,0.1); }
.agent-color-recommend .acv3-icon-box { background: rgba(6,182,212,0.1); }
.agent-color-pathplan .acv3-icon-box { background: rgba(249,115,22,0.1); }

.acv3-check {
  position: absolute;
  bottom: -3px;
  right: -3px;
  width: 16px;
  height: 16px;
  background: #22c55e;
  color: #fff;
  border-radius: 50%;
  font-size: 9px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(34,197,94,0.4);
}
.acv3-pulse {
  position: absolute;
  bottom: -3px;
  right: -3px;
  width: 12px;
  height: 12px;
  background: #3b82f6;
  border-radius: 50%;
  animation: pulseRing 1.5s ease-in-out infinite;
}
@keyframes pulseRing {
  0% { box-shadow: 0 0 0 0 rgba(59,130,246,0.4); }
  70% { box-shadow: 0 0 0 6px rgba(59,130,246,0); }
  100% { box-shadow: 0 0 0 0 rgba(59,130,246,0); }
}

.acv3-tag {
  font-size: 10px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
  white-space: nowrap;
}
.tag-done { background: rgba(34,197,94,0.12); color: #16a34a; }
.tag-running { background: rgba(59,130,246,0.12); color: #2563eb; }
.tag-pending { background: rgba(0,0,0,0.05); color: #94a3b8; }

.acv3-name {
  font-size: 12px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 2px 0;
}
.acv3-desc {
  font-size: 10px;
  color: #94a3b8;
  margin: 0 0 8px 0;
  line-height: 1.3;
}

.acv3-progress {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.acv3-bar {
  flex: 1;
  height: 4px;
  background: rgba(0,0,0,0.05);
  border-radius: 10px;
  overflow: hidden;
}
.acv3-fill {
  height: 100%;
  border-radius: 10px;
  transition: width 0.6s ease;
}
.acv3-pct {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  font-family: 'JetBrains Mono', monospace;
  min-width: 32px;
  text-align: right;
}

.acv3-stats {
  border-top: 1px solid rgba(0,0,0,0.04);
  padding-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.acv3-stat {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
}
.acv3-stat-label { color: #94a3b8; }
.acv3-stat-value { color: #334155; font-weight: 600; font-family: 'JetBrains Mono', monospace; }

/* --- 资源生成中状态 --- */
.generating-banner {
  margin: 16px 0 8px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #eff6ff 0%, #f0fdf4 100%);
  border-radius: 12px;
  border: 1px solid #bfdbfe;
}
.generating-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.generating-icon {
  font-size: 18px;
  animation: pulse 1.5s ease-in-out infinite;
}
.generating-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e40af;
}
.generating-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.generating-bar {
  flex: 1;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}
.generating-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #22c55e);
  border-radius: 4px;
  transition: width 0.5s ease;
}
.generating-pct {
  font-size: 13px;
  font-weight: 700;
  color: #2563eb;
  font-family: 'JetBrains Mono', monospace;
  min-width: 40px;
  text-align: right;
}
.generating-step {
  font-size: 12px;
  color: #64748b;
  margin: 0;
}

/* --- 底部汇总 --- */
.agent-summary-v3 {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 6px 0 0;
  font-size: 11px;
  color: #64748b;
}
.summary-item { font-weight: 500; }
.summary-sep { color: #cbd5e1; }

/* ============================================================
   AI 今日建议
   ============================================================ */
.monitor-panel {
  background: #fff;
  border-radius: 18px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}
.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.monitor-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.monitor-badge {
  font-size: 9px;
  font-weight: 700;
  color: #06b6d4;
  background: rgba(6,182,212,0.08);
  border: 1px solid rgba(6,182,212,0.15);
  padding: 3px 10px;
  border-radius: 20px;
  font-family: 'JetBrains Mono', monospace;
}

/* ============================================================
   底部 3 列网格
   ============================================================ */
.bottom-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.bottom-col {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ============================================================
   底部新布局：画像 + 知识图谱 + 薄弱环节
   ============================================================ */
.bottom-grid-v2 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
  align-items: start;
  margin-bottom: 8px;
}
.bottom-col-v2 {
  display: flex;
  flex-direction: column;
}

/* 画像卡片：正方形 */
.profile-card-square {
  aspect-ratio: 1 / 1;
  display: flex;
  flex-direction: column;
}
.chart-container-square {
  flex: 1;
  width: 100%;
  min-height: 0;
}

/* 词云卡片 */
.wordcloud-card {
  aspect-ratio: 1 / 1;
  display: flex;
  flex-direction: column;
}
.wordcloud-wrapper {
  position: relative;
  background: #FAFAFA;
  border-radius: 8px;
  flex: 1;
  overflow: hidden;
}
.wordcloud-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.wordcloud-hover-card {
  position: absolute;
  background: #fff;
  border-radius: 10px;
  padding: 12px 16px;
  box-shadow: 0 6px 24px rgba(0,0,0,0.1);
  pointer-events: none;
  z-index: 100;
  animation: wcFadeIn 0.15s ease;
}
@keyframes wcFadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
.wc-title { font-size: 14px; font-weight: 600; color: #1e293b; margin-bottom: 4px; }
.wc-desc { font-size: 12px; color: #64748b; margin-bottom: 6px; line-height: 1.5; }
.wc-tag { font-size: 10px; color: #6366f1; background: #eef2ff; padding: 2px 8px; border-radius: 6px; display: inline-block; }

/* C语言薄弱点卡片：与画像等高 */
.mindmap-card-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.mindmap-card-wrapper :deep(.mindmap-wrapper) {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: 0;
}
.mindmap-card-wrapper :deep(.mindmap-svg-wrap) {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ─── 引导页（未完成导学）─── */
.onboarding-welcome {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 500px;
}
.ow-card {
  text-align: center;
  max-width: 460px;
  padding: 48px 40px;
}
.ow-icon { font-size: 64px; margin-bottom: 16px; }
.ow-card h1 { font-size: 24px; font-weight: 800; color: #1e293b; margin: 0 0 6px; }
.ow-sub { font-size: 14px; color: #64748b; margin: 0 0 28px; }
.ow-features { display: flex; flex-direction: column; gap: 10px; margin-bottom: 32px; }
.owf-item { font-size: 14px; color: #334155; padding: 10px 16px; background: #f8fafc; border-radius: 10px; border: 1px solid rgba(0,0,0,0.04); }
.ow-btn {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 14px 32px; font-size: 16px; font-weight: 700;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; border: none; border-radius: 14px;
  cursor: pointer; transition: all 0.2s; font-family: inherit;
  box-shadow: 0 4px 16px rgba(99,102,241,0.3);
}
.ow-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 24px rgba(99,102,241,0.4); }

/* ─── 玻璃卡片 ─── */
.glass-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
}
.gc-stretch {
  flex: 1;
}
/* — 让底部列的每个卡片都等分高度 — */
.bottom-col .glass-card:last-child {
  flex: 1;
}
.glass-card:hover {
  box-shadow: 0 6px 20px rgba(0,0,0,0.05);
}

/* ─── 底部全宽学习路径 ─── */
.path-bottom-row {
  margin-top: 20px;
}

.gc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.gc-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.gc-subtitle {
  font-size: 10px;
  color: #94a3b8;
}
.gc-footer {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #94a3b8;
  border-top: 1px solid rgba(0,0,0,0.04);
  padding-top: 10px;
  font-family: 'JetBrains Mono', monospace;
}
.fc-purple { color: #8b5cf6; }
.fc-blue { color: #3b82f6; }
.fc-green { color: #22c55e; }

.chart-container {
  width: 100%;
  height: 180px;
}
.graph-chart {
  height: 220px;
}

.mb-12 { margin-bottom: 12px !important; }

/* ─── AI 今日建议 ─── */
.suggestion-box {
  background: rgba(245,158,11,0.06);
  border: 1px solid rgba(245,158,11,0.15);
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 12px;
}
.sug-empty { border-color: rgba(34,197,94,0.2); background: rgba(34,197,94,0.04); }
.sug-heading {
  font-size: 12px;
  font-weight: 700;
  color: #f59e0b;
  margin: 0 0 6px 0;
  display: flex;
  align-items: center;
  gap: 4px;
}
.sug-text {
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
  margin: 0 0 8px 0;
}
.sug-highlight {
  color: #ef4444;
  font-weight: 700;
  text-decoration: underline;
}
.sug-meta {
  display: flex;
  gap: 16px;
  font-size: 11px;
  color: #64748b;
  font-family: 'JetBrains Mono', monospace;
}

/* ===== 艾宾浩斯智能复习（紧凑横向布局） ===== */
.ebbinghaus-horizontal {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.06) 0%, rgba(168, 85, 247, 0.06) 100%);
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 12px;
  padding: 12px 16px;
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.ebbinghaus-h-left {
  flex: 1;
  min-width: 0;
}
.ebbinghaus-h-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.ebbinghaus-h-icon { font-size: 14px; }
.ebbinghaus-h-title {
  font-size: 13px;
  font-weight: 700;
  color: #4f46e5;
}
.ebbinghaus-h-badge {
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: auto;
}
.ebbinghaus-h-body {
  display: flex;
  align-items: center;
  gap: 16px;
}
.ebbinghaus-h-tags {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.kp-tag {
  font-size: 11px;
  font-weight: 500;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.12);
  padding: 2px 8px;
  border-radius: 6px;
}
.ebbinghaus-h-progress-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 100px;
}
.progress-bar-bg {
  flex: 1;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
}
.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1 0%, #a855f7 100%);
  border-radius: 3px;
  transition: width 0.3s ease;
}
.ebbinghaus-h-pct {
  font-size: 12px;
  font-weight: 700;
  color: #4f46e5;
  min-width: 32px;
}
.ebbinghaus-h-timeline-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ebbinghaus-h-timeline {
  display: flex;
  align-items: center;
}
.tl-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e2e8f0;
  flex-shrink: 0;
}
.tl-dot.done { background: #22c55e; }
.tl-dot.active {
  background: #6366f1;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}
.tl-line {
  width: 16px;
  height: 2px;
  background: #e2e8f0;
}
.tl-line.done { background: #22c55e; }
.tl-line.active { background: linear-gradient(90deg, #22c55e, #6366f1); }
.ebbinghaus-h-next {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}
.ebbinghaus-h-empty {
  font-size: 12px;
  color: #94a3b8;
}
.ebbinghaus-h-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.ebbinghaus-h-stats {
  display: flex;
  gap: 12px;
}
.eh-stat-item {
  text-align: center;
}
.eh-stat-num {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #4f46e5;
  line-height: 1;
}
.eh-stat-label {
  display: block;
  font-size: 10px;
  color: #94a3b8;
  margin-top: 2px;
}
.ebbinghaus-h-btn {
  padding: 8px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
  white-space: nowrap;
}
.ebbinghaus-h-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
}

/* kp-tags 共用 */
.kp-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.kp-tag {
  font-size: 11px;
  font-weight: 500;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.12);
  padding: 3px 8px;
  border-radius: 6px;
}

.task-box {
  background: rgba(0,0,0,0.02);
  border: 1px solid rgba(0,0,0,0.04);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 14px;
}
.task-label {
  display: block;
  font-size: 10px;
  color: #94a3b8;
  font-weight: 500;
  margin-bottom: 4px;
}
.task-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 2px;
}
.task-meta {
  font-size: 10px;
  color: #94a3b8;
}

.start-btn {
  width: 100%;
  padding: 10px 0;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 4px 14px rgba(59,130,246,0.2);
}
.start-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(59,130,246,0.3);
}

/* ─── AI 生成资源 ─── */
.res-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.res-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}
.res-item:hover {
  background: rgba(0,0,0,0.02);
  border-color: rgba(0,0,0,0.04);
  transform: translateX(3px);
}
.res-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.res-body {
  flex: 1;
  min-width: 0;
}
.res-name {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.res-time {
  font-size: 10px;
  color: #94a3b8;
}
.res-tag {
  font-size: 9px;
  font-weight: 600;
  color: #22c55e;
  background: rgba(34,197,94,0.08);
  border: 1px solid rgba(34,197,94,0.15);
  padding: 2px 10px;
  border-radius: 6px;
}
.res-empty {
  text-align: center;
  padding: 16px 0;
}
.res-empty-icons {
  font-size: 24px;
  margin-bottom: 10px;
  letter-spacing: 8px;
}
.res-empty p {
  font-size: 12px;
  color: #94a3b8;
  margin: 0;
  line-height: 1.6;
}



/* ============================================================
   SKELETON LOADING
   ============================================================ */
.skeleton-wrapper {
  display: flex;
  flex-direction: column;
  gap: 20px;
  animation: skeletonPulse 1.6s ease-in-out infinite;
}
.skeleton-hero {
  height: 180px;
  border-radius: 20px;
  background: linear-gradient(90deg, #f1f1ee 25%, #f8f7f4 50%, #f1f1ee 75%);
  background-size: 200% 100%;
  animation: shimmerBg 1.6s ease-in-out infinite;
}
.skeleton-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.skeleton-card-item {
  height: 76px;
  border-radius: 14px;
  background: linear-gradient(90deg, #f1f1ee 25%, #f8f7f4 50%, #f1f1ee 75%);
  background-size: 200% 100%;
  animation: shimmerBg 1.6s ease-in-out infinite;
}
.skeleton-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.skeleton-block {
  border-radius: 14px;
  background: linear-gradient(90deg, #f1f1ee 25%, #f8f7f4 50%, #f1f1ee 75%);
  background-size: 200% 100%;
  animation: shimmerBg 1.6s ease-in-out infinite;
}
.skeleton-block.tall { height: 320px; }

@keyframes skeletonPulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
@keyframes shimmerBg {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ============================================================
   RESPONSIVE
   ============================================================ */
@media (max-width: 1200px) {
  .main-grid { grid-template-columns: 1fr; }
  .agent-grid-v3 { grid-template-columns: repeat(4, 1fr); }
  .bottom-grid-v2 { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 1024px) {
  .bottom-grid { grid-template-columns: 1fr 1fr; }
  .bottom-grid-v2 { grid-template-columns: 1fr; }
  .top-header { flex-wrap: wrap; }
  .header-subtitle { white-space: normal; }
}
@media (max-width: 900px) {
  .agent-grid-v3 { grid-template-columns: repeat(3, 1fr); }
  .header-right { flex-wrap: wrap; }
}
@media (max-width: 768px) {
  .student-home { padding: 16px; }
  .bottom-grid { grid-template-columns: 1fr; }
  .agent-grid-v3 { grid-template-columns: repeat(2, 1fr); }
  .skeleton-stats { grid-template-columns: 1fr 1fr; }
  .skeleton-grid { grid-template-columns: 1fr; }
}
@media (max-width: 480px) {
  .agent-grid-v3 { grid-template-columns: 1fr; }
  .header-right { flex-direction: column; align-items: stretch; }
  .header-stat-item { justify-content: center; }
}

/* 空状态 */
.empty-onboarding {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: 60vh; text-align: center; color: #666;
}
.empty-onboarding .empty-icon { font-size: 64px; margin-bottom: 16px; }
.empty-onboarding h3 { font-size: 20px; color: #333; margin-bottom: 8px; }
.empty-onboarding p { font-size: 14px; max-width: 400px; line-height: 1.6; margin-bottom: 24px; }
.btn-start-onboarding {
  background: #4A90E2; color: #fff; border: none; padding: 12px 32px;
  border-radius: 8px; font-size: 16px; cursor: pointer; transition: background 0.2s;
}
.btn-start-onboarding:hover { background: #357ABD; }

/* ===== 词云区块 ===== */
</style>
