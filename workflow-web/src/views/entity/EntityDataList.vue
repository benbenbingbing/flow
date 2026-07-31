<template>
  <div class="entity-data-list">
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>

    <PageState
      v-else-if="loadError"
      type="error"
      title="业务列表加载失败"
      :description="loadError"
      retryable
      @retry="loadEntityDefinition"
    />
    
    <el-empty v-else-if="!entityCode" description="未配置实体编码" />
    <el-empty v-else-if="!entityDefinition.id" description="实体不存在或未发布" />
    
    <template v-else>
      <PageState
        v-if="dataError"
        type="stale"
        title="列表数据未能刷新"
        :description="dataError"
        retryable
        compact
        @retry="loadDataList"
      />

      <component
        v-if="!dataError && customListComponent && hasCustomListComponent(customListComponent)"
        :is="getCustomListComponent(customListComponent)"
        :entityCode="entityCode"
        :entityDefinition="entityDefinition"
        :entityName="entityName"
        :listConfig="listConfig"
        :listConfigFields="listConfigFields"
        :listFields="listFields"
        :queryFields="queryFields"
        :queryForm="queryForm"
        :dataList="dataList"
        :loading="loading"
        :tableLoading="tableLoading"
        :total="total"
        :pageNum="pageNum"
        :pageSize="pageSize"
        :config="viewConfig.customComponentProps"
        :runtime="customListRuntime"
        @search="handleSearch"
        @reset="handleReset"
        @sizeChange="handleSizeChange"
        @pageChange="handlePageChange"
        @create="handleCreate"
        @view="handleView"
        @edit="handleEdit"
        @delete="handleDelete"
        @approve="handleApprove"
        @versions="handleVersions"
        :canAction="canAction"
        :getActionReason="getActionReason"
        :getStatusType="getStatusType"
        :getStatusText="getStatusText"
        :formatDate="formatDate"
      />
      <template v-else-if="!dataError">
        <EntityDataSearchForm
          v-if="queryFields.length > 0"
          v-model:form="queryForm"
          :fields="queryFields"
          :useListConfig="useListConfig"
          :viewConfig="viewConfig"
          @search="handleSearch"
          @reset="handleReset"
        />

        <EntityDataTable
          :dataList="dataList"
          :loading="tableLoading"
          :total="total"
          :pageNum="pageNum"
          :pageSize="pageSize"
          :listFields="listFields"
          :toolbarButtons="toolbarButtons"
          :toolbarCapabilities="listConfig?.toolbarCapabilities || {}"
          :rowActionButtons="rowActionButtons"
          :showSelectionColumn="showSelectionColumn"
          :useListConfig="useListConfig"
          :entityCode="entityCode"
          :entityDefinition="entityDefinition"
          :entityStatusMap="entityStatusMap"
          :refEntityNameMap="refEntityNameMap"
          :refresh="loadDataList"
          :viewConfig="viewConfig"
          :showVersionAction="!selectionScene && !isSystemEntity"
          :selection-mode="effectiveSelectionMode"
          v-model:selectedRows="selectedRows"
          @create="handleCreate"
          @view="handleView"
          @edit="handleEdit"
          @delete="handleDelete"
          @approve="handleApprove"
          @versions="handleVersions"
          @batch-delete="handleBatchDelete"
          @export-selected="() => handleExport('SELECTED')"
          @export-all="() => handleExport('ALL')"
          @event-action="handleEventAction"
          @size-change="handleSizeChange"
          @page-change="handlePageChange"
        />
      </template>

      <div v-if="selectionScene" class="selection-footer">
        <span>已选择 {{ selectedRows.length }} 条</span>
        <div>
          <el-button @click="emit('cancel')">取消</el-button>
          <el-button
            type="primary"
            :disabled="selectedRows.length === 0"
            @click="confirmSelection"
          >
            确认选择
          </el-button>
        </div>
      </div>
    </template>

    <EntityDataFormDialog
      ref="formDialogRef"
      :entityCode="entityCode"
      :entityDefinition="entityDefinition"
      :entityFields="entityFields"
      :defaultForm="defaultForm"
      :listKey="listConfig?.listKey"
      @success="loadDataList"
    />

    <EntityApprovalDialog
      ref="approvalDialogRef"
      :entityCode="entityCode"
      :defaultForm="defaultForm"
      :entityDefinition="entityDefinition"
      :entityFields="entityFields"
      :listKey="listConfig?.listKey"
      @success="loadDataList"
    />

    <EntityRecordVersionDrawer
      ref="versionDrawerRef"
      :entityCode="entityCode"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityApi, entityDataApi } from '@/api/entity'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
