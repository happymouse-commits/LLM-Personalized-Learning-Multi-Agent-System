<template>
  <div class="tutor-layout">
    <!-- ===== 左侧：对话区 ===== -->
    <div class="chat-panel">
      <div v-if="onboardingActive" class="new-chat-bar">
        <el-button size="small" text @click="restartOnboarding">🔄 重新开始导学</el-button>
        <span class="history-hint">导学进行中 · 第 {{ onboardingStep }} 步</span>
      </div>
      <div v-else-if="messages.length > 0" class="new-chat-bar">
        <el-button size="small" text @click="startNewChat">✨ 新对话</el-button>
        <el-button size="small" text @click="historyVisible = !historyVisible; if(historyVisible) refreshSessionList()">📋 历史会话</el-button>
        <span class="history-hint">当前显示最近 {{ messages.length }} 条消息</span>
      </div>

      <div class="chat-messages" ref="msgBox">
        <div v-for="msg in messages" :key="msg.id" :class="['msg', msg.role === 'user' ? 'msg-user' : 'msg-ai']">
          <div v-if="msg.role === 'assistant'" class="msg-avatar msg-avatar-img"><img src="/images/avatar.png" alt="AI" /></div>
          <div class="msg-bubble" :class="{ 'onboarding-bubble': msg.onboarding }">
            <div v-if="msg.agentName && msg.role === 'assistant'" class="agent-tag">{{ msg.agentName }}</div>

            <!-- 渲染消息内容（支持代码块） -->
            <div class="msg-text" v-if="msg.typing" v-html="renderMd(msg.displayContent)"></div>
            <div class="msg-content-parsed" v-else v-html="renderContentWithCode(msg.content)"></div>

            <span v-if="msg.typing" class="cursor">▌</span>

            <!-- PPT操作按钮 -->
            <div v-if="msg.pptUrl && !msg.typing" class="ppt-actions">
              <button class="ppt-btn preview" @click="openPptPreview(msg.pptUrl, msg.pptTitle)">
                👁️ 预览
              </button>
              <a :href="msg.pptUrl" download class="ppt-btn download">
                📥 下载
              </a>
            </div>

            <!-- TTS 朗读按钮 -->
            <button v-if="msg.role === 'assistant' && !msg.typing && msg.content" class="tts-btn" :class="{ playing: msg._ttsPlaying }" @click="toggleTts(msg)">
              {{ msg._ttsPlaying ? '⏹ 停止朗读' : '🔊 朗读' }}
            </button>

            <div v-if="msg.options && msg.options.length && !msg.typing" class="quick-options">
              <button v-for="(opt, idx) in msg.options" :key="idx" class="option-chip" @click="selectOption(opt)">{{ opt }}</button>
            </div>

            <div v-if="msg.onboardingComplete && !msg.typing" class="nav-buttons">
              <button class="nav-chip" @click="$router.push('/student/home')">🏠 去首页查看学习概览</button>
              <button class="nav-chip" @click="$router.push('/student/resources')">📚 去资源中心查看生成的资源</button>
              <button class="nav-chip" @click="$router.push('/student/learning-path')">🗺️ 查看AI为你规划的学习路径</button>
              <button class="nav-chip outline" @click="$router.push('/student/profile-card')">🧠 查看你的完整学习画像</button>
            </div>

            <!-- 资源查看按钮 -->
            <ResourceButton
              v-if="msg.showResourceButton && msg.resourceButtons && msg.resourceButtons.length > 0 && !msg.typing"
              :resources="msg.resourceButtons"
              :suggestPractice="msg.suggestPractice"
              :practiceChapterId="msg.practiceChapterId"
              @click="handleResourceButtonClick"
              @practice="handlePracticeSuggestion"
            />

            <!-- 场景3：询问是否生成资源的按钮 -->
            <div v-if="msg.showAskGenerate && !msg.typing" class="ask-generate-buttons">
              <button class="ask-btn yes" @click="handleAskGenerate(msg, true)">✅ 生成资源</button>
              <button class="ask-btn no" @click="handleAskGenerate(msg, false)">❌ 暂时不需要</button>
            </div>
          </div>
          <div v-if="msg.role === 'user'" class="msg-avatar user-avatar">👤</div>
        </div>
        <div v-if="loading && !hasTyping && !streamStarted" class="msg msg-ai">
          <div class="msg-avatar msg-avatar-img"><img src="/images/avatar.png" alt="AI" /></div>
          <div class="msg-bubble thinking">
            <span class="agent-tag">正在深度思考</span>
            <span class="thinking-spinner"></span>
          </div>
        </div>
      </div>

      <!-- RAG 检索结果面板（聊天区域内，输入框上方） -->
      <transition name="rag-slide">
        <div v-if="ragResults.length > 0" class="rag-panel-chat">
          <div class="rag-panel-header" @click="ragExpanded = !ragExpanded">
            <div class="rag-panel-left">
              <span class="rag-panel-icon">📚</span>
              <span class="rag-panel-title">RAG 知识库检索</span>
              <span class="rag-panel-count">{{ ragResults.length }} 个知识片段</span>
            </div>
            <div class="rag-panel-right">
              <!-- 幻觉率快捷指标 -->
              <span class="rag-hallucination-badge" :class="hallucinationLevelClass">
                <span class="hallucination-dot"></span>
                幻觉风险 {{ hallucinationPercent }}%
              </span>
              <span class="rag-score-badge" :class="ragScoreClass">
                可信度 {{ reliabilityPercent }}%
              </span>
              <span :class="['rag-toggle-icon', { open: ragExpanded }]">▴</span>
            </div>
          </div>
          <transition name="rag-expand">
            <div v-if="ragExpanded" class="rag-panel-body">

              <!-- 左侧环形可视化 + 右侧简洁可信度描述 -->
              <div class="rag-metrics-section">
                <div class="rag-metric-circle">
                  <div class="circle-progress" :class="hallucinationLevelClass">
                    <svg viewBox="0 0 100 100" class="circle-svg">
                      <circle cx="50" cy="50" r="42" fill="none" stroke="#f0f2f5" stroke-width="8"/>
                      <circle cx="50" cy="50" r="42" fill="none" stroke="currentColor" stroke-width="8"
                        stroke-linecap="round"
                        :stroke-dasharray="`${(100 - hallucinationPercent) * 2.64} 264`"
                        transform="rotate(-90 50 50)"/>
                    </svg>
                    <div class="circle-content">
                      <div class="circle-value" :class="hallucinationLevelClass">{{ hallucinationPercent }}%</div>
                      <div class="circle-label">幻觉率</div>
                    </div>
                  </div>
                </div>

                <div class="rag-credibility-summary">
                  <div class="credibility-title">
                    <span class="credibility-icon">{{ reliabilityIcon }}</span>
                    <span class="credibility-text">回答可靠性：{{ reliabilityLabel }}</span>
                    <span class="credibility-percent" :class="ragScoreClass">{{ reliabilityPercent }}%</span>
                  </div>
                  <div class="credibility-detail">
                    本次回答基于 <strong>{{ ragResults.length }}</strong> 段知识库内容生成
                  </div>
                  <div class="credibility-detail">
                    <span v-if="ragMeta?.factCheckPassed" class="check-pass">✅ 事实一致性校验通过</span>
                    <span v-else class="check-warn">⚠️ 部分内容未经知识库验证</span>
                  </div>
                  <div class="credibility-bar-wrap">
                    <div class="credibility-bar-fill" :class="ragScoreClass"
                         :style="{ width: reliabilityPercent + '%' }"></div>
                  </div>
                  <div class="credibility-hint" :class="hallucinationLevelClass">💡 {{ credibilityHint }}</div>
                </div>
              </div>

              <div class="rag-section-divider"></div>

              <!-- 知识库片段列表 -->
              <div class="rag-chunks-header">
                <span class="rag-chunks-title">📖 知识来源片段</span>
                <span class="rag-chunks-hint">点击片段查看完整内容</span>
              </div>

              <div v-for="(chunk, idx) in ragResults" :key="idx"
                   class="rag-chunk-item"
                   :class="{ expanded: chunk._expanded }"
                   @click="chunk._expanded = !chunk._expanded">
                <div class="rag-chunk-top">
                  <div class="rag-chunk-top-left">
                    <span class="rag-chunk-idx">#{{ idx + 1 }}</span>
                    <span class="rag-chunk-source">{{ chunk.source || '未注明来源' }}</span>
                  </div>
                  <span class="rag-chunk-score" :class="chunkScoreClass(chunk.score)">
                    匹配度 {{ (chunk.score * 100).toFixed(1) }}%
                  </span>
                </div>
                <div class="rag-chunk-text"
                     :class="{ 'full-text': chunk._expanded }">
                  {{ chunk.content }}
                </div>
                <div class="rag-chunk-bottom">
                  <div class="rag-chunk-kp" v-if="chunk.knowledgePoint">
                    📌 知识点标签：{{ chunk.knowledgePoint }}
                  </div>
                  <span class="rag-chunk-expand-hint">
                    {{ chunk._expanded ? '收起内容 ▲' : '展开更多 ▼' }}
                  </span>
                </div>
              </div>

              <div class="rag-panel-footer">
                <span v-if="ragMeta?.factCheckPassed" class="footer-pass">
                  ✅ 事实一致性校验通过
                </span>
                <span v-else class="footer-warn">
                  ⚠️ 回答部分内容可能未经知识库验证，请谨慎参考
                </span>
                <span class="footer-divider">·</span>
                <span>综合可信度 {{ reliabilityPercent }}%</span>
                <span class="footer-divider">·</span>
                <span>引用 {{ ragResults.length }} 段知识</span>
              </div>
            </div>
          </transition>
        </div>
      </transition>

      <div class="chat-input-area">
        <div class="input-row">
          <div class="input-wrapper">
            <input v-model="input" class="chat-input-native" placeholder="输入问题，AI了解你后会为你自动生成学习资源..."
              @keyup.enter="send" @focus="onInputFocus" :disabled="loading" />
          </div>
          <button class="send-btn" @click="send" :disabled="loading || !input.trim()">
            <div class="svg-wrapper-1"><div class="svg-wrapper">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20">
                <path fill="none" d="M0 0h24v24H0z"></path>
                <path fill="currentColor" d="M1.946 9.315c-.522-.174-.527-.455.01-.634l19.087-6.362c.529-.176.832.12.684.638l-5.454 19.086c-.15.529-.455.547-.679.045L12 14l6-8-8 6-8.054-2.685z"></path>
              </svg>
            </div></div>
            <span>发送</span>
          </button>

          <!-- 语音按钮 -->
          <button v-if="voiceState !== 'retry'" class="voice-btn" @mousedown.prevent="startRecording"
            :class="{ recording: voiceState === 'recording', listening: voiceState === 'listening' }" :disabled="!voiceSupported || voiceState === 'listening'" :title="voiceSupported ? '按住说话' : '语音不可用'">
            <svg v-if="voiceState === 'listening'" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/>
              <path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
            </svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/>
              <path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
            </svg>
          </button>

          <!-- 重新录音按钮 -->
          <button v-if="voiceState === 'retry'" class="voice-btn retry-btn" @click="retryVoice" title="点击重新录音">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
            </svg>
          </button>
        </div>

        <!-- 语音状态提示 -->
        <div v-if="voiceState === 'recording'" class="voice-status recording-status">
          <span class="voice-pulse"></span>
          正在录音...松手自动发送
        </div>
        <div v-if="voiceState === 'listening'" class="voice-status listening-status">
          <span class="voice-spinner"></span>
          AI正在理解...
        </div>
        <div v-if="voiceState === 'retry'" class="voice-status retry-status">
          <span>⚠️ {{ voiceErrorMessage || 'AI没有听清，请重新说一遍' }}</span>
        </div>
        <div v-if="voiceErrorMessage && voiceState === 'idle'" class="voice-status error-status">
          <span>{{ voiceErrorMessage }}</span>
        </div>
        <!-- 语音识别结果预览 -->
        <div v-if="voiceText && voiceState === 'listening'" class="voice-transcript">
          <span class="voice-transcript-icon">🎤</span>
          <span class="voice-transcript-text">{{ voiceText }}</span>
        </div>

      </div>

      <!-- 历史会话面板 -->
      <div v-if="historyVisible" class="history-overlay" @click.self="historyVisible = false">
        <div class="history-panel">
          <div class="history-header">
            <span>📋 历史会话</span>
            <button class="history-close" @click="historyVisible = false">✕</button>
          </div>
          <div class="history-list" v-if="savedSessions.length">
            <div v-for="sess in savedSessions" :key="sess.key" class="history-item" @click="switchSession(sess.key)">
              <div class="history-item-main">
                <div class="history-preview">{{ sess.preview }}</div>
                <div class="history-meta">
                  <span>{{ sess.count }} 条消息</span>
                  <span>{{ formatDate(sess.date) }}</span>
                </div>
              </div>
              <button class="history-delete" @click.stop="deleteSession(sess.key)" title="删除">🗑️</button>
            </div>
          </div>
          <div v-else class="history-empty">暂无保存的会话</div>
        </div>
      </div>
    </div>

    <!-- ===== 右侧面板 ===== -->
    <div class="side-panel">
      <!-- 0. 智能体协作流 -->
      <AgentPipeline ref="pipelineRef" />

      <!-- 1. 今日学习数据 -->
      <div class="panel-card today-stats">
        <div class="panel-title">📊 今日学习</div>
        <div class="stats-grid">
          <div class="stat-box">
            <div class="stat-val">{{ todayStats.studyMinutes }}</div>
            <div class="stat-unit">分钟</div>
            <div class="stat-label">学习时长</div>
          </div>
          <div class="stat-box">
            <div class="stat-val">{{ todayStats.questionsDone }}</div>
            <div class="stat-unit">道</div>
            <div class="stat-label">完成题目</div>
          </div>
          <div class="stat-box accent">
            <div class="stat-val">{{ todayStats.correctRate }}%</div>
            <div class="stat-unit"></div>
            <div class="stat-label">正确率</div>
          </div>
        </div>
      </div>

      <!-- 2. 最近学习动态 -->
      <div class="panel-card recent-activity">
        <div class="panel-title">🕐 最近动态</div>
        <div class="activity-list" v-if="recentActivity.length">
          <div v-for="(act, idx) in recentActivity" :key="idx" class="activity-item">
            <span class="act-icon">{{ act.icon }}</span>
            <div class="act-body">
              <div class="act-text">{{ act.text }}</div>
              <div class="act-time">{{ act.time }}</div>
            </div>
          </div>
        </div>
        <div v-else class="profile-empty">开始学习后这里会记录你的动态</div>
      </div>

      <!-- 3. 弱项提醒（精简版） -->
      <div class="panel-card" v-if="weakAreas.length">
        <div class="panel-title">⚠️ 薄弱知识点</div>
        <div class="weak-tags">
          <span v-for="w in weakAreas" :key="w" class="weak-tag">{{ w }}</span>
        </div>
        <el-button class="goto-practice" text type="primary" @click="$router.push('/student/learning-center')">去针对性练习 →</el-button>
      </div>
    </div>

    <!-- PPT预览弹窗 -->
    <Teleport to="body">
      <div v-if="showPptPreview" class="ppt-preview-modal" @click.self="closePptPreview">
        <div class="ppt-preview-container">
          <div class="ppt-preview-header">
            <span class="ppt-preview-title">{{ pptPreviewTitle }}</span>
            <button class="ppt-preview-close" @click="closePptPreview">✕</button>
          </div>
          <div class="ppt-preview-body">
            <iframe v-if="showPptPreview" :src="pptPreviewUrl" frameborder="0" allowfullscreen></iframe>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick, onUnmounted } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { useUserStore } from '../../store/user'
