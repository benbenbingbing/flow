<template>
  <el-form :model="modelValue" label-width="108px" size="small" class="node-operation-permissions">
    <el-form-item label="允许转办">
      <el-switch :model-value="modelValue.allowTransfer" aria-label="允许转办" @update:model-value="update('allowTransfer', $event)" />
    </el-form-item>
    <el-form-item label="允许加签">
      <el-switch :model-value="modelValue.allowAddSign" aria-label="允许加签" @update:model-value="update('allowAddSign', $event)" />
    </el-form-item>
    <el-form-item label="允许终止">
      <template #label><ConfigHelpLabel label="允许终止" content="存在并行活动节点时，所有节点均允许才能终止流程。终止与撤回分别控制，互不替代。" /></template>
      <el-switch :model-value="modelValue.allowTerminate" aria-label="允许终止" @update:model-value="update('allowTerminate', $event)" />
    </el-form-item>
    <el-form-item label="允许撤回">
      <template #label><ConfigHelpLabel label="允许撤回" content="流程未完成且当前节点允许时，发起人可以撤回。存在并行活动节点时，所有节点均允许才能撤回；无需同时开启允许终止。" /></template>
      <el-switch :model-value="modelValue.allowWithdraw" aria-label="允许撤回" @update:model-value="update('allowWithdraw', $event)" />
    </el-form-item>
  </el-form>
</template>

<script setup>
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
const props = defineProps({ modelValue: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue'])
function update(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<style scoped>
.node-operation-permissions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}
.node-operation-permissions :deep(.el-form-item) { min-width: 0; }
</style>
