<template>
  <div class="chapter-page">
    <!-- 顶部渐变背景 -->
    <div class="chapter-hero" :style="{ background: chapterGradient }">
      <div class="hero-pattern"></div>
      <div class="hero-content">
        <button class="hero-back" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <path d="M19 12H5M12 19l-7-7 7-7" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回学习中心
        </button>
        <div class="hero-info">
          <div class="hero-icon">{{ chapterEmoji }}</div>
          <div class="hero-text">
            <h1 class="hero-title">{{ chapterName }}</h1>
            <div class="hero-meta">
              <span class="hero-diff">{{ diffLabel }}</span>
              <span class="hero-sep">·</span>
              <span>{{ chapterResources.length }} 个资源</span>
            </div>
          </div>
        </div>
        <p class="hero-desc">{{ typeSummary }}</p>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="chapter-body">
      <!-- Tab 导航 -->
      <div class="tab-bar">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          @click="activeTab = tab.id"
          :class="['tab-item', { active: activeTab === tab.id }]"
        >
          <span class="tab-icon">{{ tab.icon }}</span>
          <span class="tab-label">{{ tab.label }}</span>
          <span v-if="tabCount(tab.id)" class="tab-badge">{{ tabCount(tab.id) }}</span>
        </button>
      </div>

      <!-- Tab 内容 -->
      <div class="tab-panel">

        <!-- 概念讲解 - 精美长页面 -->
        <div v-if="activeTab === 'concept'">
          <!-- 优先显示详细章节长页面 -->
          <div v-if="chapterDetail" class="concept-long-page">
            <div class="long-page-header">
              <h1 class="long-page-title">{{ chapterDetail.title }}</h1>
              <div class="long-page-badge">📚 精讲内容 · 共{{ chapterDetail.sections.length }}个知识模块</div>
            </div>
            <div v-for="(sec, idx) in chapterDetail.sections" :key="idx" class="long-section" v-reveal="{delay: idx * 80}">
              <div class="section-icon">{{ sec.icon }}</div>
              <div class="section-content">
                <h2 class="section-title">{{ sec.title }}</h2>
                <div class="section-body" v-html="renderMd(sec.content)"></div>
              </div>
            </div>
            <div class="long-page-footer">
              <div class="footer-tip">💡 学完本章内容后，试试下面的代码示例和练习题吧！</div>
            </div>
          </div>
          <!-- 兜底：后端资源 -->
          <div v-else-if="conceptResources.length" class="concept-card" @click="openPreview(conceptResources[0])">
            <div class="concept-header">
              <div class="concept-icon">📖</div>
              <div class="concept-title-area">
                <h2 class="concept-title">{{ conceptResources[0].title || '概念讲解' }}</h2>
                <div class="concept-meta">
                  <span v-if="conceptResources[0].difficulty" :class="['meta-tag', 'tag-' + conceptResources[0].difficulty]">
                    {{ diffLabelItem(conceptResources[0].difficulty) }}
                  </span>
                  <span v-if="conceptResources[0].hallucinationScore != null" class="meta-tag tag-safe">
                    🛡️ 可信度 {{ (conceptResources[0].hallucinationScore * 100).toFixed(0) }}%
                  </span>
                  <span class="meta-date">📅 {{ formatDate(conceptResources[0].createdAt) }}</span>
                </div>
              </div>
            </div>
            <div class="concept-body" v-html="renderMd(conceptResources[0].content || '')"></div>
          </div>
          <!-- 兜底 -->
          <div v-else class="concept-card demo-concept">
            <div class="concept-header">
              <div class="concept-icon">📖</div>
              <div class="concept-title-area">
                <h2 class="concept-title">{{ demoConcepts[0].title }}</h2>
                <div class="concept-meta">
                  <span class="meta-tag tag-medium">中等难度</span>
                  <span class="meta-date">📅 2026-07-10</span>
                </div>
              </div>
            </div>
            <div class="concept-body">
              <p>{{ demoConcepts[0].desc }}</p>
            </div>
          </div>
        </div>

        <!-- 代码示例 - 精准对应章节，直接展示 -->
        <div v-else-if="activeTab === 'code'">
          <div v-if="codeResources.length" class="code-list">
            <div v-for="(r, idx) in codeResources" :key="r.id" class="code-card-enhanced">
              <div class="code-card-header">
                <div class="code-card-num">{{ idx + 1 }}</div>
                <div class="code-card-info">
                  <h3 class="code-card-title">{{ r.title || '代码示例' }}</h3>
                  <div class="code-card-tags">
                    <span v-if="r.compileStatus === 'PASSED'" class="code-tag tag-pass">✅ 编译通过</span>
                    <span v-else-if="r.compileStatus === 'FAILED'" class="code-tag tag-fail">❌ 编译失败</span>
                    <span v-if="r.hallucinationScore != null" :class="['code-tag', r.hallucinationScore >= 0.8 ? 'tag-verified' : 'tag-warn']">
                      🛡️ 可信度 {{ (r.hallucinationScore * 100).toFixed(0) }}%
                    </span>
                  </div>
                </div>
              </div>
              <div class="code-content-body" v-html="renderMd(r.content || '')"></div>
            </div>
          </div>
          <div v-else class="code-list">
            <div v-for="(item, idx) in demoCodes" :key="idx" class="code-card-enhanced">
              <div class="code-card-header">
                <div class="code-card-num">{{ idx + 1 }}</div>
                <div class="code-card-info">
                  <h3 class="code-card-title">{{ item.title }}</h3>
                  <div class="code-card-tags">
                    <span class="code-tag tag-pass">✅ 编译通过</span>
                    <span class="code-tag tag-verified">🔍 已验证</span>
                  </div>
                </div>
              </div>
              <div class="code-preview-block">
                <div class="code-window">
                  <div class="code-window-bar">
                    <span class="window-dot red"></span>
                    <span class="window-dot yellow"></span>
                    <span class="window-dot green"></span>
                    <span class="window-filename">main.c</span>
                  </div>
                  <pre class="code-window-content"><code>{{ item.code }}</code></pre>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 练习题目 - 简洁入口卡片 -->
        <div v-else-if="activeTab === 'practice'">
          <div class="practice-header">
            <div class="practice-stats">
              <span class="stat-item">📝 共 {{ practiceCount }} 道题</span>
              <span class="stat-item">⏱️ 预计 {{ practiceCount * 2 }} 分钟</span>
            </div>
          </div>
          <div class="practice-overview-card" @click="goToPractice">
            <div class="practice-overview-left">
              <div class="practice-overview-icon">📝</div>
              <div class="practice-overview-info">
                <h3 class="practice-overview-title">{{ chapterName }} · 练习题</h3>
                <p class="practice-overview-desc">共 {{ practiceCount }} 道选择题，涵盖本章核心知识点，AI 根据你的薄弱环节智能组卷</p>
              </div>
            </div>
            <div class="practice-overview-right">
              <div class="practice-overview-stats">
                <div class="po-stat">
                  <span class="po-stat-val">{{ practiceCount }}</span>
                  <span class="po-stat-label">题目</span>
                </div>
                <div class="po-stat">
                  <span class="po-stat-val">{{ practiceCount * 2 }}</span>
                  <span class="po-stat-label">分钟</span>
                </div>
              </div>
              <button class="practice-start-btn-sm">
                <span>▶ 开始答题</span>
              </button>
            </div>
          </div>

          <!-- 编程题形式参考 - 区块头与上方"练习题入口卡片"样式统一 -->
          <div v-if="chapterProgramReferences.length" class="program-ref-section">
            <div class="program-ref-overview-card" @click="openProgramReference(chapterProgramReferences[0])">
              <div class="practice-overview-left">
                <div class="program-ref-overview-icon">💻</div>
                <div class="practice-overview-info">
                  <h3 class="practice-overview-title">{{ chapterName }} · 编程题形式参考</h3>
                  <p class="practice-overview-desc">标准编程题格式样例，含题面描述 / 输入输出 / 样例与代码解析，点击进入编程工作台练习</p>
                </div>
              </div>
              <div class="practice-overview-right">
                <div class="practice-overview-stats">
                  <div class="po-stat">
                    <span class="po-stat-val">{{ chapterProgramReferences.length }}</span>
                    <span class="po-stat-label">参考题</span>
                  </div>
                  <div class="po-stat">
                    <span class="po-stat-val">15</span>
                    <span class="po-stat-label">分钟</span>
                  </div>
                </div>
                <button class="practice-start-btn-sm" @click.stop="openProgramReference(chapterProgramReferences[0])">
                  <span>▶ 开始练习</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 学习资源 - 视频卡片优化 -->
        <div v-else-if="activeTab === 'resource'">
          <div v-if="learningResources.length" class="resource-grid">
            <div v-for="r in learningResources" :key="r.id" class="resource-card-enhanced" @click="openPreview(r)">
              <div class="resource-card-top" :style="{ background: getResourceGradient(r.type) }">
                <div class="resource-type-badge">{{ typeEmoji(r.type) }}</div>
                <div class="resource-play-btn" v-if="r.type === 'course-video'">
                  <span>▶</span>
                </div>
              </div>
              <div class="resource-card-bottom">
                <h3 class="resource-card-title">{{ r.title || '学习资源' }}</h3>
                <div class="resource-card-meta">
                  <span class="resource-tag">{{ typeLabel(r.type) }}</span>
                  <span v-if="r.category" class="resource-category">{{ r.category }}</span>
                </div>
                <div class="resource-card-date">{{ formatDate(r.createdAt) }}</div>
              </div>
            </div>
          </div>
          <div v-else class="resource-grid">
            <div v-for="(item, idx) in demoVideos" :key="idx" class="resource-card-enhanced" @click="openPreview(item)">
              <div class="resource-card-top" style="background: linear-gradient(135deg, #06b6d4, #0891b2);">
                <div class="resource-type-badge">🎬</div>
                <div class="resource-play-btn">
                  <span>▶</span>
                </div>
              </div>
              <div class="resource-card-bottom">
                <h3 class="resource-card-title">{{ item.title }}</h3>
                <div class="resource-card-meta">
                  <span class="resource-tag">视频</span>
                  <span class="resource-category">翁恺C语言</span>
                </div>
                <div class="resource-card-date">2026-07-10</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 听学博客 - 音频学习资源 -->
        <div v-else-if="activeTab === 'blog'">
          <div v-if="blogResources.length" class="resource-grid">
            <div v-for="r in blogResources" :key="r.id" class="resource-card-enhanced blog-card">
              <div class="resource-card-top" style="background: linear-gradient(135deg, #8b5cf6, #7c3aed);">
                <div class="resource-type-badge">🎧</div>
                <div class="resource-play-btn" @click.stop="toggleAudio(r)">
                  <span>{{ audioPlaying === r.id ? '⏸' : '▶' }}</span>
                </div>
              </div>
              <div class="resource-card-bottom">
                <h3 class="resource-card-title">{{ r.title || '听学博客' }}</h3>
                <div class="resource-card-meta">
                  <span class="resource-tag">听学笔记</span>
                  <span v-if="r.duration" class="resource-duration">⏱️ {{ r.duration }}</span>
                  <span v-if="r.hallucinationScore != null" :class="['resource-tag', r.hallucinationScore >= 0.8 ? 'tag-safe' : 'tag-warn']">
                    🛡️ {{ (r.hallucinationScore * 100).toFixed(0) }}%
                  </span>
                  <span v-if="r.category" class="resource-category">{{ r.category }}</span>
                </div>
                <!-- 音频播放器 -->
                <div v-if="r.audioUrl && audioPlaying === r.id" class="audio-player">
                  <audio ref="audioRef" :src="r.audioUrl" controls autoplay class="audio-element"></audio>
                </div>
                <div v-else-if="r.audioUrl" class="audio-hint">
                  <span class="audio-hint-icon">🔊</span>
                  <span>点击播放音频</span>
                </div>
                <div class="resource-card-date">{{ formatDate(r.createdAt) }}</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <div class="empty-icon">🎧</div>
            <p class="empty-text">暂无听学博客资源</p>
            <p class="empty-hint">AI正在为你准备音频学习内容...</p>
          </div>
        </div>

        <!-- 学习总结 - 直接展示内容 -->
        <div v-else-if="activeTab === 'summary'">
          <div v-if="summaryResources.length" class="concept-long-page">
            <div v-for="(r, idx) in summaryResources" :key="r.id">
              <div class="long-page-header" style="background: linear-gradient(135deg, #06b6d4, #0891b2);">
                <h1 class="long-page-title">{{ r.title || '学习总结' }}</h1>
                <div class="long-page-badge">
                  📊 本章知识点总结
                  <span v-if="r.hallucinationScore != null" style="margin-left: 12px; padding: 4px 10px; background: rgba(255,255,255,0.25); border-radius: 20px; font-size: 13px;">
                    🛡️ 可信度 {{ (r.hallucinationScore * 100).toFixed(0) }}%
                  </span>
                </div>
              </div>
              <div class="long-section">
                <div class="section-content">
                  <div class="section-body" v-html="renderMd(r.content || '')"></div>
                </div>
              </div>
            </div>
          </div>
          <!-- 兜底：使用 chapterDetailContent 中的学习总结 -->
          <div v-else-if="chapterSummarySection" class="concept-long-page">
            <div class="long-page-header" style="background: linear-gradient(135deg, #06b6d4, #0891b2);">
              <h1 class="long-page-title">{{ chapterName }} · 学习总结</h1>
              <div class="long-page-badge">📊 本章知识点总结</div>
            </div>
            <div class="long-section">
              <div class="section-icon">📊</div>
              <div class="section-content">
                <h2 class="section-title">{{ chapterSummarySection.title }}</h2>
                <div class="section-body" v-html="renderMd(chapterSummarySection.content || '')"></div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <div class="empty-icon">📊</div>
            <p class="empty-text">暂无学习总结</p>
            <p class="empty-hint">AI正在为你准备学习总结...</p>
          </div>
        </div>

        <!-- 薄弱分析 - 直接展示内容 -->
        <div v-else-if="activeTab === 'analysis'">
          <div v-if="weaknessResources.length" class="concept-long-page">
            <div v-for="(r, idx) in weaknessResources" :key="r.id">
              <div class="long-page-header" style="background: linear-gradient(135deg, #f43f5e, #e11d48);">
                <h1 class="long-page-title">{{ r.title || '薄弱分析' }}</h1>
                <div class="long-page-badge">
                  🎯 个性化薄弱点分析
                  <span v-if="r.hallucinationScore != null" style="margin-left: 12px; padding: 4px 10px; background: rgba(255,255,255,0.25); border-radius: 20px; font-size: 13px;">
                    🛡️ 可信度 {{ (r.hallucinationScore * 100).toFixed(0) }}%
                  </span>
                </div>
              </div>
              <div class="long-section">
                <div class="section-content">
                  <div class="section-body" v-html="renderMd(r.content || '')"></div>
                </div>
              </div>
            </div>
          </div>
          <!-- 兜底：使用 chapterDetailContent 中的薄弱点分析 -->
          <div v-else-if="chapterWeaknessSection" class="concept-long-page">
            <div class="long-page-header" style="background: linear-gradient(135deg, #f43f5e, #e11d48);">
              <h1 class="long-page-title">{{ chapterName }} · 薄弱点分析</h1>
              <div class="long-page-badge">🎯 个性化薄弱点分析</div>
            </div>
            <div class="long-section">
              <div class="section-icon">🎯</div>
              <div class="section-content">
                <h2 class="section-title">{{ chapterWeaknessSection.title }}</h2>
                <div class="section-body" v-html="renderMd(chapterWeaknessSection.content || '')"></div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">
            <div class="empty-icon">🎯</div>
            <p class="empty-text">暂无薄弱分析</p>
            <p class="empty-hint">AI正在为你分析薄弱点...</p>
          </div>
        </div>

      </div>
    </div>

    <!-- 预览弹窗 -->
    <Teleport to="body">
      <div v-if="previewResource" class="modal-overlay" @click.self="previewResource = null">
        <div class="modal-box">
          <div class="modal-header">
            <h2 class="modal-title">{{ previewResource.title }}</h2>
            <button class="modal-close" @click="previewResource = null">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
                <path d="M18 6L6 18M6 6l12 12" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
          <div class="modal-body" v-if="previewResource.type === 'course-video'">
            <VideoPlayer :src="previewResource.content" :page="extractPage(previewResource)" />
          </div>
          <div class="modal-body" v-else-if="previewResource.type === 'mindmap'">
            <MindMapViewer :content="previewResource.content" />
          </div>
          <div class="modal-body markdown-body" v-else v-html="renderMd(previewResource.content || '')"></div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { useDemoStore } from '../../store/demo'
