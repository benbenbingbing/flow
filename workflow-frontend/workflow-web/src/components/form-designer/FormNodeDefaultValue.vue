<template>
  <el-form-item label="默认值">
    <div class="node-default-value">
      <el-select :model-value="source" aria-label="默认值来源" @update:model-value="changeFieldDefaultMode(field, $event)">
        <el-option label="继承实体默认值" value="inherit" />
        <el-option label="无默认值" value="none" />
        <el-option label="固定值" value="fixed" />
        <el-option label="公式" value="formula" />
        <el-option label="数据源" value="dataSource" />
      </el-select>
      <el-button v-if="configTitles[source]" type="primary" link aria-haspopup="dialog" @click="openConfig">配置</el-button>
    </div>
  </el-form-item>

  <el-dialog v-model="configVisible" :title="configTitles[source]"
    width="min(640px, calc(100vw - 32px))" append-to-body destroy-on-close>
    <el-form label-width="108px" size="small" @submit.prevent>
      <el-form-item v-if="source === 'fixed'" label="固定默认值" class="default-fixed-value">
        <el-date-picker v-if="dateType" v-model="fixedValueDraft" aria-label="固定默认值" :type="dateType"
          :value-format="dateType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'" />
        <el-input-number v-else-if="numericType" :model-value="numberValue" aria-label="固定默认值"
          :precision="fieldType === 'INTEGER' ? 0 : undefined" controls-position="right" @update:model-value="fixedValueDraft = $event" />
        <el-select v-else-if="valueOptions.length" v-model="fixedValueDraft" aria-label="固定默认值" clearable>
          <el-option v-for="option in valueOptions" :key="String(option.value)" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-else v-model="fixedValueDraft" aria-label="固定默认值" placeholder="请输入默认值" />
      </el-form-item>
      <template v-else-if="source === 'formula'">
        <el-form-item label="默认值公式">
          <FormNodeFormulaInput v-model="formula" :fields="formulaFields" :parameters="parameters" label="默认值公式"
            :context-parameters="contextParameters"
            :placeholder="dateType ? '例如 ADD_DAYS(TODAY(), 3)' : undefined" />
          <p v-if="contextUnavailableReason" class="context-unavailable">{{ contextUnavailableReason }}</p>
        </el-form-item>
        <el-form-item>
          <details class="default-formula-trial">
            <summary>试算公式</summary>
            <el-input v-model="sampleText" aria-label="试算输入" type="textarea" :rows="2" placeholder='{"fields":{"days":3},"params":{}}' />
            <el-button size="small" @click="evaluateSample">试算</el-button>
            <span v-if="sampleResult" role="status">{{ sampleResult }}</span>
          </details>
        </el-form-item>
      </template>
      <FormNodeDataSourceBindingEditor v-else-if="source === 'dataSource'" v-model:extension-id="bindingDraft.extensionId"
        v-model:input-mapping-text="bindingDraft.inputMappingText" v-model:output-mapping-text="bindingDraft.outputMappingText"
        :interfaces="interfaces" :context-parameters="contextParameters" :help="interfaceHelp" output-placeholder='{"value":"data.defaultValue"}' />
    </el-form>
    <p class="default-value-scope">新增记录初始化时填充空字段，填入后可以修改。</p>
    <el-alert v-if="configError" :title="configError" type="error" :closable="false" show-icon />
    <template #footer>
      <el-button @click="configVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmConfig">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { evaluateLinkageExpression, hasFormContextReference } from '@flow/workflow-core/utils/calcEngine'
import { getInputParameterDefinitions } from '@flow/workflow-core/subform-parameter-contract'
import { resolvePageParameters } from '@flow/workflow-core/page-parameters'
import { resolveFormNodeBinding } from '@flow/workflow-core/form-node-property-schema'
import { useFieldDictionaryOptions } from '@/composables/useFieldDictionaryOptions'
import { getFormDataSourceInterfaceHelp } from '@/shared/config-field-help'
import { parseJsonConfig } from '@/utils/jsonConfig'
import {
  changeFieldDefaultMode, getDefaultFormulaError, getFieldDefaultError, readFieldDefaultDataSourceDraft, readFieldDefaultFormula,
  readFieldDefaultMode, writeFieldDefaultFormula
} from '@/shared/form-field-default'
import { formContextDefaults, formContextSample } from '@/shared/form-context-parameters'
import FormNodeFormulaInput from './FormNodeFormulaInput.vue'
import FormNodeDataSourceBindingEditor from './FormNodeDataSourceBindingEditor.vue'

