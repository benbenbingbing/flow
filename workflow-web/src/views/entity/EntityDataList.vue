<template>
  <div class="entity-data-list" :class="{ embedded }">
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
      <RuntimeVersionDiagnostics
        v-if="showPageRuntimeDiagnostics && (dataError || usesCustomListComponent)"
        class="list-runtime-diagnostics list-runtime-diagnostics--fallback"
        :entries="listRuntimeDiagnosticEntries"
        :copy-entries="listRuntimeDiagnosticCopyEntries"
        copy-title="列表运行版本排障信息"
        :reset-key="listRuntimeDiagnosticResetKey"
      >
        <!-- 错误态或自定义列表没有默认 tbar，保留无标题的隐藏排障入口。 -->
        <span
          class="list-runtime-diagnostics__trigger"
          aria-hidden="true"
        />
      </RuntimeVersionDiagnostics>
      <PageState
        v-if="dataError"
        type="stale"
        title="列表数据未能刷新"
        :description="dataError"
        retryable
        compact
        @retry="loadDataList"
      />
      <el-alert
        v-if="pageSectionCompositions.length && !listSourceRecordId"
        title="请选择一条列表记录"
        description="选中左侧复选框后，下方关联内容会按该记录加载；切换选择会自动刷新。"
        type="info"
        :closable="false"
        show-icon
        class="master-detail-hint"
      />
      <template v-else>
        <RelatedContentRuntime
          v-for="item in pageSectionCompositions"
          :key="item.id || item.compositionKey"
          :composition="item"
          owner-type="LIST"
          :owner-id="listConfig.id"
          :release-id="listConfig.releaseId"
          :release-version="listConfig.publishedVersion"
          :source-record-id="listSourceRecordId"
          :traversal-context-token="viewCompositionTraversalToken"
          @target-saved="loadDataList"
        />
      </template>
      <component
        v-if="!dataError && usesCustomListComponent"
        :is="getCustomListComponent(customListComponent)"
        :entityCode="entityCode"
        :entityDefinition="entityDefinition"
        :entityName="entityName"
        :listConfig="listConfig"
        :listConfigFields="listConfigFields"
        :listFields="listFields"
        :queryFields="queryFields"
        :queryForm="queryForm"
        :entityStatusMap="entityStatusMap"
        :entityStatusOptions="entityStatusOptions"
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
          v-if="queryFields.length > 0 && (!embedded || showSearch)"
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
          :showVersionAction="!selectionScene && !isSystemEntity && canViewVersions"
          :show-pagination="!embedded || showPagination"
          :max-height="embedded && maxHeight > 0 ? maxHeight : undefined"
          :selection-mode="runtimeSelectionMode"
          :runtime-context="relatedContentRuntimeContext"
          :row-expand-compositions="rowExpandCompositions"
          :row-action-compositions="relatedRowActionCompositions"
          :toolbar-action-compositions="relatedToolbarActionCompositions"
          :list-owner-id="listConfig?.id || ''"
          :list-release-id="listConfig?.releaseId || ''"
          :list-release-version="listConfig?.publishedVersion || 0"
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
        >
          <template v-if="showPageRuntimeDiagnostics" #toolbar-leading>
            <RuntimeVersionDiagnostics
              class="list-runtime-diagnostics"
              :entries="listRuntimeDiagnosticEntries"
              :copy-entries="listRuntimeDiagnosticCopyEntries"
              copy-title="列表运行版本排障信息"
              :reset-key="listRuntimeDiagnosticResetKey"
            >
              <!-- 复用 tbar 左侧空白区作为隐藏入口，避免为排障能力额外展示列表标题。 -->
              <span
                class="list-runtime-diagnostics__trigger"
                aria-hidden="true"
              />
            </RuntimeVersionDiagnostics>
          </template>
        </EntityDataTable>
      </template>
      <div v-if="selectionScene" class="selection-footer">
        <span>已选择 {{ selectedRows.length }} 条</span>
        <div>
          <el-button @click="emit('cancel')">取消</el-button>
          <el-button
            v-for="option in selectionActionOptions"
            :key="option.action"
            :type="option.type || 'primary'"
            :disabled="selectedRows.length === 0"
            @click="confirmRelatedContentAction(option.action)"
          >
            {{ option.label }}
          </el-button>
          <el-button
            v-if="selectionActionOptions.length === 0"
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
      :defaultForm="runtimeDefaultForm"
      :listKey="listConfig?.listKey"
      :list-release-id="listConfig?.releaseId"
      :list-release-version="listConfig?.publishedVersion"
      :list-release-resolution-token="releaseResolutionToken"
      :entity-status-options="entityStatusOptions"
      @success="loadDataList"
    />
    <EntityApprovalDialog
      ref="approvalDialogRef"
      :entityCode="entityCode"
      :defaultForm="runtimeDefaultForm"
      :entityDefinition="entityDefinition"
      :entityFields="entityFields"
      :listKey="listConfig?.listKey"
      :list-release-id="listConfig?.releaseId"
      :list-release-version="listConfig?.publishedVersion"
      :list-release-resolution-token="releaseResolutionToken"
      :entity-status-options="entityStatusOptions"
      @success="loadDataList"
    />
    <EntityRecordVersionDrawer
      ref="versionDrawerRef"
      :entityCode="entityCode"
    />
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick, toRefs } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityApi, entityDataApi } from '@/api/entity'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
import { uiEventBindingApi } from '@/api/uiConfig'
import { applySelectionReturnMappings } from '@/utils/selectionReturnMappings'
import { getFormForNewData } from '@/api/entityFormResolve'
import { useUserStore } from '@/stores/user'
import request from '@/utils/request'
import { getEntityStatusList } from '@/api/entityStatus'
import { getItemTreeByDictCode } from '@/api/system/dict'
import { getCustomListComponent, hasCustomListComponent } from '@/utils/customComponentRegistry.js'
import {
  canExecuteAction,
  getActionCapabilityReason,
  hasButtonPermission
} from '@/utils/listButtonPermission'
import {
  buildListRequestFilters,
  formatDateValue,
  getCellValue,
  isReferenceListField
} from '@/shared/list-runtime'
import { safeParseConfig } from '@/shared/config-runtime'
import { filterRelatedContentButtons } from '@/shared/related-content-runtime'
import { withListButtonTypeDefault } from '@/shared/list-config-design'
import { loadExplicitListButtonForm } from '@/shared/list-button-form-runtime'
import {
  buildEntityStatusMap,
  getEffectiveEntityStatusOptions,
  resolveEntityStatusLabel,
  withEntityStatusFieldOptions
} from '@/shared/entity-status-runtime'
import EntityDataSearchForm from './components/EntityDataSearchForm.vue'
import EntityDataTable from './components/EntityDataTable.vue'
import EntityDataFormDialog from './components/EntityDataFormDialog.vue'
import EntityApprovalDialog from './components/approval/EntityApprovalDialog.vue'
import EntityRecordVersionDrawer from './components/EntityRecordVersionDrawer.vue'
import { useEntityDataSelectionState } from './composables/useEntityDataSelectionState'
import PageState from '@/components/PageState.vue'
import RelatedContentRuntime from '@/components/related-content/RelatedContentRuntime.vue'
import RuntimeVersionDiagnostics from '@/components/RuntimeVersionDiagnostics.vue'
import { formatRuntimeCodeVersion } from '@/shared/runtime-diagnostics'
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const props = withDefaults(defineProps<{
  entityCode?: string
  listKey?: string
  releaseId?: string
  releaseVersion?: number | null
  releaseResolutionToken?: string
  viewCompositionContextToken?: string
  viewCompositionTraversalToken?: string
  relatedContentActions?: string[]
  scene?: string
  context?: Record<string, any>
  selectionMode?: 'NONE' | 'SINGLE' | 'MULTIPLE'
  selectionActionOptions?: Array<{
    action: string
    label: string
    type?: string
  }>
  initialSelectedRows?: any[]
  embedded?: boolean
  showSearch?: boolean
  showPagination?: boolean
  showToolbar?: boolean
  showRowActions?: boolean
  fixedFilters?: Record<string, any>
  createInitialData?: Record<string, any>
  createContext?: Record<string, any>
  pageSize?: number
  maxHeight?: number
  defaultForm?: Record<string, any> | null
  allowDefaultFormResolve?: boolean
}>(), {
  entityCode: '',
  listKey: '',
  releaseId: '',
  releaseVersion: null,
  releaseResolutionToken: '',
  viewCompositionContextToken: '',
  viewCompositionTraversalToken: '',
  context: () => ({}),
  selectionMode: 'NONE',
  selectionActionOptions: () => [],
  initialSelectedRows: () => [],
  embedded: false,
  showSearch: true,
  showPagination: true,
  showToolbar: true,
  showRowActions: true,
  fixedFilters: () => ({}),
  createInitialData: () => ({}),
  createContext: () => ({}),
  pageSize: 10,
  maxHeight: 420,
  defaultForm: null,
  allowDefaultFormResolve: true
})

