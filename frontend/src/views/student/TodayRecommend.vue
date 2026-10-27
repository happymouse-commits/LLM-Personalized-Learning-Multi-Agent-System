<template>
  <div class="today-recommend">
    <!-- 页面头部 -->
    <div class="page-header">
      <h1 class="page-title">🎯 今日推荐</h1>
      <p class="page-subtitle">根据你的学习情况，为你推荐以下内容</p>
    </div>

    <!-- 练习题区域 -->
    <section class="recommend-section">
      <h2 class="section-title">📝 练习题</h2>
      <div class="recommend-scroll-wrapper">
        <div class="recommend-grid">
        <div
          v-for="item in recommendItems"
          :key="item.id"
          class="recommend-card"
          :class="{ completed: isCompleted(item.type) }"
          @click="handleRecommendClick(item)"
        >
          <div class="card-icon" :style="{ background: item.iconBg }">
            <span>{{ item.icon }}</span>
          </div>
          <div class="card-content">
            <h3 class="card-title">{{ item.title }}</h3>
            <p class="card-desc">{{ item.desc }}</p>
            <div v-if="isCompletedToday(item.type)" class="completed-score">
              得分：<span :style="{ color: getScoreColor(getScore(item.type)) }">{{ getScore(item.type) }}</span> 分
            </div>
          </div>
          <div class="card-action">
            <span class="action-btn" :class="{ 'done': isCompletedToday(item.type) }">
              开始学习
            </span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M5 12h14M12 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>
      </div>
      </div>
    </section>

    <!-- 学习资料区域（每种类型一份） -->
    <section class="resource-section">
      <div class="section-header">
        <h2 class="section-title">📚 学习资料</h2>
        <div class="header-actions">
          <span v-if="generatingPpt" class="ppt-generating-tip">
            <span class="loading-spinner"></span>
            {{ generatingProgress || '正在生成资料...' }}
          </span>
          <button v-else class="generate-btn" @click="showGenerateModal = true">
            生成资源
          </button>
        </div>
      </div>
      <div class="filter-tabs">
        <button
          v-for="tab in resourceTabs"
          :key="tab.id"
          :class="['filter-tab', { active: activeTab === tab.id }]"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
        </button>
      </div>
      <div class="resource-grid">
        <div
          v-for="resource in filteredResources"
          :key="resource.id"
          :id="'resource-' + resource.id"
          :class="['resource-card', { 'highlighted': highlightedResourceId === resource.id }]"
          @click="handleResourceClick(resource)"
        >
          <!-- 封面区域（HTML风格） -->
          <div class="tr-cover-area">
            <!-- VS Code 代码封面 -->
            <div v-if="resource.type === 'code'" class="cover-vscode">
              <div class="vs-titlebar">
                <div class="vs-tab active"><span class="vs-icon">📄</span> main.c</div>
              </div>
              <div class="vs-editor">
                <div><span class="vs-line-num">1</span><span class="vs-pp">#include</span> <span class="vs-inc">&lt;stdio.h&gt;</span></div>
                <div><span class="vs-line-num">2</span></div>
                <div><span class="vs-line-num">3</span><span class="vs-ty">int</span> <span class="vs-fn">main</span><span class="vs-op">() {</span></div>
                <div><span class="vs-line-num">4</span>    <span class="vs-fn">printf</span><span class="vs-op">(</span><span class="vs-str">"Hello, World!\n"</span><span class="vs-op">);</span></div>
                <div><span class="vs-line-num">5</span>    <span class="vs-kw">return</span> <span class="vs-num">0</span><span class="vs-op">;</span></div>
                <div><span class="vs-line-num">6</span><span class="vs-op">}</span></div>
              </div>
              <div class="vs-overlay"></div>
              <div class="vs-badge">{{ resource.icon }} {{ resource.typeLabel }}</div>
            </div>

            <!-- PPT课件封面（直接显示照片） -->
            <div v-else-if="resource.type === 'course-ppt'" class="cover-ppt">
              <img
                v-if="resource.coverImage"
                :src="resource.coverImage"
                class="ppt-slide-img"
                alt="PPT预览"
                @error="onCoverImageError(resource)"
                loading="lazy"
              />
              <div v-else class="cover-book" :style="{ background: resource.coverBg }">
                <div class="book-icon">📑</div>
                <div class="book-title">{{ resource.title }}</div>
              </div>
              <div class="vs-overlay"></div>
              <div class="vs-badge">{{ resource.icon }} {{ resource.typeLabel }}</div>
            </div>

            <!-- 书籍封面（文章/讲解） -->
            <div v-else-if="resource.type === 'article' || resource.type === 'explanation'" class="cover-book" :style="{ background: resource.coverBg }">
              <div class="book-icon">{{ resource.icon }}</div>
              <div class="book-title">{{ resource.title }}</div>
              <div class="book-sub">{{ resource.chapter }} · {{ resource.level || '基础' }}</div>
              <div class="book-byline">C语言程序设计</div>
              <div class="vs-overlay"></div>
              <div class="vs-badge">{{ resource.icon }} {{ resource.typeLabel }}</div>
            </div>

            <!-- 视频封面 -->
            <div v-else-if="resource.type === 'video' || resource.type === 'course-video'" class="cover-book" :style="{ background: resource.coverBg }">
              <div class="book-icon">🎬</div>
              <div class="book-title">{{ resource.title }}</div>
              <div class="book-sub">{{ resource.chapter }} · 15:00</div>
              <div class="vs-overlay"></div>
              <div class="vs-badge">{{ resource.icon }} {{ resource.typeLabel }}</div>
            </div>

            <!-- 思维导图封面 -->
            <div v-else-if="resource.type === 'mindmap'" class="cover-mindmap">
              <div class="mindmap-preview">
                <div class="mindmap-center">{{ resource.chapter || 'C语言' }}</div>
                <div class="mindmap-branches">
                  <div v-for="(branch, i) in ['语法', '函数', '数组', '指针', '结构体', '文件']" :key="i" class="mindmap-branch" :style="{ transform: `rotate(${i * 60}deg)` }">
                    <div class="branch-line"></div>
                    <div class="branch-node">{{ branch }}</div>
                  </div>
                </div>
              </div>
              <div class="vs-overlay"></div>
              <div class="vs-badge">{{ resource.icon }} {{ resource.typeLabel }}</div>
            </div>

            <!-- 笔记本封面（总结） -->
            <div v-else-if="resource.type === 'summary'" class="cover-notebook">
              <div class="nb-title">📋 {{ resource.title }}</div>
              <ul class="nb-list">
                <li>struct 定义与初始化</li>
                <li>点运算符 . 访问成员</li>
                <li>箭头运算符 -> 指针访问</li>
                <li>typedef 起别名</li>
              </ul>
              <div class="vs-overlay"></div>
              <div class="vs-badge">{{ resource.icon }} {{ resource.typeLabel }}</div>
            </div>

            <!-- 默认封面 -->
            <div v-else class="cover-book" :style="{ background: resource.coverBg }">
              <div class="book-icon">{{ resource.icon }}</div>
              <div class="book-title">{{ resource.chapter }}</div>
              <div class="book-sub">{{ resource.typeLabel }}</div>
              <div class="vs-overlay"></div>
              <div class="vs-badge">{{ resource.icon }} {{ resource.typeLabel }}</div>
            </div>
          </div>

          <!-- 卡片内容区 -->
          <div class="tr-card-body">
            <h4 class="tr-card-title">{{ resource.title }}</h4>
            <div class="tr-card-tags">
              <span class="tr-tag" :class="'tag-' + resource.type">{{ resource.icon }} {{ resource.typeLabel }}</span>
              <span class="tr-tag tag-chapter">{{ resource.chapter }}</span>
              <span class="tr-score"><span class="score-dot"></span> 95%</span>
            </div>
            <!-- 听学笔记卡片：显示文稿预览 -->
            <div v-if="resource.type === 'blog'" class="blog-preview">
              <span class="blog-hint">🎧 点击查看全文 + 听音频</span>
            </div>
            <HallucinationBadge
              v-if="resource.hallucinationScore != null"
              :score="resource.hallucinationScore"
              :risk="resource.hallucinationRisk"
              :note="resource.hallucinationNote"
              size="small"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 生成资源筛选浮窗 ===== -->
    <teleport to="body">
      <div v-if="showGenerateModal" class="gen-modal-overlay">
        <div class="gen-modal-backdrop" @click="showGenerateModal = false"></div>
        <div class="gen-modal-box">
          <div class="gen-modal-header">
            <div class="gen-modal-header-left">
              <div class="gen-modal-header-icon"></div>
              <span class="gen-modal-title">生成学习资源</span>
            </div>
            <button class="gen-modal-close" @click="showGenerateModal = false">✕</button>
          </div>
          <div class="gen-modal-body">
            <!-- 资源种类（多选） -->
            <div class="gen-filter-group">
              <div class="gen-filter-label"><span class="gen-label-icon">🏷️</span> 资源种类</div>
              <div class="gen-chip-grid">
                <div v-for="t in generateTypeOptions" :key="t.value"
                     :class="['gen-chip', { active: genSelectedTypes.includes(t.value) }]"
                     @click="toggleGenType(t.value)">
                  <span class="gen-chip-check"></span>
                  <span class="gen-chip-icon">{{ t.icon }}</span> {{ t.label }}
                </div>
              </div>
            </div>
            <!-- 薄弱章节（单选） -->
            <div class="gen-filter-group">
              <div class="gen-filter-label"><span class="gen-label-icon">📑</span> 薄弱章节</div>
              <div class="gen-chapter-grid">
                <div v-for="ch in chapterOptions" :key="ch.value"
                     :class="['gen-chapter-chip', { active: genSelectedChapter === ch.value }]"
                     @click="genSelectedChapter = ch.value">
                  {{ ch.label }}
                </div>
              </div>
            </div>
            <!-- 难易程度（单选） -->
            <div class="gen-filter-group">
              <div class="gen-filter-label"><span class="gen-label-icon">🎯</span> 难易程度</div>
              <div class="gen-diff-grid">
                <div v-for="d in diffOptions" :key="d.value"
                     :class="['gen-diff-chip', d.value, { active: genSelectedDiff === d.value }]"
                     @click="genSelectedDiff = d.value">
                  {{ d.icon }} {{ d.label }}
                </div>
              </div>
            </div>
          </div>
          <div class="gen-modal-footer">
            <span class="gen-footer-tip" :style="{ color: genSelectedTypes.length ? '#6366f1' : '#94a3b8' }">
              {{ genSelectedTypes.length ? `将为选中的 ${genSelectedTypes.length} 种类型各生成1份资源` : '请至少选择1种资源类型' }}
            </span>
            <div class="gen-footer-actions">
              <button class="gen-btn-cancel" @click="showGenerateModal = false">取消</button>
              <button class="gen-btn-confirm" :disabled="!genSelectedTypes.length" @click="handleGenerate">
                开始生成 <span class="gen-arrow">▶</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../../store/user'
