<!--
  操作条件参数编辑模板，只负责生成 { type, field, operator, value }。
  配套后端须支持已注册 type 的判定逻辑。fields 的格式为 [{ value, label }]。
-->
<template>
  <div>
    <el-select :model-value="modelValue.field" @update:model-value="update('field', $event)">
      <el-option v-for="field in fields" :key="field.value" :label="field.label" :value="field.value" />
    </el-select>
    <el-select :model-value="modelValue.operator || 'EQ'" @update:model-value="update('operator', $event)">
      <el-option label="等于" value="EQ" />
      <el-option label="不等于" value="NE" />
    </el-select>
    <el-input :model-value="modelValue.value" @update:model-value="update('value', $event)" />
  </div>
</template>

<script setup>
import { actionRuleConditionProps, actionRuleConditionEmits } from '@/contracts/action-rule.js'

const props = defineProps(actionRuleConditionProps)
const emit = defineEmits(actionRuleConditionEmits)

/** 返回完整条件对象，保留注册的 type 及当前编辑器没有展示的业务参数。 */
function update(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>
