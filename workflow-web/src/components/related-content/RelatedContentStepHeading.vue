<script setup>
defineProps({ number: { type: String, required: true }, title: { type: String, required: true }, description: { type: String, default: '' } })
</script>
<template>
  <div class="step-heading"><span class="step-heading__number">{{ number }}</span><div><strong>{{ title }}</strong><small>{{ description }}</small></div></div>
</template>
<style scoped>
.step-heading { display: flex; align-items: center; justify-content: flex-start; gap: 16px; margin-bottom: 20px; }
.step-heading > div { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.step-heading__number { display: flex; flex: 0 0 34px; align-items: center; justify-content: center; width: 34px; height: 34px; border-radius: 50%; background: var(--el-color-primary); color: #fff; font-weight: 700; }
.step-heading strong { font-size: 16px; }
.step-heading small { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.55; }
</style>