import { marked } from 'marked'
marked.use({ breaks: true, gfm: true })
import api from '../../api'
import VideoPlayer from '../../components/VideoPlayer.vue'
import MindMapViewer from '../../components/MindMapViewer.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const demoStore = useDemoStore()

const resources = ref([])
const previewResource = ref(null)
const activeTab = ref('concept')
const audioPlaying = ref(null) // 当前播放的音频资源ID

// 切换音频播放
function toggleAudio(resource) {
  if (audioPlaying.value === resource.id) {
    audioPlaying.value = null
  } else {
    audioPlaying.value = resource.id
  }
}

// 假数据 — 当真实数据为空时展示
const demoConcepts = [
  { title: 'C语言简介与开发环境', desc: 'C语言是一种通用的、过程式的编程语言，由Dennis Ritchie于1972年在贝尔实验室开发。C语言广泛应用于系统软件、嵌入式系统和应用程序开发。本节介绍C语言的历史背景、应用领域以及DevC++开发环境的配置方法。' },
  { title: '变量与数据类型', desc: '变量是程序中用于存储数据的命名内存空间。C语言提供多种基本数据类型：int（整型）、float（单精度浮点）、double（双精度浮点）、char（字符型）。每种类型占用不同大小的内存空间，适用于不同的数据范围。' },
  { title: '输入输出函数', desc: 'printf()用于格式化输出，scanf()用于格式化输入。%d对应整数，%f对应浮点数，%c对应字符，%s对应字符串。使用&运算符获取变量地址传给scanf。' },
]
const demoCodes = [
  { title: 'Hello World 程序', code: '#include <stdio.h>\n\nint main() {\n    printf("Hello, World!\\n");\n    return 0;\n}' },
  { title: '变量定义与运算', code: '#include <stdio.h>\n\nint main() {\n    int a = 10, b = 3;\n    printf("a + b = %d\\n", a + b);\n    printf("a - b = %d\\n", a - b);\n    printf("a * b = %d\\n", a * b);\n    printf("a / b = %d\\n", a / b);\n    return 0;\n}' },
]
const demoPractices = [
  { title: 'C语言中哪个关键字用于定义整型变量？', diff: 'easy', diffLabel: '简单', desc: 'A. float  B. int  C. char  D. double' },
  { title: '以下哪个是C语言的合法标识符？', diff: 'easy', diffLabel: '简单', desc: 'A. 2name  B. _age  C. my-var  D. void' },
  { title: 'sizeof运算符的作用是什么？', diff: 'medium', diffLabel: '中等', desc: 'A. 返回变量值  B. 返回类型占用字节数  C. 计算表达式  D. 定义数组大小' },
]
const demoVideos = [
  { title: '📺 C语言简史与概述', type: 'course-video', content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=1', category: '翁恺C语言' },
  { title: '📺 第一个C程序：Hello World', type: 'course-video', content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=7', category: '翁恺C语言' },
]

const chapterName = computed(() => route.query.name || '未知章节')

// ===== 编程题形式参考：每个章节一道标准格式编程题 =====
// 参照 TodayRecommend 编程实战模块的设计，提供结构化的编程题参考样例
// content 字段遵循 CodeWorkbench 的 parseCodeResourceContent 解析格式
const programReferences = [
  {
    id: 'pr-basic-syntax',
    chapter: '基础语法',
    title: '三位数各位数字之和',
    difficulty: 'easy',
    diffLabel: '简单',
    codePreview: '#include <stdio.h>\n\nint main() {\n    int n, sum = 0;\n    scanf("%d", &n);\n    while (n > 0) {\n        sum += n % 10;\n        n /= 10;\n    }\n    printf("%d\\n", sum);\n    return 0;\n}',
    content: '## 问题描述\n从键盘输入一个三位正整数，求该数各位数字之和并输出。\n\n## 输入格式\n输入一个三位正整数 $n$（$100 \\le n \\le 999$）。\n\n## 输出格式\n输出一个整数，表示各位数字之和。\n\n## 样例输入\n```plaintext\n123\n```\n\n## 样例输出\n```plaintext\n6\n```\n\n## 代码解析\n使用 `%` 取个位数字，用 `/` 去掉已处理的低位。循环条件 `n > 0` 保证处理完所有位数。该解法对任意位数的正整数都适用。\n\n```c\n#include <stdio.h>\n\nint main() {\n    int n, sum = 0;\n    scanf("%d", &n);\n    while (n > 0) {\n        sum += n % 10;\n        n /= 10;\n    }\n    printf("%d\\n", sum);\n    return 0;\n}\n```\n\n## 改进建议\n可增加输入合法性校验，确保 $n$ 为三位正整数。'
  },
  {
    id: 'pr-flow-control',
    chapter: '流程控制',
    title: '闰年判断',
    difficulty: 'easy',
    diffLabel: '简单',
    codePreview: '#include <stdio.h>\n\nint main() {\n    int year;\n    scanf("%d", &year);\n    if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0)\n        printf("Yes\\n");\n    else\n        printf("No\\n");\n    return 0;\n}',
    content: '## 问题描述\n给定一个年份，判断该年是否为闰年。\n\n闰年规则：能被 4 整除但不能被 100 整除，或者能被 400 整除的年份为闰年。\n\n## 输入格式\n输入一个整数 $year$（$1 \\le year \\le 9999$）。\n\n## 输出格式\n是闰年输出 `Yes`，否则输出 `No`。\n\n## 样例输入\n```plaintext\n2024\n```\n\n## 样例输出\n```plaintext\nYes\n```\n\n## 代码解析\n使用逻辑运算符 `&&`（与）和 `||`（或）组合闰年条件。注意运算符优先级：`&&` 高于 `||`，因此可以不加额外括号，但为了可读性建议显式分组。\n\n```c\n#include <stdio.h>\n\nint main() {\n    int year;\n    scanf("%d", &year);\n    if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0)\n        printf("Yes\\n");\n    else\n        printf("No\\n");\n    return 0;\n}\n```\n\n## 改进建议\n可使用三元运算符简化输出：`printf("%s\\n", isLeap ? "Yes" : "No");`'
  },
  {
    id: 'pr-control-structure',
    chapter: '控制结构',
    title: '九九乘法表',
    difficulty: 'easy',
    diffLabel: '简单',
    codePreview: '#include <stdio.h>\n\nint main() {\n    for (int i = 1; i <= 9; i++) {\n        for (int j = 1; j <= i; j++)\n            printf("%d*%d=%-4d", j, i, i * j);\n        printf("\\n");\n    }\n    return 0;\n}',
    content: '## 问题描述\n输出九九乘法表的下三角形式，每行输出对应行数的乘法算式。\n\n## 输入格式\n无输入。\n\n## 输出格式\n输出 9 行，第 $i$ 行输出 $i$ 个乘法算式，格式为 `a*b=c`，每个算式占 4 个字符宽度左对齐。\n\n## 样例输入\n```plaintext\n```\n\n## 样例输出\n```plaintext\n1*1=1   \n1*2=2   2*2=4   \n1*3=3   2*3=6   3*3=9   \n```\n\n## 代码解析\n外层循环控制行号 $i$（1 到 9），内层循环控制列号 $j$（1 到 $i$）。`%-4d` 表示左对齐占 4 位宽度，保证列对齐。\n\n```c\n#include <stdio.h>\n\nint main() {\n    for (int i = 1; i <= 9; i++) {\n        for (int j = 1; j <= i; j++)\n            printf("%d*%d=%-4d", j, i, i * j);\n        printf("\\n");\n    }\n    return 0;\n}\n```\n\n## 改进建议\n可通过参数控制输出上三角或下三角形式，增强程序通用性。'
  },
  {
    id: 'pr-function',
    chapter: '函数',
    title: '递归求阶乘',
    difficulty: 'medium',
    diffLabel: '中等',
    codePreview: '#include <stdio.h>\n\nlong long factorial(int n) {\n    if (n <= 1) return 1;\n    return n * factorial(n - 1);\n}\n\nint main() {\n    int n;\n    scanf("%d", &n);\n    printf("%lld\\n", factorial(n));\n    return 0;\n}',
    content: '## 问题描述\n使用递归函数计算 $n!$（$n$ 的阶乘）。\n\n阶乘定义：$n! = n \\times (n-1)!$，其中 $0! = 1$。\n\n## 输入格式\n输入一个非负整数 $n$（$0 \\le n \\le 20$）。\n\n## 输出格式\n输出 $n!$ 的值。\n\n## 样例输入\n```plaintext\n5\n```\n\n## 样例输出\n```plaintext\n120\n```\n\n## 代码解析\n递归三要素：① 终止条件 `n <= 1` 返回 1；② 递归调用 `factorial(n-1)` 向终止条件靠近；③ 当前层处理 `n * factorial(n-1)`。注意 $n$ 较大时结果会溢出 int，使用 `long long` 存储。\n\n```c\n#include <stdio.h>\n\nlong long factorial(int n) {\n    if (n <= 1) return 1;\n    return n * factorial(n - 1);\n}\n\nint main() {\n    int n;\n    scanf("%d", &n);\n    printf("%lld\\n", factorial(n));\n    return 0;\n}\n```\n\n## 改进建议\n递归在 $n$ 较大时会产生大量函数调用栈开销，可改用循环迭代实现，避免栈溢出。'
  },
  {
    id: 'pr-array',
    chapter: '数组',
    title: '数组求最大值',
    difficulty: 'easy',
    diffLabel: '简单',
    codePreview: '#include <stdio.h>\n\nint main() {\n    int n, arr[100];\n    scanf("%d", &n);\n    for (int i = 0; i < n; i++)\n        scanf("%d", &arr[i]);\n    int max = arr[0];\n    for (int i = 1; i < n; i++)\n        if (arr[i] > max) max = arr[i];\n    printf("%d\\n", max);\n    return 0;\n}',
    content: '## 问题描述\n从键盘输入 $n$ 个整数，找出其中的最大值并输出。\n\n## 输入格式\n第一行输入一个整数 $n$（$1 \\le n \\le 100$）。\n第二行输入 $n$ 个整数，用空格分隔。\n\n## 输出格式\n输出一个整数，表示这 $n$ 个数中的最大值。\n\n## 样例输入\n```plaintext\n5\n3 7 1 9 4\n```\n\n## 样例输出\n```plaintext\n9\n```\n\n## 代码解析\n使用「打擂台」算法：将首个元素设为初始最大值 `max = arr[0]`，然后遍历剩余元素，遇到更大的就更新 `max`。时间复杂度 $O(n)$。\n\n```c\n#include <stdio.h>\n\nint main() {\n    int n, arr[100];\n    scanf("%d", &n);\n    for (int i = 0; i < n; i++)\n        scanf("%d", &arr[i]);\n    int max = arr[0];\n    for (int i = 1; i < n; i++)\n        if (arr[i] > max) max = arr[i];\n    printf("%d\\n", max);\n    return 0;\n}\n```\n\n## 改进建议\n可同时记录最大值下标，或一次遍历同时求最大值和最小值。'
  },
  {
    id: 'pr-array-pointer',
    chapter: '数组与指针',
    title: '指针法逆序数组',
    difficulty: 'medium',
    diffLabel: '中等',
    codePreview: '#include <stdio.h>\n\nint main() {\n    int n, arr[100];\n    scanf("%d", &n);\n    for (int i = 0; i < n; i++) scanf("%d", arr + i);\n    int *p = arr, *q = arr + n - 1;\n    while (p < q) {\n        int t = *p; *p = *q; *q = t;\n        p++; q--;\n    }\n    for (int i = 0; i < n; i++) printf("%d ", arr[i]);\n    return 0;\n}',
    content: '## 问题描述\n使用指针操作将一维数组逆序存放，并输出逆序后的数组。\n\n## 输入格式\n第一行输入整数 $n$（$1 \\le n \\le 100$）。\n第二行输入 $n$ 个整数。\n\n## 输出格式\n输出逆序后的 $n$ 个整数，用空格分隔。\n\n## 样例输入\n```plaintext\n5\n1 2 3 4 5\n```\n\n## 样例输出\n```plaintext\n5 4 3 2 1\n```\n\n## 代码解析\n使用双指针：`p` 指向首元素，`q` 指向末元素。交换 `*p` 与 `*q` 后，`p` 后移、`q` 前移，直到 `p >= q` 相遇。体现了指针算术与数组的关系：`arr + i` 等价于 `&arr[i]`。\n\n```c\n#include <stdio.h>\n\nint main() {\n    int n, arr[100];\n    scanf("%d", &n);\n    for (int i = 0; i < n; i++) scanf("%d", arr + i);\n    int *p = arr, *q = arr + n - 1;\n    while (p < q) {\n        int t = *p; *p = *q; *q = t;\n        p++; q--;\n    }\n    for (int i = 0; i < n; i++) printf("%d ", arr[i]);\n    return 0;\n}\n```\n\n## 改进建议\n可封装为 `void reverse(int *arr, int n)` 函数，提高复用性。注意指针比较仅在同一数组内才有意义。'
  },
  {
    id: 'pr-pointer',
    chapter: '指针',
    title: '指针交换两个变量',
    difficulty: 'easy',
    diffLabel: '简单',
    codePreview: '#include <stdio.h>\n\nvoid swap(int *a, int *b) {\n    int t = *a;\n    *a = *b;\n    *b = t;\n}\n\nint main() {\n    int x, y;\n    scanf("%d %d", &x, &y);\n    swap(&x, &y);\n    printf("%d %d\\n", x, y);\n    return 0;\n}',
    content: '## 问题描述\n使用指针作为函数参数，实现两个整数的交换。\n\n## 输入格式\n输入两个整数 $x$ 和 $y$，用空格分隔。\n\n## 输出格式\n输出交换后的两个整数，用空格分隔。\n\n## 样例输入\n```plaintext\n10 20\n```\n\n## 样例输出\n```plaintext\n20 10\n```\n\n## 代码解析\nC 语言函数参数默认是值传递，无法直接修改实参。通过传递变量地址（指针），在函数内用解引用 `*a` 修改调用者的变量。`swap(&x, &y)` 传入 $x$、$y$ 的地址。\n\n```c\n#include <stdio.h>\n\nvoid swap(int *a, int *b) {\n    int t = *a;\n    *a = *b;\n    *b = t;\n}\n\nint main() {\n    int x, y;\n    scanf("%d %d", &x, &y);\n    swap(&x, &y);\n    printf("%d %d\\n", x, y);\n    return 0;\n}\n```\n\n## 改进建议\n若仅交换整型，可用异或运算 `*a ^= *b; *b ^= *a; *a ^= *b;` 省去临时变量，但可读性较差。'
  },
  {
    id: 'pr-dynamic-memory',
    chapter: '动态内存',
    title: '动态数组求平均值',
    difficulty: 'medium',
    diffLabel: '中等',
    codePreview: '#include <stdio.h>\n#include <stdlib.h>\n\nint main() {\n    int n;\n    scanf("%d", &n);\n    int *arr = malloc(n * sizeof(int));\n    if (!arr) return 1;\n    double sum = 0;\n    for (int i = 0; i < n; i++) {\n        scanf("%d", arr + i);\n        sum += arr[i];\n    }\n    printf("%.2f\\n", sum / n);\n    free(arr);\n    arr = NULL;\n    return 0;\n}',
    content: '## 问题描述\n使用动态内存分配读取 $n$ 个整数，计算并输出它们的平均值（保留两位小数）。\n\n## 输入格式\n第一行输入整数 $n$（$1 \\le n \\le 10000$）。\n第二行输入 $n$ 个整数。\n\n## 输出格式\n输出平均值，保留两位小数。\n\n## 样例输入\n```plaintext\n3\n10 20 30\n```\n\n## 样例输出\n```plaintext\n20.00\n```\n\n## 代码解析\n使用 `malloc` 在堆上动态分配 $n$ 个整型空间，运行时按需分配。使用后必须 `free` 释放，并将指针置 `NULL` 防止野指针。`malloc` 返回值需检查是否为 `NULL`。\n\n```c\n#include <stdio.h>\n#include <stdlib.h>\n\nint main() {\n    int n;\n    scanf("%d", &n);\n    int *arr = malloc(n * sizeof(int));\n    if (!arr) return 1;\n    double sum = 0;\n    for (int i = 0; i < n; i++) {\n        scanf("%d", arr + i);\n        sum += arr[i];\n    }\n    printf("%.2f\\n", sum / n);\n    free(arr);\n    arr = NULL;\n    return 0;\n}\n```\n\n## 改进建议\n可使用 `calloc` 替代 `malloc`，自动将内存初始化为 0。若数组大小后续需要改变，可使用 `realloc`。'
  },
  {
    id: 'pr-struct',
    chapter: '结构体',
    title: '学生成绩排序',
    difficulty: 'medium',
    diffLabel: '中等',
    codePreview: '#include <stdio.h>\n\nstruct Student { char name[20]; int score; };\n\nint main() {\n    int n;\n    scanf("%d", &n);\n    struct Student s[100];\n    for (int i = 0; i < n; i++)\n        scanf("%s %d", s[i].name, &s[i].score);\n    for (int i = 0; i < n - 1; i++)\n        for (int j = 0; j < n - 1 - i; j++)\n            if (s[j].score < s[j+1].score) {\n                struct Student t = s[j]; s[j] = s[j+1]; s[j+1] = t;\n            }\n    for (int i = 0; i < n; i++)\n        printf("%s %d\\n", s[i].name, s[i].score);\n    return 0;\n}',
    content: '## 问题描述\n输入 $n$ 个学生的姓名和成绩，按成绩从高到低排序后输出。\n\n## 输入格式\n第一行输入整数 $n$（$1 \\le n \\le 100$）。\n接下来 $n$ 行，每行输入一个字符串（姓名，不含空格）和一个整数（成绩）。\n\n## 输出格式\n按成绩从高到低输出 $n$ 行，每行格式为 `姓名 成绩`。\n\n## 样例输入\n```plaintext\n3\nAlice 95\nBob 88\nCharlie 92\n```\n\n## 样例输出\n```plaintext\nAlice 95\nCharlie 92\nBob 88\n```\n\n## 代码解析\n定义结构体 `struct Student` 将姓名和成绩组合。使用冒泡排序按 `score` 降序排列，交换时直接交换整个结构体变量（C 语言支持同类型结构体直接赋值）。\n\n```c\n#include <stdio.h>\n\nstruct Student { char name[20]; int score; };\n\nint main() {\n    int n;\n    scanf("%d", &n);\n    struct Student s[100];\n    for (int i = 0; i < n; i++)\n        scanf("%s %d", s[i].name, &s[i].score);\n    for (int i = 0; i < n - 1; i++)\n        for (int j = 0; j < n - 1 - i; j++)\n            if (s[j].score < s[j+1].score) {\n                struct Student t = s[j]; s[j] = s[j+1]; s[j+1] = t;\n            }\n    for (int i = 0; i < n; i++)\n        printf("%s %d\\n", s[i].name, s[i].score);\n    return 0;\n}\n```\n\n## 改进建议\n冒泡排序时间复杂度 $O(n^2)$，数据量大时可改用 `qsort` 配合自定义比较函数，复杂度降为 $O(n \\log n)$。'
  },
  {
    id: 'pr-file',
    chapter: '文件操作',
    title: '统计文件行数',
    difficulty: 'medium',
    diffLabel: '中等',
    codePreview: '#include <stdio.h>\n\nint main() {\n    FILE *fp = fopen("data.txt", "r");\n    if (!fp) { printf("open failed\\n"); return 1; }\n    int count = 0; char buf[256];\n    while (fgets(buf, sizeof(buf), fp)) count++;\n    fclose(fp);\n    printf("%d\\n", count);\n    return 0;\n}',
    content: '## 问题描述\n编写程序读取文件 `data.txt` 的内容，统计文件总行数并输出。\n\n## 输入格式\n程序从当前目录下的 `data.txt` 文件读取，文件内每行为一个字符串（长度不超过 255）。\n\n## 输出格式\n输出一个整数，表示文件的总行数。\n\n## 样例输入\n文件 `data.txt` 内容：\n```plaintext\nHello\nWorld\nC Language\n```\n\n## 样例输出\n```plaintext\n3\n```\n\n## 代码解析\n使用 `fopen("data.txt", "r")` 以只读方式打开文件，必须检查返回值是否为 `NULL`。`fgets` 逐行读取到缓冲区，每读取一行计数器加 1。最后 `fclose` 关闭文件释放资源。\n\n```c\n#include <stdio.h>\n\nint main() {\n    FILE *fp = fopen("data.txt", "r");\n    if (!fp) { printf("open failed\\n"); return 1; }\n    int count = 0; char buf[256];\n    while (fgets(buf, sizeof(buf), fp)) count++;\n    fclose(fp);\n    printf("%d\\n", count);\n    return 0;\n}\n```\n\n## 改进建议\n可增加命令行参数支持任意文件名。注意 `fgets` 会保留行尾的换行符 `\\n`，若需去除可用 `buf[strcspn(buf, "\\n")] = 0;`。'
  },
]

// 当前章节对应的编程题形式参考（支持章节别名匹配）
const chapterProgramReferences = computed(() => {
  const chapter = chapterName.value
  const aliasMap = {
    '流程控制': ['流程控制', '控制结构'],
    '数组与指针': ['数组与指针', '数组', '指针'],
  }
  const matchNames = aliasMap[chapter] || [chapter]
  return programReferences.filter(r => matchNames.includes(r.chapter))
})

// 6个章节的详细概念讲解内容（长页面精美格式）
const chapterDetailContent = {
  '基础语法': {
    title: 'C语言基础语法精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '掌握C语言的基本数据类型、变量声明、运算符使用和输入输出函数，能够编写简单的C程序。' },
      { icon: '📖', title: '1. C语言简介', content: 'C语言由Dennis Ritchie于1972年在贝尔实验室开发，是一种通用的、过程式的编程语言。它广泛应用于系统软件（如操作系统）、嵌入式系统（如单片机）、游戏引擎和高性能计算等领域。\n\nC语言的特点：\n• 语法简洁紧凑，只有32个关键字\n• 运算符丰富（34种运算符）\n• 可直接访问物理地址，能进行位操作\n• 生成目标代码质量高，执行效率高\n• 可移植性好，跨平台能力强' },
      { icon: '📦', title: '2. 基本数据类型', content: 'C语言提供以下基本数据类型：\n\n| 类型 | 关键字 | 字节数 | 范围 |\n|------|--------|--------|------|\n| 整型 | int | 4 | -21亿~21亿 |\n| 短整型 | short | 2 | -32768~32767 |\n| 长整型 | long | 8 | 更大范围 |\n| 单精度浮点 | float | 4 | 6-7位有效数字 |\n| 双精度浮点 | double | 8 | 15-16位有效数字 |\n| 字符型 | char | 1 | 0~127 (ASCII) |\n\n代码示例：\n```c\n#include <stdio.h>\nint main() {\n    int age = 20;\n    float score = 92.5;\n    double pi = 3.14159265358979;\n    char grade = \'A\';\n    printf("年龄：%d，成绩：%.1f\\n", age, score);\n    printf("圆周率：%.14f，等级：%c\\n", pi, grade);\n    return 0;\n}\n```' },
      { icon: '⚠️', title: '3. 变量与常量', content: '变量是程序中用于存储数据的命名内存空间。使用前必须先声明类型。\n\n```c\nint count = 0;      // 整型变量，初始化为0\nfloat price = 9.99; // 浮点变量\nchar ch = \'A\';      // 字符变量\n```\n\n常量用const或#define定义：\n```c\nconst double PI = 3.14159;  // const常量\n#define MAX_SIZE 100         // 宏常量\n```\n\n常见错误：\n• 使用未初始化的变量（值是随机垃圾值）\n• 变量名不能以数字开头\n• 同一作用域内不能重复定义同名变量' },
      { icon: '🔢', title: '4. 运算符', content: 'C语言运算符分类：\n\n**算术运算符**：+（加）、-（减）、*（乘）、/（除）、%（取余）\n注意：整数除法会截断小数部分，7/2=3而不是3.5\n\n**关系运算符**：==（等于）、!=（不等于）、>、<、>=、<=\n\n**逻辑运算符**：&&（与）、||（或）、!（非）\n支持短路求值：如果左边已经能确定结果，右边不再执行\n\n**自增自减**：i++（先用后加）、++i（先加后用）\n```c\nint a = 5;\nint b = a++;  // b=5, a=6\nint c = ++a;  // c=7, a=7\n```' },
      { icon: '💻', title: '5. 输入输出', content: 'printf()用于格式化输出：\n```c\nprintf("整数：%d\\n", 42);\nprintf("浮点：%.2f\\n", 3.14);\nprintf("字符：%c\\n", \'A\');\nprintf("字符串：%s\\n", "Hello");\nprintf("地址：%p\\n", &variable);\n```\n\nscanf()用于格式化输入：\n```c\nint num;\nscanf("%d", &num);  // 注意要加&取地址符\n\nchar str[20];\nscanf("%s", str);   // 字符串不需要&\n```\n\n常见错误：\n• scanf忘记写&符号\n• printf格式符与数据类型不匹配' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| 数据类型与字节大小 | ⭐⭐⭐ | 中 |\n| 变量声明与初始化 | ⭐⭐ | 高 |\n| 运算符优先级 | ⭐⭐ | 高 |\n| printf/scanf格式符 | ⭐⭐ | 高 |\n| const与#define | ⭐⭐⭐ | 中 |\n\n**常见错误解析：**\n\n1. **使用未初始化的变量**：局部变量未赋值时内容是随机垃圾值，可能引发难以排查的逻辑错误。\n```c\nint x;           // x是垃圾值，不是0\nprintf("%d", x); // 输出不可预测\n```\n→ 修复：声明时立即初始化 `int x = 0;`\n\n2. **整数除法截断**：两个整数相除结果仍为整数，小数部分被截断。\n```c\nint a = 7, b = 2;\ndouble c = a / b;  // c = 3.0，不是3.5！\n```\n→ 修复：`double c = (double)a / b;` 或 `c = 1.0 * a / b;`\n\n3. **scanf忘记取地址符&**：\n```c\nint n;\nscanf("%d", n);   // 错误！n的值被当作地址\nscanf("%d", &n);  // 正确\n```\n\n4. **= 与 == 混淆**：\n```c\nif (a = 5) { ... }   // 赋值，永远为真\nif (a == 5) { ... }  // 判断，正确\n```\n\n**针对性提升建议：**\n\n- 每次写声明都顺手初始化，养成"声明即赋值"的习惯\n- 做运算时先确认参与运算的是整数还是浮点数，必要时强制转换\n- 编译时开启 `-Wall` 警告，编译器会提示未初始化变量和 =/== 问题\n- 制作 printf/scanf 格式符速查表贴在桌边，用熟后再撤掉' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **数据类型**：C语言通过类型系统规定数据的存储大小和运算方式，基本类型包括整型（int/short/long）、浮点型（float/double）和字符型（char）\n2. **变量与常量**：变量是命名的内存空间，声明时指定类型；常量用 `const` 或 `#define` 定义，运行期间不可修改\n3. **运算符**：算术运算符完成数学计算，关系运算符比较大小，逻辑运算符组合条件，自增自减运算符简化计数操作\n4. **输入输出**：`printf` 按格式符输出数据，`scanf` 按格式符读取输入，注意格式符与类型必须匹配\n\n**重点难点归纳：**\n\n- **难点1**：运算符优先级——逻辑运算符 `&&` 优先于 `||`，赋值运算符优先级最低，建议用括号明确意图\n- **难点2**：整数除法截断——`7/2=3` 而非 `3.5`，需类型转换后再除\n- **难点3**：scanf的&符号——普通变量必须加&，数组名（字符串）不需要\n- **重点**：格式符对应关系——`%d`(int)、`%f`(float)/`%lf`(double)、`%c`(char)、`%s`(字符串)\n\n**知识体系构建：**\n\n```\nC语言基础语法\n├── 数据表示\n│   ├── 基本类型（int/float/double/char）\n│   ├── 变量声明与初始化\n│   └── 常量定义（const/#define）\n├── 数据运算\n│   ├── 算术运算（+ - * / %）\n│   ├── 关系运算（== != > < >= <=）\n│   ├── 逻辑运算（&& || !）\n│   └── 赋值与自增自减（= += ++ --）\n└── 数据交互\n    ├── 格式化输出（printf）\n    └── 格式化输入（scanf）\n```' },
      { icon: '💡', title: '学习建议', content: '1. 多动手写代码，每个示例都自己敲一遍\n2. 理解内存模型：变量就是内存中的一个格子\n3. 善用printf调试，打印变量值观察变化\n4. 遇到编译错误不要慌，从第一个错误开始修' },
    ]
  },
  '流程控制': {
    title: '流程控制精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '掌握if-else条件判断、switch分支、for/while循环、break/continue控制，能编写有逻辑判断和循环的程序。' },
      { icon: '🔀', title: '1. if-else条件语句', content: '根据条件决定执行哪段代码：\n```c\nint score = 85;\nif (score >= 90) {\n    printf("优秀\\n");\n} else if (score >= 80) {\n    printf("良好\\n");\n} else if (score >= 60) {\n    printf("及格\\n");\n} else {\n    printf("不及格\\n");\n}\n```\n\n注意else的匹配规则：else总是与最近的未匹配if配对（悬空else问题）。\n\n常见错误：\n• if(a = 5) 是赋值，永远为真；应该写 if(a == 5)\n• 多条件判断时忘记加括号' },
      { icon: '🔄', title: '2. switch-case语句', content: '适用于多个固定值的分支判断：\n```c\nint day = 3;\nswitch (day) {\n    case 1: printf("星期一\\n"); break;\n    case 2: printf("星期二\\n"); break;\n    case 3: printf("星期三\\n"); break;\n    case 6: case 7: printf("周末\\n"); break;  // 多个case共享\n    default: printf("无效\\n");\n}\n```\n\n注意：\n• switch只能用于整型和字符型\n• 每个case后必须加break，否则会穿透\n• case后面必须是常量表达式' },
      { icon: '🔁', title: '3. for循环', content: '已知循环次数时使用for循环：\n```c\n// 计算1到100的和\nint sum = 0;\nfor (int i = 1; i <= 100; i++) {\n    sum += i;\n}\nprintf("和为：%d\\n", sum);  // 5050\n\n// 嵌套循环：九九乘法表\nfor (int i = 1; i <= 9; i++) {\n    for (int j = 1; j <= i; j++) {\n        printf("%d×%d=%-4d", j, i, i*j);\n    }\n    printf("\\n");\n}\n```' },
      { icon: '⏳', title: '4. while与do-while', content: 'while循环：先判断再执行\n```c\nint num;\nprintf("请输入1-100的数：");\nscanf("%d", &num);\nwhile (num < 1 || num > 100) {\n    printf("无效，请重新输入：");\n    scanf("%d", &num);\n}\n```\n\ndo-while循环：先执行再判断（至少执行一次）\n```c\nint choice;\ndo {\n    printf("1.开始 2.退出\\n");\n    scanf("%d", &choice);\n} while (choice != 2);\n```' },
      { icon: '⚡', title: '5. break与continue', content: 'break：立即跳出当前循环\ncontinue：跳过本次循环剩余部分\n```c\n// 找第一个能被7整除的两位数\nfor (int i = 10; i <= 99; i++) {\n    if (i % 7 == 0) {\n        printf("找到了：%d\\n", i);\n        break;  // 找到后立即退出\n    }\n}\n\n// 输出1-20中不能被3整除的数\nfor (int i = 1; i <= 20; i++) {\n    if (i % 3 == 0) continue;\n    printf("%d ", i);\n}\n```' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| if-else条件判断 | ⭐⭐⭐ | 中 |\n| switch-case穿透 | ⭐⭐ | 高 |\n| for循环边界 | ⭐⭐ | 高 |\n| while/do-while区别 | ⭐⭐⭐ | 中 |\n| break/continue | ⭐⭐ | 高 |\n| 嵌套循环 | ⭐ | 高 |\n\n**常见错误解析：**\n\n1. **悬空else问题**：else与最近的if配对，而非缩进对应的if。\n```c\nif (a > 0)\n    if (b > 0) printf("A\\n");\nelse\n    printf("B\\n");  // else与内层if配对，不是外层！\n```\n→ 修复：用大括号明确代码块 `if (a > 0) { if (b > 0) ... } else { ... }`\n\n2. **switch缺少break导致穿透**：\n```c\nswitch (day) {\n    case 1: printf("周一\\n");  // 忘记break，会继续执行case 2\n    case 2: printf("周二\\n");\n}\n```\n→ 修复：每个case末尾加 `break;`，除非有意利用穿透\n\n3. **循环边界off-by-one**：\n```c\nfor (int i = 0; i <= 5; i++)  // 执行6次，不是5次\nfor (int i = 0; i < 5; i++)   // 执行5次\n```\n→ 建议：明确"左闭右开"习惯 `[0, n)` 更不容易出错\n\n4. **死循环**：循环条件永远为真。\n```c\nint i = 0;\nwhile (i < 10);  // 分号导致空循环体，i永远不增长！\n```\n\n**针对性提升建议：**\n\n- 写if-else时始终使用大括号，即使只有一行代码\n- switch写完case先写break再填逻辑，防止遗漏\n- 循环先在纸上算清楚边界条件，再写代码\n- 嵌套循环不超过3层，超过时考虑拆分函数' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **条件判断**：`if-else` 实现二选一或多分支逻辑，`switch-case` 适合对固定值的分支判断\n2. **循环结构**：`for` 适合已知循环次数，`while` 适合条件驱动的循环，`do-while` 保证至少执行一次\n3. **流程控制**：`break` 立即跳出当前循环，`continue` 跳过本轮剩余代码进入下一轮\n4. **嵌套结构**：循环和条件可以嵌套组合，内层循环完整执行一轮后外层才前进一步\n\n**重点难点归纳：**\n\n- **难点1**：悬空else——else总是与最近的未匹配if配对，用大括号避免歧义\n- **难点2**：switch穿透——漏写break会顺序执行后续所有case\n- **难点3**：循环边界——区分 `<` 和 `<=`，明确"执行几次"\n- **重点**：for/while/do-while的选用——已知次数用for，条件驱动用while，至少一次用do-while\n\n**知识体系构建：**\n\n```\n流程控制\n├── 分支结构\n│   ├── if-else（条件判断）\n│   ├── else if（多分支）\n│   └── switch-case（固定值匹配）\n├── 循环结构\n│   ├── for（已知次数）\n│   ├── while（条件驱动）\n│   └── do-while（至少执行一次）\n└── 控制语句\n    ├── break（跳出循环）\n    └── continue（跳过本轮）\n```' },
      { icon: '💡', title: '学习建议', content: '1. 先画流程图再写代码，理清逻辑\n2. 注意循环边界条件，防止死循环\n3. 嵌套循环不超过3层，否则可读性差\n4. 用调试器单步执行，观察变量变化' },
    ]
  },
  '数组与指针': {
    title: '数组与指针精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '理解数组和指针的概念，掌握指针运算、指针与数组的关系、动态内存分配，能够使用指针高效操作数据。' },
      { icon: '📦', title: '1. 一维数组', content: '数组是相同类型元素的集合，存储在连续内存空间中：\n```c\nint scores[5] = {88, 92, 75, 96, 81};\n\n// 遍历数组\nfor (int i = 0; i < 5; i++) {\n    printf("scores[%d] = %d\\n", i, scores[i]);\n}\n\n// 数组名等价于首元素指针\nint *p = scores;  // p指向scores[0]\nprintf("第一个元素：%d\\n", *p);      // 88\nprintf("第二个元素：%d\\n", *(p+1));  // 92\n```\n\n重要规则：\n• 数组下标从0开始，不是1\n• 数组名在大多数情况下等价于&arr[0]\n• 数组不能直接赋值，要用memcpy或循环复制' },
      { icon: '🎯', title: '2. 指针基础', content: '指针是存储内存地址的变量：\n```c\nint a = 42;\nint *p = &a;  // p存储a的地址\n\nprintf("a的值：%d\\n", a);       // 42\nprintf("a的地址：%p\\n", (void*)&a);\nprintf("p指向的值：%d\\n", *p);   // 42（解引用）\n\n*p = 100;  // 通过指针修改a的值\nprintf("a的新值：%d\\n", a);     // 100\n```\n\n核心概念：\n• &取地址符：获取变量的内存地址\n• *解引用符：通过地址访问内存中的值\n• 指针本身也占内存（32位系统4字节，64位系统8字节）' },
      { icon: '🔗', title: '3. 指针与函数', content: '指针作为函数参数，实现"引用传递"：\n```c\nvoid swap(int *a, int *b) {\n    int temp = *a;\n    *a = *b;\n    *b = temp;\n}\n\nint main() {\n    int x = 10, y = 20;\n    swap(&x, &y);  // 传递地址\n    printf("x=%d, y=%d\\n", x, y);  // x=20, y=10\n    return 0;\n}\n```\n\n如果没有指针，swap函数无法修改调用者的变量（值传递只影响副本）。' },
      { icon: '💾', title: '4. 动态内存分配', content: '使用malloc/calloc在堆上分配内存：\n```c\n#include <stdlib.h>\n\nint n = 5;\nint *arr = (int*)malloc(n * sizeof(int));\nif (arr == NULL) {\n    printf("内存分配失败\\n");\n    return 1;\n}\n\n// 使用动态数组\nfor (int i = 0; i < n; i++) {\n    arr[i] = (i+1) * 10;\n}\n\nfree(arr);   // 释放内存\narr = NULL;  // 防止野指针\n```\n\n致命错误：\n• 内存泄漏：malloc后忘记free\n• 野指针：free后继续使用指针\n• 重复释放：对同一指针free两次' },
      { icon: '⚠️', title: '5. 常见陷阱', content: '• 数组越界：访问arr[5]但数组只有5个元素（有效下标0-4）\n• 空指针解引用：对NULL指针执行*p操作\n• 指针未初始化：int *p; *p = 10;（p指向随机地址）\n• sizeof陷阱：sizeof(arr)在函数内会退化为指针大小' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| 一维数组遍历 | ⭐⭐⭐ | 中 |\n| 数组与指针等价 | ⭐⭐ | 高 |\n| 指针运算 | ⭐⭐ | 高 |\n| 指针作为函数参数 | ⭐⭐ | 高 |\n| 动态内存分配 | ⭐ | 高 |\n| 多级指针 | ⭐ | 高 |\n\n**常见错误解析：**\n\n1. **数组越界访问**：C语言不检查下标范围，越界访问是未定义行为。\n```c\nint arr[5] = {1,2,3,4,5};\nprintf("%d", arr[5]);  // 越界！有效下标是0-4\n```\n→ 修复：始终确保下标在 `[0, n-1]` 范围内\n\n2. **野指针**：指针未初始化或free后继续使用。\n```c\nint *p;          // 未初始化，p指向随机地址\n*p = 10;         // 未定义行为！可能崩溃\nfree(p); *p = 5; // free后使用，野指针！\n```\n→ 修复：声明时初始化 `int *p = NULL;`，free后置 `p = NULL;`\n\n3. **sizeof对指针和数组的区别**：\n```c\nint arr[10];\nprintf("%lu", sizeof(arr));    // 40（10×4字节）\n\nvoid func(int *p) {\n    printf("%lu", sizeof(p));    // 8（指针大小，不是数组大小！）\n}\n```\n→ 注意：数组传参后退化为指针，sizeof无法获取数组长度\n\n4. **内存泄漏**：malloc后忘记free。\n```c\nvoid leak() {\n    int *p = malloc(100 * sizeof(int));\n    // 使用p...\n    return;  // 忘记free(p)！内存泄漏\n}\n```\n\n**针对性提升建议：**\n\n- 每次使用数组时，先画出内存示意图，标注下标范围\n- malloc后立即写好配对的free，养成"分配即释放"的习惯\n- 画指针指向图，用箭头标明每个指针指向的内存\n- 用valgrind工具检测内存泄漏和越界访问' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **数组**：相同类型元素的连续内存集合，通过下标随机访问，数组名等价于首元素地址\n2. **指针**：存储内存地址的变量，`&`取地址、`*`解引用，指针类型决定偏移步长\n3. **指针与数组关系**：`arr[i]` 等价于 `*(arr+i)`，`&arr[i]` 等价于 `arr+i`\n4. **指针与函数**：传递指针实现"引用传递"，让函数修改调用者的变量\n5. **动态内存**：`malloc`/`calloc` 在堆上分配，`free` 释放，必须配对使用\n\n**重点难点归纳：**\n\n- **难点1**：指针运算——`p+1` 偏移的是一个元素的大小（不是1字节），`int *p` 的 `p+1` 偏移4字节\n- **难点2**：数组传参退化——数组作为函数参数时退化为指针，丢失长度信息\n- **难点3**：内存管理——malloc必须检查NULL，free后置NULL，防止野指针和内存泄漏\n- **重点**：指针解引用前必须确保指针有效（非NULL、非野指针、未越界）\n\n**知识体系构建：**\n\n```\n数组与指针\n├── 数组\n│   ├── 一维数组（定义/遍历/初始化）\n│   ├── 二维数组（矩阵/行优先存储）\n│   └── 字符数组与字符串\n├── 指针\n│   ├── 指针基础（&取地址 / *解引用）\n│   ├── 指针运算（算术/比较）\n│   ├── 指针与函数（值传递 vs 指针传递）\n│   └── 多级指针（指向指针的指针）\n└── 动态内存\n    ├── malloc / calloc / realloc\n    ├── free 与内存释放\n    └── 常见陷阱（泄漏/野指针/越界）\n```' },
      { icon: '💡', title: '学习建议', content: '1. 画内存图理解指针，每个变量标上地址\n2. malloc后立即检查返回值是否为NULL\n3. free后将指针置为NULL\n4. 用valgrind检测内存泄漏' },
    ]
  },
  '函数': {
    title: '函数精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '掌握函数的定义与调用、参数传递机制、递归算法、函数指针和回调函数，能设计模块化的C程序。' },
      { icon: '⚙️', title: '1. 函数定义与调用', content: '函数是代码复用的基本单元：\n```c\n// 函数声明（原型）\nint add(int a, int b);\nvoid printArray(int arr[], int size);\n\n// 函数定义\nint add(int a, int b) {\n    return a + b;\n}\n\nvoid printArray(int arr[], int size) {\n    for (int i = 0; i < size; i++) {\n        printf("%d ", arr[i]);\n    }\n    printf("\\n");\n}\n\nint main() {\n    int result = add(3, 5);  // 调用\n    printf("3+5=%d\\n", result);\n    int nums[] = {1,2,3,4,5};\n    printArray(nums, 5);\n    return 0;\n}\n```\n\n注意：数组作为参数传递时退化为指针，必须额外传递长度。' },
      { icon: '🔁', title: '2. 递归', content: '递归是函数调用自身，必须有终止条件：\n```c\n// 阶乘：n! = n × (n-1)!\nint factorial(int n) {\n    if (n <= 1) return 1;  // 终止条件\n    return n * factorial(n - 1);  // 递归调用\n}\n\n// 斐波那契数列\nint fib(int n) {\n    if (n <= 1) return n;\n    return fib(n-1) + fib(n-2);\n}\n\nint main() {\n    printf("5! = %d\\n", factorial(5));  // 120\n    for (int i = 0; i < 10; i++) {\n        printf("fib(%d)=%d ", i, fib(i));\n    }\n    return 0;\n}\n```\n\n递归三要素：\n1. 明确的终止条件\n2. 递归调用向终止条件靠近\n3. 每次递归后处理当前层的逻辑' },
      { icon: '📌', title: '3. 函数指针与回调', content: '函数指针可以存储函数地址，用于回调和动态调用：\n```c\nint add(int a, int b) { return a + b; }\nint sub(int a, int b) { return a - b; }\n\n// 回调函数\nvoid calculate(int a, int b, int (*op)(int, int)) {\n    printf("结果：%d\\n", op(a, b));\n}\n\nint main() {\n    calculate(10, 5, add);  // 结果：15\n    calculate(10, 5, sub);  // 结果：5\n    return 0;\n}\n```\n\n应用场景：\n• qsort排序时传入比较函数\n• 事件处理回调\n• 链表遍历时对每个节点执行操作' },
      { icon: '⚠️', title: '4. 常见错误', content: '• 函数声明和定义的参数类型/个数不一致\n• 递归缺少终止条件导致栈溢出\n• 返回局部变量的地址（函数结束后内存已释放）\n• 数组参数退化为指针，sizeof结果不对' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| 函数声明与定义 | ⭐⭐⭐ | 中 |\n| 值传递与指针传递 | ⭐⭐ | 高 |\n| 递归思想 | ⭐ | 高 |\n| 作用域规则 | ⭐⭐ | 中 |\n| 函数指针 | ⭐ | 高 |\n| 返回值与内存安全 | ⭐⭐ | 高 |\n\n**常见错误解析：**\n\n1. **返回局部变量地址**：函数结束后局部变量内存被释放，返回其地址是严重错误。\n```c\nint* getPointer() {\n    int x = 42;\n    return &x;  // 错误！x在函数返回后已销毁\n}\n```\n→ 修复：返回动态分配的内存 `int *p = malloc(sizeof(int)); *p = 42; return p;` 或使用static变量\n\n2. **递归缺少终止条件**：\n```c\nint fib(int n) {\n    return fib(n-1) + fib(n-2);  // 无终止条件，无限递归→栈溢出\n}\n```\n→ 修复：`if (n <= 1) return n;`\n\n3. **函数声明与定义不一致**：\n```c\nint add(int a, int b);     // 声明：两个int参数\nint add(int a, int b, int c) { ... }  // 定义：三个参数，编译报错\n```\n\n4. **参数修改无效（值传递误解）**：\n```c\nvoid addTen(int x) { x += 10; }  // 修改的是副本，调用者的变量不变\nint main() { int a = 5; addTen(a); printf("%d", a); }  // 输出5，不是15\n```\n→ 修复：传递指针 `void addTen(int *x) { *x += 10; }` 调用 `addTen(&a);`\n\n**针对性提升建议：**\n\n- 理解值传递的本质：函数参数是实参的副本，修改副本不影响原始值\n- 递归题先写终止条件和递归式，再编码\n- 画函数调用栈图，理解每次调用都有独立的局部变量空间\n- 函数指针先从qsort的compare函数开始练，再逐步扩展' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **函数定义与调用**：函数由返回类型、函数名、参数列表和函数体组成，声明（原型）放在调用前\n2. **参数传递**：C语言只有值传递，传递指针模拟引用传递，让函数修改调用者的数据\n3. **递归**：函数调用自身，必须有终止条件和递归调用，适合分治问题（阶乘、斐波那契、汉诺塔）\n4. **作用域**：局部变量在函数/块内有效，全局变量在整个文件有效，static限制作用域到当前文件\n5. **函数指针**：存储函数地址的变量，用于回调和动态调度\n\n**重点难点归纳：**\n\n- **难点1**：递归理解——每次递归调用创建新的栈帧，递归深度受栈大小限制\n- **难点2**：值传递 vs 指针传递——何时传值、何时传指针，传指针才能修改调用者数据\n- **难点3**：函数指针语法——`int (*fp)(int, int)` 声明、`fp = add` 赋值、`fp(3,5)` 调用\n- **重点**：返回局部变量地址是未定义行为，必须避免\n\n**知识体系构建：**\n\n```\n函数\n├── 基础\n│   ├── 函数声明（原型）\n│   ├── 函数定义（实现）\n│   └── 函数调用\n├── 参数传递\n│   ├── 值传递（复制实参）\n│   └── 指针传递（传递地址）\n├── 递归\n│   ├── 终止条件\n│   ├── 递归调用\n│   └── 经典问题（阶乘/斐波那契/汉诺塔）\n├── 作用域\n│   ├── 局部变量\n│   ├── 全局变量\n│   └── static变量\n└── 进阶\n    ├── 函数指针\n    ├── 回调函数\n    └── 可变参数\n```' },
      { icon: '💡', title: '学习建议', content: '1. 函数职责单一，一个函数只做一件事\n2. 递归画调用栈图，理清每一层的状态\n3. 函数不超过50行，超过就拆分\n4. 善用static局部变量和全局变量的区别' },
    ]
  },
  '结构体': {
    title: '结构体精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '掌握结构体定义与使用、结构体指针、链表实现，能设计复杂数据结构。' },
      { icon: '🏗️', title: '1. 结构体定义', content: '结构体将不同类型的数据组合成一个整体：\n```c\nstruct Student {\n    char name[50];\n    int age;\n    float score;\n};\n\n// 初始化方式\nstruct Student stu1 = {"张三", 20, 92.5};\n\nstruct Student stu2;\nstrcpy(stu2.name, "李四");\nstu2.age = 19;\nstu2.score = 88.0;\n\n// 访问成员\nprintf("姓名：%s，成绩：%.1f\\n", stu1.name, stu1.score);\n```' },
      { icon: '🔗', title: '2. 结构体指针', content: '使用->运算符通过指针访问成员：\n```c\nvoid printStudent(struct Student *s) {\n    printf("姓名：%s，年龄：%d\\n", s->name, s->age);\n}\n\nvoid birthday(struct Student *s) {\n    s->age++;  // 通过指针修改\n}\n\nint main() {\n    struct Student stu = {"王五", 20, 90.0};\n    printStudent(&stu);   // 传地址\n    birthday(&stu);       // 年龄+1\n    printf("过完生日：%d岁\\n", stu.age);  // 21\n    return 0;\n}\n```\n\n• . 运算符：用于结构体变量 (stu.name)\n• -> 运算符：用于结构体指针 (p->name)' },
      { icon: '🔗', title: '3. 链表', content: '链表是用指针连接的动态数据结构：\n```c\nstruct Node {\n    int data;\n    struct Node *next;\n};\n\n// 创建节点\nstruct Node* createNode(int data) {\n    struct Node *node = malloc(sizeof(struct Node));\n    node->data = data;\n    node->next = NULL;\n    return node;\n}\n\n// 尾插法\nvoid append(struct Node **head, int data) {\n    struct Node *newNode = createNode(data);\n    if (*head == NULL) { *head = newNode; return; }\n    struct Node *cur = *head;\n    while (cur->next) cur = cur->next;\n    cur->next = newNode;\n}\n\n// 遍历\nvoid printList(struct Node *head) {\n    while (head) {\n        printf("%d -> ", head->data);\n        head = head->next;\n    }\n    printf("NULL\\n");\n}\n```' },
      { icon: '⚠️', title: '4. 常见错误', content: '• 结构体传参时按值传递会复制整个结构体，大结构体效率低\n• 链表遍历时忘记检查NULL\n• 链表节点malloc后忘记free\n• 删除链表节点时忘记处理前驱节点的next指针' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| 结构体定义与初始化 | ⭐⭐⭐ | 中 |\n| 结构体指针与->运算 | ⭐⭐ | 高 |\n| 结构体作为函数参数 | ⭐⭐ | 高 |\n| 链表基本操作 | ⭐ | 高 |\n| typedef使用 | ⭐⭐ | 中 |\n| 内存对齐 | ⭐ | 中 |\n\n**常见错误解析：**\n\n1. **结构体传值拷贝开销大**：按值传递会复制整个结构体。\n```c\nvoid printStudent(struct Student s) {  // 复制整个结构体，效率低\n    printf("%s\\n", s.name);\n}\n```\n→ 修复：传指针 `void printStudent(const struct Student *s) { printf("%s\\n", s->name); }`\n\n2. **链表操作忘记处理NULL**：\n```c\nvoid printList(struct Node *head) {\n    while (head->next) {  // 若head为NULL，直接崩溃！\n        printf("%d ", head->data);\n        head = head->next;\n    }\n}\n```\n→ 修复：`while (head) { ... head = head->next; }`\n\n3. **删除链表节点时断链**：删除节点后忘记将前驱节点的next指向后继节点。\n```c\n// 错误：直接free，链断了\nfree(cur);\n// 正确：先链接，再释放\nprev->next = cur->next;\nfree(cur);\n```\n\n4. **. 和 -> 运算符混淆**：\n```c\nstruct Student s = {"张三", 20};\nstruct Student *p = &s;\ns.age;    // 用.访问变量\np->age;   // 用->访问指针\np.age;    // 错误！p是指针，不能用.\n```\n\n**针对性提升建议：**\n\n- 结构体传参一律用指针，大结构体用const指针保护\n- 链表操作前先画图，标注每个节点的地址和指针指向\n- 删除节点时记住三步：保存后继 → 修改前驱 → 释放节点\n- 练习typedef简化结构体类型名，提升代码可读性' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **结构体定义**：用 `struct` 将不同类型的数据组合成一个逻辑整体，通过 `.` 访问成员\n2. **结构体指针**：用 `->` 运算符通过指针访问成员，`p->age` 等价于 `(*p).age`\n3. **链表**：通过指针将节点串联的动态数据结构，支持高效的插入和删除操作\n4. **typedef**：为结构体类型创建别名，简化声明\n\n**重点难点归纳：**\n\n- **难点1**：链表操作——插入/删除需要正确修改多个指针，容易断链\n- **难点2**：. 与 -> 区分——变量用.，指针用->，`p->age` 等价于 `(*p).age`\n- **难点3**：结构体传参——大结构体传值拷贝开销大，应传指针\n- **重点**：链表遍历必须检查NULL，删除节点必须处理前驱和后继\n\n**知识体系构建：**\n\n```\n结构体\n├── 基础\n│   ├── struct定义\n│   ├── 成员访问（.运算符）\n│   ├── 初始化与赋值\n│   └── typedef别名\n├── 结构体指针\n│   ├── ->运算符\n│   ├── 结构体传参（指针 vs 值）\n│   └── 指向结构体的指针数组\n├── 链表\n│   ├── 节点定义\n│   ├── 创建与遍历\n│   ├── 插入与删除\n│   └── 常见变体（双向/循环）\n└── 其他\n    ├── 共用体（union）\n    ├── 枚举（enum）\n    └── 位域\n```' },
      { icon: '💡', title: '学习建议', content: '1. 链表一定要画图，标明每个节点的地址\n2. 指针操作前先检查是否为NULL\n3. 链表vs数组：链表增删快，数组查询快\n4. 用typedef简化结构体类型名' },
    ]
  },
  '文件操作': {
    title: '文件操作精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '掌握文件的打开关闭、文本读写、二进制读写、文件定位，能实现数据的持久化存储。' },
      { icon: '📁', title: '1. 文件操作基础', content: 'C语言通过FILE指针进行文件操作：\n```c\n#include <stdio.h>\n\nint main() {\n    // 写入文件\n    FILE *fp = fopen("test.txt", "w");\n    if (fp == NULL) {\n        printf("无法打开文件\\n");\n        return 1;\n    }\n    fprintf(fp, "Hello, File!\\n");\n    fprintf(fp, "数字：%d\\n", 42);\n    fclose(fp);\n\n    // 读取文件\n    fp = fopen("test.txt", "r");\n    char buffer[100];\n    while (fgets(buffer, sizeof(buffer), fp)) {\n        printf("%s", buffer);\n    }\n    fclose(fp);\n    return 0;\n}\n```\n\n文件打开模式：\n• "r" 只读  • "w" 写入（覆盖）  • "a" 追加\n• "rb" 二进制读  • "wb" 二进制写' },
      { icon: '💾', title: '2. 二进制文件', content: '使用fread/fwrite进行二进制读写：\n```c\nstruct Record {\n    int id;\n    char name[50];\n    float score;\n};\n\n// 写入二进制文件\nstruct Record records[3] = {\n    {1, "Alice", 95.5},\n    {2, "Bob", 88.0},\n    {3, "Charlie", 92.3}\n};\nFILE *fp = fopen("records.dat", "wb");\nfwrite(records, sizeof(struct Record), 3, fp);\nfclose(fp);\n\n// 读取二进制文件\nstruct Record readRecords[3];\nfp = fopen("records.dat", "rb");\nfread(readRecords, sizeof(struct Record), 3, fp);\nfclose(fp);\n```' },
      { icon: '📍', title: '3. 文件定位', content: 'fseek和ftell用于随机访问文件：\n```c\nfseek(fp, offset, origin);  // 移动文件指针\nlong pos = ftell(fp);        // 获取当前位置\nrewind(fp);                  // 回到文件开头\n```\n\norigin取值：\n• SEEK_SET (0) 文件开头\n• SEEK_CUR (1) 当前位置\n• SEEK_END (2) 文件末尾\n\n示例：读取文件第3条记录\n```c\nfseek(fp, 2 * sizeof(struct Record), SEEK_SET);\nfread(&record, sizeof(struct Record), 1, fp);\n```' },
      { icon: '⚠️', title: '4. 常见错误', content: '• fopen后忘记fclose（文件句柄泄漏）\n• 以"w"模式打开已存在文件会清空内容\n• 二进制读写时fwrite/fread的size参数写错\n• 文件指针使用前未检查是否为NULL' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| fopen/fclose | ⭐⭐⭐ | 中 |\n| 文本读写（fprintf/fgets） | ⭐⭐ | 中 |\n| 二进制读写（fread/fwrite） | ⭐⭐ | 高 |\n| 文件定位（fseek/ftell） | ⭐ | 高 |\n| 打开模式选择 | ⭐⭐ | 高 |\n| 错误处理 | ⭐ | 高 |\n\n**常见错误解析：**\n\n1. **fopen后忘记检查NULL**：文件可能不存在或无权限。\n```c\nFILE *fp = fopen("data.txt", "r");\nfgets(buf, 100, fp);  // fp可能为NULL，直接崩溃！\n```\n→ 修复：`if (fp == NULL) { perror("打开失败"); return 1; }`\n\n2. **"w"模式覆盖已有文件**：\n```c\nFILE *fp = fopen("data.txt", "w");  // 文件内容被清空！\n```\n→ 注意：需追加用"a"模式，需保留原内容用"r+"模式\n\n3. **fread/fwrite的size参数错误**：\n```c\nfwrite(&record, sizeof(char), 1, fp);  // 只写了1字节，不是整个结构体\nfwrite(&record, sizeof(struct Record), 1, fp);  // 正确\n```\n\n4. **忘记fclose导致文件句柄泄漏**：\n```c\nFILE *fp = fopen("data.txt", "w");\nfprintf(fp, "Hello");\n// 忘记fclose(fp)，数据可能未写入磁盘\n```\n→ 注意：fclose会刷新缓冲区，不关闭可能导致数据丢失\n\n**针对性提升建议：**\n\n- fopen后立即写if检查NULL，养成固定模板\n- 文本文件用fprintf/fgets，二进制用fread/fwrite，不要混用\n- 读写前确认打开模式：只读r、写入w、追加a、读写r+\n- 大文件分块读写，用feof或返回值判断结束' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **文件打开与关闭**：`fopen` 打开文件返回FILE指针，`fclose` 关闭文件释放资源并刷新缓冲区\n2. **文本读写**：`fprintf`/`fscanf` 格式化读写，`fgets`/`fputs` 行级读写\n3. **二进制读写**：`fread`/`fwrite` 按字节块读写，适合结构体等复杂数据\n4. **文件定位**：`fseek` 移动文件指针，`ftell` 获取当前位置，`rewind` 回到开头\n5. **打开模式**：r(只读)、w(覆盖写)、a(追加)、rb/wb(二进制)、r+(读写)\n\n**重点难点归纳：**\n\n- **难点1**：二进制读写——fread/fwrite的参数顺序：buffer、元素大小、元素个数、文件指针\n- **难点2**：文件定位——fseek的origin参数：SEEK_SET(开头)、SEEK_CUR(当前)、SEEK_END(末尾)\n- **难点3**：打开模式选择——"w"会清空已有文件，"a"追加在末尾，"r+"可读可写但不创建\n- **重点**：fopen必须检查返回值，fclose必须调用\n\n**知识体系构建：**\n\n```\n文件操作\n├── 基础\n│   ├── fopen（打开）\n│   ├── fclose（关闭）\n│   └── 打开模式（r/w/a/rb/wb/r+）\n├── 文本读写\n│   ├── fprintf / fscanf（格式化）\n│   ├── fgets / fputs（行级）\n│   └── fgetc / fputc（字符级）\n├── 二进制读写\n│   ├── fread（读取块）\n│   └── fwrite（写入块）\n└── 文件定位\n    ├── fseek（移动指针）\n    ├── ftell（获取位置）\n    └── rewind（回到开头）\n```' },
      { icon: '💡', title: '学习建议', content: '1. 文本文件用fprintf/fgets，二进制用fread/fwrite\n2. fopen后立即检查返回值\n3. 用完文件及时fclose\n4. 大文件考虑分块读写，不要一次性加载' },
    ]
  },
  // 以下为 demo 模式的章节名称（与后端主分类名保持一致）
  '指针': {
    title: '指针精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '理解指针的概念，掌握指针运算、指针与数组的关系。' },
      { icon: '🔗', title: '1. 指针是什么', content: '指针是存储内存地址的变量。\n```c\nint a = 42;\nint *p = &a;  // p存储a的地址\nprintf("%d\\n", *p);  // 42（解引用）\n*p = 100;  // 通过指针修改a\n```\n• `&` 取地址符  • `*` 解引用符' },
      { icon: '📦', title: '2. 指针与数组', content: '```c\nint arr[5] = {10, 20, 30, 40, 50};\nint *p = arr;\nprintf("%d\\n", *(p+1));  // 20\n```' },
      { icon: '⚙️', title: '3. 指针与函数', content: '```c\nvoid swap(int *a, int *b) {\n    int t = *a; *a = *b; *b = t;\n}\n```' },
      { icon: '💾', title: '4. 动态内存', content: '```c\nint *p = malloc(n * sizeof(int));\nfree(p); p = NULL;\n```' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| &和*运算符 | ⭐⭐ | 高 |\n| 指针与数组关系 | ⭐⭐ | 高 |\n| 指针作为函数参数 | ⭐⭐ | 高 |\n| 动态内存管理 | ⭐ | 高 |\n\n**常见错误解析：**\n\n1. **野指针**：未初始化或free后继续使用的指针\n```c\nint *p; *p = 10;  // p指向随机地址，未定义行为\nfree(p); *p = 5;  // free后使用，野指针\n```\n→ 修复：声明时初始化为NULL，free后立即置NULL\n\n2. **指针运算误解**：`p+1`偏移的是一个元素大小，不是1字节\n```c\nint *p = arr;\nprintf("%d", *(p+1));  // 偏移4字节(int大小)，指向arr[1]\n```\n\n**针对性提升建议：**\n- 画内存图标注地址和指针指向，每个指针画箭头\n- malloc/free成对出现，分配后立即写好释放代码\n- 解引用前确认指针非NULL' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **指针本质**：存储内存地址的变量，`&`取地址、`*`解引用\n2. **指针与数组**：`arr[i]` 等价于 `*(arr+i)`，数组名是首元素地址\n3. **指针与函数**：传指针实现引用传递，修改调用者数据\n4. **动态内存**：`malloc`分配、`free`释放，必须配对\n\n**重点难点归纳：**\n- 指针类型决定偏移步长：`int *p; p+1`偏移4字节\n- 野指针和内存泄漏是指针最常见陷阱\n- 数组传参退化为指针，丢失长度信息\n\n**知识体系构建：**\n\n```\n指针\n├── 基础（&取地址 / *解引用）\n├── 指针与数组（arr[i] = *(arr+i)）\n├── 指针与函数（引用传递）\n└── 动态内存（malloc/free）\n```' },
      { icon: '💡', title: '学习建议', content: '1. 画内存图  2. malloc后检查NULL  3. free后置NULL' },
    ]
  },
  '数组': {
    title: '数组精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '掌握一维数组和二维数组的定义、初始化、遍历。' },
      { icon: '📦', title: '1. 一维数组', content: '```c\nint scores[5] = {88, 92, 75, 96, 81};\nfor (int i = 0; i < 5; i++) printf("%d\\n", scores[i]);\n```\n下标从0开始，数组名等价于&arr[0]。' },
      { icon: '📊', title: '2. 二维数组', content: '```c\nint m[3][4] = {{1,2,3,4},{5,6,7,8},{9,10,11,12}};\n```' },
      { icon: '🔗', title: '3. 字符数组', content: '```c\nchar s[20] = "Hello";\nstrlen(s); strcpy(s, "Hi"); strcmp(s, "Hi");\n```\n注意 \'\\0\' 结尾，用strcmp比较。' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| 一维数组遍历 | ⭐⭐⭐ | 中 |\n| 二维数组理解 | ⭐⭐ | 高 |\n| 字符数组与字符串 | ⭐⭐ | 高 |\n| 数组越界 | ⭐⭐ | 高 |\n\n**常见错误解析：**\n\n1. **数组越界**：C语言不检查下标，`arr[5]`在大小为5的数组上是越界\n→ 修复：下标范围 `[0, n-1]`\n\n2. **字符串比较用==**：\n```c\nchar s1[] = "Hello"; char s2[] = "Hello";\nif (s1 == s2) ...  // 比较地址，永远不等！\nif (strcmp(s1, s2) == 0) ...  // 正确\n```\n\n3. **字符数组忘记\\0**：`char s[5] = "Hello"` 越界！需要6个位置存5个字符+\\0\n\n**针对性提升建议：**\n- 使用数组时先确认有效下标范围\n- 字符串操作一律用str系列函数\n- 字符数组声明时多留1个位置给\\0' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **一维数组**：相同类型元素的连续内存集合，下标从0开始\n2. **二维数组**：行优先存储的矩阵，`m[i][j]`访问第i行第j列\n3. **字符数组**：以\\0结尾的字符串，用str系列函数操作\n\n**重点难点归纳：**\n- 数组下标从0开始，越界访问是未定义行为\n- 字符串必须以\\0结尾，比较用strcmp而非==\n- 数组名等价于首元素地址\n\n**知识体系构建：**\n\n```\n数组\n├── 一维数组（定义/遍历/初始化）\n├── 二维数组（矩阵/行优先）\n└── 字符数组（字符串/str函数）\n```' },
      { icon: '💡', title: '学习建议', content: '1. 下标从0开始  2. 注意数组越界  3. 字符串用str系列函数' },
    ]
  },
  '控制结构': {
    title: '控制结构精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '掌握if-else、switch、for、while、break、continue。' },
      { icon: '🔀', title: '1. if-else', content: '```c\nif (score >= 90) printf("优秀\\n");\nelse if (score >= 60) printf("及格\\n");\nelse printf("不及格\\n");\n```' },
      { icon: '🔄', title: '2. switch', content: '```c\nswitch (day) {\n    case 1: printf("周一\\n"); break;\n    default: printf("其他\\n");\n}\n```' },
      { icon: '🔁', title: '3. 循环', content: '```c\nfor (int i=0; i<100; i++) sum+=i;\nwhile (cond) { ... }\ndo { ... } while (cond);\n```' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| if-else条件判断 | ⭐⭐⭐ | 中 |\n| switch穿透 | ⭐⭐ | 高 |\n| 循环边界 | ⭐⭐ | 高 |\n| break/continue | ⭐⭐ | 高 |\n\n**常见错误解析：**\n\n1. **switch缺少break**：漏写break会穿透执行后续case\n→ 修复：每个case末尾加break\n\n2. **循环边界off-by-one**：`i <= n`比`i < n`多执行一次\n→ 建议：统一用"左闭右开"`[0, n)`习惯\n\n3. **= 与 == 混淆**：`if (a = 5)`是赋值永远为真\n→ 修复：写成 `if (5 == a)` 常量在左，编译器会警告\n\n**针对性提升建议：**\n- if-else始终用大括号，即使只有一行代码\n- switch写完case先写break再填逻辑\n- 循环先在纸上确定边界再编码' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **分支**：if-else实现条件判断，switch匹配固定值\n2. **循环**：for(已知次数)、while(条件驱动)、do-while(至少一次)\n3. **控制**：break跳出循环，continue跳过本轮\n\n**重点难点归纳：**\n- switch穿透：漏写break会顺序执行后续所有case\n- 循环边界：区分<和<=，明确执行次数\n- 死循环：while后误加分号或循环变量未更新\n\n**知识体系构建：**\n\n```\n控制结构\n├── 分支（if-else / switch）\n├── 循环（for / while / do-while）\n└── 控制（break / continue）\n```' },
      { icon: '💡', title: '学习建议', content: '1. 先画流程图  2. 注意边界条件  3. 防止死循环' },
    ]
  },
  '动态内存': {
    title: '动态内存管理精讲',
    sections: [
      { icon: '🎯', title: '本章目标', content: '掌握malloc、calloc、realloc、free的使用。' },
      { icon: '💾', title: '1. malloc与free', content: '```c\nint *p = malloc(n * sizeof(int));\nif (p == NULL) { /* 错误处理 */ }\n// 使用...\nfree(p); p = NULL;\n```' },
      { icon: '⚠️', title: '2. 常见错误', content: '内存泄漏、野指针、重复释放、越界访问。' },
      { icon: '🎯', title: '薄弱点分析', content: '**知识点掌握情况评估：**\n\n| 知识点 | 掌握程度 | 易错等级 |\n|--------|----------|----------|\n| malloc/free基本使用 | ⭐⭐ | 高 |\n| calloc与malloc区别 | ⭐⭐⭐ | 中 |\n| realloc扩容 | ⭐ | 高 |\n| 内存泄漏检测 | ⭐ | 高 |\n\n**常见错误解析：**\n\n1. **内存泄漏**：malloc后忘记free，程序运行越久占用内存越多\n```c\nvoid leak() {\n    int *p = malloc(100 * sizeof(int));\n    return;  // 忘记free(p)！\n}\n```\n→ 修复：每个malloc配对一个free，分配后立即写好释放\n\n2. **野指针**：free后继续使用指针\n```c\nfree(p);\np[0] = 10;  // p已释放，野指针！\n```\n→ 修复：`free(p); p = NULL;`\n\n3. **重复释放**：对同一指针free两次\n```c\nfree(p);\nfree(p);  // 重复释放，未定义行为！\n```\n→ 修复：free后立即置NULL，free(NULL)是安全的\n\n4. **malloc返回值未检查**：内存不足时返回NULL\n```c\nint *p = malloc(n * sizeof(int));\np[0] = 1;  // p可能为NULL，解引用崩溃！\n```\n\n**针对性提升建议：**\n- 分配后立即写好配对的free，形成习惯\n- free后立即将指针置NULL，防止野指针和重复释放\n- 用valgrind工具检测内存泄漏\n- calloc适合需要初始化为0的场景，malloc不初始化' },
      { icon: '📊', title: '学习总结', content: '**核心概念梳理：**\n\n1. **malloc**：在堆上分配指定字节数的内存，不初始化内容\n2. **calloc**：分配并初始化为0，参数为（元素个数，元素大小）\n3. **realloc**：调整已分配内存的大小，可能移动到新地址\n4. **free**：释放堆内存，必须与malloc/calloc/realloc配对\n\n**重点难点归纳：**\n- malloc不初始化，calloc初始化为0\n- realloc可能返回新地址，必须用返回值更新指针\n- 内存四大陷阱：泄漏、野指针、重复释放、越界访问\n- free(NULL)安全，但free后不置NULL则危险\n\n**知识体系构建：**\n\n```\n动态内存\n├── 分配\n│   ├── malloc（不初始化）\n│   ├── calloc（初始化为0）\n│   └── realloc（调整大小）\n├── 释放\n│   └── free（释放后置NULL）\n└── 陷阱\n    ├── 内存泄漏\n    ├── 野指针\n    ├── 重复释放\n    └── 越界访问\n```' },
      { icon: '💡', title: '学习建议', content: '1. malloc后检查NULL  2. free后置NULL  3. 用valgrind检测' },
    ]
  },
}

