<template>
  <div class="mindmap-viewer">
    <div class="mm-controls" v-if="hasContent">
      <button class="mm-btn" @click="zoomIn" title="放大">+</button>
      <button class="mm-btn" @click="zoomOut" title="缩小">−</button>
      <button class="mm-btn" @click="fitView" title="适应画面">⊡</button>
    </div>
    <div ref="mmContainer" class="mm-container">
      <svg ref="mmSvg" width="100%" height="100%"></svg>
    </div>
    <!-- 空状态提示 -->
    <div v-if="!hasContent" class="mm-empty">
      <div class="mm-empty-icon">🗺️</div>
      <div class="mm-empty-text">知识图谱加载中...</div>
      <div class="mm-empty-hint">正在构建知识关联网络</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { Transformer } from 'markmap-lib'
import { Markmap } from 'markmap-view'

const props = defineProps({
  content: { type: String, default: '' },
  title: { type: String, default: '' }
})

const mmContainer = ref(null)
const mmSvg = ref(null)
let mmInstance = null
const transformer = new Transformer()

const hasContent = computed(() => {
  return props.content && props.content.trim().length > 0
})

// 生成默认的思维导图内容（根据标题生成更丰富的内容）
function generateDefaultContent() {
  const title = props.title || '知识图谱'

  // 根据标题关键词生成不同的内容
  const contentMap = {
    '结构体': `# 结构体知识图谱
## 基础概念
### 定义与声明
- struct 关键字
- 结构体名称规范
### 成员变量
- 数据类型选择
- 内存对齐
## 使用方法
### 创建实例
- 直接声明
- 指针访问
### 成员访问
- 点运算符 .
- 箭头运算符 ->
## 高级特性
### 结构体数组
- 遍历与操作
### 结构体指针
- 动态内存分配
- 函数参数传递
## 实践应用
### 数据组织
- 学生信息管理
- 图形坐标表示
### 常见模式
- 嵌套结构体
- 结构体套娃`,
    '指针': `# 指针知识图谱
## 核心概念
### 什么是指针
- 内存地址的概念
- 指针变量的定义
### 基本操作
- 取地址 &
- 解引用 *
## 指针与数组
### 数组名的含义
- 首地址指针
- 指针算术
### 数组遍历
- 下标访问
- 指针偏移
## 指针与函数
### 参数传递
- 值传递 vs 地址传递
- 指针参数
### 返回值
- 返回指针
- 函数指针
## 常见陷阱
### 野指针
- 未初始化的指针
### 空指针
- NULL 的使用
### 内存泄漏
- 动态内存管理`,
    '数组': `# 数组知识图谱
## 基础概念
### 数组定义
- 一维数组
- 二维数组
### 数组初始化
- 静态初始化
- 动态初始化
## 数组操作
### 遍历
- for 循环
- while 循环
### 查找
- 线性查找
- 二分查找
### 排序
- 冒泡排序
- 选择排序
## 字符数组
### 字符串基础
- 字符数组存储
- 字符串函数
### 常用函数
- strlen
- strcpy
- strcmp
## 内存与数组
### 数组存储
- 连续内存
- 数组名指针
### 多维数组
- 行列存储
- 指针访问`,
    '函数': `# 函数知识图谱
## 基础概念
### 函数定义
- 返回类型
- 函数名
- 参数列表
### 函数声明
- 原型声明
- 头文件包含
## 参数传递
### 值传递
- 形参与实参
- 单向传递
### 地址传递
- 指针参数
- 数组参数
## 返回值
### 基本类型
- int, float, char
### 指针返回
- 局部指针问题
## 高级特性
### 递归
- 基准情况
- 递归调用
### 函数指针
- 回调函数
### 作用域
- 局部变量
- 全局变量
- static 变量`
  }

  // 查找匹配的内容
  for (const [key, content] of Object.entries(contentMap)) {
    if (title.includes(key)) {
      return content
    }
  }

  // 默认通用内容
  return `# ${title}
## 核心概念
### 基础定义
- 核心概念解释
- 关键术语说明
### 基本原理
- 工作机制
- 设计思想
## 使用方法
### 基本语法
- 定义方式
- 声明格式
### 常用操作
- 基本使用
- 常见模式
## 注意事项
### 常见错误
- 易错点提醒
- 调试方法
### 最佳实践
- 编码规范
- 性能优化
## 拓展内容
### 相关知识
- 前置知识
- 后续学习
### 实战应用
- 项目案例
- 面试考点`
}

// JSON格式 → Markdown
function jsonToMarkdown(json) {
  const central = json.central || json.name || '知识图谱'
  let md = '# ' + central + '\n'
  const branches = json.branches || json.children || []
  for (const branch of branches) {
    const name = branch.name || branch.topic || branch.title || ''
    const level = branch.level ? ` [${branch.level}]` : ''
    md += '## ' + name + level + '\n'
    const subs = branch.subtopics || branch.children || []
    for (const sub of subs) {
      if (typeof sub === 'string') {
        md += '### ' + sub + '\n'
      } else if (sub && sub.name) {
        md += '### ' + sub.name + '\n'
        const leaves = sub.leaves || sub.children || []
        for (const leaf of leaves) {
          if (typeof leaf === 'string') {
            md += '- ' + leaf + '\n'
          } else if (leaf && leaf.name) {
            md += '- ' + leaf.name + '\n'
          }
        }
      }
    }
    // tip显示为注释
    if (branch.tip) {
      md += '> 💡 ' + branch.tip + '\n'
    }
    // example显示为代码块
    if (branch.example) {
      md += '```\n' + branch.example + '\n```\n'
    }
  }
  return md
}

