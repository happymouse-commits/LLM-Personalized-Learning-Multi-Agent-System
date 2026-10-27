<template>
  <div class="lr-section">
    <!-- ═══ 学习资源轮播 ═══ -->
    <div class="lr-resources-card">
      <div class="lr-card-header">
        <div class="lr-card-header-left">
          <span class="lr-header-icon">✨</span>
          <h2 class="lr-card-title">为你生成的学习资源</h2>
          <span class="lr-card-badge">基于你的学习画像和薄弱点，AI为你精选</span>
        </div>
      </div>

      <!-- 无限轮播 -->
      <div class="carousel-wrapper">
        <div class="carousel-track">
          <div
            v-for="(item, idx) in carouselItems"
            :key="item.id"
            class="carousel-item"
            :style="{ animationDelay: `-${idx * (25 / carouselItems.length)}s` }"
          >
            <!-- 翻转卡片 -->
            <div class="flip-card" @click="openResource(item)">
              <!-- 正面：封面 + 标题标签评分（HTML风格） -->
              <div class="flip-card-front">
                <!-- 封面区域 -->
                <div class="card-cover-area">
                  <!-- VS Code 代码封面 -->
                  <div v-if="item.type === 'code'" class="cover-vscode">
                    <div class="vs-titlebar">
                      <div class="vs-tab active"><span class="vs-icon">📄</span> {{ item.filename || 'hello.c' }}</div>
                    </div>
                    <div class="vs-editor">
                      <div v-for="(line, i) in (item.codeLines || defaultCodeLines)" :key="i">
                        <span class="vs-line-num">{{ i + 1 }}</span>
                        <span :class="'vs-' + line.cls" v-html="line.text"></span>
                      </div>
                    </div>
                    <div class="vs-overlay"></div>
                    <div class="vs-badge">{{ item.typeIcon }} {{ item.typeLabel }}</div>
                  </div>

                  <!-- 课件PPT/文章 书籍封面 -->
                  <div v-else-if="item.type === 'course-ppt' || item.type === 'article' || item.type === 'explanation'" class="cover-book" :style="{ background: getGradient(item.topic) }">
                    <div class="book-icon">{{ item.typeIcon }}</div>
                    <div class="book-title">{{ item.title }}</div>
                    <div class="book-sub">{{ item.topic }} · {{ item.level }}</div>
                    <div class="book-byline">C语言程序设计</div>
                    <div class="vs-overlay"></div>
                    <div class="vs-badge">{{ item.typeIcon }} {{ item.typeLabel }}</div>
                  </div>

                  <!-- 视频封面 -->
                  <div v-else-if="item.type === 'video' || item.type === 'course-video'" class="cover-book" :style="{ background: getGradient(item.topic) }">
                    <div class="book-icon">🎬</div>
                    <div class="book-title">{{ item.title }}</div>
                    <div class="book-sub">{{ item.topic }} · {{ item.duration || '15:00' }}</div>
                    <div class="vs-overlay"></div>
                    <div class="vs-badge">{{ item.typeIcon }} {{ item.typeLabel }}</div>
                  </div>

                  <!-- 思维导图封面 -->
                  <div v-else-if="item.type === 'mindmap'" class="cover-mindmap">
                    <div class="mindmap-preview">
                      <div class="mindmap-center">{{ item.mindmapCenter || 'C语言' }}</div>
                      <div class="mindmap-branches">
                        <div v-for="(branch, i) in (item.mindmapBranches || defaultBranches)" :key="i" class="mindmap-branch" :style="{ transform: `rotate(${i * 60}deg)` }">
                          <div class="branch-line"></div>
                          <div class="branch-node">{{ branch }}</div>
                        </div>
                      </div>
                    </div>
                    <div class="vs-overlay"></div>
                    <div class="vs-badge">{{ item.typeIcon }} {{ item.typeLabel }}</div>
                  </div>

                  <!-- 练习题封面 -->
                  <div v-else-if="item.type === 'exercise'" class="cover-book" :style="{ background: getGradient(item.topic) }">
                    <div class="book-icon">✏️</div>
                    <div class="book-title">练习题</div>
                    <div class="book-sub">{{ item.topic }} · 巩固训练</div>
                    <div class="vs-overlay"></div>
                    <div class="vs-badge">{{ item.typeIcon }} {{ item.typeLabel }}</div>
                  </div>

                  <!-- 总结封面 -->
                  <div v-else-if="item.type === 'summary'" class="cover-notebook">
                    <div class="nb-title">📋 {{ item.title }}</div>
                    <ul class="nb-list">
                      <li v-for="(point, i) in (item.points || defaultPoints)" :key="i">{{ point }}</li>
                    </ul>
                    <div class="vs-overlay"></div>
                    <div class="vs-badge">{{ item.typeIcon }} {{ item.typeLabel }}</div>
                  </div>

                  <!-- 默认封面 -->
                  <div v-else class="cover-book" :style="{ background: getGradient(item.topic) }">
                    <div class="book-icon">{{ getIcon(item.topic) }}</div>
                    <div class="book-title">{{ item.topic }}</div>
                    <div class="book-sub">{{ item.level }}</div>
                    <div class="vs-overlay"></div>
                    <div class="vs-badge">{{ item.typeIcon }} {{ item.typeLabel }}</div>
                  </div>
                </div>

                <!-- 卡片内容区（标题+标签+评分） -->
                <div class="card-body">
                  <div class="card-title">{{ item.title }}</div>
                  <div class="card-tags">
                    <span class="card-tag" :class="'tag-' + item.type">{{ item.typeIcon }} {{ item.typeLabel }}</span>
                    <span class="card-tag tag-chapter">{{ item.topic }}</span>
                    <span class="card-score"><span class="score-dot"></span> {{ item.level === '进阶' ? '92' : item.level === '入门' ? '88' : '95' }}%</span>
                  </div>
                </div>
              </div>

              <!-- 背面：简介 -->
              <div class="flip-card-back" :style="{ background: getGradient(item.topic) }">
                <div class="back-content">
                  <div class="back-icon">{{ item.typeIcon }}</div>
                  <h3 class="back-title">{{ item.title }}</h3>
                  <p class="back-desc">{{ item.description || getDefaultDesc(item) }}</p>
                  <div class="back-tags">
                    <span class="back-tag">{{ item.typeLabel }}</span>
                    <span class="back-tag">{{ item.level }}</span>
                  </div>
                  <div class="back-action">
                    <span>点击查看详情</span>
                    <span class="arrow">→</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  resources: { type: Array, default: () => [] },
  task: { type: Object, default: () => ({}) }
})

