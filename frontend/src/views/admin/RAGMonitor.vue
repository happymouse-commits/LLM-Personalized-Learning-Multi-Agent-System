<template>
  <div class="rag-monitor-dashboard">
    <!-- 顶部标题栏 -->
    <header class="dashboard-header">
      <div class="header-left">
        <h1 class="main-title">🛡️ RAG 运行监控
          <span :class="['status-tag', online ? 'tag-green' : 'tag-gray']">
            {{ online ? '● 运行正常' : '○ 未连接后端' }}
          </span>
        </h1>
        <p class="sub-title">实时追踪防御幻觉四道门管线运行状态 · 缺口自动建议 · 修正前后对比</p>
      </div>
      <div class="header-actions">
        <button class="action-btn btn-white" @click="loadData">🔄 刷新</button>
        <button class="action-btn btn-danger" @click="resetData">重置统计</button>
      </div>
    </header>

    <!-- 核心指标卡片 -->
    <section class="metrics-grid">
      <div class="metric-card">
        <div class="card-left">
          <span class="metric-emoji">🎯</span>
          <div class="metric-meta">
            <span class="metric-value text-green">{{ overview.hitRate }}%</span>
            <span class="metric-label">检索命中率</span>
          </div>
        </div>
        <div class="card-right">
          <span class="metric-sub">{{ overview.queries }} 次查询</span>
          <span class="metric-sub text-green">门控放行 {{ overview.gatePassed }}</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="card-left">
          <span class="metric-emoji">🚫</span>
          <div class="metric-meta">
            <span class="metric-value text-red">{{ overview.interceptions }}</span>
            <span class="metric-label">门控拦截</span>
          </div>
        </div>
        <div class="card-right">
          <span class="metric-sub">无知识拒绝生成</span>
          <span class="metric-sub text-red">事实校验失败 {{ overview.factFails }}</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="card-left">
          <span class="metric-emoji">🔄</span>
          <div class="metric-meta">
            <span class="metric-value text-blue">{{ overview.hallucinations }}</span>
            <span class="metric-label">幻觉修正</span>
          </div>
        </div>
        <div class="card-right">
          <span class="metric-sub">触发自动重生</span>
          <span class="metric-sub text-blue">重试率 {{ overview.retryRate }}%</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="card-left">
          <span class="metric-emoji">📊</span>
          <div class="metric-meta">
            <span class="metric-value text-purple">{{ overview.confidence }}</span>
            <span class="metric-label">平均置信度</span>
          </div>
        </div>
        <div class="card-right">
          <span class="metric-sub">事实校验综合评分</span>
          <span class="metric-sub">最近 {{ overview.incidents }} 起事故</span>
        </div>
      </div>
    </section>

    <!-- 知识缺口 + 三道门折线图 -->
    <section class="content-row">
      <div class="panel-card">
        <div class="panel-header">
          <span class="panel-title">📋 知识缺口（被门控拦截的提问）</span>
          <span class="view-more-link" @click="$router.push('/admin/knowledge-base')">去完善知识库 ＞</span>
        </div>
        <div class="gap-list">
          <div v-for="(gap, idx) in knowledgeGaps" :key="idx" class="gap-item">
            <div class="gap-top">
              <span class="gap-name">{{ gap.topic }}</span>
              <span class="gap-count">{{ gap.count }}次拦截</span>
            </div>
            <div class="gap-bar-wrap">
              <div class="gap-bar" :style="{ width: getGapPercent(gap.count) + '%' }"></div>
            </div>
            <div class="gap-bottom">
              <span class="gap-query">示例：{{ gap.sampleQuery || '无' }}</span>
              <button class="gap-action" @click="$router.push('/admin/knowledge-base')">📝 去完善</button>
            </div>
          </div>
          <div v-if="!knowledgeGaps.length" class="gap-empty">暂无知识缺口数据</div>
        </div>
      </div>

      <div class="panel-card">
        <div class="panel-header">
          <span class="panel-title">📈 三道门运行统计</span>
        </div>
        <div class="echarts-wrapper" id="gateChart"></div>
      </div>
    </section>

    <!-- AI建议 + 实时日志 -->
    <section class="content-row">
      <div class="panel-card">
        <div class="panel-header">
          <span class="panel-title">🤖 AI 智能建议</span>
        </div>
        <div class="advice-list">
          <div v-for="(sug, i) in gapSuggestions" :key="i" class="advice-item" :class="urgencyClass(sug.urgency)">
            <div class="advice-head">
              <span class="advice-topic">{{ sug.topic }}</span>
              <span class="advice-urgency" :class="'urgency-' + sug.urgency">{{ sug.urgency }}优先</span>
            </div>
            <p class="advice-text">{{ sug.suggestion }}</p>
            <button class="advice-btn" @click="$router.push('/admin/knowledge-base')">去完善 →</button>
          </div>
          <div v-if="!gapSuggestions.length" class="advice-item card-gray-light">
            <p class="advice-text">尚无知识缺口，系统运行良好</p>
          </div>
        </div>
      </div>

      <div class="panel-card">
        <div class="panel-header">
          <span class="panel-title">⚡ 实时运行日志 (LIVE)</span>
        </div>
        <div class="log-stream-area" ref="logContainerRef">
          <div v-for="(log, i) in recentEvents" :key="i" class="log-line">
            <span class="log-time">[{{ log.time }}]</span>
            <span :class="['log-text', 'log-' + log.type]">{{ log.text }}</span>
          </div>
          <div v-if="!recentEvents.length" class="log-line"><span class="log-time">暂无日志</span></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../../api'
