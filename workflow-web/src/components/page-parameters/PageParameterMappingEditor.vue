<template>
  <section class="page-parameter-mapping">
    <div class="parameter-heading"><strong>参数传递</strong><el-button size="small" :disabled="!parameters.length" @click="add">增加映射</el-button></div>
    <p>打开时传递当前表单最新值或列表当前行，由目标页面决定如何使用。传参不会修改实体关系或开放编辑权限。</p>
    <el-alert v-if="!parameters.length" title="目标页面尚未声明输入参数，请先在目标页面的“输入参数”中配置并发布。" type="info" :closable="false" />
    <div v-for="(row, index) in modelValue" :key="index" class="parameter-row">
      <el-select :model-value="row.parameter" placeholder="目标参数" @update:model-value="patch(index, { parameter: $event })">
        <el-option v-for="item in parameters" :key="item.code" :label="`${item.name} (${item.code})`" :value="item.code" />
      </el-select>
      <el-select :model-value="row.sourceType" @update:model-value="patch(index, { sourceType: $event, sourceField: '', value: '' })">
        <el-option label="当前数据字段" value="FIELD" /><el-option label="当前记录 ID" value="RECORD_ID" /><el-option label="当前页面参数" value="PARAMETER" /><el-option label="固定值" value="LITERAL" />
      </el-select>
      <el-select v-if="row.sourceType === 'FIELD'" :model-value="row.sourceField" filterable placeholder="来源字段" @update:model-value="patch(index, { sourceField: $event })">
        <el-option v-for="field in sourceFields" :key="field.fieldCode" :value="field.fieldCode" :label="`${field.fieldLabel || field.fieldName || field.fieldCode} (${field.fieldCode})`" />
      </el-select>
      <el-input v-else-if="row.sourceType === 'PARAMETER'" :model-value="row.sourceField" placeholder="来源参数编码" @update:model-value="patch(index, { sourceField: $event })" />
      <el-input v-else-if="row.sourceType === 'LITERAL'" :model-value="literalText(row.value)" placeholder="固定值（对象/数组填写 JSON）" @update:model-value="patch(index, { value: $event })" />
      <span v-else>当前记录主键</span>
      <el-button link type="danger" @click="$emit('update:modelValue', modelValue.filter((_, i) => i !== index))">移除</el-button>
    </div>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { getInputParameterDefinitions } from '@flow/workflow-core/subform-parameter-contract'
const props = defineProps({ modelValue: { type: Array, default: () => [] }, schema: { type: Object, default: () => ({}) }, sourceFields: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue'])
const parameters = computed(() => getInputParameterDefinitions(props.schema))
const literalText = value => typeof value === 'object' ? JSON.stringify(value) : value
function patch(index, value) { emit('update:modelValue', props.modelValue.map((row, i) => i === index ? { ...row, ...value } : row)) }
function add() { emit('update:modelValue', [...props.modelValue, { parameter: parameters.value.find(p => !props.modelValue.some(row => row.parameter === p.code))?.code || '', sourceType: 'FIELD', sourceField: '' }]) }
</script>
<style scoped>
.parameter-heading { display:flex; justify-content:space-between; align-items:center; }
p { color:var(--el-text-color-secondary); font-size:12px; line-height:1.6; }
.parameter-row { display:grid; grid-template-columns:1fr 150px 1fr auto; gap:10px; margin:12px 0; align-items:center; }
</style>
