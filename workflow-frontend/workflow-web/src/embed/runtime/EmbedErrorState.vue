<template>
  <section
    class="embed-error-state"
    :class="`embed-error-state--${category}`"
    role="alert"
    aria-live="assertive"
  >
    <div class="embed-error-state__icon" aria-hidden="true">!</div>
    <h1>{{ title }}</h1>
    <p>{{ displayMessage }}</p>
    <p v-if="error?.traceId" class="embed-error-state__trace">
      跟踪号：{{ error.traceId }}
    </p>
    <button
      v-if="error?.recoverable"
      class="embed-error-state__retry"
      type="button"
      @click="$emit('retry')"
    >
      重试
    </button>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  error: {
    type: Object,
    default: null
  }
})

defineEmits(['retry'])

const category = computed(() => String(props.error?.category || 'unknown').toLowerCase())
const title = computed(() => {
  const titles = {
    SESSION: '需要重新打开',
    IDENTITY: '账号不可用',
    ORIGIN: '无法嵌入此页面',
    RESOURCE: '资源不可用',
    TRANSIENT: '暂时无法加载'
  }
  return titles[props.error?.category] || '页面无法加载'
})
const displayMessage = computed(() => props.error?.message || '请返回宿主系统后重试')
</script>

<style scoped>
.embed-error-state {
  box-sizing: border-box;
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 320px;
  padding: 40px 24px;
  color: var(--el-text-color-primary);
  text-align: center;
  background: var(--el-bg-color);
}

.embed-error-state__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: 14px;
  color: var(--el-color-danger);
  font-size: 24px;
  font-weight: 700;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 50%;
  background: var(--el-color-danger-light-9);
}

.embed-error-state h1 {
  margin: 0 0 8px;
  font-size: 20px;
}

.embed-error-state p {
  max-width: 560px;
  margin: 0;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.embed-error-state__trace {
  margin-top: 8px !important;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.embed-error-state__retry {
  margin-top: 20px;
  padding: 9px 18px;
  color: #fff;
  border: 0;
  border-radius: 6px;
  background: var(--el-color-primary);
  cursor: pointer;
}

.embed-error-state__retry:focus-visible {
  outline: 3px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}
</style>
