<template>
  <div class="custom-validator-editor">
    <p class="help">只影响当前表单的当前字段；提交时始终校验，以下时机用于提前提示。</p>
    <el-alert v-for="error in errors" :key="error" :title="error" type="error" :closable="false" />
    <el-button v-if="malformed" type="danger" plain @click="save([])">清空无效配置</el-button>
    <div v-for="(rule, index) in rules" :key="index" class="validator-rule">
      <div class="rule-header">
        <el-select :model-value="keyOf(rule)" placeholder="选择校验器" @update:model-value="replace(index, $event)">
          <el-option v-if="!options.some(option => keyOf(option) === keyOf(rule))"
            :value="keyOf(rule)" :label="`${keyOf(rule)}（未安装或不适用）`" disabled />
          <el-option v-for="option in options" :key="keyOf(option)" :value="keyOf(option)"
            :label="`${option.label} · v${option.version}`"
            :disabled="rules.some((item, position) => position !== index && keyOf(item) === keyOf(option))" />
        </el-select>
        <el-button type="danger" link @click="remove(index)">删除</el-button>
      </div>
      <p v-if="descriptor(rule)" class="help">
        {{ descriptor(rule).description }}
        <br />适用实体：{{ descriptor(rule).supportedEntityCodes.join('、') || '全部实体' }}
      </p>
      <el-checkbox-group :model-value="rule.triggers || []" @update:model-value="update(index, { triggers: $event })">
        <el-checkbox value="BLUR">失去焦点</el-checkbox>
        <el-checkbox value="CHANGE">值变化</el-checkbox>
      </el-checkbox-group>
      <ConfigSchemaEditor v-if="descriptor(rule)?.configSchema.length"
        class="validator-params" :grouped="false"
        :schema="descriptor(rule).configSchema" :model-value="rule.params || {}"
        @update:model-value="update(index, { params: $event })" />
    </div>
    <el-button :disabled="malformed || !nextOption || rules.length >= CUSTOM_VALIDATION_MAX_RULES" @click="add">添加校验器</el-button>
    <p v-if="!options.length" class="help">当前实体和字段类型暂无适用校验器，请在项目扩展入口注册。</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ConfigSchemaEditor from './ConfigSchemaEditor.vue'
import { getCustomValidator, getCustomValidatorOptions } from '@/contracts/validator-registry'
import { CUSTOM_VALIDATION_MAX_RULES, customValidatorDefaults, validateCustomValidationConfig } from '@/shared/form-custom-validation'

const props = defineProps({ modelValue: { default: undefined }, field: { type: Object, required: true }, entityCode: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])
const keyOf = rule => `${rule?.name || ''}@${rule?.version || ''}`
const descriptor = rule => getCustomValidator(rule?.name, rule?.version)
const options = computed(() => getCustomValidatorOptions(props.entityCode, props.field.fieldType))
const malformed = computed(() => props.modelValue !== undefined && (!props.modelValue || props.modelValue.version !== 1
  || !Array.isArray(props.modelValue.rules) || props.modelValue.rules.some(rule => !rule || typeof rule !== 'object' || Array.isArray(rule))))
const rules = computed(() => malformed.value ? [] : props.modelValue?.rules || [])
const errors = computed(() => validateCustomValidationConfig(props.modelValue, props.field, props.entityCode))
const nextOption = computed(() => options.value.find(option => !rules.value.some(rule => keyOf(rule) === keyOf(option))))
// 显式空数组随草稿/发布保存，避免删除最后一条后旧字段投影恢复原规则。
const save = rules => emit('update:modelValue', { version: 1, rules })
const binding = option => ({ name: option.name, version: option.version, params: customValidatorDefaults(option), triggers: ['BLUR'] })
function add() { if (nextOption.value && rules.value.length < CUSTOM_VALIDATION_MAX_RULES) save([...rules.value, binding(nextOption.value)]) }
function remove(index) { save(rules.value.filter((_rule, position) => position !== index)) }
function update(index, patch) { save(rules.value.map((rule, position) => position === index ? { ...rule, ...patch } : rule)) }
/** 更换实现时重置参数，防止旧校验器的参数被错误传给另一实现。 */
function replace(index, key) {
  const option = options.value.find(item => keyOf(item) === key)
  if (option) update(index, binding(option))
}
</script>

<style scoped>
.help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.validator-rule { padding: 12px; margin: 12px 0; border: 1px solid var(--el-border-color); border-radius: 6px; }
.rule-header { display: flex; gap: 12px; align-items: center; }
.rule-header .el-select { flex: 1; }
.validator-params { margin-top: 12px; }
.el-alert { margin-bottom: 8px; }
</style>