defineEmits(['refresh'])

const router = useRouter()

// 知识点 → 封面配置映射
const chapterConfig = {
  '控制结构': { gradient: 'linear-gradient(145deg, #667eea 0%, #764ba2 100%)', icon: '🔄', keywords: ['流程控制', '选择结构', '循环结构', 'if', 'for', 'while'] },
  '数组': { gradient: 'linear-gradient(145deg, #f093fb 0%, #f5576c 100%)', icon: '📊', keywords: ['指针与数组', '数组指针', '一维数组', '二维数组'] },
  '指针': { gradient: 'linear-gradient(145deg, #4facfe 0%, #00f2fe 100%)', icon: '🎯', keywords: ['指针与内存', '内存地址', '指针变量', '二级指针'] },
  '数组与指针': { gradient: 'linear-gradient(145deg, #667eea 0%, #764ba2 100%)', icon: '🎯', keywords: ['数组', '指针', '动态内存', '指针运算'] },
  '结构体': { gradient: 'linear-gradient(145deg, #43e97b 0%, #38f9d7 100%)', icon: '🏗️', keywords: ['结构体内存布局', '共用体', '联合体', '枚举'] },
  '内存管理': { gradient: 'linear-gradient(145deg, #fa709a 0%, #fee140 100%)', icon: '💾', keywords: ['动态内存分配', 'malloc', 'free', '内存泄漏'] },
  '函数指针': { gradient: 'linear-gradient(145deg, #a18cd1 0%, #fbc2eb 100%)', icon: '🔗', keywords: ['回调机制', '函数指针', '回调函数'] },
  '函数': { gradient: 'linear-gradient(145deg, #ffecd2 0%, #fcb69f 100%)', icon: '⚡', keywords: ['函数定义', '参数传递', '递归', '作用域'] },
  '文件操作': { gradient: 'linear-gradient(145deg, #89f7fe 0%, #66a6ff 100%)', icon: '📁', keywords: ['fopen', 'fclose', 'fread', 'fwrite'] },
  '综合': { gradient: 'linear-gradient(145deg, #a8edea 0%, #fed6e3 100%)', icon: '📚', keywords: ['C语言', '基础'] },
}

