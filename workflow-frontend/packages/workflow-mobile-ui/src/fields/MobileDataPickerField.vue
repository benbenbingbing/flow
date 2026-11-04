<template>
  <div class="mobile-data-picker">
    <VanField :label="field.fieldLabel || field.fieldName" :model-value="label" :placeholder="field.placeholder || '请选择数据'"
      readonly :is-link="!disabled && !readonly" :clickable="!disabled && !readonly" :required="required" :label-align="labelPosition" :border="false" @click="open" />
    <button v-if="modelValue !== null && modelValue !== undefined && modelValue !== '' && !disabled && !readonly" class="picker-clear" :disabled="state.confirming" @click="clear">清空选择</button>
    <div v-if="state.resolving" class="picker-note" role="status">正在回显已选数据…</div>
    <div v-if="state.restoreError || (!state.open && state.error)" class="picker-error" role="alert">{{ state.restoreError || state.error }} <button @click="retry">重试回显</button></div>
    <VanPopup :show="state.open" position="bottom" round safe-area-inset-bottom :close-on-click-overlay="false" @update:show="value => !value && close()">
      <VanNavBar :title="config.pickerTitle || '选择数据'" left-text="取消" @click-left="close" />
      <div class="picker-search">
        <VanSearch v-model="keyword" placeholder="输入关键词搜索" :disabled="state.confirming" @search="search" />
        <button :disabled="state.confirming" @click="search">搜索</button>
      </div>
      <div v-if="state.loading || state.confirming" class="picker-note" role="status">{{ state.confirming ? '正在确认选择…' : '正在加载…' }}</div>
      <div v-if="state.error" class="picker-error" role="alert">{{ state.error }}</div>
      <div class="picker-records">
        <button v-for="row in state.rows" :key="row.value" class="picker-record" :disabled="row.disabled || state.loading || state.confirming" @click="commit(row)">
          <strong>{{ row.label }}{{ row.disabled ? '（不可选）' : '' }}</strong>
          <span v-for="column in state.schema?.columns || []" :key="column.field">{{ column.label }}：{{ row.data[column.field] ?? '—' }}</span>
        </button>
        <VanEmpty v-if="!state.loading && !state.rows?.length" description="暂无可选数据" />
      </div>
      <div class="picker-pagination">
        <button :disabled="state.pageNum <= 1 || state.loading || state.confirming" @click="query({ pageNum: state.pageNum - 1 })">上一页</button>
        <span>第 {{ state.pageNum }} 页 · 共 {{ state.total }} 条</span>
        <button :disabled="state.pageNum * state.pageSize >= state.total || state.loading || state.confirming" @click="query({ pageNum: state.pageNum + 1 })">下一页</button>
      </div>
    </VanPopup>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { Field as VanField, Popup as VanPopup, NavBar as VanNavBar, Search as VanSearch, Empty as VanEmpty, showConfirmDialog } from 'vant'
import { formFieldProps } from '@flow/workflow-core/extensions/contracts/form-field'
import { useDataPickerFieldRuntime } from '@flow/workflow-core/vue/useDataPickerFieldRuntime'
import { resolveMobileLabelPosition } from './mobileFieldLayout.js'
const props = defineProps({ ...formFieldProps, required: Boolean })
const emit = defineEmits(['patch-form'])
const keyword = ref('')
const labelPosition = computed(() => resolveMobileLabelPosition(props.context.form))
const { state, config, label, open, close, query, commit, clear, retry, validate } = useDataPickerFieldRuntime(props, emit, async () => {
  try { await showConfirmDialog({ title: '确认回填', message: '将更新已有字段内容，是否继续？' }); return true }
  catch { return false }
})
function search() {
  if (!state.value.confirming) query({ pageNum: 1, keyword: keyword.value })
}
watch(() => state.value.open, () => { keyword.value = '' })
defineExpose({ validate })
</script>
<style scoped>
.mobile-data-picker { border-bottom: 1px solid var(--flow-mobile-border); }
button { border: 0; background: transparent; color: var(--flow-mobile-accent-text); min-height: 44px; padding: 8px 16px; }
button:disabled { color: var(--flow-mobile-muted); }
.picker-note, .picker-error { padding: 8px 16px; font-size: 13px; }
.picker-note { color: var(--flow-mobile-muted); }.picker-error { color: #a94833; }
.picker-search { display: flex; align-items: center; }.picker-search :deep(.van-search) { flex: 1; min-width: 0; }
.picker-records { overflow: auto; max-height: 55vh; }
.picker-record { display: flex; flex-direction: column; text-align: left; width: 100%; gap: 6px; border-bottom: 1px solid var(--flow-mobile-border); }
.picker-record span { font-size: 13px; color: var(--flow-mobile-muted); }
.picker-pagination { display: flex; align-items: center; justify-content: space-between; font-size: 13px; }
</style>
