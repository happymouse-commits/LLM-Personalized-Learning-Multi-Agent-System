<template>
  <div class="onboarding-page">
    <div class="app-container">

      <!-- ================= 左侧面板：AI导学助手 ================= -->
      <aside class="left-panel" style="padding-top: 20px !important; justify-content: center;">
        <!-- 背景装饰 -->
        <div class="bg-glow"></div>

        <!-- 助手头部 -->
        <div class="assistant-header">
          <div class="assistant-info">
            <span class="online-dot"></span>
            <div>
              <h4 class="assistant-name">AI 导学助手小泉</h4>
            </div>
          </div>
        </div>

        <!-- 虚拟人头像 -->
        <div class="avatar-area">
          <div class="avatar-glow"></div>
          <div class="avatar-wrapper">
            <img src="/images/avatar.png" alt="AI Virtual Assistant" class="avatar-img">
            <div class="live-badge">
              <span class="live-dot"></span>
              <span class="live-text">LIVE</span>
            </div>
          </div>
        </div>

        <!-- AI对话气泡 -->
        <div class="chat-bubble">
          <p class="chat-text">{{ aiBubbleText }}</p>
          <div v-if="aiSpeaking" class="ai-speaking-indicator">
            <span class="speaking-dot"></span>
            <span class="speaking-text">正在思考...</span>
          </div>
        </div>

        <!-- 推荐提示词 -->
        <div class="quick-prompts">
          <span class="prompts-label">你可以这样说：</span>
          <div class="prompts-tags">
            <button v-for="tag in quickTags" :key="tag" class="prompt-tag" @click="handleVoiceInput(tag)">
              {{ tag }}
            </button>
          </div>
        </div>

        <!-- 占位空间，让麦克风固定在底部 -->
        <div class="flex-spacer"></div>

        <!-- 底部麦克风 -->
        <div class="mic-area" style="margin-top: 8px !important;">
          <div class="wave-bars">
            <span class="wave-bar" style="animation-delay: -0.2s; height: 12px;"></span>
            <span class="wave-bar" style="animation-delay: -0.4s; height: 18px;"></span>
            <span class="wave-bar" style="animation-delay: -0.6s; height: 8px;"></span>
          </div>
          <button class="mic-btn"
            @mousedown.prevent="startRecording"
            @touchstart.prevent="startRecording" @touchend="stopRecording" @touchcancel="stopRecording"
            :class="{ recording: isRecording, processing: isProcessing }">
            <!-- 处理中动画 -->
            <div v-if="isProcessing" class="processing-ring"></div>
            <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
              <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/>
              <path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
            </svg>
          </button>
          <div class="wave-bars">
            <span class="wave-bar" style="animation-delay: -0.6s; height: 8px;"></span>
            <span class="wave-bar" style="animation-delay: -0.4s; height: 18px;"></span>
            <span class="wave-bar" style="animation-delay: -0.2s; height: 12px;"></span>
          </div>
        </div>
        <span class="mic-hint">{{ isProcessing ? 'AI思考中...' : '点击说话（可选）' }}</span>
      </aside>

      <!-- ================= 中间面板：问卷主体 ================= -->
      <main class="center-panel">
        <!-- 步骤指示器 -->
        <div class="step-indicators">
          <template v-for="i in totalSteps" :key="i">
            <div class="step-item">
              <div :class="['step-circle', { active: currentStep === i, completed: currentStep > i }]">
                {{ currentStep > i ? '✓' : i }}
              </div>
              <span :class="['step-label', { active: currentStep === i }]">{{ stepLabels[i - 1] }}</span>
            </div>
            <div v-if="i < totalSteps" class="step-line">
              <div class="step-line-fill" :style="{ width: currentStep > i ? '100%' : '0%' }"></div>
            </div>
          </template>
        </div>

        <!-- 问题区域 -->
        <div class="question-area">
          <transition name="slide-fade" mode="out-in">
            <div :key="currentStep" class="step-content">
              <span class="step-tag">Step {{ currentStep }} / {{ totalSteps }} {{ stepLabels[currentStep - 1] }}</span>
              <h2 class="question-title">{{ questions[currentStep - 1] }}</h2>
              <p v-if="stepHints[currentStep - 1]" class="question-hint">{{ stepHints[currentStep - 1] }}</p>

              <!-- 单选选项 -->
              <div v-if="!steps[currentStep - 1].multi" class="options-grid">
                <div v-for="(opt, idx) in steps[currentStep - 1].options" :key="idx"
                  :class="['option-card', { selected: selections[currentStep - 1] === idx }]"
                  @click="selectOption(currentStep - 1, idx)">
                  <div class="option-left">
                    <span v-if="steps[currentStep - 1].icons" class="option-icon">{{ steps[currentStep - 1].icons[idx] }}</span>
                    <span class="option-text">{{ opt }}</span>
                  </div>
                  <div class="radio-indicator">
                    <div class="radio-dot"></div>
                  </div>
                </div>
              </div>

              <!-- 多选选项 -->
              <div v-else :class="[steps[currentStep - 1].options.length > 6 ? 'options-wrap' : 'options-grid']">
                <div v-for="(opt, idx) in steps[currentStep - 1].options" :key="idx"
                  :class="['option-card', { selected: multiSelections[currentStep - 1].includes(idx) }]"
                  @click="toggleMultiSelect(currentStep - 1, idx)">
                  <span class="option-text">{{ opt }}</span>
                  <div class="radio-indicator">
                    <div class="radio-dot"></div>
                  </div>
                </div>
              </div>

              <!-- 温馨提示 -->
              <p class="tip-text" v-if="steps[currentStep - 1].multi">
                <span class="tip-dot"></span>
                可多选，帮助AI优先推荐相关内容
              </p>
            </div>
          </transition>
        </div>

        <!-- 底部按钮 -->
        <div class="nav-buttons">
          <button class="btn btn-prev" :class="{ disabled: currentStep === 1 }" @click="prevStep">
            ← 返回上一步
          </button>
          <button class="btn btn-skip" @click="skipOnboarding">跳过导学</button>
          <button class="btn btn-next" @click="nextStep" :disabled="!canProceed">
            {{ currentStep === totalSteps ? '🎯 开始能力检测' : '下一步' }}
          </button>
        </div>
      </main>

      <!-- ================= 右侧面板：进度与成果 ================= -->
      <aside class="right-panel">
        <!-- 导学进度卡片 -->
        <div class="progress-card">
          <div class="progress-header">
            <h4 class="progress-title">导学进度</h4>
            <span class="progress-count">{{ currentStep }} / {{ totalSteps }}</span>
          </div>
          <div class="progress-bar">
            <div class="progress-bar-fill" :style="{ width: (currentStep / totalSteps * 100) + '%' }"></div>
          </div>
          <span class="progress-estimate">预计剩余时间：{{ estimatedTime }} 分钟</span>
        </div>

        <!-- 生成成果清单 -->
        <div class="output-list">
          <h4 class="output-title">我们将为您生成：</h4>
          <div class="output-items">
            <div v-for="(item, idx) in outputItems" :key="idx" class="output-item">
              <span class="output-icon" :style="{ background: item.bg, color: item.color }">
                {{ item.emoji }}
              </span>
              <div>
                <h5 class="output-name">{{ item.name }}</h5>
                <p class="output-desc">{{ item.desc }}</p>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>



    <!-- 加载动画 -->
    <transition name="fade">
      <div v-if="showLoading" class="loading-overlay">
        <div class="loading-card">
          <div class="spinner-box">
            <div class="pulse-circle"></div>
            <div class="pulse-circle"></div>
            <div class="pulse-core"></div>
          </div>
          <h3 class="loading-title">{{ loadingMessage }}</h3>
          <div class="loading-steps">
            <div v-for="(step, idx) in loadingSteps" :key="idx"
              :class="['loading-step', loadingStep > idx ? 'done' : loadingStep === idx ? 'active' : '']">
              {{ loadingStep > idx ? '✓' : loadingStep === idx ? '●' : '○' }} {{ step }}
            </div>
          </div>
          <div class="loading-progress">
            <div class="loading-progress-bar"></div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { useDemoStore } from '../../store/demo'
