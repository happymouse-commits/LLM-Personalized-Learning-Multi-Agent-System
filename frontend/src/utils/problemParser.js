/**
 * 编程实战资源内容结构化解析器
 * 从 CodeAgent 生成的 Markdown 内容中提取：
 * - 问题描述 / 输入格式 / 输出格式 / 样例输入输出 / 约束
 * - 代码块 / 代码解析 / 改进建议
 * - 判题比对函数
 */

/**
 * 解析编程实战资源的 Markdown 内容
 * @param {string} markdown - LearningResource.content
 * @returns {ParsedProblem}
 */
export function parseCodeResourceContent(markdown) {
  if (!markdown) {
    return { description: '', inputFormat: '', outputFormat: '', samples: [], constraints: '', explanation: '', improvement: '', rawMarkdown: '', fallback: true, codeSnippet: '', codeCompileStatus: '' }
  }

  // 1. 尝试按 ## 标题结构化解析
  const sections = splitByHeaders(markdown)

  // 2. 提取各节内容（问题描述缺失时回退到首个 ## 之前的前言）
  const preamble = sections[''] || ''
  const description = sections['问题描述'] || ''
  const inputFormat = sections['输入格式'] || ''
  const outputFormat = sections['输出格式'] || ''
  const constraints = sections['评测用例规模与约定'] || sections['数据范围与约定'] || sections['约束'] || ''
  const explanation = sections['代码解析'] || sections['解析'] || ''
  const improvement = sections['改进建议'] || sections['改进'] || ''

  // 3. 提取样例输入/输出（可能有样例1、样例2...）
  const samples = extractSamples(sections)

  // 4. 提取代码块
  const codeSnippet = extractCCodeBlock(markdown) || ''

  // 5. 判断是否成功结构化解析
  const hasStructured = description || inputFormat || outputFormat || samples.length > 0
  const fallback = !hasStructured

  return {
    description: description || preamble,
    inputFormat,
    outputFormat,
    samples,
    constraints,
    explanation,
    improvement,
    rawMarkdown: markdown,
    fallback,
    codeSnippet,
    codeCompileStatus: '',
  }
}

/**
 * 按 ## 二级标题切分 Markdown
 * @returns {Object<string, string>} - 标题 → 内容的映射
 */
function splitByHeaders(markdown) {
  const sections = {}
  const lines = markdown.split('\n')
  let currentHeader = ''  // 空字符串表示首个 ## 之前的前言
  let currentContent = []

  for (const line of lines) {
    const headerMatch = line.match(/^##\s+(.+)/)
    if (headerMatch) {
      // 保存前一节（空 header 保存前言）
      sections[currentHeader] = currentContent.join('\n').trim()
      currentHeader = headerMatch[1].trim()
      currentContent = []
    } else {
      currentContent.push(line)
    }
  }
  // 最后一节
  sections[currentHeader] = currentContent.join('\n').trim()

  return sections
}

/**
 * 从 sections 中提取样例输入/输出对
 * 支持 "样例输入 1" / "样例输入1" / "样例输入" 等变体
 */
function extractSamples(sections) {
  const samples = []

  // 查找所有匹配的样例输入/输出键
  const inputKeys = []
  const outputKeys = []

  for (const key of Object.keys(sections)) {
    if (/样例\s*输入/i.test(key)) {
      inputKeys.push(key)
    }
    if (/样例\s*输出/i.test(key)) {
      outputKeys.push(key)
    }
  }

  // 按"序号"排序
  const extractIndex = (key) => {
    const m = key.match(/(\d+)/)
    return m ? parseInt(m[1]) : 1
  }
  inputKeys.sort((a, b) => extractIndex(a) - extractIndex(b))
  outputKeys.sort((a, b) => extractIndex(a) - extractIndex(b))

  // 配对
  const count = Math.max(inputKeys.length, outputKeys.length)
  for (let i = 0; i < count; i++) {
    const inputKey = inputKeys[i] || ''
    const outputKey = outputKeys[i] || ''
    const input = stripCodeBlock(sections[inputKey] || '')
    const output = stripCodeBlock(sections[outputKey] || '')
    samples.push({ input, output })
  }

  return samples
}

/**
 * 去掉 ```plaintext 或 ``` 包裹
 */
function stripCodeBlock(text) {
  if (!text) return ''
  // 去掉开头的 ```plaintext 或 ``` 行
  let result = text.replace(/^```(?:plaintext|text)?\s*\n?/i, '')
  // 去掉结尾的 ```
  result = result.replace(/\n?```\s*$/i, '')
  return result.trim()
}

/**
 * 从 Markdown 中提取第一个 C/C++ 代码块（c / cpp / c++）
 */
export function extractCCodeBlock(markdown) {
  if (!markdown) return ''
  // 支持 c / cpp / c++（大小写不敏感）
  const match = markdown.match(/```(?:c|cpp|c\+\+)\s*\n([\s\S]*?)```/i)
  if (match) return match[1].trim()
  // 无代码围栏但内容本身是 C 代码（以 #include 开头或含 int main），直接作为代码返回
  if (/^\s*#include/.test(markdown) || /int\s+main\s*\(/.test(markdown)) {
    return markdown.trim()
  }
  return ''
}

/**
 * 判题输出比对：逐行 trim 后对比
 * @param {string} expected - 样例期望输出
 * @param {string} actual - 实际运行输出
 * @returns {{ pass: boolean, diffLines: number[] }} - 是否通过 + 差异行号列表(1-based)
 */
export function judgeOutput(expected, actual) {
  if (expected == null || actual == null) {
    return { pass: false, diffLines: [] }
  }

  const expectedLines = expected.split('\n')
  const actualLines = actual.split('\n')

  const maxLines = Math.max(expectedLines.length, actualLines.length)
  const diffLines = []

  for (let i = 0; i < maxLines; i++) {
    const expLine = (expectedLines[i] || '').trim()
    const actLine = (actualLines[i] || '').trim()
    if (expLine !== actLine) {
      diffLines.push(i + 1)  // 1-based 行号
    }
  }

  return {
    pass: diffLines.length === 0,
    diffLines,
  }
}