const chapterDetail = computed(() => chapterDetailContent[chapterName.value] || null)

// 从 chapterDetailContent 中提取"薄弱点分析"和"学习总结"section，作为后端资源的兜底
const chapterWeaknessSection = computed(() => {
  const detail = chapterDetailContent[chapterName.value]
  if (!detail) return null
  return detail.sections.find(s => s.title === '薄弱点分析') || null
})
const chapterSummarySection = computed(() => {
  const detail = chapterDetailContent[chapterName.value]
  if (!detail) return null
  return detail.sections.find(s => s.title === '学习总结') || null
})

// 主分类映射（与学习中心6个章节一致）
// 使用精确匹配，避免"函数指针"被错误匹配到"指针"章节
const mainCategoryMap = {
  '指针': ['指针', '指针变量', '指针运算', '取地址', '解引用', '多级指针', 'void指针', 'const指针'],
  '数组': ['数组', '一维数组', '二维数组', '数组初始化', '数组遍历', '数组作为参数'],
  '函数': ['函数', '递归', '参数传递', '返回值', '作用域', '函数指针', '回调', '局部变量', '全局变量'],
  '结构体': ['结构体', 'struct', '链表', '共用体', '枚举', '联合体', '位域'],
  '控制结构': ['if', 'else', 'switch', 'case', 'for', 'while', 'do', 'break', 'continue', '循环', '判断', '控制结构', '条件语句', '循环语句'],
  '动态内存': ['动态内存', 'malloc', 'calloc', 'realloc', 'free', '内存泄漏', '野指针', '内存管理', '堆', '栈'],
}