import api from '../../api'
import { speakWithTts, stopTts } from '../../utils/tts'

const router = useRouter()
const store = useUserStore()
const demoStore = useDemoStore()

const currentStep = ref(1)
const totalSteps = 6
const selections = ref([null, null, null, null, null, null])
const multiSelections = ref([[], [], [], [], [], []])
const showLoading = ref(false)
const loadingStep = ref(0)
const loadingMessage = ref('正在分析你的学习画像...')
const loadingSteps = ref([])

// 快捷标签：根据当前题目的选项动态生成
const quickTags = computed(() => {
  const step = steps[currentStep.value - 1]
  if (!step || !step.options) return []
  // 单选题取前3个选项，去掉emoji
  return step.options.slice(0, 3).map(opt =>
    opt.replace(/[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu, '').trim()
  )
})

const stepLabels = ['学习基础', '学习目标', '学习时间', '学习方式', '知识兴趣', '学习困难']

const questions = [
  '你的 C 语言基础如何？',
  '你希望通过学习 C 语言实现什么目标？',
  '每天计划学习多久？',
  '你更喜欢哪种学习方式？',
  '你更想先学习哪些内容？',
  '你觉得学习 C 语言最大的困难是什么？'
]

const stepHints = [
  '',
  '可多选',
  '',
  '',
  '可多选，帮助AI优先推荐相关内容',
  '可多选，AI会针对性辅导'
]

// AI对话气泡文字
const aiBubbleText = ref('你好呀！我是你的AI导学助手小泉😊 在开始学习之前，我想先简单了解一下你，这样可以为你制定更合适的学习计划~')

const estimatedTime = computed(() => {
  return Math.max(1, (totalSteps - currentStep.value) * 1)
})

const outputItems = [
  { name: '个性化学习画像', desc: '多维度评估你目前的专长与痛点', emoji: '👤', bg: '#ecfdf5', color: '#10b981' },
  { name: '专属学习路径', desc: '跳过冗余，1:1定制高能知识地图', emoji: '🗺️', bg: '#eff6ff', color: '#3b82f6' },
  { name: '推荐学习资源', desc: '精准适配符合你的难度练习', emoji: '📖', bg: '#eef2ff', color: '#6366f1' },
  { name: '智能学习计划', desc: '按周智能提醒，助力成果通关', emoji: '📅', bg: '#faf5ff', color: '#a855f7' },
  { name: '学习效果评估', desc: '跟踪每一项小进步与阶段飞跃', emoji: '🏅', bg: '#fffbeb', color: '#f59e0b' },
]

// 步骤数据
const steps = [
  {
    options: ['🌱 完全没接触过', '📖 学过语法，但不会写程序', '💻 能完成基础实验', '🚀 能独立完成中小型程序']
  },
  {
    options: ['🎓 通过课程考试', '🏆 蓝桥杯/ACM等竞赛', '💼 完成课程设计或项目', '📚 系统学习C语言', '🧑‍💻 为C++/数据结构打基础'],
    multi: true
  },
  {
    options: ['⚡ 15分钟以内', '📖 30分钟左右', '📚 1小时左右', '🔥 2小时以上']
  },
  {
    options: ['📺 视频讲解', '📖 图文教程', '💻 边学边练', '🎯 刷题巩固', '🔄 综合学习（推荐）'],
    icons: ['📺', '📖', '💻', '🎯', '🔄']
  },
  {
    options: ['基础语法', '顺序、选择、循环', '数组', '函数', '指针', '字符串', '结构体', '文件操作', '动态内存', '综合项目'],
    multi: true
  },
  {
    options: ['😵 看不懂代码', '🤯 指针太难', '📝 不知道怎么做题', '💻 调试困难', '📚 知识点容易忘', '❓ 不知道学到哪了'],
    multi: true
  }
]

const canProceed = computed(() => {
  switch (currentStep.value) {
    case 1: return selections.value[0] !== null
    case 2: return multiSelections.value[1].length > 0
    case 3: return selections.value[2] !== null
    case 4: return selections.value[3] !== null
    case 5: return multiSelections.value[4].length > 0
    case 6: return multiSelections.value[5].length > 0
    default: return false
  }
})

let autoAdvanceTimer = null  // 单选自动跳转的定时器，语音录制时取消
// 跟踪 completeOnboarding 中的所有 setTimeout，便于卸载时清理
const loadingTimers = []

function selectOption(stepIdx, optionIdx) {
  selections.value[stepIdx] = optionIdx
  // 单选后自动跳下一步（仅对非多选步骤）
  const currentStepData = steps[currentStep.value - 1]
  if (!currentStepData.multi && currentStep.value < totalSteps) {
    clearTimeout(autoAdvanceTimer)
    autoAdvanceTimer = setTimeout(() => { currentStep.value++ }, 600)
  }
}

function toggleMultiSelect(stepIdx, optionIdx) {
  const arr = multiSelections.value[stepIdx]
  const idx = arr.indexOf(optionIdx)
  if (idx === -1) arr.push(optionIdx)
  else arr.splice(idx, 1)
}

function nextStep() {
  if (!canProceed.value) return
  if (currentStep.value === totalSteps) {
    completeOnboarding()
    return
  }
  currentStep.value++
}

function prevStep() {
  if (currentStep.value > 1) currentStep.value--
}

// ===== TTS 语音朗读 =====
const aiSpeaking = ref(false)

function speakText(text, callbacks) {
  if (!text) return
  const cleanText = text.replace(/[😊🤖💡🎯📚✨🚀]/g, '').trim()
  if (!cleanText) return
  speakWithTts(cleanText, {
    onStart: () => { aiSpeaking.value = true; callbacks?.onStart?.() },
    onEnd: () => { aiSpeaking.value = false; callbacks?.onEnd?.() },
    onError: () => {
      aiSpeaking.value = false
      if (callbacks?.onError) { callbacks.onError() }
      else { callbacks?.onEnd?.() }
    }
  })
}

function stopSpeaking() {
  stopTts()
  aiSpeaking.value = false
}

// 步骤切换 → 统一朗读下一题（不管谁触发的都走这里）
let stepSpeakTimer = null
watch(currentStep, (newStep) => {
  if (newStep < 1 || newStep > totalSteps) return
  clearTimeout(stepSpeakTimer)
  stopTts()
  aiBubbleText.value = questions[newStep - 1]
  stepSpeakTimer = setTimeout(() => speakText(questions[newStep - 1]), 300)
})

// ===== 语音识别（讯飞ASR） =====
const isRecording = ref(false)
const isProcessing = ref(false)
const voiceText = ref('')
const voiceFollowUpCount = ref(0)
let audioContext = null
let mediaStreamSource = null
let scriptProcessor = null
let mediaStream = null
let pcmChunks = []
let isStopping = false  // 防止 stopRecording 重复调用

// Float32 → Int16（16bit PCM）
function float32ToInt16(float32) {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]))
    int16[i] = s < 0 ? s * 0x8000 : s * 0x7FFF
  }
  return int16
}

