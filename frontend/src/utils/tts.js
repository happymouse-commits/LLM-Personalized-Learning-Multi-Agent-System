/**
 * TTS 工具 — 三级降级：MiMo VoiceClone → MiMo TTS → 浏览器TTS
 * 策略：第一级失败立即降级到第二级，第二级失败才用浏览器
 * 两级都有后端缓存，重复文本秒级响应
 */

import { useUserStore } from '../store/user'

let audioContext = null
let currentSource = null
let isSpeaking = false
let currentUtterance = null
let currentRequestId = 0  // 用于防止并发请求冲突

// 配置常量
const MAX_TEXT_LENGTH = 1500       // 前端文本长度限制（留余量给后端2000）
const TTS_TIMEOUT_MS = 30000      // TTS请求超时时间30秒
const DEBOUNCE_MS = 300           // 防抖间隔

let lastSpeakTime = 0

const speechSynth = window.speechSynthesis

export function stopTts() {
  currentRequestId++  // 使正在进行的请求失效
  if (currentSource) {
    try { currentSource.stop() } catch (e) { /* ignore */ }
    currentSource = null
  }
  if (speechSynth) speechSynth.cancel()
  currentUtterance = null
  isSpeaking = false
}

function getAudioContext() {
  if (!audioContext || audioContext.state === 'closed') {
    audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 24000 })
  }
  if (audioContext.state === 'suspended') audioContext.resume()
  return audioContext
}

function speakBrowser(text, callbacks) {
  if (!speechSynth) {
    console.warn('浏览器不支持TTS')
    callbacks.onError?.(new Error('No TTS supported'))
    return
  }
  speechSynth.cancel()
  const utter = new SpeechSynthesisUtterance(text)
  utter.lang = 'zh-CN'
  utter.rate = 1.15
  utter.pitch = 1.0
  currentUtterance = utter
  utter.onstart = () => {
    isSpeaking = true
    callbacks.onStart?.()
  }
  utter.onend = () => {
    isSpeaking = false
    currentUtterance = null
    callbacks.onEnd?.()
  }
  utter.onerror = (e) => {
    console.warn('浏览器TTS错误:', e.error)
    isSpeaking = false
    currentUtterance = null
    callbacks.onError?.(new Error(e.error || 'Browser TTS error'))
  }
  speechSynth.speak(utter)
}

function playPcmAudio(pcmBase64, callbacks) {
  try {
    const pcmBytes = Uint8Array.from(atob(pcmBase64), c => c.charCodeAt(0))
    if (pcmBytes.length < 2) {
      throw new Error('Audio data too short')
    }
    const pcmFloat = new Float32Array(pcmBytes.length / 2)
    const dv = new DataView(pcmBytes.buffer)
    for (let i = 0; i < pcmFloat.length; i++) {
      pcmFloat[i] = dv.getInt16(i * 2, true) / 32768.0
    }
    const ctx = getAudioContext()
    const buffer = ctx.createBuffer(1, pcmFloat.length, 24000)
    buffer.getChannelData(0).set(pcmFloat)
    const source = ctx.createBufferSource()
    source.buffer = buffer
    source.connect(ctx.destination)
    currentSource = source
    source.onended = () => {
      currentSource = null
      isSpeaking = false
      callbacks.onEnd?.()
    }
    source.onerror = (e) => {
      console.error('Audio playback error:', e)
      currentSource = null
      isSpeaking = false
      callbacks.onError?.(new Error('Audio playback failed'))
    }
    source.start()
  } catch (e) {
    console.error('PCM audio decode error:', e)
    isSpeaking = false
    callbacks.onError?.(e)
  }
}

/**
 * 构造带认证的请求头 —— 与 api/index.js 的 axios 拦截器保持一致
 * 后端 /api/tts/** 要求登录，未携带 token 会被 Spring Security 拒绝（500/403）
 */
function buildAuthHeaders(extra = {}) {
  const headers = { ...extra }
  try {
    const store = useUserStore()
    if (store.token) headers['Authorization'] = 'Bearer ' + store.token
  } catch (e) { /* store 未初始化时忽略 */ }
  return headers
}

/**
 * 带超时的fetch请求
 */