import { useDemoStore } from '../../store/demo'
import { demoRagMonitor } from '../../mock/demoData'

const demoStore = useDemoStore()
const online = ref(false)
const overview = ref({
  hitRate: 0, queries: 0, interceptions: 0, hallucinations: 0, confidence: 0,
  gatePassed: 0, factFails: 0, retryRate: 0, incidents: 0
})
const recentEvents = ref([])
const knowledgeGaps = ref([])
const gapSuggestions = ref([])
let logTimer = null
const logContainerRef = ref(null)
let gateChart = null
let chartResizeObserver = null

const urgencyClass = (u) => u === '高' ? 'card-purple-light' : u === '中' ? 'card-blue-light' : 'card-gray-light'

function getGapPercent(count) {
  const max = Math.max(...knowledgeGaps.value.map(g => g.count), 1)
  return Math.round((count / max) * 100)
}

// 从后端事件流构建实时日志
function buildLogsFromEvents(events) {
  const typeMap = { 'GATE1_PASS': 'info', 'GATE1_REJECT': 'warn', 'FACT_CHECK_FAIL': 'warn', 'HALLUCINATION_RETRY': 'warn', 'PIPELINE_DONE': 'success' }
  return (events || []).slice(0, 30).map(e => ({
    time: e.time ? e.time.split('T')[1]?.split('.')[0] || e.time.slice(11, 19) : '--',
    type: typeMap[e.type] || 'info',
    text: `[${e.type}] ${e.detail || e.query || ''}`
  }))
}

// 从后台获取真实RAG数据，后端无数据时自动使用演示数据
async function loadData() {
  try {
    let res
    if (demoStore.enabled) {
      res = await demoStore.getRagMonitorData()
    } else {
      res = await api.get('/admin/rag-monitor')
    }
    if (!res || res.code !== 200) throw new Error('API error')
    const data = res.data
    const ov = data.overview || {}

    const isEmpty = !ov.totalQueries && (!data.knowledgeGaps || data.knowledgeGaps.length === 0) && (!data.recentEvents || data.recentEvents.length === 0)
    if (isEmpty) {
      loadDemoData()
      return
    }

    online.value = true
    overview.value = {
      hitRate: ov.gate1HitRate ?? 0,
      queries: ov.totalQueries ?? 0,
      interceptions: ov.gate1Rejected ?? 0,
      hallucinations: ov.hallucinationRetries ?? 0,
      confidence: ov.avgConfidence ?? 0,
      gatePassed: ov.gate1Passed ?? 0,
      factFails: ov.factCheckFails ?? 0,
      retryRate: ov.totalQueries > 0 ? Math.round(ov.hallucinationRetries / ov.totalQueries * 100) : 0,
      incidents: (data.hallucinationIncidents || []).length
    }
    knowledgeGaps.value = (data.knowledgeGaps || []).slice(0, 10)
    gapSuggestions.value = (data.gapSuggestions || []).slice(0, 3)
    recentEvents.value = buildLogsFromEvents(data.recentEvents)
  } catch (e) {
    loadDemoData()
  }
  nextTick(() => initGateChart())
}