// 线性降采样到 16kHz
function downsampleTo16k(buffer, fromRate) {
  if (fromRate === 16000) return buffer
  const ratio = fromRate / 16000
  const newLength = Math.round(buffer.length / ratio)
  const result = new Float32Array(newLength)
  let offsetResult = 0
  let offsetBuffer = 0
  while (offsetResult < newLength) {
    const nextOffsetBuffer = Math.round((offsetResult + 1) * ratio)
    let accum = 0, count = 0
    for (let i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) {
      accum += buffer[i]
      count++
    }
    result[offsetResult] = count > 0 ? accum / count : 0
    offsetResult++
    offsetBuffer = nextOffsetBuffer
  }
  return result
}

async function startRecording() {
  if (isRecording.value) return
  stopSpeaking()
  clearTimeout(autoAdvanceTimer)

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true }
    })
    // 使用 Web Audio API 直接采集 PCM，避免 WebM→PCM 转码导致格式不匹配
    audioContext = new (window.AudioContext || window.webkitAudioContext)()
    // Chrome 自动策略要求用户交互后才能启动 AudioContext，必须显式 resume
    if (audioContext.state === 'suspended') {
      await audioContext.resume()
    }
    const sourceRate = audioContext.sampleRate
    mediaStreamSource = audioContext.createMediaStreamSource(mediaStream)
    // 使用 AudioWorkletNode 采集 PCM（ScriptProcessorNode 已废弃）
    const workletCode = `
      class PCMCollectorProcessor extends AudioWorkletProcessor {
        process(inputs) {
          const input = inputs[0]
          if (input && input[0]) {
            const chunk = new Float32Array(input[0])
            this.port.postMessage(chunk, [chunk.buffer])
          }
          return true
        }
      }
      registerProcessor('pcm-collector-processor', PCMCollectorProcessor)
    `
    const workletUrl = URL.createObjectURL(new Blob([workletCode], { type: 'application/javascript' }))
    await audioContext.audioWorklet.addModule(workletUrl)
    URL.revokeObjectURL(workletUrl)
    scriptProcessor = new AudioWorkletNode(audioContext, 'pcm-collector-processor', {
      numberOfInputs: 1,
      numberOfOutputs: 1,
      outputChannelCount: [1]
    })
    pcmChunks = []
    scriptProcessor.port.onmessage = (e) => {
      const resampled = downsampleTo16k(e.data, sourceRate)
      pcmChunks.push(float32ToInt16(resampled))
    }

    // 静音输出，避免麦克风回声
    const zeroGain = audioContext.createGain()
    zeroGain.gain.value = 0
    mediaStreamSource.connect(scriptProcessor)
    scriptProcessor.connect(zeroGain)
    zeroGain.connect(audioContext.destination)

    isRecording.value = true
    voiceText.value = ''
    aiBubbleText.value = '正在聆听，请说话...'
    // 在 document 上监听 mouseup，确保鼠标移出按钮后松开也能停止录音
    document.addEventListener('mouseup', stopRecording, { once: true })
  } catch (e) {
    console.warn('麦克风访问失败:', e)
    aiBubbleText.value = '无法访问麦克风，请检查浏览器权限设置。'
  }
}