import { uiEventBindingApi } from '@/api/uiConfig'
import { applySelectionReturnMappings } from '@/utils/selectionReturnMappings'
import { getFormForNewData } from '@/api/entityFormResolve'
import { useUserStore } from '@/stores/user'
import { getEntityStatusList } from '@/api/entityStatus'
import { getCustomListComponent, hasCustomListComponent } from '@/utils/customComponentRegistry.js'
import {
  canExecuteAction,
  getActionCapabilityReason,
  hasButtonPermission
} from '@/utils/listButtonPermission'
import { formatDateValue, getCellValue } from '@/shared/list-runtime'
import { safeParseConfig } from '@/shared/config-runtime'
import EntityDataSearchForm from './components/EntityDataSearchForm.vue'
import EntityDataTable from './components/EntityDataTable.vue'
import EntityDataFormDialog from './components/EntityDataFormDialog.vue'
import EntityApprovalDialog from './components/approval/EntityApprovalDialog.vue'
import EntityRecordVersionDrawer from './components/EntityRecordVersionDrawer.vue'
import { useEntityDataSelectionState } from './composables/useEntityDataSelectionState'
import PageState from '@/components/PageState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const props = withDefaults(defineProps<{
  entityCode?: string
  listKey?: string
  scene?: string
  context?: Record<string, any>
  selectionMode?: 'NONE' | 'SINGLE' | 'MULTIPLE'
  initialSelectedRows?: any[]
}>(), {
  entityCode: '',
  listKey: '',
  context: () => ({}),
  selectionMode: 'NONE',
  initialSelectedRows: () => []
})

const emit = defineEmits<{
  confirm: [rows: any[]]
  cancel: []
}>()

const entityCode = computed(() =>
  props.entityCode
  || route.params.entityCode as string
  || route.query.entityCode as string
)
const runtimeListKey = computed(() =>
  props.listKey
  || route.params.listKey as string
  || route.query.listKey as string
)
const runtimeScene = computed(() =>
  (props.scene || route.query.scene as string || 'PAGE').toUpperCase()
)
const listConfig = ref<any>(null), listConfigFields = ref<any[]>([])
const { effectiveSelectionMode, selectionScene, selectedRows } =
  useEntityDataSelectionState(props, runtimeScene, listConfig)

const loading = ref(false)
const tableLoading = ref(false)
const loadError = ref('')
const dataError = ref('')

const entityDefinition = ref<any>({})
const entityFields = ref<any[]>([])

const DEFAULT_VIEW_CONFIG = {
  search: { defaultVisibleCount: 4, collapsible: true, labelWidth: 100 },
  table: { stripe: true, border: false, showIndex: true, size: 'default' },
  pagination: { pageSize: 10, pageSizes: [10, 20, 50, 100] },
  customComponentProps: {}
}

const viewConfig = computed(() => {
  const saved = safeParseConfig(listConfig.value?.viewConfig)
  return {
    ...DEFAULT_VIEW_CONFIG,
    ...saved,
    search: { ...DEFAULT_VIEW_CONFIG.search, ...(saved.search || {}) },
    table: { ...DEFAULT_VIEW_CONFIG.table, ...(saved.table || {}) },
    pagination: { ...DEFAULT_VIEW_CONFIG.pagination, ...(saved.pagination || {}) },
    customComponentProps: saved.customComponentProps || {}
  }
})

