<template>
  <div class="data-picker-field">
    <div class="picker-input">
      <el-input :model-value="label" readonly :placeholder="field.placeholder || '请选择数据'" :disabled="disabled || readonly"
        aria-label="打开数据选择面板" @click="open" @keydown.enter="open" @keydown.space.prevent="open">
        <template #append><el-button :disabled="disabled || readonly" @click="open">选择</el-button></template>
      </el-input>
      <el-button v-if="modelValue !== null && modelValue !== undefined && modelValue !== ''" text :disabled="disabled || readonly || state.confirming" @click="clear">清空</el-button>
    </div>
    <div v-if="state.resolving" class="picker-note" role="status">正在回显已选数据…</div>
    <div v-if="state.restoreError || (!state.open && state.error)" class="picker-error" role="alert">
      {{ state.restoreError || state.error }} <el-button link type="primary" @click="retry">重试回显</el-button>
    </div>
    <el-dialog :model-value="state.open" :title="config.pickerTitle || `选择${field.fieldLabel || field.fieldName || '数据'}`"
      width="min(1000px, 94vw)" append-to-body destroy-on-close :close-on-click-modal="false" @close="close">
      <el-form @submit.prevent="search">
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
import { ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { formFieldProps } from '@flow/workflow-core/extensions/contracts/form-field'
import { useDataPickerFieldRuntime } from '@flow/workflow-core/vue/useDataPickerFieldRuntime'

// 回填由一次 patch-form 进入宿主更新链，避免先写主值再等待其它字段覆盖确认。
defineOptions({ scriptEvents: [] })
const props = defineProps(formFieldProps)
const emit = defineEmits(['patch-form'])
const keyword = ref(''), draft = ref(null)
const { state, config, label, open, close, query, commit, clear, retry, validate } = useDataPickerFieldRuntime(props, emit, async () => {
  try { await ElMessageBox.confirm('将更新已有字段内容，是否继续？', '确认回填', { type: 'warning' }); return true }
  catch { return false }
})
watch(() => state.value.open, () => { keyword.value = ''; draft.value = null })
watch(() => state.value.rows, () => { draft.value = null })
function search() { if (!state.value.confirming) query({ pageNum: 1, keyword: keyword.value }) }
function changePage(pageNum) { if (!state.value.loading && !state.value.confirming) query({ pageNum }) }
function changePageSize(pageSize) { if (!state.value.loading && !state.value.confirming) query({ pageNum: 1, pageSize }) }
defineExpose({ validate })
</script>
<style scoped>
.picker-input { display: flex; align-items: center; width: 100%; }
.picker-note { color: var(--el-text-color-secondary); font-size: 12px; }
.picker-error { color: var(--el-color-danger); font-size: 12px; }
.picker-alert, .picker-pagination { margin-top: 16px; }
</style>