const chapterResources = computed(() => {
  const chapter = chapterName.value

  // 获取该主分类下的所有关键词
  const keywords = mainCategoryMap[chapter] || [chapter]

  return resources.value.filter(r => {
    const kp = r.knowledgePoint || ''
    const ch = r.chapter || ''
    // 精确匹配：资源的chapter字段必须等于当前章节名
    if (ch === chapter) return true
    // 或者knowledgePoint完全匹配某个关键词
    return keywords.some(kw => kp === kw)
  })
})

const conceptResources = computed(() => chapterResources.value.filter(r => ['article', 'explanation'].includes(r.type)))
const codeResources = computed(() => chapterResources.value.filter(r => r.type === 'code'))
const practiceResources = computed(() => chapterResources.value.filter(r => r.type === 'exercise'))
const learningResources = computed(() => chapterResources.value.filter(r => ['course-video', 'mindmap'].includes(r.type)))
const summaryResources = computed(() => chapterResources.value.filter(r => r.type === 'summary'))
const weaknessResources = computed(() => chapterResources.value.filter(r => r.type === 'weakness'))
const blogResources = computed(() => chapterResources.value.filter(r => r.type === 'blog'))
const practiceCount = computed(() => practiceResources.value.length || demoPractices.length)

const tabs = [
  { id: 'concept', label: '概念讲解', icon: '📖' },
  { id: 'code', label: '代码示例', icon: '💻' },
  { id: 'practice', label: '练习题目', icon: '📝' },
  { id: 'resource', label: '学习资源', icon: '🎬' },
  { id: 'summary', label: '学习总结', icon: '📊' },
  { id: 'analysis', label: '薄弱分析', icon: '🎯' },
  { id: 'blog', label: '听学博客', icon: '🎧' },
]