import { useDemoStore } from '../../store/demo'
import { marked } from 'marked'
marked.use({ breaks: true, gfm: true })
import api from '../../api'
import { ElMessage } from 'element-plus'
import AgentPipeline from '../../components/AgentPipeline.vue'
import ResourceButton from '../../components/ResourceButton.vue'
import { speakWithTts, stopTts } from '../../utils/tts'

const router = useRouter()
const store = useUserStore()
const demoStore = useDemoStore()

const messages = ref([])
const input = ref('')
const loading = ref(false)
const msgBox = ref(null)
const hasTyping = computed(() => messages.value.some(m => m.typing))
const streamStarted = ref(false)
const isDemo = computed(() => store.username === 'student1' || demoStore.enabled)

// RAG 检索结果面板（新数据结构：{ fragments, hallucinationRate, reliability, factCheckConfidence, avgSimilarity, reliabilityLevel, factCheckPassed, factCheckReason, fragmentCount }）
const ragMeta = ref(null) // 完整元数据对象（含幻觉率）
const ragResults = computed(() => ragMeta.value?.fragments || []) // 兼容旧代码
const ragExpanded = ref(false) // 默认收起
const ragRegenerating = ref(false)
const avgScore = computed(() => {
  if (ragMeta.value?.avgSimilarity != null) return ragMeta.value.avgSimilarity
  if (!ragResults.value.length) return 0
  const sum = ragResults.value.reduce((acc, c) => acc + (c.score || 0), 0)
  return Math.round(sum / ragResults.value.length * 1000) / 10
})
const ragScoreClass = computed(() => {
  const level = ragMeta.value?.reliabilityLevel
  if (level === 'high') return 'score-high'
  if (level === 'medium') return 'score-medium'
  if (level === 'low') return 'score-low'
  const s = Number(avgScore.value)
  if (s >= 85) return 'score-high'
  if (s >= 65) return 'score-medium'
  return 'score-low'
})
const reliabilityPercent = computed(() => {
  if (ragMeta.value?.reliability != null) return ragMeta.value.reliability
  return Number(avgScore.value)
})
const hallucinationPercent = computed(() => {
  if (ragMeta.value?.hallucinationRate != null) return ragMeta.value.hallucinationRate
  return 0
})
const hallucinationLevelClass = computed(() => {
  const h = Number(hallucinationPercent.value)
  if (h <= 10) return 'level-low'
  if (h <= 25) return 'level-medium'
  return 'level-high'
})
const reliabilityLabel = computed(() => {
  const level = ragMeta.value?.reliabilityLevel
  if (level === 'high') return '高'
  if (level === 'medium') return '中'
  return '偏低'
})
const reliabilityIcon = computed(() => {
  const level = ragMeta.value?.reliabilityLevel
  if (level === 'high') return '🟢'
  if (level === 'medium') return '🟡'
  return '🔴'
})
const credibilityHint = computed(() => {
  const h = Number(hallucinationPercent.value)
  if (h <= 10) return '回答可靠，可放心参考'
  if (h <= 25) return '回答基本可靠，建议核对关键信息'
  return '回答可能存在不准确之处，请谨慎参考'
})
function chunkScoreClass(score) {
  if (score >= 0.9) return 'score-high'
  if (score >= 0.7) return 'score-medium'
  return 'score-low'
}

// 语音（讯飞 ASR）
const voiceSupported = ref(true)  // 讯飞 ASR 通过后端调用，无需浏览器原生支持
const isRecording = ref(false)
const voiceText = ref('')
let audioContext = null
let mediaStreamSource = null
let scriptProcessor = null
let mediaStream = null
let pcmChunks = []
let isStoppingVoice = false  // 防止 stopRecording 重复调用

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

let typewriterTimer = null // 非流式打字机定时器
let chunkFlushTimer = null // 流式chunk缓冲刷新定时器
let pendingChunks = [] // 待刷新的chunk缓冲区

// 画像
const profile = ref(null)
const justUpdated = ref(false)

// 聊天记录持久化 — 用 getter 函数确保始终读取最新的 store.id
function getStorageKey() { return 'cryd_chat_messages_' + (store.id || 'default') }
function getOnboardingActiveKey() { return 'cryd_onboarding_active_' + (store.id || 'default') }
function getOnboardingStepKey() { return 'cryd_onboarding_step_' + (store.id || 'default') }
const historyVisible = ref(false)
const savedSessions = ref([])

function loadMessages() {
  try {
    const saved = localStorage.getItem(getStorageKey())
    if (saved) {
      const parsed = JSON.parse(saved)
      if (Array.isArray(parsed) && parsed.length > 0) {
        messages.value = parsed
        return true
      }
    }
  } catch(e) {}
  return false
}

function saveMessages() {
  try {
    localStorage.setItem(getStorageKey(), JSON.stringify(messages.value))
    localStorage.setItem(getOnboardingActiveKey(), JSON.stringify(onboardingActive.value))
    localStorage.setItem(getOnboardingStepKey(), JSON.stringify(onboardingStep.value))
  } catch(e) {}
}

function refreshSessionList() {
  try {
    const all = { ...localStorage }
    const sessions = []
    for (const key in all) {
      if (key.startsWith('cryd_chat_messages_')) {
        try {
          const msgs = JSON.parse(all[key])
          if (Array.isArray(msgs) && msgs.length > 0) {
            const first = msgs[0]
            sessions.push({
              key,
              date: first.createdAt || new Date().toISOString(),
              preview: msgs.slice(0, 3).map(m => m.content).join(' ').substring(0, 50),
              count: msgs.length
            })
          }
        } catch(e) {}
      }
    }
    sessions.sort((a, b) => new Date(b.date) - new Date(a.date))
    savedSessions.value = sessions
  } catch(e) { savedSessions.value = [] }
}

