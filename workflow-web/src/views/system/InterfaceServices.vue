<template>
  <div class="interface-services-page">
    <div class="page-heading">
      <div>
        <h2>接口服务</h2>
        <p>统一管理服务定义与操作，并追踪服务在事件执行链中的引用。</p>
      </div>
      <el-button :loading="loading" title="刷新页面数据" @click="loadAll">
        <el-icon><Refresh /></el-icon>
      </el-button>
    </div>

    <el-tabs v-model="activeTab" class="main-tabs">
      <el-tab-pane label="接口服务" name="services">
        <div class="table-toolbar">
          <div class="toolbar-filters">
            <el-input
              v-model="keyword"
              clearable
              placeholder="搜索服务名称或编码"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="sourceTypeFilter" clearable placeholder="全部实现类型">
              <el-option
                v-for="option in sourceTypeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </div>
          <el-button v-if="canUpdateServices" type="primary" @click="openCreateService">
            <el-icon><Plus /></el-icon>
            新增接口服务
          </el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="filteredServices"
          row-key="id"
          border
          stripe
        >
          <el-table-column label="服务" min-width="230">
            <template #default="{ row }">
              <div class="primary-text">{{ row.sourceName }}</div>
              <div class="secondary-text">{{ row.sourceCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="实现" width="160">
            <template #default="{ row }">
              <el-tag effect="plain">{{ sourceTypeLabel(row.sourceType) }}</el-tag>
              <div v-if="row.providerCode" class="secondary-text">
                {{ row.providerCode }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="作用范围" width="150">
            <template #default="{ row }">
              {{ scopeLabel(row.scopeType) }}
              <div v-if="row.scopeId" class="secondary-text">
                {{ scopeObjectName(row) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="260">
            <template #default="{ row }">
              <div class="operation-list">
                <el-tag
                  v-for="operation in serviceOperations(row)"
                  :key="operation.code"
                  :type="operation.kind === 'WRITE' ? 'warning' : 'info'"
                  effect="plain"
                >
                  {{ operation.name }} · {{ contextTypeLabel(operation.contextType) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="策略" width="150">
            <template #default="{ row }">
              <div>{{ executionPolicy(row).timeoutMs || 3000 }} ms</div>
              <div class="secondary-text">
                缓存 {{ executionPolicy(row).cacheSeconds || 0 }} 秒
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled === false ? 'info' : 'success'">
                {{ row.enabled === false ? '停用' : '启用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right" align="center">
            <template #default="{ row }">
              <el-button
                v-if="canTestServices"
                link
                type="primary"
                @click="openTest(row)"
              >
                调试
              </el-button>
              <el-button
                v-if="canUpdateServices"
                link
                type="primary"
                @click="openEditService(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canUpdateServices"
                link
                type="danger"
                @click="removeService(row)"
              >
                删除
              </el-button>
              <span
                v-if="!canTestServices && !canUpdateServices"
                class="secondary-text"
              >
                只读
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="事件使用情况" name="usage" lazy>
        <InterfaceServiceUsagePanel
          :services="services"
          :can-configure-entity-events="canConfigureEntityEvents"
          @configure="goToReferenceConfiguration"
        />
      </el-tab-pane>
    </el-tabs>

    <InterfaceServiceEditorDialog
      ref="serviceEditorRef"
      :catalog="catalog"
      :source-type-options="sourceTypeOptions"
      @saved="loadAll"
    />
    <InterfaceServiceTestDialog
      ref="serviceTestRef"
      :forms="forms"
      :lists="lists"
      :entity-id="selectedEntityId"
      :entity-code="selectedEntity?.entityCode || ''"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import InterfaceServiceEditorDialog from '@/components/ui-config/InterfaceServiceEditorDialog.vue'
import InterfaceServiceTestDialog from '@/components/ui-config/InterfaceServiceTestDialog.vue'
import InterfaceServiceUsagePanel from '@/components/ui-config/InterfaceServiceUsagePanel.vue'
import {
  buildInterfaceServiceReferenceRoute
} from '@/components/ui-config/interfaceServiceUsageModel'
import {
  executionPolicy,
  serviceOperations,
  sourceTypeOptions
} from '@/components/ui-config/interfaceServiceModel'
import { entityApi } from '@/api/entity'
import {
  getFormById,
  getFormsByEntity
} from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiDataSourceApi } from '@/api/uiConfig'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('services')
const loading = ref(false)
const keyword = ref('')
const sourceTypeFilter = ref('')
const services = ref([])
const scopeObjectNames = ref({})
const catalog = ref({})
const forms = ref([])
const lists = ref([])
const selectedEntityId = ref('')
const serviceEditorRef = ref(null)
const serviceTestRef = ref(null)

function hasPermission(permission) {
  return userStore.isSuperAdmin
    || userStore.permissions.includes('*')
    || userStore.permissions.includes(permission)
}

const canUpdateServices = computed(() =>
  hasPermission('system:interface-service:update'))
const canTestServices = computed(() =>
  hasPermission('system:interface-service:test'))
const canViewEntityDefinitions = computed(() =>
  hasPermission('entity:definition:view'))
const canConfigureEntityEvents = computed(() =>
  canViewEntityDefinitions.value && hasPermission('entity:definition:manage'))

const filteredServices = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return services.value.filter(service => {
    if (sourceTypeFilter.value && service.sourceType !== sourceTypeFilter.value) {
      return false
    }
    if (!text) return true
    return `${service.sourceName} ${service.sourceCode}`
      .toLowerCase()
      .includes(text)
  })
})

const selectedEntity = ref(null)

async function loadAll() {
  loading.value = true
  try {
    const entityOptionsRequest = canViewEntityDefinitions.value
      ? entityApi.getOptions({
          pageNum: 1,
          pageSize: 1,
          storageMode: 'DYNAMIC'
        })
      : Promise.resolve({ records: [] })
    const [serviceRows, serviceCatalog, entityPage] = await Promise.all([
      uiDataSourceApi.list(),
      uiDataSourceApi.catalog(),
      entityOptionsRequest
    ])
    services.value = Array.isArray(serviceRows) ? serviceRows : []
    await resolveScopeObjectNames(services.value)
    catalog.value = serviceCatalog || {}
    if (!selectedEntityId.value && entityPage?.records?.length) {
      selectedEntity.value = entityPage.records[0]
      selectedEntityId.value = selectedEntity.value.id
      await loadEntityChildren()
    }
  } catch (error) {
    ElMessage.error(error.message || '加载接口服务失败')
  } finally {
    loading.value = false
  }
}

async function resolveScopeObjectNames(rows) {
  if (!canViewEntityDefinitions.value) {
    scopeObjectNames.value = Object.fromEntries(rows
      .filter(row => row.scopeId)
      .map(row => [
        `${row.scopeType}:${row.scopeId}`,
        row.scopeName || row.scopeId
      ]))
    return
  }
  const entries = await Promise.all(rows
    .filter(row => row.scopeId)
    .map(async row => {
      const key = `${row.scopeType}:${row.scopeId}`
      try {
        if (row.scopeType === 'ENTITY') {
          const [entity] = await entityApi.resolveOptions({
            ids: [String(row.scopeId)]
          })
          return [key, entity?.entityName || entity?.entityCode || row.scopeId]
        }
        if (row.scopeType === 'FORM') {
          const form = await getFormById(row.scopeId)
          return [key, `${form.formName} (${form.formKey})`]
        }
        if (row.scopeType === 'LIST') {
          const list = await entityListConfigApi.getById(row.scopeId)
          return [key, `${list.listName} (${list.listKey})`]
        }
      } catch {
        return [key, row.scopeId]
      }
      return [key, row.scopeId]
    }))
  scopeObjectNames.value = Object.fromEntries(entries)
}

function scopeObjectName(row) {
  return scopeObjectNames.value[`${row.scopeType}:${row.scopeId}`]
    || row.scopeId
}

function contextTypeLabel(value) {
  return {
    FORM: '表单',
    LIST: '列表',
    ENTITY: '实体'
  }[String(value || '').toUpperCase()] || value || '-'
}

async function loadEntityChildren() {
  forms.value = []
  lists.value = []
  if (!selectedEntityId.value) return
  const [formRows, listRows] = await Promise.all([
    getFormsByEntity(selectedEntityId.value),
    entityListConfigApi.getByEntityId(selectedEntityId.value)
  ])
  forms.value = Array.isArray(formRows) ? formRows : []
  lists.value = Array.isArray(listRows) ? listRows : []
}

function openCreateService() {
  serviceEditorRef.value?.openCreate()
}

function openEditService(row) {
  serviceEditorRef.value?.openEdit(row)
}

async function removeService(row) {
  await ElMessageBox.confirm(
    `确认删除接口服务“${row.sourceName}”？当前发布、仍可固定访问的历史版本、流程或 Embed 仍有可执行引用时，后端都会阻止删除。可先在“事件使用情况”定位 ACTIVE 引用，但仅解除并重新发布可能不足；请确认历史版本及相关运行入口均已退役，冲突提示会给出具体版本。`,
    '删除接口服务',
    { type: 'warning' }
  )
  try {
    await uiDataSourceApi.remove(row.id, row.revision)
    ElMessage.success('已删除')
    await loadAll()
  } catch (error) {
    // 删除冲突包含具体的线上引用信息，不能被通用提示覆盖。
    ElMessage.error(error?.message || '删除接口服务失败')
  }
}

function openTest(row) {
  serviceTestRef.value?.open(row)
}

async function goToReferenceConfiguration(reference) {
  if (reference.ownerType === 'ENTITY' && !canConfigureEntityEvents.value) {
    ElMessage.warning('缺少实体定义查看或维护权限，无法进入实体默认事件配置')
    return
  }
  const location = buildInterfaceServiceReferenceRoute(reference)
  if (!location) {
    ElMessage.warning('该引用缺少可定位的配置对象')
    return
  }
  await router.push(location)
}

function sourceTypeLabel(type) {
  return sourceTypeOptions.find(option => option.value === type)?.label || type
}

function scopeLabel(type) {
  return {
    GLOBAL: '全局',
    ENTITY: '实体',
    FORM: '表单',
    LIST: '列表'
  }[type] || type
}

onMounted(loadAll)
</script>

<style scoped>
.interface-services-page {
  padding: 18px;
}

.page-heading,
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-heading {
  margin-bottom: 8px;
}

.page-heading h2 {
  margin: 0;
  font-size: 22px;
  letter-spacing: 0;
}

.page-heading p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
}

.main-tabs {
  min-height: calc(100vh - 140px);
}

.table-toolbar {
  margin: 4px 0 12px;
}

.toolbar-filters {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 180px;
  gap: 10px;
}

.primary-text {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.secondary-text {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.operation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

</style>