function tabCount(id) {
  const map = { concept: conceptResources, code: codeResources, practice: practiceResources, resource: learningResources, summary: summaryResources, analysis: weaknessResources, blog: blogResources }
  const count = map[id]?.value?.length || 0
  // 兜底：后端无数据时，如果有 chapterDetailContent 中的对应 section，显示计数1
  if (count === 0) {
    if (id === 'summary' && chapterSummarySection.value) return 1
    if (id === 'analysis' && chapterWeaknessSection.value) return 1
  }
  return count
}

const typeSummary = computed(() => {
  const parts = []
  if (conceptResources.value.length) parts.push(`${conceptResources.value.length}篇概念讲解`)
  if (codeResources.value.length) parts.push(`${codeResources.value.length}个代码示例`)
  if (practiceResources.value.length) parts.push(`${practiceResources.value.length}道练习题`)
  if (learningResources.value.length) parts.push(`${learningResources.value.length}个学习资源`)
  return parts.join('、') || '暂无资源'
})

const diffLabel = computed(() => {
  const counts = {}
  chapterResources.value.forEach(r => { if (r.difficulty) counts[r.difficulty] = (counts[r.difficulty] || 0) + 1 })
  const top = Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0]
  const m = { easy: '初级', medium: '中级', hard: '高级' }
  return m[top] || '综合'
})

