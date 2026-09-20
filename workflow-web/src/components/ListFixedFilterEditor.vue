<template>
  <div class="fixed-filter-editor">
    <div class="fixed-filter-header">
      <span>同时满足以下条件 <span class="condition-count">（{{ modelValue.length }} 条）</span></span>
      <el-button type="primary" link :icon="Plus" @click="add">添加条件</el-button>
    </div>
    <div v-if="!modelValue.length" class="fixed-filter-empty">暂未设置固定条件，列表仍按数据权限查询。</div>
    <div v-for="(row, index) in modelValue" :key="index" class="fixed-filter-item">
      <div v-if="row.readOnly" class="fixed-filter-legacy">
        <div><strong>{{ fieldLabel(row.field) }}</strong> · 已有特殊条件，原样保留</div>
        <details><summary>查看原配置</summary><pre>{{ JSON.stringify(row.original, null, 2) }}</pre></details>
        <el-button type="danger" link :aria-label="`删除第 ${index + 1} 条条件`" @click="remove(index)">删除</el-button>
      </div>
      <div v-else class="fixed-filter-row">
        <el-select
          :model-value="row.field" filterable placeholder="选择字段" :aria-label="`第 ${index + 1} 条条件的字段`"
          @update:model-value="value => changeField(index, value)"
        >
          <el-option v-if="row.field && !fields.some(field => field.fieldCode === row.field)" :label="`${row.field}（已有字段）`" :value="row.field" />
          <el-option
            v-for="field in availableFields" :key="field.fieldCode"
            :label="`${field.fieldName || field.fieldCode}（${field.fieldCode}）`" :value="field.fieldCode"
            :disabled="modelValue.some((item, rowIndex) => rowIndex !== index && item.field === field.fieldCode)"
          />
        </el-select>
        <el-select :model-value="row.operator" :aria-label="`第 ${index + 1} 条条件的比较方式`" @update:model-value="value => changeOperator(index, value)">
          <el-option v-for="option in operators" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <div v-if="row.operator === 'BETWEEN'" class="fixed-filter-range">
          <ListFixedFilterValue :model-value="row.start" :field="fieldFor(row.field)" placeholder="起始值（含）" :label="`第 ${index + 1} 条条件的起始值`" @update:model-value="value => patch(index, { start: value })" />
          <span>至</span>
          <ListFixedFilterValue :model-value="row.end" :field="fieldFor(row.field)" placeholder="结束值（含）" :label="`第 ${index + 1} 条条件的结束值`" @update:model-value="value => patch(index, { end: value })" />
        </div>
        <span v-else-if="row.operator === 'IS_NULL'" class="fixed-filter-valueless">无需填写值</span>
        <ListFixedFilterValue
          v-else :key="`${row.field}:${row.operator}`" :model-value="row.value" :field="fieldFor(row.field)"
          :multiple="['IN', 'NOT_IN'].includes(row.operator)" :label="`第 ${index + 1} 条条件的值`"
          @update:model-value="value => patch(index, { value })"
        />
        <el-button type="danger" link :aria-label="`删除第 ${index + 1} 条条件`" @click="remove(index)">删除</el-button>
      </div>
    </div>
    <div v-if="validationError" class="fixed-filter-error" role="alert">{{ validationError }}</div>
    <div class="fixed-filter-note">保存并发布后生效，与数据范围取交集；用户筛选和自定义查询不能放宽这些条件。</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import ListFixedFilterValue from './ListFixedFilterValue.vue'
import { createFixedFilterRow, FIXED_FILTER_OPERATORS, writeFixedFilterRows } from '@/shared/list-fixed-filters'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  fields: { type: Array, default: () => [] },
  systemEntity: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])
const operators = computed(() => FIXED_FILTER_OPERATORS.filter(option => !props.systemEntity || option.value !== 'NOT_IN'))
// 查询协议把这些后缀解释成运算符/边界，不能作为独立字段新建条件。
const availableFields = computed(() => props.fields.filter(field => field.fieldCode && !/_(op|start|end)$/.test(field.fieldCode)))
const fieldFor = code => props.fields.find(field => field.fieldCode === code) || {}
const fieldLabel = code => fieldFor(code).fieldName || code
const validationError = computed(() => {
  try { writeFixedFilterRows(props.modelValue, { systemEntity: props.systemEntity, fields: props.fields }); return '' }
  catch (error) { return error.message }
})

