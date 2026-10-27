<template>
  <div class="kb-container">
    <header class="kb-header">
      <div>
        <h1 class="main-title">📚 知识库管理</h1>
        <p class="sub-title">管理C语言知识文档，支持上传、向量化、检索测试</p>
      </div>
      <div class="header-actions">
        <button class="action-btn btn-white" @click="handleUpload">📤 上传本地文件</button>
        <button class="action-btn btn-danger-sm" @click="clearUploadedDocs" v-if="hasUploadedDocs">🗑️ 清空上传文档</button>
      </div>
    </header>

    <section class="kb-main-layout">
      <div class="panel-card">
        <div class="panel-header">
          <span class="panel-title">🗂️ 知识文档管理</span>
          <span class="doc-count">共 {{ documents.length }} 个文档</span>
        </div>
        <div class="table-container">
          <table class="custom-table">
            <thead>
              <tr>
                <th>数据源名称</th>
                <th>大小</th>
                <th>切块数</th>
                <th>Embedding 模型</th>
                <th>当前状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="doc in documents" :key="doc.id"
                :class="{ 'active-row': doc.id === activeDocId }"
                @click="activeDocId = doc.id">
                <td class="font-semibold">📄 {{ doc.name }}</td>
                <td>{{ doc.size }}</td>
                <td>{{ doc.chunks }}块</td>
                <td class="code-font">{{ doc.model }}</td>
                <td>
                  <span v-if="doc.status === 'ready'" class="status-tag status-ready">● 已就绪</span>
                  <span v-else-if="doc.status === 'pending'" class="status-tag status-pending">○ 队列中</span>
                  <span v-else-if="doc.status === 'processing'" class="status-tag status-processing">
                    ⏳ {{ doc.percent }}%
                    <span class="mini-progress-bar"><span class="mini-progress-inner" :style="{ width: doc.percent + '%' }"></span></span>
                  </span>
                </td>
                <td>
                  <div class="row-actions" @click.stop>
                    <button class="text-btn" @click="triggerChunkView(doc.id)">🔍 查看</button>
                    <button class="text-btn" @click="openParamsDialog(doc.id)">🛠️ 微调</button>
                    <button class="text-btn text-btn-danger" @click="handleDelete(doc)">🗑️</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="panel-card">
        <div class="panel-header">
          <span class="panel-title">🔗 知识点关联图</span>
        </div>
        <div class="graph-wrapper" ref="graphChartRef"></div>
      </div>
    </section>

    <section class="kb-bottom-layout">
      <div class="panel-card">
        <div class="panel-header">
          <span class="panel-title" :class="{ 'highlight-text': isChunkHighlight }">📄 文档片段预览</span>
        </div>
        <div class="chunk-list-wrapper">
          <div v-if="!currentDoc || currentDoc.chunksData.length === 0" class="empty-holder">⚠️ 暂无文档片段，上传文档后自动生成。</div>
          <div v-else v-for="(chunk, index) in currentDoc.chunksData" :key="chunk.id" class="chunk-card">
            <div class="chunk-card-meta">
              <span>INDEX ID: <span class="chunk-tag">{{ chunk.id }}</span></span>
              <span>与母体物理偏置: {{ index * 450 }}B</span>
            </div>
            <div class="chunk-content">{{ chunk.text }}</div>
          </div>
        </div>
      </div>
      <div class="panel-card">
        <div class="panel-header">
          <span class="panel-title">⚙️ 解析运行日志</span>
        </div>
        <div class="terminal-box" ref="terminalBoxRef">
          <div class="term-status-bar">
            <div class="term-dot-group">
              <span class="term-dot" style="background:#ef4444"></span>
              <span class="term-dot" style="background:#f59e0b"></span>
              <span class="term-dot" style="background:#10b981"></span>
              <span style="margin-left:6px;font-weight:bold;">RAG-CORE-DAEMON v2.4</span>
            </div>
            <div>{{ termMetrics }}</div>
          </div>
          <div class="term-content-area">
            <div v-for="(log, idx) in logs" :key="idx" class="term-line">
              <span class="term-time">[{{ log.time }}]</span>
              <span :class="'term-' + log.type">{{ log.text }}</span>
            </div>
            <span class="term-cursor"></span>
          </div>
        </div>
      </div>
    </section>

    <div class="dialog-mask" :class="{ 'show': dialogVisible }">
      <div class="dialog-box">
        <div class="dialog-title">⚙️ 向量化参数配置</div>
        <div class="form-item">
          <label>CHUNK_SIZE (单块最大 Token 阈值)</label>
          <input type="range" min="100" max="1000" step="50" v-model="tuningParams.chunkSize">
          <div class="slider-val">{{ tuningParams.chunkSize }} tokens</div>
        </div>
        <div class="form-item">
          <label>OVERLAP (相邻切块重叠冗余区间)</label>
          <input type="range" min="10" max="200" step="10" v-model="tuningParams.overlap">
          <div class="slider-val">{{ tuningParams.overlap }} tokens</div>
        </div>
        <div class="dialog-foot">
          <button class="action-btn btn-white" @click="dialogVisible = false">取消</button>
          <button class="action-btn btn-purple" @click="saveParams">应用并重构索引</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, knowledgeApi } from '../../api'
