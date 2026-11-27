<template>
  <span>{{ label || code }}</span>
  <small v-if="label && code && label !== code" class="parameter-option-code">{{ code }}</small>
</template>

<script>
/** Element Plus 按 label 搜索，加入编码便于定位参数，映射仍使用独立的 value。 */
export function parameterOptionLabel(label, code) {
  return label && code && label !== code ? `${label} · ${code}` : label || code
}
</script>

<script setup>
defineProps({
  label: String,
  code: String
})
</script>

<style scoped>
.parameter-option-code {
  margin-inline-start: 12px;
  color: var(--el-text-color-secondary);
}
</style>