import { useDemoStore } from '../../store/demo'
import api, { resourceApi } from '../../api'
import HallucinationBadge from '../../components/HallucinationBadge.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const demoStore = useDemoStore()

const activeTab = ref('all')
const highlightedResourceId = ref(null)
const loading = ref(false)
const generatingPpt = ref(false)
const generatingProgress = ref('') // 生成过程中的阶段性提示文案
const audioPlaying = ref(null) // 当前播放的音频资源ID

// ===== 生成资源筛选浮窗 =====
const showGenerateModal = ref(false)
const genSelectedTypes = ref([])
const genSelectedChapter = ref('all')
const genSelectedDiff = ref('medium')

const generateTypeOptions = [
  { value: 'article', icon: '📖', label: '知识精讲' },
  { value: 'mindmap', icon: '🧠', label: '知识导图' },
  { value: 'code', icon: '💻', label: '编程实战' },
  { value: 'exercise', icon: '📝', label: '巩固练习' },
  { value: 'weakness', icon: '❌', label: '错题诊断' },
  { value: 'course-video', icon: '🎬', label: '视频讲解' },
  { value: 'course-ppt', icon: '📚', label: '学习课件' },
  { value: 'blog', icon: '🎧', label: '听学笔记' },
]
const chapterOptions = [
  { value: 'all', label: '全部' },
  { value: '1', label: '基础语法' },
  { value: '2', label: '流程控制' },
  { value: '3', label: '函数' },
  { value: '4', label: '数组' },
  { value: '5', label: '指针' },
  { value: '6', label: '结构体' },
  { value: '7', label: '文件操作' },
]
const diffOptions = [
  { value: 'easy', icon: '🌱', label: '简单' },
  { value: 'medium', icon: '⚡', label: '中等' },
  { value: 'hard', icon: '🔥', label: '困难' },
]

function toggleGenType(val) {
  const idx = genSelectedTypes.value.indexOf(val)
  if (idx >= 0) genSelectedTypes.value.splice(idx, 1)
  else genSelectedTypes.value.push(val)
}

// 检查练习是否已完成
function isCompleted(type) {
  return demoStore.isPracticeCompleted(type)
}

// 检查今天是否已答题
function isCompletedToday(type) {
  const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
  const practiceKey = isDemo ? 'demo_completed_practice' : 'cryd_completed_practice'
  const completedPractice = JSON.parse(localStorage.getItem(practiceKey) || '{}')
  const info = completedPractice[type]
  if (!info || !info.completed || !info.date) return false
  const today = new Date().toISOString().split('T')[0]
  return info.date === today
}

// 获取练习分数
function getScore(type) {
  const info = demoStore.getPracticeCompletedInfo(type)
  return info?.score || 0
}

// 获取分数颜色
function getScoreColor(score) {
  if (score >= 80) return '#10b981'
  if (score >= 60) return '#f59e0b'
  return '#ef4444'
}

// 切换音频播放
function toggleAudio(resource) {
  if (audioPlaying.value === resource.id) {
    audioPlaying.value = null
  } else {
    audioPlaying.value = resource.id
  }
}

// 推荐练习项目
const recommendItems = ref([
  {
    id: 1,
    title: '能力检测',
    desc: '综合测试你的C语言掌握程度',
    icon: '🎯',
    iconBg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    type: 'quiz',
    difficulty: 'medium'
  },
  {
    id: 2,
    title: '巩固练习',
    desc: '针对薄弱知识点的专项训练',
    icon: '💪',
    iconBg: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    type: 'practice',
    difficulty: 'easy'
  },
  {
    id: 3,
    title: '艾宾浩斯复习',
    desc: '基于遗忘曲线的智能复习计划',
    icon: '🧠',
    iconBg: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    type: 'ebbinghaus',
    difficulty: 'medium'
  }
])

// 资源筛选标签（练习题已在上方单独展示，这里只展示学习资料）
const resourceTabs = ref([
  { id: 'all', label: '全部' },
  { id: 'article', label: '知识精讲' },
  { id: 'mindmap', label: '知识导图' },
  { id: 'code', label: '编程实战' },
  { id: 'summary', label: '学习总结' },
  { id: 'course-video', label: '视频讲解' },
  { id: 'ppt', label: '学习课件' },
  { id: 'blog', label: '听学笔记' }
])