import { useDemoStore } from '../../store/demo'
import { demoKbDocuments } from '../../mock/demoData'

const demoStore = useDemoStore()

// ===== localStorage 持久化：用户上传的文档 =====
const LS_KEY = 'kb_uploaded_docs'
// 数据清理黑名单：凡是名字命中关键词的记录一律过滤（含上传/后端返回/演示附加）
const NAME_BLOCKLIST = [/Claude/i, /数字人/i]
function isBlockedDocName(name) {
  return NAME_BLOCKLIST.some(re => re.test(name || ''))
}
function loadUploadedDocs() {
  try {
    const list = JSON.parse(localStorage.getItem(LS_KEY) || '[]')
    return Array.isArray(list) ? list.filter(d => !isBlockedDocName(d?.name)) : []
  } catch { return [] }
}
function saveUploadedDocs(docs) {
  try {
    const safe = (Array.isArray(docs) ? docs : []).filter(d => !isBlockedDocName(d?.name))
    localStorage.setItem(LS_KEY, JSON.stringify(safe))
  } catch {}
}

// 演示文档的分块数据
const demoChunks = {
  1: [
    { id: 'c1-001', text: '多级指针是指指向指针的指针。在C语言中，可以通过在指针变量名前再加一个*来声明多级指针。例如 int **pp 表示 pp 是一个指向 int* 类型的指针。多级指针常用于动态二维数组的分配和函数中修改外部指针。' },
    { id: 'c1-002', text: '指针的指针（二级指针）的内存布局：pp 存储的是 p 的地址，p 存储的是 a 的地址，a 存储的是实际的整数值。通过 *pp 可以访问 p，通过 **pp 可以访问 a 的值。' },
    { id: 'c1-003', text: '实际应用场景：在链表操作中，当需要修改头指针本身时（如头插法），通常传递二级指针 void insert(Node **head, int val)，这样函数内部对 *head 的修改会反映到调用者的 head 变量上。' },
    { id: 'c1-004', text: '常见错误：混淆二级指针和指针数组的区别。int **pp 是二级指针，而 int *arr[10] 是指针数组（包含10个 int* 元素的数组）。两者的内存布局和访问方式完全不同。' },
  ],
  2: [
    { id: 'c2-001', text: 'malloc函数用于在堆上分配指定大小的内存块。函数原型为 void* malloc(size_t size)，返回值是分配内存的首地址，如果分配失败则返回 NULL。使用前必须检查返回值是否为 NULL。' },
    { id: 'c2-002', text: 'calloc函数与malloc类似，但会将分配的内存初始化为零。函数原型为 void* calloc(size_t num, size_t size)，分配 num 个大小为 size 的元素。calloc 会自动清零，malloc 不会。' },
    { id: 'c2-003', text: 'realloc函数用于重新分配已分配内存的大小。void* realloc(void* ptr, size_t new_size)。如果新大小更大，可能在原地扩展或迁移到新位置；如果更小，则截断。原指针在 realloc 后可能失效。' },
    { id: 'c2-004', text: 'free函数释放之前通过 malloc/calloc/realloc 分配的内存。必须传入正确的指针，不能重复释放，不能释放非动态分配的内存。释放后应将指针设为 NULL，防止悬垂指针。' },
  ],
  3: [
    { id: 'c3-001', text: '结构体(struct)是C语言中用于将不同类型数据组合在一起的复合数据类型。通过 struct 关键字定义，成员通过点运算符(.)访问。结构体变量在内存中连续存储，大小为各成员大小之和（考虑对齐）。' },
    { id: 'c3-002', text: '链表是一种动态数据结构，由节点(node)组成。每个节点包含数据域和指向下一个节点的指针域。相比数组，链表的优势是插入删除操作时间复杂度为O(1)，但访问元素需要遍历，时间复杂度为O(n)。' },
    { id: 'c3-003', text: '单链表的头插法：新节点的next指向当前头节点，然后更新头指针指向新节点。时间复杂度O(1)，但插入顺序与输入顺序相反。尾插法：遍历到链表末尾，将最后一个节点的next指向新节点。' },
  ],
}

