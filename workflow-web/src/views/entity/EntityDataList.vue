<template>
  <div class="entity-data-list">
    <!-- 加载中 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>
    
    <!-- 实体未配置提示 -->
    <el-empty v-else-if="!entityCode" description="未配置实体编码" />
    
    <!-- 实体不存在提示 -->
    <el-empty v-else-if="!entityDefinition.id" description="实体不存在或未发布" />
    
    <template v-else>
      <!-- 自定义列表组件 -->
      <component
        v-if="customListComponent && hasCustomListComponent(customListComponent)"
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
        :canAction="canAction"
        :getActionReason="getActionReason"
        :getStatusType="getStatusType"
        :getStatusText="getStatusText"
        :formatDate="formatDate"
      />
      <template v-else>
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
          v-model:selectedRows="selectedRows"
          @create="handleCreate"
          @view="handleView"
          @edit="handleEdit"
          @delete="handleDelete"
          @approve="handleApprove"
          @batch-delete="handleBatchDelete"
          @export-selected="() => handleExport('SELECTED')"
          @export-all="() => handleExport('ALL')"
          @size-change="handleSizeChange"
          @page-change="handlePageChange"
        />
      </template>
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
      :listKey="listConfig?.listKey"
      @success="loadDataList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityApi, entityDataApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import { getFormForNewData } from '@/api/entityFormResolve'
import { useUserStore } from '@/stores/user'
import { getEntityStatusList } from '@/api/entityStatus'
import { getCustomListComponent, hasCustomListComponent } from '@/utils/customComponentRegistry.js'
import {
  canExecuteAction,
  getActionCapabilityReason,
  hasButtonPermission
} from '@/utils/listButtonPermission'
import { formatDateValue } from '@/shared/list-runtime'
import { safeParseConfig } from '@/shared/config-runtime'
import EntityDataSearchForm from './components/EntityDataSearchForm.vue'
import EntityDataTable from './components/EntityDataTable.vue'
import EntityDataFormDialog from './components/EntityDataFormDialog.vue'
import EntityApprovalDialog from './components/approval/EntityApprovalDialog.vue'

const route = useRoute()
const userStore = useUserStore()

// 从路由参数获取实体编码
const entityCode = computed(() => route.params.entityCode as string || route.query.entityCode as string)

// 状态
const loading = ref(false)
const tableLoading = ref(false)

// 实体定义
const entityDefinition = ref<any>({})
const entityFields = ref<any[]>([])

// 列表配置（entity_list_config）
const listConfig = ref<any>(null)
const listConfigFields = ref<any[]>([])

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

// 表格选中行
const selectedRows = ref<any[]>([])

// 数据列表
const dataList = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 查询条件
const queryForm = reactive<Record<string, any>>({})

// 默认表单配置（用于自定义表单组件）
const defaultForm = ref<any>(null)

// 弹窗组件 ref
const formDialogRef = ref<InstanceType<typeof EntityDataFormDialog>>()
const approvalDialogRef = ref<InstanceType<typeof EntityApprovalDialog>>()

// 计算属性
const entityName = computed(() => entityDefinition.value?.entityName)

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
    return !f.isSystem && !['SUB_FORM', 'SUB_FORM_LIST'].includes(type)
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
  return entityFields.value.filter((f: any) => !f.isSystem)
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
  exportData: handleExport,
  canAction,
  getActionReason
}))

function safeJsonParse(text: any) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch (e) {
    return null
  }
}

// 工具栏按钮（按配置 + 权限过滤）
const toolbarButtons = computed(() => {
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
    .sort((a: any, b: any) => (a.sort || 0) - (b.sort || 0))
  return buttons
})

// 操作列按钮（按配置 + 权限过滤）
const rowActionButtons = computed(() => {
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
    .sort((a: any, b: any) => (a.sort || 0) - (b.sort || 0))
  return buttons
})

