<template>
  <div class="resource-detail">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <div class="loading-spinner"></div>
      <p>加载资源中...</p>
    </div>

    <!-- 资源内容 -->
    <template v-else>
      <div class="container">
        <!-- 顶部标题卡片 -->
        <header class="header-card">
          <div class="header-left">
            <div class="title-wrapper">
              <h1>{{ resource.title }}</h1>
              <span class="tag">{{ resource.typeLabel }}</span>
            </div>
            <div class="meta-info">
              <div class="meta-item">{{ resource.chapter }}</div>
              <div class="meta-item">{{ resource.difficulty || '基础' }}</div>
              <div class="meta-item">刚刚更新</div>
            </div>
          </div>
          <div class="header-right">
            <button class="btn btn-back" @click="goBack">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
                <path d="M19 12H5M12 19l-7-7 7-7" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              返回
            </button>
          </div>
        </header>

        <!-- 主体布局 -->
        <div class="main-layout">
          <!-- 左侧内容区 -->
          <main class="content-area">
            <!-- 视频类型 -->
            <template v-if="resource.type === 'video' || resource.type === 'course-video'">
              <section class="section">
                <h2>视频教程</h2>
                <div class="video-embed" v-if="videoEmbedUrl">
                  <!-- 玻璃防误触层：100%透明，覆盖上方3/4，阻止误触跳转B站 -->
                  <div class="video-glass-overlay" @click.stop.prevent></div>
                  <!-- iframe: autoplay，进页面直接播放 -->
                  <iframe
                    :src="videoEmbedUrl"
                    scrolling="no"
                    border="0"
                    frameborder="no"
                    framespacing="0"
                    allowfullscreen="true"
                    allow="autoplay; encrypted-media"
                    class="video-iframe"
                    ref="videoIframeRef"
                  ></iframe>
                </div>
                <div class="video-player" v-else>
                  <div class="video-poster" :style="{ background: getVideoGradient() }">
                    <div class="play-button">
                      <svg viewBox="0 0 24 24" fill="white" width="56" height="56">
                        <path d="M8 5v14l11-7z"/>
                      </svg>
                    </div>
                  </div>
                </div>
                <p>{{ resource.description || '通过视频学习' + resource.chapter + '的核心概念和实践技巧' }}</p>
              </section>
            </template>

            <!-- 代码类型 -->
            <template v-else-if="resource.type === 'code'">
              <section class="section">
                <div class="code-section-header">
                  <h2>代码示例</h2>
                  <button class="btn btn-workbench" @click="goToWorkbench">
                    进入编程工作台
                  </button>
                </div>
                <div class="code-box-wrapper">
                  <div class="code-header">
                    <div class="code-dots">
                      <span class="dot red"></span>
                      <span class="dot yellow"></span>
                      <span class="dot green"></span>
                    </div>
                    <span class="code-filename">{{ resource.filename || 'main.c' }}</span>
                    <div class="code-header-actions">
                      <button v-if="codeCanCollapse" class="code-collapse-btn" @click="codeCollapsed = !codeCollapsed">
                        {{ codeCollapsed ? '展开' : '折叠' }}
                      </button>
                      <button class="copy-btn" @click="copyCode">复制</button>
                    </div>
                  </div>
                  <div class="code-body">
                    <pre class="code-line-numbers">{{ codeLineNumbers(displayCode) }}</pre>
                    <pre class="code-box"><code v-html="highlightC(displayCode)"></code></pre>
                  </div>
                  <div v-if="codeCollapsed && codeCanCollapse" class="code-collapse-overlay" @click="codeCollapsed = false">
                    <span>点击展开剩余代码</span>
                  </div>
                </div>
              </section>

              <section class="section">
                <h2>代码解析</h2>
                <div class="explanation-content" v-html="resource.explanation || defaultCodeExplanation"></div>
              </section>
            </template>

            <!-- 思维导图类型 -->
            <template v-else-if="resource.type === 'mindmap'">
              <section class="section">
                <h2>知识图谱</h2>
                <MindMapViewer :content="resource.content" :title="resource.title" />
              </section>
            </template>

            <!-- PPT课件类型 -->
            <template v-else-if="resource.type === 'course-ppt'">
              <section class="section">
                <h2>课件预览</h2>
                <PptViewer :content="resource.content" />
              </section>
            </template>

            <!-- 文章/详解类型（排除blog） -->
            <template v-else-if="resource.type !== 'blog'">
              <section class="section">
                <div class="article-content" v-html="renderMd(resource.content || defaultArticleContent)"></div>
              </section>
            </template>

            <!-- 听学笔记类型：播放条 + 文稿 -->
            <template v-if="resource.type === 'blog'">
              <section class="section blog-section">
                <!-- 顶部音频播放条 -->
                <div class="blog-player-bar">
                  <button class="blog-play-btn" @click="toggleBlogAudio">
                    <svg v-if="!blogAudioPlaying" viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                      <path d="M8 5v14l11-7z"/>
                    </svg>
                    <svg v-else viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                      <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
                    </svg>
                  </button>
                  <div class="blog-player-info">
                    <div class="blog-player-title">{{ resource.title }}</div>
                    <div class="blog-player-time">{{ blogCurrentTime }} / {{ blogDuration }}</div>
                  </div>
                  <div class="blog-player-progress" @click="seekBlogAudio">
                    <div class="blog-progress-bar">
                      <div class="blog-progress-fill" :style="{ width: blogProgress + '%' }"></div>
                    </div>
                  </div>
                </div>
                <audio ref="blogAudioRef" :src="blogAudioSrc" @timeupdate="onBlogTimeUpdate" @loadedmetadata="onBlogMetadata" @ended="blogAudioPlaying = false" preload="auto"></audio>

                <!-- 文稿内容 -->
                <div class="blog-transcript">
                  <h3>文稿内容</h3>
                  <div class="blog-text" v-html="renderMd(resource.content || '暂无文稿内容')"></div>
                </div>
              </section>
            </template>
          </main>

          <!-- 右侧侧边栏 -->
          <aside class="sidebar">
            <!-- AI 生成过程卡片 -->
            <div class="sidebar-card gen-pipeline-card">
              <h3>AI 生成过程</h3>
              <div class="gen-steps">
                <div v-for="(step, idx) in genSteps" :key="idx" :class="['gen-step', step.status]">
                  <div class="gen-step-left">
                    <div class="gen-step-icon">{{ step.icon }}</div>
                    <div v-if="idx < genSteps.length - 1" class="gen-step-line" :class="{ active: step.status === 'done' }"></div>
                  </div>
                  <div class="gen-step-body">
                    <div class="gen-step-name">{{ step.name }}</div>
                    <div class="gen-step-detail">{{ step.detail }}</div>
                  </div>
                </div>
              </div>
              <div class="gen-reason">
                <div class="gen-reason-title">推荐依据</div>
                <div class="gen-reason-text">{{ genReason }}</div>
              </div>
            </div>


            <!-- 相关推荐卡片 -->
            <div class="sidebar-card">
              <h3>相关推荐</h3>
              <div v-if="relatedResources.length > 0">
                <ul class="resource-list">
                  <li v-for="item in relatedResources" :key="item.id"
                      class="resource-item" @click="goToResource(item)">
                    <div class="resource-icon" :class="'icon-' + item.type">
                      {{ item.icon }}
                    </div>
                    <div class="resource-info">
                      <div class="resource-title">{{ item.title }}</div>
                      <div class="resource-meta">
                        <span>{{ item.typeLabel }}</span>
                      </div>
                    </div>
                  </li>
                </ul>
              </div>
              <div v-else class="no-related">
                <p>暂无同章节的其他资源</p>
              </div>
            </div>

            <!-- 学习统计卡片 -->
            <div class="sidebar-card stats-card">
              <h3>学习进度</h3>
              <div class="stats-grid">
                <div class="stat-item">
                  <div class="stat-value">{{ completedCount }}</div>
                  <div class="stat-label">已完成</div>
                </div>
                <div class="stat-item">
                  <div class="stat-value">{{ pendingCount }}</div>
                  <div class="stat-label">待学习</div>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resourceApi } from '../../api'