// 资源类型 → 图标/标签
const typeConfig = {
  'course-video': { icon: '🎬', label: '视频课' },
  'video': { icon: '🎬', label: '视频课' },
  'article': { icon: '📄', label: '学习笔记' },
  'exercise': { icon: '✏️', label: '练习题' },
  'mindmap': { icon: '🗺️', label: '思维导图' },
  'code': { icon: '💻', label: '代码示例' },
  'explanation': { icon: '📖', label: '详解' },
  'summary': { icon: '📊', label: '学习总结' },
  'weakness': { icon: '⚠️', label: '薄弱分析' },
}

// 默认代码预览
const defaultCodePreview = `#include <stdio.h>

int main() {
    int num = 42;
    int *ptr = &num;
    printf("%d", *ptr);
    return 0;
}`

// 默认思维导图分支
const defaultBranches = ['语法', '函数', '数组', '指针', '结构体', '文件']

// 默认代码行（带语法高亮）
const defaultCodeLines = [
  { cls: 'pp', text: '#include' }, { cls: 'inc', text: ' &lt;stdio.h&gt;' },
  { cls: '', text: '' },
  { cls: 'ty', text: 'int' }, { cls: 'fn', text: ' main' }, { cls: 'op', text: '() {' },
  { cls: 'ty', text: '    int' }, { cls: 'op', text: ' num = ' }, { cls: 'num', text: '42' }, { cls: 'op', text: ';' },
  { cls: 'ty', text: '    int' }, { cls: 'op', text: ' *ptr = &' }, { cls: 'op', text: 'num;' },
  { cls: 'fn', text: '    printf' }, { cls: 'op', text: '(' }, { cls: 'str', text: '"%d"' }, { cls: 'op', text: ', *ptr);' },
  { cls: 'kw', text: '    return' }, { cls: 'num', text: ' 0' }, { cls: 'op', text: ';' },
  { cls: 'op', text: '}' },
]

// 默认笔记本要点
const defaultPoints = ['struct 定义与初始化', '点运算符 . 访问成员', '箭头运算符 -> 指针访问', 'typedef 起别名', 'enum 枚举常量']

// 根据知识点查找对应的章节配置
function findChapterConfig(knowledgePoint) {
  if (!knowledgePoint) return null
  const kp = knowledgePoint.toLowerCase()

  for (const [key, config] of Object.entries(chapterConfig)) {
    if (kp.includes(key) || key.toLowerCase().includes(kp)) {
      return { chapter: key, ...config }
    }
  }

  for (const [key, config] of Object.entries(chapterConfig)) {
    for (const keyword of config.keywords) {
      if (kp.includes(keyword.toLowerCase()) || keyword.toLowerCase().includes(kp)) {
        return { chapter: key, ...config }
      }
    }
  }

  return null
}

// 获取渐变色
function getGradient(topic) {
  const config = findChapterConfig(topic)
  return config ? config.gradient : 'linear-gradient(145deg, #667eea 0%, #764ba2 100%)'
}

// 获取图标
function getIcon(topic) {
  const config = findChapterConfig(topic)
  return config ? config.icon : '📚'
}

// 获取默认描述
function getDefaultDesc(item) {
  const type = item.type
  const topic = item.topic || 'C语言'

  const descs = {
    'code': `深入学习${topic}的代码实现，包含完整可运行的示例代码和详细注释。`,
    'mindmap': `${topic}知识体系思维导图，帮助你梳理知识点之间的关联关系。`,
    'video': `${topic}视频教程，通过生动的讲解快速掌握核心概念。`,
    'article': `${topic}详细讲解文章，从基础到进阶全面覆盖。`,
    'explanation': `${topic}深度解析，带你理解底层原理和设计思想。`,
    'exercise': `${topic}练习题，通过实战巩固所学知识。`,
    'summary': `${topic}学习总结，回顾重点，查漏补缺。`,
  }

  return descs[type] || `学习${topic}的相关内容，提升你的编程能力。`
}

