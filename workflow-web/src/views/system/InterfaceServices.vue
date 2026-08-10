<template>
  <div class="interface-services-page">
    <div class="page-heading">
      <div>
        <h2>接口服务</h2>
        <p>统一管理服务操作，并把操作绑定到列表、表单、字段和按钮事件。</p>
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
          <el-button type="primary" @click="openCreateService">
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
              <el-button link type="primary" @click="openTest(row)">调试</el-button>
              <el-button link type="primary" @click="openEditService(row)">编辑</el-button>
              <el-button link type="danger" @click="removeService(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="事件绑定" name="bindings">
        <div class="binding-layout">
          <aside class="binding-scope-panel">
            <div class="scope-title">绑定对象</div>
            <el-form label-position="top">
              <el-form-item label="实体">
                <EntityDefinitionPicker
                  v-model="selectedEntityId"
                  placeholder="选择实体"
                  value-key="id"
                  title="选择事件绑定实体"
                  :query="{ storageMode: 'DYNAMIC' }"
                  @selected="handleBindingEntitySelected"
                  @resolved="rememberBindingEntity"
                />
              </el-form-item>
              <el-form-item label="配置层级">
                <el-segmented
                  v-model="bindingOwnerType"
                  :options="ownerTypeOptions"
                />
              </el-form-item>
              <el-form-item v-if="bindingOwnerType === 'FORM'" label="表单">
                <el-select v-model="selectedFormId" filterable>
                  <el-option
                    v-for="form in forms"
                    :key="form.id"
                    :label="`${form.formName} (${form.formKey})`"
                    :value="form.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="bindingOwnerType === 'LIST'" label="列表">
                <el-select v-model="selectedListId" filterable>
                  <el-option
                    v-for="list in lists"
                    :key="list.id"
                    :label="`${list.listName} (${list.listKey})`"
                    :value="list.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="bindingOwnerType !== 'ENTITY'"
                label="绑定位置"
              >
                <el-select v-model="bindingTargetType">
                  <el-option label="当前表单或列表" value="OWNER" />
                  <el-option
                    v-if="bindingOwnerType === 'FORM'"
                    label="字段"
                    value="FIELD"
                  />
                  <el-option label="按钮" value="BUTTON" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="bindingTargetType === 'FIELD'" label="字段">
                <el-select v-model="bindingTargetKey" filterable>
                  <el-option
                    v-for="field in formFields"
                    :key="field.id || field.fieldCode"
                    :label="`${fieldLabel(field)} (${field.fieldCode})`"
                    :value="String(field.fieldCode)"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="bindingTargetType === 'BUTTON'" label="按钮编码">
                <el-input
                  v-model="bindingTargetKey"
                  placeholder="按钮稳定编码"
                />
              </el-form-item>
            </el-form>
          </aside>

          <main class="binding-main">
            <EventBindingEditor
              :owner-type="bindingOwnerType"
              :owner-id="bindingOwnerId"
              :target-type="effectiveTargetType"
              :target-key="effectiveTargetKey"
              :target-name="bindingTargetName"
              :allowed-events="allowedEvents"
              :field-options="fieldOptions"
              title="统一事件执行链"
            />
          </main>
        </div>
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
      :event-codes="eventCodes"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import EventBindingEditor from '@/components/ui-config/EventBindingEditor.vue'
import InterfaceServiceEditorDialog from '@/components/ui-config/InterfaceServiceEditorDialog.vue'
import InterfaceServiceTestDialog from '@/components/ui-config/InterfaceServiceTestDialog.vue'
import {
  configurableEntities,
  eventCodes,
  executionPolicy,
  serviceOperations,
  sourceTypeOptions
} from '@/components/ui-config/interfaceServiceModel'
import { entityApi } from '@/api/entity'
import {
  getEntityFields,
  getFormById,
  getFormsByEntity
} from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiDataSourceApi } from '@/api/uiConfig'

const ownerTypeOptions = [
  { label: '实体默认', value: 'ENTITY' },
  { label: '表单覆盖', value: 'FORM' },
  { label: '列表覆盖', value: 'LIST' }
]

const activeTab = ref('services')
const loading = ref(false)
const keyword = ref('')
const sourceTypeFilter = ref('')
const services = ref([])
const scopeObjectNames = ref({})
const catalog = ref({})
const forms = ref([])
const lists = ref([])
const entityFields = ref([])
const formFields = ref([])
const selectedEntityId = ref('')
const selectedFormId = ref('')
const selectedListId = ref('')
const bindingOwnerType = ref('ENTITY')
const bindingTargetType = ref('OWNER')
const bindingTargetKey = ref('')
const serviceEditorRef = ref(null)
const serviceTestRef = ref(null)

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

const bindingOwnerId = computed(() => {
  if (bindingOwnerType.value === 'ENTITY') return selectedEntityId.value
  if (bindingOwnerType.value === 'FORM') return selectedFormId.value
  return selectedListId.value
})

const effectiveTargetType = computed(() =>
  bindingOwnerType.value === 'ENTITY' ? 'OWNER' : bindingTargetType.value)

const effectiveTargetKey = computed(() =>
  effectiveTargetType.value === 'OWNER' ? '' : bindingTargetKey.value)

