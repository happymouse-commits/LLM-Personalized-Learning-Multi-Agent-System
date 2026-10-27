<template>
  <div class="resource-button-group">
    <div class="resource-button-header">
      <span class="button-icon">📚</span>
      <span class="button-text">已为您生成学习资源，点击下方按钮查看：</span>
    </div>
    <div class="resource-buttons">
      <button
        v-for="resource in resources"
        :key="resource.resourceId"
        :class="['resource-btn', resource.type]"
        @click="$emit('click', resource)"
      >
        <span class="btn-icon">{{ getTypeIcon(resource.type) }}</span>
        <span class="btn-title">{{ resource.title }}</span>
        <span class="btn-arrow">→</span>
      </button>
    </div>
    <div v-if="suggestPractice" class="practice-suggestion">
      <p class="suggestion-text">💡 建议您进行巩固练习，加深理解</p>
      <button class="practice-btn" @click="$emit('practice', practiceChapterId)">
        <span>✏️</span>
        <span>开始巩固练习</span>
      </button>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  resources: { type: Array, default: () => [] },
  suggestPractice: { type: Boolean, default: false },
  practiceChapterId: { type: String, default: '' }
})

defineEmits(['click', 'practice'])

function getTypeIcon(type) {
  const icons = {
    'article': '🎧',
    'code': '💻',
    'mindmap': '🗺️',
    'exercise': '📝',
    'course-video': '🎬',
    'course-ppt': '📑',
    'explanation': '📖',
    'weakness': '🎯',
    'summary': '📊'
  }
  return icons[type] || '📄'
}
</script>

<style scoped>
.resource-button-group {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08) 0%, rgba(168, 85, 247, 0.08) 100%);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 12px;
  padding: 16px;
  margin-top: 12px;
}

.resource-button-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.button-icon {
  font-size: 18px;
}

.button-text {
  font-size: 13px;
  color: #4f46e5;
  font-weight: 500;
}

.resource-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resource-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
  font-family: inherit;
}

.resource-btn:hover {
  background: #f5f3ff;
  border-color: #6366f1;
  transform: translateX(4px);
}

.btn-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.btn-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
}

.btn-arrow {
  color: #6366f1;
  font-weight: 600;
  opacity: 0;
  transition: opacity 0.2s;
}

.resource-btn:hover .btn-arrow {
  opacity: 1;
}

.practice-suggestion {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(99, 102, 241, 0.15);
}

.suggestion-text {
  font-size: 12px;
  color: #64748b;
  margin: 0 0 10px 0;
}

.practice-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 10px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.practice-btn:hover {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}
</style>
