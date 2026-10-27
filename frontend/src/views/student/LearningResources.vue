<template>
  <div class="resource-platform">
    <div class="platform-main">
      
      <!-- 页面头部 -->
      <div class="platform-header" v-reveal>
        <div class="header-left">
          <div class="header-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="28" height="28">
              <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" stroke-linecap="round"/>
              <path d="M12 6v7m-3.5-3.5L12 13l3.5-3.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <div>
            <h1 class="page-title">学习中心</h1>
            <p class="page-subtitle">你的薄弱章节，点击进入针对性学习</p>
          </div>
        </div>
        <div class="header-actions">
          <div class="header-stat-badge">
            <span class="stat-dot"></span>
            <span>{{ weakAreas.length || chapterCards.length }} 个薄弱章节</span>
          </div>
        </div>
      </div>

      <!-- 知识图谱全景（词云） -->
      <div v-reveal="{delay:100}" class="wordcloud-section">
        <div class="wordcloud-header">
          <h3 class="wordcloud-title">🧩 知识图谱全景</h3>
          <span class="wordcloud-subtitle" v-if="weakAreas.length">
            薄弱知识点一览，点击查看详情
          </span>
          <span class="wordcloud-subtitle" v-else>C语言知识体系 · 点击查看详情</span>
        </div>
        <div class="wordcloud-wrapper" id="wordcloudWrapper">
          <canvas ref="wordcloudCanvas" class="wordcloud-canvas"></canvas>
          <div v-if="wordcloudHovered && wordcloudHovered.info" class="wordcloud-card" :style="wordcardStyle">
            <div class="wc-title">{{ wordcloudHovered.info.name }}</div>
            <div class="wc-desc">{{ wordcloudHovered.info.desc }}</div>
            <div class="wc-tag">{{ wordcloudHovered.info.tag }}</div>
          </div>
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-section" v-reveal="{delay:200}">
        <div class="search-wrapper">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <circle cx="11" cy="11" r="8"/>
            <path d="M21 21l-4.35-4.35" stroke-linecap="round"/>
          </svg>
          <input v-model="searchKey" placeholder="搜索章节名称..." class="search-input" />
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>正在加载学习资源...</p>
      </div>

      <!-- 章节卡片网格 -->
      <div v-else-if="chapterCards.length" class="chapter-grid">
        <div
          v-for="(ch, idx) in chapterCards"
          :key="ch.name"
          class="chapter-card"
          @click="goToChapter(ch.name)"
        >
          <!-- 卡片顶部色带 -->
          <div class="chapter-card-top" :style="{ background: chapterGradient(idx) }">
            <div class="chapter-icon">{{ chapterEmoji(ch.name) }}</div>
            <div class="chapter-resource-count">{{ ch.resources.length }} 个资源</div>
          </div>
          <!-- 卡片内容 -->
          <div class="chapter-card-body">
            <h3 class="chapter-name">{{ ch.name }}</h3>
            <div class="chapter-types">
              <span v-for="t in ch.typeList" :key="t.type" class="type-badge" :style="{ background: typeBg(t.type), color: typeColor(t.type) }">
                {{ typeLabel(t.type) }} {{ t.count }}
              </span>
            </div>
            <div class="chapter-footer">
              <span class="chapter-difficulty" v-if="ch.difficulty">
                {{ diffLabel(ch.difficulty) }}
              </span>
              <span class="chapter-enter">
                进入学习
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                  <path d="M5 12h14M12 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 → 展示示例章节 -->
      <div v-else-if="!loading && !chapterCards.length" class="chapter-grid">
        <div v-for="(ch, idx) in demoChapters" :key="ch.name" class="chapter-card" @click="goToChapter(ch.name)">
          <div class="chapter-card-top" :style="{ background: chapterGradient(idx) }">
            <div class="chapter-icon">{{ chapterEmoji(ch.name) }}</div>
            <div class="chapter-resource-count">{{ ch.count }} 个资源</div>
          </div>
          <div class="chapter-card-body">
            <h3 class="chapter-name">{{ ch.name }}</h3>
            <div class="chapter-types">
              <span v-for="t in ch.types" :key="t" class="type-badge" :style="{ background: typeBg(t), color: typeColor(t) }">
                {{ typeLabel(t) }}
              </span>
            </div>
            <div class="chapter-footer">
              <span class="chapter-difficulty">{{ ch.diff }}</span>
              <span class="chapter-enter">进入学习 →</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { useDemoStore } from '../../store/demo'