// 默认资源列表
const defaultResources = [
  {
    id: 'default_1',
    title: '控制结构与流程控制',
    type: 'video',
    topic: '控制结构',
    level: '基础',
    duration: '15:30',
    typeIcon: '🎬',
    typeLabel: '视频课',
    description: '学习if-else、for、while等控制结构，掌握程序流程控制的核心技巧。'
  },
  {
    id: 'default_2',
    title: '数组与指针的关系详解',
    type: 'article',
    topic: '数组',
    level: '基础',
    typeIcon: '📄',
    typeLabel: '学习笔记',
    description: '深入理解数组与指针的内在联系，掌握数组名作为指针的使用方法。'
  },
  {
    id: 'default_3',
    title: '指针与内存地址',
    type: 'code',
    topic: '指针',
    level: '进阶',
    filename: 'pointer.c',
    codePreview: `#include <stdio.h>

int main() {
    int num = 42;
    int *ptr = &num;
    printf("%d", *ptr);
    return 0;
}`,
    typeIcon: '💻',
    typeLabel: '代码示例',
    description: '通过实际代码理解指针的概念，学会声明、初始化和使用指针变量。'
  },
  {
    id: 'default_4',
    title: '结构体知识图谱',
    type: 'mindmap',
    topic: '结构体',
    level: '进阶',
    mindmapCenter: '结构体',
    mindmapBranches: ['定义', '指针', '内存', '链表', '共用体', '枚举'],
    typeIcon: '🗺️',
    typeLabel: '思维导图',
    description: '结构体知识体系全景图，清晰展示各知识点之间的逻辑关系。'
  },
  {
    id: 'default_5',
    title: '动态内存分配与管理',
    type: 'code',
    topic: '内存管理',
    level: '进阶',
    filename: 'malloc.c',
    codePreview: `#include <stdlib.h>

int main() {
    int *arr = malloc(5*sizeof(int));
    for(int i=0; i<5; i++)
        arr[i] = i * 10;
    free(arr);
    return 0;
}`,
    typeIcon: '💻',
    typeLabel: '代码示例',
    description: '掌握malloc、calloc、free等内存管理函数，避免内存泄漏。'
  },
  {
    id: 'default_6',
    title: 'C语言学习总结',
    type: 'summary',
    topic: '综合',
    level: '基础',
    typeIcon: '📊',
    typeLabel: '学习总结',
    description: 'C语言核心知识点总结，帮助你回顾学习成果，发现薄弱环节。'
  },
  {
    id: 'default_7',
    title: '数组与指针专项训练',
    type: 'exercise',
    topic: '数组与指针',
    level: '进阶',
    typeIcon: '✏️',
    typeLabel: '练习题',
    description: '针对数组与指针的专项练习，巩固指针运算和数组操作。'
  },
  {
    id: 'default_8',
    title: '函数与递归精讲',
    type: 'article',
    topic: '函数',
    level: '基础',
    typeIcon: '📄',
    typeLabel: '学习笔记',
    description: '深入理解函数的定义、调用和递归，掌握代码复用的核心技巧。'
  },
]