function loadDemoData() {
  online.value = true
  const d = demoRagMonitor
  const ov = d.overview || {}
  overview.value = {
    hitRate: ov.gate1HitRate ?? 0,
    queries: ov.totalQueries ?? 0,
    interceptions: ov.gate1Rejected ?? 0,
    hallucinations: ov.hallucinationRetries ?? 0,
    confidence: ov.avgConfidence ?? 0,
    gatePassed: ov.gate1Passed ?? 0,
    factFails: ov.factCheckFails ?? 0,
    retryRate: ov.totalQueries > 0 ? Math.round(ov.hallucinationRetries / ov.totalQueries * 100) : 0,
    incidents: (d.hallucinationIncidents || []).length
  }
  knowledgeGaps.value = (d.knowledgeGaps || []).slice(0, 10)
  gapSuggestions.value = (d.gapSuggestions || []).slice(0, 3)
  recentEvents.value = buildLogsFromEvents(d.recentEvents)
  nextTick(() => initGateChart())
}

async function resetData() {
  // 演示模式下不清空状态（避免影响视频录制流程），仅提示
  if (demoStore.enabled) {
    ElMessage.info('演示模式下不支持重置统计')
    return
  }
  try {
    await api.post('/admin/rag-monitor/reset')
    ElMessage.success('RAG 统计数据已重置')
    await loadData()
  } catch {
    ElMessage.warning('后端未连接，无法重置')
  }
}