function stopRecording() {
  // 防止重复调用（mouseup 在 document 上只触发一次，但其他事件可能重复触发）
  if (isStopping || !isRecording.value) return
  isStopping = true
  isRecording.value = false
  // 清除 document 上的 mouseup 监听（防止泄漏）
  document.removeEventListener('mouseup', stopRecording)

  // 断开音频处理链 — 先停 stream 再断处理链，确保 pcmChunks 数据完整
  if (mediaStream) { mediaStream.getTracks().forEach(t => t.stop()); mediaStream = null }
  if (scriptProcessor) { scriptProcessor.disconnect(); scriptProcessor = null }
  if (mediaStreamSource) { mediaStreamSource.disconnect(); mediaStreamSource = null }
  // audioContext.close() 是异步的，延迟关闭让残留 onmessage 回调正常完成
  const ctx = audioContext
  audioContext = null
  if (ctx) { setTimeout(() => ctx.close().catch(() => {}), 200) }

  if (pcmChunks.length === 0) {
    isStopping = false
    return
  }

  isProcessing.value = true
  aiBubbleText.value = '🤔 正在识别语音...'

  try {
    // 合并 PCM 分片
    const totalLength = pcmChunks.reduce((sum, chunk) => sum + chunk.length, 0)
    const pcmData = new Int16Array(totalLength)
    let offset = 0
    for (const chunk of pcmChunks) {
      pcmData.set(chunk, offset)
      offset += chunk.length
    }
    pcmChunks = []

    // Int16 → base64
    const bytes = new Uint8Array(pcmData.buffer)
    let binary = ''
    const chunkSize = 0x8000
    for (let i = 0; i < bytes.length; i += chunkSize) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize))
    }
    const base64 = btoa(binary)

    // 发送到后端讯飞识别（PCM 16kHz 16bit 单声道，与 XunfeiAsrService 要求一致）
    api.post('/voice/recognize', { audio: base64 }).then(res => {
      const text = res?.data?.text || ''
      isProcessing.value = false
      isStopping = false

      if (!text) {
        aiBubbleText.value = '没有听清，请再试一次~'
        speakText('没有听清，请再试一次')
        return
      }

      voiceText.value = text
      aiBubbleText.value = `🎤 你说："${text}"`

      // 先尝试本地关键词匹配（零延迟）
      const matched = handleVoiceInput(text)
      if (matched) {
        voiceFollowUpCount.value = 0
        if (currentStep.value < totalSteps) {
          clearTimeout(autoAdvanceTimer)
          // 本地匹配成功后先说"好的"，读完再跳题，避免静默跳转
          speakText('好的', {
            onEnd: () => { currentStep.value++ },
            onError: () => { currentStep.value++ }
          })
        }
        return
      }
      // 本地匹配失败，调 DeepSeek
      setTimeout(() => sendMessageToAi(text), 300)
    }).catch(e => {
      console.warn('语音识别失败:', e)
      isProcessing.value = false
      isStopping = false
      aiBubbleText.value = '语音识别失败，请重试或直接选择选项~'
      speakText('语音识别失败，请重试，或直接点击选项')
    })
  } catch (e) {
    console.warn('语音识别失败:', e)
    isProcessing.value = false
    isStopping = false
    aiBubbleText.value = '语音识别失败，请重试或直接选择选项~'
    speakText('语音识别失败，请重试，或直接点击选项')
  }
}

let voiceRequestPending = false  // 防止语音请求并发

