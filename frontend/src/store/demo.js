import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  demoUser, demoProfile, demoResources, demoChatHistory,
  demoWeakAreas, demoStats, demoAnalysis, demoQuestions,
  demoPath, demoCalendar, demoTimeline,
  demoAchievements, demoHeatmap, demoRagMonitor, demoKbDocuments
} from '../mock/demoData'
import api from '../api'

// 演示模式使用demo_前缀，与真实账户完全隔离
const LS = {
  onboarded: 'demo_onboarded',
  profile: 'demo_profile',
  resources: 'demo_resources',
  practice: 'demo_practice',
  heatmap: 'demo_heatmap',
  history: 'demo_practice_history',
  completedPractice: 'demo_completed_practice',
}

function load(key, fallback) {
  try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback } catch { return fallback }
}
function save(key, val) {
  try { localStorage.setItem(key, JSON.stringify(val)) } catch {}
}

function generateResources(level) {
  const all = demoResources.map(r => ({ ...r }))
  // 根据知识水平筛选合适的资源
  // Fisher-Yates 洗牌算法，保证均匀随机
  for (let i = all.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [all[i], all[j]] = [all[j], all[i]]
  }
  const count = Math.min(level >= 60 ? 6 : level >= 30 ? 4 : 3, all.length)
  const picked = all.slice(0, count).map(r => ({
    ...r,
    createdAt: new Date(Date.now() - Math.random() * 86400000 * 3).toISOString()
  }))
  return picked
}