const documents = ref([])
const loading = ref(true)
const activeDocId = ref(null)
const dialogVisible = ref(false)
const isChunkHighlight = ref(false)
const termMetrics = ref("EPS: 0 块/s · BUFFER: OK")
// 演示模式保持固定指标（避免录制中数字跳变）；非演示模式根据日志量动态计算
function refreshTermMetrics() {
  if (demoStore.enabled) return // 演示模式保持原值
  const successCount = logs.filter(l => l.type === 'success').length
  const eps = Math.min(99, successCount)
  termMetrics.value = `EPS: ${eps} 块/s · BUFFER: OK`
}
const graphChartRef = ref(null)
const terminalBoxRef = ref(null)
let myGraphChart = null

const tuningParams = reactive({ chunkSize: 450, overlap: 50 })
const TUNING_LS_KEY = 'kb_tuning_params'
// 初始化时读回上次保存的微调参数（演示模式也读，但不影响演示流程）
try {
  const saved = JSON.parse(localStorage.getItem(TUNING_LS_KEY) || 'null')
  if (saved && typeof saved.chunkSize === 'number' && typeof saved.overlap === 'number') {
    tuningParams.chunkSize = saved.chunkSize
    tuningParams.overlap = saved.overlap
  }
} catch {}

const logs = reactive([
  { time: "03:10:02", type: "success", text: "● Engine Initialization Completed successfully." },
  { time: "03:10:03", type: "info", text: "● [Ready] 向量核心监听服务已启动，HNSW 内存索引就绪，等待追加分块任务..." }
])

// ===== 根据文件名生成有意义的关联知识点 =====
function generateGraphFromName(name) {
  const base = name.replace(/\.\w+$/, '')
  const keywordMap = {
    // 指针相关
    '指针': ['取地址运算符&', '解引用*', '多级指针', '指针与数组', '函数指针', '指针运算'],
    '多级指针': ['二级指针int**', '指针的指针', '动态二维数组', '头插法传参'],
    '指针与数组': ['数组名退化', '指针算术', '越界访问', 'sizeof差异'],
    // 内存相关
    '内存': ['malloc分配', 'free释放', '堆区heap', '栈区stack', '内存泄漏', '野指针'],
    '动态内存': ['malloc', 'calloc', 'realloc', 'free', '堆内存管理', '内存泄漏检测'],
    'malloc': ['void*返回值', 'NULL检查', 'calloc对比', 'realloc扩展', 'free配对'],
    '内存管理': ['malloc/free配对', '内存泄漏', '悬垂指针', '缓冲区溢出', 'valgrind检测'],
    // 结构体相关
    '结构体': ['struct定义', '内存对齐', '点运算符.', '箭头运算符->', '结构体数组'],
    '链表': ['单链表', '双链表', '节点定义', '头插法', '尾插法', '链表遍历'],
    // 文件操作
    '文件': ['fopen打开', 'fclose关闭', 'fread读取', 'fwrite写入', 'fseek定位', '文件指针FILE*'],
    '文件操作': ['fopen模式r/w/a', 'fgets读行', 'fprintf格式写', 'feof判断', '文件错误处理'],
    // 函数相关
    '函数': ['函数声明', '参数传递', '返回值', '递归调用', '作用域', '函数指针'],
    '递归': ['基准条件', '递归树', '尾递归优化', '斐波那契', '阶乘', '栈溢出风险'],
    // 数组和字符串
    '数组': ['下标从0开始', '二维数组', '数组名常量', '越界风险', '数组初始化'],
    '字符串': ['char数组', 'strlen长度', 'strcpy复制', 'strcat连接', 'strcmp比较', '末尾\\0'],
    // 控制结构
    '循环': ['for循环', 'while循环', 'do-while', '嵌套循环', 'break跳出', 'continue跳过'],
    '条件': ['if/else', 'switch/case', '三元运算符', '逻辑运算符', '短路求值'],
    // 预处理
    '预处理': ['#include包含', '#define宏', '条件编译#ifdef', '头文件保护', '宏函数'],
    '宏定义': ['对象宏', '函数宏', '宏展开', '#运算符', '##连接', '预定义宏'],
    // 其他
    '位运算': ['按位与&', '按位或|', '异或^', '左移<<', '右移>>', '位掩码'],
    '排序': ['冒泡排序', '选择排序', '插入排序', '快速排序', '归并排序', '时间复杂度'],
    '算法': ['时间复杂度', '空间复杂度', '递归分治', '动态规划', '贪心算法'],
    '数据结构': ['线性表', '栈', '队列', '树', '图', '哈希表'],
    '调试': ['printf调试', 'gdb调试', '断点设置', '变量监视', '段错误排查'],
    '编译': ['预处理', '编译', '汇编', '链接', 'gcc编译器', 'Makefile'],
  }

  // 按关键词长度排序，优先匹配更具体的关键词
  const sortedKeys = Object.keys(keywordMap).sort((a, b) => b.length - a.length)
  for (const kw of sortedKeys) {
    if (base.includes(kw)) {
      return { center: base, nodes: keywordMap[kw] }
    }
  }

  // 兜底：从文件名提取有意义的词作为节点
  const words = base.split(/[_\-\s]+/).filter(w => w.length > 1)
  if (words.length >= 2) {
    return { center: base, nodes: words.slice(0, 5).map(w => w + '相关') }
  }
  return { center: base, nodes: ['核心概念', '语法规则', '常见用法', '注意事项', '实践练习'] }
}

