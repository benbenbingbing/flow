<template>
  <div class="extension-page">
    <el-card class="search-card" shadow="never">
      <el-form
        class="extension-search-form"
        :model="filters"
        inline
        label-width="72px"
        @submit.prevent="handleSearch"
      >
        <el-form-item label="扩展类型" class="filter-select">
          <el-select v-model="filters.capabilityType" clearable placeholder="全部类型">
            <el-option
              v-for="option in typeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字" class="filter-keyword">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="名称、编码或实现类"
          />
        </el-form-item>
        <el-form-item v-if="searchExpanded" label="目录状态" class="filter-select">
          <el-select v-model="filters.status" clearable placeholder="全部状态">
            <el-option label="已启用" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
            <el-option label="待纳管" value="DISCOVERED" />
            <el-option label="实现缺失" value="MISSING" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="searchExpanded" label="实现归属" class="filter-select">
          <el-select
            v-model="filters.implementationOrigin"
            clearable
            placeholder="全部归属"
          >
            <el-option label="平台内置" value="PLATFORM" />
            <el-option label="项目自定义" value="CUSTOM" />
            <el-option label="暂无法识别" value="UNKNOWN" />
          </el-select>
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" native-type="submit">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button link type="primary" @click="searchExpanded = !searchExpanded">
            {{ searchExpanded ? '收起' : '展开' }}
            <el-icon>
              <ArrowUp v-if="searchExpanded" />
              <ArrowDown v-else />
            </el-icon>
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="extension-panel" shadow="never">
      <div class="table-toolbar">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button
          v-if="canUpdate && (!filters.capabilityType || filters.capabilityType === 'INTERFACE')"
          type="primary"
          @click="openCreateInterface"
        >
          <el-icon><Plus /></el-icon>
          新增扩展接口
        </el-button>
        <el-button
          v-if="canUpdate && filters.capabilityType !== 'INTERFACE'"
          type="primary"
          plain
          @click="openCreateUi"
        >
          <el-icon><Plus /></el-icon>
          新增 UI 扩展
        </el-button>
      </div>

      <PageState
        v-if="loadError"
        type="error"
        title="扩展目录加载失败"
        :description="loadError"
        retryable
        compact
        @retry="load"
      />

      <template v-else>
        <el-table
          v-loading="loading"
          :data="rows"
          border
          stripe
          row-key="rowKey"
          class="extension-table"
        >
          <el-table-column label="类型" width="122">
            <template #default="{ row }">
              <el-tag effect="plain" type="info">
                {{ typeLabel(row.capabilityType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="实现归属" width="116" align="center">
            <template #default="{ row }">
              <el-tooltip
                :content="originDescription(row.implementationOrigin)"
                placement="top"
              >
                <el-tag :type="originTagType(row.implementationOrigin)" effect="plain">
                  {{ originLabel(row.implementationOrigin) }}
                </el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="名称" min-width="210">
            <template #default="{ row }">
              <div class="primary-line">{{ row.displayName || row.key }}</div>
              <div class="meta-line">{{ row.key }}</div>
            </template>
          </el-table-column>
          <el-table-column label="实现" min-width="230">
            <template #default="{ row }">
              <div class="primary-line">
                {{ sourceLabel(row.sourceType) }}
              </div>
              <div class="meta-line" :title="row.implementationClass || row.sourceName">
                {{ row.implementationClass || row.sourceName || '-' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="120">
            <template #default="{ row }">
              <template v-if="isInterface(row)">
                <div>接口定义</div>
                <div class="meta-line">修订 {{ row.revision ?? 0 }}</div>
              </template>
              <template v-else>
                <div>实现 v{{ row.implementationVersion || 1 }}</div>
              </template>
              <div v-if="!isInterface(row) && isUi(row)" class="meta-line">
                快照 v{{ row.snapshotVersion || 1 }}
              </div>
              <div v-else-if="!isInterface(row)" class="meta-line">
                契约 v{{ row.contractVersion || 1 }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="适用范围" min-width="170">
            <template #default="{ row }">
              <div>{{ scopeSummary(row) }}</div>
              <div v-if="capabilitySummary(row)" class="meta-line">
                {{ capabilitySummary(row) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="参数" width="110" align="center">
            <template #default="{ row }">
              <el-tag
                :type="schemaSize(row) ? 'success' : 'info'"
                effect="plain"
              >
                {{ schemaSize(row) ? `${schemaSize(row)} 项` : '无' }}
              </el-tag>
              <div v-if="row.dynamicExtraParams" class="meta-line">允许扩展</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="112" align="center">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
              <div v-if="isUi(row)" class="meta-line">
                {{ row.available ? '当前构建已加载' : '当前构建未加载' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="isInterface(row) && canTest"
                link
                type="primary"
                @click="openTestInterface(row)"
              >
                调试
              </el-button>
              <el-button
                v-if="canUpdate && row.available !== false"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ row.configured ? '编辑' : '纳管' }}
              </el-button>
              <el-button
                v-if="isInterface(row) && canUpdate"
                link
                type="danger"
                @click="removeInterface(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="pageInfo.pageNum"
          v-model:page-size="pageInfo.pageSize"
          :total="pageInfo.total"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </template>
    </el-card>

    <ExtensionCatalogEditorDialog ref="catalogEditorRef" @saved="load" />

    <el-drawer
      v-model="detailVisible"
      title="扩展详情"
      size="min(720px, 94vw)"
      destroy-on-close
    >
      <div v-if="detail" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="类型">
            {{ typeLabel(detail.capabilityType) }}
          </el-descriptions-item>
          <el-descriptions-item label="实现归属">
            {{ originLabel(detail.implementationOrigin) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ statusLabel(detail.status) }}
          </el-descriptions-item>
          <el-descriptions-item label="名称">
            {{ detail.displayName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="编码">
            {{ detail.key || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="实现来源" :span="2">
            {{ detail.implementationClass || detail.sourceName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">
            {{ detail.description || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <h3>固定契约</h3>
          <pre class="json-viewer">{{ formatJson(contractDetail(detail)) }}</pre>
        </section>
        <section class="detail-section">
          <h3>配置 Schema</h3>
          <pre class="json-viewer">{{ formatJson(detail.configSchema || detail.extraParamSchema || {}) }}</pre>
        </section>
      </div>
    </el-drawer>

    <InterfaceExtensionEditorDialog
      ref="interfaceEditorRef"
      :catalog="interfaceCatalog"
      @saved="load"
    />
    <InterfaceExtensionTestDialog
      ref="interfaceTestRef"
      :forms="testForms"
      :lists="testLists"
      :entity-id="testEntityId"
      :entity-code="testEntityCode"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown, ArrowUp, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { extensionCatalogApi } from '@/api/system/extension'
import { uiExtensionApi } from '@/api/uiConfig'
import { entityApi } from '@/api/entity'
import { getFormById, getFormsByEntity } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import ExtensionCatalogEditorDialog from '@/components/ui-config/ExtensionCatalogEditorDialog.vue'
import InterfaceExtensionEditorDialog from '@/components/ui-config/InterfaceExtensionEditorDialog.vue'
import InterfaceExtensionTestDialog from '@/components/ui-config/InterfaceExtensionTestDialog.vue'
import { normalizeInterfaceExtension } from '@/components/ui-config/interfaceExtensionModel'
import {
  getManagedExtensionManifest,
  isPlatformBuiltInUiExtension
} from '@/extensions/manifest'
import { useUserStore } from '@/stores/user'
import {
  loadAllExtensionCatalogRows,
  paginateExtensionCatalogRows
} from '@/shared/extension-catalog-pagination'
const typeOptions = [
  { value: 'INTERFACE', label: '扩展接口' },
  { value: 'FLOW_ACTION', label: '流程动作' },
  { value: 'PERSON_RESOLVER', label: '人员接口' },
  { value: 'UI_FORM', label: '自定义表单' },
  { value: 'UI_LIST', label: '自定义列表' },
  { value: 'UI_NODE', label: '表单节点' },
  { value: 'UI_FIELD', label: '表单字段' }
]
const typeMap = Object.fromEntries(typeOptions.map(item => [item.value, item.label]))
const personUsageOptions = [
  { value: 'ASSIGNEE', label: '办理人' },
  { value: 'CANDIDATE', label: '候选人' },
  { value: 'MULTI_INSTANCE', label: '会签人员' },
  { value: 'CC', label: '知会人员' }
]
const route = useRoute()
const userStore = useUserStore()
const canUpdate = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:extension:update'))
const canTest = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:extension:test'))
const localManifest = getManagedExtensionManifest()
const filters = reactive({
  capabilityType: normalizeRouteType(route.query.type),
  keyword: '',
  status: '',
  implementationOrigin: ''
})
const pageInfo = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const rows = ref([])
const allCatalogRows = ref([])
const loading = ref(false)
const loadError = ref('')
const searchExpanded = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const interfaceCatalog = ref({})
const catalogEditorRef = ref(null)
const interfaceEditorRef = ref(null)
const interfaceTestRef = ref(null)
const testForms = ref([])
const testLists = ref([])
const testEntityId = ref('')
const testEntityCode = ref('')
let catalogLoadSequence = 0

function filterSnapshot() {
  return {
    capabilityType: filters.capabilityType || undefined,
    keyword: filters.keyword?.trim() || undefined,
    status: filters.status || undefined,
    implementationOrigin: filters.implementationOrigin || undefined
  }
}

function applyCatalogPagination() {
  const page = paginateExtensionCatalogRows(
    allCatalogRows.value,
    pageInfo.pageNum,
    pageInfo.pageSize
  )
  rows.value = page.list
  pageInfo.total = page.total
  pageInfo.pageNum = page.pageNum
  pageInfo.pageSize = page.pageSize
}

async function load() {
  const sequence = ++catalogLoadSequence
  const currentFilters = filterSnapshot()
  loading.value = true
  loadError.value = ''
  try {
    const [catalogRows, allDefinitions, catalog] = await Promise.all([
      loadAllExtensionCatalogRows(
        params => extensionCatalogApi.manage(params),
        currentFilters
      ),
      uiExtensionApi.list(),
      uiExtensionApi.catalog()
    ])
    // 只允许最后一次查询提交数据，防止慢响应覆盖新的筛选结果。
    if (sequence !== catalogLoadSequence) return
    interfaceCatalog.value = catalog || {}
    const interfaceDefinitionById = new Map((allDefinitions || [])
      .filter(item => item.extensionType === 'INTERFACE')
      .map(item => [String(item.id || item.extensionId), item]))
    const managedRows = catalogRows
      .filter(row => !isPlatformBuiltInUiExtension(
        row.capabilityType, row.key))
      .map(row => {
        if (row.capabilityType !== 'INTERFACE') return decorateRemote(row)
        const raw = interfaceDefinitionById.get(
          String(row.id || row.extensionId)
        ) || {}
        // 管理目录负责筛选；定义详情只补齐编辑接口所需的内部实现字段。
        return decorateInterface({ ...row, ...raw })
      })
    const remoteUiKeys = new Set((allDefinitions || []).map(item =>
      `UI_${item.extensionType}:${item.extensionKey}:${item.version || 1}`))
    allCatalogRows.value = [
      ...localOnlyRows(remoteUiKeys, currentFilters),
      ...managedRows
    ]
    applyCatalogPagination()
  } catch (error) {
    if (sequence !== catalogLoadSequence) return
    loadError.value = error?.message || '无法读取扩展目录，请重试。'
  } finally {
    if (sequence === catalogLoadSequence) {
      loading.value = false
    }
  }
}

function decorateInterface(value) {
  const item = normalizeInterfaceExtension(value)
  return {
    ...item,
    id: item.extensionId,
    rowKey: `INTERFACE:${item.extensionId}`,
    capabilityType: 'INTERFACE',
    key: item.extensionKey,
    sourceType: item.implementationType,
    sourceName: item.providerCode,
    implementationClass: item.providerCode || item.implementationType,
    configSchema: item.inputSchema,
    configured: true,
    available: true,
    description: ''
  }
}

function decorateRemote(row) {
  const local = isUi(row) ? findLocal(row) : null
  return {
    ...row,
    rowKey: `${row.capabilityType}:${row.key}:${row.implementationVersion || 1}:${row.id || 'discovered'}`,
    available: isUi(row) ? Boolean(local) : row.available,
    status: isUi(row) && !local && row.status === 'ACTIVE' ? 'MISSING' : row.status,
    description: row.description || local?.description || '',
    configSchema: row.configSchema ?? local?.configSchema ?? {},
    capabilities: row.capabilities ?? local?.capabilities ?? {},
    localManifest: local || null
  }
}

function localOnlyRows(remoteKeys, currentFilters) {
  return localManifest
    .filter(item => ['FORM', 'LIST', 'NODE', 'FIELD'].includes(item.type))
    .map(item => ({
      rowKey: `LOCAL:${item.id}`,
      id: null,
      capabilityType: `UI_${item.type}`,
      key: item.name,
      displayName: item.label,
      description: item.description,
      implementationVersion: item.version,
      snapshotVersion: item.snapshotVersion,
      contractVersion: 1,
      sourceType: 'FRONTEND_BUNDLE',
      sourceName: item.source,
      implementationClass: '',
      implementationOrigin: 'CUSTOM',
      status: 'DISCOVERED',
      configured: false,
      available: true,
      enabled: false,
      visibilityScope: 'GLOBAL',
      entityCodes: [],
      supportedModes: item.supportedModes || [],
      supportedNodeTypes: [],
      supportedBindings: [],
      configSchema: item.configSchema || [],
      capabilities: item.capabilities || {},
      dynamicExtraParams: item.capabilities?.dynamicExtraParams === true,
      localManifest: item
    }))
    .filter(row => !remoteKeys.has(
      `${row.capabilityType}:${row.key}:${row.implementationVersion || 1}`))
    .filter(row => !currentFilters.capabilityType
      || row.capabilityType === currentFilters.capabilityType)
    .filter(row => !currentFilters.status
      || row.status === currentFilters.status)
    .filter(row => !currentFilters.implementationOrigin
      || row.implementationOrigin === currentFilters.implementationOrigin)
    .filter(row => matchesKeyword(row, currentFilters.keyword))
}

function findLocal(row) {
  const type = String(row.capabilityType || '').replace(/^UI_/, '')
  return localManifest.find(item =>
    item.type === type
      && item.name === row.key
      && Number(item.version) === Number(row.implementationVersion || 1))
}

function matchesKeyword(row, keyword) {
  const value = String(keyword || '').trim().toLowerCase()
  if (!value) return true
  return [row.key, row.displayName, row.description, row.sourceName]
    .some(item => String(item || '').toLowerCase().includes(value))
}

function normalizeRouteType(value) {
  const normalized = String(value || '').toUpperCase()
  return typeOptions.some(item => item.value === normalized)
    ? normalized
    : ''
}

function handleSearch() {
  pageInfo.pageNum = 1
  load()
}

function handleReset() {
  Object.assign(filters, {
    capabilityType: '',
    keyword: '',
    status: '',
    implementationOrigin: ''
  })
  pageInfo.pageNum = 1
  load()
}

function handleSizeChange(pageSize) {
  pageInfo.pageSize = pageSize
  pageInfo.pageNum = 1
  applyCatalogPagination()
}

function handleCurrentChange(pageNum) {
  pageInfo.pageNum = pageNum
  applyCatalogPagination()
}

async function openEdit(row) {
  if (isInterface(row)) {
    await interfaceEditorRef.value?.openEdit(row)
    return
  }
  catalogEditorRef.value?.open(row)
}

function openCreateUi() {
  catalogEditorRef.value?.openCreateUi()
}

function openCreateInterface() {
  interfaceEditorRef.value?.openCreate()
}

/** 调试必须携带一个真实配置对象，以复用运行态的范围与权限校验。 */
async function openTestInterface(row) {
  try {
    const item = normalizeInterfaceExtension(row)
    let entityId = ''
    if (item.scopeType === 'FORM' && item.scopeId) {
      entityId = (await getFormById(item.scopeId))?.entityId || ''
    } else if (item.scopeType === 'LIST' && item.scopeId) {
      entityId = (await entityListConfigApi.getById(item.scopeId))?.entityId || ''
    } else if (item.scopeType === 'ENTITY') {
      entityId = item.scopeId || ''
    }
    if (!entityId) {
      const result = await entityApi.getOptions({
        pageNum: 1,
        pageSize: 1,
        storageMode: 'DYNAMIC'
      })
      const candidates = result?.list || result?.records || result || []
      entityId = candidates[0]?.id || ''
    }
    const entity = entityId ? await entityApi.getById(entityId) : null
    const [forms, lists] = entityId
      ? await Promise.all([
          getFormsByEntity(entityId),
          entityListConfigApi.getByEntityId(entityId)
        ])
      : [[], []]
    testEntityId.value = entityId
    testEntityCode.value = entity?.entityCode || ''
    testForms.value = Array.isArray(forms) ? forms : []
    testLists.value = Array.isArray(lists) ? lists : []
    interfaceTestRef.value?.open(item)
  } catch (error) {
    ElMessage.error(error?.message || '无法准备扩展接口调试上下文')
  }
}

async function removeInterface(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除扩展接口“${row.displayName || row.key}”？仍被已发布配置引用时服务端会拒绝删除。`,
      '删除扩展接口',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await uiExtensionApi.remove(row.id, row.revision)
    ElMessage.success('扩展接口已删除')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '删除扩展接口失败')
  }
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

function isUi(row) {
  return String(row?.capabilityType || '').startsWith('UI_')
}

function isInterface(row) {
  return row?.capabilityType === 'INTERFACE'
}

function typeLabel(value) {
  return typeMap[value] || value || '-'
}

function sourceLabel(value) {
  return {
    FRONTEND_BUNDLE: '前端构建',
    BACKEND_BEAN: '后端 Bean',
    DICTIONARY: '平台字典',
    STATIC_OPTIONS: '平台静态数据',
    REGISTERED_PROVIDER: '已注册 Provider',
    RUNTIME_CONTEXT: '运行时上下文',
    STRUCTURED_COMPUTE: '结构化计算'
  }[value] || value || '-'
}

function originLabel(value) {
  return {
    PLATFORM: '平台内置',
    CUSTOM: '项目自定义',
    UNKNOWN: '暂无法识别'
  }[value] || value || '-'
}

function originTagType(value) {
  return {
    PLATFORM: 'primary',
    CUSTOM: 'success',
    UNKNOWN: 'info'
  }[value] || 'info'
}

function originDescription(value) {
  return {
    PLATFORM: '底层执行实现由流程平台提供，不代表这条配置由平台创建',
    CUSTOM: '底层执行实现由项目或二次开发代码提供',
    UNKNOWN: '当前实现未加载、未声明归属或存在归属冲突，暂时无法可靠判断'
  }[value] || '未声明实现归属'
}

function statusLabel(value) {
  return {
    ACTIVE: '已启用',
    DISABLED: '已停用',
    DISCOVERED: '待纳管',
    MISSING: '实现缺失'
  }[value] || value || '-'
}

function statusType(value) {
  return {
    ACTIVE: 'success',
    DISABLED: 'info',
    DISCOVERED: 'warning',
    MISSING: 'danger'
  }[value] || 'info'
}

function scopeSummary(row) {
  if (isInterface(row)) {
    if (row.scopeType === 'GLOBAL') return '全局'
    const label = { ENTITY: '实体', FORM: '表单', LIST: '列表' }[row.scopeType]
      || row.scopeType
    return `${label} · ${row.scopeId || '未指定'}`
  }
  if (row.capabilityType === 'FLOW_ACTION') {
    if (row.visibilityScope === 'GLOBAL') return '全部实体'
    return row.entityCodes?.length ? `${row.entityCodes.length} 个实体` : '尚未指定实体'
  }
  if (row.capabilityType === 'PERSON_RESOLVER') return '全局受控接口'
  if (row.capabilityType === 'UI_FORM') {
    if (row.visibilityScope !== 'ENTITY') return '全部实体'
    return row.entityCodes?.length
      ? `指定 ${row.entityCodes.length} 个实体`
      : '尚未指定实体'
  }
  return '前端构建范围'
}

function capabilitySummary(row) {
  if (isInterface(row)) {
    const kind = row.interfaceKind === 'WRITE' ? '写接口' : '读接口'
    const context = {
      FORM: '表单上下文',
      LIST: '列表上下文',
      ENTITY: '实体上下文'
    }[row.interfaceContextType] || row.interfaceContextType
    return [kind, context].filter(Boolean).join(' · ')
  }
  if (row.capabilityType === 'PERSON_RESOLVER') {
    return [...(row.supportedUsages || [])]
      .map(value => personUsageOptions.find(item => item.value === value)?.label || value)
      .join('、')
  }
  if (row.capabilityType === 'FLOW_ACTION') {
    return [...(row.supportedExecutionModes || [])].join('、')
  }
  return [...(row.supportedModes || [])].join('、')
}

function schemaSize(row) {
  const schema = isInterface(row)
    ? row.inputSchema
    : row.configSchema ?? row.extraParamSchema
  if (Array.isArray(schema)) return schema.length
  if (schema && typeof schema === 'object') {
    if (schema.properties && typeof schema.properties === 'object') {
      return Object.keys(schema.properties).length
    }
    return Object.keys(schema).length
  }
  return 0
}

function contractDetail(row) {
  if (isInterface(row)) {
    return {
      interfaceKind: row.interfaceKind,
      interfaceContextType: row.interfaceContextType,
      scopeType: row.scopeType,
      scopeId: row.scopeId || null,
      implementationType: row.implementationType,
      providerCode: row.providerCode || null,
      providerOperationCode: row.providerOperationCode || null,
      executionPolicy: row.executionPolicy || {},
      inputSchema: row.inputSchema || {},
      outputSchema: row.outputSchema || {}
    }
  }
  return {
    contractVersion: row.contractVersion || 1,
    supportedUsages: row.supportedUsages || [],
    supportedTriggerTimings: row.supportedTriggerTimings || [],
    supportedExecutionModes: row.supportedExecutionModes || [],
    supportedModes: row.supportedModes || [],
    supportedNodeTypes: row.supportedNodeTypes || [],
    supportedBindings: row.supportedBindings || [],
    visibilityScope: row.visibilityScope || 'GLOBAL',
    entityCodes: row.entityCodes || [],
    dynamicExtraParams: row.dynamicExtraParams === true
  }
}

function formatJson(value) {
  return JSON.stringify(value ?? {}, null, 2)
}

watch(
  () => route.query.type,
  (value) => {
    const nextType = normalizeRouteType(value)
    if (nextType === filters.capabilityType) return
    filters.capabilityType = nextType
    pageInfo.pageNum = 1
    load()
  }
)
onMounted(load)
</script>

<style scoped>
.extension-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.extension-search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 12px;
}
.filter-select {
  width: 280px;
}
.filter-keyword {
  width: 420px;
}
.filter-select :deep(.el-select),
.filter-keyword :deep(.el-input) {
  width: 100%;
}

.search-actions {
  margin-left: auto;
}

.table-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.primary-line {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.meta-line {
  margin-top: 4px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.detail-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.json-viewer {
  max-height: 320px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--el-border-color);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 900px) {
  .filter-select,
  .filter-keyword {
    width: 100%;
  }
  .search-actions {
    margin-left: 0;
  }
}
</style>