function buildMarkmap() {
  if (!mmSvg.value) return

  // 等待SVG有实际尺寸后再初始化
  const rect = mmSvg.value.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) {
    requestAnimationFrame(buildMarkmap)
    return
  }

  // d3-zoom (used internally by markmap-view) reads svg.width.baseVal.value;
  // when the attribute is a relative length like '100%' and the layout is not fully
  // resolved, reading .value throws NotSupportedError: Could not resolve relative length.
  // Override the SVG width/height attributes with absolute pixel values from the container.
  mmSvg.value.setAttribute('width', Math.round(rect.width))
  mmSvg.value.setAttribute('height', Math.round(rect.height))

  let rawContent = hasContent.value ? props.content : generateDefaultContent()

  try {
    // 检测是否为JSON格式，转成Markdown
    const trimmed = rawContent.trim()
    if (trimmed.startsWith('{')) {
      try {
        const json = JSON.parse(trimmed)
        if (json.central || json.branches) {
          rawContent = jsonToMarkdown(json)
        }
      } catch (e) { /* 不是JSON，保持原样 */ }
    }

    const { root } = transformer.transform(rawContent)
    if (!root) return

    if (mmInstance) {
      mmInstance.setData(root)
      mmInstance.fit()
    } else {
      mmInstance = Markmap.create(mmSvg.value, {
        autoFit: true,
        duration: 600,
        maxWidth: 280,
        initialExpandLevel: 3,
        spacingHorizontal: 80,
        spacingVertical: 16,
        paddingX: 12,
        color: (node) => {
          const colors = [
            '#6366f1', '#8b5cf6', '#a855f7', '#d946ef',
            '#ec4899', '#f43f5e', '#f59e0b', '#10b981'
          ]
          return colors[node.state?.depth % colors.length || 0]
        },
      }, root)
    }
  } catch (e) {
    console.error('思维导图渲染失败:', e)
  }
}

function zoomIn() { mmInstance?.rescale(1.3) }
function zoomOut() { mmInstance?.rescale(0.7) }
function fitView() { mmInstance?.fit() }

watch(() => props.content, () => nextTick(buildMarkmap))

onMounted(() => {
  nextTick(buildMarkmap)
})
</script>

<style scoped>
.mindmap-viewer {
  position: relative;
  width: 100%;
  height: 500px;
  background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 50%, #f5f3ff 100%);
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(99, 102, 241, 0.15);
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.08);
  /* 建立独立层叠上下文，隔离内部 z-index 防止与外部互相干扰 */
  isolation: isolate;
  z-index: 1;
}
.mm-container {
  position: relative;
  width: 100%;
  height: 100%;
  /* 思维导图 SVG 容器保持在较高层级，确保显示在加载遮罩上方 */
  z-index: 2;
}
.mm-container :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}
.mm-controls {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 6px;
  /* 控件保持在最顶层 */
  z-index: 20;
}
.mm-btn {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px solid rgba(99, 102, 241, 0.2);
  background: rgba(255, 255, 255, 0.98);
  font-size: 17px;
  font-weight: 700;
  color: #6366f1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s ease;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.1);
  backdrop-filter: blur(4px);
}
.mm-btn:hover {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border-color: transparent;
  transform: scale(1.05);
}

/* 空状态样式 — 层级低于 SVG 容器，不阻挡思维导图交互 */
.mm-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.02) 0%, rgba(139, 92, 246, 0.02) 100%);
  /* 加载遮罩置于 SVG 图层下方，确保思维导图可见且可交互 */
  z-index: 1;
  pointer-events: none;
}
.mm-empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
  animation: pulse 2s ease-in-out infinite;
  opacity: 0.55;
}
.mm-empty-text {
  font-size: 18px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 8px;
  opacity: 0.75;
}
.mm-empty-hint {
  font-size: 14px;
  color: #94a3b8;
  opacity: 0.75;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.55; }
  50% { transform: scale(1.1); opacity: 0.9; }
}

/* ===== 响应式：不同屏幕尺寸下的高度与控件尺寸适配 ===== */
@media (max-width: 1200px) {
  .mindmap-viewer {
    height: 440px;
  }
}

@media (max-width: 900px) {
  .mindmap-viewer {
    height: 380px;
  }
  .mm-empty-icon {
    font-size: 48px;
  }
  .mm-empty-text {
    font-size: 16px;
  }
  .mm-empty-hint {
    font-size: 13px;
  }
  .mm-btn {
    width: 30px;
    height: 30px;
    font-size: 15px;
  }
}

@media (max-width: 600px) {
  .mindmap-viewer {
    height: 320px;
    border-radius: 14px;
  }
  .mm-empty-icon {
    font-size: 42px;
    margin-bottom: 12px;
  }
  .mm-empty-text {
    font-size: 15px;
  }
  .mm-empty-hint {
    font-size: 12px;
  }
  .mm-controls {
    top: 8px;
    right: 8px;
    gap: 4px;
  }
  .mm-btn {
    width: 28px;
    height: 28px;
    font-size: 14px;
    border-radius: 8px;
  }
}
</style>