// ===== 资源预设：为每种类型预置一份默认资源（系统初始化时展示） =====
const presetResources = [
  { id: 'preset_article', title: 'C语言基础语法入门', type: 'article', typeLabel: '文章讲解', chapter: '基础语法', icon: '文', coverBg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', content: '# C语言基础语法入门\n\n## 一、C语言程序结构\n\n每个C程序都由一个或多个函数组成，其中**必须有一个 `main` 函数**作为程序的入口点。一个最简单的C程序如下：\n\n```c\n#include <stdio.h>\n\nint main() {\n    printf("Hello, World!\\n");\n    return 0;\n}\n```\n\n- `#include <stdio.h>`：预处理器指令，引入标准输入输出头文件\n- `int main()`：主函数，程序从这里开始执行\n- `printf()`：标准输出函数，将内容打印到屏幕\n- `return 0`：表示程序正常退出\n\n## 二、数据类型\n\nC语言提供以下基本数据类型，不同平台上占用字节数可能略有差异：\n\n| 类型 | 关键字 | 占用字节 | 取值范围 | 格式符 |\n|------|--------|----------|----------|--------|\n| 整型 | `int` | 4 | -2,147,483,648 ~ 2,147,483,647 | `%d` |\n| 短整型 | `short` | 2 | -32,768 ~ 32,767 | `%hd` |\n| 长整型 | `long` | 4/8 | 取决于平台 | `%ld` |\n| 单精度浮点 | `float` | 4 | 约 ±3.4e-38 ~ ±3.4e+38 | `%f` |\n| 双精度浮点 | `double` | 8 | 约 ±1.7e-308 ~ ±1.7e+308 | `%lf` |\n| 字符型 | `char` | 1 | -128 ~ 127 或 0 ~ 255 | `%c` |\n\n### 类型修饰符\n- `unsigned`：无符号，将负数范围映射到正数（如 `unsigned int` 范围 0 ~ 4,294,967,295）\n- `signed`：有符号（默认，可省略）\n- `short` / `long`：改变长度\n\n### sizeof 运算符\n用 `sizeof` 获取变量或类型占用的字节数：\n```c\nprintf("int: %zu 字节\\n", sizeof(int));\nprintf("char: %zu 字节\\n", sizeof(char));\nprintf("double: %zu 字节\\n", sizeof(double));\n```\n\n## 三、变量\n\n### 变量的声明与初始化\n```c\nint age;              // 声明但未初始化（值不确定）\nint score = 95;       // 声明并初始化\nfloat pi = 3.14159;\nchar grade = \'A\';\n\n// 多变量同时声明\nint a = 1, b = 2, c = 3;\n```\n\n### 变量命名规则\n- 只能包含字母、数字和下划线 `_`\n- 不能以数字开头\n- 区分大小写（`age` 和 `Age` 是不同的变量）\n- 不能使用C语言关键字（如 `int`、`if`、`return` 等）\n- 建议使用有意义的英文名，如 `studentAge`、`totalScore`\n\n### 变量作用域\n```c\nint global = 100;     // 全局变量：整个文件可见\n\nint main() {\n    int local = 50;   // 局部变量：仅在 main 函数内可见\n    {\n        int block = 10;  // 块级变量：仅在当前代码块内可见\n    }\n    // block 在此处不可见\n    return 0;\n}\n```\n\n## 四、常量\n\n### 方法一：#define 宏常量\n```c\n#define PI 3.1415926\n#define MAX_STUDENTS 50\n```\n- 预处理器在编译前进行文本替换\n- 无类型检查，不分配内存\n- 习惯用大写字母命名\n\n### 方法二：const 常变量\n```c\nconst double PI = 3.1415926;\nconst int MAX_STUDENTS = 50;\n```\n- 有类型检查，分配内存\n- 声明后不可修改\n- 更加安全，推荐使用\n\n## 五、运算符\n\n### 算术运算符\n| 运算符 | 含义 | 示例 | 结果 |\n|--------|------|------|------|\n| `+` | 加法 | `5 + 3` | 8 |\n| `-` | 减法 | `5 - 3` | 2 |\n| `*` | 乘法 | `5 * 3` | 15 |\n| `/` | 除法 | `5 / 2` | 2（整数除法截断） |\n| `%` | 取余（模） | `5 % 2` | 1 |\n\n> **注意**：C语言中整数除以整数得到整数，小数部分被截断。要得到浮点结果，需要将其中一个操作数转换为浮点类型：`5.0 / 2` 或 `(float)5 / 2`。\n\n### 自增自减运算符\n```c\nint a = 5;\nint b = ++a;  // 前置自增：a先加1再赋值给b，结果 a=6, b=6\nint c = a++;  // 后置自增：a先赋值给c再加1，结果 c=6, a=7\n```\n\n### 关系运算符\n| 运算符 | 含义 | 返回值 |\n|--------|------|--------|\n| `==` | 等于 | 1（真）或 0（假） |\n| `!=` | 不等于 | 1 或 0 |\n| `>` | 大于 | 1 或 0 |\n| `<` | 小于 | 1 或 0 |\n| `>=` | 大于等于 | 1 或 0 |\n| `<=` | 小于等于 | 1 或 0 |\n\n> **常见错误**：将 `==`（等于）误写为 `=`（赋值）：`if (a = 5)` 永远为真。\n\n### 逻辑运算符\n| 运算符 | 含义 | 短路特性 |\n|--------|------|----------|\n| `&&` | 逻辑与 | 左边为假时右边不再执行 |\n| `||` | 逻辑或 | 左边为真时右边不再执行 |\n| `!` | 逻辑非 | 取反 |\n\n### 位运算符（进阶）\n| 运算符 | 含义 | 示例 |\n|--------|------|------|\n| `&` | 按位与 | `5 & 3 = 1` |\n| `|` | 按位或 | `5 | 3 = 7` |\n| `^` | 按位异或 | `5 ^ 3 = 6` |\n| `~` | 按位取反 | `~5 = -6` |\n| `<<` | 左移 | `5 << 1 = 10` |\n| `>>` | 右移 | `5 >> 1 = 2` |\n\n### 运算符优先级（从高到低）\n1. `()` `[]` `->` `.`\n2. `!` `~` `++` `--` `+`（正）`-`（负）`*`（解引用）`&`（取地址）`sizeof`\n3. `*` `/` `%`\n4. `+` `-`\n5. `<<` `>>`\n6. `<` `<=` `>` `>=`\n7. `==` `!=`\n8. `&`（按位与）\n9. `^`（按位异或）\n10. `|`（按位或）\n11. `&&`\n12. `||`\n13. `=` `+=` `-=` 等赋值运算符\n\n> **建议**：不确定优先级时，使用括号 `()` 明确运算顺序。\n\n## 六、输入输出\n\n### printf 格式化输出\n```c\nint age = 20;\nfloat score = 95.5;\nprintf("年龄：%d，成绩：%.1f\\n", age, score);\n// 输出：年龄：20，成绩：95.5\n```\n\n常用格式说明符：\n\n| 格式符 | 说明 | 示例 |\n|--------|------|------|\n| `%d` | 十进制整数 | `printf("%d", 42)` |\n| `%f` | 浮点数 | `printf("%.2f", 3.14159)` → 3.14 |\n| `%c` | 单个字符 | `printf("%c", \'A\')` |\n| `%s` | 字符串 | `printf("%s", "hello")` |\n| `%p` | 指针地址 | `printf("%p", &a)` |\n| `%x` | 十六进制 | `printf("%x", 255)` → ff |\n| `%%` | 百分号本身 | `printf("%%")` |\n\n### scanf 格式化输入\n```c\nint age;\nfloat score;\nprintf("请输入年龄和成绩：");\nscanf("%d %f", &age, &score);\n// 注意：age 和 score 前面必须加 & 取地址符号\n```\n\n> **scanf 注意事项**：\n> - 变量前必须加 `&`（取地址符），否则会导致段错误或数据异常\n> - `scanf` 读取字符串时遇到空格会停止，可用 `gets()` 或 `fgets()` 读取含空格的字符串\n> - `scanf` 读取字符时，缓冲区中的换行符可能被当作输入，可用 `getchar()` 清除\n\n## 七、类型转换\n\n### 隐式转换（自动）\nC语言在运算时自动将"小"类型转换为"大"类型：\n```c\nint a = 5;\ndouble b = 2.5;\ndouble result = a + b;  // a 自动转换为 double，结果为 7.5\n```\n\n### 强制转换（手动）\n```c\nint a = 5, b = 2;\nfloat result = (float)a / b;  // 结果为 2.5\n// 等价于：float result = a / (float)b;\n```\n\n> **常见陷阱**：`printf("%d", sizeof(\'A\'))` 在C语言中输出 4（字符常量在C中是 int 类型），而在C++中输出 1。\n\n## 八、常见错误与调试\n\n1. **忘记分号**：每条语句末尾必须加分号 `;`\n2. **scanf 忘记 &**：`scanf("%d", age)` 应为 `scanf("%d", &age)`\n3. **整数除法**：`5 / 2` 结果为 2，不是 2.5\n4. **= 与 == 混淆**：`if (a = 5)` 永远为真，应为 `if (a == 5)`\n5. **数组越界**：访问不存在的元素导致未定义行为\n6. **字符与字符串混淆**：`\'A\'` 是字符（单引号），`"A"` 是字符串（双引号，尾部有 `\\0`）\n\n## 九、练习建议\n\n1. 编写程序输入半径，计算圆的面积和周长\n2. 实现摄氏温度与华氏温度的相互转换\n3. 编写一个简单计算器，支持加减乘除四种运算\n4. 用 `sizeof` 打印所有基本数据类型的大小', difficulty: 'easy', generatedBy: 'preset', hallucinationScore: 0.95 },
  { id: 'preset_mindmap', title: '基础语法知识图谱', type: 'mindmap', typeLabel: '知识图谱', chapter: '基础语法', icon: '图', coverBg: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)', content: '# C语言基础语法知识图谱', difficulty: 'easy', generatedBy: 'preset', hallucinationScore: 0.97 },
  { id: 'preset_code', title: '基础语法综合示例', type: 'code', typeLabel: '代码实战', chapter: '基础语法', icon: '码', coverBg: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', content: '#include <stdio.h>\n\nint main() {\n    printf("Hello, World!\\n");\n    return 0;\n}', difficulty: 'easy', generatedBy: 'preset', hallucinationScore: 0.98 },
  { id: 'preset_summary', title: '第1周学习总结', type: 'summary', typeLabel: '学习总结', chapter: '基础语法', icon: '总', coverBg: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)', content: '# 第1周学习总结：C语言基础语法\n\n## 一、已掌握知识点\n\n### 1. C程序结构\n- 每个C程序必须有且仅有一个 `main` 函数作为入口\n- `#include <stdio.h>` 引入标准库头文件\n- 每条语句以分号 `;` 结尾\n- `return 0` 表示程序正常退出\n\n### 2. 数据类型\n- 掌握 `int`（整型）、`float`（单精度浮点）、`double`（双精度浮点）、`char`（字符型）四种基本类型\n- 理解 `sizeof` 运算符获取类型占用字节数\n- 了解 `unsigned`、`short`、`long` 类型修饰符的作用\n\n### 3. 变量与常量\n- 变量声明语法：`类型 变量名 = 初始值;`\n- 变量命名规则：字母/下划线开头，区分大小写，不能使用关键字\n- 两种常量定义方式：`#define` 宏常量和 `const` 常变量\n- 理解局部变量与全局变量的作用域区别\n\n### 4. 运算符\n- 算术运算符：`+ - * / %`，注意整数除法截断\n- 自增自减：`++i` 前置与 `i++` 后置的区别\n- 关系运算符：`== != > < >= <=`\n- 逻辑运算符：`&& || !` 及其短路特性\n- 位运算符：`& | ^ ~ << >>`\n- 运算符优先级与结合性，善用括号避免歧义\n\n### 5. 输入输出\n- `printf` 格式化输出：`%d`、`%f`、`%c`、`%s`、`%p` 等格式符\n- `scanf` 格式化输入：**变量前必须加 `&` 取地址符**\n- 转义字符：`\\n`（换行）、`\\t`（制表符）、`\\\\`（反斜杠）、`\\0`（空字符）\n\n### 6. 关键代码示例\n```c\n#include <stdio.h>\n\nint main() {\n    int a, b;\n\n    // 输入两个整数\n    printf("请输入两个整数: ");\n    scanf("%d %d", &a, &b);\n\n    // 四则运算\n    printf("和 = %d\\n", a + b);\n    printf("差 = %d\\n", a - b);\n    printf("积 = %d\\n", a * b);\n    printf("商 = %.2f\\n", (float)a / b);  // 注意强制转换\n\n    // 判断大小\n    if (a > b) {\n        printf("%d 大于 %d\\n", a, b);\n    } else if (a < b) {\n        printf("%d 小于 %d\\n", a, b);\n    } else {\n        printf("两数相等\\n");\n    }\n\n    return 0;\n}\n```\n\n## 二、待加强知识点\n\n### 1. 整数除法陷阱\n- `5 / 2` 结果为 `2`（截断小数），不是 `2.5`\n- 解决方案：`(float)5 / 2` 或 `5.0 / 2`\n- 记忆口诀："整数除整数，结果还是整数"\n\n### 2. scanf 的细节\n- `scanf("%d", &a)` 中 `&` 最容易忘记\n- `scanf` 读取字符串遇到空格会停止\n- 读取字符后缓冲区可能残留换行符，需用 `getchar()` 清除\n\n### 3. 自增自减的微妙之处\n- `int b = a++`：先赋值，a 再加 1\n- `int b = ++a`：a 先加 1，再赋值\n- 在复杂表达式中混用自增可能导致未定义行为\n\n### 4. 位运算\n- 异或 `^` 可用于交换两个变量（不借助临时变量）\n- 左移 `<<` 相当于乘以 2 的幂次，右移 `>>` 相当于除以 2 的幂次\n- 位运算效率极高，在嵌入式开发中广泛使用\n\n## 三、常见易错点\n\n| 易错点 | 错误写法 | 正确写法 | 原因 |\n|--------|---------|----------|------|\n| 整数除法 | `1/2 = 0` | `(double)1/2 = 0.5` | 整数除法截断小数 |\n| scanf漏& | `scanf("%d", a)` | `scanf("%d", &a)` | 必须传地址 |\n| 格式符不匹配 | `printf("%f", 10)` | `printf("%d", 10)` | 格式符与类型对应 |\n| =与==混淆 | `if (a = 5)` | `if (a == 5)` | 赋值表达式永远为真 |\n| 忘记分号 | `printf("hello")` | `printf("hello");` | 语句必须以分号结尾 |\n| 字符与字符串 | `if (c == "y")` | `if (c == \'y\')` | 单引号是字符，双引号是字符串 |\n| 中文符号 | `printf("hello");` | `printf("hello");` | 使用英文标点符号 |\n\n## 四、练习建议\n\n### 基础题（必做）\n1. 编写程序输入半径，计算圆的面积（π × r²）和周长（2 × π × r）\n2. 实现摄氏温度（C）与华氏温度（F）的相互转换：F = C × 9/5 + 32\n3. 输入一个三位数，输出其个位、十位、百位数字\n4. 用 `sizeof` 打印所有基本数据类型在当前平台上的大小\n\n### 进阶题（选做）\n5. 编写简单计算器，根据用户输入的操作符（+、-、*、/）执行对应运算\n6. 输入三个整数，找出最大值和最小值\n7. 实现简单的字符加密：将每个字符的 ASCII 码偏移固定值\n\n## 五、下周学习预告\n\n将进入**流程控制**章节，建议预习：\n- `if-else` 条件判断语句（单分支、双分支、多分支嵌套）\n- `switch-case` 多路选择（注意 `break` 的作用）\n- `for` 循环：已知循环次数的场景\n- `while` 循环：先判断后执行，可能一次都不执行\n- `do-while` 循环：先执行后判断，至少执行一次\n- `break` 跳出当前循环，`continue` 跳过本次迭代\n- 嵌套循环：九九乘法表、打印图形等经典案例', difficulty: 'easy', generatedBy: 'preset', hallucinationScore: 0.95 },
  { id: 'preset_course-video', title: 'C语言简史与概述', type: 'course-video', typeLabel: '视频教程', chapter: 'C语言概述', icon: '视', coverBg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', content: 'https://www.bilibili.com/video/BV1dr4y1n7vA/?p=1', difficulty: 'easy', generatedBy: 'preset', hallucinationScore: null },
  { id: 'preset_ppt', title: 'C语言课件PPT', type: 'course-ppt', typeLabel: '课件PPT', chapter: 'C语言概述', icon: '课', coverBg: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', content: '[PPT_FILE:c-programming-overview.pptx]', coverImage: '/ppt/slide-1.png', difficulty: 'easy', generatedBy: 'preset', hallucinationScore: 0.96 },
  { id: 'preset_blog', title: '指针入门听学笔记', type: 'blog', typeLabel: '听学笔记', chapter: '指针', icon: '听', coverBg: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', content: '什么是指针？指针就是存储内存地址的变量。', difficulty: 'easy', generatedBy: 'preset', hallucinationScore: 0.95, audioUrl: '/audio/chapters/指针/指针入门-听学笔记.mp3' },
]

// 从后端/演示模式加载的资源（演示期间动态生成的部分）
const backendResources = ref([])

// 演示期间生成的资源ID集合，用于退出时清理
const demoGeneratedIds = ref(new Set())

// 资源列表：预设资源 + 动态加载的资源
const resources = computed(() => {
  if (backendResources.value.length > 0) {
    return backendResources.value
  }
  return presetResources
})

// 获取资源类型标签
function getTypeLabel(type) {
  const labels = {
    'article': '文章讲解',
    'code': '代码实战',
    'mindmap': '知识图谱',
    'exercise': '练习题',
    'video': '视频教程',
    'course-video': '视频教程',
    'course-ppt': '课件PPT',
    'ppt': '课件PPT',
    'explanation': '文章讲解',
    'summary': '学习总结',
    'blog': '听学笔记',
    'weakness': '错题诊断'
  }
  return labels[type] || '资源'
}

// 获取资源类型图标
function getTypeIcon(type) {
  const icons = {
    'article': '文',
    'code': '码',
    'mindmap': '图',
    'exercise': '题',
    'video': '视',
    'course-video': '视',
    'course-ppt': '课',
    'ppt': '课',
    'explanation': '文',
    'summary': '总',
    'blog': '听',
    'weakness': '错'
  }
  return icons[type] || '源'
}

// 获取封面渐变色
function getCoverGradient(type) {
  const gradients = {
    'article': 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'code': 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    'mindmap': 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    'exercise': 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    'ppt': 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'video': 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
    'course-video': 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'course-ppt': 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'explanation': 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'summary': 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    'blog': 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'weakness': 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)'
  }
  return gradients[type] || 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
}

// 筛选后的资源（排除练习题，保留全部类型）
const filteredResources = computed(() => {
  let list = resources.value

  // 排除练习题类型（练习题已在上方能力检测区域展示）
  list = list.filter(r => r.type !== 'exercise' && r.type !== 'quiz')

  // 按tab筛选
  if (activeTab.value !== 'all') {
    if (activeTab.value === 'article') {
      list = list.filter(r => r.type === 'article' || r.type === 'explanation')
    } else if (activeTab.value === 'ppt') {
      list = list.filter(r => r.type === 'ppt' || r.type === 'course-ppt')
    } else if (activeTab.value === 'course-video') {
      list = list.filter(r => r.type === 'course-video' || r.type === 'video')
    } else {
      list = list.filter(r => r.type === activeTab.value)
    }
  }

  return list
})

// 处理推荐卡片点击
function handleRecommendClick(item) {
  // 根据类型获取章节名称
  const chapterMap = {
    'quiz': '能力检测',
    'practice': '巩固练习',
    'ebbinghaus': '艾宾浩斯复习',
  }
  const chapterName = chapterMap[item.type]

  // 检查今天是否已答题
  if (isCompletedToday(item.type)) {
    // 今天已答题 - 跳转到刷题房查看解析（带viewPaper参数）
    router.push({
      path: '/student/fullscreen-practice/' + chapterName,
      query: { from: 'today-recommend', viewPaper: 'true', t: Date.now() }
    })
  } else {
    // 今天没答题 - 跳转到刷题房开始新答题
    router.push({ path: '/student/fullscreen-practice/' + chapterName, query: { from: 'today-recommend' } })
  }
}

// 处理资源卡片点击
function handleResourceClick(resource) {
  // code 类型资源跳转蓝桥杯风格编程工作台
  if (resource.type === 'code') {
    router.push({
      path: '/student/code-workbench/' + (resource.id || 'demo'),
      query: {
        title: resource.title,
        knowledgePoint: resource.chapter,
        difficulty: resource.difficulty,
        from: 'today-recommend'
      }
    })
    return
  }
  router.push({
    path: '/student/resource-detail',
    query: {
      id: resource.id,
      title: resource.title,
      type: resource.type,
      chapter: resource.chapter,
      content: resource.content || '',
      audioUrl: resource.audioUrl || '',
      from: 'today-recommend'
    }
  })
}

// 滚动到高亮的资源
function scrollToHighlightedResource() {
  if (highlightedResourceId.value) {
    setTimeout(() => {
      const element = document.getElementById(`resource-${highlightedResourceId.value}`)
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    }, 300)
  }
}

// ===== 演示模式资源持久化（支持刷新后保留已生成的资源） =====
const DEMO_TODAY_RESOURCES_KEY = 'demo_today_recommend_resources_v2'

function loadDemoTodayResources() {
  try {
    const saved = localStorage.getItem(DEMO_TODAY_RESOURCES_KEY)
    return saved ? JSON.parse(saved) : null
  } catch { return null }
}

function saveDemoTodayResources(resources) {
  try {
    localStorage.setItem(DEMO_TODAY_RESOURCES_KEY, JSON.stringify(resources))
  } catch (e) {
    console.warn('持久化演示资源失败:', e)
  }
}

// 从 PPT 资源内容中提取 [PPT_FILE:xxx.pptx] 并构造封面图 URL
// 例：[PPT_FILE:流程控制_课件_讯飞.pptx] → /api/ppt/slides/%E6%B5%81%E7%A8%8B%E6%8E%A7%E5%88%B6_%E8%AF%BE%E4%BB%B6_%E8%AE%AF%E9%A3%9E.pptx/slide-1.png
function resolvePptCoverFromContent(content) {
  if (!content) return null
  const m = String(content).match(/\[PPT_FILE:\s*([^\]]+?\.pptx)\s*\]/i)
  if (!m) return null
  const fileName = m[1].trim()
  try {
    return `/api/ppt/slides/${encodeURIComponent(fileName)}/slide-1.png`
  } catch {
    return `/api/ppt/slides/${fileName}/slide-1.png`
  }
}

// 封面图片加载失败时的降级：清空 coverImage 触发 v-else（渐变封面），避免破损图标
function onCoverImageError(resource) {
  if (resource && typeof resource === 'object') {
    resource.coverImage = null
  }
}

// 将原始mock资源映射为卡片展示对象
function mapDemoResource(r) {
  const type = r.type
  const fallbackCover = type === 'course-ppt' ? resolvePptCoverFromContent(r.content) : null
  return {
    id: r.id,
    title: r.title,
    type: type,
    typeLabel: getTypeLabel(type),
    chapter: r.knowledgePoint || r.chapter || 'C语言',
    icon: getTypeIcon(type),
    coverBg: getCoverGradient(type),
    coverImage: r.coverImage || fallbackCover || null,
    content: r.content || '',
    difficulty: r.difficulty || 'medium',
    generatedBy: r.generatedBy || 'auto-pipeline',
    hallucinationScore: r.hallucinationScore ?? null,
    hallucinationRisk: r.hallucinationRisk ?? null,
    hallucinationNote: r.hallucinationNote ?? null,
    audioUrl: r.audioUrl || r.tags || ''
  }
}

// 将后端 LearningResource 映射为卡片展示对象（补齐前端显示字段）
function mapBackendResource(r) {
  const type = r.type
  const fallbackCover = type === 'course-ppt' ? resolvePptCoverFromContent(r.content) : null
  return {
    id: r.id,
    title: r.title,
    type: type,
    typeLabel: getTypeLabel(type),
    chapter: r.knowledgePoint || r.chapter || 'C语言',
    icon: getTypeIcon(type),
    coverBg: getCoverGradient(type),
    coverImage: r.coverImage || fallbackCover || null,
    content: r.content || '',
    difficulty: r.difficulty || 'medium',
    generatedBy: r.generatedBy || 'auto-pipeline',
    hallucinationScore: r.hallucinationScore ?? null,
    hallucinationRisk: r.hallucinationRisk ?? null,
    hallucinationNote: r.hallucinationNote ?? null,
    audioUrl: r.audioUrl || r.tags || ''
  }
}

// 恢复到初始预设状态（退出演示模式时调用）
function restoreToPreset() {
  backendResources.value = [...presetResources]
  demoGeneratedIds.value = new Set()
  localStorage.removeItem(DEMO_TODAY_RESOURCES_KEY)
}

// 从后端加载今日推荐资源
async function loadBackendResources() {
  const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'

  // 演示模式：优先加载localStorage中已生成的资源（刷新后保留），否则用预设资源初始化
  if (isDemo) {
    try {
      // 1. 优先从localStorage加载已生成的资源（刷新后不丢失）
      const persisted = loadDemoTodayResources()
      if (persisted && persisted.length > 0) {
        backendResources.value = persisted
        // 从持久化数据中识别出演示生成的资源ID（非preset_前缀的）
        demoGeneratedIds.value = new Set(
          persisted.filter(r => !String(r.id).startsWith('preset_')).map(r => r.id)
        )
        return
      }
      // 2. 首次进入演示模式：以预设资源作为初始状态
      backendResources.value = [...presetResources]
      demoGeneratedIds.value = new Set()
      saveDemoTodayResources(backendResources.value)
      return
    } catch (e) {
      console.warn('加载演示资源失败:', e)
    }
    return
  }

  // 非演示模式：清理可能残留的演示数据
  localStorage.removeItem(DEMO_TODAY_RESOURCES_KEY)

  const userId = store.id
  if (!userId) {
    // 未登录且非演示模式：使用预设资源
    backendResources.value = [...presetResources]
    return
  }

  loading.value = true
  try {
    const res = await resourceApi.getTodayRecommend(userId)
    if (res.data && Array.isArray(res.data)) {
      // 后端有资源时使用后端的，映射显示字段，否则回退到预设
      backendResources.value = res.data.length > 0
        ? res.data.map(mapBackendResource)
        : [...presetResources]
    }
  } catch (e) {
    console.warn('加载今日推荐资源失败（使用预设资源）:', e.message || e)
    backendResources.value = [...presetResources]
  } finally {
    loading.value = false
  }
}

// 生成PPT课件
async function generatePpt() {
  if (!store.id) {
    ElMessage.warning('请先登录')
    return
  }

  generatingPpt.value = true
  try {
    const res = await resourceApi.generatePpt({
      topic: 'C语言基础',
      style: '简洁专业'
    })

    if (res.data) {
      ElMessage.success('PPT生成成功！')
      // 刷新资源列表
      await loadBackendResources()
    } else {
      ElMessage.error('PPT生成失败，请稍后重试')
    }
  } catch (e) {
    console.error('生成PPT失败:', e)
    ElMessage.error('生成PPT失败：' + (e.message || '未知错误'))
  } finally {
    generatingPpt.value = false
  }
}

// 监听资源刷新事件（WebSocket推送）
function handleRefreshResources() {
  console.log('[TodayRecommend] 收到资源刷新事件，重新加载资源列表')
  loadBackendResources().catch(() => {})
}

// 监听演示模式退出事件：清空演示期间生成的资源，恢复预设状态
function handleDemoModeExit() {
  console.log('[TodayRecommend] 演示模式退出，恢复预设资源状态')
  restoreToPreset()
}

// 页面加载时检查是否有从首页传来的参数
onMounted(() => {
  // 加载后端资源（不阻塞页面）
  loadBackendResources().catch(() => {})

  if (route.query.resourceId) {
    highlightedResourceId.value = route.query.resourceId
    scrollToHighlightedResource()
  }

  // 检查是否需要自动生成PPT（不阻塞页面）
  autoGeneratePptIfNeeded().catch(() => {})

  // 监听WebSocket资源刷新事件
  window.addEventListener('refresh-resources', handleRefreshResources)
  // 监听演示模式退出事件
  window.addEventListener('demo-mode-exit', handleDemoModeExit)
})

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('refresh-resources', handleRefreshResources)
  window.removeEventListener('demo-mode-exit', handleDemoModeExit)
})

// 自动检查并生成PPT（如果没有PPT资源）
async function autoGeneratePptIfNeeded() {
  // demo模式下不生成PPT
  const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
  if (isDemo || !store.id) return

  // 检查是否已有PPT资源
  const hasPpt = resources.value.some(r => r.type === 'course-ppt')
  if (hasPpt) return

  // 没有PPT，自动生成
  generatingPpt.value = true
  try {
    const res = await resourceApi.generatePpt({
      topic: 'C语言基础',
      style: '简洁专业'
    })

    if (res.data) {
      await loadBackendResources()
    }
  } catch (e) {
    console.warn('PPT自动生成失败（非致命，继续使用默认资源）:', e.message || e)
  } finally {
    generatingPpt.value = false
  }
}

// ===== 生成资源：通用辅助 =====
// 判断是否为超时错误
function isTimeoutError(e) {
  return e?.code === 'ECONNABORTED' || /timeout/i.test(e?.message || '')
}

// 演示模式：模拟真实生成过程的阶段性文案（预置资源，仅模拟UI反馈）
const demoGenSteps = [
  '正在分析学习画像...',
  '正在检索知识库...',
  '正在调用AI智能体生成资源...',
  '正在执行四道门防幻觉校验...',
  '正在保存生成的资源...',
]
async function runDemoGenerateSimulation() {
  for (const step of demoGenSteps) {
    generatingProgress.value = step
    await new Promise(r => setTimeout(r, 450 + Math.random() * 350))
  }
}

// 真实用户：带超时自动重试的资源生成
async function generateWithRetry(studentId, payload, maxRetries = 1) {
  let lastError
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      generatingProgress.value = attempt === 0
        ? '正在调用AI智能体生成资源（预计1-3分钟，请耐心等待）...'
        : `生成超时，正在自动重试（第 ${attempt + 1} 次尝试）...`
      return await resourceApi.generate(studentId, payload, { skipErrorMessage: true })
    } catch (e) {
      lastError = e
      if (attempt < maxRetries && isTimeoutError(e)) {
        await new Promise(r => setTimeout(r, 2000))
        continue
      }
      throw e
    }
  }
  throw lastError
}

