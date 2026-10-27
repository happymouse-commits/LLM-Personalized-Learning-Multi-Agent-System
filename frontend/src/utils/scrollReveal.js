/**
 * 滚动渐入动画指令 v-reveal
 *
 * 用法:
 *   <div v-reveal>             默认 fade-up（从下往上）
 *   <div v-reveal:left>        从左滑入
 *   <div v-reveal:right>       从右滑入
 *   <div v-reveal:scale>       缩放淡入
 *   <div v-reveal="{delay:200}">  自定义延迟 (ms)
 *   <div v-reveal="{threshold:0.3}">  自定义触发阈值 (0~1)
 *   <div v-reveal="{duration:0.4}">  自定义过渡时长 (s)，默认 0.6
 *
 * 网格卡片 staggered 效果:
 *   v-for 中直接传 :key 索引 * 延迟基数作为 delay
 *   <div v-for="(item,i) in list" :key="i"
 *        v-reveal="{delay: i * 60}">
 */

const ANIM_MAP = {
  'fade-up':    { opacity: 0, transform: 'translateY(40px)' },
  'fade-left':  { opacity: 0, transform: 'translateX(-40px)' },
  'fade-right': { opacity: 0, transform: 'translateX(40px)' },
  'scale-in':   { opacity: 0, transform: 'scale(0.92)' },
}

const observerMap = new WeakMap()

function makeTransition(s) {
  return `opacity ${s}s cubic-bezier(.22,1,.36,1), transform ${s}s cubic-bezier(.22,1,.36,1)`
}

export default {
  mounted(el, binding) {
    // 解析参数
    let type = 'fade-up'
    let delay = 0
    let threshold = 0.15
    let duration = 0.6

    if (typeof binding.value === 'object') {
      delay = binding.value.delay ?? 0
      threshold = binding.value.threshold ?? 0.15
      duration = binding.value.duration ?? 0.6
      type = binding.value.type ?? (binding.arg || 'fade-up')
    } else if (binding.arg) {
      type = binding.arg
    }

    const anim = ANIM_MAP[type] || ANIM_MAP['fade-up']

    // 设置初始状态
    Object.assign(el.style, {
      ...anim,
      transition: makeTransition(duration),
      willChange: 'opacity, transform',
    })

    // IntersectionObserver
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setTimeout(() => {
            el.style.opacity = '1'
            el.style.transform = 'translateY(0) translateX(0) scale(1)'
          }, delay)
          observer.unobserve(el)
        }
      },
      { threshold }
    )

    observer.observe(el)
    observerMap.set(el, observer)
  },

  unmounted(el) {
    const obs = observerMap.get(el)
    if (obs) {
      obs.unobserve(el)
      observerMap.delete(el)
    }
  },
}
