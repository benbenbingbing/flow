<template>
  <el-card>
    <div class="table-toolbar">
      <template v-for="btn in toolbarButtons" :key="btn.key">
        <component
          v-if="btn.type === 'custom' && btn.customMode === 'component' && hasListButtonComponent(btn.customHandler)"
          :is="getListButtonComponent(btn.customHandler)"
          mode="toolbar"
          :context="{
            ...runtimeContext,
            selectedRows,
            entityCode: entityCode,
            entityDefinition: entityDefinition,
            refresh,
            canAction,
            getActionReason
          }"
        />
        <el-button
          v-else
          :type="btn.buttonType || 'default'"
          :disabled="isToolbarDisabled(btn)"
          :title="getToolbarReason(btn)"
          @click="onToolbarClick(btn)"
        >
          <el-icon v-if="btn.icon && iconMap[btn.icon]"><component :is="iconMap[btn.icon]" /></el-icon>
          {{ btn.label }}
        </el-button>
      </template>
    </div>
    <el-table
      ref="tableRef"
      :data="dataList"
      v-loading="loading"
      row-key="id"
      :stripe="tableConfig.stripe !== false"
      :border="tableConfig.border === true"
      :size="tableConfig.size || 'default'"
      :max-height="maxHeight"
      @selection-change="handleSelectionChange"
    >
      <el-table-column v-if="showSelectionColumn" type="selection" width="50" />
      <el-table-column v-if="tableConfig.showIndex !== false" type="index" width="50" />
      <!-- 使用列表配置时：完全动态列 -->
      <template v-if="useListConfig">
        <el-table-column v-for="field in listFields" :key="field.fieldCode"
          :prop="getListFieldProp(field.fieldCode)"
          :label="field.fieldName"
          :width="field.width > 0 ? field.width : undefined"
          :align="field.align"
          :fixed="getColumnConfig(field).fixed || undefined"
          :min-width="field.width > 0 ? undefined : (getColumnConfig(field).minWidth || 100)"
          :show-overflow-tooltip="getColumnConfig(field).showOverflowTooltip !== false">
          <template #default="{ row }">
            <!-- 自定义渲染组件 -->
            <ListCellRenderer
              v-if="field.renderComponent || (field.dataSourceType && field.dataSourceType !== 'ENTITY_FIELD')"
              :row="row"
              :field="field"
              :context="{
                entityCode,
                entityDefinition,
                entityStatusMap,
                getStatusText,
                refresh,
                refEntityNameMap
              }"
            />
            <!-- 状态字段特殊渲染 -->
            <el-tag v-else-if="field.fieldCode === 'status'" :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
            <!-- 日期字段格式化 -->
            <span v-else-if="isDateFieldCode(field.fieldCode)">
              {{ formatDate(row[field.fieldCode]) }}
            </span>
            <!-- 默认显示 -->
            <span v-else>{{ getFieldDisplayValue(row, field) }}</span>
          </template>
        </el-table-column>
      </template>
      <!-- 默认列 -->
      <template v-else>
        <el-table-column prop="dataNo" label="编号" width="150" />
        <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
        <el-table-column v-for="field in listFields" :key="field.fieldCode" 
                        :label="field.fieldName" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getFieldDisplayValue(row, field) }}
          </template>
        </el-table-column>
        <el-table-column prop="submitterName" label="提交人" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
      </template>
      <el-table-column v-if="hasVisibleRowActions" label="操作" min-width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="showVersionAction"
            type="primary"
            link
            @click="emit('versions', row)"
          >
            版本
          </el-button>
          <template v-for="btn in visibleRowButtons(row)" :key="btn.key">
            <component
              v-if="btn.type === 'custom' && btn.customMode === 'component' && hasListButtonComponent(btn.customHandler)"
              :is="getListButtonComponent(btn.customHandler)"
              mode="row"
              :row="row"
              :context="{
                ...runtimeContext,
                entityCode: entityCode,
                entityDefinition: entityDefinition,
                refresh,
                canAction,
                getActionReason
              }"
            />
            <el-button
              v-else
              :type="btn.buttonType || 'primary'"
              link
              :disabled="!canAction(row, btn.key)"
              :title="getActionReason(row, btn.key)"
              @click="onRowActionClick(btn, row)"
            >
              {{ btn.label }}
            </el-button>
          </template>
          <span v-if="!showVersionAction && visibleRowButtons(row).length === 0">-</span>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 分页 -->
    <div v-if="showPagination !== false" class="pagination-container">
      <el-pagination
        :current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        :page-sizes="paginationConfig.pageSizes || [10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="(val) => emit('size-change', val)"
        @current-change="(val) => emit('page-change', val)"
      />
    </div>

    <EntityListLauncher
      v-if="openListState.targetEntityCode && openListState.targetListKey"
      ref="entityListLauncherRef"
      :entity-code="openListState.targetEntityCode"
      :list-key="openListState.targetListKey"
      :presentation="openListState.presentation"
      :selection-mode="openListState.selectionMode"
      :context="openListState.context"
      :title="openListState.title"
      @confirm="handleOpenListConfirm"
    >
      <template #default></template>
    </EntityListLauncher>
  </el-card>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Download, Delete, View, Edit, Check, Close, Printer, FolderChecked } from '@element-plus/icons-vue'