function switchSession(key) {
  try {
    const saved = localStorage.getItem(key)
    if (saved) {
      messages.value = JSON.parse(saved)
      saveMessages()
    }
  } catch(e) {}
  historyVisible.value = false
  nextTick(scrollToBottom)
}

function deleteSession(key) {
  localStorage.removeItem(key)
  refreshSessionList()
  if (key === getStorageKey()) {
    messages.value = []
    saveMessages()
  }
}

function formatDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) return '今天 ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
  const yesterday = new Date(now); yesterday.setDate(yesterday.getDate() - 1)
  if (d.toDateString() === yesterday.toDateString()) return '昨天 ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
  return pad(d.getMonth()+1) + '/' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}

// 自动保存到 localStorage
watch(messages, saveMessages, { deep: true })

// ===== 今日学习数据 =====
const todayStats = computed(() => {
  // 从localStorage读取demo数据（demoStore保存时写入localStorage）
  try {
    const practice = JSON.parse(localStorage.getItem('cryd_practice') || '{"done":0,"correct":0,"wrong":0,"totalTime":0}')
    if (practice.done > 0) {
      return {
        studyMinutes: Math.round(practice.totalTime / 60) || 0,
        questionsDone: practice.done,
        correctRate: practice.done > 0 ? Math.round((practice.correct / practice.done) * 100) : 0
      }
    }
  } catch(e) {}

  // 正式模式：从profile获取
  const p = profile.value || {}
  return {
    studyMinutes: p.todayStudyMinutes || 0,
    questionsDone: p.todayQuestions || 0,
    correctRate: p.todayCorrectRate || 0
  }
})

// ===== 薄弱知识点 =====
const weakAreas = computed(() => {
  // 从localStorage读取profile
  try {
    const saved = JSON.parse(localStorage.getItem('cryd_profile') || 'null')
    if (saved?.weakAreas) {
      return saved.weakAreas.split(/[,，、]/).filter(Boolean).map(s => s.trim()).slice(0, 5)
    }
  } catch(e) {}
  // 从当前profile获取
  const weakStr = profile.value?.weakAreas || ''
  if (!weakStr) return ['指针', '内存管理', '结构体']
  return weakStr.split(/[,，、]/).filter(Boolean).map(s => s.trim()).slice(0, 5)
})

// ===== 最近学习动态 =====
const recentActivity = computed(() => {
  const acts = []
  // 从localStorage获取最近的练习记录
  try {
    const practiceData = JSON.parse(localStorage.getItem('cryd_practice_history') || '[]')
    practiceData.slice(0, 5).forEach(item => {
      acts.push({
        icon: item.correct ? '✅' : '❌',
        text: `${item.correct ? '答对' : '答错'}了「${item.question || '练习题'}」`,
        time: timeAgo(item.timestamp),
        ts: new Date(item.timestamp).getTime()
      })
    })
  } catch(e) {}

  // 从资源列表获取
  try {
    const resources = JSON.parse(localStorage.getItem('cryd_resources') || '[]')
    resources.slice(0, 3).forEach(r => {
      acts.push({
        icon: typeEmoji(r.type),
        text: `生成了「${r.title}」`,
        time: timeAgo(r.createdAt),
        ts: new Date(r.createdAt).getTime()
      })
    })
  } catch(e) {}

  // 聊天记录
  if (messages.value.length > 0) {
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg.role === 'assistant') {
      acts.push({
        icon: '🤖',
        text: 'AI为你分析了学习情况',
        time: timeAgo(lastMsg.createdAt),
        ts: new Date(lastMsg.createdAt || Date.now()).getTime()
      })
    }
  }

  // 按时间排序，取最近6条
  acts.sort((a, b) => b.ts - a.ts)
  return acts.slice(0, 6)
})

function renderMd(t) { return t ? marked.parse(preprocessAiText(t)) : '' }

// 预处理AI回复文本：自动分段，让排版不再是一大坨
function preprocessAiText(text) {
  if (!text) return ''
  let t = text

  // 1. 把 mid-sentence 的 "-xxx" 拆成换行列表项
  //    匹配：中文标点/空格后紧跟 -开头的短句（不是代码里的减号）
  t = t.replace(/([。！？\s])\s*-\s+/g, '$1\n\n- ')

  // 2. 中文句号/感叹号/问号后紧跟中文汉字且没有换行 → 加换行
  //    但排除短句（<30字符），避免拆太碎
  t = t.replace(/([。！？])((?=[\u4e00-\u9fa5]{20,}))/g, '$1\n\n')

  // 3. 冒号后紧跟大段内容 → 加换行
  t = t.replace(/(：\s*)(?=[\u4e00-\u9fa5]{15,})/g, '$1\n\n')

  // 4. **加粗标题** 开头的段落前加换行（如果前面没有的话）
  t = t.replace(/([^\n])\s*(?=\*\*[^*]+\*\*[：:])/g, '$1\n\n')

  // 5. 连续空行最多保留两个
  t = t.replace(/\n{3,}/g, '\n\n')

  // 6. 去掉首尾多余换行
  t = t.trim()

  return t
}

// 渲染消息内容，支持代码块
function renderContentWithCode(content) {
  if (!content) return ''

  // 分割代码块和普通文本
  const codeBlockRegex = /```(\w+)?\n([\s\S]*?)```/g
  const parts = []
  let lastIndex = 0
  let match

  while ((match = codeBlockRegex.exec(content)) !== null) {
    // 添加代码块前的普通文本
    if (match.index > lastIndex) {
      const text = content.substring(lastIndex, match.index)
      parts.push({ type: 'text', content: text })
    }

    // 添加代码块
    parts.push({
      type: 'code',
      language: match[1] || 'c',
      content: match[2].trim()
    })

    lastIndex = match.index + match[0].length
  }

  // 添加剩余的普通文本
  if (lastIndex < content.length) {
    parts.push({ type: 'text', content: content.substring(lastIndex) })
  }

  // 如果没有代码块，直接返回渲染后的markdown
  if (parts.length === 0 || (parts.length === 1 && parts[0].type === 'text')) {
    return renderMd(content)
  }

  // 生成HTML（使用行内样式避免scoped问题）
  let html = ''
  for (const part of parts) {
    if (part.type === 'text') {
      html += renderMd(part.content)
    } else if (part.type === 'code') {
      // 生成代码块HTML
      const escapedCode = part.content
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
      html += `
        <div class="code-editor-inline">
          <div class="code-header-inline">
            <span class="code-lang">${part.language}</span>
          </div>
          <pre class="code-pre"><code class="language-${part.language}">${escapedCode}</code></pre>
        </div>
      `
    }
  }

  return html
}

function typeEmoji(t) { return { article:'📄', exercise:'✏️', explanation:'📖', mindmap:'🗺️', code:'💻', video:'🎬' }[t] || '📄' }
function timeAgo(d) { if (!d) return ''; const diff = Date.now()-new Date(d).getTime(); const m=Math.floor(diff/60000); if(m<1) return '刚刚'; if(m<60) return m+'分钟前'; const h=Math.floor(m/60); if(h<24) return h+'小时前'; return Math.floor(h/24)+'天前' }

// 流式chunk缓冲刷新 + 逐字显示
let _scrollPending = false
let _revealPos = 0       // 当前已显示到 content 的第几个字符
let _revealTarget = null  // 正在逐字显示的消息对象
let _revealTimer = null   // 逐字定时器
let _streamDone = false   // 流式是否已结束

function flushChunks() {
  if (!pendingChunks.length || !pendingChunks._target) return
  const target = pendingChunks._target
  const text = pendingChunks.join('')
  pendingChunks.length = 0
  target.content += text
}

// 启动逐字显示：每 30ms 从 content 中取下一个字符追加到 displayContent
function startReveal(target) {
  stopReveal()  // 先停掉旧的 timer
  _revealTarget = target
  _revealPos = 0
  _streamDone = false
  _revealTimer = setInterval(() => {
    if (!_revealTarget) { stopReveal(); return }
    const full = _revealTarget.content
    // 还有字没显示完 → 推进
    if (_revealPos < full.length) {
      const ch = full[_revealPos]
      const step = /[一-鿿]/.test(ch) ? 1 : Math.min(3, full.length - _revealPos)
      _revealPos += step
      _revealTarget.displayContent = full.substring(0, _revealPos)
      if (!_scrollPending) {
        _scrollPending = true
        requestAnimationFrame(() => { scrollToBottom(); _scrollPending = false })
      }
    } else {
      // 所有字符都已显示 → 关闭打字状态，停止 timer
      _revealTarget.typing = false
      _revealTarget.displayContent = _revealTarget.content
      stopReveal()
      scrollToBottom()
    }
  }, 30)
}
function stopReveal() {
  if (_revealTimer) { clearInterval(_revealTimer); _revealTimer = null }
  // 停止前确保显示完整内容
  if (_revealTarget && _revealTarget.typing) {
    _revealTarget.typing = false
    _revealTarget.displayContent = _revealTarget.content
  }
  _revealTarget = null
  _revealPos = 0
  _streamDone = false
}

function bufferChunk(chunk, aiMsg) {
  if (!pendingChunks._target) pendingChunks._target = aiMsg
  pendingChunks.push(chunk)
  // 首次：开启打字状态，启动逐字显示
  if (!aiMsg.typing) {
    aiMsg.typing = true
    aiMsg.displayContent = ''
    startReveal(aiMsg)
  }
  if (!chunkFlushTimer) {
    chunkFlushTimer = setInterval(flushChunks, 30)
  }
}

function flushAndStopBuffer() {
  if (chunkFlushTimer) { clearInterval(chunkFlushTimer); chunkFlushTimer = null }
  flushChunks()
  _streamDone = true  // 标记结束，让逐字定时器自然消费完后停止
  pendingChunks.length = 0
  pendingChunks._target = null
  pendingChunks._preBuffer = null
}

// 对话
function scrollToBottom() { nextTick(() => { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight }) }
function typewriterEffect(msg, fullText, speed = 18) {
  if (typewriterTimer) clearInterval(typewriterTimer)
  let i = 0; msg.displayContent = ''; msg.typing = true
  let scrollPending = false
  typewriterTimer = setInterval(() => {
    if (i < fullText.length) {
      const step = /[一-鿿]/.test(fullText[i]) ? 1 : Math.min(3, fullText.length - i)
      i += step
      msg.displayContent = fullText.substring(0, i)
      // 节流滚动：用 rAF 合并同帧多次滚动
      if (!scrollPending) {
        scrollPending = true
        requestAnimationFrame(() => { scrollToBottom(); scrollPending = false })
      }
    } else {
      clearInterval(typewriterTimer); typewriterTimer = null; msg.typing = false; msg.displayContent = fullText
      scrollToBottom()
      // 打字完成后自动朗读AI回复
      if (msg.role === 'assistant') {
        msg._ttsPlaying = true
        speakWithTts(fullText, {
          onEnd: () => { msg._ttsPlaying = false },
          onError: () => { msg._ttsPlaying = false }
        })
      }
    }
  }, speed)
}

