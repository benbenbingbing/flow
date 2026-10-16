<template>
  <section class="embed-list" aria-labelledby="embed-list-title">
    <header v-if="bootstrap.ui.showToolbar" class="embed-list__header">
      <div>
        <h1 id="embed-list-title">{{ bootstrap.view.name }}</h1>
        <p>{{ schema.entity.name }}</p>
      </div>
      <div class="embed-list__header-actions">
        <button
          v-if="createAction"
          type="button"
          :disabled="loading || createAction.enabled !== true"
          :title="createAction.disabledReason || ''"
          @click="$emit('open-create')"
        >
          {{ createAction.label }}
        </button>
        <span class="embed-list__actor" aria-label="当前用户">
          {{ bootstrap.actor.displayName }}
        </span>
      </div>
    </header>

    <form
      v-if="bootstrap.ui.showSearch && schema.list.filters.length"
      class="embed-list__search"
      aria-label="列表筛选"
      @submit.prevent="submitSearch"
    >
      <div
        v-for="field in schema.list.filters"
        :key="field.code"
        class="embed-list__filter"
      >
        <label :for="`embed-filter-${field.code}`">{{ field.label }}</label>

        <div v-if="field.operator === 'BETWEEN'" class="embed-list__range">
          <input
            :id="`embed-filter-${field.code}`"
            v-model="queryValues[field.code][0]"
            :type="inputType(field)"
            :disabled="loading"
            :aria-label="`${field.label}起始值`"
          >
          <span aria-hidden="true">至</span>
          <input
            v-model="queryValues[field.code][1]"
            :type="inputType(field)"
            :disabled="loading"
            :aria-label="`${field.label}结束值`"
          >
        </div>

        <select
          v-else-if="field.type === 'SELECT' || field.type === 'MULTI_SELECT'"
          :id="`embed-filter-${field.code}`"
          v-model="queryValues[field.code]"
          :multiple="field.type === 'MULTI_SELECT' || field.operator === 'IN'"
          :disabled="loading"
        >
          <option v-if="field.type !== 'MULTI_SELECT' && field.operator !== 'IN'" value="">
            全部
          </option>
          <option v-for="option in field.options" :key="optionKey(option)" :value="option.value">
            {{ option.label }}
          </option>
        </select>

        <select
          v-else-if="field.type === 'BOOLEAN'"
          :id="`embed-filter-${field.code}`"
          v-model="queryValues[field.code]"
          :multiple="field.operator === 'IN'"
          :disabled="loading"
        >
          <option v-if="field.operator !== 'IN'" value="">全部</option>
          <option :value="true">是</option>
          <option :value="false">否</option>
        </select>

        <input
          v-else
          :id="`embed-filter-${field.code}`"
          v-model="queryValues[field.code]"
          :type="inputType(field)"
          :disabled="loading"
          autocomplete="off"
        >
      </div>

      <div class="embed-list__search-actions">
        <button type="submit" :disabled="loading">查询</button>
        <button type="button" class="secondary" :disabled="loading" @click="resetSearch">
          重置
        </button>
      </div>
    </form>

    <div v-if="error" class="embed-list__inline-error" role="alert">
      <span>{{ error.message }}</span>
      <button v-if="error.recoverable" type="button" @click="$emit('retry')">重试</button>
    </div>

    <div class="embed-list__table-wrap" :aria-busy="loading">
      <table>
        <caption class="sr-only">{{ bootstrap.view.name }}</caption>
        <thead>
          <tr>
            <th v-if="selectionMode !== 'NONE'" class="embed-list__selection-column">
              <span class="sr-only">选择</span>
            </th>
            <th
              v-for="column in schema.list.columns"
              :key="column.code"
              scope="col"
              :class="columnWidthClass(column)"
            >
              {{ column.label }}
            </th>
            <th v-if="viewAction" class="embed-list__action-column" scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading && !page.items.length">
            <td
              :colspan="schema.list.columns.length + selectionColumnCount + actionColumnCount"
              class="embed-list__empty"
            >
              正在加载…
            </td>
          </tr>
          <tr v-else-if="!page.items.length">
            <td
              :colspan="schema.list.columns.length + selectionColumnCount + actionColumnCount"
              class="embed-list__empty"
            >
              暂无数据
            </td>
          </tr>
          <template v-else>
            <tr v-for="record in page.items" :key="record.id">
              <td v-if="selectionMode !== 'NONE'" class="embed-list__selection-column">
                <input
                  :type="selectionMode === 'SINGLE' ? 'radio' : 'checkbox'"
                  name="embed-record-selection"
                  :checked="selectedIds.has(record.id)"
                  :aria-label="`选择记录 ${record.id}`"
                  @change="toggleSelection(record, $event.target.checked)"
                >
              </td>
              <td v-for="column in schema.list.columns" :key="column.code">
                {{ formatCell(record.values[column.code], column) }}
              </td>
              <td v-if="viewAction" class="embed-list__action-column">
                <button
                  v-if="viewCapability(record)?.visible === true"
                  type="button"
                  class="secondary"
                  :disabled="loading || viewAction.enabled !== true
                    || viewCapability(record)?.enabled !== true"
                  :title="viewCapability(record)?.reason || viewAction.disabledReason || ''"
                  @click="$emit('open-view', record.id)"
                >
                  {{ viewAction.label }}
                </button>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <div v-if="loading && page.items.length" class="embed-list__loading-mask" role="status">
        正在刷新…
      </div>
    </div>

    <footer v-if="bootstrap.ui.showPagination" class="embed-list__pagination">
      <span>
        第 {{ page.pageNum }} 页
        <template v-if="typeof page.total === 'number'">，共 {{ page.total }} 条</template>
      </span>
      <div>
        <button
          type="button"
          class="secondary"
          :disabled="loading || page.pageNum <= 1"
          @click="changePage(page.pageNum - 1)"
        >
          上一页
        </button>
        <button
          type="button"
          class="secondary"
          :disabled="loading || !page.hasMore"
          @click="changePage(page.pageNum + 1)"
        >
          下一页
        </button>
      </div>
    </footer>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'

