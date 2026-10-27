<template>
  <div class="ppt-viewer">
    <!-- 加载状态 -->
    <div v-if="loading" class="ppt-loading">
      <div class="ppt-loading-spinner"></div>
      <p>正在加载课件...</p>
    </div>
    <!-- 幻灯片图片 -->
    <div v-else-if="slideImages.length" class="ppt-slides">
      <div class="ppt-slide-nav">
        <button class="ppt-nav-btn" @click="prevSlide" :disabled="currentSlide === 0">‹</button>
        <span class="ppt-slide-num">{{ currentSlide + 1 }} / {{ slideImages.length }}</span>
        <button class="ppt-nav-btn" @click="nextSlide" :disabled="currentSlide === slideImages.length - 1">›</button>
      </div>
      <div class="ppt-slide-area">
        <template v-if="!failedSlideIndices.has(currentSlide)">
          <img
            :src="slideImages[currentSlide]"
            class="ppt-slide-img"
            alt="slide"
            @error="onSlideImageError(currentSlide)"
            loading="lazy"
          />
        </template>
        <div v-else class="ppt-slide-fallback">
          <div class="ppt-fallback-icon">📄</div>
          <div class="ppt-fallback-title">第 {{ currentSlide + 1 }} 页幻灯片暂不可用</div>
          <div class="ppt-fallback-hint">PPT 源文件或预渲染图片未在服务器上找到，请联系管理员部署 data/xxxppt课件 目录</div>
        </div>
      </div>
    </div>
    <!-- 降级：纯文本内容 -->
    <div v-else class="ppt-content">
      <div v-html="fallbackContent"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'

const props = defineProps({ content: { type: String, default: '' } })

const loading = ref(false)
const slideImages = ref([])
const currentSlide = ref(0)
const failedSlideIndices = ref(new Set())

const isFileRef = computed(() => props.content && props.content.startsWith('[PPT_FILE:'))
const fileName = computed(() => {
  if (!isFileRef.value) return ''
  const raw = props.content.replace('[PPT_FILE:', '').replace(']', '')
  return raw.split(/[/\\]/).pop()
})

const fallbackContent = computed(() => {
  if (!props.content) return '<p>暂无课件内容</p>'
  return props.content.replace(/\n/g, '<br>')
})

function prevSlide() { if (currentSlide.value > 0) currentSlide.value-- }
function nextSlide() { if (currentSlide.value < slideImages.value.length - 1) currentSlide.value++ }
function onSlideImageError(idx) {
  const next = new Set(failedSlideIndices.value)
  next.add(idx)
  failedSlideIndices.value = next
}

// 键盘翻页
function onKeydown(e) {
  if (!slideImages.value.length) return
  if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') prevSlide()
  if (e.key === 'ArrowRight' || e.key === 'ArrowDown') nextSlide()
}

async function loadSlides() {
  if (!isFileRef.value || !fileName.value) return
  loading.value = true
  slideImages.value = []
  currentSlide.value = 0
  failedSlideIndices.value = new Set()

  try {
    // 调用后端API获取幻灯片URL列表（同时触发PPT转图片）
    const res = await fetch('/api/ppt/slides/' + encodeURIComponent(fileName.value))
    if (res.ok) {
      const data = await res.json()
      if (data && data.data && Array.isArray(data.data) && data.data.length > 0) {
        slideImages.value = data.data
        loading.value = false
        return
      }
    }
  } catch (e) {
    console.warn('PptViewer: API加载失败，尝试静态路径', e)
  }

  // 降级：使用静态图片路径
  const staticImages = []
  for (let i = 1; i <= 12; i++) {
    staticImages.push('/ppt/slide-' + i + '.png')
  }
  slideImages.value = staticImages
  loading.value = false
}

watch(() => props.content, () => { currentSlide.value = 0; loadSlides() })
onMounted(() => {
  if (isFileRef.value) loadSlides()
  document.addEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.ppt-viewer { width: 100%; }

.ppt-loading {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 60px 20px; background: #f8fafc; border-radius: 12px; gap: 12px;
}
.ppt-loading-spinner {
  width: 36px; height: 36px; border: 3px solid #e2e8f0; border-top-color: #8b5cf6;
  border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.ppt-loading p { color: #64748b; font-size: 14px; }

.ppt-slides { background: #1e293b; border-radius: 12px; overflow: hidden; }

.ppt-slide-nav {
  display: flex; align-items: center; justify-content: center; gap: 16px;
  padding: 10px; background: #0f172a;
}
.ppt-nav-btn {
  width: 36px; height: 36px; border-radius: 8px; border: 1px solid #334155;
  background: #1e293b; color: #e2e8f0; font-size: 20px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: all 0.2s;
}
.ppt-nav-btn:hover:not(:disabled) { background: #334155; border-color: #475569; }
.ppt-nav-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.ppt-slide-num { color: #94a3b8; font-size: 13px; font-family: 'JetBrains Mono', monospace; min-width: 60px; text-align: center; }

.ppt-slide-area {
  display: flex; align-items: center; justify-content: center;
  padding: 20px; background: #111827;
}
.ppt-slide-img {
  max-width: 100%; max-height: 70vh; border-radius: 6px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.4);
}

.ppt-slide-fallback {
  width: 100%; max-width: 960px; aspect-ratio: 16 / 9;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 16px; color: #fff; padding: 40px; text-align: center;
  box-shadow: 0 4px 20px rgba(0,0,0,0.4);
}
.ppt-fallback-icon { font-size: 64px; line-height: 1; opacity: 0.9; }
.ppt-fallback-title { font-size: 20px; font-weight: 600; }
.ppt-fallback-hint { font-size: 13px; opacity: 0.8; max-width: 480px; }

.ppt-content {
  padding: 24px; background: #f8fafc; border-radius: 12px; line-height: 1.8; color: #334155;
}
</style>