// 为轮播生成项（复制一份用于无缝循环）
const carouselItems = computed(() => {
  const buildItems = () => {
    const items = [...defaultResources]

    if (props.resources.length > 0) {
      const usedTopics = new Set()
      const backendItems = []

      for (const r of props.resources) {
        const kp = r.knowledgePoint || ''
        if (kp && !usedTopics.has(kp)) {
          usedTopics.add(kp)

          const chapterConf = findChapterConfig(kp)
          const tc = typeConfig[r.type] || { icon: '📄', label: '资源' }

          const backendItem = {
            id: r.id,
            title: r.title,
            type: r.type,
            topic: kp,
            level: r.difficulty === 'hard' ? '进阶' : r.difficulty === 'easy' ? '入门' : '基础',
            typeIcon: tc.icon,
            typeLabel: tc.label,
            description: r.description || `学习${kp}的相关内容，提升你的编程能力。`
          }

          if (r.type === 'code') {
            backendItem.filename = r.title.replace(/[^a-zA-Z0-9一-龥]/g, '').substring(0, 12) + '.c'
            backendItem.codePreview = r.content ? r.content.substring(0, 200) : defaultCodePreview
          } else if (r.type === 'mindmap') {
            backendItem.mindmapCenter = kp
            backendItem.mindmapBranches = chapterConf ? chapterConf.keywords.slice(0, 6) : defaultBranches
          } else if (r.type === 'video' || r.type === 'course-video') {
            backendItem.duration = '15:00'
          }

          backendItems.push(backendItem)
        }
      }

      for (const bItem of backendItems) {
        const idx = items.findIndex(d => {
          if (d.topic === bItem.topic) return true
          const conf = findChapterConfig(bItem.topic)
          if (conf && d.topic === conf.chapter) return true
          return false
        })

        if (idx >= 0) {
          items[idx] = bItem
        } else {
          if (items.length < 9) {
            items.push(bItem)
          }
        }
      }
    }

    return items
  }

  const first = buildItems()
  const second = first.map((item, i) => ({ ...item, id: item.id + '_dup_' + i }))
  return [...first, ...second]
})

function openResource(item) {
  router.push({
    path: '/student/resource-detail',
    query: {
      id: item.id,
      title: item.title,
      type: item.type,
      chapter: item.topic,
      content: item.content || ''
    }
  })
}
</script>

<style scoped>
.lr-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.lr-resources-card {
  background: #fff;
  border-radius: 18px;
  padding: 22px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(0,0,0,0.05);
  overflow: hidden;
}

.lr-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

.lr-card-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.lr-header-icon {
  font-size: 18px;
  animation: lr-pulse 2s ease-in-out infinite;
}

@keyframes lr-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.lr-card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.lr-card-badge {
  font-size: 11px;
  color: #94a3b8;
  background: #f8fafc;
  padding: 3px 10px;
  border-radius: 6px;
}