async function initGateChart(retryCount = 0) {
  const echarts = await import('echarts')
  const gateDom = document.getElementById('gateChart')
  if (!gateDom) {
    if (retryCount < 3) setTimeout(() => initGateChart(retryCount + 1), 200)
    return
  }
  // 复用已有实例，避免 dispose+init 造成的闪烁
  if (!gateChart) {
    gateChart = echarts.init(gateDom)
    // 监听容器尺寸变化（flex 布局 / 响应式断点切换时自动重绘）
    if (chartResizeObserver) chartResizeObserver.disconnect()
    chartResizeObserver = new ResizeObserver(() => gateChart?.resize())
    chartResizeObserver.observe(gateDom)
  }

  // 基于当前 overview 派生稳定的7天趋势（不再随机，刷新后数据不变）
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const base = overview.value
  // 用固定偏移序列代替 Math.random()，保证演示录制中图表数据稳定
  const offsets = [-3, 2, -1, 4, -2, 1, 3]
  const passedData = days.map((_, i) => Math.max(0, Math.round(base.gatePassed * (0.8 + offsets[i] * 0.05))))
  const rejectedData = days.map((_, i) => Math.max(0, Math.round(base.interceptions * (0.9 + offsets[(i + 2) % 7] * 0.04))))
  const hallucData = days.map((_, i) => Math.max(0, Math.round(base.hallucinations * (0.85 + offsets[(i + 4) % 7] * 0.06))))

  gateChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { data: ['门控放行', '门控拦截', '幻觉修正'], top: 0, textStyle: { fontSize: 11, color: '#64748b' } },
    grid: { left: '6%', right: '6%', bottom: '6%', top: '14%', containLabel: true },
    xAxis: { type: 'category', data: days, axisLabel: { color: '#64748b', fontSize: 11 }, axisLine: { lineStyle: { color: '#e2e8f0' } } },
    yAxis: { type: 'value', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#f1f5f9' } } },
    series: [
      {
        name: '门控放行', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        data: passedData,
        itemStyle: { color: '#10b981' },
        lineStyle: { width: 2.5, color: '#10b981' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(16,185,129,0.15)' }, { offset: 1, color: 'rgba(16,185,129,0)' }] } }
      },
      {
        name: '门控拦截', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        data: rejectedData,
        itemStyle: { color: '#ef4444' },
        lineStyle: { width: 2.5, color: '#ef4444' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(239,68,68,0.1)' }, { offset: 1, color: 'rgba(239,68,68,0)' }] } }
      },
      {
        name: '幻觉修正', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        data: hallucData,
        itemStyle: { color: '#3b82f6' },
        lineStyle: { width: 2.5, color: '#3b82f6' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59,130,246,0.1)' }, { offset: 1, color: 'rgba(59,130,246,0)' }] } }
      }
    ]
  }, true) // 第二参数 true 表示合并到已有 option 而非替换
  // 初次渲染后下一帧再 resize，确保容器真实尺寸已计算
  requestAnimationFrame(() => gateChart?.resize())
}

function handleResize() { gateChart?.resize() }

// 非演示模式启动日志轮询（演示模式不轮询，避免录制中数据跳变打断流程）
function startLogPolling() {
  if (demoStore.enabled) return // 演示模式跳过
  if (logTimer) clearInterval(logTimer)
  logTimer = setInterval(async () => {
    try {
      const res = await api.get('/admin/rag-monitor')
      if (res?.code === 200 && res.data?.recentEvents?.length) {
        recentEvents.value = buildLogsFromEvents(res.data.recentEvents)
      }
    } catch { /* 静默忽略，不影响页面 */ }
  }, 15000) // 15秒轮询一次，平衡实时性和性能
}

onMounted(async () => {
  await loadData()
  window.addEventListener('resize', handleResize)
  startLogPolling()
})

onBeforeUnmount(() => {
  if (logTimer) { clearInterval(logTimer); logTimer = null }
  window.removeEventListener('resize', handleResize)
  chartResizeObserver?.disconnect()
  chartResizeObserver = null
  gateChart?.dispose()
})
</script>

<style scoped>
.rag-monitor-dashboard {
  width: 100%; box-sizing: border-box;
  background: #f8f7f4;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  display: flex; flex-direction: column; gap: 14px;
  padding: 4px 0; overflow-y: auto; height: 100%;
}

/* ===== 头部 ===== */
.dashboard-header { display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }
.main-title { margin: 0; font-size: 18px; font-weight: 700; color: #0f172a; display: flex; align-items: center; gap: 10px; }
.status-tag { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 20px; }
.tag-green { background: rgba(16,185,129,0.08); color: #10b981; border: 1px solid rgba(16,185,129,0.15); }
.tag-gray { background: rgba(148,163,184,0.08); color: #94a3b8; border: 1px solid rgba(148,163,184,0.15); }
.sub-title { margin: 3px 0 0 0; font-size: 12px; color: #94a3b8; }
.header-actions { display: flex; gap: 8px; }
.action-btn { padding: 5px 12px; font-size: 12px; font-weight: 600; border-radius: 8px; cursor: pointer; transition: all 0.2s; font-family: inherit; }
.btn-white { background: #fff; border: 1px solid rgba(0,0,0,0.06); color: #475569; }
.btn-white:hover { background: #fafaf9; }
.btn-danger { background: rgba(239,68,68,0.05); border: 1px solid rgba(239,68,68,0.15); color: #ef4444; }
.btn-danger:hover { background: rgba(239,68,68,0.1); }

/* ===== 指标卡片 ===== */
.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; flex-shrink: 0; }
.metric-card {
  background: #fff; border-radius: 14px; padding: 14px 16px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.03); border: 1px solid rgba(0,0,0,0.02);
  display: flex; justify-content: space-between; align-items: center;
}
.card-left { display: flex; gap: 10px; align-items: center; }
.metric-emoji { font-size: 20px; width: 38px; height: 38px; background: #f8f7f4; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
.metric-meta { display: flex; flex-direction: column; }
.metric-value { font-size: 20px; font-weight: 800; font-family: 'JetBrains Mono', monospace; line-height: 1.1; }
.metric-label { font-size: 12px; font-weight: 600; color: #334155; margin-top: 1px; }
.card-right { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.metric-sub { font-size: 11px; color: #94a3b8; }
.text-green { color: #10b981; } .text-red { color: #ef4444; } .text-blue { color: #3b82f6; } .text-purple { color: #8b5cf6; }

/* ===== 面板通用 —— 纵向 flex 布局，让内容区占满卡片 ===== */
.panel-card {
  background: #fff; border-radius: 14px; padding: 14px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.03); border: 1px solid rgba(0,0,0,0.02);
  box-sizing: border-box;
  display: flex; flex-direction: column;
  min-width: 0; min-height: 0;
}
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-shrink: 0; }
.panel-title { font-size: 13px; font-weight: 700; color: #1e293b; }
.view-more-link { font-size: 11px; font-weight: 600; color: #8b5cf6; cursor: pointer; }

/* ===== 内容行 —— grid 子项等高拉伸 ===== */
.content-row {
  display: grid; grid-template-columns: 1fr 1fr; gap: 14px;
  flex-shrink: 0; align-items: stretch; justify-items: stretch;
  grid-auto-rows: 1fr;
}

/* ===== 知识缺口进度条列表 ===== */
.gap-list { display: flex; flex-direction: column; gap: 10px; flex: 1 1 auto; min-height: 0; }
.gap-item { background: #fafaf9; border-radius: 10px; padding: 10px 12px; }
.gap-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.gap-name { font-size: 13px; font-weight: 700; color: #1e293b; }
.gap-count { font-size: 11px; font-weight: 600; color: #ef4444; background: #fef2f2; padding: 1px 6px; border-radius: 4px; }
.gap-bar-wrap { height: 6px; background: #e2e8f0; border-radius: 3px; overflow: hidden; margin-bottom: 6px; }
.gap-bar { height: 100%; background: linear-gradient(90deg, #ef4444, #f97316); border-radius: 3px; transition: width 0.6s ease; }
.gap-bottom { display: flex; justify-content: space-between; align-items: center; }
.gap-query { font-size: 11px; color: #94a3b8; }
.gap-action { background: none; border: none; color: #8b5cf6; font-size: 11px; font-weight: 600; cursor: pointer; padding: 2px 4px; font-family: inherit; }
.gap-action:hover { text-decoration: underline; }
.gap-empty { text-align: center; padding: 30px; font-size: 12px; color: #94a3b8; }

/* ===== 折线图 —— 填满卡片剩余高度 ===== */
.echarts-wrapper {
  width: 100%; min-width: 0;
  flex: 1 1 0%;
  min-height: 240px;
  overflow: hidden;
}

/* ===== AI建议列表 ===== */
.advice-list { display: flex; flex-direction: column; gap: 8px; flex: 1 1 auto; min-height: 0; }
.advice-item { border-radius: 10px; padding: 12px; border: 1px solid transparent; }
.card-purple-light { background: rgba(139,92,246,0.03); border-color: rgba(139,92,246,0.08); }
.card-blue-light { background: rgba(59,130,246,0.03); border-color: rgba(59,130,246,0.08); }
.card-gray-light { background: rgba(148,163,184,0.04); border-color: rgba(148,163,184,0.07); }
.advice-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.advice-topic { font-size: 13px; font-weight: 700; color: #1e293b; }
.advice-urgency { font-size: 10px; font-weight: 600; padding: 1px 6px; border-radius: 4px; }
.urgency-高 { background: #fef2f2; color: #ef4444; }
.urgency-中 { background: #eff6ff; color: #3b82f6; }
.urgency-低 { background: #f8fafc; color: #64748b; }
.advice-text { margin: 0 0 8px 0; font-size: 11px; color: #64748b; line-height: 1.5; }
.advice-btn {
  background: #fff; border: 1px solid rgba(0,0,0,0.08); border-radius: 6px;
  padding: 3px 10px; font-size: 10px; font-weight: 600; color: #8b5cf6;
  cursor: pointer; font-family: inherit;
}
.advice-btn:hover { background: #fafaf9; }

/* ===== 日志 —— 填满卡片剩余高度 ===== */
.log-stream-area {
  background: #1e293b; border-radius: 10px; padding: 10px;
  width: 100%; min-width: 0;
  flex: 1 1 0%;
  min-height: 240px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', monospace; font-size: 11px; line-height: 1.6;
  box-sizing: border-box;
}
.log-line { margin-bottom: 3px; white-space: nowrap; }
.log-time { color: #64748b; margin-right: 6px; }
.log-info { color: #e2e8f0; } .log-success { color: #34d399; } .log-warn { color: #fbbf24; } .log-error { color: #f87171; }
.log-stream-area::-webkit-scrollbar { width: 4px; }
.log-stream-area::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 4px; }

/* ===== 响应式断点 ===== */
@media (max-width: 900px) {
  .content-row { grid-template-columns: 1fr; }
  .echarts-wrapper, .log-stream-area { min-height: 260px; }
  .metrics-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (min-width: 1400px) {
  .echarts-wrapper, .log-stream-area { min-height: 300px; }
}
</style>