async function sendMessageToAi(text) {
  if (voiceRequestPending) return  // 上一个请求还没处理完，跳过
  voiceRequestPending = true
  console.log('[语音] sendMessageToAi called:', text, '当前步骤:', currentStep.value)
  isProcessing.value = true
  aiBubbleText.value = `🎤 你说："${text}"\n\n🤔 AI正在理解...`
  try {
    const currentQuestion = questions[currentStep.value - 1]
    const currentOptions = steps[currentStep.value - 1].options.join('、')
    // 使用专门的快速解析提示，减少 LLM 响应时间
    const res = await api.post('/chat/send', {
      studentId: store.id || 0,
      message: `【导学问题${currentStep.value}】${currentQuestion}\n选项：${currentOptions}\n用户语音回答：${text}`,
      onboardingStep: currentStep.value
    })

    const reply = res?.data?.message || res?.message || '好的，收到~'
    const voiceSufficient = res?.data?.voiceSufficient
    isProcessing.value = false

    if (voiceSufficient === false) {
      // 无效回答 → 追问，不跳转
      if (voiceFollowUpCount.value < 1) {
        // 第一次无效：追问一次
        voiceFollowUpCount.value++
        aiBubbleText.value = reply
        speakText(reply)
      } else {
        // 第二次无效：语音引导用户改用点击选项，不跳题
        voiceFollowUpCount.value = 0
        const fallback = '好像还是没听懂呢，你可以直接点击下方的选项告诉我哦'
        aiBubbleText.value = fallback
        speakText(fallback)
      }
    } else {
      // 有效 → 说"好的" → 朗读完再跳转
      voiceFollowUpCount.value = 0
      aiBubbleText.value = reply
      handleVoiceInput(text)
      if (currentStep.value < totalSteps) {
        clearTimeout(autoAdvanceTimer)
        speakText(reply, {
          onEnd: () => { currentStep.value++ },  // "好的"读完再跳
          onError: () => { currentStep.value++ }  // 失败也跳
        })
      }
    }
  } catch (e) {
    console.warn('AI理解失败:', e)
    isProcessing.value = false
    aiBubbleText.value = '网络异常，请重试或直接选择选项~'
    speakText('网络异常，请重试，或直接点击选项')
  } finally {
    voiceRequestPending = false
  }
}

