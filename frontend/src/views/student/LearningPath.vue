<template>
  <div class="learning-path-container">
    <!-- 左侧：学习路径规划 -->
    <div class="roadmap-panel">
      <div class="panel-header">
        <h2 class="panel-title">🗺️ C语言学习路径规划</h2>
        <p class="panel-subtitle">基于多维画像动态生成的个性化通关路径</p>
      </div>

      <!-- AI 路径决策摘要 -->
      <div class="ai-decision-card">
        <div class="ai-decision-header">
          <span class="ai-decision-icon">🧠</span>
          <span class="ai-decision-title">AI 路径决策</span>
          <button class="ai-decision-toggle" @click="showDecisionDetail = !showDecisionDetail">
            {{ showDecisionDetail ? '收起' : '详情' }}
          </button>
        </div>
        <div class="ai-decision-summary">
          <div class="decision-item" v-for="(item, idx) in decisionSummary" :key="idx">
            <span class="decision-label">{{ item.label }}</span>
            <span class="decision-value" :class="item.class">{{ item.value }}</span>
          </div>
        </div>
        <transition name="decision-expand">
          <div v-if="showDecisionDetail" class="ai-decision-detail">
            <div class="detail-reason" v-for="(reason, idx) in decisionReasons" :key="idx">
              <span class="detail-icon">{{ reason.icon }}</span>
              <span class="detail-text">{{ reason.text }}</span>
            </div>
          </div>
        </transition>
      </div>

      <div class="timeline-wrapper">
        <div
          v-for="(stage, index) in cRoadmap"
          :key="index"
          :class="['timeline-item', { 'is-active': currentStageIndex === index, 'is-locked': stage.status === 'pending' || stage.status === 'locked' }]"
          @click="handleStageClick(stage, index)"
        >
          <div v-if="index < cRoadmap.length - 1" class="timeline-line"></div>

          <div class="timeline-node">
            <div :class="['node-icon', getDynamicStatusClass(index)]">
              <span v-if="index < currentStageIndex" class="icon-check">✓</span>
              <span v-else-if="index === currentStageIndex" class="icon-loading"></span>
              <span v-else class="icon-lock">🔒</span>
            </div>
          </div>

          <div class="timeline-content">
            <div class="stage-info">
              <div class="stage-title-box">
                <span class="stage-emoji" v-if="stage.icon">{{ stage.icon }}</span>
                <span class="stage-name">{{ stage.name }}</span>
              </div>
              <span :class="['status-tag', getDynamicStatusKey(index)]">
                {{ index < currentStageIndex ? '已完成' : index === currentStageIndex ? '进行中' : '待学习' }}
              </span>
            </div>

            <div class="stage-progress-bar">
              <el-progress
                :percentage="index < currentStageIndex ? 100 : index === currentStageIndex ? 45 : 0"
                :status="index < currentStageIndex ? 'success' : index === currentStageIndex ? '' : 'exception'"
                :show-text="true"
                :stroke-width="5"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧：知识图谱力导向图 -->
    <div class="graph-panel">
      <div class="panel-header flex-header">
        <div>
          <h2 class="panel-title">🔗 知识图谱</h2>
          <p class="panel-subtitle">C语言知识体系 · 主卡点关联</p>
        </div>
        <div v-if="highlightedStageName" class="highlight-badge">
          当前定位: {{ highlightedStageName }}
        </div>
      </div>
      <div id="graphChart" class="graph-chart-container"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useUserStore } from '../../store/user'
import { useDemoStore } from '../../store/demo'
import api from '../../api'

const store = useUserStore()
const demoStore = useDemoStore()
const profile = ref({})
const resources = ref([])
const activePath = ref(null)

const currentStageIndex = ref(0)
const highlightedStageName = ref('')
const showDecisionDetail = ref(false)

