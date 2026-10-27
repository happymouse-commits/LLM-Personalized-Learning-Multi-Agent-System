<template>
  <div class="agent-pipeline" v-if="activeAgents.length">
    <div class="pipeline-header">
      <div class="pipeline-title">🤖 智能体协作流</div>
    </div>
    <div class="pipeline-track">
      <div
        v-for="agent in activeAgents"
        :key="agent.id"
        :class="['pipeline-node', agent.status]"
      >
        <div v-if="agent !== activeAgents[0]" class="pipeline-line">
          <div class="line-fill" :class="{ active: agent.status === 'running' || agent.status === 'done' }"></div>
        </div>
        <div class="node-content">
          <div class="node-icon-wrap">
            <div class="node-icon">{{ agent.icon }}</div>
            <div v-if="agent.status === 'running'" class="node-spinner"></div>
            <div v-else-if="agent.status === 'done'" class="node-check">✓</div>
            <div v-else-if="agent.status === 'failed'" class="node-fail">✗</div>
          </div>
          <div class="node-info">
            <div class="node-name">{{ agent.name }}</div>
            <div class="node-task">{{ agent.task }}</div>
            <div class="node-detail" v-if="agent.detail">{{ agent.detail }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue'

const emit = defineEmits(['complete'])

const agents = reactive([])

const activeAgents = computed(() => agents.filter(a => a.status !== 'idle'))

function reset() {
  agents.length = 0
}

// 接收后端真实管道事件
function updateAgent(agentName, status, detail) {
  // 尝试精确匹配
  let agent = agents.find(a => a.id === agentName || a.name === agentName)
  // 模糊匹配
  if (!agent) {
    agent = agents.find(a =>
      agentName.includes(a.id) || a.id.includes(agentName) ||
      agentName.includes(a.name) || a.name.includes(agentName)
    )
  }
  // 动态创建新节点
  if (!agent) {
    agent = {
      id: agentName,
      name: agentName,
      icon: '🤖',
      task: detail || '处理中...',
      status: 'idle',
      detail: ''
    }
    agents.push(agent)
  }

  agent.status = status
  if (detail) agent.detail = detail

  if (status === 'done') {
    emit('complete')
  }
}

defineExpose({ run: updateAgent, reset, updateAgent })
</script>

<style scoped>
.agent-pipeline {
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 10px;
}
.pipeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.pipeline-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pipeline-track {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.pipeline-node {
  position: relative;
  padding: 4px 0;
}
.pipeline-line {
  position: absolute;
  left: 16px;
  top: -4px;
  width: 2px;
  height: 20px;
  background: #e2e8f0;
}
.line-fill.active {
  background: #3b82f6;
  animation: line-grow 0.3s ease;
}
.node-content {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.node-icon-wrap {
  position: relative;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 15px;
  transition: all 0.3s;
}
.pipeline-node.running .node-icon-wrap {
  background: #eff6ff;
  box-shadow: 0 0 0 3px rgba(59,130,246,0.15);
}
.pipeline-node.done .node-icon-wrap {
  background: #f0fdf4;
}
.pipeline-node.failed .node-icon-wrap {
  background: #fef2f2;
}
.node-spinner {
  position: absolute;
  inset: -3px;
  border: 2px solid transparent;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.node-check {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #22c55e;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-fail {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #ef4444;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.node-info { flex: 1; min-width: 0; }
.node-name { font-size: 12px; font-weight: 600; color: #334155; }
.node-task { font-size: 11px; color: #94a3b8; }
.node-detail {
  font-size: 10px;
  color: #3b82f6;
  margin-top: 1px;
  font-weight: 500;
}
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes line-grow { from { height: 0; } to { height: 20px; } }
</style>