const {
  embedded,
  showSearch,
  showPagination,
  maxHeight
} = toRefs(props)

const emit = defineEmits<{
  confirm: [rows: any[]]
  cancel: []
  'selection-action': [action: string, rows: any[]]
  'selection-change': [rows: any[]]
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
const hasRelatedContentActionScope = computed(() =>
  Array.isArray(props.relatedContentActions)
)
const listConfig = ref<any>(null), listConfigFields = ref<any[]>([])
const publishedRelatedContents = computed(() =>
  (listConfig.value?.viewCompositions || []).filter(
    (item: any) => item?.config?.enabled !== false
  )
)
const pageSectionCompositions = computed(() =>
  publishedRelatedContents.value.filter((item: any) =>
    String(item?.anchorType || '').toUpperCase() === 'PAGE_SECTION'
  )
)
const rowExpandCompositions = computed(() =>
  publishedRelatedContents.value.filter((item: any) =>
    String(item?.anchorType || '').toUpperCase() === 'ROW_EXPAND'
  )
)
const relatedRowActionCompositions = computed(() =>
  publishedRelatedContents.value.filter((item: any) =>
    String(item?.anchorType || '').toUpperCase() === 'ROW_ACTION'
  )
)
const relatedToolbarActionCompositions = computed(() =>
  publishedRelatedContents.value.filter((item: any) =>
    String(item?.anchorType || '').toUpperCase() === 'TOOLBAR_ACTION'
  )
)
const viewCompositionTraversalToken = computed(() => String(
  props.viewCompositionTraversalToken
  || props.context?.viewCompositionTraversalToken
  || ''
))
const relatedContentRuntimeContext = computed(() => ({
  ...props.context,
  viewCompositionTraversalToken:
    viewCompositionTraversalToken.value
}))
const { effectiveSelectionMode, selectionScene, selectedRows } =
  useEntityDataSelectionState(props, runtimeScene, listConfig)
// 页级关联内容把当前选中行作为可信解析的来源标识；服务端仍会按 ID
// 重新读取并应用数据范围，前端整行不会成为筛选或授权依据。
const listSourceRecordId = computed(() => String(
  props.context?.sourceRecordId
  || props.context?.recordId
  || selectedRows.value[0]?.id
  || ''
))
const runtimeSelectionMode = computed(() => {
  if (effectiveSelectionMode.value !== 'NONE') {
    return effectiveSelectionMode.value
  }
  return pageSectionCompositions.value.length > 0 ? 'SINGLE' : 'NONE'
})
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
const loadedDefaultForm = ref<any>(null)
const runtimeDefaultForm = computed(() =>
  props.defaultForm || loadedDefaultForm.value)
const createFormLoading = ref(false)
const dictOptionMap = ref<Record<string, any[]>>({})

const formDialogRef = ref<InstanceType<typeof EntityDataFormDialog>>()
const approvalDialogRef = ref<InstanceType<typeof EntityApprovalDialog>>()
const versionDrawerRef = ref<InstanceType<typeof EntityRecordVersionDrawer>>()
// 计算属性
const entityName = computed(() => entityDefinition.value?.entityName)
const showPageRuntimeDiagnostics = computed(() =>
  !props.embedded && runtimeScene.value === 'PAGE'
)
const listRuntimeDiagnosticEntries = computed(() => [{
  label: '列表',
  value: formatRuntimeCodeVersion(
    listConfig.value?.listKey,
    listConfig.value?.publishedVersion
  )
}])
const listRuntimeDiagnosticCopyEntries = computed(() => [
  ...listRuntimeDiagnosticEntries.value
])
const listRuntimeDiagnosticResetKey = computed(() => [
  listConfig.value?.listKey || '',
  listConfig.value?.publishedVersion ?? '',
  listConfig.value?.releaseId || ''
].join(':'))
const isSystemEntity = computed(() => entityDefinition.value?.storageMode === 'SYSTEM')
const entityViewPermission = computed(() =>
  `entity:${String(entityCode.value || '').trim().toLowerCase()}:view`)
const canViewVersions = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || (userStore.permissions.includes('entity:version:record:view')
    && userStore.permissions.includes(entityViewPermission.value)))