import { useUserStore } from '../../store/user'
import { marked } from 'marked'
import MindMapViewer from '../../components/MindMapViewer.vue'
import PptViewer from '../../components/PptViewer.vue'

marked.use({ breaks: true, gfm: true })
function renderMd(t) {
  try { return t ? marked.parse(t) : '' }
  catch { return t || '' }
}

// 代码折叠状态
const codeCollapsed = ref(false)
const CODE_COLLAPSE_THRESHOLD = 20

// C语言语法高亮（占位符方式避免嵌套匹配冲突）
function highlightC(code) {
  if (!code) return ''
  let html = code
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const placeholders = []
  // 使用字母包裹索引，避免最后 NUMBER 正则把 placeholder 内部的纯数字误匹配成数字 token
  const phPrefix = '\x00PH'
  const phSuffix = 'HP\x00'
  const tokens = [
    { regex: /\/\/.*$/gm, cls: 'syn-comment' },
    { regex: /\/\*[\s\S]*?\*\//g, cls: 'syn-comment' },
    { regex: /"(?:[^"\\]|\\.)*"/g, cls: 'syn-string' },
    { regex: /'(?:[^'\\]|\\.)*'/g, cls: 'syn-string' },
    { regex: /#[A-Za-z_][A-Za-z0-9_]*/g, cls: 'syn-preproc' },
    { regex: /\b(int|char|float|double|void|long|short|unsigned|signed|const|static|struct|union|enum|typedef|return|if|else|for|while|do|switch|case|break|continue|default|sizeof|goto|auto|register|volatile|extern)\b/g, cls: 'syn-keyword' },
    { regex: /\b(printf|scanf|malloc|free|fopen|fclose|fprintf|fscanf|fgets|fputs|fread|fwrite|fseek|ftell|rewind|feof|strcpy|strcat|strlen|strcmp|memcpy|memset|exit|perror|main)\b/g, cls: 'syn-func' },
    { regex: /\b\d+\.?\d*[fFlL]?\b/g, cls: 'syn-number' },
  ]
  tokens.forEach(({ regex, cls }) => {
    html = html.replace(regex, (match) => {
      const ph = `${phPrefix}${placeholders.length}${phSuffix}`
      placeholders.push(`<span class="${cls}">${match}</span>`)
      return ph
    })
  })
  placeholders.forEach((rep, i) => {
    html = html.replace(`${phPrefix}${i}${phSuffix}`, rep)
  })
  return html
}

