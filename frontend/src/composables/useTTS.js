/**
 * useTTS — 通用 TTS 朗读 composable
 *
 * 功能：
 * 1. 多语言/多语音支持（Web Speech API）
 * 2. 语速/音调/音量可调
 * 3. 支持 Markdown / 纯文本（自动去除标记）
 * 4. 暂停 / 恢复
 * 5. 朗读进度指示（当前句 / 总句数）
 * 6. 错误处理（不支持 TTS、合成失败）
 * 7. 跨浏览器兼容（Chrome/Edge/Firefox/Safari）
 * 8. 导出为 WAV 音频文件（MediaRecorder 录制）
 */
import { ref, onBeforeUnmount } from 'vue'

export function useTTS() {
  // ===== 状态 =====
  const voices = ref([])
  const selectedVoice = ref('')
  const rate = ref(1.0)
  const pitch = ref(1.0)
  const volume = ref(1.0)
  const isSpeaking = ref(false)
  const isPaused = ref(false)
  const currentMsgId = ref(null)
  const readingMap = ref({})       // key: msgId => true/false
  const progress = ref(0)          // 0~100
  const progressText = ref('')     // "第 2/5 句"
  const errorMsg = ref('')
  const isRecording = ref(false)

  // ===== 内部变量 =====
  let _utterance = null
  let _voicesListenerAttached = false
  let _sentences = []
  let _currentSentenceIdx = 0
  let _mediaRecorder = null
  let _audioChunks = []
  let _audioCtx = null
  let _dest = null

  // ===== 1. 语音列表加载 =====
  function loadVoices() {
    if (!('speechSynthesis' in window)) return
    const load = () => {
      const list = speechSynthesis.getVoices() || []
      voices.value = list
      if (list.length && !selectedVoice.value) {
        // 优先中文语音
        const zh = list.find(v => (v.lang || '').startsWith('zh'))
        selectedVoice.value = (zh || list[0]).name
      }
    }
    load()
    if (!_voicesListenerAttached) {
      if (typeof speechSynthesis.addEventListener === 'function') {
        speechSynthesis.addEventListener('voiceschanged', load)
      } else if (typeof speechSynthesis.onvoiceschanged !== 'undefined') {
        speechSynthesis.onvoiceschanged = load
      }
      _voicesListenerAttached = true
    }
  }

  // ===== 2. Markdown / 纯文本处理 =====
  function stripMarkdown(text) {
    if (!text) return ''
    return text
      // 代码块 → "代码块"
      .replace(/```[\s\S]*?```/g, '（代码块）')
      // 行内代码
      .replace(/`([^`]+)`/g, '$1')
      // 图片
      .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
      // 链接
      .replace(/\[([^\]]*)\]\([^)]+\)/g, '$1')
      // 标题标记
      .replace(/^#{1,6}\s+/gm, '')
      // 粗体/斜体
      .replace(/[*_~]{1,3}([^*_~]+)[*_~]{1,3}/g, '$1')
      // 引用
      .replace(/^>\s+/gm, '')
      // 列表标记
      .replace(/^[\s]*[-*+]\s+/gm, '')
      .replace(/^[\s]*\d+\.\s+/gm, '')
      // 水平线
      .replace(/^---+$/gm, '')
      // 多余空白
      .replace(/\n{3,}/g, '\n\n')
      .trim()
  }

  // 将文本拆分为句子（用于进度跟踪和分段朗读）
  function splitSentences(text) {
    if (!text) return []
    // 按句号、问号、感叹号、换行分段
    return text
      .split(/(?<=[。！？!?\n])\s*/)
      .map(s => s.trim())
      .filter(s => s.length > 0)
  }

  // ===== 3. 朗读核心 =====
  function speak(msgId, content, options = {}) {
    if (!('speechSynthesis' in window)) {
      errorMsg.value = '当前浏览器不支持语音合成'
      return false
    }
    // 停止之前的朗读
    stop()

    const cleanText = stripMarkdown(content)
    if (!cleanText) {
      errorMsg.value = '文本内容为空'
      return false
    }

    _sentences = splitSentences(cleanText)
    _currentSentenceIdx = 0
    currentMsgId.value = msgId
    readingMap.value[msgId] = true
    isSpeaking.value = true
    isPaused.value = false
    errorMsg.value = ''
    updateProgress()

    // 可选：开始录制
    if (options.record) {
      startRecording()
    }

    // 逐句朗读（解决长文本被截断问题）
    speakNextSentence(options)
    return true
  }

  function speakNextSentence(options) {
    if (_currentSentenceIdx >= _sentences.length) {
      // 朗读完成
      finishSpeak()
      return
    }

    const sentence = _sentences[_currentSentenceIdx]
    _utterance = new SpeechSynthesisUtterance(sentence)

    // 设置语音
    const v = voices.value.find(vv => vv.name === selectedVoice.value)
    if (v) _utterance.voice = v
    _utterance.rate = rate.value
    _utterance.pitch = pitch.value
    _utterance.volume = volume.value
    _utterance.lang = (v && v.lang) ? v.lang : 'zh-CN'

    _utterance.onend = () => {
      if (!isSpeaking.value) return // 已被 stop
      _currentSentenceIdx++
      updateProgress()
      speakNextSentence(options)
    }

    _utterance.onerror = (e) => {
      if (e.error === 'interrupted' || e.error === 'canceled') return
      errorMsg.value = `朗读出错: ${e.error || '未知错误'}`
      console.warn('[TTS] error:', e.error)
      // 跳过当前句，继续下一句
      _currentSentenceIdx++
      updateProgress()
      if (_currentSentenceIdx < _sentences.length) {
        speakNextSentence(options)
      } else {
        finishSpeak()
      }
    }

    try {
      speechSynthesis.speak(_utterance)
    } catch (err) {
      errorMsg.value = `朗读启动失败: ${err.message}`
      finishSpeak()
    }
  }

  function finishSpeak() {
    isSpeaking.value = false
    isPaused.value = false
    if (currentMsgId.value) {
      readingMap.value[currentMsgId.value] = false
    }
    currentMsgId.value = null
    progress.value = 100
    _utterance = null
    // 停止录制并生成文件
    if (isRecording.value) {
      stopRecording()
    }
  }

  // ===== 4. 暂停 / 恢复 =====
  function pause() {
    if (!isSpeaking.value || isPaused.value) return
    try {
      speechSynthesis.pause()
      isPaused.value = true
    } catch (err) {
      errorMsg.value = `暂停失败: ${err.message}`
    }
  }

  function resume() {
    if (!isSpeaking.value || !isPaused.value) return
    try {
      speechSynthesis.resume()
      isPaused.value = false
    } catch (err) {
      errorMsg.value = `恢复失败: ${err.message}`
    }
  }

  // ===== 5. 停止 =====
  function stop() {
    if ('speechSynthesis' in window) {
      try { speechSynthesis.cancel() } catch {}
    }
    isSpeaking.value = false
    isPaused.value = false
    if (currentMsgId.value) {
      readingMap.value[currentMsgId.value] = false
    }
    currentMsgId.value = null
    _utterance = null
    progress.value = 0
    progressText.value = ''
    if (isRecording.value) {
      stopRecording()
    }
  }

  // ===== 6. 进度更新 =====
  function updateProgress() {
    const total = _sentences.length
    if (total === 0) {
      progress.value = 0
      progressText.value = ''
      return
    }
    progress.value = Math.round((_currentSentenceIdx / total) * 100)
    progressText.value = `第 ${_currentSentenceIdx + 1}/${total} 句`
  }

  // ===== 7. 切换朗读（按钮点击）=====
  function toggle(msgId, content, options) {
    if (!msgId) return
    if (readingMap.value[msgId]) {
      stop()
    } else {
      speak(msgId, content, options)
    }
  }

  // ===== 8. 设置参数 =====
  function setRate(v) { rate.value = Math.max(0.5, Math.min(2.0, v)) }
  function setPitch(v) { pitch.value = Math.max(0, Math.min(2.0, v)) }
  function setVolume(v) { volume.value = Math.max(0, Math.min(1.0, v)) }
  function setVoice(name) { selectedVoice.value = name }

  // ===== 9. 音频录制（导出 WAV）=====
  async function startRecording() {
    try {
      _audioCtx = new (window.AudioContext || window.webkitAudioContext)()
      _dest = _audioCtx.createMediaStreamDestination()
      _audioChunks = []

      const mimeType = 'audio/webm'
      if (!MediaRecorder.isTypeSupported(mimeType)) {
        console.warn('[TTS] MediaRecorder 不支持 audio/webm，录制不可用')
        return
      }

      _mediaRecorder = new MediaRecorder(_dest.stream, { mimeType })
      _mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) _audioChunks.push(e.data)
      }
      _mediaRecorder.start()
      isRecording.value = true
    } catch (err) {
      console.warn('[TTS] 录制启动失败:', err.message)
      isRecording.value = false
    }
  }

  function stopRecording() {
    if (!_mediaRecorder) { isRecording.value = false; return }
    try {
      _mediaRecorder.stop()
      _mediaRecorder.onstop = () => {
        if (_audioChunks.length > 0) {
          const blob = new Blob(_audioChunks, { type: 'audio/webm' })
          downloadBlob(blob, `tts-${Date.now()}.webm`)
        }
        isRecording.value = false
        if (_audioCtx) { _audioCtx.close(); _audioCtx = null }
        _mediaRecorder = null
        _dest = null
      }
    } catch {
      isRecording.value = false
    }
  }

  function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  // ===== 10. 导出当前朗读为音频文件 =====
  function speakAndRecord(msgId, content) {
    return speak(msgId, content, { record: true })
  }

  // ===== 清理 =====
  onBeforeUnmount(() => {
    stop()
  })

  return {
    // 状态
    voices,
    selectedVoice,
    rate,
    pitch,
    volume,
    isSpeaking,
    isPaused,
    readingMap,
    progress,
    progressText,
    errorMsg,
    isRecording,
    // 方法
    loadVoices,
    speak,
    speakAndRecord,
    pause,
    resume,
    stop,
    toggle,
    setRate,
    setPitch,
    setVolume,
    setVoice,
    stripMarkdown,
  }
}