import api from '../../api'

const store = useUserStore()
const demoStore = useDemoStore()
const router = useRouter()

const resources = ref([])
const searchKey = ref('')
const loading = ref(true)
const weakAreas = ref([])

// 假数据 — 当真实数据为空时展示
const demoChapters = [
  { name: '指针', count: 8, types: ['📖知识点', '💻代码', '📝练习', '🎬视频'], diff: '困难' },
  { name: '数组', count: 6, types: ['📖知识点', '💻代码', '📝练习'], diff: '中等' },
  { name: '函数', count: 5, types: ['📖知识点', '💻代码', '📝练习'], diff: '中等' },
  { name: '结构体', count: 4, types: ['📖知识点', '💻代码'], diff: '中等' },
  { name: '控制结构', count: 7, types: ['📖知识点', '💻代码', '📝练习', '🎬视频'], diff: '简单' },
  { name: '动态内存', count: 3, types: ['📖知识点', '💻代码'], diff: '困难' },
]

// ===== 词云相关 =====
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

// 知识点 → 章节映射
const knowledgePointChapterMap = {
  '基础语法': { chapter: '第1章', points: ['数据类型', '变量声明', '运算符', '输入输出', '注释'] },
  '流程控制': { chapter: '第2章', points: ['if-else', 'switch', 'for循环', 'while循环', 'break/continue'] },
  '函数': { chapter: '第3章', points: ['函数定义', '参数传递', '递归', '作用域'] },
  '数组与指针': { chapter: '第4-5章', points: ['一维数组', '二维数组', '指针变量', '指针运算', '动态内存'] },
  '结构体': { chapter: '第6章', points: ['结构体定义', '结构体指针', '链表'] },
  '文件操作': { chapter: '第7章', points: ['文件打开关闭', '读写函数', '文本读写'] },
}

