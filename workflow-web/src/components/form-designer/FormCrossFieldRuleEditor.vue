<template>
  <div class="cross-field-editor">
    <el-alert
      v-if="errors.length"
      type="error"
      :closable="false"
      :title="errors[0]"
      show-icon
    />
    <div v-for="(rule, index) in rules" :key="rule.id || index" class="cross-field-rule">
      <div class="cross-field-rule-heading">
        <strong>规则 {{ index + 1 }}</strong>
        <el-button type="danger" link :aria-label="`删除跨字段规则 ${index + 1}`" @click="remove(index)">删除</el-button>
      </div>
      <el-form-item label="当前字段"><span>{{ fieldLabel }}</span></el-form-item>
      <el-form-item label="比较方式">
        <el-select :model-value="rule.operator" aria-label="跨字段比较方式" @update:model-value="update(index, 'operator', $event)">
          <el-option v-for="item in CROSS_FIELD_OPERATORS" :key="item.value" :label="`${item.label} ${item.symbol}`" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="比较字段">
        <el-select :model-value="rule.targetFieldCode" filterable placeholder="请选择同一实体中的字段" aria-label="跨字段比较字段" @update:model-value="update(index, 'targetFieldCode', $event)">
          <el-option v-if="rule.targetFieldCode && !candidates.some(item => item.fieldCode === rule.targetFieldCode)" :value="rule.targetFieldCode" :label="`已失效：${rule.targetFieldCode}`" disabled />
          <el-option v-for="item in candidates" :key="item.fieldCode" :value="item.fieldCode" :label="`${label(item)}（${item.fieldCode}）`" />
        </el-select>
      </el-form-item>
      <el-form-item label="错误提示">
        <el-input :model-value="rule.message || ''" maxlength="200" placeholder="留空时自动生成提示" aria-label="跨字段错误提示" @update:model-value="update(index, 'message', $event)" />
      </el-form-item>
      <p class="cross-field-preview">规则预览：{{ preview(rule) }}</p>
    </div>
    <p class="cross-field-help">当前字段可见且可编辑时自动校验；隐藏、只读或禁用时跳过。任一值为空时跳过比较，必填由字段原有属性控制。</p>
    <el-button v-if="malformed" type="danger" plain @click="emit('update:modelValue', { version: 1, rules: [] })">清除无效跨字段配置</el-button>
    <el-button v-else :disabled="rules.length >= CROSS_FIELD_MAX_RULES" @click="add">＋添加规则</el-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  CROSS_FIELD_MAX_RULES,
  CROSS_FIELD_OPERATORS,
  areCrossFieldTypesCompatible,
  validateCrossFieldConfiguration
} from '@/shared/form-cross-field-validation'

const props = defineProps({
  modelValue: { default: null },
  field: { type: Object, required: true },
  fields: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])
const label = field => field?.fieldLabel || field?.fieldName || field?.fieldCode || '当前字段'
const fieldLabel = computed(() => label(props.field))
const malformed = computed(() => props.modelValue != null && (
  props.modelValue.version !== 1 || !Array.isArray(props.modelValue.rules)
  || props.modelValue.rules.some(rule => !rule || typeof rule !== 'object' || Array.isArray(rule))
))
const rules = computed(() => malformed.value ? [] : (props.modelValue?.rules || []))
const errors = computed(() => validateCrossFieldConfiguration(props.modelValue, props.field, props.fields))
const candidates = computed(() => props.fields.filter(field => field.fieldCode !== props.field.fieldCode
  && areCrossFieldTypesCompatible(props.field.fieldType, field.fieldType)))

/** 保留数组顺序和稳定标识；空数组显式覆盖发布投影中的旧配置。 */
function persist(value) {
  emit('update:modelValue', { version: 1, rules: value })
}
function add() {
  if (rules.value.length >= CROSS_FIELD_MAX_RULES) return
  // 内网 HTTP 页面可能没有 randomUUID；规则标识用于稳定追踪，不作为安全凭证。
  const id = globalThis.crypto?.randomUUID?.().replaceAll('-', '')
    || `${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`
  persist([...rules.value, { id: `cf_${id}`, operator: 'GE', targetFieldCode: '', message: '' }])
}
function update(index, key, value) {
  persist(rules.value.map((rule, i) => i === index ? { ...rule, [key]: value } : rule))
}
function remove(index) {
  persist(rules.value.filter((_, i) => i !== index))
}
function preview(rule) {
  const target = props.fields.find(field => field.fieldCode === rule.targetFieldCode)
  const operator = CROSS_FIELD_OPERATORS.find(item => item.value === rule.operator)
  return `${fieldLabel.value} ${operator?.symbol || '？'} ${target ? label(target) : '请选择比较字段'}`
}
</script>

<style scoped>
.cross-field-editor { display: grid; gap: 12px; }
.cross-field-rule { padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.cross-field-rule-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.cross-field-rule :deep(.el-select) { width: 100%; }
.cross-field-preview, .cross-field-help { margin: 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
</style>