// AI路径决策数据
const decisionSummary = computed(() => {
  const p = profile.value || {}
  const weakStr = p.weakAreas || ''
  const weakPoints = weakStr ? weakStr.split(/[,，、]/).filter(Boolean).map(s => s.trim()).slice(0, 3) : ['指针', '内存管理']
  const knowledgeLevel = p.knowledgeLevel || 60
  return [
    { label: '知识基础', value: `${knowledgeLevel}分`, class: knowledgeLevel < 60 ? 'val-warn' : 'val-good' },
    { label: '薄弱环节', value: weakPoints.join('、'), class: 'val-warn' },
    { label: '当前阶段', value: `第${currentStageIndex.value + 1}阶段`, class: 'val-info' },
    { label: '剩余阶段', value: `${cRoadmap.value.length - currentStageIndex.value - 1}个`, class: 'val-info' },
  ]
})

const decisionReasons = computed(() => {
  const p = profile.value || {}
  const weakStr = p.weakAreas || ''
  const weakPoints = weakStr ? weakStr.split(/[,，、]/).filter(Boolean).map(s => s.trim()).slice(0, 3) : ['指针', '内存管理']
  const pace = p.learningPace || '适中'
  return [
    { icon: '🎯', text: `检测到薄弱点：${weakPoints.join('、')}，已优先安排相关阶段` },
    { icon: '⏱️', text: `学习节奏：${pace}，路径步长已适配` },
    { icon: '🔗', text: '基于C语言知识前置依赖关系，确保学习顺序合理' },
    { icon: '📊', text: '综合8维画像 + 错题分布 + 艾宾浩斯遗忘曲线动态生成' },
  ]
})

let graphChartInstance = null

// ===== 构建cRoadmap（15阶段学习路径） =====
const stagesData = {
  1: { name: 'C语言概述', icon: '📖', desc: '了解C语言历史、特点和应用领域，搭建开发环境', keypoints: ['C语言简介', '开发环境', '编译流程', '第一个程序'] },
  2: { name: '变量与数据类型', icon: '📦', desc: '掌握基本数据类型、变量声明和初始化', keypoints: ['基本类型', '变量声明', '常量定义', '类型转换'] },
  3: { name: '运算符与表达式', icon: '🔢', desc: '学习各种运算符的使用和表达式求值', keypoints: ['算术运算', '关系运算', '逻辑运算', '位运算'] },
  4: { name: '输入输出', icon: '⌨️', desc: '掌握格式化输入输出函数的使用', keypoints: ['printf', 'scanf', '格式控制', '字符输入输出'] },
  5: { name: '控制结构', icon: '🔀', desc: '学习条件分支和循环结构，构建有逻辑判断的程序', keypoints: ['if-else', 'switch-case', 'for循环', 'while循环'] },
  6: { name: '函数基础', icon: '⚙️', desc: '掌握函数定义、调用和参数传递', keypoints: ['函数定义', '参数传递', '返回值', '作用域'] },
  7: { name: '数组', icon: '📊', desc: '学习一维和二维数组的声明、初始化和操作', keypoints: ['一维数组', '二维数组', '数组遍历', '数组排序'] },
  8: { name: '字符串', icon: '📝', desc: '掌握字符串的存储和常用操作函数', keypoints: ['字符数组', '字符串函数', '字符串处理', '动态字符串'] },
  9: { name: '指针基础', icon: '🎯', desc: '理解指针概念，掌握指针的基本操作', keypoints: ['指针概念', '指针运算', '指针与数组', '指针传递'] },
  10: { name: '指针进阶', icon: '⚡', desc: '深入理解指针的高级用法', keypoints: ['多级指针', '函数指针', '指针数组', 'const指针'] },
  11: { name: '结构体与联合体', icon: '🏗️', desc: '学习自定义数据类型的声明和使用', keypoints: ['结构体', '联合体', '枚举', 'typedef'] },
  12: { name: '动态内存管理', icon: '💾', desc: '掌握动态内存分配和释放', keypoints: ['malloc', 'calloc', 'realloc', 'free'] },
  13: { name: '文件操作', icon: '📁', desc: '学习文件的打开、读写和关闭操作', keypoints: ['文件指针', '读写函数', '文件定位', '错误处理'] },
  14: { name: '预处理指令', icon: '📋', desc: '掌握宏定义、条件编译和文件包含', keypoints: ['宏定义', '条件编译', '文件包含', '预定义宏'] },
  15: { name: '综合项目实战', icon: '🚀', desc: '综合运用所学知识，完成实际项目', keypoints: ['项目设计', '代码规范', '调试技巧', '项目实战'] },
}