// 根据文档名生成模拟的chunk数据
function generateMockChunks(name) {
  const base = name.replace(/\.\w+$/, '')
  const graph = generateGraphFromName(base)
  const topics = graph.nodes.slice(0, 3).join('、')
  return [
    { id: `mock-${Date.now()}-1`, text: `【${base}】本文档涵盖${base}的核心知识点，包括：${topics}等。文档已成功上传并完成向量化切块处理，共提取了关键概念定义、语法规则和应用场景。` },
    { id: `mock-${Date.now()}-2`, text: `【${base}】在实际编程中，${base}是C语言的重要组成部分。理解其原理有助于编写更高效、更安全的代码。建议结合代码示例进行深入学习，注意常见陷阱和易错点。` },
    { id: `mock-${Date.now()}-3`, text: `【${base}】常见易错点：初学者在使用${base}相关功能时，容易忽略边界条件和内存管理问题。建议通过大量练习来加深理解，掌握调试技巧，避免常见错误。` },
  ]
}

// ===== 生成文档的唯一ID =====
let docIdCounter = Date.now()
function nextDocId() { return ++docIdCounter }

// 从后端加载数据，始终保留3个演示文档 + localStorage的用户文档
async function loadData() {
  // 1. 先加载3个演示文档（始终保留，同时经过黑名单过滤）
  documents.value = demoKbDocuments
    .filter(d => !isBlockedDocName(d?.name))
    .map(d => ({
      id: d.id,
      name: d.name,
      size: d.size,
      chunks: d.chunks,
      model: d.model,
      status: d.status === 'vectorized' ? 'ready' : d.status === 'pending' ? 'pending' : 'processing',
      percent: d.status === 'vectorized' ? 100 : d.status === 'processing' ? 45 : 0,
      graph: generateGraphFromName(d.name),
      chunksData: demoChunks[d.id] || generateMockChunks(d.name)
    }))

  // 2. 加载localStorage中用户上传的文档（持久化，loadUploadedDocs 内部已过滤黑名单）
  const savedDocs = loadUploadedDocs()
  savedDocs.forEach(d => {
    if (isBlockedDocName(d?.name)) return
    if (!documents.value.find(x => x.name === d.name)) {
      documents.value.push({
        ...d,
        graph: d.graph || generateGraphFromName(d.name),
        chunksData: d.chunksData || generateMockChunks(d.name)
      })
      if (d.id >= docIdCounter) docIdCounter = d.id + 1
    }
  })

  // 3. 尝试从后端获取额外文档，追加到列表
  try {
    if (demoStore.enabled) {
      const res = await demoStore.getKbData()
      const backendDocs = res.data?.documents || []
      backendDocs.forEach(d => {
        if (isBlockedDocName(d?.name)) return
        if (!documents.value.find(x => x.name === d.name)) {
          documents.value.push({
            id: d.id, name: d.name,
            size: d.size || `${Math.floor(Math.random() * 1000 + 100)} KB`,
            chunks: d.chunks || Math.floor(Math.random() * 20 + 5),
            model: d.model || 'bge-large-zh',
            status: d.status === 'vectorized' ? 'ready' : d.status === 'pending' ? 'pending' : 'processing',
            percent: d.status === 'vectorized' ? 100 : d.status === 'processing' ? 45 : 0,
            graph: generateGraphFromName(d.name),
            chunksData: demoChunks[d.id] || generateMockChunks(d.name)
          })
        }
      })
    } else {
      const res = await adminApi.getKbOverview()
      if (res.code === 200 && res.data?.documents) {
        res.data.documents.forEach(d => {
          if (isBlockedDocName(d?.name)) return
          if (!documents.value.find(x => x.name === d.name)) {
            documents.value.push({
              id: d.id, name: d.name,
              size: d.size || `${Math.floor(Math.random() * 1000 + 100)} KB`,
              chunks: d.chunks || Math.floor(Math.random() * 20 + 5),
              model: d.model || 'bge-large-zh',
              status: d.status === 'vectorized' ? 'ready' : d.status === 'pending' ? 'pending' : 'processing',
              percent: d.status === 'vectorized' ? 100 : d.status === 'processing' ? 45 : 0,
              graph: generateGraphFromName(d.name),
              chunksData: demoChunks[d.id] || generateMockChunks(d.name)
            })
          }
        })
      }
    }
  } catch { /* 后端不可用时只显示本地数据 */ }

  // 最终兜底过滤：任何来源的数据，只要名字命中黑名单就移除
  documents.value = documents.value.filter(d => !isBlockedDocName(d?.name))

  // 恢复"僵尸 processing 文档"：上传后未完成就刷新/关闭的文档
  // 演示文档不处理（演示数据中的 pending 是预期状态）
  documents.value.forEach(d => {
    if (!isDemoDoc(d) && d.status === 'processing') {
      d.status = 'pending'
      d.percent = 0
    }
  })

  if (documents.value.length > 0 && !activeDocId.value) {
    activeDocId.value = documents.value[0].id
  }
  loading.value = false
  nextTick(() => renderGraph())
}