/* ═══ 无限轮播 ═══ */
.carousel-wrapper {
  width: 100%;
  height: 320px;
  overflow: hidden;
  mask-image: linear-gradient(to right, transparent, #000 8% 92%, transparent);
  -webkit-mask-image: linear-gradient(to right, transparent, #000 8% 92%, transparent);
  padding: 15px 0;
}

.carousel-track {
  display: flex;
  gap: 24px;
  width: max-content;
  animation: scroll-left 35s linear infinite;
}

.carousel-wrapper:hover .carousel-track {
  animation-play-state: paused;
}

@keyframes scroll-left {
  0% { transform: translateX(0); }
  100% { transform: translateX(calc(-50% - 12px)); }
}

.carousel-item {
  flex-shrink: 0;
  width: 320px;
  height: 280px;
  perspective: 1200px;
}

/* ═══ 翻转卡片 ═══ */
.flip-card {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transition: transform 0.6s cubic-bezier(0.175, 0.885, 0.32, 1.275), box-shadow 0.3s ease;
  cursor: pointer;
}

.flip-card:hover {
  transform: rotateY(180deg);
  box-shadow: 0 20px 40px rgba(99, 102, 241, 0.25);
}

.flip-card-front,
.flip-card-back {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  backface-visibility: hidden;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
}

.flip-card-front {
  background: #fff;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(255,255,255,0.8);
}

.flip-card-back {
  transform: rotateY(180deg);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

/* ═══ 正面：封面区域 ═══ */
.card-cover-area {
  position: relative;
  height: 190px;
  overflow: hidden;
  flex-shrink: 0;
  border-radius: 16px 16px 0 0;
}

/* ═══ 正面：卡片内容区 ═══ */
.card-body {
  padding: 14px 16px 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.card-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
  color: #1e293b;
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  letter-spacing: 0.3px;
}

.card-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: auto;
}

.card-tag {
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.tag-code { background: #fef2f2; color: #ef4444; }
.tag-video, .tag-course-video { background: #eff6ff; color: #3b82f6; }
.tag-course-ppt, .tag-article { background: #fff7ed; color: #f97316; }
.tag-mindmap { background: #f0fdf4; color: #16a34a; }
.tag-exercise { background: #fef2f2; color: #ef4444; }
.tag-summary { background: #ecfdf5; color: #059669; }
.tag-chapter { background: #f5f3ff; color: #8b5cf6; }

.card-score {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: 20px;
  font-size: 10px;
  font-weight: 600;
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
  margin-left: auto;
}

.score-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #059669;
}

/* ═══ VS Code 封面 ═══ */
.cover-vscode {
  background: linear-gradient(180deg, #1e1e1e 0%, #252526 100%);
  height: 100%;
  display: flex;
  flex-direction: column;
  font-family: 'JetBrains Mono', 'Cascadia Code', 'SF Mono', Consolas, monospace;
  font-size: 11px;
  line-height: 1.7;
  border-radius: 16px 16px 0 0;
}

.vs-titlebar {
  background: #323233;
  height: 28px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  gap: 6px;
}

.vs-tab {
  background: #2d2d2d;
  padding: 4px 12px;
  border-radius: 4px 4px 0 0;
  font-size: 10px;
  color: #969696;
  display: flex;
  align-items: center;
  gap: 4px;
}

.vs-tab.active {
  background: #1e1e1e;
  color: #fff;
}

.vs-icon {
  font-size: 12px;
}

.vs-editor {
  padding: 8px 12px;
  flex: 1;
  overflow: hidden;
}

.vs-line-num {
  color: #505050;
  display: inline-block;
  width: 24px;
  text-align: right;
  margin-right: 12px;
  user-select: none;
}

.vs-pp { color: #c586c0; }
.vs-ty { color: #4ec9b0; }
.vs-fn { color: #dcdcaa; }
.vs-str { color: #ce9178; }
.vs-num { color: #b5cea8; }
.vs-cm { color: #6a9955; }
.vs-op { color: #d4d4d4; }
.vs-kw { color: #c586c0; }
.vs-inc { color: #ce9178; }

/* ═══ 书籍封面 ═══ */
.cover-book {
  background: linear-gradient(160deg, #0f172a 0%, #1e293b 40%, #334155 100%);
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 20px;
  text-align: center;
  position: relative;
  overflow: hidden;
}

.cover-book::before {
  content: '';
  position: absolute;
  inset: 8px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  pointer-events: none;
}

.cover-book::after {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(255,255,255,0.03) 0%, transparent 70%);
  pointer-events: none;
}

.book-icon {
  font-size: 36px;
  margin-bottom: 8px;
  filter: drop-shadow(0 4px 12px rgba(0,0,0,0.35));
  animation: book-icon-glow 4s ease-in-out infinite;
}

@keyframes book-icon-glow {
  0%, 100% { filter: drop-shadow(0 4px 12px rgba(0,0,0,0.35)); }
  50% { filter: drop-shadow(0 4px 20px rgba(255,255,255,0.2)); }
}

.book-title {
  font-size: 22px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 4px;
  text-shadow: 0 2px 8px rgba(0,0,0,0.25);
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.book-sub {
  font-size: 11px;
  color: rgba(255,255,255,0.5);
  letter-spacing: 2px;
  margin-top: 6px;
}

.book-byline {
  position: absolute;
  bottom: 16px;
  font-size: 10px;
  color: rgba(255,255,255,0.3);
  letter-spacing: 1.5px;
}

/* ═══ 笔记本封面 ═══ */
.cover-notebook {
  background: #fffef5;
  background-image: linear-gradient(rgba(0,0,0,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(0,0,0,0.04) 1px, transparent 1px);
  background-size: 16px 16px;
  height: 100%;
  padding: 14px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.nb-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  border-bottom: 2px solid #6366f1;
  padding-bottom: 4px;
  margin-bottom: 6px;
  display: inline-block;
}

.nb-list {
  list-style: none;
  font-size: 10px;
  color: #475569;
  line-height: 1.7;
  padding: 0;
  margin: 0;
}

.nb-list li::before {
  content: '✓ ';
  color: #22c55e;
  font-weight: bold;
}

/* ═══ 思维导图封面 — 建立独立层叠上下文，中心节点在上，分支在下 ═══ */
.cover-mindmap {
  background: linear-gradient(145deg, #f0f4ff 0%, #e8eeff 100%);
  height: 100%;
  border-radius: 16px 16px 0 0;
  position: relative;
  isolation: isolate;
  overflow: hidden;
}

.mindmap-preview {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  isolation: isolate;
}

.mindmap-center {
  position: absolute;
  width: 68px;
  height: 68px;
  background: linear-gradient(145deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 800;
  color: #fff;
  /* 中心节点保持在最顶层 */
  z-index: 5;
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.55);
  animation: mindmap-pulse 3s ease-in-out infinite;
}

@keyframes mindmap-pulse {
  0%, 100% { box-shadow: 0 8px 24px rgba(99, 102, 241, 0.55); }
  50% { box-shadow: 0 8px 32px rgba(99, 102, 241, 0.75); }
}

.mindmap-branches {
  position: absolute;
  width: 100%;
  height: 100%;
  /* 分支层在中心节点下方 */
  z-index: 1;
  pointer-events: none;
}

.mindmap-branch {
  position: absolute;
  top: 50%;
  left: 50%;
  transform-origin: 0 0;
  /* 各分支内节点保持在同一层级，不覆盖中心 */
  z-index: 1;
}

.branch-line {
  width: 44px;
  height: 3px;
  background: linear-gradient(90deg, #818cf8 0%, #c7d2fe 100%);
  border-radius: 2px;
  box-shadow: 0 1px 4px rgba(99, 102, 241, 0.3);
}

.branch-node {
  position: absolute;
  left: 40px;
  top: -11px;
  padding: 5px 12px;
  background: #fff;
  border: 1px solid #e0e7ff;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  color: #4338ca;
  white-space: nowrap;
  box-shadow: 0 3px 10px rgba(99, 102, 241, 0.18);
  transition: all 0.2s ease;
  /* 分支标签不超过中心节点层级 */
  z-index: 2;
}

/* ═══ 封面通用 ═══ */
.vs-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 40%, rgba(0,0,0,0.4) 100%);
  pointer-events: none;
}

.vs-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(6px);
  padding: 3px 10px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  color: #1e293b;
  box-shadow: 0 2px 6px rgba(0,0,0,0.08);
}

/* ═══ 翻转卡片背面 ═══ */
.back-content {
  text-align: center;
  color: #fff;
}

.back-icon {
  font-size: 48px;
  margin-bottom: 16px;
  filter: drop-shadow(0 4px 12px rgba(0,0,0,0.25));
  animation: back-icon-float 3s ease-in-out infinite;
}

@keyframes back-icon-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.back-title {
  font-size: 18px;
  font-weight: 800;
  margin: 0 0 12px;
  line-height: 1.4;
  text-shadow: 0 2px 8px rgba(0,0,0,0.2);
  letter-spacing: 0.5px;
}

.back-desc {
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 18px;
  opacity: 0.95;
  text-shadow: 0 1px 3px rgba(0,0,0,0.15);
}

.back-tags {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-bottom: 18px;
}

.back-tag {
  padding: 5px 14px;
  background: rgba(255,255,255,0.25);
  border-radius: 14px;
  font-size: 12px;
  font-weight: 600;
  backdrop-filter: blur(6px);
  border: 1px solid rgba(255,255,255,0.2);
}

.back-action {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  padding: 10px 20px;
  background: rgba(255,255,255,0.2);
  border-radius: 25px;
  border: 1px solid rgba(255,255,255,0.3);
  transition: all 0.3s ease;
}

.back-action:hover {
  background: rgba(255,255,255,0.35);
  transform: scale(1.05);
}

.back-action .arrow {
  transition: transform 0.3s;
}

.flip-card:hover .back-action .arrow {
  transform: translateX(4px);
}

/* ═══ 响应式 ═══ */
@media (max-width: 768px) {
  .carousel-wrapper {
    height: 300px;
  }
  .carousel-item {
    width: 280px;
    height: 250px;
  }
  .card-cover-area {
    height: 160px;
  }
  .mindmap-center {
    width: 56px;
    height: 56px;
    font-size: 11px;
  }
  .branch-line {
    width: 36px;
  }
  .branch-node {
    font-size: 10px;
    padding: 4px 10px;
  }
  .card-title {
    font-size: 13px;
  }
  .back-title {
    font-size: 16px;
  }
  .back-icon {
    font-size: 42px;
  }
}
</style>