function handleVoiceInput(text) {
  if (currentStep.value > totalSteps) return false
  const stepIdx = currentStep.value - 1
  const options = steps[stepIdx].options
  const normalizedText = text.toLowerCase().trim()
  let matched = false

  // 语音回答的常见别名映射，增强匹配能力
  const voiceAliases = {
    '零基础': ['没有学过', '没学过', '什么都不懂', '没接触过', '完全不会', '从零开始'],
    '学过语法': ['学过一点', '学过一些', '懂一点', '了解一点', '入门了'],
    '基础实验': ['能写', '会写', '做过实验', '写过程序', '能完成'],
    '中小型程序': ['独立完成', '写过项目', '做过项目', '比较熟练', '很熟练'],
    '通过考试': ['考试', '及格', '过考', '期末'],
    '竞赛': ['蓝桥杯', 'ACM', '比赛', '竞赛'],
    '项目': ['课程设计', '做项目', '大作业'],
    '系统学习': ['系统', '全面', '从头学'],
    '数据结构': ['C++', '数据结构', '打基础'],
    '15分钟': ['很短', '没时间', '很忙'],
    '30分钟': ['半小时', '一点时间', '少一点'],
    '1小时': ['一小时', '一小时左右', '中等'],
    '2小时': ['两个小时', '很多时间', '充足'],
    '视频': ['看视频', '听课', '看教程'],
    '图文': ['看文章', '阅读', '看文字', '看文档'],
    '边学边练': ['边练', '动手', '实践', '敲代码'],
    '刷题': ['做题', '刷题', '练习'],
    '综合': ['都要', '混合', '综合', '都来'],
  }

  if (steps[stepIdx].multi) {
    const matchedIndices = []
    for (let i = 0; i < options.length; i++) {
      const cleanOption = options[i].replace(/[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu, '').trim()
      // 直接匹配
      if (normalizedText.includes(cleanOption) || cleanOption.includes(normalizedText)) {
        matchedIndices.push(i)
        continue
      }
      // 别名匹配
      for (const [key, aliases] of Object.entries(voiceAliases)) {
        if (cleanOption.includes(key) && aliases.some(a => normalizedText.includes(a))) {
          matchedIndices.push(i)
          break
        }
      }
    }
    if (matchedIndices.length > 0) {
      multiSelections.value[stepIdx] = matchedIndices
      matched = true
    }
  } else {
    for (let i = 0; i < options.length; i++) {
      const cleanOption = options[i].replace(/[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/gu, '').trim()
      // 直接匹配
      if (normalizedText.includes(cleanOption) || cleanOption.includes(normalizedText)) {
        selections.value[stepIdx] = i
        matched = true
        break
      }
      // 别名匹配
      for (const [key, aliases] of Object.entries(voiceAliases)) {
        if (cleanOption.includes(key) && aliases.some(a => normalizedText.includes(a))) {
          selections.value[stepIdx] = i
          matched = true
          break
        }
      }
      if (matched) break
    }
  }
  return matched
}

// 页面加载时预初始化语音识别，然后 AI 朗读第一个问题
onMounted(() => {
  // 延迟缩短到300ms，加快首题朗读
  setTimeout(() => {
    speakText(questions[0])
  }, 300)
})

// ===== 完成导学 =====
async function completeOnboarding() {
  stopSpeaking()
  showLoading.value = true
  loadingStep.value = 0

  const goalTexts = multiSelections.value[1].map(i => steps[1].options[i])
  const interestTexts = multiSelections.value[4].map(i => steps[4].options[i])
  const difficultyTexts = multiSelections.value[5].map(i => steps[5].options[i])

  const experienceText = steps[0].options[selections.value[0]] || '零基础'
  const styleText = steps[3].options[selections.value[3]] || '综合学习'
  const selectedDifficulties = multiSelections.value[5].map(i => steps[5].options[i].replace(/[🌱📖💻🚀🎯📋]/g, '').trim())
  const mainDifficulty = selectedDifficulties[0] || 'C语言基础'

  loadingSteps.value = [
    `分析学习基础（${experienceText.replace(/[🌱📖💻🚀]/g, '').trim()}）`,
    '构建个性化学习画像',
    `生成${styleText.replace(/[🎯📋]/g, '').trim()}学习路径`,
    `AI针对「${mainDifficulty}」出题`,
    '能力检测准备完成'
  ]

  loadingMessage.value = '正在分析你的学习画像...'

  const profile = {
    experience: steps[0].options[selections.value[0]] || '零基础',
    goals: goalTexts,
    dailyTime: steps[2].options[selections.value[2]] || '30分钟左右',
    style: steps[3].options[selections.value[3]] || '综合学习',
    interests: interestTexts,
    difficulties: difficultyTexts,
    completed: true,
    createdAt: new Date().toISOString()
  }

  if (demoStore.enabled) {
    // 演示模式使用 demo_ 前缀，与路由守卫 demoGet('onboarded') 对齐
    localStorage.setItem('demo_onboarded', 'true')
    localStorage.setItem('demo_profile', JSON.stringify(profile))
  } else {
    try {
      await api.put('/profile/' + store.id, profile)
      localStorage.setItem('cryd_onboarded', 'true')
    } catch (error) {
      console.error('保存画像失败:', error)
    }
  }

  const loadSteps = [
    { delay: 500, message: '正在分析你的学习画像...', step: 0 },
    { delay: 700, message: `已识别基础: ${experienceText.replace(/[🌱📖💻🚀]/g, '').trim()}`, step: 1 },
    { delay: 600, message: `正在生成${styleText.replace(/[🎯📋]/g, '').trim()}学习路径...`, step: 2 },
    { delay: 800, message: `AI正在针对「${mainDifficulty}」生成题目...`, step: 3 },
    { delay: 500, message: '能力检测准备完成，即将开始', step: 4 },
  ]

  let totalDelay = 0
  loadSteps.forEach(({ delay, message, step }) => {
    totalDelay += delay
    loadingTimers.push(setTimeout(() => {
      loadingMessage.value = message
      loadingStep.value = step
    }, totalDelay))
  })

  loadingTimers.push(setTimeout(() => {
    showLoading.value = false
    router.push('/student/fullscreen-practice/能力检测?from=onboarding')
  }, totalDelay + 300))
}

function skipOnboarding() {
  stopSpeaking()
  // 演示模式使用 demo_ 前缀，与路由守卫对齐
  const onboardedKey = demoStore.enabled ? 'demo_onboarded' : 'cryd_onboarded'
  localStorage.setItem(onboardedKey, 'true')
  router.push('/student/fullscreen-practice/能力检测?from=onboarding')
}

onBeforeUnmount(() => {
  stopSpeaking()
  // 清理 completeOnboarding 中的所有 setTimeout，避免卸载后触发 router.push
  loadingTimers.forEach(t => clearTimeout(t))
  loadingTimers.length = 0
  if (autoAdvanceTimer) { clearTimeout(autoAdvanceTimer); autoAdvanceTimer = null }
  if (stepSpeakTimer) { clearTimeout(stepSpeakTimer); stepSpeakTimer = null }
  // 仅释放录音硬件资源（麦克风、AudioContext），不触发 ASR 处理流程
  isRecording.value = false
  isStopping = false
  document.removeEventListener('mouseup', stopRecording)
  if (scriptProcessor) { try { scriptProcessor.disconnect() } catch {} ; scriptProcessor = null }
  if (mediaStreamSource) { try { mediaStreamSource.disconnect() } catch {} ; mediaStreamSource = null }
  if (mediaStream) { mediaStream.getTracks().forEach(t => t.stop()); mediaStream = null }
  if (audioContext) { try { audioContext.close() } catch {} ; audioContext = null }
})
</script>

<style scoped>
.onboarding-page {
  min-height: 100vh;
  width: 100vw;
  background: linear-gradient(135deg, #f0f4ff 0%, #e8edf5 50%, #f0f0ff 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif;
  padding: 20px;
}

.app-container {
  width: 88%;
  max-width: 1500px;
  height: calc(100vh - 40px);
  max-height: 880px;
  background: white;
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(13, 32, 82, 0.08), 0 0 0 1px rgba(0,0,0,0.03);
  display: flex;
  overflow: hidden;
}

/* ===== 左侧面板 ===== */
.left-panel {
  width: 28%;
  min-width: 280px;
  background: linear-gradient(180deg, #EFF5FF 0%, #F4F8FF 50%, #FAF9FF 100%);
  border-right: 1px solid #f1f5f9;
  padding-top: 60px !important;
  padding-left: 40px !important;
  padding-right: 40px !important;
  padding-bottom: 30px !important;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.bg-glow {
  position: absolute;
  top: -48px;
  left: -48px;
  width: 192px;
  height: 192px;
  border-radius: 50%;
  background: rgba(191, 219, 254, 0.2);
  filter: blur(48px);
  pointer-events: none;
}

.assistant-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 260px;
}

.assistant-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.online-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.assistant-name {
  font-size: 16px;
  font-weight: 700;
  color: #334155;
}

/* 虚拟人头像 */
.avatar-area {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 20px 0 4px 0;
  width: 80%;
}

.avatar-glow {
  position: absolute;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: rgba(96, 165, 250, 0.12);
  filter: blur(40px);
  pointer-events: none;
}

.avatar-wrapper {
  position: relative;
  width: 200px;
  height: 220px;
  z-index: 1;
  overflow: hidden;
  border-radius: 16px;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
}

.live-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  padding: 2px 8px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 4px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  animation: pulse 2s ease-in-out infinite;
}

.live-text {
  font-size: 8px;
  color: white;
  font-weight: 900;
  letter-spacing: 0.1em;
}

/* AI对话气泡 */
.chat-bubble {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  padding: 10px 14px;
  border-radius: 14px;
  box-shadow: 0 4px 16px rgba(13, 32, 82, 0.04);
  border: 1px solid white;
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 280px;
  min-height: 80px;
  max-height: 120px;
  overflow-y: auto;
}

.chat-text {
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
}

.ai-speaking-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 4px 0;
}

.speaking-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
  animation: speakPulse 1s ease-in-out infinite;
}

.speaking-text {
  font-size: 10px;
  color: #2563eb;
  font-weight: 600;
}

@keyframes speakPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}

