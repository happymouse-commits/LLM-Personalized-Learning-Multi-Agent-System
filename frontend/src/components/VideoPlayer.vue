<template>
  <div class="vp-container">
    <!-- 占位层：iframe加载期间显示（不是黑屏） -->
    <div v-if="!iframeReady" class="vp-poster">
      <div class="vp-poster-bg"></div>
      <div class="vp-poster-content">
        <div class="vp-poster-icon">
          <svg viewBox="0 0 24 24" fill="white" width="48" height="48"><path d="M8 5v14l11-7z"/></svg>
        </div>
        <div class="vp-poster-text">视频加载中...</div>
      </div>
    </div>

    <!-- iframe：一加载完就覆盖占位层 -->
    <iframe
      ref="iframeRef"
      :src="embedUrl"
      class="vp-iframe"
      :class="{ visible: iframeReady }"
      allowfullscreen
      allow="autoplay; encrypted-media"
      frameborder="0"
      scrolling="no"
      referrerpolicy="origin-when-cross-origin"
      @load="onIframeLoad"
    ></iframe>

    <!-- 玻璃防误触层 -->
    <div class="vp-glass-overlay" @click.stop.prevent></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  src: { type: String, required: true },
  page: { type: Number, default: 1 }
})

const iframeRef = ref(null)
const iframeReady = ref(false)

function extractBvId(url) {
  const match = url.match(/BV[A-Za-z0-9]+/)
  return match ? match[0] : null
}

const embedUrl = computed(() => {
  const bvid = extractBvId(props.src)
  return bvid
    ? `https://player.bilibili.com/player.html?bvid=${bvid}&p=${props.page}&autoplay=1&danmaku=0&high_quality=1`
    : props.src
})

function onIframeLoad() {
  // iframe加载完成，隐藏占位层
  iframeReady.value = true
}

onUnmounted(() => {
  const iframe = iframeRef.value
  if (iframe && iframe.contentWindow) {
    try {
      iframe.contentWindow.postMessage(JSON.stringify({ event: 'command', command: 'pause' }), '*')
    } catch (e) {}
  }
})
</script>

<style scoped>
.vp-container {
  width: 100%;
  position: relative;
  aspect-ratio: 16 / 9;
  border-radius: 12px;
  overflow: hidden;
  background: #0f172a;
}
.vp-iframe {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: none;
  opacity: 0;
  transition: opacity 0.3s ease;
}
.vp-iframe.visible {
  opacity: 1;
}

/* 占位层：加载期间显示 */
.vp-poster {
  position: absolute;
  inset: 0;
  z-index: 2;
}
.vp-poster-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
}
.vp-poster-content {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  z-index: 1;
}
.vp-poster-icon {
  width: 72px;
  height: 72px;
  background: rgba(255,255,255,0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: poster-pulse 1.5s ease-in-out infinite;
}
@keyframes poster-pulse {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.05); opacity: 1; }
}
.vp-poster-text {
  font-size: 14px;
  color: rgba(255,255,255,0.5);
}

/* 玻璃防误触层 */
.vp-glass-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 75%;
  z-index: 10;
  cursor: default;
  background: transparent;
}
</style>
