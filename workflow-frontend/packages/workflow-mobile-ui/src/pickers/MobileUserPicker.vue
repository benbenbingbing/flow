<template>
  <VanPopup :show="show" position="bottom" round safe-area-inset-bottom :style="{ height: '85dvh' }" @update:show="$emit('update:show', $event)">
    <div class="picker-shell"><VanNavBar :title="title" left-text="取消" right-text="确定" @click-left="$emit('update:show', false)" @click-right="confirm" />
      <VanSearch v-model="keyword" placeholder="搜索" @search="search" @clear="search" />
      <p v-if="error" class="picker-error" role="alert">{{ error }}<VanButton size="mini" @click="load(true)">重试</VanButton></p>
      <div v-if="selected.length" class="picker-selected"><VanTag v-for="item in selected" :key="keyOf(item)" closeable size="medium" @close="toggle(item)">{{ labelOf(item) }}</VanTag></div>
      <div class="picker-results"><VanList :loading="loading" :finished="finished" :immediate-check="false" @load="load(false)"><VanCell v-for="item in rows" :key="keyOf(item)" clickable :title="labelOf(item)" :label="item.deptName || item.description" @click="toggle(item)"><template #right-icon><VanCheckbox :model-value="isSelected(item)" /></template></VanCell><VanEmpty v-if="!loading && !rows.length && !error" description="暂无可选项" /></VanList></div>
    </div>
  </VanPopup>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { Popup as VanPopup, NavBar as VanNavBar, Search as VanSearch, List as VanList, Cell as VanCell, Checkbox as VanCheckbox, Tag as VanTag, Empty as VanEmpty, Button as VanButton, showFailToast } from 'vant'
const props = defineProps({ show: Boolean, title: { type: String, default: '选择人员' }, modelValue: { type: Array, default: () => [] }, multiple: Boolean, max: { type: Number, default: 0 }, loadOptions: { type: Function, required: true }, identity: { type: String, default: '' }, valueKey: { type: String, default: 'id' } })
const emit = defineEmits(['update:show', 'confirm'])
const keyword = ref(''), selected = ref([]), rows = ref([]), loading = ref(false), error = ref(''), finished = ref(false)
let page = 0, generation = 0
const keyOf = item => String(item?.[props.valueKey] ?? item?.userKey ?? item?.username ?? item?.id ?? item)
const labelOf = item => item?.displayName || item?.nickname || item?.label || item?.name || item?.dataName || item?.username || keyOf(item)
const isSelected = item => selected.value.some(value => keyOf(value) === keyOf(item))
const unavailable = item => item?.disabled === true || item?.selectable === false || item?.available === false
function toggle(item) {
  if (isSelected(item)) selected.value = selected.value.filter(value => keyOf(value) !== keyOf(item))
  // 候选范围内仍可能有停用或不可选项；允许移除旧选择，但不能新增这些人员。
  else if (unavailable(item)) showFailToast(item.disabledReason || '此项当前不可选')
  else if (!props.multiple) selected.value = [item]
  else if (props.max && selected.value.length >= props.max) showFailToast(`最多选择 ${props.max} 项`)
  else selected.value = [...selected.value, item]
}
/** 查询身份、搜索词和分页变化后作废旧响应，下一审批人范围变化时不能复用旧选择。 */
async function load(reset = false) {
  if (!reset && (loading.value || finished.value)) return
  const version = reset ? ++generation : generation, nextPage = reset ? 1 : page + 1
  loading.value = true; error.value = ''
  try {
    const result = await props.loadOptions({ keyword: keyword.value, pageNum: nextPage, pageSize: 20 })
    if (version !== generation || !props.show) return
    const data = Array.isArray(result) ? result : result.list || result.records || result.rows || result.options || []
    rows.value = [...new Map((reset ? data : [...rows.value, ...data]).map(item => [keyOf(item), item])).values()]; page = nextPage
    finished.value = Array.isArray(result) || data.length < 20 || (result.total != null && rows.value.length >= Number(result.total))
  } catch (cause) { if (version === generation) error.value = cause.message || '无法加载候选项' }
  finally { if (version === generation) loading.value = false }
}
function search() { rows.value = []; load(true) }
watch(() => [props.show, props.identity], ([show], previous) => {
  generation++
  if (!show) return
  const identityChanged = previous && previous[1] !== props.identity
  selected.value = identityChanged ? [] : [...props.modelValue]; keyword.value = ''; rows.value = []; load(true)
}, { immediate: true })
onBeforeUnmount(() => { generation++ })
function confirm() { emit('confirm', selected.value); emit('update:show', false) }
</script>
<style scoped>
.picker-shell { height: 100%; display: flex; flex-direction: column; }.picker-results { flex: 1; min-height: 0; overflow-y: auto; }.picker-selected { padding: 8px 16px; display: flex; flex-wrap: wrap; gap: 8px; }.picker-error { padding: 12px 16px; color: #b44b35; font-size: 14px; }.picker-error button { margin-left: 12px; }
</style>