const dataList = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const queryForm = reactive<Record<string, any>>({})

const defaultForm = ref<any>(null)
const createFormLoading = ref(false)

const formDialogRef = ref<InstanceType<typeof EntityDataFormDialog>>()
const approvalDialogRef = ref<InstanceType<typeof EntityApprovalDialog>>()
const versionDrawerRef = ref<InstanceType<typeof EntityRecordVersionDrawer>>()

// 计算属性
const entityName = computed(() => entityDefinition.value?.entityName)
const isSystemEntity = computed(() => entityDefinition.value?.storageMode === 'SYSTEM')

// 查询字段（使用列表配置）
const queryFields = computed(() => {
  if (listConfigFields.value.length > 0) {
    return listConfigFields.value
      .filter((f: any) => f.isQuery)
      .map((f: any) => {
        const originField = entityFields.value.find((ef: any) => ef.fieldCode === f.fieldCode)
        const queryConfig = safeParseConfig(f.queryConfig)
        return {
          ...f,
          componentType: queryConfig.componentType || originField?.componentType || f.componentType,
          placeholder: queryConfig.placeholder || f.placeholder,
          defaultValue: queryConfig.defaultValue,
          fieldType: originField?.fieldType || f.fieldType || 'STRING',
          optionsJson: originField?.optionsJson || f.optionsJson,
          refEntityType: originField?.refEntityType,
          refEntityId: originField?.refEntityId,
          queryType: f.queryType || 'LIKE'
        }
      })
      .filter((f: any) => !['SUB_FORM', 'SUB_FORM_LIST'].includes((f.componentType || f.fieldType || '').toUpperCase()))
  }
  return entityFields.value.filter((f: any) => {
    const type = (f.componentType || f.fieldType || '').toUpperCase()
    return f.runtimeReadable !== false
      && !['SUB_FORM', 'SUB_FORM_LIST'].includes(type)
  })
})

// 列表显示字段（使用列表配置）
const listFields = computed(() => {
  if (listConfigFields.value.length > 0) {
    return listConfigFields.value
      .filter((f: any) => f.showInList)
      .map((f: any) => {
        const originField = entityFields.value.find((ef: any) => ef.fieldCode === f.fieldCode)
        return {
          ...f,
          fieldType: originField?.fieldType || 'STRING',
          optionsJson: originField?.optionsJson,
          refEntityType: originField?.refEntityType,
          refEntityId: originField?.refEntityId
        }
      })
  }
  return entityFields.value.filter((f: any) => f.runtimeReadable !== false)
})

// 是否使用列表配置
const useListConfig = computed(() => listConfigFields.value.length > 0)

// 自定义列表组件名
const customListComponent = computed(() => listConfig.value?.customComponent || '')

const customListRuntime = computed(() => ({
  version: 2,
  viewConfig: viewConfig.value,
  reload: loadDataList,
  search: handleSearch,
  reset: handleReset,
  create: handleCreate,
  view: handleView,
  edit: handleEdit,
  delete: handleDelete,
  approve: handleApprove,
  versions: handleVersions,
  exportData: handleExport,
  canAction,
  getActionReason
}))

function safeJsonParse(text: any) {
  if (!text) return null
  if (typeof text === 'object') return text
  try {
    return JSON.parse(text)
  } catch (e) {
    return null
  }
}

function buttonOrder(button: any) {
  const orderKey = Number(button?.orderKey)
  if (Number.isFinite(orderKey) && orderKey > 0) {
    return orderKey
  }
  return Number(button?.sort || 0) * 1000000
}

