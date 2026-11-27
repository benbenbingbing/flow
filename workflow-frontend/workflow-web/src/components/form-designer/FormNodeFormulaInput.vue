<template>
  <div class="formula-input">
    <el-input ref="input" :model-value="modelValue" :disabled="disabled" :aria-label="label"
      type="textarea" :rows="3" :placeholder="placeholder" @update:model-value="$emit('update:modelValue', $event)" />
    <div class="formula-tools">
      <el-select model-value="" :disabled="disabled || !fields.length" filterable placeholder="插入字段" aria-label="插入字段" @change="insertField">
        <el-option v-for="field in fields" :key="fieldKey(field)" :label="field.fieldName || field.fieldLabel || fieldKey(field)" :value="fieldKey(field)" />
      </el-select>
      <el-select v-if="parameters.length" model-value="" :disabled="disabled" filterable placeholder="插入页面参数" aria-label="插入页面参数" @change="insertParameter">
        <el-option v-for="parameter in parameters" :key="parameter.code"
          :label="parameterOptionLabel(parameter.name, parameter.code)" :value="parameter.code">
          <ParameterOptionLabel :label="parameter.name" :code="parameter.code" />
        </el-option>
      </el-select>
      <el-select v-if="contextParameters.length" model-value="" :disabled="disabled" filterable
        placeholder="插入上下文参数" aria-label="插入上下文参数" @change="insertContext">
        <el-option v-for="parameter in contextParameters" :key="parameter.key"
          :label="parameterOptionLabel(parameter.label, parameter.key)" :value="parameter.key">
          <ParameterOptionLabel :label="parameter.label" :code="parameter.key" />
        </el-option>
      </el-select>
      <el-dropdown trigger="click" :disabled="disabled" @command="insert">
        <el-button :disabled="disabled">插入函数</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="TODAY()">TODAY() · 当前日期</el-dropdown-item>
            <el-dropdown-item command="ADD_DAYS(TODAY(), 3)">ADD_DAYS · 日期加减天数</el-dropdown-item>
            <el-dropdown-item command="DATEDIFF(TODAY(), ADD_DAYS(TODAY(), 3))">DATEDIFF · 日期相差天数</el-dropdown-item>
            <el-dropdown-item command="IF(true, TODAY(), ADD_DAYS(TODAY(), 7))">IF · 按条件取值</el-dropdown-item>
            <el-dropdown-item v-if="contextParameters.length" command='COALESCE(null, "")'>COALESCE · 空值时取备用值</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <div class="formula-help" v-pre>用 ${字段编码} 引用字段。支持四则运算、比较和日期函数。</div>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import ParameterOptionLabel, { parameterOptionLabel } from '@/components/page-parameters/ParameterOptionLabel.vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  parameters: { type: Array, default: () => [] },
  contextParameters: { type: Array, default: () => [] },
  label: { type: String, default: '计算公式' },
  placeholder: { type: String, default: '例如 ${quantity} * ${price}' },
  disabled: Boolean
})
const emit = defineEmits(['update:modelValue'])
const input = ref()
const fieldKey = field => field.fieldCode || field.fieldKey
const insertField = code => insert('${' + code + '}')
const insertParameter = code => insert('${param.' + code + '}')
const insertContext = key => insert('${ctx.' + key + '}')

/** 在光标处插入片段，使已有表达式可继续组合字段和函数。 */
async function insert(fragment) {
  if (props.disabled) return
  const textarea = input.value?.textarea
  const start = textarea?.selectionStart ?? props.modelValue.length
  const end = textarea?.selectionEnd ?? start
  emit('update:modelValue', props.modelValue.slice(0, start) + fragment + props.modelValue.slice(end))
  await nextTick()
  textarea?.focus()
  textarea?.setSelectionRange(start + fragment.length, start + fragment.length)
}
</script>

<style scoped>
.formula-input { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.formula-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.formula-tools :deep(.el-select) { max-width: 160px; }
.formula-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
</style>