async function send() {
  const text = input.value.trim()
  if (!text) {
    return
  }
  if (loading.value) {
    return
  }

  // 检测是否为PPT生成请求
  const isPptRequest = text.includes('生成PPT') || text.includes('做PPT') || text.includes('做个PPT') ||
    text.includes('制作PPT') || text.includes('帮我做PPT') || text.includes('帮我生成PPT')

  // 发送新消息时停止上一条朗读
  stopTts()
  messages.value.forEach(m => { m._ttsPlaying = false })
  messages.value.push({ id: Date.now(), role: 'user', content: text, createdAt: new Date().toISOString() })
  input.value = ''; loading.value = true; streamStarted.value = false; scrollToBottom(); ragMeta.value = null; ragExpanded.value = false; voiceText.value = ''

  // PPT生成请求
  if (isPptRequest) {
    await handlePptGeneration(text)
    return
  }

  // 引导模式
  if (onboardingActive.value) {
    try {
      const step = onboardingStep.value || 1
      const payload = { studentId: store.id || 0, message: text, onboardingStep: step }
      const res = await api.post('/chat/send', payload)
      await handleOnboardingReply(res.data)
    } catch {
      if (demoStore.enabled) {
        const step = onboardingStep.value || 1
        const reply = await demoStore.sendChatMessage(text, step)
        await handleOnboardingReply(reply)
      } else {
        ElMessage.error('发送失败'); messages.value.pop()
      }
    }
    finally { loading.value = false; scrollToBottom() }
    return
  }

  // SSE 流式 → 改用 /chat/send（与问卷导学一致，避免 ERR_ABORTED）
  if (demoStore.enabled) { sendDemo(text); return }
  try {
    const res = await api.post('/chat/send', { studentId: store.id, message: text })
    const reply = res?.data?.message || res?.data?.data?.message || ''
    if (!reply) throw new Error('AI 返回空内容')
    const aiMsg = { id:Date.now()+1, role:'assistant', content:reply, agentName:'AI导师', createdAt:new Date().toISOString(), onboarding:false, onboardingComplete:false, options:null }
    messages.value.push(aiMsg)
    streamStarted.value = true
    loading.value = false
    justUpdated.value = true
    scrollToBottom()
    setTimeout(() => { refreshProfile(); refreshResources() }, 1500)
    // 自动朗读
    if (!aiMsg._ttsPlaying) {
      aiMsg._ttsPlaying = true
      speakWithTts(aiMsg.content, {
        onEnd: () => { aiMsg._ttsPlaying = false },
        onError: () => { aiMsg._ttsPlaying = false }
      })
    }
    // 语音模式：检查AI是否理解
    if (voiceState.value === 'listening') {
      if (checkAiUnderstanding(aiMsg.content)) {
        voiceState.value = 'retry'
        voiceErrorMessage.value = 'AI没有听清，请重新说一遍'
      } else {
        voiceState.value = 'idle'
      }
    }
  } catch (err) {
    loading.value = false
    if (voiceState.value === 'listening') {
      voiceState.value = 'retry'
      voiceErrorMessage.value = 'AI处理失败，请重新说一遍'
    }
    ElMessage.error('发送失败: '+(err.message||'网络错误'))
  }
  scrollToBottom()
}

// ===== PPT 生成 =====
const showPptPreview = ref(false)
const pptPreviewUrl = ref('')
const pptPreviewTitle = ref('')

async function handlePptGeneration(text) {
  // 提取主题（去掉"生成PPT"等关键词）
  let topic = text
    .replace(/帮我|请|生成PPT|做PPT|做个PPT|制作PPT/g, '')
    .trim()

  if (!topic) {
    topic = '演示文稿'
  }

  // 添加提示消息
  const tipMsg = {
    id: Date.now() + 1,
    role: 'assistant',
    content: `正在为您生成关于"${topic}"的PPT，请稍候...`,
    displayContent: '',
    agentName: 'AI导师',
    createdAt: new Date().toISOString(),
    typing: false
  }
  messages.value.push(tipMsg)
  typewriterEffect(tipMsg, tipMsg.content, 18)

  try {
    const res = await api.post('/ppt/generate', { topic, style: '简洁专业' })
    if (res.data && res.data.url) {
      // 生成完整URL用于预览
      const fullUrl = window.location.origin + res.data.url

      const successMsg = {
        id: Date.now() + 2,
        role: 'assistant',
        content: `PPT生成成功！`,
        displayContent: '',
        agentName: 'AI导师',
        createdAt: new Date().toISOString(),
        typing: false,
        pptUrl: res.data.url,
        pptTitle: topic
      }
      messages.value.push(successMsg)
      typewriterEffect(successMsg, successMsg.content, 18)
    } else {
      throw new Error('生成失败')
    }
  } catch (e) {
    console.error('PPT生成失败:', e)
    const errorMsg = {
      id: Date.now() + 2,
      role: 'assistant',
      content: '抱歉，PPT生成失败了，请稍后再试。',
      displayContent: '',
      agentName: 'AI导师',
      createdAt: new Date().toISOString(),
      typing: false
    }
    messages.value.push(errorMsg)
    typewriterEffect(errorMsg, errorMsg.content, 18)
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function openPptPreview(url, title) {
  // 使用Office Online Viewer预览
  const fullUrl = window.location.origin + url
  pptPreviewUrl.value = `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(fullUrl)}`
  pptPreviewTitle.value = title || 'PPT预览'
  showPptPreview.value = true
}

function closePptPreview() {
  showPptPreview.value = false
  pptPreviewUrl.value = ''
}

function selectOption(opt) {
  if (loading.value) return
  // 选择选项时停止朗读
  stopTts()
  messages.value.forEach(m => { m._ttsPlaying = false })
  input.value = opt; send()
}
function startNewChat() {
  messages.value = []
  onboardingActive.value = false
  onboardingStep.value = 0
  localStorage.removeItem(getOnboardingActiveKey())
  localStorage.removeItem(getOnboardingStepKey())
  localStorage.removeItem(getStorageKey())
  scrollToBottom()
}

// 重新开始导学：清理所有缓存，重新触发
function restartOnboarding() {
  messages.value = []
  onboardingActive.value = false
  onboardingStep.value = 0
  localStorage.removeItem(getOnboardingActiveKey())
  localStorage.removeItem(getOnboardingStepKey())
  localStorage.removeItem(getStorageKey())
  nextTick(() => {
    triggerOnboarding()
    onboardingActive.value = true
  })
}

// ===== TTS 朗读 =====
function onInputFocus() {
  // 用户开始输入时自动暂停朗读
  stopTts()
  messages.value.forEach(m => { m._ttsPlaying = false })
}

function toggleTts(msg) {
  if (msg._ttsPlaying) {
    stopTts()
    msg._ttsPlaying = false
  } else {
    // 停止其他正在播放的
    messages.value.forEach(m => { if (m._ttsPlaying) m._ttsPlaying = false })
    msg._ttsPlaying = true
    speakWithTts(msg.content, {
      onEnd: () => { msg._ttsPlaying = false },
      onError: () => { msg._ttsPlaying = false }
    })
  }
}

// 处理资源按钮点击
function handleResourceButtonClick(resource) {
  // 特殊处理：跳转到今日推荐
  if (resource.resourceId === 'today-recommend') {
    router.push('/student/today-recommend')
    return
  }

  if (resource.type === 'exercise') {
    // 跳转到刷题房
    router.push({
      path: '/student/fullscreen-practice/' + resource.chapter,
      query: { from: 'ai-coaching', errorKnowledgePoint: resource.knowledgePoint }
    })
  } else {
    // 跳转到资源详情
    router.push({
      path: '/student/resource-detail',
      query: {
        id: resource.resourceId,
        title: resource.title,
        type: resource.type,
        chapter: resource.chapter
      }
    })
  }
}

// 场景3：处理询问是否生成资源的按钮点击
async function handleAskGenerate(msg, agree) {
  if (!agree) {
    // 用户拒绝，更新消息显示
    msg.content = msg.content.replace('是否需要我为你生成相关的学习资源？', '好的，有需要随时告诉我~')
    msg.showAskGenerate = false
    return
  }

  // 用户同意，生成资源
  msg.showAskGenerate = false
  msg.content = msg.content.replace('是否需要我为你生成相关的学习资源？', '正在为你生成资源，请稍候...')

  try {
    // 从消息中获取知识点和资源类型
    const knowledgePoint = msg.knowledgePoint || 'C语言基础'
    const resourceType = msg.resourceType || 'mindmap'

    // 调用后端生成资源
    await api.post('/chat/send', {
      studentId: store.id || 0,
      message: `帮我生成${resourceType}资源，知识点是${knowledgePoint}`
    })
    ElMessage.success('资源生成中，请稍候...')
  } catch (e) {
    console.error('生成资源失败:', e)
    ElMessage.error('生成资源失败，请稍后再试')
  }
}

// 处理巩固练习建议
function handlePracticeSuggestion(chapterId) {
  router.push({
    path: '/student/fullscreen-practice/' + chapterId,
    query: { from: 'ai-coaching', mode: 'targeted' }
  })
}

// Demo 模式发送：使用真实SSE管道
const pipelineRef = ref(null)

async function sendDemo(message) {
  loading.value = true
  streamStarted.value = false
  // 重置Pipeline面板
  if (pipelineRef.value) pipelineRef.value.reset()

  try {
    const res = await api.post('/chat/send', { studentId: store.id || 0, message })
    const reply = res?.data?.message || res?.data?.data?.message || ''
    if (!reply) throw new Error('AI 返回空内容')
    const aiMsg = { id:Date.now()+1, role:'assistant', content:reply, agentName:'AI导师', createdAt:new Date().toISOString(), onboarding:false, onboardingComplete:false, options:null }
    messages.value.push(aiMsg)
    streamStarted.value = true
    loading.value = false
    justUpdated.value = true
    scrollToBottom()
    setTimeout(() => { refreshProfile(); refreshResources() }, 1500)
    // 自动朗读
    aiMsg._ttsPlaying = true
    speakWithTts(aiMsg.content, {
      onEnd: () => { aiMsg._ttsPlaying = false },
      onError: () => { aiMsg._ttsPlaying = false }
    })
  } catch (err) {
    loading.value = false
    ElMessage.error('发送失败: ' + (err.message || '网络错误'))
  }
  scrollToBottom()
}

// ===== 导学（统一逻辑） =====
const onboardingActive = ref(false)
const onboardingStep = ref(0)

// 统一处理导学后端响应：更新状态 → push消息 → 自动朗读下一题
function handleOnboardingReply(data) {
  if (!data) return
  const aiMsg = {
    id: Date.now() + 1,
    role: 'assistant',
    content: data.message || '抱歉，我无法回答这个问题。',
    displayContent: '',
    agentName: data.agentName || '学习导师',
    createdAt: new Date().toISOString(),
    typing: false,
    onboarding: data.onboarding || false,
    onboardingComplete: data.onboardingComplete || false,
    options: data.options || null,
    showResourceButton: data.showResourceButton || false,
    resourceButtons: data.resourceButtons || [],
    suggestPractice: data.suggestPractice || false,
    practiceChapterId: data.practiceChapterId || ''
  }
  // 1. 先更新导学状态（避免watch保存旧步骤）
  if (data.onboardingStep) onboardingStep.value = data.onboardingStep
  if (data.onboardingComplete) {
    onboardingActive.value = false
    onboardingStep.value = 0
    try {
      localStorage.removeItem(getOnboardingActiveKey())
      localStorage.removeItem(getOnboardingStepKey())
    } catch(e) {}
    setTimeout(() => { refreshProfile(); refreshResources(); connectWs() }, 2000)
  }
  // 2. push消息
  messages.value.push(aiMsg)
  // 3. 打字机效果 + 自动朗读下一题
  typewriterEffect(aiMsg, aiMsg.content, 18)
  // 导学模式下自动朗读AI的回复（下一题的问题）
  if (onboardingActive.value && aiMsg.content) {
    aiMsg._ttsPlaying = true
    speakWithTts(aiMsg.content, {
      onEnd: () => { aiMsg._ttsPlaying = false },
      onError: () => { aiMsg._ttsPlaying = false }
    })
  }
  if (data.profileUpdated) { justUpdated.value = true; setTimeout(refreshProfile, 3000) }
  setTimeout(refreshResources, 2000)
}

async function triggerOnboarding() {
  loading.value = true
  try {
    let data
    if (demoStore.enabled) {
      // 演示模式：走本地导学状态机，避免后端不通时卡死
      data = await demoStore.sendChatMessage('', 1)
    } else {
      const res = await api.post('/chat/send', { studentId: store.id || 0, message: '' })
      data = res.data
    }
    await handleOnboardingReply(data)
  } catch {} finally { loading.value = false; scrollToBottom() }
}

async function refreshProfile() {
  if (isDemo.value) { const p = await demoStore.getProfile(); if (p && Object.keys(p).length) { profile.value = p }; return }
  try { const res = await api.get('/student/by-sysuser/'+store.id); if (res.data) { profile.value = res.data } } catch {}
}
async function refreshResources() {
  // 资源数据已通过recentActivity computed属性从localStorage获取
  // 保留此函数用于兼容WebSocket通知
}

// 语音识别状态
const voiceState = ref('idle') // idle, recording, listening, retry
const voiceErrorMessage = ref('')

// 语音识别（讯飞 ASR，通过后端 /voice/recognize 接口）
async function startRecording() {
  if (!voiceSupported.value || voiceState.value === 'recording') return
  stopTts()
  messages.value.forEach(m => { m._ttsPlaying = false })

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true }
    })
    audioContext = new (window.AudioContext || window.webkitAudioContext)()
    const sourceRate = audioContext.sampleRate
    mediaStreamSource = audioContext.createMediaStreamSource(mediaStream)

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

    const zeroGain = audioContext.createGain()
    zeroGain.gain.value = 0
    mediaStreamSource.connect(scriptProcessor)
    scriptProcessor.connect(zeroGain)
    zeroGain.connect(audioContext.destination)

    voiceState.value = 'recording'
    voiceErrorMessage.value = ''
    voiceText.value = ''
    // 在 document 上监听 mouseup，确保鼠标移出按钮后松开也能停止录音
    document.addEventListener('mouseup', stopRecording, { once: true })
  } catch (e) {
    console.warn('麦克风访问失败:', e)
    voiceState.value = 'idle'
    voiceErrorMessage.value = '无法访问麦克风，请检查浏览器权限设置'
  }
}

