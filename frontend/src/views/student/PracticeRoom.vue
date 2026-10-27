<template>
  <div class="practice-page">
    <!-- 顶部导航栏 -->
    <nav class="top-navbar">
      <div class="nav-left">
        <button v-if="from !== 'onboarding'" class="back-btn" @click="goBack">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
          返回详情页
        </button>
        <div class="quiz-title">{{ chapterName }}练习</div>
      </div>
      <div class="nav-right">
        <div class="timer-display">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
          <span>{{ formattedTime }}</span>
        </div>
        <div class="progress-info">
          进度 <span>{{ currentIndex + 1 }}/{{ totalQuestions }}</span>
        </div>
      </div>
    </nav>

    <!-- 主内容区 -->
    <div class="main-wrapper" v-if="!showResult">
      <!-- 左侧题目导航 -->
      <aside class="sidebar-nav">
        <div class="sidebar-header">
          <div class="sidebar-title">题目导航</div>
          <div class="legend">
            <div class="legend-item">
              <div class="legend-dot current"></div>
              <span>当前</span>
            </div>
            <div class="legend-item">
              <div class="legend-dot correct"></div>
              <span>正确</span>
            </div>
            <div class="legend-item">
              <div class="legend-dot wrong"></div>
              <span>错误</span>
            </div>
          </div>
        </div>
        <div class="question-nav-grid">
          <button
            v-for="(q, i) in questions"
            :key="i"
            :class="['nav-btn', {
              'current': i === currentIndex,
              'answered': questionResults[i] !== undefined
            }]"
            @click="goToQuestion(i)"
          >
            <span class="nav-btn-number">{{ i + 1 }}</span>
            <span class="nav-btn-status">
              {{ i === currentIndex ? '当前' : (questionResults[i] !== undefined ? '已答' : '未答') }}
            </span>
          </button>
        </div>
      </aside>

      <!-- 右侧答题区 -->
      <div class="content-area">
        <div class="question-container" v-if="currentQuestion">
          <div class="question-card">
            <div class="question-header">
              <div class="question-number">第 {{ currentIndex + 1 }} 题 / 共 {{ totalQuestions }} 题</div>
              <div class="q-tags">
                <span class="tag">单选题</span>
                <span class="tag tag-info">{{ currentQuestion.knowledgePoint }}</span>
                <HallucinationBadge
                  v-if="currentQuestion.hallucinationScore != null"
                  :score="currentQuestion.hallucinationScore"
                  :risk="currentQuestion.hallucinationRisk"
                  :note="currentQuestion.hallucinationNote"
                  size="small"
                />
              </div>
              <div class="question-text">{{ currentQuestion.question }}</div>
            </div>

            <div class="options-list">
              <div
                v-for="(opt, oi) in currentQuestion.options"
                :key="oi"
                :class="['option-btn', {
                  'selected': selectedIndex === oi,
                }]"
                @click="selectOption(oi)"
              >
                <div class="option-label">{{ ['A','B','C','D'][oi] }}</div>
                <div class="option-text">{{ opt.replace(/^[A-D][.、]\s*/, '') }}</div>
                <span v-if="selectedIndex === oi" class="option-icon icon-selected">●</span>
              </div>
            </div>

            <!-- 答案解析 - 答题过程中不显示，结果页面才显示 -->
            <div v-if="false" class="analysis-section">
              <div :class="['analysis-header', answeredCorrectly ? 'correct' : 'wrong']">
                {{ answeredCorrectly ? '✅ 回答正确' : '❌ 回答错误' }}
              </div>
              <div class="analysis-body">{{ analysisText }}</div>
            </div>

            <!-- 底部按钮 -->
            <div class="question-footer">
              <button
                class="next-btn"
                @click="goToNext"
              >
                {{ isLastQuestion ? '📊 查看结果' : '下一题 →' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 正在生成页面 -->
    <div v-if="showGenerating" class="generating-overlay">
      <div class="generating-card">
        <div class="generating-icon">
          <div class="spinner"></div>
        </div>
        <h2 class="generating-title">正在分析你的学习画像...</h2>
        <div class="generating-steps">
          <div v-for="(step, index) in generatingSteps" :key="index" class="generating-step" :class="{ active: index <= generatingStep, done: index < generatingStep }">
            <span class="step-icon">{{ index < generatingStep ? '✓' : (index === generatingStep ? '●' : '○') }}</span>
            <span class="step-text">{{ step }}</span>
          </div>
        </div>
        <div class="generating-progress">
          <div class="progress-bar" :style="{ width: ((generatingStep + 1) / generatingSteps.length * 100) + '%' }"></div>
        </div>
      </div>
    </div>

    <!-- 结果弹窗 -->
    <div v-if="showResult" class="result-overlay" @click.self="goBack">
      <div class="result-card">
        <button class="result-close-btn" @click="goBack" title="关闭">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
        <div class="result-icon" :class="resultIconClass">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="48" height="48">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <path d="M22 4L12 14.01l-3-3"/>
          </svg>
        </div>
        <h2 class="result-title">🎉 练习完成!</h2>
        <p class="result-subtitle">{{ chapterName }}练习</p>

        <div class="score-display">
          <div class="score-circle">
            <svg viewBox="0 0 100 100">
              <circle class="score-bg" cx="50" cy="50" r="45"/>
              <circle class="score-progress" :style="{ strokeDashoffset: scoreOffset, stroke: ringColor }" cx="50" cy="50" r="45"/>
            </svg>
            <div class="score-text">
              <span class="score-number">{{ score }}</span>
              <span class="score-unit">分</span>
            </div>
          </div>
        </div>

        <div class="result-stats">
          <div class="stat-item">
            <div class="stat-label">正确</div>
            <div class="stat-value correct">{{ correctCount }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">错误</div>
            <div class="stat-value wrong">{{ wrongCount }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">用时</div>
            <div class="stat-value">{{ formattedTime }}</div>
          </div>
        </div>

        <!-- 能力检测完成提示 -->
        <div v-if="chapterName === '能力检测'" class="resource-generated-notice">
          <div class="notice-icon">✨</div>
          <div class="notice-content">
            <h4>个性化学习资源已生成</h4>
            <p>AI已根据你的测试结果，为你生成了以下资源：</p>
            <div class="resource-tags">
              <span class="res-tag">📄 学习笔记</span>
              <span class="res-tag">💻 代码示例</span>
              <span class="res-tag">📝 练习题</span>
              <span class="res-tag">🎬 视频教程</span>
              <span class="res-tag">🗺️ 思维导图</span>
            </div>
          </div>
        </div>

        <div class="result-actions">
          <button class="action-btn secondary" @click="viewPaper">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            查看试卷
          </button>
          <button class="action-btn primary" @click="retryQuiz">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
            再练一次
          </button>
          <!-- 能力检测完成后的跳转按钮 -->
          <button v-if="chapterName === '能力检测'" class="action-btn go-home" @click="goToHome">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
              <polyline points="9 22 9 12 15 12 15 22"/>
            </svg>
            进入首页
          </button>
        </div>
      </div>
    </div>

    <!-- 试卷查看 -->
    <div v-if="showPaper" class="paper-overlay">
      <nav class="paper-navbar">
        <button class="action-btn secondary" @click="backToResult">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
          返回结果
        </button>
        <div class="paper-title">试卷详情</div>
        <div class="paper-legend">
          <span class="legend-correct">✓ 正确</span>
          <span class="legend-wrong">✗ 错误</span>
        </div>
      </nav>
      <div class="paper-content">
        <div
          v-for="(q, i) in questions"
          :key="i"
          :class="['paper-question', questionResults[i]?.correct ? 'correct' : 'wrong']"
        >
          <div class="paper-q-header">
            <span class="paper-q-number">第 {{ i + 1 }} 题</span>
            <span :class="['paper-q-status', questionResults[i]?.correct ? 'correct' : 'wrong']">
              {{ questionResults[i]?.correct ? '✓ 正确' : '✗ 错误' }}
            </span>
          </div>
          <div class="paper-q-text">{{ q.question }}</div>
          <div class="paper-options">
            <div
              v-for="(opt, oi) in q.options"
              :key="oi"
              :class="['paper-option', {
                'correct-answer': oi === q.correctIndex,
                'wrong-answer': questionResults[i] && questionResults[i].selectedIndex === oi && oi !== q.correctIndex
              }]"
            >
              <div class="paper-opt-label">{{ ['A','B','C','D'][oi] }}</div>
              <div class="paper-opt-text">{{ opt.replace(/^[A-D][.、]\s*/, '') }}</div>
            </div>
          </div>
          <div class="paper-analysis">
            <div class="analysis-label">解析</div>
            <div class="analysis-text">{{ q.analysis }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDemoStore } from '../../store/demo'
import { useUserStore } from '../../store/user'
import api from '../../api'
import HallucinationBadge from '../../components/HallucinationBadge.vue'

const route = useRoute()
const router = useRouter()
const demoStore = useDemoStore()
const userStore = useUserStore()

const chapterName = computed(() => route.params.knowledgePoint || '未知章节')
const from = computed(() => route.query.from || '')

const questions = ref([])
const DEMO_QUESTIONS = {
  '指针': [
    { id: 1, question: '若有定义 int *p, a=10; p=&a; 则以下哪个表达式表示 a 的值？', options: ['A. p', 'B. &p', 'C. *p', 'D. &a'], correctIndex: 2, knowledgePoint: '指针', analysis: '*p 是解引用操作，p 指向 a，所以 *p 就是 a 的值 10。' },
    { id: 2, question: '指针变量存储的是什么？', options: ['A. 变量的值', 'B. 变量的地址', 'C. 变量的类型', 'D. 变量的名称'], correctIndex: 1, knowledgePoint: '指针', analysis: '指针变量存储的是其他变量的内存地址。' },
    { id: 3, question: '以下哪个是正确的指针声明？', options: ['A. int p*', 'B. *int p', 'C. int *p', 'D. pointer int p'], correctIndex: 2, knowledgePoint: '指针', analysis: 'C语言中指针声明格式为：类型 *指针名。' },
    { id: 4, question: '如何获取变量a的地址？', options: ['A. *a', 'B. &a', 'C. a', 'D. #a'], correctIndex: 1, knowledgePoint: '指针', analysis: '&是取地址运算符，&a返回变量a的内存地址。' },
    { id: 5, question: '指针p指向变量a，*p=20后，a的值变为？', options: ['A. 原来的值', 'B. 20', 'C. p的地址', 'D. 无法确定'], correctIndex: 1, knowledgePoint: '指针', analysis: '*p=20修改了p指向的内存地址的值，即a的值。' },
    { id: 6, question: '以下代码的输出是什么？\nint a = 10;\nint *p = &a;\n*p = 20;\nprintf("%d", a);', options: ['A. 10', 'B. 20', 'C. 编译错误', 'D. 随机值'], correctIndex: 1, knowledgePoint: '指针', analysis: '通过指针修改了a的值，所以a变为20。' },
    { id: 7, question: '二级指针是指？', options: ['A. 指向数组的指针', 'B. 指向指针的指针', 'C. 两个指针相加', 'D. 指针的别名'], correctIndex: 1, knowledgePoint: '指针', analysis: '二级指针是指向指针的指针，声明格式如 int **pp。' },
    { id: 8, question: '对于 int *p, *q; 以下哪个操作是合法的？', options: ['A. p + q', 'B. p - q', 'C. p * q', 'D. p / q'], correctIndex: 1, knowledgePoint: '指针', analysis: '两个指针可以相减，得到它们之间的元素个数。' },
    { id: 9, question: '以下哪个是void指针的正确用法？', options: ['A. void *p; *p = 10;', 'B. void *p; int *ip = p;', 'C. void p = 10;', 'D. int *p = void;'], correctIndex: 1, knowledgePoint: '指针', analysis: 'void*可以指向任何类型，但需要类型转换后才能解引用。' },
    { id: 10, question: '指针运算 p+1 实际上地址增加了多少？', options: ['A. 1字节', 'B. 指向类型的大小', 'C. 4字节', 'D. 8字节'], correctIndex: 1, knowledgePoint: '指针', analysis: '指针运算的步长取决于指向类型的大小。int*加1增加4字节。' },
  ],
  '数组': [
    { id: 1, question: '定义 int arr[5] = {1,2,3,4,5}; 则 arr[2] 的值是？', options: ['A. 1', 'B. 2', 'C. 3', 'D. 4'], correctIndex: 2, knowledgePoint: '数组', analysis: 'C 语言数组下标从 0 开始，arr[0]=1, arr[1]=2, arr[2]=3。' },
    { id: 2, question: 'C语言数组的下标从几开始？', options: ['A. 1', 'B. 0', 'C. -1', 'D. 任意'], correctIndex: 1, knowledgePoint: '数组', analysis: 'C语言数组下标从0开始。' },
    { id: 3, question: '数组名代表什么？', options: ['A. 第一个元素', 'B. 数组的首地址', 'C. 数组的大小', 'D. 最后一个元素'], correctIndex: 1, knowledgePoint: '数组', analysis: '数组名代表数组的首地址，即第一个元素的地址。' },
    { id: 4, question: '数组作为函数参数时，传递的是？', options: ['A. 数组的副本', 'B. 数组的首地址', 'C. 数组的大小', 'D. 第一个元素的值'], correctIndex: 1, knowledgePoint: '数组', analysis: '数组作为参数传递的是首地址，不是副本。' },
    { id: 5, question: '字符串结束符是？', options: ['A. \\n', 'B. \\0', 'C. \\t', 'D. 空格'], correctIndex: 1, knowledgePoint: '数组', analysis: 'C语言字符串以\\0作为结束符。' },
    { id: 6, question: '以下哪个是正确的二维数组声明？', options: ['A. int arr[3,4]', 'B. int arr[3][4]', 'C. int arr(3)(4)', 'D. int arr{3}{4}'], correctIndex: 1, knowledgePoint: '数组', analysis: 'C语言二维数组声明格式为 类型 数组名[行数][列数]。' },
    { id: 7, question: 'sizeof(arr) 对于 int arr[10] 返回什么？', options: ['A. 10', 'B. 40', 'C. 4', 'D. 8'], correctIndex: 1, knowledgePoint: '数组', analysis: 'sizeof(arr)返回整个数组的大小，10个int共40字节。' },
    { id: 8, question: '数组 arr[5] 的合法下标范围是？', options: ['A. 1-5', 'B. 0-4', 'C. 0-5', 'D. 1-4'], correctIndex: 1, knowledgePoint: '数组', analysis: '数组下标从0开始，arr[5]的合法下标是0到4。' },
    { id: 9, question: '以下哪个操作可以正确初始化数组？', options: ['A. int arr[3] = {1,2,3,4}', 'B. int arr[] = {1,2,3}', 'C. int arr[3] = {1,2}', 'D. B和C都正确'], correctIndex: 3, knowledgePoint: '数组', analysis: 'B省略大小由编译器推断，C未初始化的元素自动补0。' },
    { id: 10, question: '字符数组和字符串的区别是？', options: ['A. 没有区别', 'B. 字符串以\\0结尾', 'C. 字符串更长', 'D. 字符数组不能存储字符'], correctIndex: 1, knowledgePoint: '数组', analysis: '字符串是特殊的字符数组，以\\0作为结束标志。' },
  ],
  '函数': [
    { id: 1, question: '以下函数声明正确的是？', options: ['A. int func(int x, int y)', 'B. func(int x, int y)', 'C. int func(int x, y)', 'D. int func[x,y]'], correctIndex: 0, knowledgePoint: '函数', analysis: '函数声明需要返回类型+函数名+参数列表(每个参数需要类型)。' },
    { id: 2, question: '函数的返回类型可以省略吗？', options: ['A. 可以', 'B. 不可以', 'C. 只有void可以', 'D. 取决于编译器'], correctIndex: 1, knowledgePoint: '函数', analysis: 'C语言函数必须明确声明返回类型，即使没有返回值也要用void。' },
  ],
  '结构体': [
    { id: 1, question: '以下定义结构体的语法正确的是？', options: ['A. struct Student { char name[20]; int age; };', 'B. struct { char name[20]; int age; }', 'C. Student { char name[20]; int age; };', 'D. define struct Student { char name[20]; int age; }'], correctIndex: 0, knowledgePoint: '结构体', analysis: 'A 是标准的结构体定义语法：struct 结构体名 { 成员列表 };' },
    { id: 2, question: '如何访问结构体成员？', options: ['A. 使用 -> 运算符', 'B. 使用 . 运算符', 'C. 使用 [] 运算符', 'D. 使用 * 运算符'], correctIndex: 1, knowledgePoint: '结构体', analysis: '使用结构体变量时用.运算符，使用结构体指针时用->运算符。' },
  ],
  '内存管理': [
    { id: 1, question: '动态分配内存后必须调用哪个函数释放？', options: ['A. delete()', 'B. release()', 'C. free()', 'D. dispose()'], correctIndex: 2, knowledgePoint: '内存管理', analysis: 'malloc/calloc/realloc 分配的内存必须用 free() 释放，否则造成内存泄漏。' },
    { id: 2, question: 'malloc函数的头文件是？', options: ['A. stdio.h', 'B. stdlib.h', 'C. string.h', 'D. math.h'], correctIndex: 1, knowledgePoint: '内存管理', analysis: 'malloc、free等动态内存分配函数声明在stdlib.h中。' },
  ],
  '控制结构': [
    { id: 1, question: 'C语言中，break语句可以用于？', options: ['A. 只能用于循环', 'B. 只能用于switch', 'C. 用于循环和switch', 'D. 用于任何地方'], correctIndex: 2, knowledgePoint: '控制结构', analysis: 'break语句可以跳出循环和switch语句。' },
    { id: 2, question: '以下哪个循环结构会至少执行一次循环体？', options: ['A. for循环', 'B. while循环', 'C. do-while循环', 'D. 所有循环'], correctIndex: 2, knowledgePoint: '控制结构', analysis: 'do-while循环先执行循环体，再判断条件，所以至少执行一次。' },
    { id: 3, question: 'switch语句中，如果没有break会怎样？', options: ['A. 程序出错', 'B. 跳出switch', 'C. 继续执行下一个case', 'D. 返回main函数'], correctIndex: 2, knowledgePoint: '控制结构', analysis: '没有break会继续执行下一个case的代码。' },
  ],
  '基础语法': [
    { id: 1, question: '以下哪个是C语言的正确注释方式？', options: ['A. // 这是注释', 'B. /* 这是注释 */', 'C. -- 这是注释', 'D. A和B都正确'], correctIndex: 3, knowledgePoint: '基础语法', analysis: 'C语言支持单行注释//和多行注释/* */。' },
    { id: 2, question: '以下哪个是合法的变量名？', options: ['A. 2name', 'B. _name', 'C. my-name', 'D. my name'], correctIndex: 1, knowledgePoint: '基础语法', analysis: '变量名可以以字母或下划线开头。' },
    { id: 3, question: '以下哪个是C语言的关键字？', options: ['A. printf', 'B. scanf', 'C. int', 'D. main'], correctIndex: 2, knowledgePoint: '基础语法', analysis: 'int是C语言的关键字。' },
  ],
  '文件操作': [
    { id: 1, question: 'fopen打开文件失败返回什么？', options: ['A. -1', 'B. 0', 'C. NULL', 'D. EOF'], correctIndex: 2, knowledgePoint: '文件操作', analysis: 'fopen失败返回NULL。' },
    { id: 2, question: '以下哪个函数用于比较字符串？', options: ['A. strcmp()', 'B. strcpy()', 'C. strcat()', 'D. strlen()'], correctIndex: 0, knowledgePoint: '文件操作', analysis: 'strcmp用于比较两个字符串。' },
  ],
}

const currentIndex = ref(0)
const selectedIndex = ref(null)
const answered = ref(false)
const showResult = ref(false)
const showPaper = ref(false)
const showGenerating = ref(false)
const generatingStep = ref(0)
const generatingSteps = ['分析学习基础', '构建个性化学习画像', '生成综合学习路径', '生成学习资源']
const questionResults = ref([])
const timerSeconds = ref(0)
let timerInterval = null

const totalQuestions = computed(() => questions.value.length)
const currentQuestion = computed(() => currentIndex.value < questions.value.length ? questions.value[currentIndex.value] : null)
const correctCount = computed(() => questionResults.value.filter(r => r?.correct).length)
const wrongCount = computed(() => questionResults.value.filter(r => r && !r.correct).length)
const accuracy = computed(() => questionResults.value.length === 0 ? 100 : Math.round((correctCount.value / questionResults.value.length) * 100))
const answeredCorrectly = computed(() => answered.value && currentQuestion.value ? selectedIndex.value === currentQuestion.value.correctIndex : false)
const analysisText = computed(() => answered.value && currentQuestion.value ? currentQuestion.value.analysis || '' : '')
const isLastQuestion = computed(() => currentIndex.value >= questions.value.length - 1)
const score = computed(() => totalQuestions.value > 0 ? Math.round((correctCount.value / totalQuestions.value) * 100) : 0)

const formattedTime = computed(() => {
  const m = Math.floor(timerSeconds.value / 60)
  const s = timerSeconds.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const ringColor = computed(() => score.value >= 80 ? '#10b981' : score.value >= 60 ? '#f59e0b' : '#ef4444')
const scoreOffset = computed(() => {
  const circumference = 2 * Math.PI * 45
  return circumference - (score.value / 100) * circumference
})
const resultIconClass = computed(() => score.value >= 80 ? 'excellent' : score.value >= 60 ? 'good' : 'fail')

function startTimer() {
  stopTimer()
  timerInterval = setInterval(() => timerSeconds.value++, 1000)
}

function stopTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

function selectOption(oi) {
  if (answered.value) return
  selectedIndex.value = oi
  answered.value = true
  const q = currentQuestion.value
  questionResults.value[currentIndex.value] = {
    selectedIndex: oi,
    correct: oi === q.correctIndex,
    knowledgePoint: q.knowledgePoint || ''
  }

  // 异步提交到后端
  if (!demoStore.enabled) {
    api.post('/practice/targeted/' + userStore.id + '/answer', {
      answer: oi,
      correctAnswer: q.correctIndex,
      knowledgePoint: q.knowledgePoint || ''
    }).then(res => {
      if (res.data && res.data.explanation) {
        questionResults.value[currentIndex.value].analysis = res.data.explanation
      }
    }).catch(e => {
      console.warn('提交答案到后端失败:', e.message || e)
    })
  }
}

function goToQuestion(index) {
  currentIndex.value = index
  selectedIndex.value = questionResults.value[index]?.selectedIndex ?? null
  answered.value = questionResults.value[index] !== undefined
}

function goToNext() {
  if (isLastQuestion.value) {
    stopTimer()

    // 计算分数
    const correct = questionResults.value.filter(r => r?.correct).length
    const total = questionResults.value.length
    const score = Math.round((correct / total) * 100)

    try { demoStore.updatePracticeResults(questionResults.value, timerSeconds.value, chapterName.value) } catch(e) {}

    // 根据章节名称标记练习类型为已完成
    const practiceTypeMap = {
      '能力检测': 'quiz',
      '巩固练习': 'practice',
      '艾宾浩斯复习': 'ebbinghaus',
    }
    const practiceType = practiceTypeMap[chapterName.value]
    if (practiceType) {
      try { demoStore.markPracticeCompleted(practiceType, score) } catch(e) {}
    }

    // 保存当前会话的答题记录，以便同一登录期间可以查看
    const sessionKey = 'cryd_session_practice_' + chapterName.value
    localStorage.setItem(sessionKey, JSON.stringify({
      completed: true,
      score: score,
      questions: JSON.parse(JSON.stringify(questions.value)), // 深拷贝
      results: JSON.parse(JSON.stringify(questionResults.value)), // 深拷贝
      timeSpent: timerSeconds.value
    }))

    // 真实模式：调用后端submit接口，触发资源生成和画像更新
    if (!demoStore.enabled && userStore.id) {
      submitToBackend(score, correct, total)
    }

    // 能力检测完成后，显示"正在生成"页面
    if (chapterName.value === '能力检测') {
      showGenerating.value = true
      generatingStep.value = 0
      // 模拟生成过程
      simulateGenerating()
    } else {
      showResult.value = true
    }
    return
  }
  currentIndex.value++
  selectedIndex.value = null
  answered.value = false
}

// 调用后端submit接口
async function submitToBackend(score, correct, total) {
  try {
    // 构建答案对象
    const answers = {}
    questionResults.value.forEach((r, i) => {
      answers[i + 1] = r.selectedIndex !== null ? String.fromCharCode(65 + r.selectedIndex) : ''
    })

    // 能力检测：调用EvaluationController的submitTest API
    if (chapterName.value === '能力检测') {
      // 先创建TestRecord
      const generateRes = await api.post('/evaluation/test/generate', {
        studentId: userStore.id,
        topic: '能力检测',
        questionCount: questions.value.length
      })

      if (generateRes.code === 200 && generateRes.data) {
        const testId = generateRes.data.id
        // 提交答案到EvaluationController
        const submitRes = await api.post(`/evaluation/test/${testId}/submit`, {
          answers: JSON.stringify(answers)
        })

        if (submitRes.code === 200) {
          // 能力检测完成，已触发资源生成
        }
      }
      return
    }

    // 其他练习类型：调用PracticeController的assignment submit API
    const chapterIdMap = {
      '巩固练习': 2,
      '艾宾浩斯复习': 3
    }
    const chapterId = chapterIdMap[chapterName.value] || 1

    const res = await api.post(`/practice/assignment/${chapterId}/submit`, {
      studentId: userStore.id,
      answers: JSON.stringify(answers)
    })

    if (res.code === 200) {
      // 如果有调整信息，显示给用户
      if (res.data?.adjustedInfo) {
      }
    }
  } catch (e) {
    console.warn('提交答题结果失败:', e.message || e)
  }
}

function goBack() {
  // 检查是否来自今日推荐页面
  const from = route.query.from
  if (from === 'today-recommend') {
    router.push('/student/today-recommend')
  } else {
    router.push({ path: '/student/resources/chapter', query: { name: chapterName.value } })
  }
}

// 模拟生成过程
function simulateGenerating() {
  const steps = [
    '分析学习基础',
    '构建个性化学习画像',
    '生成综合学习路径',
    '生成学习资源'
  ]

  let step = 0
  const delays = [2000, 2500, 2000, 1500] // 每个步骤的延迟时间（毫秒）

  function nextStep() {
    if (step < steps.length) {
      generatingStep.value = step
      step++
      setTimeout(nextStep, delays[step - 1] || 2000)
    } else {
      // 生成完成，跳转到首页
      setTimeout(() => {
        goToHome()
      }, 1000)
    }
  }

  nextStep()
}

// 跳转到首页
function goToHome() {
  // 如果是能力检测完成，设置标记并记录答题日期
  if (chapterName.value === '能力检测') {
    const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
    // 根据模式使用不同的存储键
    const abilityKey = isDemo ? 'demo_ability_tested' : 'cryd_ability_tested'
    const practiceKey = isDemo ? 'demo_completed_practice' : 'cryd_completed_practice'

    localStorage.setItem(abilityKey, 'true')
    // 记录答题完成状态和日期
    const today = new Date().toISOString().split('T')[0]
    const completedPractice = JSON.parse(localStorage.getItem(practiceKey) || '{}')
    completedPractice.quiz = {
      completed: true,
      score: score.value || 0,
      date: today
    }
    localStorage.setItem(practiceKey, JSON.stringify(completedPractice))
  }
  router.push('/student/home')
}

function viewPaper() {
  showResult.value = false
  showPaper.value = true
}

function backToResult() {
  showPaper.value = false
  showResult.value = true
}

function retryQuiz() {
  currentIndex.value = 0
  selectedIndex.value = null
  answered.value = false
  showResult.value = false
  showPaper.value = false
  questionResults.value = []
  timerSeconds.value = 0
  loadQuestions()
  startTimer()
}

async function loadQuestions() {
  // 根据不同的练习类型，使用不同的出题策略
  const practiceType = chapterName.value

  // 能力检测、巩固练习、艾宾浩斯复习 - 使用AI智能出题
  if (['能力检测', '巩固练习', '艾宾浩斯复习'].includes(practiceType)) {
    if (!demoStore.enabled && userStore.id) {
      try {
        // 根据练习类型确定出题参数
        let prompt = ''
        let count = 15  // 每次出15题

        if (practiceType === '能力检测') {
          prompt = '综合测试C语言掌握程度，覆盖所有核心知识点'
        } else if (practiceType === '巩固练习') {
          prompt = '针对薄弱知识点的专项训练'
        } else if (practiceType === '艾宾浩斯复习') {
          prompt = '根据遗忘曲线，复习用户做错的题目和薄弱知识点'
        }

        const res = await api.post('/practice/ai-generate/' + userStore.id + '?count=' + count)
        if (res.code === 200 && res.data) {
          const qList = res.data
          if (qList.length > 0) {
            questions.value = qList.map((q, i) => ({
              id: i + 1,
              question: q.question || q.content || '',
              options: q.options || [],
              correctIndex: q.answer != null ? q.answer : 0,
              knowledgePoint: q.knowledgePoint || practiceType,
              analysis: q.explanation || q.analysis || ''
            }))
            return
          }
        }
      } catch (e) {
        console.warn('AI出题失败:', e)
      }
    }

    // 演示模式或AI出题失败时，使用预设题目
    const demoQuestions = generateDemoQuestions(practiceType)
    questions.value = demoQuestions.map((q, i) => ({ ...q, id: i + 1 }))
    return
  }

  // 其他章节 - 先尝试从后端获取对应章节的题目
  if (!demoStore.enabled) {
    try {
      const res = await api.get('/practice/targeted/' + userStore.id + '/next?knowledgePoint=' + encodeURIComponent(chapterName.value))
      if (res.code === 200 && res.data) {
        const d = res.data
        const qList = d.questions || (d.question ? [d] : [])
        if (qList.length > 0) {
          questions.value = qList.map((q, i) => ({
            id: i + 1,
            question: q.question || '',
            options: q.options || [],
            correctIndex: q.answer != null ? q.answer : 0,
            knowledgePoint: q.knowledgePoint || d.weakPoint || chapterName.value,
            analysis: q.explanation || ''
          }))
          return
        }
      }
    } catch {}
  }

  // 使用demo数据，根据章节名称匹配（支持主分类映射）
  const chapterToKey = {
    '数组与指针': ['数组', '指针'],
    '基础语法': ['基础语法'],
    '流程控制': ['控制结构'],
    '函数': ['函数'],
    '结构体': ['结构体'],
    '文件操作': ['文件操作'],
  }
  const keys = chapterToKey[chapterName.value] || [chapterName.value]
  // 从题库中找到匹配的题目
  let chapterQuestions = []
  for (const key of keys) {
    if (DEMO_QUESTIONS[key]) {
      chapterQuestions = chapterQuestions.concat(DEMO_QUESTIONS[key])
    }
  }
  // 如果没找到，使用指针的题目
  if (chapterQuestions.length === 0) {
    chapterQuestions = DEMO_QUESTIONS['指针'] || []
  }
  questions.value = chapterQuestions.map((q, i) => ({ ...q, id: i + 1 }))
}

// 生成演示模式下的题目（根据用户导学选择动态生成）
function generateDemoQuestions(type) {
  // 从用户画像获取薄弱点
  const profile = JSON.parse(localStorage.getItem('demo_profile') || '{}')
  const weakAreas = (profile.weakAreas || '指针,数组,函数').split('、')

  // 扩展题库 - 每个知识点有多道题
  const allQuestions = {
    '基础语法': [
      { question: '以下哪个是C语言的正确注释方式？', options: ['A. // 这是注释', 'B. /* 这是注释 */', 'C. -- 这是注释', 'D. A和B都正确'], correctIndex: 3, knowledgePoint: '基础语法', analysis: 'C语言支持单行注释//和多行注释/* */。' },
      { question: '以下哪个是合法的变量名？', options: ['A. 2name', 'B. _name', 'C. my-name', 'D. my name'], correctIndex: 1, knowledgePoint: '基础语法', analysis: '变量名可以以字母或下划线开头，不能以数字开头，不能包含空格或连字符。' },
      { question: '以下哪个是C语言的关键字？', options: ['A. printf', 'B. scanf', 'C. int', 'D. main'], correctIndex: 2, knowledgePoint: '基础语法', analysis: 'int是C语言的关键字，用于声明整型变量。printf、scanf是函数名。' },
      { question: '以下哪个运算符用于取余？', options: ['A. /', 'B. %', 'C. //', 'D. **'], correctIndex: 1, knowledgePoint: '基础语法', analysis: '%是取余运算符。' },
      { question: 'sizeof运算符返回什么？', options: ['A. 变量的值', 'B. 变量占用的字节数', 'C. 变量的地址', 'D. 变量的类型'], correctIndex: 1, knowledgePoint: '基础语法', analysis: 'sizeof返回变量或类型占用的字节数。' },
      { question: '以下哪个不是C语言的基本数据类型？', options: ['A. int', 'B. float', 'C. string', 'D. char'], correctIndex: 2, knowledgePoint: '基础语法', analysis: 'C语言没有string类型，字符串用字符数组表示。' },
    ],
    '指针': [
      { question: '指针变量存储的是什么？', options: ['A. 变量的值', 'B. 变量的地址', 'C. 变量的类型', 'D. 变量的名称'], correctIndex: 1, knowledgePoint: '指针', analysis: '指针变量存储的是其他变量的内存地址。' },
      { question: '如何获取变量a的地址？', options: ['A. *a', 'B. &a', 'C. a', 'D. #a'], correctIndex: 1, knowledgePoint: '指针', analysis: '&是取地址运算符，&a返回变量a的内存地址。' },
      { question: '指针p指向变量a，以下哪个表达式等价于a？', options: ['A. p', 'B. &p', 'C. *p', 'D. **p'], correctIndex: 2, knowledgePoint: '指针', analysis: '*p是解引用操作，p指向a，所以*p就是a的值。' },
      { question: '若有定义 int *p, a=10; p=&a; 则 *p 的值是？', options: ['A. p的地址', 'B. a的地址', 'C. 10', 'D. 无法确定'], correctIndex: 2, knowledgePoint: '指针', analysis: 'p指向a，*p就是a的值10。' },
      { question: '以下哪个是正确的指针声明？', options: ['A. int p*', 'B. *int p', 'C. int *p', 'D. pointer int p'], correctIndex: 2, knowledgePoint: '指针', analysis: 'C语言中指针声明格式为：类型 *指针名。' },
      { question: '指针变量p指向a，*p=20后，a的值变为？', options: ['A. 原来的值', 'B. 20', 'C. p的地址', 'D. 无法确定'], correctIndex: 1, knowledgePoint: '指针', analysis: '*p=20修改了p指向的内存地址的值，即a的值。' },
      { question: '以下代码的输出是什么？\nint a = 10;\nint *p = &a;\n*p = 20;\nprintf("%d", a);', options: ['A. 10', 'B. 20', 'C. 编译错误', 'D. 随机值'], correctIndex: 1, knowledgePoint: '指针', analysis: '通过指针修改了a的值，所以a变为20。' },
      { question: '二级指针是指？', options: ['A. 指向数组的指针', 'B. 指向指针的指针', 'C. 两个指针相加', 'D. 指针的别名'], correctIndex: 1, knowledgePoint: '指针', analysis: '二级指针是指向指针的指针，声明格式如 int **pp。' },
      { question: '对于 int *p, *q; 以下哪个操作是合法的？', options: ['A. p + q', 'B. p - q', 'C. p * q', 'D. p / q'], correctIndex: 1, knowledgePoint: '指针', analysis: '两个指针可以相减，得到它们之间的元素个数。' },
      { question: '指针运算 p+1 实际上地址增加了多少？', options: ['A. 1字节', 'B. 指向类型的大小', 'C. 4字节', 'D. 8字节'], correctIndex: 1, knowledgePoint: '指针', analysis: '指针运算的步长取决于指向类型的大小。int*加1增加4字节。' },
    ],
    '数组': [
      { question: '定义 int arr[5] = {1,2,3,4,5}; 则 arr[2] 的值是？', options: ['A. 1', 'B. 2', 'C. 3', 'D. 4'], correctIndex: 2, knowledgePoint: '数组', analysis: 'C语言数组下标从0开始，arr[0]=1, arr[1]=2, arr[2]=3。' },
      { question: '数组名代表什么？', options: ['A. 第一个元素', 'B. 数组的首地址', 'C. 数组的大小', 'D. 最后一个元素'], correctIndex: 1, knowledgePoint: '数组', analysis: '数组名代表数组的首地址，即第一个元素的地址。' },
      { question: 'C语言数组的下标从几开始？', options: ['A. 1', 'B. 0', 'C. -1', 'D. 任意'], correctIndex: 1, knowledgePoint: '数组', analysis: 'C语言数组下标从0开始。' },
      { question: '数组作为函数参数时，传递的是？', options: ['A. 数组的副本', 'B. 数组的首地址', 'C. 数组的大小', 'D. 第一个元素的值'], correctIndex: 1, knowledgePoint: '数组', analysis: '数组作为参数传递的是首地址，不是副本。' },
      { question: '字符串结束符是？', options: ['A. \\n', 'B. \\0', 'C. \\t', 'D. 空格'], correctIndex: 1, knowledgePoint: '数组', analysis: 'C语言字符串以\\0作为结束符。' },
      { question: '以下哪个是正确的二维数组声明？', options: ['A. int arr[3,4]', 'B. int arr[3][4]', 'C. int arr(3)(4)', 'D. int arr{3}{4}'], correctIndex: 1, knowledgePoint: '数组', analysis: 'C语言二维数组声明格式为 类型 数组名[行数][列数]。' },
      { question: 'sizeof(arr) 对于 int arr[10] 返回什么？', options: ['A. 10', 'B. 40', 'C. 4', 'D. 8'], correctIndex: 1, knowledgePoint: '数组', analysis: 'sizeof(arr)返回整个数组的大小，10个int共40字节。' },
      { question: '数组 arr[5] 的合法下标范围是？', options: ['A. 1-5', 'B. 0-4', 'C. 0-5', 'D. 1-4'], correctIndex: 1, knowledgePoint: '数组', analysis: '数组下标从0开始，arr[5]的合法下标是0到4。' },
      { question: '以下哪个操作可以正确初始化数组？', options: ['A. int arr[3] = {1,2,3,4}', 'B. int arr[] = {1,2,3}', 'C. int arr[3] = {1,2}', 'D. B和C都正确'], correctIndex: 3, knowledgePoint: '数组', analysis: 'B省略大小由编译器推断，C未初始化的元素自动补0。' },
      { question: '字符数组和字符串的区别是？', options: ['A. 没有区别', 'B. 字符串以\\0结尾', 'C. 字符串更长', 'D. 字符数组不能存储字符'], correctIndex: 1, knowledgePoint: '数组', analysis: '字符串是特殊的字符数组，以\\0作为结束标志。' },
    ],
    '函数': [
      { question: '以下函数声明正确的是？', options: ['A. int func(int x, int y)', 'B. func(int x, int y)', 'C. int func(int x, y)', 'D. int func[x,y]'], correctIndex: 0, knowledgePoint: '函数', analysis: '函数声明需要返回类型+函数名+参数列表。' },
      { question: '函数的返回类型可以省略吗？', options: ['A. 可以', 'B. 不可以', 'C. 只有void可以', 'D. 取决于编译器'], correctIndex: 1, knowledgePoint: '函数', analysis: 'C语言函数必须明确声明返回类型。' },
      { question: '递归函数必须有？', options: ['A. 循环', 'B. 基准情况', 'C. 指针', 'D. 全局变量'], correctIndex: 1, knowledgePoint: '函数', analysis: '递归函数必须有基准情况（终止条件），否则会无限递归。' },
      { question: '以下哪个是正确的函数指针声明？', options: ['A. int (*fp)(int)', 'B. int *fp(int)', 'C. (int)fp(int)', 'D. fp: int(int)'], correctIndex: 0, knowledgePoint: '函数', analysis: '函数指针声明格式：返回类型 (*指针名)(参数列表)。' },
    ],
    '结构体': [
      { question: '以下定义结构体的语法正确的是？', options: ['A. struct Student { char name[20]; int age; };', 'B. struct { char name[20]; int age; }', 'C. Student { char name[20]; int age; };', 'D. define struct Student { char name[20]; int age; }'], correctIndex: 0, knowledgePoint: '结构体', analysis: '标准的结构体定义语法：struct 结构体名 { 成员列表 };' },
      { question: '如何访问结构体变量s的成员name？', options: ['A. s->name', 'B. s.name', 'C. s[0].name', 'D. (*s).name'], correctIndex: 1, knowledgePoint: '结构体', analysis: '使用结构体变量时用.运算符访问成员。' },
      { question: '结构体指针访问成员用什么运算符？', options: ['A. .', 'B. ->', 'C. *', 'D. &'], correctIndex: 1, knowledgePoint: '结构体', analysis: '结构体指针用->运算符访问成员。' },
      { question: '如何访问结构体成员？', options: ['A. 使用 -> 运算符', 'B. 使用 . 运算符', 'C. 使用 [] 运算符', 'D. 使用 * 运算符'], correctIndex: 1, knowledgePoint: '结构体', analysis: '使用结构体变量时用.运算符。' },
    ],
    '内存管理': [
      { question: '动态分配内存后必须调用哪个函数释放？', options: ['A. delete()', 'B. release()', 'C. free()', 'D. dispose()'], correctIndex: 2, knowledgePoint: '内存管理', analysis: 'malloc分配的内存必须用free()释放。' },
      { question: 'malloc函数的头文件是？', options: ['A. stdio.h', 'B. stdlib.h', 'C. string.h', 'D. math.h'], correctIndex: 1, knowledgePoint: '内存管理', analysis: 'malloc、free等动态内存分配函数声明在stdlib.h中。' },
      { question: '以下哪个函数可以动态分配内存？', options: ['A. malloc()', 'B. alloc()', 'C. new()', 'D. create()'], correctIndex: 0, knowledgePoint: '内存管理', analysis: 'malloc是C语言的动态内存分配函数。' },
      { question: '内存泄漏是什么？', options: ['A. 内存被破坏', 'B. 分配的内存未释放', 'C. 内存不够', 'D. 内存速度慢'], correctIndex: 1, knowledgePoint: '内存管理', analysis: '内存泄漏是分配的内存没有被释放，导致内存浪费。' },
    ],
    '控制结构': [
      { question: 'C语言中，break语句可以用于？', options: ['A. 只能用于循环', 'B. 只能用于switch', 'C. 用于循环和switch', 'D. 用于任何地方'], correctIndex: 2, knowledgePoint: '控制结构', analysis: 'break语句可以跳出循环和switch语句。' },
      { question: '以下哪个循环结构会至少执行一次循环体？', options: ['A. for循环', 'B. while循环', 'C. do-while循环', 'D. 所有循环'], correctIndex: 2, knowledgePoint: '控制结构', analysis: 'do-while循环先执行循环体，再判断条件，所以至少执行一次。' },
      { question: 'switch语句中，如果没有break会怎样？', options: ['A. 程序出错', 'B. 跳出switch', 'C. 继续执行下一个case', 'D. 返回main函数'], correctIndex: 2, knowledgePoint: '控制结构', analysis: '没有break会继续执行下一个case的代码。' },
      { question: 'continue语句的作用是？', options: ['A. 跳出循环', 'B. 跳过本次循环剩余部分', 'C. 终止程序', 'D. 返回函数'], correctIndex: 1, knowledgePoint: '控制结构', analysis: 'continue跳过本次循环的剩余部分，继续下一次循环。' },
    ],
  }

  // 根据薄弱点选择题目，优先选择薄弱点相关的题目
  let selectedQuestions = []
  const usedIndices = new Set()

  // 先从薄弱点相关的题目中选择
  for (const weak of weakAreas) {
    const topic = weak.trim()
    if (allQuestions[topic] && selectedQuestions.length < 10) {
      const available = allQuestions[topic].filter((_, i) => !usedIndices.has(`${topic}-${i}`))
      // Fisher-Yates 洗牌算法（项目硬约束：禁止 sort+Math.random）
      const shuffled = available.slice()
      for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1))
        ;[shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
      }
      const pick = shuffled.slice(0, Math.min(3, shuffled.length))
      pick.forEach(q => {
        selectedQuestions.push(q)
        const idx = allQuestions[topic].indexOf(q)
        usedIndices.add(`${topic}-${idx}`)
      })
    }
  }

  // 如果题目不够，从其他题目中补充
  const allTopics = Object.keys(allQuestions)
  while (selectedQuestions.length < 10) {
    const randomTopic = allTopics[Math.floor(Math.random() * allTopics.length)]
    const questions = allQuestions[randomTopic]
    const availableIndices = questions.map((_, i) => i).filter(i => !usedIndices.has(`${randomTopic}-${i}`))
    if (availableIndices.length > 0) {
      const randomIdx = availableIndices[Math.floor(Math.random() * availableIndices.length)]
      selectedQuestions.push(questions[randomIdx])
      usedIndices.add(`${randomTopic}-${randomIdx}`)
    } else {
      // 所有题目都用过了，跳出
      break
    }
  }

  // 随机打乱题目顺序（Fisher-Yates 洗牌算法，项目硬约束）
  const finalQuestions = selectedQuestions.slice()
  for (let i = finalQuestions.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[finalQuestions[i], finalQuestions[j]] = [finalQuestions[j], finalQuestions[i]]
  }
  return finalQuestions.slice(0, 10)
}

onMounted(async () => {
  // 检查是否是查看已完成的答卷
  const viewPaperParam = route.query.viewPaper
  const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'

  // 演示模式：清除旧的session数据，确保每次都是新题
  if (isDemo && viewPaperParam !== 'true') {
    const sessionKey = 'cryd_session_practice_' + chapterName.value
    localStorage.removeItem(sessionKey)
  }

  // 优先检查当前会话的session数据（有完整的questions和results）
  const sessionKey = 'cryd_session_practice_' + chapterName.value
  const sessionData = localStorage.getItem(sessionKey)
  if (sessionData) {
    try {
      const data = JSON.parse(sessionData)
      if (data.completed && data.questions && data.results && data.questions.length > 0) {
        questions.value = data.questions
        questionResults.value = data.results
        showResult.value = true
        showPaper.value = true
        return
      }
    } catch(e) { console.error('加载session数据失败:', e) }
  }

  // 如果session没有，检查history数据
  if (viewPaperParam === 'true') {
    const history = JSON.parse(localStorage.getItem('cryd_practice_history') || '[]')
    if (history.length > 0) {
      const chapterRecord = history.find(h => h.chapterName === chapterName.value)
      if (chapterRecord && chapterRecord.results && chapterRecord.results.length > 0) {
        await loadQuestions()
        questionResults.value = chapterRecord.results
        showResult.value = true
        showPaper.value = true
        return
      }
    }
  }

  loadQuestions()
  startTimer()
})

onBeforeUnmount(() => {
  stopTimer()
})
</script>

<style scoped>
.practice-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%);
}

