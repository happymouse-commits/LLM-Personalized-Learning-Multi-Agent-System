<template>
  <span
    v-if="score != null"
    :class="['hallucination-badge', riskClass, sizeClass]"
    :title="tooltipText"
  >
    <span class="badge-icon">{{ badgeIcon }}</span>
    <span class="badge-text">{{ displayText }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  score: { type: Number, default: null },
  risk: { type: String, default: '' },
  note: { type: String, default: '' },
  size: { type: String, default: 'small' } // small, medium, large
})

// 风险等级对应的样式
const riskClass = computed(() => {
  if (props.score == null) return ''
  if (props.score >= 0.85) return 'risk-low'
  if (props.score >= 0.70) return 'risk-medium'
  if (props.score >= 0.50) return 'risk-high'
  return 'risk-critical'
})

// 徽章图标
const badgeIcon = computed(() => {
  if (props.score == null) return ''
  if (props.score >= 0.85) return '🛡️'
  if (props.score >= 0.70) return '⚠️'
  if (props.score >= 0.50) return '⚠️'
  return '❌'
})

// 显示文字
const displayText = computed(() => {
  if (props.score == null) return ''
  const percentage = Math.round(props.score * 100)
  return `${percentage}%`
})

// 提示文字
const tooltipText = computed(() => {
  if (props.score == null) return ''
  const percentage = Math.round(props.score * 100)
  let riskLabel = ''
  if (props.score >= 0.85) riskLabel = '可信'
  else if (props.score >= 0.70) riskLabel = '基本可靠'
  else if (props.score >= 0.50) riskLabel = '存疑'
  else riskLabel = '高风险'

  let text = `可信度: ${percentage}% (${riskLabel})`
  if (props.note) text += `\n${props.note}`
  return text
})

// 尺寸样式
const sizeClass = computed(() => `size-${props.size}`)
</script>

<style scoped>
.hallucination-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  cursor: help;
}

/* 风险等级颜色 */
.risk-low {
  background: rgba(34, 197, 94, 0.1);
  color: #16a34a;
  border: 1px solid rgba(34, 197, 94, 0.2);
}

.risk-medium {
  background: rgba(234, 179, 8, 0.1);
  color: #ca8a04;
  border: 1px solid rgba(234, 179, 8, 0.2);
}

.risk-high {
  background: rgba(249, 115, 22, 0.1);
  color: #ea580c;
  border: 1px solid rgba(249, 115, 22, 0.2);
}

.risk-critical {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

/* 尺寸 */
.size-small {
  font-size: 10px;
  padding: 1px 6px;
}

.size-medium {
  font-size: 12px;
  padding: 2px 8px;
}

.size-large {
  font-size: 14px;
  padding: 4px 12px;
}

.badge-icon {
  font-size: 0.9em;
}

.badge-text {
  font-family: 'JetBrains Mono', monospace;
}
</style>