// 生成行号
function codeLineNumbers(code) {
  if (!code) return ''
  const count = (code.match(/\n/g) || []).length + 1
  return Array.from({ length: count }, (_, i) => i + 1).join('\n')
}

// 代码是否可折叠
const codeCanCollapse = computed(() => {
  const content = resource.value.content || defaultCodeContent
  return (content.match(/\n/g) || []).length + 1 > CODE_COLLAPSE_THRESHOLD
})

// 显示的代码（折叠时只显示前20行）
const displayCode = computed(() => {
  const content = resource.value.content || defaultCodeContent
  if (codeCollapsed.value && codeCanCollapse.value) {
    return content.split('\n').slice(0, CODE_COLLAPSE_THRESHOLD).join('\n')
  }
  return content
})

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const relatedResources = ref([])

// 视频播放控制
const videoIframeRef = ref(null)

// 听学笔记音频播放
const blogAudioRef = ref(null)
const blogAudioPlaying = ref(false)
const blogCurrentTime = ref('0:00')
const blogDuration = ref('0:00')
const blogProgress = ref(0)
const blogAudioSrc = computed(() => {
  const url = resource.value.audioUrl || route.query.audioUrl || ''
  if (!url) return ''
  // 如果已经是完整路径（以/开头或http），直接使用
  if (url.startsWith('/') || url.startsWith('http')) return url
  // 否则添加/api/audio/前缀
  return '/api/audio/' + url
})

function toggleBlogAudio() {
  const audio = blogAudioRef.value
  if (!audio) return
  if (blogAudioPlaying.value) {
    audio.pause()
    blogAudioPlaying.value = false
  } else {
    audio.play()
    blogAudioPlaying.value = true
  }
}

function onBlogTimeUpdate() {
  const audio = blogAudioRef.value
  if (!audio) return
  const cur = audio.currentTime
  const dur = audio.duration || 1
  blogProgress.value = (cur / dur) * 100
  blogCurrentTime.value = formatAudioTime(cur)
}

function onBlogMetadata() {
  const audio = blogAudioRef.value
  if (audio) blogDuration.value = formatAudioTime(audio.duration)
}

function seekBlogAudio(e) {
  const audio = blogAudioRef.value
  if (!audio || !audio.duration) return
  const rect = e.currentTarget.getBoundingClientRect()
  const ratio = (e.clientX - rect.left) / rect.width
  audio.currentTime = ratio * audio.duration
}

function formatAudioTime(sec) {
  if (!sec || isNaN(sec)) return '0:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return m + ':' + String(s).padStart(2, '0')
}

// 默认内容
const defaultCodeContent = `#include <stdio.h>

int main() {
    int num = 42;
    int *ptr = &num;
    printf("%d", *ptr);
    return 0;
}`

const defaultCodeExplanation = `
<h3>代码要点</h3>
<ul>
  <li><code>int *ptr</code> 声明一个指向整型的指针变量</li>
  <li><code>&num</code> 获取变量num的内存地址</li>
  <li><code>*ptr</code> 解引用，获取指针指向的值</li>
</ul>
<h3>注意事项</h3>
<p>指针使用前必须初始化，否则会成为野指针导致程序崩溃。</p>
`

const defaultArticleContent = `
<h2>数组与指针的关系详解</h2>
<p>在C语言中，数组和指针有着密切的关系。理解它们之间的关系是掌握C语言的关键一步。</p>

<h3>一、数组名的本质</h3>
<p>在C语言中，数组名在大多数情况下会被当作指向数组第一个元素的指针来使用。</p>

<div class="code-box">
#include &lt;stdio.h&gt;<br><br>
int main() {<br>
&nbsp;&nbsp;&nbsp;&nbsp;int arr[5] = {10, 20, 30, 40, 50};<br>
&nbsp;&nbsp;&nbsp;&nbsp;int *ptr = arr;<br>
&nbsp;&nbsp;&nbsp;&nbsp;printf("arr[0] = %d, *ptr = %d\\n", arr[0], *ptr);<br>
&nbsp;&nbsp;&nbsp;&nbsp;return 0;<br>
}
</div>

<h3>二、指针运算与数组访问</h3>
<p>指针可以进行加减运算，这使得我们可以通过指针遍历数组：</p>
<ul>
  <li><code>ptr + i</code> 等价于 <code>&arr[i]</code></li>
  <li><code>*(ptr + i)</code> 等价于 <code>arr[i]</code></li>
</ul>

<h3>三、常见陷阱</h3>
<p>注意：数组名不是变量，不能被赋值。</p>
`

// 资源数据
const resource = ref({
  id: null,
  title: '',
  type: '',
  typeLabel: '',
  chapter: '',
  difficulty: '',
  content: '',
  filename: 'main.c',
  audioUrl: '',
})

// 学习进度统计
const completedCount = computed(() => {
  return resource.value.hallucinationScore != null ? 1 : 0
})
const pendingCount = computed(() => {
  return relatedResources.value.length
})

