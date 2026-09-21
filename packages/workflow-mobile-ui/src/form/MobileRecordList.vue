<template>
  <div class="record-list">
    <p v-if="error" class="record-error" role="alert">{{ error }}<VanButton size="small" @click="load(true)">重试</VanButton></p>
    <VanLoading v-if="loading && !rows.length" size="20" />
    <VanEmpty v-if="!loading && !error && !rows.length" description="暂无关联记录" :image-size="48" />
    <VanCollapse v-model="expanded"><VanCollapseItem v-for="(row, index) in rows" :key="row.id || index" :name="row.id || index" :title="row.name || row.dataName || row.data?.name || row.code || `记录 ${index + 1}`">
      <MobileReadonlyField v-for="column in visibleColumns" :key="column.fieldCode || column.prop" :field="columnField(column)" :model-value="columnValue(row, column)" :options="column.options" :services="services" />
    </VanCollapseItem></VanCollapse>
    <VanButton v-if="rows.length && !finished" block plain size="small" :loading="loading" @click="load(false)">加载更多</VanButton>
  </div>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { Collapse as VanCollapse, CollapseItem as VanCollapseItem, Button as VanButton, Loading as VanLoading, Empty as VanEmpty } from 'vant'
import MobileReadonlyField from '../fields/MobileReadonlyField.vue'
const props = defineProps({ identity: { type: String, required: true }, loadPage: { type: Function, required: true }, services: Object })
const rows = ref([]), columns = ref([]), loading = ref(false), error = ref(''), finished = ref(false), expanded = ref([])
let revision = 0, page = 0
const visibleColumns = computed(() => columns.value.filter(column => column.visible !== false && column.isVisible !== false && (column.fieldCode || column.prop)))
const columnField = column => ({ ...column, fieldCode: column.fieldCode || column.prop, fieldName: column.fieldName || column.columnName || column.label || column.title || column.fieldCode, componentType: column.componentType })
function columnValue(row, column) { const key = column.fieldCode || column.prop; return Object.hasOwn(row, key) ? row[key] : row.data?.[key] }
/** 仅展示服务端发布列；不枚举任意返回属性，避免把隐含数据暴露为字段。 */
async function load(reset = false) {
  if (!reset && (loading.value || finished.value)) return
  const current = reset ? ++revision : revision, next = reset ? 1 : page + 1
  loading.value = true; error.value = ''
  if (reset) { rows.value = []; columns.value = []; expanded.value = [] }
  try {
    const result = await props.loadPage({ pageNum: next, pageSize: 20 })
    if (current !== revision) return
    const records = result.records || result.list || []
    columns.value = result.columns || columns.value
    rows.value = reset ? records : [...new Map([...rows.value, ...records].map(row => [String(row.id), row])).values()]
    page = next; finished.value = records.length < 20 || rows.value.length >= Number(result.total ?? Infinity)
  } catch (cause) { if (current === revision) error.value = cause.message || '关联记录加载失败' }
  finally { if (current === revision) loading.value = false }
}
watch(() => props.identity, () => load(true), { immediate: true })
onBeforeUnmount(() => revision++)
defineExpose({ refresh: () => load(true) })
</script>
<style scoped>.record-list { margin: 8px 0; }.record-error { color: #bc4738; font-size: 12px; }.record-error button { margin-left: 8px; }</style>