// 按筛选条件生成资源
async function handleGenerate() {
  const types = genSelectedTypes.value
  if (!types.length) return

  const chapter = genSelectedChapter.value
  const diff = genSelectedDiff.value
  showGenerateModal.value = false

  const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
  if (isDemo) {
    // ===== 演示模式：本地预置资源 + 模拟真实生成UI反馈（评委演示用） =====
    generatingPpt.value = true
    try {
      // 1. 模拟真实生成的阶段性加载状态（不依赖后端，保证演示流畅）
      await runDemoGenerateSimulation()

      // 2. 从本地预置数据加载资源（预存储，确保演示顺畅）
      const { demoResources } = await import('../../mock/demoData')

      // 类型映射：生成弹窗类型 → demo数据类型
      // 注意：demo数据中PPT资源type为'course-ppt'，需保持一致；'weakness'映射为'exercise'用于生成练习题
      const typeMap = {
        'weakness': 'exercise',
      }
      const mappedTypes = types.map(t => typeMap[t] || t)

      // 章节映射：章节value → 知识点关键词
      const chapterMap = {
        '1': '基础语法', '2': ['流程控制', '控制结构'], '3': '函数',
        '4': ['数组', '数组与指针'], '5': '指针', '6': '结构体', '7': '文件操作',
      }

      // 过滤：按类型和章节
      let filtered = (demoResources || []).filter(r => mappedTypes.includes(r.type))
      if (chapter !== 'all') {
        const keywords = chapterMap[chapter]
        const kwArr = Array.isArray(keywords) ? keywords : [keywords]
        filtered = filtered.filter(r => {
          const kp = r.knowledgePoint || r.chapter || ''
          return kwArr.some(kw => kp.includes(kw))
        })
      }

      // 按难度筛选：优先匹配选中难度，若无匹配则保留全部（避免结果为空）
      if (diff && diff !== 'all') {
        const diffFiltered = filtered.filter(r => (r.difficulty || 'medium') === diff)
        if (diffFiltered.length > 0) {
          filtered = diffFiltered
        }
      }

      // 每种类型只取1条（PPT课件除外，允许同一类型多条以展示不同章节课件）
      const typeCounts = {}
      filtered = filtered.filter(r => {
        typeCounts[r.type] = (typeCounts[r.type] || 0) + 1
        return r.type === 'course-ppt' || typeCounts[r.type] <= 1
      })

      const newResources = filtered.map(mapDemoResource)

      // 合并：将新资源追加到已有列表前面，按ID去重，保留之前的资源
      const existingIds = new Set(backendResources.value.map(r => r.id))
      const toAdd = newResources.filter(r => !existingIds.has(r.id))
      backendResources.value = [...toAdd, ...backendResources.value]

      // 记录演示期间新生成的资源ID，用于退出时清理
      toAdd.forEach(r => demoGeneratedIds.value.add(r.id))

      // 持久化到localStorage，确保刷新后资源不丢失
      saveDemoTodayResources(backendResources.value)

      if (toAdd.length > 0) {
        // 区分可见资源和不可见资源（exercise类型不在资源列表显示）
        const visible = toAdd.filter(r => r.type !== 'exercise')
        const hidden = toAdd.filter(r => r.type === 'exercise')
        if (visible.length > 0 && hidden.length > 0) {
          ElMessage.success(`已生成 ${visible.length} 种学习资源，${hidden.length} 份练习题已加入能力检测区域`)
        } else if (visible.length > 0) {
          ElMessage.success(`已生成 ${visible.length} 种个性化学习资源`)
        } else {
          ElMessage.success(`已生成 ${hidden.length} 份练习题，请到能力检测区域查看`)
        }
      } else {
        ElMessage.info('所选类型资源已全部加载，请尝试其他类型或章节')
      }
    } catch (e) {
      console.error('加载演示资源失败:', e)
      ElMessage.error('生成资源失败，请稍后重试')
    } finally {
      generatingPpt.value = false
      generatingProgress.value = ''
    }
    return
  }

  // ===== 真实用户模式：调用后端真实生成（优先可靠性，允许较长耗时） =====
  if (!store.id) {
    ElMessage.warning('请先登录')
    return
  }

  generatingPpt.value = true
  const studentId = store.id
  try {
    await generateWithRetry(studentId, { types, chapter, difficulty: diff }, 1)
    generatingProgress.value = '资源生成完成，正在加载...'
    ElMessage.success(`已成功生成 ${types.length} 种学习资料`)
    // 生成成功后立即刷新资源列表
    await loadBackendResources()
  } catch (e) {
    console.error('生成资料失败:', e)
    // 区分超时与其他错误，给出友好提示并提供手动重试入口
    if (isTimeoutError(e)) {
      ElMessageBox.confirm(
        '资源生成超时，可能是AI智能体处理时间较长。\n你可以重试生成，或稍后刷新页面查看已生成的资源。',
        '生成超时',
        { confirmButtonText: '重试生成', cancelButtonText: '稍后刷新', type: 'warning' }
      ).then(() => {
        // 用户选择重试：重新触发生成（保留原筛选条件）
        handleGenerate()
      }).catch(() => {
        // 用户选择稍后刷新：尝试重新加载资源列表
        loadBackendResources().catch(() => {})
      })
    } else {
      ElMessageBox.confirm(
        '资源生成失败：' + (e.message || '未知错误') + '\n是否立即重试？',
        '生成失败',
        { confirmButtonText: '立即重试', cancelButtonText: '取消', type: 'error' }
      ).then(() => {
        handleGenerate()
      }).catch(() => {})
    }
  } finally {
    generatingPpt.value = false
    generatingProgress.value = ''
  }
}
</script>