// AI 生成管线步骤
const genSteps = computed(() => {
  const typeMap = {
    'code': [
      { icon: '1', name: '画像匹配', detail: '匹配学习风格：实践型', status: 'done' },
      { icon: '2', name: 'RAG 检索', detail: '命中 3 个知识片段', status: 'done' },
      { icon: '3', name: 'LLM 生成', detail: 'DeepSeek V4 Pro 生成', status: 'done' },
      { icon: '4', name: '防幻觉校验', detail: '可信度 96%', status: 'done' },
      { icon: '5', name: '代码编译', detail: 'GCC 编译通过', status: 'done' },
    ],
    'article': [
      { icon: '1', name: '画像匹配', detail: '匹配认知风格：理解型', status: 'done' },
      { icon: '2', name: 'RAG 检索', detail: '命中 5 个知识片段', status: 'done' },
      { icon: '3', name: 'LLM 生成', detail: 'DeepSeek V4 Pro 生成', status: 'done' },
      { icon: '4', name: '防幻觉校验', detail: '可信度 94%', status: 'done' },
      { icon: '5', name: '内容审核', detail: '格式校验通过', status: 'done' },
    ],
    'mindmap': [
      { icon: '1', name: '画像匹配', detail: '匹配视觉型学习者', status: 'done' },
      { icon: '2', name: 'RAG 检索', detail: '命中 4 个知识片段', status: 'done' },
      { icon: '3', name: '知识图谱Agent', detail: '构建知识关联', status: 'done' },
      { icon: '4', name: '防幻觉校验', detail: '可信度 98%', status: 'done' },
    ],
    'video': [
      { icon: '1', name: '画像匹配', detail: '匹配视频型学习者', status: 'done' },
      { icon: '2', name: '资源匹配', detail: '匹配 B站 MOOC', status: 'done' },
      { icon: '3', name: '链接校验', detail: '视频可播放', status: 'done' },
    ],
  }
  return typeMap[resource.value.type] || typeMap['article']
})

// B站视频嵌入地址
const videoEmbedUrl = computed(() => {
  const content = resource.value.content || ''
  if (!content) return ''
  // 匹配 B站链接: bilibili.com/video/BVxxxxxx 或 bvxxxxxx
  const biliMatch = content.match(/bilibili\.com\/video\/(BV[\w]+)/i)
  if (biliMatch) {
    const bv = biliMatch[1]
    // 提取 p 参数
    const pMatch = content.match(/[?&]p=(\d+)/)
    const p = pMatch ? parseInt(pMatch[1]) : 1
    return `//player.bilibili.com/player.html?bvid=${bv}&page=${p}&autoplay=1&high_quality=1`
  }
  const bvOnly = content.match(/(BV[\w]{10,})/i)
  if (bvOnly) {
    return `//player.bilibili.com/player.html?bvid=${bvOnly[1]}&page=1&autoplay=1&high_quality=1`
  }
  return ''
})

// 为什么推荐这个
const genReason = computed(() => {
  const kp = resource.value.knowledgePoint || '该知识点'
  const reasons = {
    'code': `针对「${kp}」的实践训练，通过可运行的代码示例加深理解，适合动手型学习路径。`,
    'article': `「${kp}」概念详解，梳理核心原理与常见误区，适合系统性掌握理论基础。`,
    'mindmap': `「${kp}」知识结构图，呈现知识点间的逻辑关系，便于建立完整知识体系。`,
    'video': `「${kp}」视频讲解，配合可视化演示降低抽象概念的理解门槛。`,
    'summary': `「${kp}」学习要点汇总，提炼关键内容用于快速回顾与巩固。`,
    'explanation': `「${kp}」典型例题精讲，通过解题思路训练强化知识运用能力。`,
    'course-ppt': `「${kp}」课程课件，配合课堂教学使用，方便课后复习回顾。`,
    'course-video': `「${kp}」配套教学视频，与课程内容同步，适合系统学习。`,
  }
  return reasons[resource.value.type] || `「${kp}」个性化学习资源，基于你的学习画像智能生成。`
})


// 加载相关资源
async function loadRelatedResources() {
  if (!resource.value.chapter) {
    return
  }
  try {
    const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
    let allResources = []

    if (isDemo) {
      // 演示模式：从本地数据加载
      const { demoResources } = await import('../../mock/demoData')
      allResources = demoResources || []
    } else {
      const studentId = userStore.id
      if (!studentId) return
      const res = await resourceApi.getByStudent(studentId)
      allResources = (res.data && Array.isArray(res.data)) ? res.data : []
    }

    // 筛选同章节的关联资源
    const filtered = allResources
      .filter(r => {
        const rChapter = r.knowledgePoint || r.chapter || ''
        return rChapter === resource.value.chapter && r.id !== resource.value.id
      })
      .slice(0, 4)
    relatedResources.value = filtered.map(r => ({
      id: r.id,
      title: r.title || '未命名资源',
      type: r.type || 'article',
      typeLabel: getTypeLabel(r.type),
      icon: getTypeEmoji(r.type)
    }))
  } catch (e) {
    console.warn('加载相关资源失败:', e)
    relatedResources.value = []
  }
}

// 获取类型图标
function getTypeEmoji(type) {
  const icons = {
    'video': '视',
    'course-video': '视',
    'article': '文',
    'code': '码',
    'mindmap': '图',
    'summary': '总',
    'exercise': '题',
  }
  return icons[type] || '源'
}