function stopRecording() {
  if (isStoppingVoice || voiceState.value !== 'recording') return
  isStoppingVoice = true
  voiceState.value = 'idle'
  // 清除 document 上的 mouseup 监听
  document.removeEventListener('mouseup', stopRecording)

  // 断开音频处理链 — 先停 stream 再断处理链，确保 pcmChunks 数据完整
  if (mediaStream) { mediaStream.getTracks().forEach(t => t.stop()); mediaStream = null }
  if (scriptProcessor) { scriptProcessor.disconnect(); scriptProcessor = null }
  if (mediaStreamSource) { mediaStreamSource.disconnect(); mediaStreamSource = null }
  const ctx = audioContext
  audioContext = null
  if (ctx) { setTimeout(() => ctx.close().catch(() => {}), 200) }

  if (pcmChunks.length === 0) {
    isStoppingVoice = false
    return
  }

  voiceState.value = 'listening'

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

    // 发送到后端讯飞识别
    api.post('/voice/recognize', { audio: base64 }).then(res => {
      const text = res?.data?.text || ''
      isStoppingVoice = false

      if (!text) {
        voiceState.value = 'retry'
        voiceErrorMessage.value = '没有听清，请重新说一遍'
        return
      }

      voiceText.value = text
      voiceState.value = 'idle'

      // 直接添加用户消息
      const userMsg = {
        id: Date.now(),
        role: 'user',
        content: text,
        createdAt: new Date().toISOString()
      }
      messages.value.push(userMsg)
      input.value = ''
      loading.value = true
      nextTick(() => scrollToBottom())

      // 调用AI API
      callAiApi(text)
    }).catch(e => {
      console.warn('语音识别失败:', e)
      isStoppingVoice = false
      voiceState.value = 'retry'
      voiceErrorMessage.value = '语音识别失败，请重试'
    })
  } catch (e) {
    console.warn('语音识别失败:', e)
    isStoppingVoice = false
    voiceState.value = 'retry'
    voiceErrorMessage.value = '语音识别失败，请重试'
  }
}

// 重新录音
function retryVoice() {
  voiceState.value = 'idle'
  voiceErrorMessage.value = ''
  voiceText.value = ''
  input.value = ''
  nextTick(() => startRecording())
}

// 检查AI回复是否表示不明白
function checkAiUnderstanding(aiMessage) {
  if (!aiMessage) return false
  const notUnderstoodPatterns = ['没听清', '不明白', '再说一遍', '没听懂', '请重复', '没太听清', '能再说一次吗']
  return notUnderstoodPatterns.some(p => aiMessage.includes(p))
}

