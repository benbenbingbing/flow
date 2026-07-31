<template>
  <div class="entity-definition-picker" :class="{ 'is-disabled': disabled }">
    <button
      type="button"
      class="picker-trigger"
      :disabled="disabled"
      @click="openPicker"
    >
      <span v-if="selectedItems.length" class="trigger-content">
        <span class="trigger-primary">{{ triggerPrimaryText }}</span>
        <span class="trigger-secondary">{{ triggerSecondaryText }}</span>
      </span>
      <span v-else class="trigger-placeholder">{{ placeholder }}</span>
      <span class="trigger-actions">
        <el-icon
          v-if="clearable && selectedItems.length"
          class="clear-icon"
          title="清空"
          @click.stop="clearCommittedSelection"
        >
          <CircleClose />
        </el-icon>
        <el-icon><Search /></el-icon>
      </span>
    </button>

    <el-dialog
      v-model="visible"
      :title="title"
      :width="dialogWidth"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      class="entity-definition-picker-dialog"
      @closed="resetTransientState"
    >
      <template #header>
        <div class="dialog-heading">
          <div>
            <strong>{{ title }}</strong>
            <span>{{ multiple ? '支持搜索、分页和跨页多选' : '支持搜索、分页和单选' }}</span>
          </div>
          <el-tag type="primary" effect="plain">已选 {{ draftItems.length }} 个</el-tag>
        </div>
      </template>

      <div class="picker-layout">
        <section class="option-section">
          <div class="search-toolbar">
            <el-input
              v-model="keyword"
              clearable
              placeholder="搜索实体名称或编码"
              @keyup.enter="handleSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select
              v-if="!query.status"
              v-model="status"
              clearable
              placeholder="全部状态"
            >
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="已停用" value="DISABLED" />
            </el-select>
            <el-select
              v-if="!query.lifecycleMode"
              v-model="lifecycleMode"
              clearable
              placeholder="全部生命周期"
            >
              <el-option label="独立实体" value="STANDALONE" />
              <el-option label="流程实体" value="WORKFLOW" />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
          </div>

          <el-table
            v-loading="loading"
            :data="rows"
            border
            stripe
            height="430"
            row-key="id"
            @row-click="handleRowClick"
          >
            <el-table-column label="选择" width="52" align="center">
              <template #default="{ row }">
                <el-checkbox
                  v-if="multiple"
                  :model-value="isSelected(row)"
                  :disabled="isExcluded(row)"
                  @click.stop
                  @change="toggle(row)"
                />
                <el-radio
                  v-else
                  :model-value="singleSelectedValue"
                  :value="entitySelectionKey(row, valueKey)"
                  :disabled="isExcluded(row)"
                  @click.stop
                  @change="toggle(row)"
                >
                  <span />
                </el-radio>
              </template>
            </el-table-column>
            <el-table-column label="实体" min-width="230">
              <template #default="{ row }">
                <div class="primary-text">{{ row.entityName || row.entityCode }}</div>
                <div class="secondary-text">{{ row.entityCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="生命周期" width="110">
              <template #default="{ row }">{{ lifecycleText(row.lifecycleMode) }}</template>
            </el-table-column>
            <el-table-column label="存储" width="100">
              <template #default="{ row }">{{ storageText(row.storageMode) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" effect="plain">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="currentPageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            class="picker-pagination"
            @size-change="handlePageSizeChange"
            @current-change="loadOptions"
          />
        </section>

        <aside class="selected-section">
          <div class="selected-heading">
            <div>
              <strong>已选实体</strong>
              <span>{{ draftItems.length }} 个</span>
            </div>
            <el-button
              v-if="multiple && draftItems.length"
              link
              type="primary"
              @click="draftItems = []"
            >
              清空
            </el-button>
          </div>
          <el-scrollbar class="selected-scrollbar" height="468px">
            <el-empty
              v-if="!draftItems.length"
              description="尚未选择实体"
              :image-size="72"
            />
            <div v-else class="selected-list">
              <div
                v-for="item in draftItems"
                :key="entitySelectionKey(item, valueKey)"
                class="selected-item"
                :class="{ 'is-missing': item.missing }"
              >
                <div>
                  <strong>{{ item.entityName || item.entityCode }}</strong>
                  <span>{{ item.entityCode || item.id }}</span>
                </div>
                <el-button
                  circle
                  text
                  title="移除"
                  @click="removeDraftItem(item)"
                >
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </div>
          </el-scrollbar>
        </aside>
      </div>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="confirmSelection">
          确认选择<span v-if="multiple">（{{ draftItems.length }}）</span>
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { CircleClose, Close, Search } from '@element-plus/icons-vue'
import { entityApi } from '@/api/entity'
import {
  entitySelectionKey,
  normalizeEntitySelectionValues,
  reconcileEntitySelection,
  serializeEntitySelection,
  toggleEntitySelection
} from '@/shared/entity-definition-selection'

const props = defineProps({
  modelValue: {
    type: [String, Number, Array],
    default: ''
  },
  multiple: {
    type: Boolean,
    default: false
  },
  valueKey: {
    type: String,
    default: 'entityCode',
    validator: value => ['id', 'entityCode'].includes(value)
  },
  valueCase: {
    type: String,
    default: 'preserve',
    validator: value => ['preserve', 'lower', 'upper'].includes(value)
  },
  title: {
    type: String,
    default: '选择实体'
  },
  placeholder: {
    type: String,
    default: '请选择实体'
  },
  query: {
    type: Object,
    default: () => ({})
  },
  excludeValues: {
    type: Array,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  },
  clearable: {
    type: Boolean,
    default: true
  },
  pageSize: {
    type: Number,
    default: 10
  },
  dialogWidth: {
    type: String,
    default: 'min(1040px, 94vw)'
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'selected', 'resolved'])

const visible = ref(false)
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const currentPageSize = ref(props.pageSize)
const keyword = ref('')
const status = ref('')
const lifecycleMode = ref('')
const selectedItems = ref([])
const draftItems = ref([])
let requestSequence = 0
let resolveSequence = 0

const normalizedValues = computed(() =>
  normalizeEntitySelectionValues(props.modelValue, props.multiple))
const excludedSet = computed(() =>
  new Set((props.excludeValues || []).map(value => String(value))))
const singleSelectedValue = computed(() =>
  draftItems.value[0]
    ? entitySelectionKey(draftItems.value[0], props.valueKey)
    : '')
const triggerPrimaryText = computed(() => {
  if (props.multiple) return `已选 ${selectedItems.value.length} 个实体`
  return selectedItems.value[0]?.entityName
    || selectedItems.value[0]?.entityCode
    || '已选择实体'
})
const triggerSecondaryText = computed(() => {
  if (props.multiple) {
    return selectedItems.value
      .slice(0, 2)
      .map(item => item.entityName || item.entityCode)
      .join('、')
  }
  return selectedItems.value[0]?.entityCode || selectedItems.value[0]?.id || ''
})

watch(
  () => [props.modelValue, props.valueKey, props.multiple],
  resolveCommittedSelection,
  { deep: true, immediate: true }
)

watch(
  () => props.pageSize,
  value => {
    currentPageSize.value = value
  }
)

async function resolveCommittedSelection() {
  const sequence = ++resolveSequence
  const values = normalizedValues.value
  if (!values.length) {
    selectedItems.value = []
    return
  }
  try {
    const payload = props.valueKey === 'id'
      ? { ids: values }
      : { entityCodes: values }
    const resolved = await entityApi.resolveOptions(payload)
    if (sequence !== resolveSequence) return
    selectedItems.value = reconcileEntitySelection(
      values,
      Array.isArray(resolved) ? resolved : [],
      props.valueKey
    )
    emit('resolved', props.multiple ? selectedItems.value : selectedItems.value[0] || null)
  } catch {
    if (sequence !== resolveSequence) return
    selectedItems.value = reconcileEntitySelection(values, [], props.valueKey)
    emit('resolved', props.multiple ? selectedItems.value : selectedItems.value[0] || null)
  }
}

async function openPicker() {
  if (props.disabled) return
  await resolveCommittedSelection()
  draftItems.value = selectedItems.value.map(item => ({ ...item }))
  visible.value = true
  pageNum.value = 1
  await loadOptions()
}

async function loadOptions() {
  if (!visible.value) return
  const sequence = ++requestSequence
  loading.value = true
  try {
    const page = await entityApi.getOptions({
      ...props.query,
      keyword: keyword.value.trim() || undefined,
      status: props.query.status || status.value || undefined,
      lifecycleMode: props.query.lifecycleMode || lifecycleMode.value || undefined,
      pageNum: pageNum.value,
      pageSize: currentPageSize.value
    })
    if (sequence !== requestSequence) return
    rows.value = page?.records || []
    total.value = Number(page?.total || 0)
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadOptions()
}

function handlePageSizeChange() {
  pageNum.value = 1
  loadOptions()
}

function isExcluded(item) {
  return excludedSet.value.has(entitySelectionKey(item, props.valueKey))
}

function isSelected(item) {
  const key = entitySelectionKey(item, props.valueKey)
  return draftItems.value.some(current =>
    entitySelectionKey(current, props.valueKey) === key)
}

function toggle(item) {
  if (isExcluded(item)) return
  draftItems.value = toggleEntitySelection(draftItems.value, item, {
    multiple: props.multiple,
    valueKey: props.valueKey
  })
}

function handleRowClick(item) {
  toggle(item)
}

function removeDraftItem(item) {
  const key = entitySelectionKey(item, props.valueKey)
  draftItems.value = draftItems.value.filter(current =>
    entitySelectionKey(current, props.valueKey) !== key)
}

function confirmSelection() {
  selectedItems.value = draftItems.value.map(item => ({ ...item }))
  const value = serializeEntitySelection(selectedItems.value, {
    multiple: props.multiple,
    valueKey: props.valueKey,
    valueCase: props.valueCase
  })
  emit('update:modelValue', value)
  emit('change', value)
  emit('selected', props.multiple ? selectedItems.value : selectedItems.value[0] || null)
  visible.value = false
}

function clearCommittedSelection() {
  selectedItems.value = []
  const value = props.multiple ? [] : ''
  emit('update:modelValue', value)
  emit('change', value)
  emit('selected', props.multiple ? [] : null)
}

function resetTransientState() {
  keyword.value = ''
  status.value = ''
  lifecycleMode.value = ''
  pageNum.value = 1
  rows.value = []
  total.value = 0
}

function lifecycleText(value) {
  return value === 'WORKFLOW' ? '流程实体' : '独立实体'
}

function storageText(value) {
  return value === 'SYSTEM' ? '系统' : '动态'
}

function statusText(value) {
  return {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    DISABLED: '已停用'
  }[value] || value || '-'
}

function statusType(value) {
  return {
    DRAFT: 'warning',
    PUBLISHED: 'success',
    DISABLED: 'info'
  }[value] || 'info'
}
</script>

<style scoped>
.entity-definition-picker {
  width: 100%;
  min-width: 0;
}

.picker-trigger {
  display: flex;
  width: 100%;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 11px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.picker-trigger:hover {
  border-color: var(--el-color-primary);
}

.picker-trigger:disabled {
  border-color: var(--el-disabled-border-color);
  background: var(--el-disabled-bg-color);
  color: var(--el-disabled-text-color);
  cursor: not-allowed;
}

.trigger-content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.35;
}

.trigger-primary,
.trigger-secondary,
.trigger-placeholder {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trigger-primary {
  color: var(--el-text-color-primary);
}

.trigger-secondary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.trigger-placeholder {
  color: var(--el-text-color-placeholder);
}

.trigger-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
}

.clear-icon:hover {
  color: var(--el-color-danger);
}

.dialog-heading,
.selected-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dialog-heading > div,
.selected-heading > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.dialog-heading strong {
  font-size: 18px;
}

.dialog-heading span,
.selected-heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.picker-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  min-height: 520px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.option-section {
  min-width: 0;
  padding: 16px 16px 0 0;
}

.selected-section {
  min-width: 0;
  padding: 16px 0 0 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.search-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 130px 150px auto;
  gap: 10px;
  margin-bottom: 12px;
}

.picker-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.primary-text {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.secondary-text {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.selected-heading {
  min-height: 36px;
  margin-bottom: 12px;
}

.selected-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 8px;
}

.selected-scrollbar {
  height: 468px;
  min-height: 0;
}

.selected-item {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.selected-item > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.selected-item strong,
.selected-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-item span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.selected-item.is-missing {
  border-color: var(--el-color-danger-light-7);
  background: var(--el-color-danger-light-9);
}

.entity-definition-picker-dialog :deep(.el-dialog__footer) {
  position: relative;
  z-index: 1;
  background: var(--el-bg-color);
}

@media (max-width: 860px) {
  .picker-layout {
    grid-template-columns: 1fr;
  }

  .selected-section {
    padding-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }

  .search-toolbar {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
