<template>
  <section class="page-parameter-mapping" :class="{ 'reference-list-mapping': referenceList }">
    <template v-if="!referenceList">
      <div class="parameter-heading">
        <strong>参数传递</strong>
        <el-button size="small" :disabled="!parameters.length" @click="add">增加映射</el-button>
      </div>
      <p>打开时传递当前表单最新值或列表当前行，由目标页面决定如何使用。传参不会修改实体关系或开放编辑权限。</p>
    </template>
    <el-alert v-if="!parameters.length" :title="referenceList
      ? '请先在目标列表的「输入参数」中配置参数和筛选用途，再发布列表。'
      : '目标页面尚未声明输入参数，请先在目标页面的“输入参数”中配置并发布。'" type="info" :closable="false" />
    <div v-for="(row, index) in modelValue" :key="index" class="parameter-row" :class="{ 'reference-list-row': referenceList }">
      <el-select :model-value="row.parameter" aria-label="目标参数" placeholder="目标参数" filterable @update:model-value="patch(index, { parameter: $event })">
        <el-option v-for="item in parameters" :key="item.code" :label="parameterOptionLabel(item.name, item.code)" :value="item.code">
          <ParameterOptionLabel :label="item.name" :code="item.code" />
        </el-option>
      </el-select>
      <el-select :model-value="row.sourceType" aria-label="来源类型" @update:model-value="patch(index, { sourceType: $event, sourceField: '', value: '' })">
        <el-option v-for="item in sourceTypes" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select
        v-if="row.sourceType === 'FIELD' || (referenceList && row.sourceType === 'ENTITY_FIELD')"
        :model-value="row.sourceField"
        filterable
        aria-label="来源字段"
        :placeholder="row.sourceType === 'ENTITY_FIELD' ? '选择实体属性' : (referenceList ? '选择表单属性' : '来源字段')"
        @update:model-value="patch(index, { sourceField: $event })"
      >
        <el-option v-for="field in (row.sourceType === 'ENTITY_FIELD' ? entityFields : sourceFields)" :key="field.fieldCode" :value="field.fieldCode" :label="`${field.fieldLabel || field.fieldName || field.fieldCode} (${field.fieldCode})`" />
      </el-select>
      <el-input v-else-if="row.sourceType === 'PARAMETER'" :model-value="row.sourceField" placeholder="来源参数编码" @update:model-value="patch(index, { sourceField: $event })" />
      <el-select v-else-if="row.sourceType === 'CONTEXT'" :model-value="row.sourceField" filterable
        aria-label="来源上下文参数" placeholder="选择上下文参数" @update:model-value="patch(index, { sourceField: $event })">
        <el-option v-for="item in formContextParameters" :key="item.key" :label="parameterOptionLabel(item.label, item.key)" :value="item.key">
          <ParameterOptionLabel :label="item.label" :code="item.key" />
        </el-option>
      </el-select>
      <el-input
        v-else-if="row.sourceType === 'LITERAL'"
        :model-value="literalText(row.value)"
        aria-label="固定值"
        :placeholder="referenceList ? undefined : '固定值（对象/数组填写 JSON）'"
        @update:model-value="patch(index, { value: $event })"
      />
      <span v-else>当前记录主键</span>
      <el-button link type="danger" @click="$emit('update:modelValue', modelValue.filter((_, i) => i !== index))">移除</el-button>
    </div>
    <el-button
      v-if="referenceList"
      class="reference-mapping-add"
      size="small"
      :disabled="modelValue.length >= parameters.length"
      @click="add"
    >增加映射</el-button>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { getInputParameterDefinitions } from '@flow/workflow-core/subform-parameter-contract'
import { formContextParameters } from '@/shared/form-context-parameters'
import ParameterOptionLabel, { parameterOptionLabel } from './ParameterOptionLabel.vue'
const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  schema: { type: Object, default: () => ({}) },
  sourceFields: { type: Array, default: () => [] },
  entityFields: { type: Array, default: () => [] },
  allowContext: Boolean,
  referenceList: Boolean
})
const emit = defineEmits(['update:modelValue'])
const parameters = computed(() => getInputParameterDefinitions(props.schema))
const sourceTypes = computed(() => [
  ...(props.referenceList ? [
      { label: '表单属性', value: 'FIELD' },
      { label: '实体属性', value: 'ENTITY_FIELD' },
      { label: '固定值', value: 'LITERAL' }
    ] : [
      { label: '当前数据字段', value: 'FIELD' },
      { label: '当前记录 ID', value: 'RECORD_ID' },
      { label: '当前页面参数', value: 'PARAMETER' },
      { label: '固定值', value: 'LITERAL' }
    ]),
  ...(props.allowContext || props.referenceList ? [{ label: '上下文参数', value: 'CONTEXT' }] : [])
])
const literalText = value => typeof value === 'object' ? JSON.stringify(value) : String(value ?? '')
function patch(index, value) { emit('update:modelValue', props.modelValue.map((row, i) => i === index ? { ...row, ...value } : row)) }
function add() { emit('update:modelValue', [...props.modelValue, { parameter: parameters.value.find(p => !props.modelValue.some(row => row.parameter === p.code))?.code || '', sourceType: 'FIELD', sourceField: '' }]) }
</script>
<style scoped>
.parameter-heading { display:flex; justify-content:space-between; align-items:center; }
p { color:var(--el-text-color-secondary); font-size:12px; line-height:1.6; }
.parameter-row { display:grid; grid-template-columns:1fr 150px 1fr auto; gap:10px; margin:12px 0; align-items:center; }
.reference-list-mapping { display:grid; width:100%; min-width:0; gap:8px; }
.parameter-row.reference-list-row { grid-template-columns:minmax(0, 1fr) minmax(0, 0.7fr) minmax(0, 1fr) auto; margin:0; }
.reference-list-row > * { min-width:0; }
.reference-mapping-add { justify-self:end; }
</style>