// 工具栏按钮（按配置 + 权限过滤）
const toolbarButtons = computed(() => {
  if (selectionScene.value || isSystemEntity.value) return []
  const DEFAULT_TOOLBAR_BUTTONS = [
    { key: 'create', type: 'built-in', label: '新增数据', icon: 'Plus', buttonType: 'primary', sort: 1, enabled: true, perm: '' },
    { key: 'exportSelected', type: 'built-in', label: '导出选中', icon: 'Download', buttonType: 'default', sort: 2, enabled: true, perm: '' },
    { key: 'exportAll', type: 'built-in', label: '导出全部', icon: 'Download', buttonType: 'default', sort: 3, enabled: true, perm: '' },
    { key: 'batchDelete', type: 'built-in', label: '批量删除', icon: 'Delete', buttonType: 'danger', sort: 4, enabled: true, perm: '' }
  ]
  const config = safeJsonParse(listConfig.value?.toolbarConfig)
  const buttons = (config && config.length > 0 ? config : DEFAULT_TOOLBAR_BUTTONS.map((b: any) => ({ ...b })))
    .filter((b: any) => b.enabled !== false)
    .filter((b: any) => hasButtonPermission(b))
    .filter((b: any) => {
      if (b.key === 'batchDelete' || b.key === 'exportSelected') return true
      return listConfig.value?.toolbarCapabilities?.[b.key]?.visible !== false
    })
    .sort((a: any, b: any) => buttonOrder(a) - buttonOrder(b))
  return buttons
})

// 操作列按钮（按配置 + 权限过滤）
const rowActionButtons = computed(() => {
  if (selectionScene.value) return []
  if (isSystemEntity.value) {
    return [
      {
        key: 'view',
        type: 'built-in',
        label: '查看',
        buttonType: 'primary',
        link: true,
        sort: 1,
        enabled: true,
        perm: ''
      }
    ]
  }
  const DEFAULT_ROW_ACTION_BUTTONS = [
    { key: 'view', type: 'built-in', label: '查看', buttonType: 'primary', link: true, sort: 1, enabled: true, perm: '' },
    { key: 'edit', type: 'built-in', label: '编辑', buttonType: 'primary', link: true, sort: 2, enabled: true, perm: '' },
    { key: 'approve', type: 'built-in', label: '审批', buttonType: 'warning', link: true, sort: 3, enabled: true, perm: '' },
    { key: 'delete', type: 'built-in', label: '删除', buttonType: 'danger', link: true, sort: 4, enabled: true, perm: '' }
  ]
  const config = safeJsonParse(listConfig.value?.rowActionConfig)
  const buttons = (config && config.length > 0 ? config : DEFAULT_ROW_ACTION_BUTTONS.map((b: any) => ({ ...b })))
    .filter((b: any) => b.enabled !== false)
    .filter((b: any) => hasButtonPermission(b))
    .sort((a: any, b: any) => buttonOrder(a) - buttonOrder(b))
  return buttons
})

// 是否显示选择列
const showSelectionColumn = computed(() => {
  if (isSystemEntity.value) return false
  return selectionScene.value
    || effectiveSelectionMode.value !== 'NONE'
    || toolbarButtons.value.some((b: any) => b.key === 'exportSelected' || b.key === 'batchDelete')
})

// 引用实体名称缓存
const refEntityNameMap = ref<Record<string, string>>({})