const props = defineProps({
  field: { type: Object, required: true },
  fields: { type: Array, default: () => [] },
  viewConfig: { type: Object, default: () => ({}) },
  interfaces: { type: Array, default: () => [] }
})
const configTitles = {
  fixed: '配置固定默认值',
  formula: '配置默认值公式',
  dataSource: '配置默认值数据源'
}
const source = computed(() => readFieldDefaultMode(props.field))
const fieldType = computed(() => String(props.field.fieldType || '').toUpperCase())
const dateType = computed(() => ({ DATE: 'date', DATETIME: 'datetime' })[fieldType.value])
const numericType = computed(() => ['INTEGER', 'DECIMAL', 'DOUBLE', 'NUMBER'].includes(fieldType.value))
const dictionaryOptions = useFieldDictionaryOptions(() => [props.field])
const valueOptions = computed(() => {
  if (fieldType.value === 'BOOLEAN') return [{ label: '是', value: true }, { label: '否', value: false }]
  if (!['SELECT', 'RADIO'].includes(fieldType.value)) return []
  return dictionaryOptions.value[props.field.dictType] || props.field.options || []
})
const fixedValue = computed(() => {
  const value = props.field.defaultValue
  if (fieldType.value === 'BOOLEAN' && value != null && value !== '') return [true, 1, 'true', '1'].includes(value)
  return value ?? ''
})
const fixedValueDraft = ref('')
const numberValue = computed(() => fixedValueDraft.value == null || fixedValueDraft.value === ''
  ? undefined : Number(fixedValueDraft.value))
const formulaFields = computed(() => props.fields.filter(field =>
  field.uiConfigurable !== false && field.fieldCode && field.fieldCode !== props.field.fieldCode))
const parameters = computed(() => getInputParameterDefinitions(props.viewConfig.inputParameterSchema))
const interfaceHelp = getFormDataSourceInterfaceHelp('FIELD_DEFAULT')
const configVisible = ref(false)
const configError = ref('')
const formula = ref('')
const hasContextBinding = computed(() => resolveFormNodeBinding(props.field, 'FIELD').bindingType === 'ENTITY_FIELD')
const contextParameters = computed(() => hasContextBinding.value ? formContextDefaults : [])
const contextUnavailableReason = computed(() => hasContextBinding.value ? '' : '绑定实体字段后可选择上下文参数。')
const bindingDraft = ref({})
const sampleText = ref('')
const sampleResult = ref('')
watch([() => props.field, source], () => { configVisible.value = false })
watch(formula, () => { sampleResult.value = '' })

/** 从节点草稿复制弹窗内容，关闭或取消时可直接丢弃本次编辑。 */
function openConfig() {
  fixedValueDraft.value = fixedValue.value
  formula.value = readFieldDefaultFormula(props.field)
  bindingDraft.value = { ...readFieldDefaultDataSourceDraft(props.field, props.interfaces) }
  configError.value = ''
  sampleText.value = ''
  sampleResult.value = ''
  configVisible.value = true
}

/** 确认后才回写节点草稿，沿用设计器的保存流程；未改动的旧配置无需重写。 */
function confirmConfig() {
  const field = props.field
  if (source.value === 'fixed') {
    configError.value = getFieldDefaultError({ defaultValueMode: 'fixed', defaultValue: fixedValueDraft.value })
    if (configError.value) return
    if (fixedValueDraft.value !== fixedValue.value) {
      field.defaultValueMode = 'fixed'
      field.defaultValue = fixedValueDraft.value
    }
  } else if (source.value === 'formula') {
    const expression = formula.value
    configError.value = getDefaultFormulaError(expression)
    if (!configError.value && !hasContextBinding.value && hasFormContextReference(expression)) {
      configError.value = contextUnavailableReason.value
    }
    if (configError.value) return
    if (expression !== readFieldDefaultFormula(field)) writeFieldDefaultFormula(field, expression)
  } else if (source.value === 'dataSource') {
    configError.value = getFieldDefaultError({
      defaultValueMode: field.defaultValueMode,
      defaultDataSourceDraft: bindingDraft.value
    })
    if (configError.value) return
    const currentBinding = readFieldDefaultDataSourceDraft(field, props.interfaces)
    if (Object.entries(bindingDraft.value).some(([key, value]) => value !== currentBinding[key])) {
      field.defaultDataSourceDraft = { ...bindingDraft.value }
    }
  }
  configVisible.value = false
}

/** 用与表单相同的解析器和参数契约试算，样例输入只在编辑器中使用。 */
function evaluateSample() {
  try {
    const error = getDefaultFormulaError(formula.value)
    if (error) throw new Error(error)
    const sample = parseJsonConfig(sampleText.value, { fieldName: '试算输入' })
    const params = resolvePageParameters(props.viewConfig, sample.params)
    const value = evaluateLinkageExpression(formula.value, sample.fields || {}, new Date(), { params, contextValues: formContextSample })
    sampleResult.value = value == null ? '未得到值，请检查样例中的字段和参数' : `结果 ${JSON.stringify(value)}`
  } catch (error) {
    sampleResult.value = error.message
  }
}
</script>

<style scoped>
.node-default-value { display: flex; align-items: center; width: 100%; gap: 10px; }
.node-default-value > .el-select { flex: 1; min-width: 0; }
.context-unavailable { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.default-fixed-value :deep(.el-date-editor), .default-fixed-value :deep(.el-input-number) { width: 100%; }
.default-formula-trial { width: 100%; }
.default-formula-trial summary { cursor: pointer; color: var(--el-color-primary); }
.default-formula-trial .el-button { margin: 8px 8px 0 0; }
.default-value-scope { margin: 8px 0 12px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