export const useDemoStore = defineStore('demo', () => {
  const urlParams = new URLSearchParams(window.location.search)
  const urlDemo = urlParams.get('demo') === 'true'
  // 如果URL带 ?demo=true 或 localStorage 已标记，则启用演示模式
  const savedDemo = localStorage.getItem('cryd_demo_mode') === 'true'
  const enabled = ref(urlDemo || savedDemo)
  // URL带 ?demo=true 时持久化到localStorage
  if (urlDemo) localStorage.setItem('cryd_demo_mode', 'true')
  // localStorage 有标记但 ref 丢了，同步回来
  if (savedDemo && !enabled.value) enabled.value = true

  // ===== 持久化状态 =====
  const onboarded = ref(load(LS.onboarded, false))
  const savedProfile = ref(load(LS.profile, null))
  const savedResources = ref(load(LS.resources, []))
  const savedPractice = ref(load(LS.practice, { done: 0, correct: 0, wrong: 0, totalTime: 0 }))
  const savedHeatmap = ref(load(LS.heatmap, { data: [] }))
  const savedHistory = ref(load(LS.history, []))  // 历史答卷记录
  const completedPractice = ref(load(LS.completedPractice, {}))  // 已完成的练习 { type: { completed: true, score: 85 } }
  const demoChatMessages = ref(demoChatHistory.map(m => ({ ...m })))

  function resetAll() {
    onboarded.value = false
    savedProfile.value = null
    savedResources.value = []
    savedPractice.value = { done: 0, correct: 0, wrong: 0, totalTime: 0 }
    savedHeatmap.value = { data: [] }
    savedHistory.value = []
    completedPractice.value = {}
    enabled.value = false
    // 只清除demo_前缀的存储键
    localStorage.removeItem(LS.onboarded)
    localStorage.removeItem(LS.profile)
    localStorage.removeItem(LS.resources)
    localStorage.removeItem(LS.practice)
    localStorage.removeItem(LS.heatmap)
    localStorage.removeItem(LS.history)
    localStorage.removeItem(LS.completedPractice)
    // 清除今日推荐页生成的资源（避免重新导学后残留旧资源）
    localStorage.removeItem('demo_today_recommend_resources_v2')
    // 派发演示模式退出事件，通知各组件恢复初始状态
    window.dispatchEvent(new CustomEvent('demo-mode-exit'))
    // 注意：不删除 cryd_demo_mode，保持演示模式状态
  }

  // 完成导学 → 生成画像 + 资源
  function completeOnboarding(answers) {
    const a = answers
    const levelMap = {
      '学过C语言': 70, '学过其他语言': 45, '完全零基础': 20,
    }
    const paceMap = {
      '30分钟左右': 'slow', '1小时': 'steady', '2小时': 'fast', '3小时以上': 'fast',
    }
    const styleMap = {
      '看视频教程，跟着敲代码': 'visual',
      '阅读文档和教材，系统学习': 'reading',
      '直接做题，在实践中学习': 'kinesthetic',
      '混合方式，根据情况切换': 'reading',
    }
    const level = levelMap[a[0]] || 45
    const pace = paceMap[a[5]] || 'steady'
    const interest = a[4] || '嵌入式开发'
    const style = styleMap[a[3]] || 'reading'
    const minutes = parseInt((a[5] || '').match(/\d+/)?.[0] || '60')

    // 根据画像决定薄弱点
    let weakStr = '指针,内存管理,动态内存,结构体,数组'
    if (level < 30) weakStr = '指针,内存管理,动态内存,结构体,数组,控制结构,函数'
    else if (level < 60) weakStr = '指针,内存管理,动态内存,结构体,数组,函数指针'

    const profile = {
      ...demoProfile,
      knowledgeLevel: level,
      cognitiveStyle: style,
      learningPace: pace,
      interestDirection: interest,
      weakAreas: weakStr,
      totalStudyMinutes: minutes,
      progress: level,
      streakDays: 0,
    }

    const resources = generateResources(level)

    // 保存
    onboarded.value = true
    savedProfile.value = profile
    savedResources.value = resources
    save(LS.onboarded, true)
    save(LS.profile, profile)
    save(LS.resources, resources)

    return { profile, resources }
  }

  // 获取数据 — 演示模式直接返回demo数据
  function getProfile() {
    if (enabled.value) return Promise.resolve({ ...demoProfile })
    if (savedProfile.value) return Promise.resolve({ ...savedProfile.value })
    return Promise.resolve({})
  }
  function getResources() {
    // 优先返回localStorage中保存的资源，否则返回默认演示数据
    if (savedResources.value && savedResources.value.length > 0) {
      return Promise.resolve(savedResources.value.map(r => ({ ...r })))
    }
    return Promise.resolve(demoResources.map(r => ({ ...r })))
  }

  // 刷题完成后更新
  function updatePracticeResults(results, totalSeconds, chapterName) {
    if (!Array.isArray(results) || !results.length) return
    const correct = results.filter(r => r.correct).length
    const wrong = results.length - correct
    savedPractice.value = {
      done: savedPractice.value.done + results.length,
      correct: savedPractice.value.correct + correct,
      wrong: savedPractice.value.wrong + wrong,
      totalTime: savedPractice.value.totalTime + totalSeconds,
    }
    save(LS.practice, savedPractice.value)

    // 保存历史答卷记录
    const historyRecord = {
      id: Date.now(),
      chapterName: chapterName || '未知章节',
      date: new Date().toISOString(),
      totalQuestions: results.length,
      correctCount: correct,
      wrongCount: wrong,
      score: Math.round((correct / results.length) * 100),
      timeSpent: totalSeconds,
      results: results.map((r, i) => ({
        questionIndex: i,
        correct: r.correct,
        knowledgePoint: r.knowledgePoint || '',
        selectedIndex: r.selectedIndex,
      }))
    }
    savedHistory.value.unshift(historyRecord) // 添加到开头
    if (savedHistory.value.length > 50) savedHistory.value = savedHistory.value.slice(0, 50) // 最多保存50条
    save(LS.history, savedHistory.value)

    // 更新热力图
    const today = new Date().toISOString().split('T')[0]
    const hm = savedHeatmap.value
    if (!hm.data) hm.data = []
    const existing = hm.data.find(d => d.date === today)
    if (existing) existing.count += totalSeconds
    else hm.data.push({ date: today, count: totalSeconds })
    savedHeatmap.value = { ...hm }
    save(LS.heatmap, savedHeatmap.value)

    // 更新画像：根据正确率和错题知识点
    const correctRate = correct / results.length
    if (savedProfile.value) {
      const delta = Math.round((correctRate - 0.5) * 10)
      savedProfile.value.knowledgeLevel = Math.min(100, Math.max(1, (savedProfile.value.knowledgeLevel || 40) + delta))
      savedProfile.value.progress = Math.min(100, (savedProfile.value.progress || 0) + Math.round(correctRate * 5))
      const wrongKps = results.filter(r => !r.correct && r.knowledgePoint).map(r => r.knowledgePoint)
      if (wrongKps.length) {
        const existing = new Set((savedProfile.value.weakAreas || '').split(/[,，、]/).map(w => w.trim()).filter(Boolean))
        wrongKps.forEach(kp => { if (kp && !existing.has(kp)) existing.add(kp) })
        savedProfile.value.weakAreas = [...existing].join(',')
      }
      save(LS.profile, savedProfile.value)
    }
    // 推进学习路径
    if (correctRate >= 0.6 && demoPath.currentStep < demoPath.totalSteps) {
      demoPath.currentStep++
    }
  }

  // 获取历史答卷记录
  function getPracticeHistory() {
    return Promise.resolve(savedHistory.value || [])
  }

  // 检查某个练习类型是否已完成
  function isPracticeCompleted(type) {
    return completedPractice.value[type]?.completed || false
  }

  // 获取练习完成信息
  function getPracticeCompletedInfo(type) {
    return completedPractice.value[type] || null
  }

  // 标记练习为已完成
  function markPracticeCompleted(type, score) {
    completedPractice.value[type] = {
      completed: true,
      score: score,
      completedAt: new Date().toISOString()
    }
    save(LS.completedPractice, completedPractice.value)
  }

  function getStats() {
    if (!onboarded.value) return Promise.resolve({ knowledgeLevel: 0, progress: 0, errorCount: 0, completedTests: 0 })
    const p = savedPractice.value
    return Promise.resolve({
      knowledgeLevel: savedProfile.value?.knowledgeLevel ?? 0,
      progress: savedProfile.value?.progress ?? 0,
      errorCount: p.wrong,
      completedTests: p.done > 0 ? Math.ceil(p.done / 8) : 0,
    })
  }

  function getHeatmap() {
    if (!onboarded.value) return Promise.resolve({ data: [] })
    const hm = savedHeatmap.value
    if (hm.data && hm.data.length) return Promise.resolve({ ...hm })
    return Promise.resolve({ data: [] })
  }

  function getWeakAreas() {
    // Demo模式下直接返回所有薄弱点
    if (enabled.value) {
      return Promise.resolve([
        { name: '指针' }, { name: '内存管理' }, { name: '动态内存' },
        { name: '结构体' }, { name: '数组' }, { name: '函数' },
        { name: '函数指针' }, { name: '链表' }, { name: '文件操作' },
        { name: '控制结构' }, { name: '预处理' }, { name: 'C语言概述' }
      ])
    }
    if (!onboarded.value || !savedProfile.value?.weakAreas) return Promise.resolve([])
    return Promise.resolve(savedProfile.value.weakAreas.split(/[,，、]/).map(w => ({ name: w.trim() })).filter(w => w.name))
  }

  function getAnalysis() {
    if (!onboarded.value) return Promise.resolve('')
    if (savedPractice.value.done === 0) return Promise.resolve('暂无分析数据，请先完成AI导学并做一些练习题。')
    return Promise.resolve(demoAnalysis)
  }

  function getAchievements() {
    const list = demoAchievements.map(a => ({ ...a, unlocked: false }))
    if (onboarded.value) list[0].unlocked = true
    if (savedPractice.value.done > 0) { list[1].unlocked = true; list[2].unlocked = true }
    return Promise.resolve(list)
  }
  function getRagMonitorData() { return Promise.resolve({ code: 200, data: { ...demoRagMonitor } }) }
  function getKbData() { return Promise.resolve({ code: 200, data: { documents: demoKbDocuments, totalDocs: 3, totalKbs: 1 } }) }
  function getUser() { return Promise.resolve({ ...demoUser }) }
  function getChatHistory() { return Promise.resolve(demoChatMessages.value.map(m => ({ ...m }))) }
  function getQuestions() { return Promise.resolve(demoQuestions.map(q => ({ ...q }))) }
  function getPath() { if (!onboarded.value) return Promise.resolve(null); return Promise.resolve(demoPath ? { ...demoPath } : null) }
  function getCalendar() { if (!onboarded.value) return Promise.resolve(null); return Promise.resolve({ ...demoCalendar }) }
  function getTimeline() { if (!onboarded.value) return Promise.resolve([]); return Promise.resolve(demoTimeline.map(t => ({ ...t }))) }

  function sendChatMessage(message, onboardingStep) {
    console.log('sendChatMessage发送:', message, 'studentId:', demoUser.id, 'onboardingStep:', onboardingStep)
    // 演示模式导学流程 → 使用本地导学状态机，避免后端不通时卡顿
    if (onboardingStep) {
      // 首次触发（空消息）→ 启动导学
      if (!message) {
        const start = startOnboarding()
        return Promise.resolve(start)
      }
      return Promise.resolve(answerOnboarding(message))
    }
    // 演示模式普通聊天 → 直接使用本地回答，避免后端不通时卡顿 30s
    demoChatMessages.value.push({
      id: Date.now() - 1, role: 'user', content: message,
      createdAt: new Date().toISOString()
    })
    return getLocalResponse(message).then(localReply => {
      // 若返回了带 showResourceButton 的特殊对象，直接入栈
      if (localReply && localReply.showResourceButton) {
        demoChatMessages.value.push(localReply)
        return {
          message: localReply.content,
          agentName: localReply.agentName || 'AI导师',
          onboarding: false,
          onboardingComplete: false,
          onboardingStep: null,
          options: null
        }
      }
      const reply = localReply?.content || '抱歉，我无法回答这个问题。'
      const agentName = localReply?.agentName || 'AI导师'
      demoChatMessages.value.push({
        id: Date.now(), role: 'assistant', content: reply,
        agentName, createdAt: new Date().toISOString()
      })
      return {
        message: reply,
        agentName,
        onboarding: false,
        onboardingComplete: false,
        onboardingStep: null,
        options: null
      }
    })
  }

  // 本地回答（API失败时的回退）
  function getLocalResponse(message) {
    const replies = {
      // 指针相关
      '指针': '🎯 好问题！指针是C语言的核心概念。\n\n在回答之前，我想先问你两个小问题：\n1. 如果我写 `int a = 10;`，a 在内存中占多少空间？它的地址是什么？\n2. 如果我写 `int *p = &a;`，p 本身占多少空间？p 里面存的是什么？\n\n💡 提示：指针就是一个"存地址的变量"，但它存的地址指向谁呢？\n\n想明白了就告诉我，我再给你详细解释！😊',
      '指针和数组': '🎯 好问题！数组和指针的关系是C语言的经典考点。\n\n我先问你：如果 `int arr[5] = {1,2,3,4,5};`，那么 `arr` 和 `&arr[0]` 的值一样吗？\n\n💡 提示：数组名在大多数情况下会"退化"为指向首元素的指针，但它们的类型是不同的。\n\n想清楚了告诉我，我再帮你深入分析！',
      '指针运算': '🎯 好问题！指针运算是理解数组和内存的关键。\n\n我先问你：如果 `int *p` 指向一个 int，`p+1` 实际上让地址增加了多少字节？为什么不是加1？\n\n💡 提示：指针运算的步长取决于指针指向的数据类型大小。\n\n想清楚了告诉我！',

      // 数组相关
      '数组': '📦 **数组**是一块连续的内存空间，用于存储相同类型的多个元素。\n\n```c\n// 声明和初始化\nint arr[5] = {1, 2, 3, 4, 5};\n\n// 访问元素（下标从0开始）\nprintf("%d", arr[0]);  // 输出1\nprintf("%d", arr[2]);  // 输出3\n```\n\n⚠️ **重要：** C语言数组下标从0开始，不是1！',
      '二维数组': '📦 **二维数组：**\n\n```c\n// 声明3行4列的二维数组\nint matrix[3][4] = {\n    {1, 2, 3, 4},\n    {5, 6, 7, 8},\n    {9, 10, 11, 12}\n};\n\n// 访问元素\nprintf("%d", matrix[1][2]);  // 输出7\n```\n\n💡 **理解：** 二维数组可以看作"数组的数组"',

      // 内存管理
      'malloc': '💾 **malloc 动态内存分配：**\n\n```c\n#include <stdlib.h>\n\n// 分配10个int大小的内存\nint *p = (int*)malloc(10 * sizeof(int));\n\nif (p == NULL) {\n    // 分配失败\n    exit(1);\n}\n\n// 使用完毕后释放\nfree(p);\np = NULL;  // 防止野指针\n```\n\n⚠️ **重要：** malloc后必须检查是否为NULL，用完必须free！',
      '内存泄漏': '💾 **内存泄漏：**\n\n分配的内存没有释放，导致内存浪费：\n\n```c\n// ❌ 错误示例\nvoid leak() {\n    int *p = (int*)malloc(100);\n    return;  // p没有释放！\n}\n\n// ✅ 正确示例\nvoid noLeak() {\n    int *p = (int*)malloc(100);\n    free(p);  // 记得释放\n}\n```\n\n💡 **建议：** malloc和free应该成对出现！',
      'free': '💾 **free 释放内存：**\n\n```c\nint *p = (int*)malloc(100);\n// 使用p...\nfree(p);   // 释放内存\np = NULL;  // 避免野指针\n```\n\n⚠️ **注意：**\n- free只能释放malloc/calloc/realloc分配的内存\n- free后指针变为野指针，建议置为NULL\n- 不能重复free同一块内存',

      // 结构体
      '结构体': '🏗️ **结构体**是自定义的数据类型，可以组合不同类型的数据：\n\n```c\nstruct Student {\n    char name[20];\n    int age;\n    float score;\n};\n\n// 使用\nstruct Student s1 = {\"张三\", 20, 95.5};\nprintf(\"%s\", s1.name);  // 输出张三\n```\n\n💡 **访问成员：**\n- 结构体变量：`.`运算符\n- 结构体指针：`->`运算符',
      '结构体指针': '🏗️ **结构体指针：**\n\n```c\nstruct Student s1 = {\"张三\", 20, 95.5};\nstruct Student *p = &s1;\n\n// 两种访问方式等价\nprintf(\"%s\", s1.name);    // 用.运算符\nprintf(\"%s\", (*p).name);  // 解引用后用.运算符\nprintf(\"%s\", p->name);    // 用->运算符（推荐）\n```\n\n💡 **记忆：** `p->name` 等价于 `(*p).name`',

      // 控制结构
      '循环': '🔄 **C语言三种循环结构：**\n\n```c\n// for循环：已知循环次数\nfor (int i = 0; i < 10; i++) {\n    printf(\"%d \", i);\n}\n\n// while循环：先判断后执行\nwhile (condition) {\n    // 循环体\n}\n\n// do-while循环：先执行后判断\ndo {\n    // 循环体\n} while (condition);\n```\n\n💡 **区别：** do-while至少执行一次！',
      'for循环': '🔄 **for循环详解：**\n\n```c\n// 基本语法\nfor (初始化; 条件; 更新) {\n    // 循环体\n}\n\n// 示例：打印1-10\nfor (int i = 1; i <= 10; i++) {\n    printf(\"%d\\n\", i);\n}\n\n// 嵌套循环：打印乘法表\nfor (int i = 1; i <= 9; i++) {\n    for (int j = 1; j <= i; j++) {\n        printf(\"%d×%d=%d\\t\", j, i, i*j);\n    }\n    printf(\"\\n\");\n}\n```\n',
      'if语句': '🔀 **if条件语句：**\n\n```c\n// 基本语法\nif (条件) {\n    // 条件为真时执行\n} else if (其他条件) {\n    // 其他条件为真时执行\n} else {\n    // 都不满足时执行\n}\n\n// 示例：判断成绩等级\nint score = 85;\nif (score >= 90) {\n    printf(\"优秀\");\n} else if (score >= 80) {\n    printf(\"良好\");\n} else if (score >= 60) {\n    printf(\"及格\");\n} else {\n    printf(\"不及格\");\n}\n```\n',
      'switch': '🔀 **switch语句：**\n\n```c\nint day = 3;\nswitch (day) {\n    case 1:\n        printf(\"星期一\");\n        break;\n    case 2:\n        printf(\"星期二\");\n        break;\n    case 3:\n        printf(\"星期三\");\n        break;\n    default:\n        printf(\"其他\");\n}\n```\n\n⚠️ **注意：**\n- case后必须是常量\n- 别忘了break，否则会"穿透"',
      'break': '🔄 **break 和 continue：**\n\n```c\n// break：跳出当前循环\nfor (int i = 0; i < 10; i++) {\n    if (i == 5) break;  // i=5时退出循环\n    printf(\"%d \", i);  // 输出0 1 2 3 4\n}\n\n// continue：跳过本次循环\nfor (int i = 0; i < 10; i++) {\n    if (i % 2 == 0) continue;  // 跳过偶数\n    printf(\"%d \", i);  // 输出1 3 5 7 9\n}\n```\n',

      // 函数
      '函数': '⚙️ **函数基础：**\n\n```c\n// 函数声明\nint add(int a, int b);\n\n// 函数定义\nint add(int a, int b) {\n    return a + b;\n}\n\n// 函数调用\nint result = add(3, 5);  // result = 8\n```\n\n💡 **要点：**\n- 返回类型：函数返回值的类型\n- 参数列表：传入的参数\n- return：返回结果',
      '递归': '🔁 **递归函数：**\n\n函数调用自身，必须有**基准情况**（终止条件）：\n\n```c\n// 阶乘：n! = n × (n-1)!\nint factorial(int n) {\n    if (n <= 1) return 1;  // 基准情况\n    return n * factorial(n - 1);  // 递归调用\n}\n\n// 斐波那契数列\nint fib(int n) {\n    if (n <= 1) return n;\n    return fib(n-1) + fib(n-2);\n}\n```\n\n⚠️ **注意：** 递归太深会导致栈溢出！',
      '函数指针': '📌 **函数指针：**\n\n```c\n// 函数指针声明\nint (*fp)(int, int);\n\n// 指向add函数\nfp = add;\n\n// 通过函数指针调用\nint result = fp(3, 5);  // result = 8\n\n// 实际应用：回调函数\nvoid sort(int arr[], int n, int (*compare)(int, int)) {\n    // 使用compare进行比较\n}\n```\n\n💡 **用途：** 回调函数、策略模式',

      // 预处理
      '宏定义': '📋 **宏定义：**\n\n```c\n// 简单宏\n#define PI 3.14159\n#define MAX(a, b) ((a) > (b) ? (a) : (b))\n\n// 使用\ndouble area = PI * r * r;\nint big = MAX(10, 20);  // big = 20\n```\n\n⚠️ **注意：**\n- 宏在预处理阶段替换\n- 带参数的宏要加括号\n- 宏没有类型检查',
      'include': '📋 **头文件包含：**\n\n```c\n// 包含系统头文件\n#include <stdio.h>   // 标准输入输出\n#include <stdlib.h>  // 标准库\n#include <string.h>  // 字符串操作\n\n// 包含自定义头文件\n#include \"myheader.h\"\n```\n\n💡 **区别：**\n- `<>`：从系统目录查找\n- `""`：先从当前目录查找',

      // 文件操作
      '文件操作': '📁 **文件操作：**\n\n```c\n#include <stdio.h>\n\n// 打开文件\nFILE *fp = fopen(\"test.txt\", \"r\");\nif (fp == NULL) {\n    printf(\"打开失败\");\n    return;\n}\n\n// 读取文件\nchar buffer[100];\nfgets(buffer, 100, fp);\n\n// 关闭文件\nfclose(fp);\n```\n\n💡 **打开模式：**\n- `\"r\"`：只读\n- `\"w\"`：只写（覆盖）\n- `\"a\"`：追加',
      'fopen': '📁 **fopen 打开文件：**\n\n```c\nFILE *fp = fopen(\"filename\", \"mode\");\n\n// mode 参数：\n// \"r\"  - 只读\n// \"w\"  - 只写（文件不存在则创建，存在则清空）\n// \"a\"  - 追加（文件不存在则创建）\n// \"r+\" - 读写\n// \"wb\" - 二进制写\n\nif (fp == NULL) {\n    perror(\"打开失败\");\n    exit(1);\n}\n```\n',

      // 字符串
      '字符串': '📝 **字符串：**\n\nC语言字符串是字符数组，以 `\\0` 结尾：\n\n```c\n// 字符串声明\nchar str1[] = \"Hello\";  // 自动添加\\0\nchar str2[6] = {\'H\', \'e\', \'l\', \'l\', \'o\', \'\\0\'};\n\n// 字符串函数（需要string.h）\nstrlen(str1);      // 长度：5\nstrcpy(dest, src); // 复制\nstrcat(dest, src); // 连接\nstrcmp(s1, s2);    // 比较\n```\n\n⚠️ **注意：** 不能用 `=` 给字符数组赋值！',

      // 通用问题
      '你好': '你好！👋 我是你的AI学习助手。\n\n我可以帮你：\n- 📚 解答C语言知识点\n- 💡 解释代码示例\n- 🎯 针对薄弱点练习\n- 📊 分析学习进度\n\n有什么C语言问题，随时问我！',
      '数组': '🎯 好问题！数组是C语言的基础数据结构。\n\n我先问你：如果 `int arr[5] = {10, 20, 30, 40, 50};`，那么 `arr[0]` 和 `arr[5]` 哪个会出问题？为什么？\n\n💡 提示：C语言数组下标从0开始，但编译器不会帮你检查越界。\n\n想清楚了告诉我！',
      '函数': '🎯 好问题！函数是代码复用的基础。\n\n我先问你：如果我写 `int add(int a, int b) { return a + b; }`，然后调用 `add(3, 5)` 和 `add(3+2, 5)`，结果一样吗？参数是怎么传递的？\n\n💡 提示：C语言函数参数是"值传递"，传的是副本不是原值。\n\n想清楚了告诉我！',
      '结构体': '🎯 好问题！结构体是C语言自定义数据类型的核心。\n\n我先问你：如果 `struct Student { char name[20]; int age; };`，那么 `sizeof(struct Student)` 是多少？为什么不是 `sizeof(char[20]) + sizeof(int)` 那么简单？\n\n💡 提示：结构体有"内存对齐"机制，编译器会在成员之间插入填充字节。\n\n想清楚了告诉我！',
      '内存': '🎯 好问题！内存管理是C语言的高级主题。\n\n我先问你：如果我写了 `int *p = malloc(sizeof(int));` 但忘记写 `free(p);`，程序会怎样？每次运行都一样吗？\n\n💡 提示：内存泄漏不会立即崩溃，但长期运行的程序会慢慢耗尽内存。\n\n想清楚了告诉我！',
      '谢谢': '不客气！😊 很高兴能帮到你。\n\n如果还有其他问题，随时问我！祝你学习顺利！💪',
      '再见': '再见！👋 祝你学习进步！\n\n记得每天练习一点点，坚持就是胜利！💪',
      'help': '📚 **我可以帮你：**\n\n1. **知识点讲解** - 指针、数组、函数、结构体等\n2. **代码示例** - 提供可运行的代码示例\n3. **错题分析** - 分析你的错误原因\n4. **学习建议** - 根据你的水平推荐学习内容\n5. **练习推荐** - 推荐针对性练习题\n\n直接输入你想了解的内容，或者告诉我你的学习目标！',
      '你是谁': '我是你的**AI学习助手** 🤖\n\n我由先进的AI技术驱动，专门为你提供C语言学习辅导。\n\n**我的能力：**\n- 理解C语言知识点\n- 生成代码示例\n- 分析你的学习情况\n- 个性化推荐学习内容\n\n有什么问题，尽管问我！',
      '学习计划': '📅 **C语言学习计划建议：**\n\n**第1-2周：基础入门**\n- 变量、数据类型\n- 输入输出\n- 运算符\n\n**第3-4周：控制结构**\n- if/else条件语句\n- for/while循环\n- switch语句\n\n**第5-6周：函数与数组**\n- 函数定义和调用\n- 数组操作\n- 字符串处理\n\n**第7-8周：指针进阶**\n- 指针基础\n- 指针与数组\n- 动态内存分配\n\n**第9-10周：高级特性**\n- 结构体\n- 文件操作\n- 预处理\n\n💡 每天坚持练习1-2小时，效果最好！',
      '怎么学': '📚 **C语言学习方法：**\n\n**1. 理解概念**\n- 先理解为什么，再记忆怎么用\n- 画图帮助理解（如内存模型）\n\n**2. 多写代码**\n- 看完例子后自己敲一遍\n- 尝试修改代码看效果\n\n**3. 做练习题**\n- 每学完一个知识点做配套练习\n- 错题要分析原因\n\n**4. 做项目**\n- 小项目：计算器、学生管理系统\n- 大项目：链表、二叉树\n\n**5. 总结归纳**\n- 做笔记，画思维导图\n- 定期复习\n\n💡 最重要的是：**坚持每天写代码！**',
    }

    // 关键词匹配（优先匹配长关键词）
    let reply = null
    const sortedKeys = Object.keys(replies).sort((a, b) => b.length - a.length)
    for (const kw of sortedKeys) {
      if (message.includes(kw)) {
        reply = replies[kw]
        break
      }
    }

    // 没有匹配到关键词，给出通用回复
    if (!reply) {
      const lowerMsg = message.toLowerCase()
      if (lowerMsg.includes('是什么') || lowerMsg.includes('什么是') || lowerMsg.includes('啥是')) {
        reply = '这是一个好问题！💡\n\n在C语言中，这个概念很重要。建议你：\n1. 先理解基本概念\n2. 看一些代码示例\n3. 动手写代码练习\n\n你想了解哪个具体知识点？我可以详细讲解！'
      } else if (lowerMsg.includes('怎么') || lowerMsg.includes('如何') || lowerMsg.includes('怎样')) {
        reply = '关于这个问题，我来帮你分析：\n\n**学习建议：**\n1. 先理解原理\n2. 看代码示例\n3. 自己动手实践\n\n你能告诉我更具体的问题吗？这样我能给你更精准的帮助！'
      } else if (lowerMsg.includes('错了') || lowerMsg.includes('错误') || lowerMsg.includes('报错')) {
        reply = '遇到错误别担心！这是学习的一部分。💪\n\n**调试建议：**\n1. 仔细阅读错误信息\n2. 检查语法是否正确\n3. 打印变量值调试\n4. 使用IDE的调试功能\n\n你能把错误信息或代码发给我看看吗？'
      } else if (lowerMsg.includes('练习') || lowerMsg.includes('刷题') || lowerMsg.includes('做题')) {
        reply = '好的！我来推荐一些练习：\n\n📚 **今日推荐：**\n- 能力检测：综合测试\n- 巩固练习：针对薄弱点\n- 艾宾浩斯复习：错题回顾\n\n你可以去**今日推荐**页面开始练习！\n\n你想练习哪个知识点？'
      } else if (lowerMsg.includes('没明白') || lowerMsg.includes('还是不懂') || lowerMsg.includes('不明白') || lowerMsg.includes('还是不会') || lowerMsg.includes('不理解') || lowerMsg.includes('不知道') || lowerMsg.includes('不懂') || lowerMsg.includes('不清楚') || lowerMsg.includes('不会')) {
        // 用户表示不理解，推荐资源
        reply = '没关系，这个知识点确实有点难度。📚\n\n我为你准备了更详细的**学习资源**，包括：\n- 视频讲解\n- 代码示例\n- 详细文章\n\n这些资源已经添加到你的**今日推荐**中了！'
        // 返回特殊标记，让前端显示跳转按钮
        return Promise.resolve({
          id: Date.now(), role: 'assistant', agentName: 'AI导师',
          content: reply, createdAt: new Date().toISOString(),
          showResourceButton: true,
          resourceButtons: [
            { resourceId: 'today-recommend', type: 'article', title: '📚 前往今日推荐查看学习资源' }
          ]
        })
      } else {
        reply = '好问题！让我想想... 🤔\n\n在C语言中，有很多有趣的知识点。你可以问我：\n- **指针**相关问题\n- **数组**和**字符串**\n- **函数**和**递归**\n- **内存管理**\n- **文件操作**\n\n或者告诉我你想了解的具体内容！'
      }
    }

    return Promise.resolve({
      id: Date.now(), role: 'assistant', agentName: 'AI导师',
      content: reply, createdAt: new Date().toISOString()
    })
  }

  function submitAnswer(questionId, userAnswer) {
    const q = demoQuestions.find(q => q.id === questionId)
    if (!q) return Promise.resolve(null)
    const correct = userAnswer === q.answer
    return Promise.resolve({ correct, ragExplanation: correct ? '回答正确！' : q.ragExplanation, ragUsed: !correct })
  }

  function triggerAnalysis() {
    return Promise.resolve({ message: '画像已从AI导学同步更新', profile: { ...demoProfile } })
  }

  const onboardingSteps = [
    { step: 1, title: '编程经验', question: '欢迎！我是你的AI导学助理 🤖\n\n在开始学习之前，我先了解一下你的情况。**你之前有编程基础吗？**', options: ['学过C语言，有一定基础', '学过其他语言（Python/Java等）', '完全零基础，第一次接触编程'] },
    { step: 2, title: '学习目标', question: '了解了！那你**学习C语言的主要目标是什么？**', options: ['通过学校考试', '做实际项目（嵌入式/工具等）', '参加编程比赛（蓝桥杯/ACM等）', '兴趣爱好，想掌握一门底层语言'] },
    { step: 3, title: '学习难点', question: '你觉得**学习编程最大的困难**是什么？', options: ['理解抽象概念（指针/内存等）', '动手写代码时无从下手', '调试找Bug很痛苦', '记不住语法和函数'] },
    { step: 4, title: '学习风格', question: '你**更喜欢哪种学习方式？**', options: ['看视频教程，跟着敲代码', '阅读文档和教材，系统学习', '直接做题，在实践中学习', '混合方式，根据情况切换'] },
    { step: 5, title: '兴趣方向', question: '学完C语言后，你对**哪个方向最感兴趣？**', options: ['嵌入式开发 / 物联网', '游戏开发（底层引擎）', '算法竞赛 / 数据结构', '后端开发 / 系统编程'] },
    { step: 6, title: '时间投入', question: '你计划**每天花多少时间**学习C语言？', options: ['30分钟左右', '1小时', '2小时', '3小时以上'] },
    { step: 7, title: '画像生成', question: null, options: null, isComplete: true },
  ]
  const onboardingAnswers = ref([])

  function startOnboarding() {
    onboardingAnswers.value = []
    const s = onboardingSteps[0]
    return { message: s.question, agentName: 'AI导学助理', onboarding: true, onboardingStep: 1, onboardingComplete: false, options: s.options }
  }

  function answerOnboarding(text) {
    const step = onboardingAnswers.value.length
    onboardingAnswers.value.push(text)
    const next = onboardingSteps[step + 1]
    if (!next || next.isComplete) {
      const result = completeOnboarding(onboardingAnswers.value)
      return Promise.resolve({
        message: `🎉 太棒了！我已经了解你的学习情况。\n\n## 📊 学习画像总结\n\n| 维度 | 分析结果 |\n|------|----------|\n| 知识基础 | **${result.profile.knowledgeLevel}分** — ${text} |\n| 学习风格 | ${result.profile.cognitiveStyle === 'visual' ? '视觉型' : result.profile.cognitiveStyle === 'reading' ? '阅读型' : '实践型'} |\n| 学习节奏 | ${result.profile.learningPace === 'fast' ? '快速型' : result.profile.learningPace === 'steady' ? '稳健型' : '慢速型'} |\n| 薄弱环节 | ${result.profile.weakAreas} |\n\n已根据你的画像生成了 **${result.resources.length}份个性化学习资源**。去首页看看吧！`,
        agentName: 'AI导学助理', onboarding: true, onboardingStep: 7,
        onboardingComplete: true, profileUpdated: true, options: null
      })
    }
    return Promise.resolve({
      message: next.question, agentName: 'AI导学助理',
      onboarding: true, onboardingStep: next.step,
      onboardingComplete: false, options: next.options
    })
  }
  function getOnboardingAnswers() { return [...onboardingAnswers.value] }

  // 通过后端代理调用 LLM，防止 API Key 在前端泄露
  async function sendToBackend(userMessage) {
    try {
      const res = await fetch('/api/chat/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ studentId: demoUser.id || 0, message: userMessage })
      })
      if (!res.ok) return null
      const data = await res.json()
      return data?.data?.message || null
    } catch { return null }
  }

  return {
    enabled,
    onboarded, savedProfile, savedResources, savedPractice, savedHeatmap, savedHistory,
    getUser, getProfile, getResources, getChatHistory,
    getWeakAreas, getStats, getAnalysis, getQuestions,
    getPath, getCalendar, getTimeline, getPracticeHistory,
    isPracticeCompleted, getPracticeCompletedInfo, markPracticeCompleted,
    sendChatMessage, submitAnswer, triggerAnalysis,
    startOnboarding, answerOnboarding, getOnboardingAnswers,
    sendToDeepSeek: sendToBackend, getAchievements, getHeatmap, getRagMonitorData, getKbData,
    completeOnboarding, updatePracticeResults, resetAll,
  }
})