.audio-progress-fill.animating {
  animation: audioProgress 2s ease-in-out infinite;
}

@keyframes audioProgress {
  0% { width: 10%; }
  50% { width: 80%; }
  100% { width: 10%; }
}

.audio-bar {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f8fafc;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
}

.play-btn {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #2563eb;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  cursor: pointer;
  flex-shrink: 0;
}

.audio-progress {
  flex: 1;
  height: 4px;
  background: #e2e8f0;
  border-radius: 2px;
  overflow: hidden;
}

.audio-progress-fill {
  background: #2563eb;
  height: 100%;
  width: 35%;
}

.audio-time {
  font-size: 9px;
  color: #94a3b8;
  font-weight: 700;
}

/* 快捷提示 */
.quick-prompts {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 280px;
}

.prompts-label {
  font-size: 10px;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.05em;
  display: block;
  margin-bottom: 6px;
}

.prompts-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.prompt-tag {
  font-size: 10px;
  font-weight: 500;
  color: #475569;
  background: white;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
  cursor: pointer;
  transition: all 0.2s;
}

.prompt-tag:hover {
  background: #eff6ff;
  color: #2563eb;
  border-color: #bfdbfe;
}

/* 麦克风区域 */
.mic-area {
  border-top: 1px solid rgba(241, 245, 249, 0.6);
  padding-top: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px !important;
}

/* 弹性占位空间 */
.flex-spacer {
  height: 12px;
  flex: none;
}

.wave-bars {
  display: flex;
  gap: 3px;
  align-items: flex-end;
  height: 24px;
  opacity: 0.4;
}

.wave-bar {
  display: inline-block;
  width: 3px;
  background: #3b82f6;
  border-radius: 9999px;
  transform-origin: bottom;
  animation: wave-bounce 1.2s infinite ease-in-out;
}

@keyframes wave-bounce {
  0%, 100% { transform: scaleY(0.3); }
  50% { transform: scaleY(1); }
}

.mic-btn {
  width: 48px;
  height: 48px;
  background: #2563eb;
  color: white;
  border: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
  transition: all 0.2s;
}

.mic-btn:hover { background: #1d4ed8; }
.mic-btn:active { transform: scale(0.95); }
.mic-btn.recording {
  background: #ef4444;
  box-shadow: 0 0 0 8px rgba(239, 68, 68, 0.15);
  animation: pulse-rec 1.2s infinite;
}
.mic-btn.processing {
  background: #f59e0b;
  box-shadow: 0 0 0 8px rgba(245, 158, 11, 0.15);
  animation: pulse-process 1s infinite;
  cursor: not-allowed;
}
.mic-btn.processing svg { animation: spin 1s linear infinite; }

.processing-ring {
  position: absolute;
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes pulse-process {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
  50% { box-shadow: 0 0 0 10px rgba(245, 158, 11, 0); }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes pulse-rec {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
}

.mic-hint {
  font-size: 10px;
  color: #94a3b8;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-align: center;
  display: block;
  margin-top: 4px;
}

/* ===== 中间面板 ===== */
.center-panel {
  flex: 1;
  background: white;
  padding: 24px 48px 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow-y: auto;
}

/* 步骤指示器 */
.step-indicators {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 500px;
  margin: 20px auto 20px;
  gap: 0;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 0;
  white-space: nowrap;
}

.step-circle {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #f1f5f9;
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  transition: all 0.3s;
  flex-shrink: 0;
}

.step-circle.active {
  background: #2563eb;
  color: white;
}

.step-circle.completed {
  background: #2563eb;
  color: white;
}

.step-label {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  display: none;
}

.step-label.active {
  display: none;
}

.step-line {
  flex: 1;
  height: 2px;
  background: #f1f5f9;
  margin: 0 10px;
}

.step-line-fill {
  height: 100%;
  background: #2563eb;
  transition: width 0.5s;
}

/* 问题区域 */
.question-area {
  max-width: 620px;
  width: 100%;
  margin: 0 auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.step-tag {
  font-size: 12px;
  font-weight: 800;
  color: #2563eb;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.question-title {
  font-size: 26px;
  font-weight: 900;
  color: #1e293b;
  margin-top: 6px;
  letter-spacing: -0.02em;
  line-height: 1.3;
}

.question-hint {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 8px;
  margin-bottom: 28px;
}

.options-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 0;
}

.options-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 0;
}

.options-wrap .option-card {
  flex: 0 0 calc(33.333% - 8px);
}

.option-card {
  border: 1.5px solid #e8ecf1;
  border-radius: 14px;
  padding: 18px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  background: white;
  transition: all 0.25s ease;
}

.option-card:hover {
  border-color: #93c5fd;
  background: #fafcff;
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.08);
  transform: translateY(-1px);
}

.option-card.selected {
  border: 2px solid #3b82f6;
  background: rgba(37, 99, 235, 0.04);
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.1);
}

.option-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.option-icon {
  font-size: 20px;
}

.option-text {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.option-card.selected .option-text {
  color: #2563eb;
}

.radio-indicator {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s ease;
  flex-shrink: 0;
}

.option-card.selected .radio-indicator {
  border-color: #2563eb;
}

.radio-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #2563eb;
  transform: scale(0);
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.option-card.selected .radio-dot {
  transform: scale(1);
}

.tip-text {
  font-size: 11px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 4px;
  margin-top: 14px;
}

.tip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f59e0b;
}