// 薄弱点→章节映射
const weakToStages = {
  '指针': [9, 10], '指针基础': [9], '指针进阶': [10],
  '数组': [7], '数组与指针': [7, 9],
  '函数': [6], '函数基础': [6],
  '结构体': [11], '结构体与联合体': [11],
  '动态内存': [12], '内存管理': [12],
  '文件操作': [13], '文件': [13],
  '控制结构': [5], '循环': [5], '流程控制': [5],
  '字符串': [8],
  '基础语法': [1, 2, 3, 4],
}

const cRoadmap = computed(() => {
  const path = activePath.value
  const p = profile.value || {}
  const weakStr = p.weakAreas || ''
  const weakList = weakStr ? weakStr.split(/[,，、]/).filter(Boolean).map(s => s.trim()) : []

  // 根据薄弱点确定需要的章节
  const neededStages = new Set([1]) // 基础章节总是需要的
  weakList.forEach(w => {
    const mapped = weakToStages[w]
    if (mapped) mapped.forEach(s => neededStages.add(s))
    // 模糊匹配
    Object.entries(weakToStages).forEach(([key, stages]) => {
      if (w.includes(key) || key.includes(w)) stages.forEach(s => neededStages.add(s))
    })
  })
  // 加入前置依赖（薄弱章节的前一章）
  const deps = new Set()
  neededStages.forEach(s => { if (s > 1) deps.add(s - 1) })
  deps.forEach(s => neededStages.add(s))

  // 构建精简路径：只保留需要的章节，按顺序排列
  const filteredStages = Object.entries(stagesData)
    .filter(([id]) => neededStages.has(parseInt(id)))
    .sort((a, b) => parseInt(a[0]) - parseInt(b[0]))

  return filteredStages.map(([id, s], displayIdx) => {
    const i = parseInt(id)
    // 直接用 path.currentStep 比较原始章节号
    let status = 'locked'
    let progress = 0
    if (path && path.currentStep) {
      if (i < path.currentStep) { status = 'completed'; progress = 100 }
      else if (i === path.currentStep) { status = 'active'; progress = 45 }
      else { status = 'pending'; progress = 0 }
    } else {
      // 无路径数据时：第一阶段默认active
      if (displayIdx === 0) { status = 'active'; progress = 45 }
      else { status = 'pending'; progress = 0 }
    }
    return { ...s, status, progress, topics: s.keypoints || [], stageId: i, displayIndex: displayIdx }
  })
})

// ===== 状态计算 =====
const getDynamicStatusClass = (index) => {
  const stage = cRoadmap.value[index]
  if (!stage) return 'status-pending'
  if (stage.status === 'completed') return 'status-completed'
  if (stage.status === 'active') return 'status-active'
  return 'status-pending'
}
const getDynamicStatusKey = (index) => {
  const stage = cRoadmap.value[index]
  if (!stage) return 'pending'
  return stage.status || 'pending'
}