import ListCellRenderer from '@/components/ListCellRenderer.vue'
import EntityListLauncher from '@/components/EntityListLauncher.vue'
import { hasListButtonComponent, getListButtonComponent } from '@/utils/listButtonComponentRegistry'
import { getListToolbarAction, getListRowAction } from '@/utils/listActionRegistry'
import { getFieldModelPath } from '@/shared/form-runtime'
import { formatDateValue, formatListFieldValue, isDateFieldCode } from '@/shared/list-runtime'
import { safeParseConfig } from '@/shared/config-runtime'
import {
  reconcileRecordPageSelection,
  recordSelectionIds
} from '@/shared/entity-record-selection'
import {
  canExecuteAction,
  getActionCapabilityReason,
  getSelectionActionState,
  isActionVisible
} from '@/utils/listButtonPermission'

const props = defineProps<{
  dataList: any[]
  loading: boolean
  total: number
  pageNum: number
  pageSize: number
  listFields: any[]
  toolbarButtons: any[]
  toolbarCapabilities: Record<string, any>
  rowActionButtons: any[]
  showSelectionColumn: boolean
  useListConfig: boolean
  entityCode: string
  entityDefinition: any
  entityStatusMap: Record<string, string>
  refEntityNameMap: Record<string, string>
  refresh: () => void
  viewConfig?: any
  showVersionAction?: boolean
  selectionMode?: 'NONE' | 'SINGLE' | 'MULTIPLE'
  showPagination?: boolean
  maxHeight?: number
  runtimeContext?: Record<string, any>
}>()

const tableConfig = computed(() => props.viewConfig?.table || {})
const paginationConfig = computed(() => props.viewConfig?.pagination || {})

const emit = defineEmits<{
  create: [btn?: any]
  'export-selected': [btn: any]
  'export-all': [btn: any]
  'batch-delete': []
  view: [row: any, btn?: any]
  edit: [row: any, btn?: any]
  approve: [row: any, btn?: any]
  delete: [row: any, btn?: any]
  versions: [row: any]
  'selection-change': [rows: any[]]
  'size-change': [val: number]
  'page-change': [val: number]
  'event-action': [payload: { button: any, row?: any, selectedRows: any[] }]
}>()

// 图标映射
const iconMap: Record<string, any> = {
  Plus,
  Download,
  Delete,
  View,
  Edit,
  Check,
  Close,
  Printer,
  FolderChecked
}

const getListFieldProp = (fieldCode: string) => {
  return getFieldModelPath(fieldCode)
}

const getColumnConfig = (field: any) => safeParseConfig(field?.columnConfig)

const getFieldDisplayValue = (row: any, field: any) => {
  return formatListFieldValue(
    row,
    field,
    props.refEntityNameMap,
    props.entityStatusMap
  )
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    'DRAFT': 'info',
    'PENDING': 'warning',
    'APPROVED': 'success',
    'REJECTED': 'danger',
    'TERMINATED': 'danger',
    'WITHDRAWN': 'info',
    'COMPLETED': 'success'
  }
  return map[status]
}

const getStatusText = (status: string) => {
  if (!status) return ''
  return props.entityStatusMap[status] || status
}