/* 导航按钮 */
.nav-buttons {
  max-width: 640px;
  width: 100%;
  margin: 8px auto 0;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.btn {
  height: 48px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  cursor: pointer;
  transition: all 0.25s ease;
  border: none;
  background: transparent;
  font-family: inherit;
}

.btn-prev {
  color: #64748b;
  padding: 0 16px;
}

.btn-prev:hover { color: #1e293b; }
.btn-prev.disabled { opacity: 0; visibility: hidden; pointer-events: none; }

.btn-skip {
  color: #94a3b8;
  padding: 0 16px;
  font-size: 13px;
}

.btn-skip:hover { color: #64748b; }

.btn-next {
  background: #2563eb;
  color: white;
  padding: 0 32px;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.25);
}

.btn-next:hover:not(:disabled) {
  background: #1d4ed8;
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(37, 99, 235, 0.35);
}

.btn-next:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== 右侧面板 ===== */
.right-panel {
  width: 22%;
  min-width: 220px;
  border-left: 1px solid #f1f5f9;
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 20px;
  background: linear-gradient(180deg, #fafbff 0%, #ffffff 100%);
  overflow-y: auto;
}

.progress-card {
  background: #f8fafc;
  border: 1px solid rgba(241, 245, 249, 0.6);
  border-radius: 14px;
  padding: 16px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.progress-title {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.progress-count {
  font-size: 12px;
  font-weight: 800;
  color: #2563eb;
}

.progress-bar {
  width: 100%;
  background: #e2e8f0;
  height: 6px;
  border-radius: 3px;
  overflow: hidden;
  margin-top: 12px;
}

.progress-bar-fill {
  background: #2563eb;
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s;
}

.progress-estimate {
  font-size: 10px;
  color: #94a3b8;
  display: block;
  margin-top: 12px;
  font-weight: 500;
}

/* 生成成果 */
.output-list {
  display: flex;
  flex-direction: column;
}

.output-title {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 12px;
}

.output-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.output-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: rgba(248, 250, 252, 0.5);
  border-radius: 10px;
  border: 1px solid rgba(241, 245, 249, 0.3);
}

.output-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.output-name {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.output-desc {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 1px;
}

/* ===== 加载动画 ===== */
.loading-overlay {
  position: fixed;
  inset: 0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.loading-card {
  text-align: center;
  max-width: 400px;
}

.spinner-box {
  width: 64px;
  height: 64px;
  position: relative;
  margin: 0 auto 24px;
}

.pulse-circle {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 2px solid #2563eb;
  border-radius: 50%;
  opacity: 0;
  animation: pulse 2s cubic-bezier(0.16, 1, 0.3, 1) infinite;
}

.pulse-circle:nth-child(2) { animation-delay: 0.6s; }

.pulse-core {
  position: absolute;
  width: 16px;
  height: 16px;
  background: #2563eb;
  border-radius: 50%;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

@keyframes pulse-ring {
  0% { transform: scale(0.3); opacity: 0; }
  50% { opacity: 0.4; }
  100% { transform: scale(1.3); opacity: 0; }
}

.loading-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 24px;
}

.loading-steps {
  text-align: left;
  margin-bottom: 24px;
}

.loading-step {
  font-size: 14px;
  padding: 8px 0;
  color: #94a3b8;
  transition: all 0.3s;
}

.loading-step.done { color: #22c55e; }
.loading-step.active { color: #2563eb; font-weight: 600; }

.loading-progress {
  height: 4px;
  background: #e2e8f0;
  border-radius: 2px;
  overflow: hidden;
}

.loading-progress-bar {
  height: 100%;
  width: 80%;
  background: linear-gradient(90deg, #2563eb, #8b5cf6);
  border-radius: 2px;
  animation: progress 2s ease-in-out;
}

@keyframes progress {
  0% { width: 0%; }
  100% { width: 100%; }
}

/* ===== 动画 ===== */
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.25s ease;
}

.slide-fade-enter-from { opacity: 0; transform: translateX(20px); }
.slide-fade-leave-to { opacity: 0; transform: translateX(-20px); }

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 1200px) {
  .onboarding-page { padding: 12px; }
  .app-container {
    width: 95%;
    height: calc(100vh - 24px);
    flex-direction: column;
  }
  .left-panel {
    width: 100%;
    min-width: auto;
    flex-direction: row;
    padding: 16px 20px;
    border-right: none;
    border-bottom: 1px solid #f1f5f9;
    align-items: center;
    gap: 16px;
  }
  .assistant-header { margin-bottom: 0; }
  .avatar-area { display: none; }
  .mic-area { display: none; }
  .mic-hint { display: none; }
  .chat-bubble { flex: 1; margin: 0; }
  .quick-prompts { display: none; }
  .center-panel { padding: 24px 20px; }
  .right-panel {
    width: 100%;
    min-width: auto;
    border-left: none;
    border-top: 1px solid #f1f5f9;
    flex-direction: row;
    flex-wrap: wrap;
    gap: 16px;
    padding: 16px 20px;
  }
  .progress-card { flex: 1; min-width: 200px; }
  .output-list { flex: 2; min-width: 300px; }
  .step-label { font-size: 11px; }
}
@media (max-width: 768px) {
  .app-container { border-radius: 16px; }
  .center-panel { padding: 20px 16px; }
  .step-indicators { max-width: 100%; gap: 2px; }
  .step-label { display: none; }
  .step-line { margin: 0 4px; }
  .question-area { max-width: 100%; }
  .right-panel { flex-direction: column; }
  .progress-card { min-width: auto; }
  .output-list { min-width: auto; }
}

@media (max-width: 640px) {
  .app-container { border-radius: 16px; }
  .center-panel { padding: 24px 16px; }
  .question-title { font-size: 1.3rem; }
  .options-grid { grid-template-columns: 1fr; }
  .step-indicators { flex-wrap: wrap; gap: 8px; }
  .step-line { display: none; }
  .right-panel { flex-direction: column; }
}
</style>