const props = defineProps({
  bootstrap: { type: Object, required: true },
  schema: { type: Object, required: true },
  page: { type: Object, required: true },
  queryValues: { type: Object, default: () => ({}) },
  selectedRecordIds: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: Object, default: null }
})

const emit = defineEmits([
  'query',
  'selection-change',
  'retry',
  'open-create',
  'open-view'
])
const queryValues = reactive({})
const selectedIds = ref(new Set())

const selectionMode = computed(() => {
  if (!props.bootstrap.capabilities.includes('SELECTION_RETURN')) return 'NONE'
  return props.schema.list.selection.mode
})
const selectionColumnCount = computed(() => selectionMode.value === 'NONE' ? 0 : 1)

function localFormAction(key, capability, placement, recordMode) {
  if (!props.bootstrap.capabilities.includes(capability)) return null
  return props.schema.actions.find(action => action.key === key
    && action.placement === placement
    && action.kind === 'NAVIGATION'
    && action.transport === 'LOCAL_FORM'
    && action.recordMode === recordMode
    && action.selectionMode === 'NONE'
    && action.requiresRecordVersion !== true
    && action.idempotencyRequired !== true) || null
}

const createAction = computed(() => localFormAction(
  'create', 'RECORD_CREATE', 'TOOLBAR', 'NONE'
))
const viewAction = computed(() => localFormAction(
  'view', 'RECORD_VIEW', 'ROW', 'CURRENT'
))
const actionColumnCount = computed(() => viewAction.value ? 1 : 0)

function emptyFilterValue(field) {
  if (field.operator === 'BETWEEN') return ['', '']
  if (field.operator === 'IN' || field.type === 'MULTI_SELECT') return []
  return ''
}

function resetFilterModel() {
  for (const key of Object.keys(queryValues)) delete queryValues[key]
  for (const field of props.schema.list.filters) {
    const restored = props.queryValues[field.code]
    queryValues[field.code] = restored === undefined
      ? emptyFilterValue(field)
      : Array.isArray(restored) ? [...restored] : restored
  }
}

watch([() => props.schema, () => props.queryValues], resetFilterModel, { immediate: true })
watch(() => props.selectedRecordIds, recordIds => {
  selectedIds.value = new Set(recordIds.map(value => String(value)))
}, { immediate: true })
watch(() => props.page.pageNum, () => {
  selectedIds.value = new Set()
  emit('selection-change', [])
})

function inputType(field) {
  if (field.type === 'NUMBER') return 'number'
  if (field.type === 'DATE') return 'date'
  if (field.type === 'DATETIME') return 'datetime-local'
  if (field.type === 'TIME') return 'time'
  return 'text'
}

function optionKey(option) {
  return `${typeof option.value}:${String(option.value)}`
}

function viewCapability(record) {
  return record.actions?.view || null
}

/**
 * 动态 Entry 的 CSP 不允许 inline style，因此把已投影列宽量化为固定 CSS 档位。
 * 服务端宽度只影响展示密度，不能成为浏览器可注入的样式值。
 */
function columnWidthClass(column) {
  const width = Number(column?.width)
  if (!Number.isFinite(width) || width <= 0) return undefined
  if (width <= 120) return 'embed-list__column--compact'
  if (width <= 240) return 'embed-list__column--regular'
  return 'embed-list__column--wide'
}