const formatDate = (date: string) => {
  return formatDateValue(date)
}

const handleSelectionChange = (selection: any[]) => {
  if (restoringPageSelection.value) return

  // Keep the incoming selection visible to toolbar capability checks before
  // reconciling cross-page selections for MULTIPLE mode.
  if (props.selectionMode !== 'MULTIPLE') {
    selectedRows.value = selection
  }

  if (props.selectionMode === 'SINGLE') {
    const nextSelection = selection.length
      ? [selection[selection.length - 1]]
      : []
    selectedRows.value = nextSelection
    emit('selection-change', nextSelection)
    restoreCurrentPageSelection()
    return
  }

  if (props.selectionMode === 'MULTIPLE') {
    const nextSelection = reconcileRecordPageSelection(
      selectedRows.value,
      props.dataList,
      selection
    )
    selectedRows.value = nextSelection
    emit('selection-change', nextSelection)
    return
  }

  selectedRows.value = selection
  emit('selection-change', selection)
}

// 内置工具栏动作映射
const BUILTIN_TOOLBAR_ACTIONS: Record<string, Function> = {
  create: (btn: any) => emit('create', btn),
  exportSelected: (btn: any) => emit('export-selected', btn),
  exportAll: (btn: any) => emit('export-all', btn),
  batchDelete: () => emit('batch-delete')
}

// 工具栏按钮点击分发
const onToolbarClick = (btn: any) => {
  if (isToolbarDisabled(btn)) {
    ElMessage.warning(getToolbarReason(btn) || '当前选择不满足操作条件')
    return
  }
  if (btn.type === 'built-in') {
    BUILTIN_TOOLBAR_ACTIONS[btn.key]?.(btn)
  } else if (btn.type === 'custom') {
    if (btn.customMode === 'event') {
      emit('event-action', {
        button: btn,
        selectedRows: selectedRows.value
      })
      return
    }
    if (btn.customMode === 'open-list') {
      openConfiguredList(btn)
      return
    }
    if (btn.customMode === 'open-form') {
      emit('create', btn)
      return
    }
    const handler = getListToolbarAction(btn.customHandler)
    if (handler) {
      handler({
        ...(props.runtimeContext || {}),
        selectedRows: selectedRows.value,
        entityCode: props.entityCode,
        entityDefinition: props.entityDefinition,
        refresh: props.refresh,
        config: btn
      })
    } else {
      ElMessage.warning(`未找到自定义执行器：${btn.customHandler}`)
    }
  }
}

// 内置操作列动作映射
const BUILTIN_ROW_ACTIONS: Record<string, Function> = {
  view: (row: any, btn: any) => emit('view', row, btn),
  edit: (row: any, btn: any) => emit('edit', row, btn),
  approve: (row: any, btn: any) => emit('approve', row, btn),
  delete: (row: any, btn: any) => emit('delete', row, btn)
}

// 操作列按钮点击分发
const onRowActionClick = (btn: any, row: any) => {
  if (!canAction(row, btn.key)) {
    ElMessage.warning(getActionReason(row, btn.key) || '当前数据不可操作')
    return
  }
  if (btn.type === 'built-in') {
    BUILTIN_ROW_ACTIONS[btn.key]?.(row, btn)
  } else if (btn.type === 'custom') {
    if (btn.customMode === 'event') {
      emit('event-action', {
        button: btn,
        row,
        selectedRows: selectedRows.value
      })
      return
    }
    if (btn.customMode === 'open-list') {
      openConfiguredList(btn, row)
      return
    }
    if (btn.customMode === 'open-form') {
      if (btn.targetFormMode === 'EDIT') {
        emit('edit', row, btn)
      } else {
        emit('view', row, btn)
      }
      return
    }
    const handler = getListRowAction(btn.customHandler)
    if (handler) {
      handler({
        ...(props.runtimeContext || {}),
        row,
        entityCode: props.entityCode,
        entityDefinition: props.entityDefinition,
        refresh: props.refresh,
        config: btn
      })
    } else {
      ElMessage.warning(`未找到自定义执行器：${btn.customHandler}`)
    }
  }
}