// 加载引用实体名称
async function loadRefEntityNames() {
  if (!dataList.value.length) return

  const sourceFields = listFields.value.length > 0 ? listFields.value : entityFields.value
  if (!sourceFields.length) return

  const refFields = sourceFields.filter((f: any) =>
    [
      'REFERENCE',
      'MULTI_REFERENCE',
      'DEPT',
      'USER',
      'ROLE',
      'GROUP',
      'MENU',
      'DICT',
      'DICT_ITEM'
    ].includes(
      String(f.refEntityType || f.fieldType || '').toUpperCase()
    )
  )
  if (!refFields.length) return

  const groupMap = new Map<string, Set<string>>()

  for (const row of dataList.value) {
    for (const field of refFields) {
      const val = getCellValue(row, field, null)
      if (!val) continue

      const entityType = field.refEntityType || field.fieldType || 'CUSTOM'
      const refEntityId = field.refEntityId || ''
      const groupKey = `${entityType}:${refEntityId}`

      let idSet = groupMap.get(groupKey)
      if (!idSet) {
        idSet = new Set<string>()
        groupMap.set(groupKey, idSet)
      }

      if (field.fieldType === 'MULTI_REFERENCE') {
        let ids = val
        if (typeof ids === 'string') {
          try { ids = JSON.parse(ids) } catch { ids = [ids] }
        }
        if (Array.isArray(ids)) {
          ids.forEach((id: any) => id && idSet.add(String(id)))
        }
      } else {
        idSet.add(String(val))
      }
    }
  }

  const promises = []
  for (const [groupKey, idSet] of groupMap) {
    if (!idSet.size) continue
    const [entityType, refEntityId] = groupKey.split(':')
    const ids = Array.from(idSet).join(',')
    const params = new URLSearchParams({ ids })
    if (refEntityId) {
      params.append('refEntityId', refEntityId)
    }

    promises.push(
      fetch(`/api/entity-selector/${entityType}/batch?${params}`, {
        headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
      })
        .then(r => r.json())
        .then((res: any) => {
          if (res.code === 200 && res.data) {
            for (const item of res.data) {
              const cacheKey = `${groupKey}:${item.id}`
              refEntityNameMap.value[cacheKey] = item.name || item.code || item.id
            }
          }
        })
        .catch(err => console.error('加载引用实体名称失败:', err))
    )
  }

  await Promise.all(promises)
}

// 实体状态码 -> 状态名称映射
const entityStatusMap = ref<Record<string, string>>({})

async function loadEntityStatusMap() {
  if (!entityCode.value) return
  try {
    const list = await getEntityStatusList(entityCode.value)
    const map: Record<string, string> = {}
    ;(list || []).forEach((s: any) => {
      if (s.statusCode) {
        map[s.statusCode] = s.statusName || s.statusCode
      }
    })
    entityStatusMap.value = map
  } catch (e) {
    entityStatusMap.value = {}
  }
}

// 获取状态样式
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
  return map[status] || ''
}

// 获取状态文本（优先读取实体状态配置）
const getStatusText = (status: string) => {
  if (!status) return ''
  const builtIn: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    TERMINATED: '已终止',
    WITHDRAWN: '已撤回',
    COMPLETED: '已完成'
  }
  return entityStatusMap.value[status] || builtIn[status] || '未配置状态'
}

// 格式化日期
const formatDate = (date: string) => {
  return formatDateValue(date)
}

// 加载实体定义
const loadEntityDefinition = async () => {
  if (!entityCode.value) return
  
  loading.value = true
  loadError.value = ''
  dataError.value = ''
  entityDefinition.value = {}
  entityFields.value = []
  listConfig.value = null
  listConfigFields.value = []
  dataList.value = []
  total.value = 0
  defaultForm.value = null
  try {
    const res = await entityApi.getByCode(entityCode.value)
    entityDefinition.value = res || {}
    entityFields.value = res?.fields || []
    
    await loadListConfig()
    await loadDefaultForm()
    await loadEntityStatusMap()

    queryFields.value.forEach((field: any) => {
      queryForm[field.fieldCode] = field.defaultValue ?? ''
    })
    
    await loadDataList()
  } catch (error) {
    console.error('加载实体定义失败:', error)
    loadError.value = error?.message || '无法读取实体或列表配置，请检查发布状态后重试。'
  } finally {
    loading.value = false
  }
}

// 加载列表配置
const loadListConfig = async () => {
  if (!entityDefinition.value?.id || !runtimeListKey.value) return
  try {
    const schema = await entityListRuntimeApi.getSchema(
      entityCode.value,
      runtimeListKey.value,
      runtimeScene.value
    )
    listConfig.value = schema || null
    listConfigFields.value = schema?.fields || []
    const configuredPageSize = Number(safeParseConfig(schema?.viewConfig)?.pagination?.pageSize)
    if (configuredPageSize > 0) {
      pageSize.value = configuredPageSize
    }
  } catch (e) {
    console.error('加载列表配置失败:', e)
    listConfig.value = null
    listConfigFields.value = []
    throw new Error(e?.message || '列表不存在、尚未发布，或当前账号没有访问权限。')
  }
}