// 语音发送时调用AI API
async function callAiApi(text) {
  // 导学模式：复用统一逻辑
  if (onboardingActive.value) {
    try {
      const step = onboardingStep.value || 1
      let data
      if (demoStore.enabled) {
        data = await demoStore.sendChatMessage(text, step)
      } else {
        const res = await api.post('/chat/send', { studentId: store.id || 0, message: text, onboardingStep: step })
        data = res.data
      }
      await handleOnboardingReply(data)
    } catch (e) {
      console.error('导学语音调用失败:', e)
      voiceState.value = 'retry'
      voiceErrorMessage.value = 'AI回复失败，请重试'
    } finally {
      voiceState.value = 'idle'
      loading.value = false
      scrollToBottom()
    }
    return
  }

  // 非导学模式：正常AI对话
  try {
    let data
    if (demoStore.enabled) {
      data = await demoStore.sendChatMessage(text)
    } else {
      const res = await api.post('/chat/send', { studentId: store.id || 0, message: text })
      data = res.data
    }
    const aiMsg = {
      id: Date.now() + 1,
      role: 'assistant',
      content: data.message || data.content || '抱歉，我无法回答这个问题。',
      displayContent: '',
      agentName: data.agentName || 'AI导师',
      createdAt: new Date().toISOString(),
      typing: false,
      onboarding: false,
      onboardingComplete: false,
      options: null
    }
    messages.value.push(aiMsg)
    typewriterEffect(aiMsg, aiMsg.content, 18)
    voiceState.value = 'idle'
  } catch (e) {
    console.error('AI API调用失败:', e)
    voiceState.value = 'retry'
    voiceErrorMessage.value = 'AI回复失败，请重试'
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// WebSocket
let ws = null, wsReconnectTimer = null
function connectWs() {
  if (ws && ws.readyState === WebSocket.OPEN) return
  // 演示模式使用伪造 token，后端会拒绝 WS 连接 — 直接跳过
  if (localStorage.getItem('cryd_demo_mode') === 'true') return
  const token = store.token
  if (!token) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  ws = new WebSocket(`${protocol}//${location.host}/ws/student?token=${encodeURIComponent(token)}`)
  ws.onmessage = (event) => { try { const data = JSON.parse(event.data); if (data.type==='resources') refreshResources(); else if (data.type==='profile') refreshProfile() } catch {} }
  ws.onclose = () => { wsReconnectTimer = setTimeout(connectWs, 5000) }
  ws.onerror = () => {}
}

// ===== 主动欢迎 =====
function sendWelcome() {
  const welcome = '你好！我是你的**AI学习助手** 🤖\n\n我可以帮你解答C语言的各种问题。\n\n**直接问我就行：**\n- 任何不懂的知识点\n- 代码看不懂的地方\n- 想要练习某个知识点\n\n我会根据你的理解情况，推荐适合你的学习资源！\n\n有什么问题，尽管问我 💪'
  const msg = {
    id: Date.now(),
    role: 'assistant',
    content: welcome,
    displayContent: '',
    agentName: 'AI学习助手',
    createdAt: new Date().toISOString(),
    typing: false,
    onboarding: false,
    onboardingComplete: false,
    options: ['指针是什么', '数组怎么用', '给我讲讲函数']
  }
  messages.value.push(msg)
  typewriterEffect(messages.value[messages.value.length - 1], welcome, 18)
}

onMounted(async () => {
  // 1. 优先从 localStorage 恢复聊天记录（切页面再回来不丢失）
  const hasLocalMessages = loadMessages()
  if (hasLocalMessages) {
    // 恢复引导状态
    try {
      const savedActive = JSON.parse(localStorage.getItem(getOnboardingActiveKey()))
      const savedStep = JSON.parse(localStorage.getItem(getOnboardingStepKey()))
      if (savedActive === true && savedStep > 0) {
        onboardingActive.value = true
        onboardingStep.value = savedStep
      }
    } catch(e) {}
    nextTick(scrollToBottom)
  }

  // 清理过期/异常的导学缓存
  try {
    const savedActive = JSON.parse(localStorage.getItem(getOnboardingActiveKey()))
    const savedStep = JSON.parse(localStorage.getItem(getOnboardingStepKey()))
    if (savedActive === true) {
      // 情况1：步骤号异常
      if (!savedStep || savedStep <= 0) {
        localStorage.removeItem(getOnboardingActiveKey())
        localStorage.removeItem(getOnboardingStepKey())
        localStorage.removeItem(getStorageKey())
        messages.value = []
        onboardingActive.value = false
        onboardingStep.value = 0
      }
      // 情况2：导学状态为active但消息为空（脏数据）
      else if (!messages.value.length) {
        localStorage.removeItem(getOnboardingActiveKey())
        localStorage.removeItem(getOnboardingStepKey())
        onboardingActive.value = false
        onboardingStep.value = 0
      }
    }
  } catch(e) {}

  // 2. Demo 模式
  if (demoStore.enabled) {
    const p = await demoStore.getProfile()
    if (p && Object.keys(p).length) { profile.value = p }
    // Demo模式下始终发送欢迎消息（如果没有历史消息）
    if (!messages.value.length) {
      sendWelcome()
    }
    return
  }

  // 3. 服务端历史（仅当无本地缓存时）
  if (!hasLocalMessages) {
    try { const res = await api.get('/chat/history/'+store.id); messages.value = (res.data||[]).map(m=>({...m,options:null})); scrollToBottom() } catch {}
  }

  // 4. 初始化数据
  if (isDemo.value) { await refreshProfile(); await refreshResources() }
  else { refreshProfile(); refreshResources(); connectWs() }

  // 5. 监听资源更新事件（资源生成完成后显示按钮）
  window.addEventListener('refresh-resources', onResourceUpdate)

  // 6. 无消息时主动欢迎
  if (!hasLocalMessages && !messages.value.length && !demoStore.enabled) sendWelcome()
})

// 路由离开时停止朗读
onBeforeRouteLeave(() => {
  stopTts()
  messages.value.forEach(m => { m._ttsPlaying = false })
})

// 资源更新事件处理 — 检查是否有新生成的资源可以显示按钮
async function onResourceUpdate(event) {
  // 刷新资源列表
  await refreshResources()

  // 查找最近的AI消息，检查是否需要显示按钮
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg && lastMsg.role === 'assistant' && lastMsg.content) {
    // 场景3：检查是否包含询问生成资源的结构化数据
    const askMatch = lastMsg.content.match(/<!-- KP:(.+?) TYPE:(.+?) ASK:true -->/)
    if (askMatch) {
      lastMsg.showAskGenerate = true
      lastMsg.knowledgePoint = askMatch[1]
      lastMsg.resourceType = askMatch[2]
      // 移除结构化数据标记
      lastMsg.content = lastMsg.content.replace(/<!-- KP:.+? TYPE:.+? ASK:true -->/, '').trim()
      return
    }

    // 场景4：检查是否包含"正在为你生成"
    if (lastMsg.content.includes('正在为你生成')) {
      // 优先使用WebSocket推送的资源按钮数据
      let resourceButtons = []
      if (event && event.detail && event.detail.buttons) {
        resourceButtons = event.detail.buttons
      } else {
        // 如果没有WebSocket数据，从API或本地数据获取
        try {
          const isDemo = localStorage.getItem('cryd_demo_mode') === 'true'
          let latestResources = []
          if (isDemo) {
            const { demoResources } = await import('../../mock/demoData')
            latestResources = (demoResources || []).slice(0, 3)
          } else {
            const res = await resourceApi.getByStudent(store.id)
            latestResources = (res.data || []).slice(0, 3)
          }
          resourceButtons = latestResources.map(r => ({
            resourceId: r.id,
            title: r.title,
            type: r.type,
            knowledgePoint: r.knowledgePoint || '',
            chapter: r.chapter || '',
            actionUrl: '/student/resource-detail?id=' + r.id,
            icon: getResourceIconForType(r.type)
          }))
        } catch (e) {
          console.warn('获取资源列表失败:', e)
        }
      }

      if (resourceButtons.length > 0) {
        // 更新消息，添加资源按钮
        lastMsg.showResourceButton = true
        lastMsg.resourceButtons = resourceButtons
        lastMsg.content = '✅ 资源已生成！点击下方按钮查看：'
      }
    }
  }
}

function getResourceIconForType(type) {
  const icons = {
    'explanation': '📖', 'mindmap': '🗺️', 'code': '💻', 'exercise': '📝',
    'weakness': '🎯', 'course-video': '🎬', 'course-ppt': '📑', 'article': '🎧'
  }
  return icons[type] || '📄'
}

onUnmounted(() => {
  stopTts()
  messages.value.forEach(m => { m._ttsPlaying = false })
  if (chunkFlushTimer) { clearInterval(chunkFlushTimer); chunkFlushTimer = null }
  stopReveal()
  window.removeEventListener('refresh-resources', onResourceUpdate)
  if (typewriterTimer) clearInterval(typewriterTimer)
  if (ws) ws.close()
  if (wsReconnectTimer) clearTimeout(wsReconnectTimer)
  // 清理语音录音资源
  document.removeEventListener('mouseup', stopRecording)
  if (scriptProcessor) { try { scriptProcessor.disconnect() } catch {} ; scriptProcessor = null }
  if (mediaStreamSource) { try { mediaStreamSource.disconnect() } catch {} ; mediaStreamSource = null }
  if (mediaStream) { mediaStream.getTracks().forEach(t => t.stop()); mediaStream = null }
  if (audioContext) { try { audioContext.close() } catch {} ; audioContext = null }
})
</script>

<style scoped>
.tutor-layout { display: flex; height: 100%; gap: 0; }
.chat-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; background: rgba(255,255,255,0.5); border-radius: 16px 0 0 16px; overflow: hidden; }
.chat-messages { flex: 1; overflow-y: auto; padding: 20px; scroll-behavior: smooth; }
.new-chat-bar { display: flex; align-items: center; gap: 12px; padding: 6px 16px; background: #f0f9ff; border-bottom: 1px solid #e0f2fe; }
.history-hint { font-size: 11px; color: #94a3b8; }
.chat-messages::-webkit-scrollbar { width: 5px; }
.chat-messages::-webkit-scrollbar-thumb { background: #d9d9d9; border-radius: 3px; }

.msg { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 18px; animation: msgIn 0.3s ease; }
@keyframes msgIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.msg-user { flex-direction: row; justify-content: flex-end; }
.msg-ai { flex-direction: row; }
.msg-avatar { width: 36px; height: 36px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 20px; flex-shrink: 0; background: #e8f4fd; }
.msg-avatar-img { background: none; padding: 0; overflow: hidden; border-radius: 8px; }
.msg-avatar-img img { width: 100%; height: 100%; object-fit: cover; object-position: 20% 15%; display: block; }
.user-avatar { background: #e6f7ff; order: 2; }
.msg-user .msg-avatar { order: 2; }
.msg-bubble { max-width: 75%; padding: 14px 18px; border-radius: 18px; font-size: 14px; line-height: 1.7; }
.msg-user .msg-bubble { background: linear-gradient(135deg, #3b82f6, #6366f1); color: #fff; border-bottom-right-radius: 4px; box-shadow: 0 2px 12px rgba(59,130,246,0.25); }
.msg-ai .msg-bubble { background: #fff; color: #303133; border-bottom-left-radius: 4px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); border: 1px solid #f1f5f9; }
.agent-tag { font-size: 11px; color: #409EFF; margin-bottom: 4px; font-weight: 600; }
.cursor { color: #409EFF; animation: blink 0.8s infinite; }
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }
.thinking { display: flex; align-items: center; gap: 8px; padding: 14px 18px; }
.thinking-spinner { width: 16px; height: 16px; border: 2px solid #e0e0e0; border-top-color: #409EFF; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.dot:nth-child(2){animation-delay:.2s} .dot:nth-child(3){animation-delay:.4s}
@keyframes bounce { 0%,60%,100%{transform:translateY(0);opacity:.4} 30%{transform:translateY(-7px);opacity:1} }

/* TTS 朗读按钮 */
.tts-btn {
  margin-top: 8px;
  padding: 4px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}
.tts-btn:hover { background: #eff6ff; color: #3b82f6; border-color: #bfdbfe; }

/* 场景3：询问是否生成资源的按钮 */
.ask-generate-buttons {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.ask-btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}
.ask-btn.yes {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  color: white;
}
.ask-btn.yes:hover {
  background: linear-gradient(135deg, #16a34a, #15803d);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(34, 197, 94, 0.3);
}
.ask-btn.no {
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}
.ask-btn.no:hover {
  background: #e2e8f0;
  color: #475569;
}
.tts-btn.playing { background: #eff6ff; color: #2563eb; border-color: #93c5fd; }

.chat-input-area { padding: 12px 20px 20px; background: linear-gradient(180deg, rgba(255,255,255,0) 0%, rgba(255,255,255,0.9) 15%, #fff 100%); border-top: 1px solid rgba(0,0,0,0.04); }
.input-row { display: flex; align-items: center; gap: 10px; }
.input-wrapper { flex: 1; }
.chat-input-native { width: 100%; height: 48px; padding: 0 20px; border: 2px solid #e2e8f0; border-radius: 28px; font-size: 14px; color: #1e293b; background: #f8fafc; outline: none; transition: all 0.3s; font-family: inherit; box-sizing: border-box; }
.chat-input-native::placeholder { color: #94a3b8; font-size: 13px; }
.chat-input-native:focus { border-color: #3b82f6; background: #fff; box-shadow: 0 0 0 4px rgba(59,130,246,0.08), 0 2px 12px rgba(0,0,0,0.04); }
.chat-input-native:disabled { background: #f1f5f9; color: #94a3b8; cursor: not-allowed; }
.send-btn { font-family: inherit; font-size: 15px; font-weight: 600; background: linear-gradient(135deg, #3b82f6, #6366f1); color: white; padding: 0 20px 0 14px; height: 46px; display: flex; align-items: center; gap: 6px; border: none; border-radius: 26px; cursor: pointer; flex-shrink: 0; box-shadow: 0 2px 8px rgba(59,130,246,0.3); transition: all 0.25s; }
.send-btn:hover { background: linear-gradient(135deg, #2563eb, #4f46e5); box-shadow: 0 6px 24px rgba(59,130,246,0.45); transform: translateY(-1px); }
.send-btn:active { transform: scale(0.95); }
.send-btn:disabled { background: #cbd5e1; color: #94a3b8; box-shadow: none; cursor: not-allowed; transform: none; }
.voice-btn { width: 46px; height: 46px; border-radius: 50%; flex-shrink: 0; background: #f1f5f9; color: #64748b; border: 2px solid #e5e7eb; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.3s; }
.voice-btn:hover { background: #eff6ff; color: #3b82f6; border-color: #bfdbfe; }
.voice-btn:disabled { background: #f1f5f9; color: #cbd5e1; border-color: #e5e7eb; cursor: not-allowed; }
.voice-btn.recording { background: #fef2f2; color: #ef4444; border-color: #fecaca; animation: pulse-rec 1.2s infinite; }
.voice-btn.listening { background: #eff6ff; color: #3b82f6; border-color: #93c5fd; animation: pulse-listen 1s infinite; }
.voice-btn.retry-btn { background: #fff7ed; color: #f97316; border-color: #fed7aa; }
.voice-btn.retry-btn:hover { background: #fffedd; color: #ea580c; border-color: #fdba74; }

@keyframes pulse-listen { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); } }

/* 语音状态提示 */
.voice-status { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 8px 16px; margin-top: 8px; border-radius: 8px; font-size: 13px; font-weight: 500; }
.recording-status { background: #fef2f2; color: #dc2626; }
.confirm-status { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; flex-wrap: wrap; }
.voice-check { font-size: 16px; }
.voice-confirm-text { font-size: 12px; color: #64748b; }
.voice-confirm-content { font-size: 14px; color: #1e293b; max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.voice-countdown { font-size: 11px; color: #94a3b8; margin-left: auto; font-family: 'JetBrains Mono', monospace; }
.voice-send-now { padding: 4px 12px; background: #16a34a; color: #fff; border: none; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; margin-left: 8px; transition: all 0.2s; }
.voice-send-now:hover { background: #15803d; }
.listening-status { background: #eff6ff; color: #2563eb; }
.retry-status { background: #fff7ed; color: #ea580c; }
.error-status { background: #fef2f2; color: #dc2626; }
.voice-pulse { width: 10px; height: 10px; background: #ef4444; border-radius: 50%; animation: pulse-rec 1s infinite; }
.voice-spinner { width: 14px; height: 14px; border: 2px solid #3b82f6; border-top-color: transparent; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse-rec { 0%,100%{box-shadow:0 0 0 0 rgba(239,68,68,0.4)} 50%{box-shadow:0 0 0 10px rgba(239,68,68,0)} }
.voice-bar { display: flex; align-items: center; gap: 10px; margin-top: 10px; padding: 8px 14px; background: #fffbe6; border-radius: 10px; font-size: 13px; }
.voice-transcript { display: flex; align-items: center; gap: 8px; padding: 10px 14px; margin-top: 8px; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; font-size: 13px; color: #374151; }
.voice-transcript-icon { font-size: 16px; }
.voice-transcript-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 右侧面板 */
.side-panel { width: 300px; flex-shrink: 0; background: #fff; border-radius: 0 16px 16px 0; padding: 14px 10px; overflow-y: auto; border-left: 1px solid #f0f0f0; }

/* ===== RAG 检索结果面板（聊天区域内） ===== */
.rag-panel-chat {
  margin: 0 20px 8px;
  background: #fff;
  border: 1px solid rgba(59,130,246,0.12);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 6px rgba(59,130,246,0.06);
  animation: ragFadeIn 0.3s ease;
}
@keyframes ragFadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}
.rag-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  user-select: none;
}
.rag-panel-header:hover {
  background: rgba(59,130,246,0.03);
}
.rag-panel-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.rag-panel-icon { font-size: 14px; }
.rag-panel-title {
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
}
.rag-panel-count {
  font-size: 10px;
  color: #3b82f6;
  background: rgba(59,130,246,0.08);
  padding: 1px 6px;
  border-radius: 6px;
}
.rag-panel-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 幻觉率徽章 */
.rag-hallucination-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', monospace;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.hallucination-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  display: inline-block;
}
.rag-hallucination-badge.level-low { color: #16a34a; background: rgba(34,197,94,0.1); }
.rag-hallucination-badge.level-medium { color: #d97706; background: rgba(245,158,11,0.1); }
.rag-hallucination-badge.level-high { color: #dc2626; background: rgba(239,68,68,0.1); }

.rag-score-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  font-family: 'JetBrains Mono', monospace;
}
.rag-score-badge.score-high { color: #16a34a; background: rgba(34,197,94,0.1); }
.rag-score-badge.score-medium { color: #d97706; background: rgba(245,158,11,0.1); }
.rag-score-badge.score-low { color: #64748b; background: rgba(100,116,139,0.1); }
.rag-toggle-icon {
  font-size: 10px;
  color: #94a3b8;
  transition: transform 0.2s;
  display: inline-block;
}
.rag-toggle-icon.open { transform: rotate(180deg); }

.rag-panel-body {
  border-top: 1px solid rgba(59,130,246,0.06);
  padding: 12px;
  max-height: 520px;
  overflow-y: auto;
}
.rag-panel-body::-webkit-scrollbar { width: 4px; }
.rag-panel-body::-webkit-scrollbar-track { background: transparent; }
.rag-panel-body::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.rag-panel-body::-webkit-scrollbar-thumb:hover { background: #94a3b8; }

/* ===== 指标量化区 ===== */
.rag-metrics-section {
  display: flex;
  align-items: stretch;
  gap: 16px;
  padding: 4px;
}
.rag-metric-circle {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.circle-progress {
  position: relative;
  width: 108px;
  height: 108px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #16a34a;
}
.circle-progress.level-medium { color: #d97706; }
.circle-progress.level-high { color: #dc2626; }
.circle-svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.circle-content {
  position: relative;
  text-align: center;
  z-index: 2;
}
.circle-value {
  font-size: 22px;
  font-weight: 800;
  font-family: 'JetBrains Mono', monospace;
  line-height: 1.1;
  color: inherit;
}
.circle-label {
  font-size: 10px;
  color: #64748b;
  margin-top: 2px;
  font-weight: 500;
}

.rag-metric-details { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }

/* ===== 右侧简洁可信度描述 ===== */
.rag-credibility-summary {
  flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 4px;
}
.credibility-title {
  display: flex; align-items: center; gap: 6px; margin-bottom: 2px;
}
.credibility-icon { font-size: 14px; }
.credibility-text { font-size: 13px; font-weight: 700; color: #1e293b; }
.credibility-percent {
  font-size: 13px; font-weight: 800; margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
}
.credibility-percent.score-high { color: #16a34a; }
.credibility-percent.score-medium { color: #d97706; }
.credibility-percent.score-low { color: #64748b; }
.credibility-detail {
  font-size: 11.5px; color: #475569; line-height: 1.5;
}
.credibility-detail strong { color: #3b82f6; font-weight: 700; }
.check-pass { color: #16a34a; font-weight: 500; }
.check-warn { color: #d97706; font-weight: 500; }
.credibility-bar-wrap {
  height: 5px; border-radius: 5px; background: #f1f5f9; overflow: hidden; margin: 4px 0 2px;
}
.credibility-bar-fill {
  height: 100%; border-radius: 5px; transition: width 0.6s ease;
}
.credibility-bar-fill.score-high { background: linear-gradient(90deg, #22c55e, #16a34a); }
.credibility-bar-fill.score-medium { background: linear-gradient(90deg, #f59e0b, #d97706); }
.credibility-bar-fill.score-low { background: linear-gradient(90deg, #94a3b8, #64748b); }
.credibility-hint {
  font-size: 11px; font-weight: 500; margin-top: 2px;
}
.credibility-hint.level-low { color: #16a34a; }
.credibility-hint.level-medium { color: #d97706; }
.credibility-hint.level-high { color: #dc2626; }

.rag-section-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, #e2e8f0, transparent);
  margin: 12px 0 10px;
}

/* ===== 片段标题栏 ===== */
.rag-chunks-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 0 2px;
}
.rag-chunks-title {
  font-size: 11px;
  font-weight: 600;
  color: #1e293b;
}
.rag-chunks-hint {
  font-size: 9px;
  color: #94a3b8;
}

/* ===== 片段卡片 ===== */
.rag-chunk-item {
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 8px;
  margin-bottom: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.2s;
  cursor: pointer;
}
.rag-chunk-item:hover,
.rag-chunk-item.expanded {
  background: #fff;
  border-color: rgba(59,130,246,0.2);
  box-shadow: 0 2px 8px rgba(59,130,246,0.06);
}
.rag-chunk-item:last-child { margin-bottom: 0; }

.rag-chunk-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.rag-chunk-top-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.rag-chunk-idx {
  font-size: 9px;
  font-weight: 700;
  color: #3b82f6;
  background: rgba(59,130,246,0.1);
  padding: 1px 5px;
  border-radius: 3px;
  flex-shrink: 0;
  font-family: 'JetBrains Mono', monospace;
}
.rag-chunk-source {
  font-size: 10px;
  color: #64748b;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rag-chunk-score {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
  flex-shrink: 0;
}
.rag-chunk-score.score-high { color: #16a34a; background: rgba(34,197,94,0.1); }
.rag-chunk-score.score-medium { color: #d97706; background: rgba(245,158,11,0.1); }
.rag-chunk-score.score-low { color: #64748b; background: rgba(100,116,139,0.1); }

.rag-chunk-text {
  font-size: 11.5px;
  color: #334155;
  line-height: 1.65;
  text-align: justify;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: all 0.2s;
}
.rag-chunk-text.full-text {
  -webkit-line-clamp: unset;
  display: block;
  overflow: visible;
  white-space: pre-wrap;
}

.rag-chunk-bottom {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
}
.rag-chunk-kp {
  font-size: 10px;
  color: #7c3aed;
  background: rgba(124,58,237,0.06);
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1.4;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rag-chunk-expand-hint {
  font-size: 9px;
  color: #94a3b8;
  flex-shrink: 0;
  font-weight: 500;
}
.rag-chunk-item.expanded .rag-chunk-expand-hint { color: #3b82f6; }

/* ===== 面板底部 ===== */
.rag-panel-footer {
  padding: 8px 12px;
  background: linear-gradient(90deg, rgba(34,197,94,0.04), rgba(59,130,246,0.04));
  border-radius: 8px;
  font-size: 10.5px;
  color: #16a34a;
  font-weight: 500;
  margin-top: 10px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  border: 1px solid rgba(34,197,94,0.1);
}
.rag-panel-footer .footer-warn { color: #dc2626; }
.rag-panel-footer .footer-pass { color: #16a34a; font-weight: 600; }
.rag-panel-footer .footer-divider { color: #cbd5e1; }

/* RAG 面板动画 */
.rag-slide-enter-active,
.rag-slide-leave-active {
  transition: all 0.3s ease;
}
.rag-slide-enter-from,
.rag-slide-leave-to {
  opacity: 0;
  transform: translateY(8px);
  max-height: 0;
  margin: 0 20px;
  padding: 0;
}
.rag-expand-enter-active,
.rag-expand-leave-active {
  transition: all 0.25s ease;
  max-height: 300px;
  overflow: hidden;
}
.rag-expand-enter-from,
.rag-expand-leave-to {
  max-height: 0;
  opacity: 0;
}
.side-panel::-webkit-scrollbar { width: 3px; }
.side-panel::-webkit-scrollbar-thumb { background: #d9d9d9; border-radius: 2px; }
.panel-card { background: #f8fafc; border-radius: 12px; padding: 14px; margin-bottom: 10px; }
.panel-title { display: flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 600; margin-bottom: 10px; color: #303133; }
.profile-empty { text-align: center; color: #c0c4cc; font-size: 12px; padding: 24px 0; }

/* 今日学习数据 */
.stats-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 12px; }
.stat-box { background: #fff; border-radius: 10px; padding: 10px; text-align: center; border: 1px solid #f0f0f0; }
.stat-box.accent { background: linear-gradient(135deg, #eff6ff, #f0f5ff); border-color: #dbeafe; }
.stat-val { font-size: 22px; font-weight: 800; color: #1e293b; line-height: 1.2; }
.stat-unit { font-size: 11px; color: #94a3b8; }
.stat-label { font-size: 11px; color: #64748b; margin-top: 2px; }

/* 最近动态 */
.activity-list { display: flex; flex-direction: column; gap: 8px; }
.activity-item { display: flex; gap: 10px; align-items: flex-start; }
.act-icon { font-size: 16px; flex-shrink: 0; margin-top: 1px; }
.act-body { flex: 1; min-width: 0; }
.act-text { font-size: 12px; color: #334155; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.act-time { font-size: 10px; color: #94a3b8; margin-top: 2px; }

/* 薄弱知识点 */
.weak-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.weak-tag { padding: 4px 12px; background: #fef2f2; border: 1px solid #fecaca; border-radius: 20px; font-size: 12px; color: #dc2626; font-weight: 500; }
.goto-practice { width: 100%; font-size: 12px; }

/* 引导 */
.onboarding-bubble { background: linear-gradient(135deg, #f0f5ff, #e8f4fd) !important; border: 1px solid #d6e4ff; }
.quick-options { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; padding-top: 12px; border-top: 1px solid #e8e8e8; }
.option-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border: 1.5px solid #e2e8f0;
  border-radius: 20px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.25s;
  text-align: left;
  white-space: nowrap;
}
.option-chip:hover {
  border-color: #3b82f6;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  color: #2563eb;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
}
.option-chip::before {
  content: '💡';
  font-size: 12px;
}
.nav-buttons { display: flex; flex-direction: column; gap: 6px; margin-top: 14px; padding-top: 12px; border-top: 1px solid #e0e8f0; }
.nav-chip { display: block; width: 100%; padding: 10px 16px; background: linear-gradient(135deg, #3b82f6, #6366f1); color: #fff; border: none; border-radius: 12px; font-size: 14px; cursor: pointer; font-weight: 500; }
.nav-chip:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(59,130,246,0.35); }
.nav-chip.outline { background: #fff; color: #3b82f6; border: 2px solid #3b82f6; }

/* Markdown 排版 — 打字中 & 最终态共用 */
.msg-text, .msg-content-parsed {
  line-height: 1.7;
  font-size: 14px;
  color: #1a1a2e;
  word-break: break-word;
}
/* 标题 */
.msg-text :deep(h1), .msg-content-parsed :deep(h1) { font-size: 1.3em; margin: 16px 0 8px; font-weight: 700; color: #0f172a; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px; }
.msg-text :deep(h2), .msg-content-parsed :deep(h2) { font-size: 1.15em; margin: 14px 0 6px; font-weight: 700; color: #1e293b; }
.msg-text :deep(h3), .msg-content-parsed :deep(h3) { font-size: 1.05em; margin: 12px 0 4px; font-weight: 600; color: #334155; }
.msg-text :deep(h4), .msg-text :deep(h5), .msg-text :deep(h6),
.msg-content-parsed :deep(h4), .msg-content-parsed :deep(h5), .msg-content-parsed :deep(h6) { font-size: 1em; margin: 10px 0 4px; font-weight: 600; color: #475569; }
/* 段落 */
.msg-text :deep(p), .msg-content-parsed :deep(p) { margin: 0 0 10px; }
.msg-text :deep(p:last-child), .msg-content-parsed :deep(p:last-child) { margin-bottom: 0; }
/* 列表 */
.msg-text :deep(ul), .msg-text :deep(ol),
.msg-content-parsed :deep(ul), .msg-content-parsed :deep(ol) { margin: 6px 0; padding-left: 22px; }
.msg-text :deep(li), .msg-content-parsed :deep(li) { margin-bottom: 4px; line-height: 1.65; }
.msg-text :deep(li::marker), .msg-content-parsed :deep(li::marker) { color: #3b82f6; }
/* 行内代码 */
.msg-text :deep(code), .msg-content-parsed :deep(code) {
  font-family: 'SF Mono', 'Fira Code', Consolas, monospace;
  font-size: 12.5px;
  background: #f1f5f9;
  color: #e11d48;
  padding: 1px 5px;
  border-radius: 4px;
  border: 1px solid #e2e8f0;
}
/* 代码块外的 pre（非 renderContentWithCode 生成的） */
.msg-text :deep(pre), .msg-content-parsed :deep(pre) {
  background: #1e293b;
  padding: 14px 16px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 10px 0;
  border: 1px solid #334155;
}
.msg-text :deep(pre code), .msg-content-parsed :deep(pre code) {
  background: none;
  color: #e2e8f0;
  padding: 0;
  border: none;
  font-size: 13px;
  line-height: 1.6;
}
/* 引用块 */
.msg-text :deep(blockquote), .msg-content-parsed :deep(blockquote) {
  border-left: 3px solid #3b82f6;
  padding: 8px 14px;
  margin: 10px 0;
  background: linear-gradient(135deg, #eff6ff, #f0f9ff);
  border-radius: 0 8px 8px 0;
  color: #475569;
  font-size: 13.5px;
}
/* 表格 */
.msg-text :deep(table), .msg-content-parsed :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 13px;
}
.msg-text :deep(th), .msg-text :deep(td),
.msg-content-parsed :deep(th), .msg-content-parsed :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 7px 12px;
  text-align: left;
}
.msg-text :deep(th), .msg-content-parsed :deep(th) {
  background: #f8fafc;
  font-weight: 600;
  color: #1e293b;
}
.msg-text :deep(tr:nth-child(even)), .msg-content-parsed :deep(tr:nth-child(even)) { background: #f8fafc; }
/* 分割线 */
.msg-text :deep(hr), .msg-content-parsed :deep(hr) { border: none; border-top: 1px solid #e2e8f0; margin: 14px 0; }
/* 加粗 / 斜体 */
.msg-text :deep(strong), .msg-content-parsed :deep(strong) { color: #0f172a; font-weight: 600; }
.msg-text :deep(em), .msg-content-parsed :deep(em) { color: #64748b; }
/* 链接 */
.msg-text :deep(a), .msg-content-parsed :deep(a) { color: #2563eb; text-decoration: none; border-bottom: 1px dashed #2563eb; }
.msg-text :deep(a:hover), .msg-content-parsed :deep(a:hover) { border-bottom-style: solid; }

.code-editor-inline {
  border-radius: 10px;
  margin: 12px 0;
  overflow: hidden;
  border: 1px solid #334155;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.code-header-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 14px;
  background: #1e293b;
  border-bottom: 1px solid #334155;
}

.code-lang {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.code-pre {
  margin: 0;
  padding: 16px 18px;
  background: #0f172a;
  overflow-x: auto;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #e2e8f0;
  white-space: pre;
  tab-size: 4;
}

.code-pre code {
  font-family: inherit;
  background: none;
  padding: 0;
  color: inherit;
}

.code-pre::-webkit-scrollbar { height: 6px; }
.code-pre::-webkit-scrollbar-track { background: rgba(255,255,255,0.1); border-radius: 3px; }
.code-pre::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.2); border-radius: 3px; }

/* 历史会话面板 */
.history-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.3);
  z-index: 1000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 80px;
}
.history-panel {
  background: #fff;
  border-radius: 16px;
  width: 420px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 40px rgba(0,0,0,0.15);
  overflow: hidden;
}
.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid #f0f0f0;
}
.history-close {
  background: none;
  border: none;
  font-size: 16px;
  color: #999;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}
.history-close:hover {
  background: #f5f5f5;
  color: #333;
}
.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}
.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  cursor: pointer;
  transition: background 0.15s;
}
.history-item:hover {
  background: #f0f7ff;
}
.history-item-main {
  flex: 1;
  min-width: 0;
}
.history-preview {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 3px;
}
.history-meta {
  font-size: 11px;
  color: #94a3b8;
  display: flex;
  gap: 12px;
}
.history-delete {
  background: none;
  border: none;
  font-size: 14px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
  opacity: 0.4;
  transition: opacity 0.2s;
  flex-shrink: 0;
}
.history-delete:hover {
  opacity: 1;
  background: #fef2f2;
}
.history-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
  font-size: 14px;
}

/* PPT 操作按钮 */
.ppt-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.ppt-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  text-decoration: none;
  border: none;
}
.ppt-btn.preview {
  background: #f0f9ff;
  color: #3b82f6;
  border: 1px solid #bfdbfe;
}
.ppt-btn.preview:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}
.ppt-btn.download {
  background: #10b981;
  color: white;
}
.ppt-btn.download:hover {
  background: #059669;
}

/* PPT 预览弹窗 */
.ppt-preview-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  animation: fadeIn 0.2s ease;
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.ppt-preview-container {
  width: 90vw;
  height: 85vh;
  background: white;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.ppt-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.ppt-preview-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}
.ppt-preview-close {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 18px;
  color: #64748b;
  transition: all 0.2s;
}
.ppt-preview-close:hover {
  background: #fee2e2;
  color: #ef4444;
}
.ppt-preview-body {
  flex: 1;
  overflow: hidden;
}
.ppt-preview-body iframe {
  width: 100%;
  height: 100%;
  border: none;
}
</style>