/* 顶部导航栏 */
.top-navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 28px;
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(0,0,0,0.06);
  flex-shrink: 0;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
}
.nav-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: linear-gradient(145deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.back-btn:hover {
  background: linear-gradient(145deg, #f1f5f9 0%, #e2e8f0 100%);
  border-color: #cbd5e1;
  transform: translateX(-2px);
}
.quiz-title {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #1e293b 0%, #6366f1 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.timer-display {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: linear-gradient(145deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  font-variant-numeric: tabular-nums;
  border: 1px solid #e2e8f0;
}
.progress-info {
  padding: 10px 16px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

/* 主内容区 */
.main-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧题目导航 */
.sidebar-nav {
  width: 220px;
  background: rgba(255,255,255,0.95);
  backdrop-filter: blur(12px);
  border-right: 1px solid rgba(0,0,0,0.06);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-header {
  padding: 18px;
  border-bottom: 1px solid rgba(0,0,0,0.06);
}
.sidebar-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 14px;
}
.legend {
  display: flex;
  gap: 14px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.legend-dot.current { background: linear-gradient(145deg, #6366f1 0%, #8b5cf6 100%); }
.legend-dot.correct { background: linear-gradient(145deg, #10b981 0%, #059669 100%); }
.legend-dot.wrong { background: linear-gradient(145deg, #ef4444 0%, #dc2626 100%); }

.question-nav-grid {
  flex: 1;
  padding: 14px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  align-content: start;
  overflow-y: auto;
}
.nav-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 10px;
  background: linear-gradient(145deg, #f8fafc 0%, #f1f5f9 100%);
  border: 2px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.nav-btn:hover {
  background: linear-gradient(145deg, #f1f5f9 0%, #e2e8f0 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}
.nav-btn.current {
  border-color: #6366f1;
  background: linear-gradient(145deg, #eef2ff 0%, #e0e7ff 100%);
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.25);
}
.nav-btn.answered {
  background: linear-gradient(145deg, #f0f9ff 0%, #e0f2fe 100%);
  border-color: #0ea5e9;
}
.nav-btn-number {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}
.nav-btn-status {
  font-size: 11px;
  color: #64748b;
  margin-top: 3px;
  font-weight: 500;
}

/* 右侧答题区 */
.content-area {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  padding: 28px;
  overflow-y: auto;
}
.question-container {
  width: 100%;
  animation: slideIn 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.question-card {
  background: rgba(255,255,255,0.95);
  backdrop-filter: blur(12px);
  border-radius: 20px;
  padding: 36px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.08);
  border: 1px solid rgba(255,255,255,0.8);
}
.question-header {
  margin-bottom: 28px;
}
.question-number {
  font-size: 14px;
  font-weight: 700;
  color: #6366f1;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.question-number::before {
  content: '';
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 2px;
}
.q-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.tag {
  padding: 5px 14px;
  background: linear-gradient(145deg, #f1f5f9 0%, #e2e8f0 100%);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}
.tag-info {
  background: linear-gradient(145deg, #eef2ff 0%, #e0e7ff 100%);
  color: #6366f1;
}
.question-text {
  font-size: 19px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.7;
}

/* 选项 */
.options-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  margin-bottom: 28px;
}
.option-btn {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: linear-gradient(145deg, #f8fafc 0%, #f1f5f9 100%);
  border: 2px solid #e2e8f0;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}
.option-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
  opacity: 0;
  transition: opacity 0.3s;
}
.option-btn:hover:not(.correct):not(.wrong) {
  border-color: #6366f1;
  transform: translateX(4px);
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.15);
}
.option-btn:hover:not(.correct):not(.wrong)::before {
  opacity: 1;
}
.option-btn.selected {
  border-color: #6366f1;
  background: linear-gradient(145deg, #eef2ff 0%, #e0e7ff 100%);
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.25);
}
.option-btn.correct {
  border-color: #10b981;
  background: linear-gradient(145deg, #f0fdf4 0%, #dcfce7 100%);
  box-shadow: 0 4px 20px rgba(16, 185, 129, 0.25);
  animation: correctPulse 0.5s ease;
}
.option-btn.wrong {
  border-color: #ef4444;
  background: linear-gradient(145deg, #fef2f2 0%, #fee2e2 100%);
  box-shadow: 0 4px 20px rgba(239, 68, 68, 0.25);
  animation: wrongShake 0.5s ease;
}

@keyframes correctPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.02); }
}

@keyframes wrongShake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-8px); }
  75% { transform: translateX(8px); }
}

.option-label {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(145deg, #e2e8f0 0%, #cbd5e1 100%);
  color: #64748b;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 700;
  flex-shrink: 0;
  transition: all 0.25s;
}
.option-btn.selected .option-label {
  background: linear-gradient(145deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
}
.option-btn.correct .option-label {
  background: linear-gradient(145deg, #10b981 0%, #059669 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.4);
}
.option-btn.wrong .option-label {
  background: linear-gradient(145deg, #ef4444 0%, #dc2626 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.4);
}
.option-text {
  flex: 1;
  font-size: 16px;
  color: #334155;
  line-height: 1.5;
}
.option-icon {
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
}
.icon-correct { color: #10b981; }
.icon-wrong { color: #ef4444; }
.icon-selected { color: #6366f1; }

/* 解析 */
.analysis-section {
  background: linear-gradient(145deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  padding: 18px 22px;
  margin-bottom: 22px;
  border: 1px solid #e2e8f0;
}
.analysis-header {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 10px;
}
.analysis-header.correct { color: #10b981; }
.analysis-header.wrong { color: #ef4444; }
.analysis-body {
  font-size: 14px;
  color: #475569;
  line-height: 1.7;
}

/* 底部按钮 */
.question-footer {
  display: flex;
  justify-content: flex-end;
}
.next-btn {
  padding: 14px 28px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  border-radius: 12px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);
}
.next-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(99, 102, 241, 0.45);
}
.next-btn:active {
  transform: translateY(0);
}

/* 正在生成页面 */
.generating-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.7);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}

.generating-card {
  background: white;
  border-radius: 20px;
  padding: 40px;
  text-align: center;
  max-width: 400px;
  width: 90%;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.generating-icon {
  margin-bottom: 20px;
}

.spinner {
  width: 60px;
  height: 60px;
  border: 4px solid #e5e7eb;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.generating-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 24px;
}

.generating-steps {
  text-align: left;
  margin-bottom: 24px;
}

.generating-step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  color: #9ca3af;
  transition: all 0.3s ease;
}

.generating-step.active {
  color: #3b82f6;
  font-weight: 500;
}

.generating-step.done {
  color: #10b981;
}

.step-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.step-text {
  font-size: 14px;
}

.generating-progress {
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  border-radius: 3px;
  transition: width 0.5s ease;
}

/* 结果弹窗 */
.result-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.7);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.result-card {
  background: rgba(255,255,255,0.98);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 44px;
  max-width: 500px;
  width: 90%;
  text-align: center;
  box-shadow: 0 24px 80px rgba(0,0,0,0.2);
  animation: slideUp 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(255,255,255,0.8);
  position: relative;
}
.result-close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(145deg, #f1f5f9 0%, #e2e8f0 100%);
  color: #64748b;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}
.result-close-btn:hover {
  background: linear-gradient(145deg, #e2e8f0 0%, #cbd5e1 100%);
  color: #475569;
  transform: rotate(90deg);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(40px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.result-icon {
  width: 90px;
  height: 90px;
  margin: 0 auto 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes popIn {
  0% { transform: scale(0); }
  100% { transform: scale(1); }
}

.result-icon.excellent {
  background: linear-gradient(145deg, #d1fae5 0%, #a7f3d0 100%);
  color: #10b981;
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.35);
}
.result-icon.good {
  background: linear-gradient(145deg, #fef3c7 0%, #fde68a 100%);
  color: #f59e0b;
  box-shadow: 0 8px 24px rgba(245, 158, 11, 0.35);
}
.result-icon.fail {
  background: linear-gradient(145deg, #fee2e2 0%, #fecaca 100%);
  color: #ef4444;
  box-shadow: 0 8px 24px rgba(239, 68, 68, 0.35);
}
.result-title {
  font-size: 26px;
  font-weight: 800;
  margin: 0 0 8px;
  color: #1e293b;
}
.result-subtitle {
  font-size: 15px;
  color: #64748b;
  margin: 0 0 28px;
}

/* 分数环 */
.score-display {
  margin-bottom: 28px;
}
.score-circle {
  position: relative;
  width: 140px;
  height: 140px;
  margin: 0 auto;
}
.score-circle svg {
  transform: rotate(-90deg);
}
.score-bg {
  fill: none;
  stroke: #e2e8f0;
  stroke-width: 10;
}
.score-progress {
  fill: none;
  stroke-width: 10;
  stroke-linecap: round;
  stroke-dasharray: 282.74;
  transition: stroke-dashoffset 1.5s cubic-bezier(0.4, 0, 0.2, 1);
  filter: drop-shadow(0 4px 8px rgba(0,0,0,0.15));
}
.score-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
}
.score-number {
  font-size: 40px;
  font-weight: 800;
  background: linear-gradient(135deg, #1e293b 0%, #6366f1 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.score-unit {
  font-size: 14px;
  color: #64748b;
  font-weight: 600;
}

/* 统计 */
.result-stats {
  display: flex;
  justify-content: center;
  gap: 40px;
  margin-bottom: 28px;
  padding: 20px 0;
  border-top: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
}
.stat-item {
  text-align: center;
}
.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 6px;
  font-weight: 500;
}
.stat-value {
  font-size: 24px;
  font-weight: 800;
  color: #1e293b;
}
.stat-value.correct {
  background: linear-gradient(145deg, #10b981 0%, #059669 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.stat-value.wrong {
  background: linear-gradient(145deg, #ef4444 0%, #dc2626 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 资源生成提示 */
.resource-generated-notice {
  background: linear-gradient(145deg, #f0fdf4 0%, #dcfce7 100%);
  border: 1px solid #bbf7d0;
  border-radius: 14px;
  padding: 18px;
  margin: 24px 0;
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.notice-icon {
  font-size: 28px;
  flex-shrink: 0;
}
.notice-content h4 {
  font-size: 15px;
  font-weight: 700;
  color: #166534;
  margin: 0 0 6px;
}
.notice-content p {
  font-size: 13px;
  color: #15803d;
  margin: 0 0 10px;
  line-height: 1.5;
}
.resource-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.res-tag {
  font-size: 12px;
  padding: 5px 10px;
  background: rgba(255,255,255,0.8);
  border-radius: 8px;
  color: #166534;
  border: 1px solid #bbf7d0;
  font-weight: 500;
}

/* 操作按钮 */
.result-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}
.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 22px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  border: none;
}
.action-btn.primary {
  background: linear-gradient(145deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);
}
.action-btn.primary:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.45);
}
.action-btn.go-home {
  background: linear-gradient(145deg, #10b981 0%, #059669 100%);
  color: #fff;
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.35);
}
.action-btn.go-home:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(16, 185, 129, 0.45);
}
.action-btn.secondary {
  background: linear-gradient(145deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  color: #475569;
}
.action-btn.secondary:hover {
  background: linear-gradient(145deg, #f1f5f9 0%, #e2e8f0 100%);
  transform: translateY(-2px);
}

/* 试卷查看 */
.paper-overlay {
  position: fixed;
  inset: 0;
  background: linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%);
  z-index: 1000;
  overflow-y: auto;
  animation: fadeIn 0.3s ease;
}
.paper-navbar {
  position: sticky;
  top: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 28px;
  background: rgba(255,255,255,0.95);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(0,0,0,0.06);
  z-index: 10;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
}
.paper-title {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #1e293b 0%, #6366f1 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.paper-legend {
  display: flex;
  gap: 18px;
  font-size: 14px;
}
.legend-correct { color: #10b981; font-weight: 600; }
.legend-wrong { color: #ef4444; font-weight: 600; }

.paper-content {
  width: 100%;
  margin: 28px 0;
  padding: 0 28px;
  box-sizing: border-box;
}
.paper-question {
  background: rgba(255,255,255,0.95);
  backdrop-filter: blur(12px);
  border-radius: 16px;
  padding: 28px;
  margin-bottom: 18px;
  border-left: 5px solid;
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
  transition: all 0.25s;
}
.paper-question:hover {
  transform: translateX(4px);
  box-shadow: 0 6px 24px rgba(0,0,0,0.1);
}
.paper-question.correct { border-color: #10b981; }
.paper-question.wrong { border-color: #ef4444; }

.paper-q-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.paper-q-number {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}
.paper-q-status {
  font-size: 13px;
  font-weight: 600;
}
.paper-q-status.correct { color: #10b981; }
.paper-q-status.wrong { color: #ef4444; }

.paper-q-text {
  font-size: 17px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.7;
  margin-bottom: 18px;
}
.paper-options {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-bottom: 18px;
}
.paper-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(145deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  transition: all 0.2s;
}
.paper-option.correct-answer {
  background: linear-gradient(145deg, #f0fdf4 0%, #dcfce7 100%);
  border-color: #10b981;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.15);
}
.paper-option.wrong-answer {
  background: linear-gradient(145deg, #fef2f2 0%, #fee2e2 100%);
  border-color: #ef4444;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.15);
}
.paper-opt-label {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(145deg, #e2e8f0 0%, #cbd5e1 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #64748b;
  flex-shrink: 0;
}
.paper-option.correct-answer .paper-opt-label {
  background: linear-gradient(145deg, #10b981 0%, #059669 100%);
  color: #fff;
}
.paper-option.wrong-answer .paper-opt-label {
  background: linear-gradient(145deg, #ef4444 0%, #dc2626 100%);
  color: #fff;
}
.paper-opt-text {
  font-size: 15px;
  color: #334155;
  line-height: 1.5;
}
.paper-analysis {
  background: linear-gradient(145deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 10px;
  padding: 14px 18px;
  border: 1px solid #e2e8f0;
}
.analysis-label {
  font-size: 13px;
  font-weight: 700;
  color: #6366f1;
  margin-bottom: 6px;
}
.analysis-text {
  font-size: 14px;
  color: #475569;
  line-height: 1.6;
}
</style>