// 根据用户薄弱点动态生成词云数据
const wordCloudData = computed(() => {
  const weakNames = weakAreas.value.map(w => w.name || w)
  const isWeak = (name) => weakNames.some(w => name.includes(w) || w.includes(name))

  // 中心节点 - 黑色
  const data = [
    { text: 'C语言', x: 0.5, y: 0.5, fontSize: 38, fontWeight: 'bold', color: '#1f2937', isCenter: true, floatAmplitude: 0, breatheSpeed: 0.3 },
  ]

  // 主分类节点 - 全灰色，距离中心更近，加浮动
  const mainCategories = [
    { text: '基础语法', x: 0.32, y: 0.32 },
    { text: '流程控制', x: 0.68, y: 0.3 },
    { text: '函数', x: 0.75, y: 0.52 },
    { text: '数组与指针', x: 0.25, y: 0.58 },
    { text: '结构体', x: 0.65, y: 0.72 },
    { text: '文件操作', x: 0.35, y: 0.73 },
  ]

  mainCategories.forEach((cat, i) => {
    const weak = isWeak(cat.text)
    data.push({
      text: cat.text,
      x: cat.x,
      y: cat.y,
      fontSize: weak ? 20 : 18,
      fontWeight: 'bold',
      color: weak ? '#4b5563' : '#64748b',
      isMainCategory: true,
      floatAmplitude: 3,
      breatheSpeed: 0.28 + i * 0.02,
    })
  })

  // 子知识点 - 全灰色，距离中心更近
  const subPoints = [
    { text: '数据类型', x: 0.22, y: 0.18, category: '基础语法', desc: '整型、浮点型、字符型等基本数据类型' },
    { text: '变量声明', x: 0.38, y: 0.15, category: '基础语法', desc: '变量的命名规则、初始化方式和作用域' },
    { text: '运算符', x: 0.15, y: 0.38, category: '基础语法', desc: '算术、关系、逻辑运算符的使用' },
    { text: '输入输出', x: 0.42, y: 0.2, category: '基础语法', desc: 'printf / scanf 格式化输入输出' },
    { text: '注释', x: 0.2, y: 0.45, category: '基础语法', desc: '单行注释和多行注释' },
    { text: 'if-else', x: 0.6, y: 0.15, category: '流程控制', desc: '条件判断，程序逻辑的核心' },
    { text: 'switch', x: 0.78, y: 0.18, category: '流程控制', desc: '多分支选择结构' },
    { text: 'for循环', x: 0.82, y: 0.35, category: '流程控制', desc: '已知循环次数时使用' },
    { text: 'while循环', x: 0.88, y: 0.25, category: '流程控制', desc: '条件满足时持续循环' },
    { text: 'break/continue', x: 0.72, y: 0.42, category: '流程控制', desc: '循环控制语句' },
    { text: '函数定义', x: 0.85, y: 0.45, category: '函数', desc: '函数的声明、定义和调用' },
    { text: '参数传递', x: 0.82, y: 0.6, category: '函数', desc: '值传递 vs 指针传递' },
    { text: '递归', x: 0.72, y: 0.62, category: '函数', desc: '函数调用自身' },
    { text: '作用域', x: 0.88, y: 0.58, category: '函数', desc: '局部 vs 全局变量' },
    { text: '一维数组', x: 0.15, y: 0.5, category: '数组与指针', desc: '相同类型元素的集合' },
    { text: '二维数组', x: 0.32, y: 0.65, category: '数组与指针', desc: '矩阵形式的数据结构' },
    { text: '指针变量', x: 0.18, y: 0.72, category: '数组与指针', desc: '存储内存地址的变量' },
    { text: '指针运算', x: 0.35, y: 0.52, category: '数组与指针', desc: '指针的算术运算' },
    { text: '动态内存', x: 0.12, y: 0.82, category: '数组与指针', desc: 'malloc / free 动态内存管理' },
    { text: '结构体定义', x: 0.58, y: 0.78, category: '结构体', desc: '自定义数据类型' },
    { text: '结构体指针', x: 0.72, y: 0.82, category: '结构体', desc: '指向结构体的指针' },
    { text: '链表', x: 0.55, y: 0.88, category: '结构体', desc: '动态数据结构' },
    { text: '文件打开关闭', x: 0.28, y: 0.85, category: '文件操作', desc: 'fopen / fclose' },
    { text: '读写函数', x: 0.42, y: 0.82, category: '文件操作', desc: 'fread / fwrite 读写操作' },
    { text: '文本读写', x: 0.45, y: 0.88, category: '文件操作', desc: 'fprintf / fscanf 格式化读写' },
  ]

  // 子知识点 - 全灰色
  subPoints.forEach(point => {
    const weak = isWeak(point.text) || isWeak(point.category)
    data.push({
      text: point.text,
      x: point.x,
      y: point.y,
      fontSize: weak ? 13 : 11,
      color: weak ? '#6b7280' : '#d1d5db',
      floatAmplitude: weak ? 4 : 3,
      breatheSpeed: 0.4 + Math.random() * 0.15,
      info: { name: point.text, desc: point.desc, tag: knowledgePointChapterMap[point.category]?.chapter || 'C语言' },
    })
  })

  return data
})

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
    // 中心不动，其他都浮动
    if (!this.isCenter && this.floatAmplitude > 0) {
      // 主分类幅度小一点，子知识点幅度大一点
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
    // 悬停时变深变粗
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

function drawWordcloudConnections(ctx, width, height) {
  const center = wordcloudNodes.find(n => n.isCenter)
  if (!center) return
  ctx.save()
  const mainCategories = wordcloudNodes.filter(n => n.isMainCategory)
  mainCategories.forEach(cat => {
    // 连接线 - 浅灰色
    ctx.beginPath(); ctx.moveTo(center.x * width, center.y * height); ctx.lineTo(cat.x * width, cat.y * height)
    ctx.strokeStyle = 'rgba(156,163,175,0.2)'
    ctx.lineWidth = 1.5
    ctx.stroke()
    // 子知识点连接线
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
  const wrapper = document.getElementById('wordcloudWrapper')
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

onMounted(() => {
  setTimeout(initWordcloud, 200)
  window.addEventListener('resize', initWordcloud)
  nextTick(() => {
    if (wordcloudCanvas.value) {
      wordcloudCanvas.value.addEventListener('mousemove', handleWordcloudMouseMove)
      wordcloudCanvas.value.addEventListener('mouseleave', handleWordcloudMouseLeave)
    }
  })
})

onBeforeUnmount(() => {
  if (wordcloudAnimId) cancelAnimationFrame(wordcloudAnimId)
  window.removeEventListener('resize', initWordcloud)
  if (wordcloudCanvas.value) {
    wordcloudCanvas.value.removeEventListener('mousemove', handleWordcloudMouseMove)
    wordcloudCanvas.value.removeEventListener('mouseleave', handleWordcloudMouseLeave)
  }
})

// 主分类映射（与知识图谱一致）
const mainCategoryMap = {
  '指针': ['指针', '指针变量', '指针运算', '取地址', '解引用', '多级指针', 'void指针', 'const指针'],
  '数组': ['数组', '一维数组', '二维数组', '数组初始化', '数组遍历', '数组作为参数'],
  '函数': ['函数', '递归', '参数传递', '返回值', '作用域', '函数指针', '回调', '局部变量', '全局变量'],
  '结构体': ['结构体', 'struct', '链表', '共用体', '枚举', '联合体', '位域'],
  '控制结构': ['if', 'else', 'switch', 'case', 'for', 'while', 'do', 'break', 'continue', '循环', '判断', '控制结构', '条件语句', '循环语句'],
  '动态内存': ['动态内存', 'malloc', 'calloc', 'realloc', 'free', '内存泄漏', '野指针', '内存管理', '堆', '栈'],
}

// 按 knowledgePoint 分组，显示所有有资源的主分类
const chapterCards = computed(() => {
  const groups = {}

  // 将细分知识点映射到主分类（精确匹配）
  function getMainCategory(knowledgePoint, chapter) {
    // 优先使用chapter字段精确匹配
    if (chapter && mainCategoryMap[chapter]) {
      return chapter
    }
    if (!knowledgePoint) return '其他'
    for (const [category, keywords] of Object.entries(mainCategoryMap)) {
      if (keywords.some(kw => knowledgePoint === kw)) {
        return category
      }
    }
    return knowledgePoint // 如果没有匹配到，返回原始值
  }

  resources.value.forEach(r => {
    const knowledgePoint = r.knowledgePoint || '其他'
    const chapter = r.chapter || ''
    const mainCategory = getMainCategory(knowledgePoint, chapter)

    // 只显示主分类级别的分组（排除"其他"）
    if (mainCategory === '其他') return

    if (!groups[mainCategory]) groups[mainCategory] = { name: mainCategory, resources: [], types: {} }
    groups[mainCategory].resources.push(r)
    const t = r.type || 'other'
    if (!groups[mainCategory].types[t]) groups[mainCategory].types[t] = 0
    groups[mainCategory].types[t]++
  })

  return Object.values(groups)
    .map(ch => ({
      ...ch,
      typeList: Object.entries(ch.types).map(([type, count]) => ({ type, count })),
      difficulty: mostCommonDifficulty(ch.resources),
    }))
    .sort((a, b) => b.resources.length - a.resources.length)
})

function mostCommonDifficulty(list) {
  const counts = {}
  list.forEach(r => { if (r.difficulty) counts[r.difficulty] = (counts[r.difficulty] || 0) + 1 })
  return Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] || ''
}

function goToChapter(name) {
  router.push({ path: '/student/resources/chapter', query: { name } })
}

function typeLabel(t) {
  const m = { article: '📄文章', explanation: '📖知识点', mindmap: '🗺️导图', code: '💻代码', summary: '📋总结', weakness: '🎯分析', 'course-video': '🎬视频', 'course-ppt': '📑课件', blog: '🎧博客' }
  return m[t] || t || '其他'
}
function typeColor(t) {
  const m = { article: '#3b82f6', explanation: '#10b981', mindmap: '#8b5cf6', code: '#ef4444', summary: '#6366f1', weakness: '#f43f5e', 'course-video': '#06b6d4', 'course-ppt': '#f97316', blog: '#8b5cf6' }
  return m[t] || '#94a3b8'
}
function typeBg(t) {
  const m = { article: '#eff6ff', explanation: '#ecfdf5', mindmap: '#f5f3ff', code: '#fef2f2', summary: '#eef2ff', weakness: '#fff1f2', 'course-video': '#ecfeff', 'course-ppt': '#fff7ed', blog: '#f5f3ff' }
  return m[t] || '#f8fafc'
}
function diffLabel(d) { const m = { easy: '简单', medium: '中等', hard: '困难' }; return m[d] || d || '' }
function chapterGradient(i) {
  const gradients = [
    'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
    'linear-gradient(135deg, #fccb90 0%, #d57eeb 100%)',
    'linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%)',
    'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)',
    'linear-gradient(135deg, #fbc2eb 0%, #a6c1ee 100%)',
  ]
  return gradients[i % gradients.length]
}
function chapterEmoji(name) {
  const map = {
    '指针': '🎯', '数组': '📦', '函数': '⚙️', '结构体': '🏗️',
    '内存管理': '🧠', '动态内存': '💾', '文件操作': '📁', '控制结构': '🔀',
    '循环': '🔄', '递归': '🔁', '预处理': '📋', '链表': '🔗',
    '函数指针': '📌', 'C语言概述': '📖', '指针与数组': '🎯📦',
  }
  return map[name] || '📚'
}

async function loadResources() {
  loading.value = true
  // 演示模式：加载演示数据
  if (demoStore.enabled) {
    resources.value = await demoStore.getResources()
    weakAreas.value = await demoStore.getWeakAreas()
    loading.value = false
    return
  }
  if (!store.id) {
    loading.value = false
    return
  }
  try {
    const [resRes, profileRes] = await Promise.all([
      api.get('/resources/student/' + store.id),
      api.get('/student/by-sysuser/' + store.id)
    ])
    resources.value = Array.isArray(resRes.data) ? resRes.data : []
    const profile = profileRes.data
    if (profile && profile.weakAreas) {
      weakAreas.value = profile.weakAreas.split(/[,，、]/).map(w => ({ name: w.trim() })).filter(w => w.name)
    }
  } catch (e) {
    console.warn('加载学习资源失败:', e)
    resources.value = []
    weakAreas.value = []
  }
  finally { loading.value = false }
}

// 刷新数据（WebSocket触发）
async function refreshData() {
  if (demoStore.enabled || !store.id) return
  try {
    const [resRes, profileRes] = await Promise.all([
      api.get('/resources/student/' + store.id),
      api.get('/student/by-sysuser/' + store.id)
    ])
    resources.value = Array.isArray(resRes.data) ? resRes.data : []
    const profile = profileRes.data
    if (profile && profile.weakAreas) {
      weakAreas.value = profile.weakAreas.split(/[,，、]/).map(w => ({ name: w.trim() })).filter(w => w.name)
    }
  } catch (e) {
    console.warn('刷新学习中心数据失败:', e)
  }
}

onMounted(() => {
  loadResources()
  // 监听WebSocket推送的刷新事件
  window.addEventListener('refresh-profile', refreshData)
  window.addEventListener('refresh-resources', refreshData)
})

onBeforeUnmount(() => {
  window.removeEventListener('refresh-profile', refreshData)
  window.removeEventListener('refresh-resources', refreshData)
})

// 薄弱点变化时重新初始化词云
watch(weakAreas, () => {
  if (wordcloudCanvas.value && !loading.value) {
    nextTick(() => initWordcloud())
  }
}, { deep: true })
</script>

<style scoped>
/* ---- 全局布局 ---- */
.resource-platform {
  display: flex;
  gap: 0;
  height: 100%;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}
.platform-main {
  flex: 1;
  overflow-y: auto;
  padding: 32px 40px 40px;
  min-width: 0;
}

/* ---- 页面头部 ---- */
.platform-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid #e2e8f0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}
.header-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #a855f7 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.35);
}
.page-title {
  margin: 0 0 4px;
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  letter-spacing: -0.5px;
}
.page-subtitle {
  margin: 0;
  font-size: 15px;
  color: #64748b;
  font-weight: 500;
}
.header-actions { flex-shrink: 0; }
.header-stat-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  border: 1px solid #bbf7d0;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #166534;
  box-shadow: 0 2px 8px rgba(34, 197, 94, 0.15);
}
.stat-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* ---- 筛选栏 ---- */
.filter-section {
  margin-bottom: 28px;
}
.search-wrapper {
  position: relative;
  max-width: 400px;
}
.search-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
}
.search-input {
  width: 100%;
  padding: 14px 20px 14px 48px;
  border: 2px solid #e2e8f0;
  border-radius: 14px;
  font-size: 15px;
  background: #fff;
  color: #1e293b;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
  font-family: inherit;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.search-input::placeholder {
  color: #94a3b8;
}
.search-input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.1), 0 2px 8px rgba(0,0,0,0.04);
}

