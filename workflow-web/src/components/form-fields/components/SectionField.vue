<template>
  <div class="section-field">
    <div class="section-bar"></div>
    <div class="section-title">{{ title }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  field: {
    type: Object,
    required: true
  }
})

const title = computed(() => {
  const f = props.field || {}
  return f.fieldLabel || f.fieldName || f.title || '节标题'
})
</script>

<style scoped>
.section-field {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 12px 0;
  box-sizing: border-box;
}

.section-bar {
  width: 4px;
  height: 18px;
  background-color: #409eff;
  border-radius: 2px;
  margin-right: 10px;
  flex-shrink: 0;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  line-height: 1;
}
</style>