const chapterEmoji = computed(() => {
  const map = {
    '指针': '🎯', '数组': '📦', '函数': '⚙️', '结构体': '🏗️',
    '内存管理': '🧠', '动态内存': '💾', '文件操作': '📁', '控制结构': '🔀',
    '循环': '🔄', '递归': '🔁', '预处理': '📋', '链表': '🔗',
    '函数指针': '📌', 'C语言概述': '📖', '指针与数组': '🎯📦',
  }
  return map[chapterName.value] || '📚'
})

const chapterGradient = computed(() => {
  const gradients = [
    'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  ]
  const hash = chapterName.value.split('').reduce((a, c) => a + c.charCodeAt(0), 0)
  return gradients[hash % gradients.length]
})

function goBack() { router.push('/student/learning-center') }
function openPreview(r) {
  previewResource.value = r
}
function goToPractice() { router.push({ path: '/student/practice/' + chapterName.value }) }

// 简单 C 语法高亮：为每一行拆分为带样式的片段
// 对齐今日推荐 cover-vscode 的 vs-* 类名
function highlightLines(code) {
  if (!code) return []
  const keywords = ['return', 'if', 'else', 'while', 'for', 'do', 'switch', 'case', 'break', 'continue', 'int', 'char', 'float', 'double', 'void', 'long', 'short', 'struct', 'typedef', 'sizeof', 'static', 'const', 'unsigned', 'signed', 'include', 'define', 'malloc', 'free', 'NULL']
  const types = ['int', 'char', 'float', 'double', 'void', 'long', 'short', 'struct', 'FILE', 'size_t']
  const functions = ['printf', 'scanf', 'malloc', 'free', 'fopen', 'fclose', 'fgets', 'fread', 'fwrite', 'fprintf', 'sprintf', 'strcpy', 'strlen', 'strcmp', 'memset', 'memcpy', 'sqrt', 'pow', 'abs', 'qsort', 'exit']
  return code.split('\n').map(line => {
    const segs = []
    let i = 0
    let cur = ''
    const flush = (cls = '') => { if (cur.length) { segs.push({ cls, text: cur }); cur = '' } }
    while (i < line.length) {
      const ch = line[i]
      // #include <xxx.h>
      if (ch === '#' && line.slice(i, i + 8) === '#include') {
        flush()
        const rest = line.slice(i)
        const m = rest.match(/^(#include)(\s+)(<[^>]+>|"[^"]+")/)
        if (m) {
          segs.push({ cls: 'vs-pp', text: m[1] })
          segs.push({ cls: '', text: m[2] })
          segs.push({ cls: 'vs-inc', text: m[3] })
          i += m[0].length
          continue
        }
      }
      // 字符串
      if (ch === '"') {
        flush()
        let j = i + 1; cur += ch
        while (j < line.length && line[j] !== '"') { if (line[j] === '\\') { cur += line[j] + (line[j+1]||''); j+=2 } else { cur += line[j]; j++ } }
        if (j < line.length) cur += line[j++]
        segs.push({ cls: 'vs-str', text: cur }); cur = ''; i = j; continue
      }
      // 字符常量
      if (ch === "'") {
        flush()
        let j = i + 1; cur += ch
        while (j < line.length && line[j] !== "'") { if (line[j] === '\\') { cur += line[j] + (line[j+1]||''); j+=2 } else { cur += line[j]; j++ } }
        if (j < line.length) cur += line[j++]
        segs.push({ cls: 'vs-str', text: cur }); cur = ''; i = j; continue
      }
      // 行注释
      if (ch === '/' && line[i+1] === '/') {
        flush()
        segs.push({ cls: 'vs-cm', text: line.slice(i) }); cur = ''; i = line.length; continue
      }
      // 数字
      if (/[0-9]/.test(ch)) {
        flush()
        while (i < line.length && /[0-9.]/.test(line[i])) { cur += line[i]; i++ }
        segs.push({ cls: 'vs-num', text: cur }); cur = ''; continue
      }
      // 标识符
      if (/[A-Za-z_]/.test(ch)) {
        flush()
        while (i < line.length && /[A-Za-z0-9_]/.test(line[i])) { cur += line[i]; i++ }
        let cls = 'vs-op'
        if (keywords.includes(cur)) cls = 'vs-kw'
        if (types.includes(cur)) cls = 'vs-ty'
        if (functions.includes(cur)) cls = 'vs-fn'
        // 函数调用：下一个非空字符是 (
        let k = i; while (k < line.length && line[k] === ' ') k++
        if (line[k] === '(' && !types.includes(cur) && !keywords.includes(cur)) cls = 'vs-fn'
        segs.push({ cls, text: cur }); cur = ''; continue
      }
      // 运算符
      if (/[{}()\[\];,.+\-*/%=<>!&|?:]/.test(ch)) {
        flush(); cur = ch
        while (i + 1 < line.length && /[=<>!&|+\-*/%]/.test(line[i+1]) && /[=<>!&|+\-*/%]/.test(ch)) { cur += line[++i] }
        segs.push({ cls: 'vs-op', text: cur }); cur = ''; i++; continue
      }
      cur += ch; i++
    }
    flush()
    // 空行或空白行返回空数组，模板不渲染空 span
    const hasText = segs.some(s => s.text && s.text.length > 0)
    return hasText ? segs : []
  })
}

// 打开编程题形式参考：将内容暂存到 sessionStorage，跳转到编程工作台
function openProgramReference(ref) {
  const storageKey = 'cryd_practice_ref_content'
  try {
    sessionStorage.setItem(storageKey, JSON.stringify({
      title: ref.title,
      content: ref.content,
      knowledgePoint: ref.chapter,
      difficulty: ref.difficulty,
      timestamp: Date.now()
    }))
    router.push({
      path: '/student/code-workbench/practice-ref',
      query: { from: 'chapter-practice-ref', title: ref.title }
    })
  } catch (e) {
    console.warn('暂存编程题参考失败:', e)
  }
}

function typeLabel(t) {
  const m = { article: '文章', explanation: '知识点', mindmap: '思维导图', code: '代码', summary: '总结', weakness: '分析', 'course-video': '视频', 'course-ppt': '课件', exercise: '练习', blog: '博客' }
  return m[t] || t || ''
}
function typeEmoji(t) {
  const m = { 'course-video': '🎬', 'course-ppt': '📑', mindmap: '🗺️', article: '📄', explanation: '📖', blog: '🎧' }
  return m[t] || '📚'
}
function videoBg(t) {
  const m = { 'course-video': '#ecfeff', 'course-ppt': '#fff7ed', mindmap: '#f5f3ff' }
  return m[t] || '#f8fafc'
}
function diffLabelItem(d) { const m = { easy: '简单', medium: '中等', hard: '困难' }; return m[d] || d || '' }
function extractCodePreview(content) {
  if (!content) return ''
  const m = content.match(/```[\w]*\n([\s\S]*?)```/)
  return m ? m[1] : content
}
function extractTopic(title) {
  if (!title) return ''
  return title.replace(/^\d+\.\d+\.\d+\s*/, '')
}
function extractPage(r) {
  if (!r.content) return 1
  const m = r.content.match(/[?&]p=(\d+)/)
  return m ? parseInt(m[1]) : 1
}
function renderMd(t) {
  try { return t ? marked.parse(t) : '' }
  catch { return t || '' }
}
function renderMdPreview(t) {
  if (!t) return ''
  try {
    const text = t.substring(0, 200)
    return marked.parse(text) + (t.length > 200 ? '...' : '')
  } catch { return t.substring(0, 200) + (t.length > 200 ? '...' : '') }
}
function formatDate(d) {
  if (!d) return ''
  const t = new Date(d)
  return t.getFullYear() + '-' + String(t.getMonth()+1).padStart(2,'0') + '-' + String(t.getDate()).padStart(2,'0')
}
function practiceGradient(i) {
  const g = ['linear-gradient(135deg,#6366f1,#8b5cf6)', 'linear-gradient(135deg,#f43f5e,#fb7185)', 'linear-gradient(135deg,#06b6d4,#22d3ee)', 'linear-gradient(135deg,#f59e0b,#fbbf24)']
  return g[i % g.length]
}

function extractFilename(content) {
  if (!content) return null
  const match = content.match(/```(\w+)?/)
  return match && match[1] ? `main.${match[1] === 'c' ? 'c' : match[1]}` : null
}

function getResourceGradient(type) {
  const gradients = {
    'course-video': 'linear-gradient(135deg, #06b6d4, #0891b2)',
    'course-ppt': 'linear-gradient(135deg, #f59e0b, #d97706)',
    'mindmap': 'linear-gradient(135deg, #8b5cf6, #7c3aed)',
  }
  return gradients[type] || 'linear-gradient(135deg, #6366f1, #4f46e5)'
}

async function loadResources() {
  if (demoStore.enabled) {
    resources.value = await demoStore.getResources()
    return
  }
  if (!store.id) return
  try {
    const res = await api.get('/resources/student/' + store.id)
    if (Array.isArray(res.data)) {
      // 映射 tags 字段到 audioUrl（tags 存的是相对音频路径如 "78/audio_xxx.mp3"）
      resources.value = res.data.map(r => ({
        ...r,
        audioUrl: r.audioUrl || (r.tags && !r.tags.includes('·') ? r.tags : '') // tags含"·"的是视频标签，不是音频路径
      }))
    } else {
      resources.value = []
    }
  } catch (e) {
    console.warn('加载章节资源失败:', e)
    resources.value = []
  }
}

onMounted(() => { loadResources() })
</script>

<style scoped>
/* ===== 页面整体 ===== */
.chapter-page {
  min-height: 100%;
  background: #f8fafc;
}

/* ===== 顶部渐变 Hero ===== */
.chapter-hero {
  position: relative;
  padding: 32px 40px 40px;
  overflow: hidden;
}
.hero-pattern {
  position: absolute;
  inset: 0;
  opacity: 0.08;
  background-image: radial-gradient(circle, #fff 1px, transparent 1px);
  background-size: 20px 20px;
}
.hero-content {
  position: relative;
  z-index: 1;
  width: 100%;
}
.hero-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: rgba(255,255,255,0.2);
  border: 1px solid rgba(255,255,255,0.3);
  border-radius: 10px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  backdrop-filter: blur(8px);
  margin-bottom: 24px;
  font-family: inherit;
}
.hero-back:hover {
  background: rgba(255,255,255,0.3);
}
.hero-info {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 12px;
}
.hero-icon {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  background: rgba(255,255,255,0.2);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  flex-shrink: 0;
  border: 1px solid rgba(255,255,255,0.2);
}
.hero-title {
  margin: 0;
  font-size: 32px;
  font-weight: 800;
  color: #fff;
  letter-spacing: -0.5px;
  text-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.hero-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 14px;
  color: rgba(255,255,255,0.85);
}
.hero-diff {
  padding: 2px 10px;
  background: rgba(255,255,255,0.2);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}
.hero-sep { opacity: 0.5; }
.hero-desc {
  margin: 0;
  font-size: 14px;
  color: rgba(255,255,255,0.8);
  line-height: 1.6;
}

/* ===== 主内容区 ===== */
.chapter-body {
  width: 100%;
  padding: 0 40px 40px;
  box-sizing: border-box;
}

/* ===== Tab 导航 ===== */
.tab-bar {
  display: flex;
  gap: 4px;
  padding: 6px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  border: 1px solid rgba(0,0,0,0.05);
  margin-bottom: 28px;
}
.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 16px;
  border: none;
  background: transparent;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}
.tab-item:hover {
  color: #334155;
  background: #f8fafc;
}
.tab-item.active {
  background: #6366f1;
  color: #fff;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25);
}
.tab-icon { font-size: 16px; }
.tab-badge {
  padding: 1px 8px;
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
}
.tab-item.active .tab-badge {
  background: rgba(255,255,255,0.25);
  color: #fff;
}

/* ===== 资源卡片列表 ===== */
.card-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.resource-card {
  background: #fff;
  border: 1px solid rgba(0,0,0,0.06);
  border-radius: 16px;
  padding: 20px 24px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.resource-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.06);
  border-color: rgba(99, 102, 241, 0.15);
}
.rc-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}
.rc-icon-box {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rc-icon { font-size: 22px; }
.rc-info { flex: 1; min-width: 0; }
.rc-title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rc-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.rc-tag {
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}
.tag-easy { background: #dcfce7; color: #16a34a; }
.tag-medium { background: #fef9c3; color: #ca8a04; }
.tag-hard { background: #fee2e2; color: #dc2626; }
.tag-safe { background: #f0fdf4; color: #16a34a; }
.tag-warn { background: #fef9c3; color: #ca8a04; }
.tag-pass { background: #dcfce7; color: #16a34a; }
.tag-fail { background: #fee2e2; color: #dc2626; }
.tag-type { background: #f1f5f9; color: #475569; }
.tag-cat { background: #eef2ff; color: #4f46e5; }
.rc-arrow {
  color: #cbd5e1;
  flex-shrink: 0;
  transition: transform 0.2s;
}
.resource-card:hover .rc-arrow {
  color: #6366f1;
  transform: translateX(3px);
}
.rc-preview {
  font-size: 14px;
  line-height: 1.7;
  color: #64748b;
  max-height: 80px;
  overflow: hidden;
  position: relative;
}
.rc-preview::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 32px;
  background: linear-gradient(transparent, #fff);
}
.rc-footer {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
}
.rc-date {
  font-size: 12px;
  color: #94a3b8;
}

/* ===== 代码卡片 ===== */
.code-block {
  background: #0f172a;
  border-radius: 12px;
  overflow: hidden;
}
.code-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: rgba(255,255,255,0.05);
}
.code-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.code-dot.red { background: #ef4444; }
.code-dot.yellow { background: #eab308; }
.code-dot.green { background: #22c55e; }
.code-filename {
  margin-left: 8px;
  font-size: 11px;
  color: #64748b;
  font-family: 'Consolas', monospace;
}
.code-content {
  padding: 14px 18px;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #e2e8f0;
  font-family: 'Consolas', 'Fira Code', monospace;
  overflow-x: auto;
}

/* ===== 练习题卡片 ===== */
.practice-cards {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 24px;
}
.practice-card {
  display: flex;
  gap: 16px;
  background: #fff;
  border: 1px solid rgba(0,0,0,0.06);
  border-radius: 14px;
  padding: 18px 20px;
}
.pc-num {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 800;
  flex-shrink: 0;
}
.pc-content { flex: 1; min-width: 0; }
.pc-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}
.pc-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.pc-preview {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  max-height: 50px;
  overflow: hidden;
}
.practice-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px 24px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.3);
  font-family: inherit;
}
.practice-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.4);
}
.btn-icon {
  width: 28px;
  height: 28px;
  background: rgba(255,255,255,0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

/* ===== 视频横幅 ===== */
.video-banner {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: linear-gradient(135deg, rgba(99,102,241,0.08), rgba(139,92,246,0.08));
  border-radius: 12px;
  margin-bottom: 4px;
}
.vb-play {
  width: 40px;
  height: 40px;
  background: #6366f1;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  flex-shrink: 0;
}
.vb-text {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

/* ===== 空状态 ===== */
.empty-state {
  text-align: center;
  padding: 80px 20px;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.7;
}
.empty-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #334155;
}
.empty-desc {
  margin: 0;
  font-size: 14px;
  color: #94a3b8;
}
.empty-desc a {
  color: #6366f1;
  font-weight: 600;
  text-decoration: none;
}
.empty-desc a:hover {
  text-decoration: underline;
}

/* ===== 预览弹窗 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
  padding: 20px;
}
.modal-box {
  background: #fff;
  border-radius: 20px;
  width: 100%;
  max-width: 900px;
  max-height: 85vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
}
.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 28px;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}
.modal-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}
.modal-close {
  width: 36px;
  height: 36px;
  border: none;
  background: #f1f5f9;
  border-radius: 10px;
  cursor: pointer;
  color: #64748b;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}
.modal-close:hover {
  background: #e2e8f0;
  color: #334155;
}
.modal-body {
  padding: 28px;
  overflow-y: auto;
  font-size: 15px;
  line-height: 1.8;
  color: #334155;
}
.modal-body :deep(pre) {
  background: #0f172a;
  color: #e2e8f0;
  padding: 18px 20px;
  border-radius: 12px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
.modal-body :deep(code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', monospace;
  font-size: 13px;
  color: #e11d48;
}
.modal-body :deep(pre code) {
  background: none;
  color: inherit;
  padding: 0;
}
.modal-body :deep(h1), .modal-body :deep(h2), .modal-body :deep(h3) {
  color: #0f172a;
  margin-top: 20px;
}
.modal-body :deep(blockquote) {
  border-left: 4px solid #6366f1;
  padding: 12px 16px;
  margin: 14px 0;
  background: #f5f3ff;
  border-radius: 0 8px 8px 0;
}
.modal-body :deep(ul), .modal-body :deep(ol) {
  padding-left: 24px;
}
.modal-body :deep(li) {
  margin: 4px 0;
}

/* ===== 概念讲解卡片 - 精美详细 ===== */
.concept-card {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 20px;
  padding: 28px 32px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}
.concept-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #6366f1, #8b5cf6, #a855f7);
}
.concept-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(99, 102, 241, 0.15);
  border-color: rgba(99, 102, 241, 0.3);
}
.concept-header {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 20px;
}
.concept-icon {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
.concept-title-area {
  flex: 1;
}
.concept-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.3;
}
.concept-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.meta-tag {
  padding: 4px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
}
.meta-date {
  font-size: 13px;
  color: #64748b;
}
.concept-body {
  font-size: 15px;
  line-height: 1.8;
  color: #334155;
  margin-bottom: 16px;
}
.concept-body :deep(p) {
  margin: 0 0 12px;
}
.concept-footer {
  display: flex;
  justify-content: center;
  padding-top: 12px;
  border-top: 1px solid rgba(99, 102, 241, 0.1);
}
.concept-hint {
  font-size: 13px;
  color: #6366f1;
  font-weight: 600;
}

/* ===== 概念讲解长页面 ===== */
.concept-long-page { margin-top: 8px; }
.long-page-header { text-align: center; padding: 32px 24px 28px; background: linear-gradient(135deg, #f8fafc 0%, #ede9fe 50%, #f0f9ff 100%); border-radius: 20px; margin-bottom: 24px; border: 1px solid rgba(99,102,241,0.1); }
.long-page-title { font-size: 26px; font-weight: 800; background: linear-gradient(135deg, #6366f1, #8b5cf6); -webkit-background-clip: text; -webkit-text-fill-color: transparent; margin-bottom: 10px; }
.long-page-badge { display: inline-block; background: rgba(99,102,241,0.1); color: #6366f1; padding: 6px 18px; border-radius: 20px; font-size: 13px; font-weight: 600; }
.long-section { display: flex; gap: 20px; background: #fff; border-radius: 16px; padding: 28px; margin-bottom: 16px; border: 1px solid rgba(0,0,0,0.04); box-shadow: 0 2px 8px rgba(0,0,0,0.02); transition: all 0.3s; }
.long-section:hover { box-shadow: 0 8px 24px rgba(99,102,241,0.08); transform: translateY(-2px); }
.section-icon { width: 52px; height: 52px; background: linear-gradient(135deg, #ede9fe, #f0f9ff); border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 26px; flex-shrink: 0; }
.section-content { flex: 1; min-width: 0; }
.section-title { font-size: 18px; font-weight: 700; color: #1e293b; margin-bottom: 12px; padding-bottom: 10px; border-bottom: 2px solid #ede9fe; }
.section-body { font-size: 14.5px; line-height: 1.85; color: #475569; }
.section-body :deep(p) { margin: 0 0 10px; }
.section-body :deep(ul), .section-body :deep(ol) { margin: 8px 0; padding-left: 20px; }
.section-body :deep(li) { margin: 4px 0; }
.section-body :deep(strong) { color: #1e293b; }
.section-body :deep(code) { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-size: 13px; color: #6366f1; font-family: 'SF Mono', 'Fira Code', monospace; }
.section-body :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 16px 20px; border-radius: 12px; overflow-x: auto; margin: 12px 0; font-size: 13px; line-height: 1.6; }
.section-body :deep(pre code) { background: none; color: inherit; padding: 0; }
.section-body :deep(table) { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 13px; }
.section-body :deep(th), .section-body :deep(td) { border: 1px solid #e2e8f0; padding: 8px 12px; text-align: left; }
.section-body :deep(th) { background: #f8fafc; font-weight: 600; color: #334155; }
.long-page-footer { text-align: center; padding: 24px; margin-top: 8px; }
.footer-tip { display: inline-block; background: linear-gradient(135deg, #fef3c7, #fde68a); color: #92400e; padding: 12px 28px; border-radius: 12px; font-size: 14px; font-weight: 600; }

/* ===== 代码示例卡片 - 增强版 ===== */
.code-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.code-card-enhanced {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 16px;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.code-card-enhanced:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}
.code-card-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 24px;
  background: linear-gradient(135deg, #fef2f2 0%, #fff 100%);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}
.code-card-num {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #ef4444, #dc2626);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 800;
  flex-shrink: 0;
}
.code-card-info {
  flex: 1;
}
.code-content-body {
  padding: 20px 24px;
  font-size: 14.5px;
  line-height: 1.85;
  color: #475569;
}
.code-content-body :deep(p) { margin: 0 0 10px; }
.code-content-body :deep(pre) { background: #1e293b; color: #e2e8f0; padding: 16px 20px; border-radius: 12px; overflow-x: auto; margin: 12px 0; font-size: 13px; line-height: 1.6; }
.code-content-body :deep(pre code) { background: none; color: inherit; padding: 0; }
.code-content-body :deep(code) { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-size: 13px; color: #6366f1; font-family: 'SF Mono', 'Fira Code', monospace; }
.code-content-body :deep(h1), .code-content-body :deep(h2), .code-content-body :deep(h3) { color: #0f172a; margin-top: 16px; }
.code-content-body :deep(ul), .code-content-body :deep(ol) { margin: 8px 0; padding-left: 20px; }
.code-content-body :deep(li) { margin: 4px 0; }
.code-content-body :deep(strong) { color: #1e293b; }
.code-content-body :deep(table) { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 13px; }
.code-content-body :deep(th), .code-content-body :deep(td) { border: 1px solid #e2e8f0; padding: 8px 12px; text-align: left; }
.code-content-body :deep(th) { background: #f8fafc; font-weight: 600; color: #334155; }
.code-card-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}
.code-card-tags {
  display: flex;
  gap: 8px;
}
.code-tag {
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}
.code-tag.tag-verified {
  background: #dbeafe;
  color: #2563eb;
}
.code-preview-block {
  padding: 0;
}
.code-window {
  background: #0f172a;
  border-radius: 0;
}
.code-window-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
.window-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}
.window-dot.red { background: #ef4444; }
.window-dot.yellow { background: #eab308; }
.window-dot.green { background: #22c55e; }
.window-filename {
  margin-left: 12px;
  font-size: 12px;
  color: #94a3b8;
  font-family: 'Consolas', monospace;
}
.code-window-content {
  padding: 18px 20px;
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #e2e8f0;
  font-family: 'Consolas', 'Fira Code', monospace;
  overflow-x: auto;
  overflow-y: auto;
  white-space: pre;
  max-height: 600px;
}

/* ===== 练习题目 - 简洁入口卡片 ===== */
.practice-header {
  margin-bottom: 20px;
}
.practice-stats {
  display: flex;
  gap: 20px;
}
.stat-item {
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}
.practice-overview-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #f8fafc 0%, #f0f4ff 100%);
  border: 1px solid rgba(99, 102, 241, 0.12);
  border-radius: 16px;
  padding: 24px 28px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.practice-overview-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 32px rgba(99, 102, 241, 0.12);
  border-color: rgba(99, 102, 241, 0.25);
}
.practice-overview-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}
.practice-overview-icon {
  width: 52px;
  height: 52px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25);
}
.practice-overview-info {
  flex: 1;
}
.practice-overview-title {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}
.practice-overview-desc {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}
.practice-overview-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  flex-shrink: 0;
}
.practice-overview-stats {
  display: flex;
  gap: 16px;
}
.po-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.po-stat-val {
  font-size: 20px;
  font-weight: 800;
  color: #6366f1;
  font-family: 'JetBrains Mono', monospace;
}
.po-stat-label {
  font-size: 11px;
  color: #94a3b8;
}
.practice-start-btn-sm {
  padding: 10px 24px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.25);
}
.practice-start-btn-sm:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.35);
}

/* ===== 编程题形式参考 =====
 * 1. 区块头（program-ref-overview-card）与上方"练习题入口卡片"样式统一
 * 2. 卡片列表（.resource-grid / .resource-card / .tr-cover / .tr-card-body）与今日推荐编程实战模块 VS Code 封面卡片设计统一
 */
.program-ref-section {
  margin-top: 24px;
}

/* ===== 区块头：完全复用 practice-overview-card 样式（两张大卡片格式统一）===== */
.program-ref-overview-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #f8fafc 0%, #f0f4ff 100%);
  border: 1px solid rgba(99, 102, 241, 0.12);
  border-radius: 16px;
  padding: 24px 28px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 24px;
}
.program-ref-overview-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 32px rgba(99, 102, 241, 0.12);
  border-color: rgba(99, 102, 241, 0.25);
}
.program-ref-overview-icon {
  width: 52px;
  height: 52px;
  background: linear-gradient(135deg, #ec4899, #f43f5e);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(236, 72, 153, 0.25);
}
/* 复用上方练习题卡片的左右栏/标题/描述/统计/按钮样式 */
.program-ref-overview-card .practice-overview-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}
.program-ref-overview-card .practice-overview-info { flex: 1; }
.program-ref-overview-card .practice-overview-title {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}
.program-ref-overview-card .practice-overview-desc {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}
.program-ref-overview-card .practice-overview-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  flex-shrink: 0;
}
.program-ref-overview-card .practice-overview-stats {
  display: flex;
  gap: 16px;
}
.program-ref-overview-card .po-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.program-ref-overview-card .po-stat-val {
  font-size: 20px;
  font-weight: 800;
  color: #ec4899;
  font-family: 'JetBrains Mono', monospace;
}
.program-ref-overview-card .po-stat-label {
  font-size: 11px;
  color: #94a3b8;
}

/* ===== 卡片列表：完全对齐今日推荐编程实战的 VS Code 封面卡片 ===== */
.program-ref-section .resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}
.program-ref-section .resource-card {
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid rgba(0, 0, 0, 0.05);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 0;
  display: flex;
  flex-direction: column;
}
.program-ref-section .resource-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1);
  border-color: rgba(99, 102, 241, 0.15);
}
.program-ref-section .tr-cover-area {
  position: relative;
  height: 180px;
  overflow: hidden;
}
.program-ref-section .cover-vscode {
  background: #1e1e1e;
  height: 100%;
  display: flex;
  flex-direction: column;
  font-family: 'JetBrains Mono', 'Cascadia Code', 'SF Mono', Consolas, monospace;
  font-size: 10.5px;
  line-height: 1.6;
}
.program-ref-section .vs-titlebar {
  background: #323233;
  height: 28px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  gap: 6px;
}
.program-ref-section .vs-tab {
  background: #2d2d2d;
  padding: 4px 12px;
  border-radius: 4px 4px 0 0;
  font-size: 10px;
  color: #969696;
  display: flex;
  align-items: center;
  gap: 4px;
}
.program-ref-section .vs-tab.active { background: #1e1e1e; color: #fff; }
.program-ref-section .vs-icon { font-size: 12px; }
.program-ref-section .vs-editor { padding: 8px 12px; flex: 1; overflow: hidden; color: #d4d4d4; }
.program-ref-section .vs-editor > div { white-space: nowrap; }
.program-ref-section .vs-line-num { color: #505050; display: inline-block; width: 24px; text-align: right; margin-right: 12px; user-select: none; }
.program-ref-section .vs-pp { color: #c586c0; }
.program-ref-section .vs-ty { color: #4ec9b0; }
.program-ref-section .vs-fn { color: #dcdcaa; }
.program-ref-section .vs-str { color: #ce9178; }
.program-ref-section .vs-num { color: #b5cea8; }
.program-ref-section .vs-cm { color: #6a9955; }
.program-ref-section .vs-op { color: #d4d4d4; }
.program-ref-section .vs-kw { color: #c586c0; }
.program-ref-section .vs-inc { color: #ce9178; }
.program-ref-section .vs-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 40%, rgba(0,0,0,0.4) 100%);
  pointer-events: none;
}
.program-ref-section .vs-badge {
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
.program-ref-section .tr-card-body {
  padding: 12px 14px 14px;
}
.program-ref-section .tr-card-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: #1e293b;
  margin: 0 0 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.program-ref-section .tr-card-tags {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}
.program-ref-section .tr-tag {
  padding: 2px 8px;
  border-radius: 20px;
  font-size: 10px;
  font-weight: 500;
  white-space: nowrap;
}
.program-ref-section .tag-code { background: #fef2f2; color: #ef4444; }
.program-ref-section .tag-chapter { background: #f5f3ff; color: #8b5cf6; }
.program-ref-section .tr-score {
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
.program-ref-section .score-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #059669;
}

/* ===== 学习资源卡片 - 增强版 ===== */
.resource-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}
.resource-card-enhanced {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.resource-card-enhanced:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1);
}
.resource-card-top {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.resource-type-badge {
  width: 56px;
  height: 56px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  backdrop-filter: blur(8px);
}
.resource-play-btn {
  position: absolute;
  bottom: 12px;
  right: 12px;
  width: 40px;
  height: 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.2s;
}
.resource-play-btn span {
  color: #0f172a;
  font-size: 14px;
  margin-left: 2px;
}
.resource-card-enhanced:hover .resource-play-btn {
  transform: scale(1.1);
  background: #fff;
}
.resource-card-bottom {
  padding: 18px 20px;
}
.resource-card-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.resource-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.resource-tag {
  padding: 3px 10px;
  background: #f1f5f9;
  color: #475569;
}
.resource-tag.tag-safe { background: #f0fdf4; color: #16a34a; }
.resource-tag.tag-warn { background: #fef9c3; color: #ca8a04; }
.resource-category {
  padding: 3px 10px;
  background: #eef2ff;
  color: #4f46e5;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}
.resource-card-date {
  font-size: 12px;
  color: #94a3b8;
}

/* ===== 音频播放器 ===== */
.audio-player {
  margin-top: 12px;
  padding: 8px;
  background: #f8fafc;
  border-radius: 8px;
}
.audio-element {
  width: 100%;
  height: 36px;
}
.audio-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 12px;
  background: linear-gradient(135deg, #f5f3ff, #ede9fe);
  border-radius: 6px;
  font-size: 12px;
  color: #7c3aed;
  cursor: pointer;
  transition: all 0.2s;
}
.audio-hint:hover {
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
}
.audio-hint-icon {
  font-size: 14px;
}

/* ===== 响应式适配 ===== */
@media (max-width: 768px) {
  .resource-grid {
    grid-template-columns: 1fr;
  }
  .concept-card {
    padding: 20px 24px;
  }
  .concept-icon {
    width: 48px;
    height: 48px;
    font-size: 24px;
  }
  .concept-title {
    font-size: 18px;
  }
  .code-card-header {
    padding: 14px 18px;
  }
  .practice-stats {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