// 当前选中行（由父组件通过 selection-change 同步）
const selectedRows = defineModel<any[]>('selectedRows', { default: () => [] })
const tableRef = ref<any>()
const restoringPageSelection = ref(false)
const entityListLauncherRef = ref<InstanceType<typeof EntityListLauncher>>()
const pendingOpenListAction = ref<{ button: any, row?: any } | null>(null)
const openListState = reactive({
  targetEntityCode: '',
  targetListKey: '',
  presentation: 'DIALOG' as 'DIALOG' | 'DRAWER',
  selectionMode: 'NONE' as 'NONE' | 'SINGLE' | 'MULTIPLE',
  title: '选择数据',
  context: {} as Record<string, any>
})

async function restoreCurrentPageSelection() {
  if (!['SINGLE', 'MULTIPLE'].includes(props.selectionMode || 'NONE')) return
  restoringPageSelection.value = true
  await nextTick()
  tableRef.value?.clearSelection()
  const selectedIds = new Set(recordSelectionIds(selectedRows.value))
  props.dataList.forEach(row => {
    if (selectedIds.has(String(row.id))) {
      tableRef.value?.toggleRowSelection(row, true)
    }
  })
  await nextTick()
  restoringPageSelection.value = false
}

watch(
  () => props.dataList,
  () => {
    restoreCurrentPageSelection()
  },
  { flush: 'sync' }
)

async function openConfiguredList(button: any, row?: any) {
  if (!button.targetEntityCode || !button.targetListKey) {
    ElMessage.warning('按钮未配置目标实体和列表')
    return
  }
  pendingOpenListAction.value = { button, row }
  openListState.targetEntityCode = button.targetEntityCode
  openListState.targetListKey = button.targetListKey
  openListState.presentation = button.presentation === 'DRAWER' ? 'DRAWER' : 'DIALOG'
  openListState.selectionMode = ['SINGLE', 'MULTIPLE'].includes(button.selectionMode)
    ? button.selectionMode
    : 'NONE'
  openListState.title = button.openListTitle || button.label || '选择数据'
  openListState.context = {
    ...(props.runtimeContext || {}),
    sourceEntityCode: props.entityCode,
    sourceRecordId: row?.id || null,
    relationKey:
      button.relationKey
      || props.runtimeContext?.relationKey
      || null,
    parameters: {
      ...(props.runtimeContext?.parameters || {})
    }
  }
  await nextTick()
  entityListLauncherRef.value?.open()
}

function handleOpenListConfirm(rows: any[]) {
  const pending = pendingOpenListAction.value
  if (!pending?.button?.selectionHandler) return
  const handler = pending.row
    ? getListRowAction(pending.button.selectionHandler)
    : getListToolbarAction(pending.button.selectionHandler)
  if (!handler) {
    ElMessage.warning(`未找到选择结果处理器：${pending.button.selectionHandler}`)
    return
  }
  handler({
    ...(props.runtimeContext || {}),
    rows,
    row: pending.row,
    selectedRows: rows,
    entityCode: props.entityCode,
    entityDefinition: props.entityDefinition,
    refresh: props.refresh,
    config: pending.button
  })
}

const hasVisibleRowActions = computed(() =>
  props.showVersionAction
  || props.dataList.some(row => visibleRowButtons(row).length > 0)
)

const visibleRowButtons = (row: any) => {
  return props.rowActionButtons.filter(btn => isActionVisible(row, btn.key))
}

const canAction = (row: any, buttonKey: string) => {
  return canExecuteAction(row, buttonKey)
}

const getActionReason = (row: any, buttonKey: string) => {
  return getActionCapabilityReason(row, buttonKey)
}

const isSelectionButton = (buttonKey: string) =>
  buttonKey === 'batchDelete' || buttonKey === 'exportSelected'

const isToolbarDisabled = (btn: any) => {
  if (!isSelectionButton(btn.key)) {
    return props.toolbarCapabilities?.[btn.key]?.enabled === false
  }
  return !getSelectionActionState(selectedRows.value, btn.key).enabled
}

const getToolbarReason = (btn: any) => {
  if (!isSelectionButton(btn.key)) {
    return props.toolbarCapabilities?.[btn.key]?.reason || ''
  }
  return getSelectionActionState(selectedRows.value, btn.key).reason
}
</script>

<style scoped lang="scss">
.table-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