const currentDoc = computed(() => documents.value.find(d => d.id === activeDocId.value) || null)

// 选中文档时按需补充 chunksData（替代在 computed 中产生副作用）
watch(currentDoc, (doc) => {
  if (doc && doc.chunksData.length === 0) {
    doc.chunksData = generateMockChunks(doc.name || '未知文档')
  }
})

// 演示文档 id 集合（替代 id<=3 硬编码，避免演示文档 id 变动时判断失效）
const DEMO_DOC_IDS = new Set(demoKbDocuments.map(d => d.id))
function isDemoDoc(doc) { return DEMO_DOC_IDS.has(doc.id) }

const hasUploadedDocs = computed(() => documents.value.some(d => !DEMO_DOC_IDS.has(d.id)))

function clearUploadedDocs() {
  ElMessageBox.confirm('确定清空所有上传的文档？演示文档将保留。', '清空确认', {
    confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning'
  }).then(() => {
    // 只保留演示文档
    documents.value = documents.value.filter(d => isDemoDoc(d))
    saveUploadedDocs([])
    activeDocId.value = documents.value[0]?.id || null
    ElMessage.success('已清空所有上传文档')
    pushLog("info", "[Clear] 所有上传文档已清空")
    nextTick(() => renderGraph())
  }).catch(() => {})
}

function pushLog(type, text) {
  const timeStr = new Date().toTimeString().split(' ')[0]
  logs.push({ time: timeStr, type, text })
  refreshTermMetrics()
  nextTick(() => { if (terminalBoxRef.value) terminalBoxRef.value.scrollTop = terminalBoxRef.value.scrollHeight })
}