function formatCell(value, column) {
  if (value === null || value === undefined || value === '') return '—'
  if (column.type === 'BOOLEAN') return value ? '是' : '否'
  if (column.type === 'SELECT') {
    return column.options.find(option => option.value === value)?.label ?? String(value)
  }
  if (column.type === 'MULTI_SELECT') {
    if (!Array.isArray(value)) return '—'
    return value.map(item => (
      column.options.find(option => option.value === item)?.label ?? String(item)
    )).join('、')
  }
  return String(value)
}

function submitSearch() {
  emit('query', {
    queryValues: { ...queryValues },
    pageNum: 1,
    pageSize: props.page.pageSize
  })
}

function resetSearch() {
  resetFilterModel()
  submitSearch()
}

function changePage(pageNum) {
  emit('query', {
    queryValues: { ...queryValues },
    pageNum,
    pageSize: props.page.pageSize
  })
}

function toggleSelection(record, checked) {
  const next = new Set(selectionMode.value === 'SINGLE' ? [] : selectedIds.value)
  if (checked && next.size < props.bootstrap.limits.maxSelectionSize) next.add(record.id)
  else next.delete(record.id)
  selectedIds.value = next
  emit(
    'selection-change',
    props.page.items.filter(item => next.has(item.id))
  )
}
</script>

<style scoped>
.embed-list {
  color: #182230;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: var(--embed-surface, #fff);
}

.embed-list__header,
.embed-list__pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.embed-list__header {
  padding: 20px 24px 16px;
  border-bottom: 1px solid #eaecf0;
}

.embed-list__header h1 {
  margin: 0;
  font-size: 20px;
}

.embed-list__header p,
.embed-list__actor {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
}

.embed-list__header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.embed-list__search {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 14px;
  padding: 16px 24px;
  border-bottom: 1px solid #eaecf0;
  background: #f9fafb;
}

.embed-list__filter {
  display: grid;
  min-width: 180px;
  gap: 6px;
}

.embed-list__filter label {
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.embed-list input,
.embed-list select,
.embed-list button {
  box-sizing: border-box;
  min-height: 36px;
  font: inherit;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
}

.embed-list input,
.embed-list select {
  min-width: 0;
  padding: 7px 10px;
  color: #101828;
  background: #fff;
}

.embed-list select[multiple] {
  min-height: 72px;
}

.embed-list input:focus-visible,
.embed-list select:focus-visible,
.embed-list button:focus-visible {
  outline: 3px solid #84adff;
  outline-offset: 1px;
}

.embed-list__range {
  display: flex;
  align-items: center;
  gap: 6px;
}

.embed-list__range input {
  width: 150px;
}

.embed-list__search-actions {
  display: flex;
  gap: 8px;
}

.embed-list button {
  padding: 7px 14px;
  color: #fff;
  border-color: #175cd3;
  background: #175cd3;
  cursor: pointer;
}

.embed-list button.secondary {
  color: #344054;
  border-color: #d0d5dd;
  background: #fff;
}

.embed-list button:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.embed-list__inline-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 24px;
  color: #912018;
  border-bottom: 1px solid #fecdca;
  background: #fef3f2;
}

.embed-list__inline-error button {
  min-height: 30px;
  padding: 4px 10px;
}

.embed-list__table-wrap {
  position: relative;
  overflow-x: auto;
}

.embed-list table {
  width: 100%;
  min-width: 560px;
  border-collapse: collapse;
  table-layout: fixed;
}

.embed-list th,
.embed-list td {
  overflow: hidden;
  padding: 12px 16px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-bottom: 1px solid #eaecf0;
}

.embed-list th {
  color: #475467;
  font-size: 12px;
  font-weight: 600;
  background: #f9fafb;
}

.embed-list__column--compact {
  width: 96px;
}

.embed-list__column--regular {
  width: 180px;
}

.embed-list__column--wide {
  width: 320px;
}

.embed-list td {
  font-size: 14px;
}

.embed-list__selection-column {
  width: 48px;
  text-align: center !important;
}

.embed-list__action-column {
  width: 104px;
  text-align: right !important;
}

.embed-list__selection-column input {
  width: 16px;
  min-height: 16px;
}

.embed-list__empty {
  padding: 48px 16px !important;
  color: #667085;
  text-align: center !important;
}

.embed-list__loading-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #344054;
  background: rgb(255 255 255 / 72%);
}

.embed-list__pagination {
  padding: 14px 24px;
  color: #475467;
  font-size: 13px;
}

.embed-list__pagination div {
  display: flex;
  gap: 8px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 640px) {
  .embed-list__header,
  .embed-list__pagination {
    align-items: flex-start;
    flex-direction: column;
  }

  .embed-list__filter,
  .embed-list__search-actions {
    width: 100%;
  }

  .embed-list__filter > input,
  .embed-list__filter > select {
    width: 100%;
  }
}
</style>
