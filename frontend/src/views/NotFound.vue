<template>
  <div class="not-found">
    <div class="nf-inner">
      <div class="nf-code">404</div>
      <div class="nf-text">页面走丢了</div>
      <div class="nf-desc">你要找的页面不存在，可能已被移除或地址有误</div>
      <button class="nf-btn" @click="$router.push('/student/home')">返回首页</button>
    </div>
  </div>
</template>

<style scoped>
.not-found { display: flex; align-items: center; justify-content: center; min-height: 100vh; background: #f8f7f4; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
.nf-inner { text-align: center; }
.nf-code { font-size: 120px; font-weight: 900; color: #e2e8f0; line-height: 1; margin-bottom: 16px; background: linear-gradient(135deg, #6366f1, #8b5cf6); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
.nf-text { font-size: 24px; font-weight: 700; color: #1e293b; margin-bottom: 8px; }
.nf-desc { font-size: 14px; color: #94a3b8; margin-bottom: 24px; }
.nf-btn { background: #6366f1; color: #fff; font-size: 14px; font-weight: 600; padding: 10px 28px; border: none; border-radius: 10px; cursor: pointer; font-family: inherit; transition: all 0.15s; }
.nf-btn:hover { background: #4f46e5; transform: translateY(-1px); }
</style>