const bindingTargetName = computed(() => {
  if (effectiveTargetType.value === 'FIELD') {
    return fieldLabel(formFields.value.find(field =>
      String(field.fieldCode) === String(bindingTargetKey.value)))
  }
  return bindingTargetKey.value
})

const fieldOptions = computed(() =>
  entityFields.value
    .filter(field => !field.isSystem)
    .map(field => ({
      label: field.fieldName || field.fieldLabel || field.fieldCode,
      value: field.fieldCode
    }))
)

const allowedEvents = computed(() => {
  if (effectiveTargetType.value === 'FIELD') {
    return ['FIELD_CHANGE', 'ENTITY_SELECTED', 'FIELD_BUTTON_CLICK']
  }
  if (effectiveTargetType.value === 'BUTTON') {
    return bindingOwnerType.value === 'LIST'
      ? ['TOOLBAR_BUTTON_CLICK', 'ROW_BUTTON_CLICK']
      : ['FORM_BUTTON_CLICK', 'FIELD_BUTTON_CLICK']
  }
  if (bindingOwnerType.value === 'LIST') {
    return [
      'LIST_LOAD', 'LIST_EXPORT', 'DETAIL_LOAD',
      'DATA_CREATE', 'DATA_UPDATE', 'DATA_DELETE', 'DATA_BATCH_DELETE'
    ]
  }
  if (bindingOwnerType.value === 'FORM') {
    return [
      'DETAIL_LOAD', 'FORM_OPEN', 'FORM_SAVE', 'FORM_RESET',
      'DATA_CREATE', 'DATA_UPDATE', 'SUBFORM_LOAD', 'SUBFORM_SAVE'
    ]
  }
  return [
    'LIST_LOAD', 'LIST_EXPORT', 'DETAIL_LOAD',
    'DATA_CREATE', 'DATA_UPDATE', 'DATA_DELETE', 'DATA_BATCH_DELETE'
  ]
})

async function loadAll() {
  loading.value = true
  try {
    const [serviceRows, serviceCatalog, entityPage] = await Promise.all([
      uiDataSourceApi.list(),
      uiDataSourceApi.catalog(),
      entityApi.getOptions({
        pageNum: 1,
        pageSize: 1,
        storageMode: 'DYNAMIC'
      })
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

function rememberBindingEntity(entity) {
  if (entity && !Array.isArray(entity)) {
    selectedEntity.value = entity
  }
}

async function handleBindingEntitySelected(entity) {
  selectedEntity.value = entity || null
  await loadEntityChildren()
}

async function loadEntityChildren() {
  forms.value = []
  lists.value = []
  entityFields.value = []
  formFields.value = []
  selectedFormId.value = ''
  selectedListId.value = ''
  bindingTargetKey.value = ''
  if (!selectedEntityId.value) return
  const [formRows, listRows, fieldRows] = await Promise.all([
    getFormsByEntity(selectedEntityId.value),
    entityListConfigApi.getByEntityId(selectedEntityId.value),
    getEntityFields(selectedEntityId.value)
  ])
  forms.value = Array.isArray(formRows) ? formRows : []
  lists.value = Array.isArray(listRows) ? listRows : []
  entityFields.value = Array.isArray(fieldRows) ? fieldRows : []
  selectedFormId.value = forms.value[0]?.id || ''
  selectedListId.value = lists.value[0]?.id || ''
  await loadSelectedFormFields()
}

async function loadSelectedFormFields() {
  const form = forms.value.find(item => String(item.id) === String(selectedFormId.value))
  formFields.value = Array.isArray(form?.fields) && form.fields.length
    ? form.fields
    : entityFields.value
  bindingTargetKey.value = ''
}

function openCreateService() {
  serviceEditorRef.value?.openCreate()
}

function openEditService(row) {
  serviceEditorRef.value?.openEdit(row)
}

async function removeService(row) {
  await ElMessageBox.confirm(
    `确认删除接口服务“${row.sourceName}”？已发布页面的历史快照不受影响。`,
    '删除接口服务',
    { type: 'warning' }
  )
  await uiDataSourceApi.remove(row.id, row.revision)
  ElMessage.success('已删除')
  await loadAll()
}

function openTest(row) {
  serviceTestRef.value?.open(row)
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

function fieldLabel(field) {
  return field?.fieldLabel || field?.fieldName || field?.fieldCode || ''
}

watch(bindingOwnerType, value => {
  bindingTargetType.value = 'OWNER'
  bindingTargetKey.value = ''
  if (value === 'FORM') loadSelectedFormFields()
})

watch(selectedFormId, loadSelectedFormFields)

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

.primary-text,
.scope-title {
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

.binding-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  min-height: 620px;
  border: 1px solid var(--el-border-color);
}

.binding-scope-panel {
  padding: 16px;
  background: var(--el-fill-color-lighter);
  border-right: 1px solid var(--el-border-color);
}

.scope-title {
  margin-bottom: 14px;
}

.binding-main {
  min-width: 0;
  padding: 18px;
}

@media (max-width: 1000px) {
  .binding-layout {
    grid-template-columns: 1fr;
  }

  .binding-scope-panel {
    border-right: 0;
    border-bottom: 1px solid var(--el-border-color);
  }
}
</style>