// 是否显示选择列
const showSelectionColumn = computed(() => {
  return toolbarButtons.value.some((b: any) => b.key === 'exportSelected' || b.key === 'batchDelete')
})

// 引用实体名称缓存
const refEntityNameMap = ref<Record<string, string>>({})

// 加载引用实体名称
async function loadRefEntityNames() {
  if (!dataList.value.length) return

  const sourceFields = listFields.value.length > 0 ? listFields.value : entityFields.value
  if (!sourceFields.length) return

  const refFields = sourceFields.filter((f: any) =>
    ['REFERENCE', 'MULTI_REFERENCE', 'DEPT', 'USER', 'ROLE', 'GROUP'].includes(f.fieldType)
  )
  if (!refFields.length) return

  const groupMap = new Map<string, Set<string>>()

  for (const row of dataList.value) {
    for (const field of refFields) {
      const val = row.data?.[field.fieldCode] ?? row[field.fieldCode]
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
  return entityStatusMap.value[status] || status
}

// 格式化日期
const formatDate = (date: string) => {
  return formatDateValue(date)
}

// 加载实体定义
const loadEntityDefinition = async () => {
  if (!entityCode.value) return
  
  loading.value = true
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
  } finally {
    loading.value = false
  }
}

// 加载列表配置
const loadListConfig = async () => {
  if (!entityDefinition.value?.id) return
  try {
    const configs = await entityListConfigApi.getByEntityId(entityDefinition.value.id)
    if (configs && configs.length > 0) {
      const config = configs.find((c: any) => c.isDefault) || configs[0]
      const detail = await entityListConfigApi.getById(config.id)
      if (detail) {
        listConfig.value = detail
        listConfigFields.value = detail.fields || []
        const configuredPageSize = Number(safeParseConfig(detail.viewConfig)?.pagination?.pageSize)
        if (configuredPageSize > 0) {
          pageSize.value = configuredPageSize
        }
      }
    } else {
      listConfig.value = null
      listConfigFields.value = []
    }
  } catch (e) {
    console.error('加载列表配置失败:', e)
    listConfig.value = null
    listConfigFields.value = []
  }
}

// 加载新增数据表单
const loadDefaultForm = async () => {
  if (!entityCode.value) return
  try {
    const res = await getFormForNewData(entityCode.value)
    defaultForm.value = res || null
  } catch (e) {
    console.log('加载新增数据表单失败:', e)
    defaultForm.value = null
  }
}

// 加载数据列表
const loadDataList = async () => {
  if (!entityCode.value) return
  
  tableLoading.value = true
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
    
    let res
    if (listConfig.value?.id) {
      res = await entityDataApi.getListWithConfig(entityCode.value, listConfig.value.listKey, params)
    } else {
      res = await entityDataApi.getList(entityCode.value, params)
    }
    
    const allData = Array.isArray(res) ? res : (res?.list || res?.records || res?.rows || [])
    total.value = allData.length
    
    const start = (pageNum.value - 1) * pageSize.value
    const end = start + pageSize.value
    dataList.value = allData.slice(start, end)
    await loadRefEntityNames()
  } catch (error) {
    console.error('加载数据列表失败:', error)
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
    await ElMessageBox.confirm('确定删除该数据吗？', '提示', { type: 'warning' })
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
    await ElMessageBox.confirm(`确定删除选中的 ${selectedRows.value.length} 条数据吗？`, '提示', { type: 'warning' })
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

// 判断是否可审批
const canAction = (row: any, buttonKey: string) => {
  return canExecuteAction(row, buttonKey)
}

const getActionReason = (row: any, buttonKey: string) => {
  return getActionCapabilityReason(row, buttonKey)
}

// 打开新增弹窗
const handleCreate = () => {
  formDialogRef.value?.openCreate()
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

// 监听实体编码变化
watch(() => entityCode.value, () => {
  if (entityCode.value) {
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
}
</style>