// 查询字段（使用列表配置）
const queryFields = computed(() => {
  if (listConfigFields.value.length > 0) {
    return listConfigFields.value
      .filter((f: any) => f.isQuery)
      .map((f: any) => {
        const originField = entityFields.value.find((ef: any) => ef.fieldCode === f.fieldCode)
        const queryConfig = safeParseConfig(f.queryConfig)
        const dictType = originField?.dictType || f.dictType
        const dictOptions = dictType ? dictOptionMap.value[dictType] : null
        return withEntityStatusFieldOptions({
          ...f,
          componentType: queryConfig.componentType || originField?.componentType || f.componentType,
          placeholder: queryConfig.placeholder || f.placeholder,
          defaultValue: queryConfig.defaultValue,
          fieldType: originField?.fieldType || f.fieldType || 'STRING',
          dictType,
          optionsJson: dictOptions?.length
            ? JSON.stringify(dictOptions)
            : originField?.optionsJson || f.optionsJson,
          refEntityType: originField?.refEntityType,
          refEntityId: originField?.refEntityId,
          queryType: f.queryType || 'LIKE'
        }, entityStatusOptions.value, { allowMultiple: true })
      })
      .filter((f: any) => !['SUB_FORM', 'SUB_LIST'].includes((f.componentType || f.fieldType || '').toUpperCase()))
  }
  return entityFields.value
    .filter((f: any) => {
      const type = (f.componentType || f.fieldType || '').toUpperCase()
      return f.runtimeReadable !== false
        && !['SUB_FORM', 'SUB_LIST'].includes(type)
    })
    .map((field: any) => {
      const dictOptions = field.dictType
        ? dictOptionMap.value[field.dictType]
        : null
      return withEntityStatusFieldOptions(
        dictOptions?.length
          ? { ...field, optionsJson: JSON.stringify(dictOptions) }
          : field,
        entityStatusOptions.value
      )
    })
})
// 列表显示字段（使用列表配置）
const listFields = computed(() => {
  if (listConfigFields.value.length > 0) {
    return listConfigFields.value
      .filter((f: any) =>
        f.showInList
        && !['SUB_FORM', 'SUB_LIST'].includes(
          String(f.fieldType || '').toUpperCase()
        )
      )
      .map((f: any) => {
        const originField = entityFields.value.find((ef: any) => ef.fieldCode === f.fieldCode)
        const dictType = originField?.dictType || f.dictType
        const dictOptions = dictType ? dictOptionMap.value[dictType] : null
        return {
          ...f,
          fieldType: originField?.fieldType || 'STRING',
          dictType,
          optionsJson: dictOptions?.length
            ? JSON.stringify(dictOptions)
            : originField?.optionsJson || f.optionsJson,
          refEntityType: originField?.refEntityType,
          refEntityId: originField?.refEntityId
        }
      })
  }
  return entityFields.value
    .filter((f: any) =>
      f.runtimeReadable !== false
      && !['SUB_FORM', 'SUB_LIST'].includes(
        String(f.fieldType || '').toUpperCase()
      )
    )
    .map((field: any) => {
      const dictOptions = field.dictType
        ? dictOptionMap.value[field.dictType]
        : null
      return dictOptions?.length
        ? { ...field, optionsJson: JSON.stringify(dictOptions) }
        : field
    })
})