async function fetchWithTimeout(url, options, timeoutMs = TTS_TIMEOUT_MS) {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const resp = await fetch(url, { ...options, signal: controller.signal })
    return resp
  } finally {
    clearTimeout(timeoutId)
  }
}

/**
 * 第1级：MiMo VoiceClone TTS（使用参考音频）
 */
async function tryVoiceClone(text) {
  const resp = await fetchWithTimeout('/api/tts/synthesize', {
    method: 'POST',
    headers: buildAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({
      text,
      voiceInstruction: '自然清晰的中文朗读，语速稍快'
    })
  })
  if (!resp.ok) {
    const errText = await resp.text().catch(() => '')
    throw new Error(`VoiceClone HTTP ${resp.status}: ${errText}`)
  }
  const data = await resp.json()
  if (data.error) throw new Error(`VoiceClone: ${data.error}`)
  if (!data.audio) throw new Error('VoiceClone: No audio data in response')
  return data.audio
}

/**
 * 第2级：MiMo TTS（标准TTS，不使用参考音频）
 */
async function tryMimoTts(text) {
  const resp = await fetchWithTimeout('/api/tts/synthesize-simple', {
    method: 'POST',
    headers: buildAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({
      text,
      voiceInstruction: '自然清晰的中文朗读'
    })
  })
  if (!resp.ok) {
    const errText = await resp.text().catch(() => '')
    throw new Error(`MiMo TTS HTTP ${resp.status}: ${errText}`)
  }
  const data = await resp.json()
  if (data.error) throw new Error(`MiMo TTS: ${data.error}`)
  if (!data.audio) throw new Error('MiMo TTS: No audio data in response')
  return data.audio
}

/**
 * 朗读文本 — 三级降级：VoiceClone → MiMo TTS → 浏览器
 * 策略：第一级失败→第二级救急→实在不行才浏览器
 * 两级都有后端缓存，重复文本命中缓存秒级响应
 */
export async function speakWithTts(text, callbacks = {}) {
  if (!text || !text.trim()) return

  // 防抖：避免快速连续调用
  const now = Date.now()
  if (now - lastSpeakTime < DEBOUNCE_MS) {
    console.warn('TTS防抖：跳过请求')
    return
  }
  lastSpeakTime = now

  // 停止当前播放
  stopTts()

  // 记录请求ID，用于检测是否已被取消
  const requestId = ++currentRequestId
  isSpeaking = true
  callbacks.onStart?.()

  // 清理文本并限制长度
  let cleanText = text.trim()
    .replace(/[😊🤖💡🎯📚✨🚀❤️👍👎🎵🎶]/g, '')  // 移除emoji
    .replace(/\s+/g, ' ')  // 合并空白
    .trim()

  if (cleanText.length > MAX_TEXT_LENGTH) {
    console.warn(`TTS文本过长(${cleanText.length}字符)，截断至${MAX_TEXT_LENGTH}`)
    cleanText = cleanText.substring(0, MAX_TEXT_LENGTH)
  }

  if (!cleanText) {
    isSpeaking = false
    callbacks.onError?.(new Error('Text is empty after cleaning'))
    return
  }

  // ===== 第1级：MiMo VoiceClone（最高质量，带参考音频）=====
  try {
    const audio = await tryVoiceClone(cleanText)
    if (requestId !== currentRequestId) return
    playPcmAudio(audio, callbacks)
    return
  } catch (e) {
    if (requestId !== currentRequestId) return
    console.warn('[TTS] 第1级 VoiceClone 降级:', e.message)
  }

  // ===== 第2级：MiMo TTS（标准质量，无参考音频）=====
  try {
    const audio = await tryMimoTts(cleanText)
    if (requestId !== currentRequestId) return
    playPcmAudio(audio, callbacks)
    return
  } catch (e) {
    if (requestId !== currentRequestId) return
    console.warn('[TTS] 第2级 MiMo TTS 降级:', e.message)
  }

  // ===== 第3级：浏览器TTS（最后兜底）=====
  console.warn('[TTS] 前两级均失败，降级到浏览器TTS')
  speakBrowser(cleanText, callbacks)
}

export function isTtsPlaying() {
  return isSpeaking
}