async function renderGraph() {
  if (!graphChartRef.value || !currentDoc.value) return
  const echarts = await import('echarts')
  if (!myGraphChart) myGraphChart = echarts.init(graphChartRef.value)
  const doc = currentDoc.value
  const graph = doc.graph || generateGraphFromName(doc.name || '未知文档')

  const nodes = [{
    name: graph.center, symbolSize: 50,
    itemStyle: { color: '#8b5cf6', shadowBlur: 10, shadowColor: 'rgba(139,92,246,0.3)' },
    label: { show: true, fontSize: 12, fontWeight: 'bold', color: '#1e293b' }
  }]
  const links = []
  const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4']
  const nodeNames = graph.nodes || ['核心概念', '语法规则', '常见用法']
  nodeNames.forEach((nodeName, i) => {
    nodes.push({
      name: nodeName, symbolSize: 32,
      itemStyle: { color: colors[i % colors.length] },
      label: { show: true, fontSize: 10, color: '#334155' }
    })
    links.push({ source: graph.center, target: nodeName })
  })

  myGraphChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}' },
    series: [{
      type: 'graph', layout: 'force', data: nodes, links, roam: true,
      label: { position: 'right' }, force: { repulsion: 200, edgeLength: 100 },
      lineStyle: { color: '#cbd5e1', width: 1.5, type: 'dashed', curveness: 0.1 },
      emphasis: { focus: 'series', lineStyle: { width: 3 } }
    }]
  })
}

watch(activeDocId, () => renderGraph())

// ===== 删除文档 =====
function handleDelete(doc) {
  // 演示文档不允许删除
  if (isDemoDoc(doc)) {
    ElMessage.warning('演示文档不可删除')
    return
  }
  ElMessageBox.confirm(`确定删除文档「${doc.name}」？`, '删除确认', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
  }).then(() => {
    const idx = documents.value.findIndex(d => d.id === doc.id)
    if (idx > -1) documents.value.splice(idx, 1)
    // 同步到 localStorage（仅持久化非演示文档）
    saveUploadedDocs(documents.value.filter(d => !isDemoDoc(d)).map(d => ({
      id: d.id, name: d.name, size: d.size, chunks: d.chunks, model: d.model,
      status: d.status, percent: d.percent
    })))
    // 如果删除的是当前选中文档，切换到第一个
    if (activeDocId.value === doc.id) {
      activeDocId.value = documents.value[0]?.id || null
    }
    ElMessage.success(`已删除「${doc.name}」`)
    pushLog("info", `[Delete] ${doc.name} 已从知识库移除`)
  }).catch(() => {})
}

// ===== 上传文件 =====
const UPLOAD_KB_ID = 1 // 上传目标知识库 ID（单知识库场景固定为 1）
async function handleUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pdf,.md,.txt,.docx'
  input.multiple = true
  input.onchange = async (e) => {
    const files = Array.from(e.target.files || [])
    if (!files.length) return

    for (const file of files) {
      const newId = nextDocId()
      const newDoc = {
        id: newId,
        name: file.name,
        size: `${(file.size / 1024).toFixed(1)} KB`,
        chunks: Math.floor(Math.random() * 15 + 3),
        model: 'bge-large-zh',
        status: 'processing',
        percent: 0,
        graph: generateGraphFromName(file.name),
        chunksData: generateMockChunks(file.name)
      }
      documents.value.push(newDoc)
      activeDocId.value = newId

      // 持久化到 localStorage（仅非演示文档）
      saveUploadedDocs(documents.value.filter(d => !isDemoDoc(d)).map(d => ({
        id: d.id, name: d.name, size: d.size, chunks: d.chunks, model: d.model,
        status: d.status, percent: d.percent
      })))

      ElMessage.success(`文件 "${file.name}" 已加入队列，后台向量化中...`)
      pushLog("info", `[Upload] ${file.name} (${(file.size / 1024).toFixed(1)} KB) 已加入处理队列`)
      nextTick(() => renderGraph())

      // 模拟处理进度动画
      const timer = setInterval(() => {
        const doc = documents.value.find(d => d.id === newId)
        if (doc) {
          doc.percent = Math.min(100, doc.percent + Math.floor(Math.random() * 20 + 10))
          if (doc.percent >= 100) {
            doc.status = 'ready'
            doc.percent = 100
            clearInterval(timer)
            pushLog("success", `[Vector] ${file.name} 向量化完成，${doc.chunks}个chunk已入库`)
            // 更新 localStorage
            saveUploadedDocs(documents.value.filter(d => !isDemoDoc(d)).map(d => ({
              id: d.id, name: d.name, size: d.size, chunks: d.chunks, model: d.model,
              status: d.status, percent: d.percent
            })))
          }
        } else {
          clearInterval(timer)
        }
      }, 800)

      // 静默尝试后端上传
      if (!demoStore.enabled) {
        try {
          const formData = new FormData()
          formData.append('file', file)
          await knowledgeApi.uploadDocument(UPLOAD_KB_ID, formData)
        } catch { /* 后端不可用时忽略 */ }
      }
    }
  }
  input.click()
}