// 加载新增数据表单
const loadDefaultForm = async (notifyOnError = false) => {
  if (!entityCode.value) return false
  try {
    const res = await getFormForNewData(entityCode.value, { silentError: true })
    defaultForm.value = res || null
    return true
  } catch (e) {
    console.error('加载新增数据表单失败:', e)
    defaultForm.value = null
    if (notifyOnError) {
      ElMessage.error(e?.message || '加载最新发布表单失败，请稍后重试')
    }
    return false
  }
}

// 加载数据列表
const loadDataList = async () => {
  if (!entityCode.value) return
  
  tableLoading.value = true
  dataError.value = ''
  try {
    const params: Record<string, any> = {}
    Object.entries(queryForm).forEach(([key, value]) => {
      if (value !== '' && value !== null && value !== undefined) {
        params[key] = value
      }
    })
    queryFields.value.forEach((field: any) => {
      const code = field.fieldCode
      if (code && params[code] !== undefined && field.queryType) {
        params[code + '_op'] = field.queryType
      }
    })
    const res = await entityListRuntimeApi.query(
      entityCode.value,
      runtimeListKey.value,
      {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        scene: runtimeScene.value,
        filters: params,
        context: props.context
      }
    )
    
    if (Array.isArray(res)) {
      total.value = res.length
      const start = (pageNum.value - 1) * pageSize.value
      dataList.value = res.slice(start, start + pageSize.value)
    } else {
      const pageRecords = res?.list || res?.records || res?.rows || []
      dataList.value = pageRecords
      total.value = Number(res?.total ?? pageRecords.length)
      pageNum.value = Number(res?.pageNum ?? res?.current ?? pageNum.value)
      pageSize.value = Number(res?.pageSize ?? res?.size ?? pageSize.value)
    }
    await loadRefEntityNames()
  } catch (error) {
    console.error('加载数据列表失败:', error)
    dataError.value = error?.message || '无法读取列表数据；当前页面不会把错误显示成空列表。'
  } finally {
    tableLoading.value = false
  }
}

// 查询
const handleSearch = () => {
  pageNum.value = 1
  loadDataList()
}

// 重置
const handleReset = () => {
  queryFields.value.forEach((field: any) => {
    queryForm[field.fieldCode] = field.defaultValue ?? ''
    delete queryForm[field.fieldCode + '_start']
    delete queryForm[field.fieldCode + '_end']
  })
  handleSearch()
}

// 分页
const handleSizeChange = (val: number) => {
  pageSize.value = val
  pageNum.value = 1
  loadDataList()
}

const handlePageChange = (val: number) => {
  pageNum.value = val
  loadDataList()
}

// 删除
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      '删除后该条业务数据将无法在列表中恢复，并可能影响关联表单、引用字段和流程记录。确定继续吗？',
      '删除业务数据',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await entityDataApi.delete(entityCode.value, row.id, listConfig.value?.listKey)
    ElMessage.success('删除成功')
    loadDataList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择数据')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将删除选中的 ${selectedRows.value.length} 条业务数据，关联表单、引用字段和流程记录可能受影响。确定继续吗？`,
      '批量删除业务数据',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await entityDataApi.batchDelete(
      entityCode.value,
      selectedRows.value.map(row => row.id),
      listConfig.value?.listKey
    )
    ElMessage.success('批量删除成功')
    selectedRows.value = []
    loadDataList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '批量删除失败')
    }
  }
}