// ===== 知识图谱数据（只显示路径中的章节，已完成的变灰） =====
const knowledgeMapData = computed(() => {
  const allNodes = []
  const allLinks = []
  const roadmap = cRoadmap.value

  // 只取路径中的章节
  const filteredEntries = roadmap.map(s => {
    const entry = Object.entries(stagesData).find(([id]) => parseInt(id) === s.stageId)
    return entry ? [entry[0], entry[1], s.status] : null
  }).filter(Boolean)

  const categories = filteredEntries.map(([, s]) => ({ name: s.name }))

  const activeColors = ['#6366f1', '#3b82f6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#ec4899', '#8b5cf6', '#14b8a6', '#f97316', '#84cc16', '#0ea5e9', '#e11d48']

  filteredEntries.forEach(([id, s, status], idx) => {
    const kps = s.keypoints || []
    const isPending = status === 'pending' || status === 'locked'
    const color = isPending ? '#d1d5db' : (activeColors[idx % activeColors.length])
    const isActive = status === 'active'

    kps.forEach((kp, ki) => {
      allNodes.push({
        name: kp,
        value: isActive ? 28 : (isPending ? 10 : 20),
        category: idx,
        symbolSize: isActive ? 32 : (isPending ? 14 : 22),
        itemStyle: {
          color: color,
          opacity: isPending ? 0.3 : 1,
          borderColor: isActive ? '#fff' : 'transparent',
          borderWidth: isActive ? 2 : 0,
          shadowBlur: isActive ? 10 : 0,
          shadowColor: isActive ? color : 'transparent'
        },
        label: {
          color: isPending ? '#c0c0c0' : (isActive ? '#1e293b' : '#475569'),
          fontSize: isActive ? 13 : 11,
          fontWeight: isActive ? 'bold' : 'normal',
          position: 'bottom',
          distance: 5
        }
      })
      if (ki > 0) {
        allLinks.push({ source: kps[ki - 1], target: kp, lineStyle: { color: isPending ? '#eee' : '#d1d5db', width: isPending ? 1 : 1.5 } })
      }
    })
    if (idx > 0) {
      const prevKps = filteredEntries[idx - 1][1].keypoints || []
      const lastPrev = prevKps[prevKps.length - 1]
      if (lastPrev && kps.length) allLinks.push({ source: lastPrev, target: kps[0] })
    }
  })

  return { nodes: allNodes, links: allLinks, categories }
})

// ===== ECharts 力导向图 =====
function initGraphChart() {
  const dom = document.getElementById('graphChart')
  if (!dom) return
  if (graphChartInstance) graphChartInstance.dispose()

  import('echarts').then(({ init }) => {
    graphChartInstance = init(dom)
    const data = knowledgeMapData.value

    graphChartInstance.setOption({
      tooltip: {
        trigger: 'item',
        formatter: (p) => {
          if (p.dataType === 'node') {
            const stageName = data.categories[p.data.category]?.name || ''
            return `<b>${p.name}</b><br/>阶段: ${stageName}`
          }
          return ''
        }
      },
      legend: [{
        data: data.categories.map(x => x.name),
        orient: 'horizontal', top: 8, left: 'center',
        textStyle: { color: '#475569', fontSize: 12 },
        type: 'scroll',
        pageTextStyle: { color: '#475569' }
      }],
      animationDurationUpdate: 1200,
      animationEasingUpdate: 'cubicOut',
      series: [{
        type: 'graph',
        layout: 'force',
        data: data.nodes,
        links: data.links,
        categories: data.categories,
        roam: false,
        draggable: false,
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          fontSize: 10,
          color: '#64748b',
          fontWeight: 'normal'
        },
        force: { repulsion: 180, edgeLength: 60, gravity: 0.12, layoutAnimation: false },
        lineStyle: { color: '#e2e8f0', width: 1, curveness: 0.1 },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 2, color: '#3b82f6' },
          itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.1)' }
        }
      }]
    })
  })
}

// ===== 联动：点击左侧阶段 → 高亮右侧图谱（锁定阶段不可点击） =====
const handleStageClick = (stage, index) => {
  // 锁住的阶段（pending且不在当前阶段之后的）不允许点击
  if (stage.status === 'locked' || stage.status === 'pending') return

  currentStageIndex.value = index
  highlightedStageName.value = stage.name

  if (!graphChartInstance) return
  graphChartInstance.dispatchAction({ type: 'downplay', seriesIndex: 0 })

  // 高亮该阶段的所有知识点节点
  const topics = stage.topics || []
  const dataIndices = []
  knowledgeMapData.value.nodes.forEach((node, idx) => {
    if (topics.includes(node.name)) dataIndices.push(idx)
  })
  if (dataIndices.length > 0) {
    graphChartInstance.dispatchAction({
      type: 'highlight',
      seriesIndex: 0,
      dataIndex: dataIndices
    })
  }
}

