<template>
  <FormInputParameterEditor v-model="schema" />
  <section class="parameter-usage">
    <div class="parameter-heading"><strong>参数用途</strong><el-button :disabled="!parameters.length" @click="add">增加用途</el-button></div>
    <p>{{ kind === 'LIST' ? '可作为列表附加查询条件；关系范围和权限仍然生效。' : '可初始化可编辑字段，只填空值、不覆盖已有数据。' }} 参数也可供数据源、接口和事件通过 params 使用。</p>
    <div v-for="(row, index) in bindings" :key="index" class="usage-row">
      <el-select :model-value="row.parameter" placeholder="输入参数" @update:model-value="patch(index, { parameter: $event })"><el-option v-for="p in parameters" :key="p.code" :value="p.code" :label="`${p.name} (${p.code})`" /></el-select>
      <span>{{ kind === 'LIST' ? '筛选字段' : '初始化字段' }}</span>
      <el-select :model-value="row.targetField" filterable placeholder="目标字段" @update:model-value="patch(index, { targetField: $event })"><el-option v-for="f in usableFields" :key="f.fieldCode" :value="f.fieldCode" :label="f.fieldLabel || f.fieldName || f.fieldCode" /></el-select>
      <el-select v-if="kind === 'LIST'" :model-value="row.operator || 'EQ'" @update:model-value="patch(index, { operator: $event })"><el-option label="等于" value="EQ" /><el-option label="不等于" value="NE" /><el-option label="包含" value="LIKE" /><el-option label="包含于" value="IN" /></el-select>
      <el-button link type="danger" @click="bindings = bindings.filter((_, i) => i !== index)">移除</el-button>
    </div>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { pageParameterFields } from '@/shared/page-parameters'
import FormInputParameterEditor from '@/components/form-designer/FormInputParameterEditor.vue'
import { getInputParameterDefinitions } from '@/shared/subform-parameter-contract'
const props = defineProps({ kind: { type: String, default: 'FORM' }, fields: { type: Array, default: () => [] } })
const config = defineModel({ type: Object, default: () => ({}) })
const schema = computed({ get: () => config.value.inputParameterSchema || {}, set: value => { config.value = { ...config.value, inputParameterSchema: value } } })
const bindings = computed({ get: () => config.value.inputParameterBindings || [], set: value => { config.value = { ...config.value, inputParameterBindings: value } } })
const parameters = computed(() => getInputParameterDefinitions(schema.value))
const usableFields = computed(() => pageParameterFields(props.fields).filter(f => f.fieldCode && (props.kind === 'LIST' ? f.isQuery : f.fieldCode !== 'id' && !f.isReadonly && !f.readonly && !f.isSystem)))
function patch(index, value) { bindings.value = bindings.value.map((row, i) => i === index ? { ...row, ...value } : row) }
function add() { bindings.value = [...bindings.value, { parameter: parameters.value[0]?.code || '', usage: props.kind === 'LIST' ? 'FILTER' : 'INITIALIZE', targetField: '', ...(props.kind === 'LIST' ? { operator: 'EQ' } : {}) }] }
</script>
<style scoped>
.parameter-usage { margin-top:24px; }.parameter-heading { display:flex; justify-content:space-between; align-items:center; }
p { color:var(--el-text-color-secondary); font-size:12px; }.usage-row { display:flex; gap:12px; align-items:center; margin:12px 0; }.el-select { flex:1; }
</style>