// 导出数据
const handleExport = async (exportType: string) => {
  try {
    const condition = { ...queryForm }
    const ids = exportType === 'SELECTED' ? selectedRows.value.map(r => r.id) : []
    const res = await entityDataApi.exportData(entityCode.value, {
      exportType,
      ids,
      listKey: listConfig.value?.listKey,
      condition
    })
    const blob = new Blob([res], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${entityCode.value}_${exportType}_${Date.now()}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (error: any) {
    ElMessage.error(error.message || '导出失败')
  }
}

const handleEventAction = async ({
  button,
  row,
  selectedRows: actionRows = []
}: {
  button: any
  row?: any
  selectedRows?: any[]
}) => {
  if (!listConfig.value?.id || !button?.key) {
    ElMessage.warning('按钮缺少可执行的事件绑定来源')
    return
  }
  const eventCode = row ? 'ROW_BUTTON_CLICK' : 'TOOLBAR_BUTTON_CLICK'
  try {
    const result = await uiEventBindingApi.execute(eventCode, {
      configType: 'LIST',
      configId: String(listConfig.value.id),
      entityCode: entityCode.value,
      listKey: listConfig.value.listKey,
      targetType: 'BUTTON',
      targetKey: String(button.key),
      recordId: row?.id,
      selectedIds: actionRows.map(item => item.id).filter(Boolean),
      input: {
        button,
        row: row || null,
        selectedRows: actionRows
      },
      context: {
        listId: String(listConfig.value.id),
        scene: runtimeScene.value
      }
    })
    await applyButtonEffects(result?.effects || [])
    if (result?.message) {
      ElMessage.success(result.message)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '按钮操作执行失败')
  }
}

async function applyButtonEffects(effects: any[]) {
  for (const effect of effects) {
    const type = String(effect?.type || '').toUpperCase()
    if (type === 'REFRESH_LIST') {
      await loadDataList()
      continue
    }
    if (type === 'MESSAGE' && effect.message) {
      ElMessage({
        type: effect.level || 'success',
        message: effect.message
      })
      continue
    }
    if (type === 'OPEN_ROUTE' && effect.route) {
      await router.push(effect.route)
      continue
    }
    if (type === 'DOWNLOAD_TASK') {
      ElMessage.success(effect.message || '下载任务已创建')
    }
  }
}

// 判断是否可审批
const canAction = (row: any, buttonKey: string) => {
  return canExecuteAction(row, buttonKey)
}

const getActionReason = (row: any, buttonKey: string) => {
  return getActionCapabilityReason(row, buttonKey)
}

// 打开新增弹窗
const handleCreate = async () => {
  if (createFormLoading.value) return
  createFormLoading.value = true
  try {
    const loaded = await loadDefaultForm(true)
    if (!loaded) return
    await nextTick()
    await formDialogRef.value?.openCreate()
  } finally {
    createFormLoading.value = false
  }
}

// 打开编辑弹窗
const handleEdit = (row: any) => {
  formDialogRef.value?.openEdit(row)
}

// 打开查看弹窗
const handleView = (row: any) => {
  approvalDialogRef.value?.openView(row)
}

// 打开审批弹窗
const handleApprove = (row: any) => {
  approvalDialogRef.value?.openApprove(row)
}

const handleVersions = (row: any) => {
  versionDrawerRef.value?.open(row)
}

const confirmSelection = () => {
  const rows = effectiveSelectionMode.value === 'SINGLE'
    ? selectedRows.value.slice(0, 1)
    : selectedRows.value
  const mappings = safeParseConfig(
    listConfig.value?.selectionConfig
  )?.returnMappings
  emit('confirm', rows.map(row =>
    applySelectionReturnMappings(row, mappings)))
}

// 监听实体编码变化
watch(() => [entityCode.value, runtimeListKey.value], () => {
  if (entityCode.value && runtimeListKey.value) {
    loadEntityDefinition()
  }
}, { immediate: true })
</script>

<style scoped lang="scss">
.entity-data-list {
  padding: 10px;
  
  .loading-container {
    padding: 10px;
  }

  .selection-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 12px;
    padding: 12px 4px 0;
    border-top: 1px solid var(--el-border-color-lighter);
  }
}
</style>