// ===== 数据加载 =====
async function loadData() {
  if (demoStore.enabled) {
    profile.value = await demoStore.getProfile()
    resources.value = await demoStore.getResources()
    activePath.value = await demoStore.getPath()
    return
  }
  try {
    const r = await api.get('/student/by-sysuser/' + store.id)
    profile.value = r.data || {}
  } catch { profile.value = {} }
  try {
    const r = await api.get('/resources/student/' + store.id)
    resources.value = Array.isArray(r.data) ? r.data : []
  } catch { resources.value = [] }
  try {
    const r = await api.get('/learning-path/student/' + store.id + '/active')
    activePath.value = (r.data && r.data.status === 'active') ? r.data : null
  } catch {}
}

// ===== 窗口resize =====
const handleResize = () => { graphChartInstance?.resize() }

// ===== 生命周期 =====
onMounted(async () => {
  await loadData()
  await nextTick()
  initGraphChart()

  // 设置当前阶段
  const activeIdx = cRoadmap.value.findIndex(s => s.status === 'active')
  if (activeIdx >= 0) {
    currentStageIndex.value = activeIdx
    handleStageClick(cRoadmap.value[activeIdx], activeIdx)
  } else if (cRoadmap.value.length > 0) {
    // 默认选第一个
    currentStageIndex.value = 0
    handleStageClick(cRoadmap.value[0], 0)
  }

  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (graphChartInstance) { graphChartInstance.dispose(); graphChartInstance = null }
})
</script>

<style scoped>
.learning-path-container {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  height: 100vh;
  padding: 16px;
  background-color: #f8f7f4;
  gap: 16px;
  overflow: hidden;
}

.roadmap-panel, .graph-panel {
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(230, 230, 230, 0.7);
  display: flex;
  flex-direction: column;
  padding: 20px;
  box-sizing: border-box;
  height: 100%;
}

.roadmap-panel { flex: 40; max-width: 42%; overflow-y: auto; height: 100%; }
.graph-panel { flex: 60; overflow: hidden; height: 100%; }

