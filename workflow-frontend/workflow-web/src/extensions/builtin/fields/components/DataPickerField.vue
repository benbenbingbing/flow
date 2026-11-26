<template>
  <div class="data-picker-field">
    <div v-if="nativeComponent" class="picker-value-input">
      <el-input v-if="exactNumber" :model-value="numberText" :placeholder="field.placeholder"
        :inputmode="editorField.fieldType === 'LONG' ? 'numeric' : 'decimal'" :disabled="disabled || readonly" clearable
        @update:model-value="editNumber" @change="$emit('change', modelValue)" @blur="$emit('blur', $event)" @focus="$emit('focus', $event)" />
      <component v-else :is="editors[nativeComponent]" :field="editorField" :model-value="modelValue" :disabled="disabled || readonly"
        @update:model-value="emit('update:modelValue', $event)" @change="emit('change', $event)" @blur="emit('blur', $event)" @focus="emit('focus', $event)" />
      <el-button v-if="!disabled && !readonly" @click="open">选择</el-button>
    </div>
    <div v-else class="picker-input">
      <el-input :model-value="label" readonly :placeholder="field.placeholder || '请选择数据'" :disabled="disabled || readonly"
        aria-label="打开数据选择面板" @click="open" @keydown.enter="open" @keydown.space.prevent="open">
        <template #append><el-button :disabled="disabled || readonly" @click="open">选择</el-button></template>
      </el-input>
      <el-button v-if="modelValue !== null && modelValue !== undefined && modelValue !== ''" text :disabled="disabled || readonly || state.confirming" @click="clear">清空</el-button>
    </div>
    <div v-if="state.resolving" class="picker-note" role="status">正在回显已选数据…</div>
    <div v-if="exactNumber && numberError" class="picker-error" role="alert">{{ numberError }}</div>
    <div v-if="!nativeComponent && (state.restoreError || (!state.open && state.error))" class="picker-error" role="alert">
      {{ state.restoreError || state.error }} <el-button link type="primary" @click="retry">重试回显</el-button>
    </div>
    <el-dialog :model-value="state.open" :title="config.pickerTitle || `选择${field.fieldLabel || field.fieldName || '数据'}`"
      width="min(1000px, 94vw)" append-to-body destroy-on-close :close-on-click-modal="false" @close="close">
      <form v-if="config.pickerSource === 'ENTITY_LIST' && state.list?.queryFields.length" class="picker-filters" @submit.prevent="searchList">
        <div v-for="item in state.list.queryFields" :key="item.fieldCode" class="picker-filter">
          <span>{{ item.fieldName }}</span>
          <el-checkbox v-if="isListNullQueryOperator(item.queryType)" v-model="listFilters[item.fieldCode]"
            :aria-label="item.fieldName + (item.queryType === 'NOT_EMPTY' ? ' 非空' : ' 为空')">
            {{ item.queryType === 'NOT_EMPTY' ? '非空' : '为空' }}
          </el-checkbox>
          <template v-else-if="item.queryType === 'BETWEEN'">
            <el-input v-model="listFilters[item.fieldCode + '_start']" :aria-label="item.fieldName + ' 起始值'" placeholder="起始值" clearable />
            <el-input v-model="listFilters[item.fieldCode + '_end']" :aria-label="item.fieldName + ' 结束值'" placeholder="结束值" clearable />
          </template>
          <el-input v-else v-model="listFilters[item.fieldCode]" :aria-label="item.fieldName" :placeholder="item.placeholder" clearable />
        </div>
        <el-button native-type="submit" :disabled="state.confirming" type="primary">搜索</el-button>
      </form>
      <el-form v-else-if="config.pickerSource !== 'ENTITY_LIST'" @submit.prevent="search">
        <el-input v-model="keyword" placeholder="输入关键词搜索" clearable :disabled="state.confirming" @keyup.enter="search">
          <template #append><el-button :disabled="state.confirming" @click="search">搜索</el-button></template>
        </el-input>
      </el-form>
      <el-alert v-if="state.error" class="picker-alert" :title="state.error" type="error" :closable="false" show-icon />
      <el-table v-loading="state.loading || state.confirming" :data="state.rows" row-key="value" max-height="420" highlight-current-row
        @current-change="row => draft = row" @row-dblclick="row => commit(row)">
        <el-table-column v-for="column in state.schema?.columns || []" :key="column.field" :label="column.label" :width="column.width" :min-width="column.minWidth">
          <template #default="{ row }">{{ row.data[column.field] }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button link type="primary" :disabled="row.disabled || state.loading || state.confirming" @click="commit(row)">{{ row.disabled ? '不可选' : '选择' }}</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination class="picker-pagination" :current-page="state.pageNum" :page-size="state.pageSize" :total="state.total" :page-sizes="[10, 20, 50]"
        :disabled="state.loading || state.confirming" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" />
      <template #footer>
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :disabled="!draft || draft.disabled || state.loading || state.confirming" @click="commit(draft)">确认选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import DOMPurify from 'dompurify'
import { formFieldProps } from '@flow/workflow-core/extensions/contracts/form-field'
import { useDataPickerFieldRuntime, usePickerNumericInput } from '@flow/workflow-core/vue/useDataPickerFieldRuntime'
import { buildListRequestFilters, isListNullQueryOperator, listQueryDefaults } from '@flow/workflow-core/list-query-filters'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
import DateField from './DateField.vue'
import NumberField from './NumberField.vue'
import RichTextField from './RichTextField.vue'
import SwitchField from './SwitchField.vue'
import TextField from './TextField.vue'