<style scoped>
.today-recommend {
  padding: 32px 40px;
  min-height: 100%;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

/* 页面头部 */
.page-header {
  margin-bottom: 32px;
}
.page-title {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 8px;
}
.page-subtitle {
  font-size: 15px;
  color: #64748b;
  margin: 0;
}

/* 推荐区域 */
.recommend-section {
  margin-bottom: 40px;
}
.section-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 20px;
}
.recommend-scroll-wrapper {
  overflow-x: auto;
  margin: 0 -20px;
  padding: 0 20px 8px;
  scrollbar-width: thin;
  scrollbar-color: #e2e8f0 transparent;
}
.recommend-scroll-wrapper::-webkit-scrollbar {
  height: 6px;
}
.recommend-scroll-wrapper::-webkit-scrollbar-track {
  background: transparent;
}
.recommend-scroll-wrapper::-webkit-scrollbar-thumb {
  background: #e2e8f0;
  border-radius: 3px;
}
.recommend-scroll-wrapper::-webkit-scrollbar-thumb:hover {
  background: #cbd5e1;
}
.recommend-grid {
  display: flex;
  gap: 20px;
  min-width: min-content;
}
.recommend-grid .recommend-card {
  min-width: 280px;
  flex: 0 0 280px;
}

/* 推荐卡片 */
.recommend-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(0, 0, 0, 0.05);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.recommend-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(99, 102, 241, 0.15);
  border-color: #c7d2fe;
}
.recommend-card.completed {
  border-color: rgba(16, 185, 129, 0.3);
  background: linear-gradient(145deg, #fff 0%, #f0fdf4 100%);
}
.recommend-card.completed:hover {
  box-shadow: 0 12px 32px rgba(16, 185, 129, 0.15);
  border-color: #86efac;
}
.completed-score {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
}
.completed-score span {
  font-size: 20px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
}
.action-btn.done {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}
.card-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}
.card-content {
  flex: 1;
}
.card-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px;
}
.card-desc {
  font-size: 14px;
  color: #64748b;
  margin: 0;
  line-height: 1.5;
}
.card-action {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border-radius: 10px;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  justify-content: center;
}
.recommend-card:hover .card-action {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
}