// 是否使用列表配置
const useListConfig = computed(() => listConfigFields.value.length > 0)
// 自定义列表组件名
// embedded 只是布局模式，不是降级渲染模式。Embed 入口会注册与
// Flow 主应用相同的扩展 registry，因此原生自定义列表也必须照常挂载。
const customListComponent = computed(() =>
  listConfig.value?.customComponent || ''
)
const usesCustomListComponent = computed(() => Boolean(
  customListComponent.value && hasCustomListComponent(customListComponent.value)
))

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
  versions: canViewVersions.value ? handleVersions : undefined,
  canViewVersions: canViewVersions.value,
  exportData: handleExport,
  canAction,
  getActionReason,
  entityStatusMap: entityStatusMap.value,
  entityStatusOptions: entityStatusOptions.value,
  getStatusText
}))
const listReleaseContext = computed(() => ({
  releaseId: listConfig.value?.releaseId,
  releaseVersion: listConfig.value?.publishedVersion,
  releaseResolutionToken: props.releaseResolutionToken || undefined
}))
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
  if (props.embedded && !props.showToolbar) return []
  const DEFAULT_TOOLBAR_BUTTONS = [
    { key: 'create', type: 'built-in', label: '新增数据', icon: 'Plus', buttonType: 'primary', sort: 1, enabled: true, perm: '' },
    { key: 'exportSelected', type: 'built-in', label: '导出选中', icon: 'Download', buttonType: 'default', sort: 2, enabled: true, perm: '' },
    { key: 'exportAll', type: 'built-in', label: '导出全部', icon: 'Download', buttonType: 'default', sort: 3, enabled: true, perm: '' },
    { key: 'batchDelete', type: 'built-in', label: '批量删除', icon: 'Delete', buttonType: 'danger', sort: 4, enabled: true, perm: '' }
  ]
  const config = safeParseConfig(listConfig.value?.toolbarConfig, null)
  const buttons = (config && config.length > 0 ? config : DEFAULT_TOOLBAR_BUTTONS.map((b: any) => ({ ...b })))
    .map((button: any) => withListButtonTypeDefault(button))
    .filter((b: any) => b.enabled !== false)
    .filter((b: any) => hasButtonPermission(b))
    .filter((b: any) => {
      if (b.key === 'batchDelete' || b.key === 'exportSelected') return true
      return listConfig.value?.toolbarCapabilities?.[b.key]?.visible !== false
    })
    .sort((a: any, b: any) => buttonOrder(a) - buttonOrder(b))
  return hasRelatedContentActionScope.value
    ? filterRelatedContentButtons(
        buttons,
        props.relatedContentActions,
        'TOOLBAR'
      )
    : buttons
})
// 操作列按钮（按配置 + 权限过滤）
const rowActionButtons = computed(() => {
  if (selectionScene.value) return []
  if (props.embedded && !props.showRowActions) return []
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
  const config = safeParseConfig(listConfig.value?.rowActionConfig, null)
  const buttons = (config && config.length > 0 ? config : DEFAULT_ROW_ACTION_BUTTONS.map((b: any) => ({ ...b })))
    .map((button: any) => withListButtonTypeDefault(button))
    .filter((b: any) => b.enabled !== false)
    .filter((b: any) => hasButtonPermission(b))
    .sort((a: any, b: any) => buttonOrder(a) - buttonOrder(b))
  return hasRelatedContentActionScope.value
    ? filterRelatedContentButtons(
        buttons,
        props.relatedContentActions,
        'ROW'
      )
    : buttons
})
// 是否显示选择列
const showSelectionColumn = computed(() => {
  if (isSystemEntity.value) return false
  return selectionScene.value
    || runtimeSelectionMode.value !== 'NONE'
    || toolbarButtons.value.some((b: any) => b.key === 'exportSelected' || b.key === 'batchDelete')
})
// 引用实体名称缓存
const refEntityNameMap = ref<Record<string, string>>({})