// 回填由一次 patch-form 进入宿主更新链，避免先写主值再等待其它字段覆盖确认。
defineOptions({ scriptEvents: [] })
const props = defineProps(formFieldProps)
const emit = defineEmits(['patch-form', 'update:modelValue', 'change', 'blur', 'focus'])
const editors = { input: TextField, textarea: TextField, number: NumberField, date: DateField,
  datetime: DateField, switch: SwitchField, rich_text: RichTextField }
const keyword = ref(''), draft = ref(null)
const listFilters = ref({})
const { state, config, nativeComponent, label, open, close, query, commit: commitPicker, clear, retry, validate: validatePicker } = useDataPickerFieldRuntime(props, emit, async () => {
  try { await ElMessageBox.confirm('将更新已有字段内容，是否继续？', '确认回填', { type: 'warning' }); return true }
  catch { return false }
}, () => entityListRuntimeApi, html => DOMPurify.sanitize(html))
const editorField = computed(() => ({ ...props.field, componentType: nativeComponent.value }))
// Element Plus 数字组件使用 Number，长整数和小数改用能保留原始精度的数字输入。
const exactNumber = computed(() => nativeComponent.value === 'number' && ['LONG', 'DECIMAL'].includes(String(props.field.fieldType).toUpperCase()))
const { value: numberText, error: numberError, input: editNumber, reset: resetNumber } = usePickerNumericInput(props, value => {
  if (!props.disabled && !props.readonly) emit('update:modelValue', value)
})
async function commit(row) { if (await commitPicker(row)) resetNumber() }
function validate() { return validatePicker() && (props.disabled || props.readonly || !exactNumber.value || !numberError.value) }
watch(() => state.value.open, () => { keyword.value = ''; draft.value = null })
watch(() => state.value.list, list => { listFilters.value = listQueryDefaults(list?.queryFields || []) })
watch(() => state.value.rows, () => { draft.value = null })
function search() { if (!state.value.confirming) query({ pageNum: 1, keyword: keyword.value }) }
function searchList() {
  if (!state.value.confirming) query({ pageNum: 1, filters: buildListRequestFilters(listFilters.value, state.value.list?.queryFields) })
}
function changePage(pageNum) { if (!state.value.loading && !state.value.confirming) query({ pageNum }) }
function changePageSize(pageSize) { if (!state.value.loading && !state.value.confirming) query({ pageNum: 1, pageSize }) }
defineExpose({ validate })
</script>
<style scoped>
.picker-input { display: flex; align-items: center; width: 100%; }
.picker-value-input { display: flex; align-items: flex-start; gap: 8px; }
.picker-value-input > :first-child { flex: 1; min-width: 0; }
.picker-note { color: var(--el-text-color-secondary); font-size: 12px; }
.picker-error { color: var(--el-color-danger); font-size: 12px; }
.picker-alert, .picker-pagination { margin-top: 16px; }
.picker-filters { display: flex; flex-wrap: wrap; gap: 8px 12px; align-items: end; margin-bottom: 12px; }
.picker-filter { display: flex; align-items: center; gap: 8px; }
.picker-filters .el-input { width: 128px; }
</style>