/* 资源区域 */
.resource-section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.generate-btn {
  padding: 8px 20px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}
.generate-btn:hover {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4);
}
.generate-btn:active {
  transform: scale(0.98);
}

.ppt-generating-tip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
  font-size: 13px;
  font-weight: 500;
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(99, 102, 241, 0.3);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 筛选标签 */
.filter-tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.filter-tab {
  padding: 8px 20px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.filter-tab:hover {
  border-color: #6366f1;
  color: #6366f1;
}
.filter-tab.active {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}

/* 资源网格 */
.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}

/* 资源卡片 */
.resource-card {
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid rgba(0, 0, 0, 0.05);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.resource-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1);
}
.resource-card.highlighted {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2), 0 12px 32px rgba(99, 102, 241, 0.15);
  transform: translateY(-4px);
}

/* 封面区域 */
.tr-cover-area {
  position: relative;
  height: 180px;
  overflow: hidden;
}

/* VS Code 封面 */
.cover-vscode {
  background: #1e1e1e;
  height: 100%;
  display: flex;
  flex-direction: column;
  font-family: 'JetBrains Mono', 'Cascadia Code', 'SF Mono', Consolas, monospace;
  font-size: 10.5px;
  line-height: 1.6;
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
.vs-tab.active { background: #1e1e1e; color: #fff; }
.vs-icon { font-size: 12px; }
.vs-editor { padding: 8px 12px; flex: 1; overflow: hidden; }
.vs-line-num { color: #505050; display: inline-block; width: 24px; text-align: right; margin-right: 12px; user-select: none; }
.vs-pp { color: #c586c0; }
.vs-ty { color: #4ec9b0; }
.vs-fn { color: #dcdcaa; }
.vs-str { color: #ce9178; }
.vs-num { color: #b5cea8; }
.vs-cm { color: #6a9955; }
.vs-op { color: #d4d4d4; }
.vs-kw { color: #c586c0; }
.vs-inc { color: #ce9178; }

/* PPT课件封面（照片） */
.cover-ppt {
  height: 100%;
  position: relative;
  overflow: hidden;
  /* 图片加载前/失败前显示渐变底色，防止感知为"白封面" */
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #ec4899 100%);
}
.ppt-slide-img {
  position: absolute;
  top: 0; left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-ppt .vs-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, transparent 60%, rgba(0,0,0,0.15));
}
.cover-ppt .vs-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  background: rgba(255,255,255,0.9);
  color: #1e293b;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

/* 书籍封面 */
.cover-book {
  background: linear-gradient(160deg, #0f172a 0%, #1e293b 40%, #334155 100%);
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 16px;
  text-align: center;
  position: relative;
}
.cover-book::before {
  content: '';
  position: absolute;
  inset: 6px;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 6px;
}
.book-icon { font-size: 32px; margin-bottom: 6px; filter: drop-shadow(0 2px 8px rgba(0,0,0,0.3)); }
.book-title { font-size: 20px; font-weight: 800; color: #fff; letter-spacing: 3px; text-shadow: 0 1px 4px rgba(0,0,0,0.2); margin-bottom: 4px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.book-sub { font-size: 10px; color: rgba(255,255,255,0.4); letter-spacing: 1.5px; margin-top: 4px; }
.book-byline { position: absolute; bottom: 14px; font-size: 9px; color: rgba(255,255,255,0.25); letter-spacing: 1px; }

/* 笔记本封面 */
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
.nb-title { font-size: 16px; font-weight: 700; color: #1e293b; border-bottom: 2px solid #6366f1; padding-bottom: 4px; margin-bottom: 6px; display: inline-block; }
.nb-list { list-style: none; font-size: 10px; color: #475569; line-height: 1.7; padding: 0; margin: 0; }
.nb-list li::before { content: '✓ '; color: #22c55e; font-weight: bold; }

/* 思维导图封面 — 建立独立层叠上下文，中心节点在上，分支在下 */
.cover-mindmap { background: linear-gradient(145deg, #f0f4ff 0%, #e8eeff 100%); height: 100%; position: relative; isolation: isolate; overflow: hidden; }
.mindmap-preview { position: relative; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; isolation: isolate; }
.mindmap-center { position: absolute; width: 60px; height: 60px; background: linear-gradient(145deg, #6366f1 0%, #8b5cf6 100%); border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; color: #fff; z-index: 5; box-shadow: 0 6px 20px rgba(99, 102, 241, 0.5); }
.mindmap-branches { position: absolute; width: 100%; height: 100%; z-index: 1; pointer-events: none; }
.mindmap-branch { position: absolute; top: 50%; left: 50%; transform-origin: 0 0; z-index: 1; }
.branch-line { width: 40px; height: 3px; background: linear-gradient(90deg, #818cf8 0%, #c7d2fe 100%); border-radius: 2px; }
.branch-node { position: absolute; left: 36px; top: -10px; padding: 4px 10px; background: #fff; border: 1px solid #e0e7ff; border-radius: 8px; font-size: 10px; font-weight: 600; color: #4338ca; white-space: nowrap; box-shadow: 0 2px 8px rgba(99, 102, 241, 0.15); z-index: 2; }

/* 封面通用 */
.vs-overlay { position: absolute; inset: 0; background: linear-gradient(180deg, transparent 40%, rgba(0,0,0,0.4) 100%); pointer-events: none; }
.vs-badge { position: absolute; top: 10px; left: 10px; background: rgba(255,255,255,0.92); backdrop-filter: blur(6px); padding: 3px 10px; border-radius: 8px; font-size: 11px; font-weight: 600; color: #1e293b; box-shadow: 0 2px 6px rgba(0,0,0,0.08); }

/* 卡片内容区 */
.tr-card-body { padding: 12px 14px 14px; }
.tr-card-title { font-size: 13px; font-weight: 600; line-height: 1.4; color: #1e293b; margin: 0 0 8px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.tr-card-tags { display: flex; align-items: center; gap: 5px; flex-wrap: wrap; }
.tr-tag { padding: 2px 8px; border-radius: 20px; font-size: 10px; font-weight: 500; white-space: nowrap; }
.tag-code { background: #fef2f2; color: #ef4444; }
.tag-video, .tag-course-video { background: #eff6ff; color: #3b82f6; }
.tag-course-ppt, .tag-ppt, .tag-article { background: #fff7ed; color: #f97316; }
.tag-explanation { background: #fff7ed; color: #f97316; }
.tag-mindmap { background: #f0fdf4; color: #16a34a; }
.tag-exercise { background: #fef2f2; color: #ef4444; }
.tag-summary { background: #ecfdf5; color: #059669; }
.tag-blog { background: #fdf4ff; color: #a855f7; }
.tag-weakness { background: #fef2f2; color: #ef4444; }
.tag-chapter { background: #f5f3ff; color: #8b5cf6; }
.tr-score { display: inline-flex; align-items: center; gap: 3px; padding: 2px 8px; border-radius: 20px; font-size: 10px; font-weight: 600; background: #ecfdf5; color: #059669; border: 1px solid #a7f3d0; margin-left: auto; }
.score-dot { width: 4px; height: 4px; border-radius: 50%; background: #059669; }

/* 听学笔记卡片提示 */
.blog-preview {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(0,0,0,0.05);
}
.blog-content-preview {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.blog-hint {
  font-size: 12px;
  color: #8b5cf6;
  font-weight: 500;
}

/* 响应式 */
@media (max-width: 768px) {
  .today-recommend { padding: 24px 20px; }
  .recommend-grid .recommend-card { min-width: 260px; flex: 0 0 260px; }
  .resource-grid { grid-template-columns: repeat(2, 1fr); }
}
/* ===== 生成资源筛选浮窗（teleport 到 body，不加 scoped） ===== */
</style>

<style>
.gen-modal-overlay {
  position: fixed; inset: 0; z-index: 2000;
  display: flex; align-items: center; justify-content: center;
  animation: genFadeIn 0.2s ease;
}
.gen-modal-backdrop {
  position: absolute; inset: 0; background: rgba(0,0,0,0.4);
  backdrop-filter: blur(4px);
}
@keyframes genFadeIn { from { opacity: 0; } to { opacity: 1; } }

.gen-modal-box {
  background: #fff; border-radius: 20px; width: 520px; max-height: 85vh;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
  animation: genSlideUp 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  overflow: hidden; display: flex; flex-direction: column;
  position: relative; z-index: 10;
}
@keyframes genSlideUp {
  from { opacity: 0; transform: translateY(30px) scale(0.96); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.gen-modal-header {
  padding: 20px 24px 16px; border-bottom: 1px solid rgba(0,0,0,0.06);
  display: flex; justify-content: space-between; align-items: center;
}
.gen-modal-header-left { display: flex; align-items: center; gap: 10px; }
.gen-modal-header-icon {
  width: 36px; height: 36px; border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; color: #fff;
}
.gen-modal-title { font-size: 17px; font-weight: 700; color: #1e293b; }
.gen-modal-close {
  width: 32px; height: 32px; border-radius: 8px; border: none;
  background: #f1f5f9; color: #64748b; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; transition: all 0.2s;
}
.gen-modal-close:hover { background: #e2e8f0; color: #334155; }

.gen-modal-body { padding: 20px 24px; overflow-y: auto; flex: 1; }

.gen-filter-group { margin-bottom: 22px; }
.gen-filter-group:last-child { margin-bottom: 0; }
.gen-filter-label {
  font-size: 13px; font-weight: 600; color: #334155;
  margin-bottom: 10px; display: flex; align-items: center; gap: 6px;
}
.gen-label-icon { font-size: 16px; line-height: 1; display: inline-flex; align-items: center; }

/* 类型标签（多选） */
.gen-chip-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.gen-chip {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 7px 14px; border-radius: 10px;
  border: 1.5px solid #e2e8f0; background: #fff;
  font-size: 12px; font-weight: 500; color: #64748b;
  cursor: pointer; transition: all 0.2s; user-select: none;
}
.gen-chip:hover { border-color: #a5b4fc; color: #6366f1; background: rgba(99,102,241,0.03); }
.gen-chip.active {
  border-color: #6366f1; background: #6366f1; color: #fff;
  box-shadow: 0 2px 8px rgba(99,102,241,0.2);
}
.gen-chip-icon { font-size: 15px; line-height: 1; display: inline-flex; align-items: center; }
.gen-chip-check {
  width: 14px; height: 14px; border-radius: 4px;
  border: 1.5px solid #d1d5db; display: flex;
  align-items: center; justify-content: center;
  font-size: 9px; transition: all 0.2s; flex-shrink: 0;
}
.gen-chip.active .gen-chip-check { border-color: rgba(255,255,255,0.5); background: rgba(255,255,255,0.3); }
.gen-chip.active .gen-chip-check::after { content: '✓'; color: #fff; }

/* 章节标签（单选） */
.gen-chapter-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.gen-chapter-chip {
  padding: 7px 14px; border-radius: 10px;
  border: 1.5px solid #e2e8f0; background: #fff;
  font-size: 12px; font-weight: 500; color: #64748b;
  cursor: pointer; transition: all 0.2s; user-select: none;
}
.gen-chapter-chip:hover { border-color: #06b6d4; color: #0891b2; }
.gen-chapter-chip.active {
  border-color: #0891b2; background: #0891b2; color: #fff;
  box-shadow: 0 2px 8px rgba(6,182,212,0.2);
}

/* 难度标签（单选） */
.gen-diff-grid { display: flex; gap: 10px; }
.gen-diff-chip {
  flex: 1; padding: 10px 0; border-radius: 10px;
  border: 1.5px solid #e2e8f0; background: #fff;
  font-size: 13px; font-weight: 600; text-align: center;
  cursor: pointer; transition: all 0.2s; user-select: none;
}
.gen-diff-chip:hover { transform: translateY(-1px); }
.gen-diff-chip.easy:hover, .gen-diff-chip.easy.active { border-color: #22c55e; background: #22c55e; color: #fff; box-shadow: 0 2px 8px rgba(34,197,94,0.25); }
.gen-diff-chip.medium:hover, .gen-diff-chip.medium.active { border-color: #f59e0b; background: #f59e0b; color: #fff; box-shadow: 0 2px 8px rgba(245,158,11,0.25); }
.gen-diff-chip.hard:hover, .gen-diff-chip.hard.active { border-color: #ef4444; background: #ef4444; color: #fff; box-shadow: 0 2px 8px rgba(239,68,68,0.25); }

/* 底部 */
.gen-modal-footer {
  padding: 16px 24px; border-top: 1px solid rgba(0,0,0,0.06);
  background: #fafbfc; display: flex; justify-content: space-between; align-items: center;
}
.gen-footer-tip { font-size: 12px; transition: color 0.2s; }
.gen-footer-actions { display: flex; gap: 10px; }
.gen-btn-cancel {
  padding: 10px 20px; border-radius: 10px; border: 1px solid #e2e8f0;
  background: #fff; color: #64748b; font-size: 13px; font-weight: 600;
  cursor: pointer; transition: all 0.2s;
}
.gen-btn-cancel:hover { background: #f1f5f9; color: #334155; }
.gen-btn-confirm {
  padding: 10px 24px; border-radius: 10px; border: none;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff; font-size: 13px; font-weight: 600;
  cursor: pointer; transition: all 0.3s;
  box-shadow: 0 2px 10px rgba(99,102,241,0.25);
  display: flex; align-items: center; gap: 6px;
}
.gen-btn-confirm:hover { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(99,102,241,0.35); }
.gen-btn-confirm:disabled { background: #e2e8f0; color: #94a3b8; cursor: not-allowed; box-shadow: none; transform: none; }
.gen-btn-confirm .gen-arrow { transition: transform 0.2s; }
.gen-btn-confirm:hover:not(:disabled) .gen-arrow { transform: translateX(3px); }
</style>