function flattenDictItems(items: any[]): any[] {
  return (items || []).flatMap((item: any) => [
    {
      value: item.itemCode,
      label: item.itemLabel,
      disabled: item.status !== '0'
    },
    ...flattenDictItems(item.children || [])
  ])
}

watch(
  () => entityFields.value
    .map((field: any) => field.dictType)
    .filter(Boolean),
  async (dictCodes: string[]) => {
    const uniqueCodes = [...new Set(dictCodes)]
    const entries = await Promise.all(uniqueCodes.map(async dictCode => {
      try {
        const items = await getItemTreeByDictCode(dictCode)
        return [dictCode, flattenDictItems(items || [])]
      } catch {
        return [dictCode, []]
      }
    }))
    dictOptionMap.value = Object.fromEntries(entries)
  },
  { immediate: true }
)

// 加载引用实体名称
async function loadRefEntityNames() {
  if (!dataList.value.length) return
  const sourceFields = listFields.value.length > 0 ? listFields.value : entityFields.value
  if (!sourceFields.length) return

  const refFields = sourceFields.filter((field: any) =>
    isReferenceListField(field)
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
      request
        .get(`/entity-selector/${entityType}/batch?${params}`)
        .then((records: any[]) => {
          for (const item of records || []) {
            const cacheKey = `${groupKey}:${item.id}`
            refEntityNameMap.value[cacheKey] =
              item.name || item.code || item.id
          }
        })
        .catch(err => console.error('加载引用实体名称失败:', err))
    )
  }
  await Promise.all(promises)
}
// 实体状态码 -> 状态名称映射
const entityStatusOptions = ref<any[]>(getEffectiveEntityStatusOptions())
const entityStatusMap = ref<Record<string, string>>(
  buildEntityStatusMap(entityStatusOptions.value)
)