/* ---- 加载状态 ---- */
.loading-state {
  text-align: center;
  padding: 80px 20px;
  color: #64748b;
}
.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #e2e8f0;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 20px;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ---- 章节卡片网格 ---- */
.chapter-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

/* ---- 章节卡片 ---- */
.chapter-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
}
.chapter-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 12px 40px rgba(99, 102, 241, 0.15), 0 4px 12px rgba(0,0,0,0.05);
  border-color: #c7d2fe;
}

/* 卡片顶部色带 */
.chapter-card-top {
  padding: 24px 24px 20px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.chapter-icon {
  font-size: 36px;
  filter: drop-shadow(0 4px 8px rgba(0,0,0,0.15));
}
.chapter-resource-count {
  background: rgba(255,255,255,0.3);
  padding: 6px 14px;
  border-radius: 24px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,0.2);
}

/* 卡片内容 */
.chapter-card-body {
  padding: 20px 24px 24px;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.chapter-name {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.3px;
}
.chapter-types {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
  flex: 1;
}
.type-badge {
  padding: 5px 12px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}
.chapter-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}
.chapter-difficulty {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 8px;
  background: #f1f5f9;
  color: #475569;
  font-weight: 600;
  border: 1px solid #e2e8f0;
}
.chapter-enter {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
  color: #6366f1;
  padding: 8px 16px;
  background: #eef2ff;
  border-radius: 10px;
  transition: all 0.2s;
}
.chapter-card:hover .chapter-enter {
  background: #6366f1;
  color: #fff;
  gap: 10px;
}

/* ---- 空状态 ---- */
.empty-state-modern {
  text-align: center;
  padding: 80px 20px;
}
.empty-state-visual {
  margin-bottom: 24px;
}
.empty-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 12px;
}
.empty-desc {
  font-size: 15px;
  color: #64748b;
  margin: 0;
}
.empty-desc a {
  color: #6366f1;
  text-decoration: none;
  font-weight: 600;
}
.empty-desc a:hover {
  text-decoration: underline;
}

/* ===== 知识图谱全景词云 ===== */
.wordcloud-section {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(0,0,0,0.05);
  margin-bottom: 28px;
  max-width: 92%;
  margin-left: auto;
  margin-right: auto;
}
.wordcloud-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.wordcloud-title {
  font-size: 15px;
  font-weight: 700;
  color: #1a2332;
  margin: 0;
}
.wordcloud-subtitle {
  font-size: 11px;
  color: #94a3b8;
}
.wordcloud-wrapper {
  position: relative;
  background: #FAFAFA;
  border-radius: 12px;
  height: 350px;
  overflow: hidden;
}
.wordcloud-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.wordcloud-card {
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
</style>