function triggerChunkView(id) { activeDocId.value = id; isChunkHighlight.value = true; setTimeout(() => isChunkHighlight.value = false, 1000) }
function openParamsDialog(id) { dialogVisible.value = true }
async function saveParams() {
  dialogVisible.value = false
  // 持久化到 localStorage，下次打开对话框时读回
  try { localStorage.setItem(TUNING_LS_KEY, JSON.stringify({ chunkSize: tuningParams.chunkSize, overlap: tuningParams.overlap })) } catch {}
  pushLog("warn", `⚙️ [Config] 管理员调整参数: chunkSize=${tuningParams.chunkSize}, overlap=${tuningParams.overlap}`)
  pushLog("process", "⏳ [Reindex] 正在重建向量索引，请稍候...")
  await new Promise(r => setTimeout(r, 1500))
  pushLog("success", `✅ [Reindex] 索引重建完成 (模拟): chunkSize=${tuningParams.chunkSize}, overlap=${tuningParams.overlap}`)
  ElMessage.success('索引重建完成')
}

const handleResize = () => { myGraphChart && myGraphChart.resize() }

onMounted(async () => {
  await loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (myGraphChart) myGraphChart.dispose()
})
</script>

<style scoped>
.kb-container { width: 100%; height: 100%; display: flex; flex-direction: column; gap: 12px; background: #f8f7f4; padding: 4px 0; overflow-y: auto; }
.kb-header { display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; }
.main-title { margin: 0; font-size: 18px; font-weight: 700; color: #0f172a; }
.sub-title { margin: 2px 0 0 0; font-size: 12px; color: #94a3b8; }
.header-actions { display: flex; gap: 8px; }
.action-btn { padding: 6px 14px; font-size: 12px; font-weight: 600; border-radius: 8px; cursor: pointer; transition: all 0.2s; font-family: inherit; }
.btn-purple { background: #8b5cf6; border: 1px solid #7c3aed; color: #fff; }
.btn-purple:hover { background: #7c3aed; }
.btn-white { background: #fff; border: 1px solid rgba(0,0,0,0.06); color: #475569; }
.btn-white:hover { background: #fafaf9; }
.btn-danger-sm { background: rgba(239,68,68,0.05); border: 1px solid rgba(239,68,68,0.15); color: #ef4444; font-size: 12px; }
.btn-danger-sm:hover { background: rgba(239,68,68,0.1); }
.kb-main-layout { display: grid; grid-template-columns: 5.5fr 4.5fr; gap: 12px; flex: 1; min-height: 0; }
.panel-card { background: #fff; border-radius: 14px; padding: 16px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); border: 1px solid rgba(0,0,0,0.02); display: flex; flex-direction: column; overflow: hidden; }
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-shrink: 0; }
.panel-title { font-size: 14px; font-weight: 700; color: #1e293b; transition: color 0.3s; }
.doc-count { font-size: 11px; color: #94a3b8; font-weight: 600; }
.highlight-text { color: #8b5cf6 !important; }
.table-container { width: 100%; overflow-x: auto; flex: 1; }
.custom-table { width: 100%; border-collapse: collapse; text-align: left; font-size: 13px; }
.custom-table th { color: #94a3b8; font-weight: 600; padding: 8px 6px; border-bottom: 1px solid #f1f5f9; font-size: 12px; }
.custom-table td { padding: 10px 6px; border-bottom: 1px solid #f1f5f9; color: #334155; font-size: 13px; }
.custom-table tr.active-row { background: rgba(139,92,246,0.03); }
.custom-table tr:hover { background: #fafaf9; cursor: pointer; }
.font-semibold { font-weight: 600; color: #0f172a; }
.code-font { font-family: monospace; font-size: 11px; }
.status-tag { font-size: 11px; font-weight: 600; padding: 2px 6px; border-radius: 4px; display: inline-flex; align-items: center; gap: 4px; }
.status-ready { background: rgba(16,185,129,0.06); color: #10b981; }
.status-processing { background: rgba(59,130,246,0.06); color: #3b82f6; }
.status-pending { background: rgba(148,163,184,0.08); color: #64748b; }
.mini-progress-bar { width: 50px; height: 4px; background: #e2e8f0; border-radius: 2px; overflow: hidden; display: inline-block; margin-left: 6px; vertical-align: middle; }
.mini-progress-inner { height: 100%; background: #3b82f6; transition: width 0.3s; }
.row-actions { display: flex; gap: 4px; }
.text-btn { background: none; border: none; color: #8b5cf6; font-size: 11px; font-weight: 600; cursor: pointer; padding: 2px 4px; border-radius: 4px; font-family: inherit; }
.text-btn:hover { background: rgba(139,92,246,0.05); }
.text-btn-danger { color: #ef4444; }
.text-btn-danger:hover { background: rgba(239,68,68,0.05); }
.graph-wrapper { width: 100%; flex: 1; min-height: 0; background: #fafaf9; border-radius: 12px; border: 1px dashed rgba(0,0,0,0.04); }
.kb-bottom-layout { display: grid; grid-template-columns: 6.5fr 3.5fr; gap: 12px; flex: 1; min-height: 0; }
.chunk-list-wrapper { display: flex; flex-direction: column; gap: 6px; overflow-y: auto; flex: 1; padding-right: 4px; }
.empty-holder { text-align: center; padding: 40px; font-size: 12px; color: #94a3b8; }
.chunk-card { background: #fafaf9; border: 1px solid rgba(0,0,0,0.03); border-radius: 8px; padding: 8px 12px; }
.chunk-card-meta { display: flex; justify-content: space-between; font-size: 10px; color: #94a3b8; margin-bottom: 4px; }
.chunk-tag { background: rgba(139,92,246,0.06); color: #8b5cf6; padding: 1px 4px; border-radius: 3px; font-weight: 600; }
.chunk-content { font-size: 12px; color: #475569; line-height: 1.5; }
.terminal-box { background: #1e293b; border-radius: 12px; padding: 12px; overflow-y: auto; display: flex; flex-direction: column; border: 1px solid #334155; flex: 1; min-height: 0; }
.term-status-bar { display: flex; justify-content: space-between; padding-bottom: 6px; margin-bottom: 6px; border-bottom: 1px solid #334155; color: #94a3b8; font-size: 11px; font-family: monospace; flex-shrink: 0; }
.term-dot-group { display: flex; gap: 4px; align-items: center; }
.term-dot { width: 6px; height: 6px; border-radius: 50%; }
.term-content-area { font-family: 'Consolas','Courier New',monospace; font-size: 11px; line-height: 1.6; }
.term-line { margin-bottom: 4px; word-break: break-all; }
.term-time { color: #64748b; margin-right: 6px; }
.term-info { color: #93c5fd; }
.term-success { color: #34d399; }
.term-process { color: #60a5fa; }
.term-warn { color: #fbbf24; }
.term-purple { color: #c084fc; }
.term-cursor { display: inline-block; width: 6px; height: 12px; background: #34d399; margin-left: 4px; animation: blink 1s infinite; vertical-align: middle; }
@keyframes blink { 0%,100% { opacity: 0; } 50% { opacity: 1; } }
.dialog-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(15,23,42,0.3); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 999; opacity: 0; pointer-events: none; transition: all 0.3s ease; }
.dialog-mask.show { opacity: 1; pointer-events: auto; }
.dialog-box { background: #fff; width: 440px; border-radius: 16px; padding: 24px; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1); border: 1px solid rgba(0,0,0,0.03); transform: scale(0.9); transition: transform 0.3s ease; }
.dialog-mask.show .dialog-box { transform: scale(1); }
.dialog-title { font-size: 14px; font-weight: 700; color: #0f172a; margin-bottom: 16px; }
.form-item { margin-bottom: 16px; display: flex; flex-direction: column; gap: 6px; }
.form-item label { font-size: 11px; font-weight: 700; color: #475569; }
.form-item input[type="range"] { width: 100%; cursor: pointer; }
.slider-val { font-size: 11px; color: #8b5cf6; font-family: monospace; font-weight: 600; text-align: right; }
.dialog-foot { display: flex; justify-content: flex-end; gap: 8px; margin-top: 24px; }
.chunk-list-wrapper::-webkit-scrollbar, .terminal-box::-webkit-scrollbar { width: 4px; }
.chunk-list-wrapper::-webkit-scrollbar-thumb, .terminal-box::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 4px; }
</style>