async function loadEntityStatusMap() {
  if (!entityCode.value) return
  try {
    const list = await getEntityStatusList(entityCode.value, {
      viewCompositionTraversalToken:
        viewCompositionTraversalToken.value || undefined
    })
    entityStatusOptions.value = getEffectiveEntityStatusOptions(list || [])
    entityStatusMap.value = buildEntityStatusMap(entityStatusOptions.value)
  } catch (e) {
    // 已签名的关联目标必须完整读取它自己的状态定义；
    // 不能回退根实体默认值，否则会与 Flow 原生界面不一致。
    if (viewCompositionTraversalToken.value) throw e
    entityStatusOptions.value = getEffectiveEntityStatusOptions()
    entityStatusMap.value = buildEntityStatusMap(entityStatusOptions.value)
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
  return resolveEntityStatusLabel(status, entityStatusMap.value)
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
  refEntityNameMap.value = {}
  dictOptionMap.value = {}
  total.value = 0
  loadedDefaultForm.value = null
  clearQueryForm()
  try {
    const res = await entityApi.getByCode(entityCode.value, {
      viewCompositionTraversalToken:
        viewCompositionTraversalToken.value || undefined
    })
    entityDefinition.value = res || {}
    entityFields.value = res?.fields || []
    await loadListConfig()
    if (!props.embedded
        || toolbarButtons.value.length > 0
        || rowActionButtons.value.length > 0) {
      await loadDefaultForm()
    }
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
      runtimeScene.value,
      {
        releaseId: props.releaseId,
        releaseVersion: props.releaseVersion,
        releaseResolutionToken: props.releaseResolutionToken,
        viewCompositionContextToken: props.viewCompositionContextToken
      }
    )
    listConfig.value = schema || null
    listConfigFields.value = schema?.fields || []
    const configuredPageSize = Number(safeParseConfig(schema?.viewConfig)?.pagination?.pageSize)
    if (props.embedded && Number(props.pageSize) > 0) {
      pageSize.value = Number(props.pageSize)
    } else if (configuredPageSize > 0) {
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
  if (props.defaultForm) {
    loadedDefaultForm.value = null
    return true
  }
  // Embed Session 已在启动时完成默认表单解析；没有固定表单就代表该
  // Session 不允许新增/编辑，不能再次查询当前 ACTIVE，否则旧会话会在
  // 管理员发布新表单后发生版本漂移。普通 Flow 页面仍保留原有动态解析。
  if (!props.allowDefaultFormResolve) {
    loadedDefaultForm.value = null
    if (notifyOnError) {
      ElMessage.error('当前固定列表版本没有可用的默认表单')
    }
    return false
  }
  try {
    const res = await getFormForNewData(entityCode.value, { silentError: true })
    loadedDefaultForm.value = res || null
    return true
  } catch (e) {
    console.error('加载新增数据表单失败:', e)
    loadedDefaultForm.value = null
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
    const params = buildRequestFilters()
    const res = await entityListRuntimeApi.query(
      entityCode.value,
      runtimeListKey.value,
      {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        scene: runtimeScene.value,
        releaseId: listConfig.value?.releaseId,
        releaseVersion: listConfig.value?.publishedVersion,
        releaseResolutionToken: props.releaseResolutionToken,
        viewCompositionContextToken: props.viewCompositionContextToken,
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
  clearQueryForm()
  queryFields.value.forEach((field: any) => {
    queryForm[field.fieldCode] = field.defaultValue ?? ''
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
    await entityDataApi.delete(
      entityCode.value,
      row.id,
      listConfig.value?.listKey,
      listReleaseContext.value
    )
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
      listConfig.value?.listKey,
      listReleaseContext.value
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
    const condition = buildRequestFilters()
    const ids = exportType === 'SELECTED' ? selectedRows.value.map(r => r.id) : []
    const res = await entityDataApi.exportData(entityCode.value, {
      exportType,
      ids,
      listKey: listConfig.value?.listKey,
      ...listReleaseContext.value,
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
      releaseId: listConfig.value.releaseId,
      releaseVersion: listConfig.value.publishedVersion,
      releaseResolutionToken: props.releaseResolutionToken,
      viewCompositionTraversalToken:
        viewCompositionTraversalToken.value || undefined,
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
        ...(props.context || {}),
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
const handleCreate = async (button?: any) => {
  if (createFormLoading.value) return
  createFormLoading.value = true
  try {
    let form = null
    if (button?.targetFormId) {
      form = await loadRuntimeButtonForm(button, 'TOOLBAR')
    } else {
      const loaded = await loadDefaultForm(true)
      if (!loaded) return
    }
    await nextTick()
    await formDialogRef.value?.openCreate({
      form,
      initialData: props.createInitialData,
      parameters: {
        ...(props.context?.parameters || {}),
        ...(props.createContext?.parameters || {}),
        ...(props.createContext?.params || {})
      },
      context: {
        ...relatedContentRuntimeContext.value,
        ...(props.createContext || {})
      }
    })
  } catch (error: any) {
    ElMessage.error(error?.message || '加载按钮指定表单失败')
  } finally {
    createFormLoading.value = false
  }
}
// 打开编辑弹窗
const handleEdit = async (row: any, button?: any) => {
  try {
    let form = null
    if (button?.targetFormId) {
      form = await loadRuntimeButtonForm(button, 'ROW')
    } else {
      const loaded = await loadDefaultForm(true)
      if (!loaded) return
    }
    // 无论使用默认表单还是按钮固定表单，都等 props/context 完成同一轮更新后再打开；
    // 这样关联子列表不会把上一次的发布快照或 traversal token 带进新弹窗。
    await nextTick()
    await formDialogRef.value?.openEdit(row, {
      form,
      context: relatedContentRuntimeContext.value
    })
  } catch (error: any) {
    ElMessage.error(error?.message || '加载按钮指定表单失败')
  }
}

// 打开查看弹窗
const handleView = async (row: any, button?: any) => {
  try {
    const form = await loadRuntimeButtonForm(button, 'ROW')
    await approvalDialogRef.value?.openView(row, {
      form,
      context: relatedContentRuntimeContext.value
    })
  } catch (error: any) {
    ElMessage.error(error?.message || '加载按钮指定表单失败')
  }
}

// 打开审批弹窗
const handleApprove = async (row: any, button?: any) => {
  try {
    const form = await loadRuntimeButtonForm(button, 'ROW')
    await approvalDialogRef.value?.openApprove(row, {
      form,
      context: relatedContentRuntimeContext.value,
      // 实体列表的审批目标必须来自服务端动作能力；即便旧响应或自定义数据源
      // 缺少 actionCapabilities，也不得回退到记录级 currentTaskId。
      requireActionCapability: true
    })
  } catch (error: any) {
    ElMessage.error(error?.message || '加载按钮指定表单失败')
  }
}

async function loadRuntimeButtonForm(
  button: any,
  placement: 'TOOLBAR' | 'ROW'
) {
  if (!button?.targetFormId) {
    return loadExplicitListButtonForm(button)
  }
  await loadListConfig()
  const buttons = placement === 'TOOLBAR'
    ? toolbarButtons.value
    : rowActionButtons.value
  const runtimeButton = buttons.find((candidate: any) =>
    String(candidate?.key || '') === String(button?.key || '')
    && String(candidate?.targetFormId || '')
      === String(button?.targetFormId || '')
  )
  if (!runtimeButton) {
    throw new Error('列表按钮运行时配置已更新，请刷新页面后重试')
  }
  return loadExplicitListButtonForm(runtimeButton)
}

const handleVersions = (row: any) => {
  if (!canViewVersions.value) return
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

/**
 * 关联内容动作只把选中记录 ID 交给父组件，整行数据不会成为权威输入。
 * 这里仍传递原行对象是为了兼容 Vue 事件形态；动作桥会立即裁剪为 ID。
 */
const confirmRelatedContentAction = (action: string) => {
  const rows = effectiveSelectionMode.value === 'SINGLE'
    ? selectedRows.value.slice(0, 1)
    : selectedRows.value
  emit('selection-action', action, rows)
}
// 监听实体编码变化
watch(() => [
  entityCode.value,
  runtimeListKey.value,
  props.releaseId,
  props.releaseVersion,
  props.releaseResolutionToken
], () => {
  if (entityCode.value && runtimeListKey.value) {
    loadEntityDefinition()
  }
}, { immediate: true })

watch(
  () => runtimeInputFingerprint(),
  (value, previous) => {
    if (value === previous
        || loading.value
        || !entityDefinition.value?.id) {
      return
    }
    pageNum.value = 1
    loadDataList()
  }
)

function buildRequestFilters() {
  return buildListRequestFilters(
    queryForm,
    queryFields.value,
    props.fixedFilters
  )
}

function clearQueryForm() {
  Object.keys(queryForm).forEach(key => {
    delete queryForm[key]
  })
}

function runtimeInputFingerprint() {
  try {
    return JSON.stringify(props.fixedFilters || {})
  } catch {
    return 'unserializable-fixed-filters'
  }
}

watch(
  () => props.pageSize,
  value => {
    if (!props.embedded) return
    const next = Number(value)
    if (!Number.isFinite(next) || next <= 0 || next === pageSize.value) return
    pageSize.value = next
    pageNum.value = 1
    if (entityDefinition.value?.id) loadDataList()
  }
)

watch(
  () => selectedRows.value.map((row: any) => String(row?.id || '')).join('\u0000'),
  () => {
    // 这个事件只在 iframe 内交给 NativeEmbeddedListPage；跨域 Bridge
    // 会在 controller 边界再裁剪为 id + 空 values，不会泄露原生整行。
    emit('selection-change', [...selectedRows.value])
  }
)

function focus() {
  const control = globalThis.document?.querySelector?.(
    '.entity-data-list button:not([disabled]), '
      + '.entity-data-list input:not([disabled]), '
      + '.entity-data-list [tabindex="0"]'
  )
  control?.focus?.()
}

defineExpose({ focus, reload: loadDataList })
</script>
<style scoped lang="scss">
.entity-data-list {
  padding: 10px;

  &.embedded {
    padding: 0;
  }
  
  .loading-container {
    padding: 10px;
  }
  .list-runtime-diagnostics {
    flex: 1 1 auto;
    min-width: 32px;
  }
  .list-runtime-diagnostics :deep(.runtime-version-diagnostics__trigger) {
    width: 100%;
  }
  .list-runtime-diagnostics__trigger {
    display: block;
    min-height: 32px;
  }
  .list-runtime-diagnostics--fallback {
    margin: 0 4px 8px;
  }
  .master-detail-hint {
    margin-bottom: 12px;
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