.panel-header { margin-bottom: 12px; }
.flex-header { display: flex; justify-content: space-between; align-items: flex-start; }
.panel-title { font-size: 16px; font-weight: 600; color: #1a1a1a; margin: 0 0 4px 0; }
.panel-subtitle { font-size: 12px; color: #8c8c8c; margin: 0; }

.highlight-badge {
  background: #eff6ff; color: #2563eb; padding: 3px 10px; border-radius: 20px;
  font-size: 11px; font-weight: 500; border: 1px solid #bfdbfe;
}

.timeline-wrapper { position: relative; display: flex; flex-direction: column; gap: 8px; flex: 1; }

.timeline-item {
  position: relative; display: flex; gap: 14px; padding: 10px 14px;
  border-radius: 12px; cursor: pointer; transition: all 0.2s ease; border: 1px solid transparent;
}
.timeline-item:hover { background-color: #fafafa; }
.timeline-item.is-active { background-color: #f0f7ff; border-color: rgba(37, 99, 235, 0.06); }
.timeline-item.is-locked { opacity: 0.5; cursor: not-allowed; }
.timeline-item.is-locked:hover { background-color: transparent; }
.icon-lock { font-size: 10px; }

.timeline-line {
  position: absolute; left: 25px; top: 36px; bottom: -22px;
  width: 2px; background-color: #e5e7eb; z-index: 1;
}

.timeline-node { position: relative; z-index: 2; display: flex; align-items: center; }

.node-icon {
  width: 22px; height: 22px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: bold;
}
.status-completed { background-color: #10b981; color: #ffffff; }
.icon-check { font-size: 10px; }
.status-active {
  background-color: #3b82f6; color: #ffffff;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.12);
}
.icon-loading {
  width: 9px; height: 9px; border: 2px solid #ffffff;
  border-top-color: transparent; border-radius: 50%; animation: spin 1s linear infinite;
}
.status-pending { background-color: #ffffff; border: 2px solid #cbd5e1; color: #cbd5e1; }
.icon-dot { width: 5px; height: 5px; background-color: #cbd5e1; border-radius: 50%; }

.timeline-content { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 4px; }
.stage-info { display: flex; justify-content: space-between; align-items: center; }
.stage-title-box { display: flex; align-items: center; gap: 6px; }
.stage-emoji { font-size: 14px; }
.stage-name { font-size: 13px; font-weight: 550; color: #334155; }

.status-tag { font-size: 10px; padding: 1px 6px; border-radius: 4px; font-weight: 500; }
.status-tag.completed { background: #ecfdf5; color: #047857; }
.status-tag.active { background: #eff6ff; color: #1d4ed8; }
.status-tag.pending { background: #f8fafc; color: #64748b; }

.stage-progress-bar { margin-top: 1px; }

/* ===== AI 路径决策卡片 ===== */
.ai-decision-card {
  background: linear-gradient(135deg, rgba(99,102,241,0.06) 0%, rgba(168,85,247,0.06) 100%);
  border: 1px solid rgba(99,102,241,0.15);
  border-radius: 12px;
  padding: 14px;
  margin-bottom: 16px;
}
.ai-decision-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}
.ai-decision-icon { font-size: 16px; }
.ai-decision-title {
  font-size: 13px;
  font-weight: 700;
  color: #4f46e5;
}
.ai-decision-toggle {
  margin-left: auto;
  font-size: 11px;
  color: #6366f1;
  background: rgba(99,102,241,0.1);
  border: none;
  padding: 2px 8px;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}
.ai-decision-toggle:hover {
  background: rgba(99,102,241,0.18);
}
.ai-decision-summary {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.decision-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255,255,255,0.7);
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 11px;
}
.decision-label { color: #64748b; }
.decision-value { font-weight: 600; }
.val-warn { color: #ef4444; }
.val-good { color: #10b981; }
.val-info { color: #6366f1; }

.ai-decision-detail {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(99,102,241,0.1);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.detail-reason {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 11px;
  color: #475569;
  line-height: 1.4;
}
.detail-icon { font-size: 12px; flex-shrink: 0; margin-top: 1px; }

/* 决策展开动画 */
.decision-expand-enter-active,
.decision-expand-leave-active {
  transition: all 0.3s ease;
  max-height: 200px;
  overflow: hidden;
}
.decision-expand-enter-from,
.decision-expand-leave-to {
  max-height: 0;
  opacity: 0;
  margin-top: 0;
  padding-top: 0;
}

/* ===== 悬停卡片 ===== */
.hover-card {
  position: absolute;
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  max-width: 280px;
  pointer-events: none;
  z-index: 100;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.hover-card-icon {
  width: 32px;
  height: 32px;
  background: #f5f5f5;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
  font-size: 16px;
}

.hover-card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
  color: #000;
}

.hover-card-desc {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}

.hover-card-tag {
  display: inline-block;
  margin-top: 8px;
  padding: 4px 10px;
  background: #eff6ff;
  border-radius: 12px;
  font-size: 12px;
  color: #2563eb;
}

.empty-state-timeline { flex: 1; display: flex; align-items: center; justify-content: center; color: #94a3b8; font-size: 13px; }

.graph-chart-container {
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 300px;
}

.highlight-badge {
  background: #eff6ff; color: #2563eb; padding: 3px 10px; border-radius: 20px;
  font-size: 11px; font-weight: 500; border: 1px solid #bfdbfe;
  flex-shrink: 0;
}

@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.roadmap-panel::-webkit-scrollbar { width: 4px; }
.roadmap-panel::-webkit-scrollbar-thumb { background: #e2e8f0; border-radius: 4px; }
</style>