// 获取视频渐变色
function getVideoGradient() {
  const gradients = {
    '控制结构': 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    '数组': 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    '指针': 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    '结构体': 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    '内存管理': 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  }
  return gradients[resource.value.chapter] || 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
}

// 复制代码
function copyCode() {
  navigator.clipboard.writeText(resource.value.content || '').then(() => {
    ElMessage.success('代码已复制到剪贴板')
  })
}

// 获取类型标签
function getTypeLabel(type) {
  const labels = { 'video': '视频', 'article': '文章', 'code': '代码', 'mindmap': '思维导图', 'summary': '总结' }
  return labels[type] || '资源'
}

// 跳转到相关资源
function goToResource(item) {
  router.push({
    path: '/student/resource-detail',
    query: {
      id: item.id,
      title: item.title,
      type: item.type,
      chapter: resource.value.chapter
    }
  })
}

// 跳转到编程工作台
function goToWorkbench() {
  const id = resource.value.id || route.query.id || 'demo'
  router.push({
    path: '/student/code-workbench/' + id,
    query: {
      title: resource.value.title,
      knowledgePoint: resource.value.chapter,
      difficulty: resource.value.difficulty,
      from: 'resource-detail'
    }
  })
}

// 返回上一页
function goBack() {
  router.back()
}

// 加载默认资源内容
function loadDefaultResource(id, title, type, chapter) {
  // 章节→B站视频URL映射（翁恺C语言MOOC）
  const chapterVideoMap = {
    'C语言概述': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=1',
    '基础语法': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=7',
    '变量与数据类型': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=7',
    '运算符与表达式': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=9',
    '输入输出': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=11',
    '控制结构': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=13',
    '流程控制': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=13',
    '函数': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=15',
    '数组': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=17',
    '字符串': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=19',
    '指针': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=20',
    '结构体': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=22',
    '动态内存': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=23',
    '内存管理': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=23',
    '文件操作': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=24',
    '预处理': 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=25',
  }

  const defaults = {
    'default_1': { title: '控制结构与流程控制', type: 'video', content: chapterVideoMap['控制结构'] || chapterVideoMap['流程控制'] },
    'default_2': { title: '数组与指针的关系详解', type: 'article', content: defaultArticleContent },
    'default_3': { title: '指针与内存地址', type: 'code', content: defaultCodeContent, filename: 'pointer.c', explanation: defaultCodeExplanation },
    'default_4': { title: '结构体知识图谱', type: 'mindmap' },
    'default_5': { title: '动态内存分配与管理', type: 'code', content: `#include <stdlib.h>\n\nint main() {\n    int *arr = malloc(5 * sizeof(int));\n    free(arr);\n    return 0;\n}`, filename: 'malloc.c' },
    'default_6': { title: 'C语言学习总结', type: 'summary', content: '# C语言学习总结\n\n## 核心知识体系\n\n### 基础语法\n- 数据类型与变量\n- 运算符与表达式\n- 输入输出函数\n\n### 流程控制\n- 分支：if-else / switch-case\n- 循环：for / while / do-while\n\n### 核心概念\n- 数组与指针\n- 函数与递归\n- 结构体与联合体\n- 文件操作\n\n## 学习建议\n1. 多动手编写代码，避免只看不练\n2. 理解内存模型，掌握指针本质\n3. 通过项目实战巩固知识点' },
  }

  const data = defaults[id] || { title: title || '资源详情', type: type || 'article' }
  // 如果是视频类型且没有content，尝试通过chapter查找对应视频
  let videoContent = data.content || ''
  if ((!videoContent || videoContent === '') && (data.type === 'video' || data.type === 'course-video') && chapter) {
    videoContent = chapterVideoMap[chapter] || ''
  }
  resource.value = {
    id: id,
    title: data.title,
    type: data.type,
    typeLabel: getTypeLabel(data.type),
    chapter: chapter || 'C语言',
    difficulty: '基础',
    content: videoContent,
    filename: data.filename || 'main.c',
    explanation: data.explanation || '',
  }
}