function add() { emit('update:modelValue', [...props.modelValue, createFixedFilterRow()]) }
function remove(index) { emit('update:modelValue', props.modelValue.filter((_, i) => i !== index)) }
function patch(index, changes) {
  emit('update:modelValue', props.modelValue.map((row, i) => i === index ? { ...row, ...changes } : row))
}
/** 更换字段时清空旧字段的值和原文，防止把不同类型或旧范围带到新字段。 */
function changeField(index, field) {
  patch(index, { ...createFixedFilterRow(), field, original: undefined, baseline: undefined })
}
/** 运算符切换时保留可复用的首个值，范围和集合使用各自的编辑控件。 */
function changeOperator(index, operator) {
  const row = props.modelValue[index]
  const current = row.operator === 'BETWEEN' ? row.start : row.value
  const scalar = Array.isArray(current) ? current[0] ?? '' : current
  patch(index, {
    operator,
    value: ['IN', 'NOT_IN'].includes(operator)
      ? Array.isArray(row.value) ? row.value : scalar === '' || scalar == null ? [] : [scalar]
      : scalar,
    start: operator === 'BETWEEN' ? scalar : '',
    end: ''
  })
}
</script>

<style scoped>
.fixed-filter-editor { width: 100%; max-width: 1100px; min-width: 0; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 12px 16px; box-sizing: border-box; container-type: inline-size; }
.fixed-filter-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; font-weight: 500; }
.condition-count, .fixed-filter-note, .fixed-filter-valueless { color: var(--el-text-color-secondary); font-size: 12px; font-weight: normal; }
.fixed-filter-empty { padding: 14px; background: var(--el-fill-color-light); color: var(--el-text-color-secondary); border-radius: 4px; text-align: center; }
.fixed-filter-item + .fixed-filter-item { margin-top: 10px; }
.fixed-filter-row { display: grid; grid-template-columns: minmax(140px, 1fr) 140px minmax(180px, 1.5fr) 40px; gap: 10px; align-items: center; }
.fixed-filter-row > *, .fixed-filter-range > * { min-width: 0; }
.fixed-filter-row :deep(.el-select), .fixed-filter-row :deep(.el-input-number), .fixed-filter-row :deep(.el-date-editor) { width: 100%; }
.fixed-filter-range { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); gap: 8px; align-items: center; }
.fixed-filter-note { margin-top: 12px; line-height: 1.6; }
.fixed-filter-error { color: var(--el-color-danger); font-size: 12px; margin-top: 10px; line-height: 1.6; }
.fixed-filter-legacy { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; padding: 10px; border-radius: 4px; background: var(--el-fill-color-light); font-size: 12px; }
.fixed-filter-legacy details { grid-column: 1; }
.fixed-filter-legacy summary { cursor: pointer; color: var(--el-color-primary); }
.fixed-filter-legacy pre { white-space: pre-wrap; overflow-wrap: anywhere; }
.fixed-filter-legacy > .el-button { grid-column: 2; grid-row: 1; }
@container (max-width: 720px) {
  .fixed-filter-row { grid-template-columns: minmax(0, 1fr) 140px 40px; }
  .fixed-filter-row > :nth-child(3) { grid-column: 1 / 3; grid-row: 2; }
  .fixed-filter-row > .el-button { grid-column: 3; grid-row: 1; }
}
@container (max-width: 400px) {
  .fixed-filter-row { grid-template-columns: minmax(0, 1fr) 40px; }
  .fixed-filter-row > :nth-child(2) { grid-column: 1; grid-row: 2; }
  .fixed-filter-row > :nth-child(3) { grid-column: 1 / -1; grid-row: 3; }
  .fixed-filter-row > .el-button { grid-column: 2; }
  .fixed-filter-range { grid-template-columns: minmax(0, 1fr); }
}
</style>
