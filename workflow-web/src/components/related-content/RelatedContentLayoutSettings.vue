<template>
  <el-form-item label="区域宽度"><el-input-number :model-value="layout.width" :min="10" :max="100" @update:model-value="update('width', $event)" /><span>%</span></el-form-item>
  <el-form-item label="外层高度"><el-input-number :model-value="layout.height" :min="120" :max="3000" @update:model-value="update('height', $event)" /><span>px</span></el-form-item>
  <el-form-item label="内边距"><el-input-number :model-value="layout.padding" :min="0" :max="80" @update:model-value="update('padding', $event)" /><span>px</span></el-form-item>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ presentation: { type: Object, required: true } })
const layout = computed(() => props.presentation.layout || {})
// 留空使用宿主默认布局；不能将区域高度写入子列表的内部滚动高度。
function update(key, value) {
  const next = { ...layout.value }
  if (value == null) delete next[key]
  else next[key] = value
  props.presentation.layout = next
}
</script>