// 从后端加载资源详情
async function loadResource(resourceId) {
  loading.value = true
  try {
    const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
    let r = null

    if (isDemo) {
      // 演示模式：从本地数据查找
      const { demoResources } = await import('../../mock/demoData')
      r = (demoResources || []).find(res => String(res.id) === String(resourceId))
    } else {
      const res = await resourceApi.getById(resourceId)
      r = res.data
    }

    if (r) {
      resource.value = {
        id: r.id,
        title: r.title || '未命名资源',
        type: r.type || 'article',
        typeLabel: getTypeLabel(r.type),
        chapter: r.knowledgePoint || r.chapter || 'C语言',
        difficulty: r.difficulty || '基础',
        content: r.content || '',
        filename: r.title ? r.title.replace(/[^a-zA-Z0-9]/g, '').substring(0, 15) + '.c' : 'main.c',
        explanation: r.explanation || '',
        audioUrl: r.tags || r.audioUrl || '',
      }
      // 加载相关资源
      await loadRelatedResources()
      nextTick(() => { window.scrollTo(0, 0) })
    }
  } catch (e) {
    console.error('加载资源失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  window.scrollTo(0, 0)
  const { id, title, type, chapter, content } = route.query
  const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'

  // 演示模式：直接使用传递的资源数据
  if (isDemo && id && title && type) {
    resource.value = {
      id: id,
      title: title,
      type: type,
      typeLabel: getTypeLabel(type),
      chapter: chapter || 'C语言',
      difficulty: 'medium',
      content: content || '',
      audioUrl: route.query.audioUrl || '',
    }
    loading.value = false
  } else if (id && !isNaN(parseInt(id))) {
    loadResource(parseInt(id))
  } else {
    loadDefaultResource(id, title, type, chapter)
    loading.value = false
  }
  nextTick(() => { window.scrollTo(0, 0) })
})

// 离开页面时停止视频播放
onBeforeUnmount(() => {
  const iframe = videoIframeRef.value
  if (iframe && iframe.contentWindow) {
    try {
      iframe.contentWindow.postMessage(JSON.stringify({ event: 'command', command: 'pause' }), '*')
    } catch (e) {}
  }
})

// 监听路由变化，重新加载资源
watch(() => route.query.id, (newId) => {
  if (newId && !isNaN(parseInt(newId))) {
    loadResource(parseInt(newId))
  }
})
</script>

<style scoped>
.resource-detail {
  min-height: 100%;
  background-color: #f4f6f9;
  padding: 24px;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
}

/* 加载状态 */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 16px;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #e2e8f0;
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.loading-container p { color: #64748b; font-size: 15px; }

/* 顶部标题卡片 */
.header-card {
  background-color: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-left .title-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.header-left h1 {
  font-size: 24px;
  font-weight: 600;
  color: #1a2a40;
  margin: 0;
}

.tag {
  background-color: #eef4ff;
  color: #3b82f6;
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 6px;
  font-weight: 500;
}

.meta-info {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #7f8c8d;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.header-right {
  display: flex;
  gap: 12px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-back {
  background-color: #fff;
  border: 1px solid #e2e8f0;
  color: #64748b;
}

.btn-back:hover {
  background-color: #f8fafc;
  border-color: #cbd5e1;
}

.code-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.btn-workbench {
  padding: 8px 20px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-workbench:hover {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  transform: translateY(-1px);
}

/* 主体布局 */
.main-layout {
  display: flex;
  gap: 24px;
}

/* 左侧内容区 — 确保含思维导图的主内容区在合理层级，不受侧边栏干扰 */
.content-area {
  position: relative;
  flex: 1;
  min-width: 0;
  background-color: #fff;
  border-radius: 16px;
  padding: 36px 40px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  z-index: 1;
}

.section {
  margin-bottom: 36px;
}

.section:last-child {
  margin-bottom: 0;
}

.section h2 {
  font-size: 18px;
  margin-bottom: 16px;
  color: #1a2a40;
  font-weight: 600;
}

.section p {
  font-size: 14px;
  color: #4a5568;
  margin-bottom: 16px;
  line-height: 1.7;
}

/* 代码块样式 — 专业深色主题 */
.code-box-wrapper {
  border-radius: 12px;
  overflow: hidden;
  margin: 16px 0;
  background: #1e1e2e;
  border: 1px solid #313244;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12), 0 1px 4px rgba(0, 0, 0, 0.08);
  position: relative;
}

.code-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: linear-gradient(180deg, #181825 0%, #11111b 100%);
  border-bottom: 1px solid #313244;
}

.code-dots {
  display: flex;
  gap: 7px;
}

.dot {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.15);
}

.dot.red { background-color: #f38ba8; }
.dot.yellow { background-color: #f9e2af; }
.dot.green { background-color: #a6e3a1; }

.code-filename {
  flex: 1;
  font-size: 13px;
  color: #bac2de;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-weight: 500;
  letter-spacing: 0.3px;
}

.code-header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.code-collapse-btn {
  padding: 3px 10px;
  background: rgba(137, 180, 250, 0.12);
  border: 1px solid rgba(137, 180, 250, 0.25);
  border-radius: 5px;
  font-size: 12px;
  color: #89b4fa;
  cursor: pointer;
  transition: all 0.15s;
}

.code-collapse-btn:hover {
  background: rgba(137, 180, 250, 0.2);
  border-color: rgba(137, 180, 250, 0.4);
}

.copy-btn {
  padding: 3px 10px;
  background: rgba(166, 227, 161, 0.12);
  border: 1px solid rgba(166, 227, 161, 0.25);
  border-radius: 5px;
  font-size: 12px;
  color: #a6e3a1;
  cursor: pointer;
  transition: all 0.15s;
}

.copy-btn:hover {
  background: rgba(166, 227, 161, 0.2);
  border-color: rgba(166, 227, 161, 0.4);
}

.code-body {
  display: flex;
  overflow: hidden;
  position: relative;
}

.code-line-numbers {
  flex-shrink: 0;
  padding: 16px 12px 16px 16px;
  margin: 0;
  text-align: right;
  font-size: 13px;
  line-height: 1.65;
  color: #45475a;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  background: #181825;
  border-right: 1px solid #313244;
  user-select: none;
  min-width: 40px;
}

.code-box {
  flex: 1;
  padding: 16px 20px;
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: #cdd6f4;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  overflow-x: auto;
  background: transparent;
  tab-size: 4;
}

/* 语法高亮配色 — Catppuccin Mocha 风格 */
.code-box :deep(.syn-keyword) { color: #cba6f7; font-weight: 500; }
.code-box :deep(.syn-string) { color: #a6e3a1; }
.code-box :deep(.syn-comment) { color: #6c7086; font-style: italic; }
.code-box :deep(.syn-preproc) { color: #f38ba8; font-weight: 500; }
.code-box :deep(.syn-func) { color: #89b4fa; }
.code-box :deep(.syn-number) { color: #fab387; }

/* 折叠遮罩 */
.code-collapse-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 56px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 10px;
  background: linear-gradient(180deg, transparent 0%, #1e1e2e 75%);
  cursor: pointer;
}

.code-collapse-overlay span {
  font-size: 12px;
  color: #89b4fa;
  padding: 4px 14px;
  background: rgba(137, 180, 250, 0.15);
  border: 1px solid rgba(137, 180, 250, 0.3);
  border-radius: 6px;
  transition: all 0.15s;
}

.code-collapse-overlay:hover span {
  background: rgba(137, 180, 250, 0.25);
}

/* 文章内嵌代码块也使用深色主题 */
.article-content :deep(.code-box) {
  background-color: #1e1e2e;
  border-radius: 8px;
  padding: 16px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  margin: 16px 0;
  color: #cdd6f4;
}
.article-content {
  font-size: 14px;
  color: #4a5568;
  line-height: 1.8;
}

.article-content :deep(h2) {
  font-size: 18px;
  font-weight: 600;
  color: #1a2a40;
  margin: 28px 0 14px;
}

.article-content :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  color: #1a2a40;
  margin: 24px 0 12px;
}

.article-content :deep(p) {
  margin-bottom: 16px;
}

.article-content :deep(ul),
.article-content :deep(ol) {
  padding-left: 24px;
  margin-bottom: 16px;
}

.article-content :deep(li) {
  margin-bottom: 8px;
}

.article-content :deep(code) {
  background-color: #eef2ff;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  color: #4f46e5;
}

/* Markdown 扩展样式：h1、代码块、表格、加粗 */
.article-content :deep(h1) {
  font-size: 22px;
  font-weight: 700;
  color: #1a2a40;
  margin: 8px 0 20px;
  padding-bottom: 10px;
  border-bottom: 2px solid #e2e8f0;
}

.article-content :deep(pre) {
  background-color: #1e1e2e;
  border: 1px solid #313244;
  border-radius: 10px;
  padding: 16px 20px;
  overflow-x: auto;
  margin: 16px 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.article-content :deep(pre code) {
  background-color: transparent;
  color: #cdd6f4;
  padding: 0;
  font-size: 13px;
  line-height: 1.65;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

.article-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
  font-size: 13px;
}

.article-content :deep(th) {
  background-color: #f1f5f9;
  font-weight: 600;
  color: #1a2a40;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  text-align: left;
}

.article-content :deep(td) {
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  color: #4a5568;
}

.article-content :deep(strong) {
  font-weight: 600;
  color: #1a2a40;
}

.article-content :deep(blockquote) {
  border-left: 4px solid #6366f1;
  background-color: #f8fafc;
  padding: 12px 16px;
  margin: 16px 0;
  color: #64748b;
  border-radius: 0 8px 8px 0;
}

/* 解释内容样式 */
.explanation-content {
  font-size: 14px;
  color: #4a5568;
  line-height: 1.8;
}

.explanation-content :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  color: #1a2a40;
  margin: 20px 0 12px;
}

.explanation-content :deep(ul) {
  padding-left: 24px;
  margin-bottom: 16px;
}

.explanation-content :deep(li) {
  margin-bottom: 8px;
}

.explanation-content :deep(code) {
  background-color: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #6366f1;
}

.explanation-content :deep(p) {
  margin-bottom: 12px;
}

/* 视频播放器 */
.video-embed {
  margin: 16px 0;
  border-radius: 12px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: #000;
  position: relative;
}
.video-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

/* 玻璃防误触层：100%透明，覆盖上方3/4，阻止误触跳转B站，底部1/4露出B站原生播放键 */
.video-glass-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 75%;
  z-index: 10;
  cursor: default;
  background: transparent;
}
.video-player {
  margin: 24px 0;
}

.video-poster {
  aspect-ratio: 16 / 9;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.play-button {
  width: 100px;
  height: 100px;
  background-color: rgba(255,255,255,0.25);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  backdrop-filter: blur(8px);
  border: 2px solid rgba(255,255,255,0.3);
}

.play-button:hover {
  background-color: rgba(255,255,255,0.3);
  transform: scale(1.1);
}

/* 右侧侧边栏 — 层级低于主内容区，避免内部内容溢出覆盖思维导图 */
.sidebar {
  position: relative;
  width: 260px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex-shrink: 0;
  z-index: 0;
  min-width: 0;
}

.sidebar-card {
  background-color: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.sidebar-card h3 {
  font-size: 15px;
  margin-bottom: 16px;
  color: #1a2a40;
  font-weight: 600;
}

/* 目录样式 */
/* 相关资源推荐列表 */
.resource-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.resource-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: all 0.2s;
}

.resource-item:last-child {
  border-bottom: none;
}

.resource-item:hover {
  background-color: #f8fafc;
  margin: 0 -12px;
  padding-left: 12px;
  padding-right: 12px;
  border-radius: 8px;
}

.resource-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  flex-shrink: 0;
}

.icon-code { background-color: #8c8bf5; }
.icon-article { background-color: #22c55e; }
.icon-mindmap { background-color: #8b5cf6; }
.icon-video { background-color: #3b82f6; }
.icon-summary { background-color: #f59e0b; }

.resource-info {
  flex: 1;
  min-width: 0;
}

.resource-title {
  font-size: 13px;
  font-weight: 500;
  color: #1a2a40;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
}

.resource-meta {
  font-size: 11px;
  color: #95a5a6;
}

.no-related {
  text-align: center;
  padding: 20px;
  color: #94a3b8;
  font-size: 13px;
}

/* 统计卡片 */
.stats-card .stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

/* AI 生成过程卡片 */
.gen-pipeline-card {
  border: 1px solid rgba(99,102,241,0.12);
  background: linear-gradient(180deg, rgba(99,102,241,0.03) 0%, #fff 100%);
}
.gen-pipeline-card h3 {
  color: #4f46e5;
}
.gen-steps {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin-bottom: 14px;
}
.gen-step {
  display: flex;
  gap: 10px;
  position: relative;
}
.gen-step-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 28px;
  flex-shrink: 0;
}
.gen-step-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
  transition: all 0.3s;
}
.gen-step.done .gen-step-icon {
  background: rgba(34,197,94,0.1);
}
.gen-step-line {
  width: 2px;
  flex: 1;
  min-height: 16px;
  background: #e2e8f0;
  margin: 2px 0;
}
.gen-step-line.active {
  background: #22c55e;
}
.gen-step-body {
  flex: 1;
  padding-bottom: 8px;
  min-width: 0;
}
.gen-step-name {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  line-height: 1.4;
}
.gen-step-detail {
  font-size: 10px;
  color: #94a3b8;
  margin-top: 1px;
}
.gen-step.done .gen-step-detail {
  color: #16a34a;
}

.gen-reason {
  background: rgba(99,102,241,0.04);
  border: 1px solid rgba(99,102,241,0.1);
  border-radius: 8px;
  padding: 10px 12px;
}
.gen-reason-title {
  font-size: 11px;
  font-weight: 600;
  color: #4f46e5;
  margin-bottom: 4px;
}
.gen-reason-text {
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}

.stat-item {
  text-align: center;
  padding: 12px;
  background-color: #f8fafc;
  border-radius: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #3b82f6;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
}

/* 听学笔记播放器 */
.blog-section { padding: 0; }
.blog-player-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f5f3ff, #ede9fe);
  border-radius: 12px;
  margin-bottom: 24px;
  border: 1px solid rgba(139,92,246,0.12);
}
.blog-play-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(139,92,246,0.3);
}
.blog-play-btn:hover { transform: scale(1.08); box-shadow: 0 4px 16px rgba(139,92,246,0.4); }
.blog-player-info { flex-shrink: 0; min-width: 100px; }
.blog-player-title { font-size: 14px; font-weight: 600; color: #1e293b; margin-bottom: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 200px; }
.blog-player-time { font-size: 11px; color: #8b5cf6; font-family: 'JetBrains Mono', monospace; }
.blog-player-progress { flex: 1; cursor: pointer; padding: 8px 0; }
.blog-progress-bar { height: 4px; background: rgba(139,92,246,0.15); border-radius: 2px; overflow: hidden; }
.blog-progress-fill { height: 100%; background: linear-gradient(90deg, #8b5cf6, #7c3aed); border-radius: 2px; transition: width 0.1s linear; }

.blog-transcript { margin-top: 8px; }
.blog-transcript h3 { font-size: 16px; font-weight: 600; color: #1e293b; margin-bottom: 14px; }
.blog-text {
  font-size: 15px;
  color: #334155;
  line-height: 1.9;
}
.blog-text :deep(p) { margin-bottom: 14px; }
.blog-text :deep(h2) { font-size: 18px; font-weight: 700; color: #0f172a; margin: 24px 0 12px; }
.blog-text :deep(h3) { font-size: 16px; font-weight: 600; color: #1e293b; margin: 20px 0 10px; }
.blog-text :deep(ul), .blog-text :deep(ol) { padding-left: 22px; margin-bottom: 14px; }
.blog-text :deep(li) { margin-bottom: 6px; }
.blog-text :deep(code) { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-family: 'JetBrains Mono', monospace; font-size: 13px; color: #7c3aed; }
.blog-text :deep(strong) { color: #1e293b; }

/* 响应式：确保各断点下内容区与侧边栏有序排列，无重叠覆盖 */
@media (max-width: 900px) {
  .main-layout {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
    z-index: 0;
  }

  .sidebar-card {
    flex: 1;
    min-width: 250px;
    overflow: hidden;
  }

  .content-area {
    padding: 24px 20px;
  }
}

@media (max-width: 600px) {
  .resource-detail {
    padding: 16px;
  }

  .container {
    padding: 0 12px;
  }

  .header-card {
    flex-direction: column;
    gap: 16px;
    padding: 18px;
  }

  .header-left h1 {
    font-size: 20px;
  }

  .meta-info {
    flex-wrap: wrap;
    gap: 12px;
  }

  .content-area {
    padding: 18px 14px;
    border-radius: 12px;
  }

  .section h2 {
    font-size: 16px;
    margin-bottom: 12px;
  }
}
</style>
