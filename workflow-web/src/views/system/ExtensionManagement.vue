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
        <el-button v-if="canUpdate" type="primary" @click="openCreateUi">
          <el-icon><Plus /></el-icon>
          新增 UI 目录版本
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
              <div>实现 v{{ row.implementationVersion || 1 }}</div>
              <div v-if="isUi(row)" class="meta-line">
                快照 v{{ row.snapshotVersion || 1 }}
              </div>
              <div v-else class="meta-line">
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
          <el-table-column label="操作" width="132" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="canUpdate && row.available !== false"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ row.configured ? '编辑' : '纳管' }}
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
          @current-change="load"
        />
      </template>
    </el-card>

    <el-dialog
      v-model="editorVisible"
      :title="editorTitle"
      width="760px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form :model="editor" label-width="104px">
        <template v-if="editor.kind === 'FLOW_ACTION'">
          <el-form-item label="动作名称" required>
            <el-input v-model="editor.displayName" />
          </el-form-item>
          <el-form-item label="用途说明">
            <el-input v-model="editor.description" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="可见范围" required>
            <el-segmented
              v-model="editor.visibilityScope"
              :options="visibilityOptions"
            />
          </el-form-item>
          <el-form-item
            v-if="editor.visibilityScope === 'ENTITY'"
            label="指定实体"
            required
          >
            <EntityDefinitionPicker
              v-model="editor.entityCodes"
              multiple
              value-key="entityCode"
              value-case="lower"
              title="选择动作适用实体"
              placeholder="选择可使用该动作的实体"
            />
          </el-form-item>
          <el-form-item label="允许配置">
            <el-switch v-model="editor.enabled" />
          </el-form-item>
        </template>

        <template v-else-if="editor.kind === 'PERSON_RESOLVER'">
          <el-form-item label="接口名称" required>
            <el-input v-model="editor.displayName" />
          </el-form-item>
          <el-form-item label="用途说明">
            <el-input v-model="editor.description" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="固定用途">
            <el-checkbox-group v-model="editor.supportedUsages" disabled>
              <el-checkbox
                v-for="usage in personUsageOptions"
                :key="usage.value"
                :value="usage.value"
              >
                {{ usage.label }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="允许配置">
            <el-switch v-model="editor.enabled" />
          </el-form-item>
        </template>

        <template v-else>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="扩展类型" required>
                <el-select
                  v-model="editor.extensionType"
                  :disabled="Boolean(editor.id)"
                  style="width: 100%"
                >
                  <el-option label="自定义表单" value="FORM" />
                  <el-option label="自定义列表" value="LIST" />
                  <el-option label="表单节点" value="NODE" />
                  <el-option label="表单字段" value="FIELD" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="注册名" required>
                <el-input v-model="editor.key" :disabled="Boolean(editor.id)" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="显示名称" required>
                <el-input v-model="editor.displayName" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="实现版本" required>
                <el-input-number
                  v-model="editor.implementationVersion"
                  :min="1"
                  :disabled="Boolean(editor.id)"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="快照版本" required>
                <el-input-number v-model="editor.snapshotVersion" :min="1" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="运行模式">
                <el-select
                  v-model="editor.supportedModes"
                  multiple
                  clearable
                  style="width: 100%"
                >
                  <el-option
                    v-for="mode in modeOptions"
                    :key="mode"
                    :label="mode"
                    :value="mode"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="配置 Schema">
                <el-input v-model="editor.configSchemaText" type="textarea" :rows="6" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="能力声明">
                <el-input v-model="editor.capabilitiesText" type="textarea" :rows="4" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="目录状态">
                <el-segmented
                  v-model="editor.status"
                  :options="uiStatusOptions"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEditor">
          保存
        </el-button>
      </template>
    </el-dialog>

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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDown, ArrowUp, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { extensionCatalogApi, personResolverApi } from '@/api/system/extension'
import { processActionApi } from '@/api/processAction'
import { uiExtensionApi } from '@/api/uiConfig'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import {
  getManagedExtensionManifest,
  isPlatformBuiltInUiExtension
} from '@/extensions/manifest'
import { useUserStore } from '@/stores/user'
const typeOptions = [
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
const modeOptions = ['CREATE', 'EDIT', 'APPROVE', 'VIEW']
const visibilityOptions = [
  { label: '全部实体', value: 'GLOBAL' },
  { label: '指定实体', value: 'ENTITY' }
]
const uiStatusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' }
]
const route = useRoute()
const userStore = useUserStore()
const canUpdate = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:extension:update'))
const localManifest = getManagedExtensionManifest()
const filters = reactive({
  capabilityType: normalizeRouteType(route.query.type),
  keyword: '',
  status: ''
})
const pageInfo = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const rows = ref([])
const loading = ref(false)
const loadError = ref('')
const searchExpanded = ref(false)
const editorVisible = ref(false)
const saving = ref(false)
const editor = reactive(emptyEditor())
const detailVisible = ref(false)
const detail = ref(null)
const editorTitle = computed(() => {
  if (editor.kind === 'FLOW_ACTION') return editor.configured ? '编辑流程动作目录' : '纳管流程动作'
  if (editor.kind === 'PERSON_RESOLVER') return editor.configured ? '编辑人员接口目录' : '纳管人员接口'
  return editor.id ? '编辑 UI 扩展目录' : '新增 UI 扩展版本'
})
function emptyEditor() {
  return {
    kind: 'UI_FORM',
    id: null,
    configured: false,
    sourceName: '',
    key: '',
    displayName: '',
    description: '',
    visibilityScope: 'ENTITY',
    entityCodes: [],
    enabled: false,
    supportedUsages: [],
    extensionType: 'FORM',
    implementationVersion: 1,
    snapshotVersion: 1,
    supportedModes: [],
    supportedNodeTypes: [],
    supportedBindings: [],
    configSchemaText: '[]',
    capabilitiesText: '{}',
    status: 'ACTIVE',
    revision: null
  }
}
function resetEditor(value = {}) {
  Object.assign(editor, emptyEditor(), value)
}
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [result, allUiDefinitions] = await Promise.all([
      extensionCatalogApi.manage({
        capabilityType: filters.capabilityType || undefined,
        keyword: filters.keyword?.trim() || undefined,
        status: filters.status || undefined,
        pageNum: pageInfo.pageNum,
        pageSize: pageInfo.pageSize
      }),
      uiExtensionApi.list()
    ])
    const remoteRows = (result?.list || [])
      .filter(row => !isPlatformBuiltInUiExtension(
        row.capabilityType, row.key))
      .map(decorateRemote)
    const remoteUiKeys = new Set((allUiDefinitions || []).map(item =>
      `UI_${item.extensionType}:${item.extensionKey}:${item.version || 1}`))
    const localRows = pageInfo.pageNum === 1
      ? localOnlyRows(remoteUiKeys)
      : []
    rows.value = [...localRows, ...remoteRows]
    pageInfo.total = Number(result?.total || 0) + localRows.length
  } catch (error) {
    loadError.value = error?.message || '无法读取扩展目录，请重试。'
  } finally {
    loading.value = false
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

function localOnlyRows(remoteKeys) {
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
      status: 'DISCOVERED',
      configured: false,
      available: true,
      enabled: false,
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
    .filter(row => !filters.capabilityType
      || row.capabilityType === filters.capabilityType)
    .filter(row => !filters.status || row.status === filters.status)
    .filter(row => matchesKeyword(row, filters.keyword))
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
  Object.assign(filters, { capabilityType: '', keyword: '', status: '' })
  pageInfo.pageNum = 1
  load()
}

function handleSizeChange() {
  pageInfo.pageNum = 1
  load()
}

async function openEdit(row) {
  if (row.capabilityType === 'FLOW_ACTION') {
    resetEditor({
      kind: 'FLOW_ACTION',
      configured: row.configured,
      sourceName: row.sourceName,
      key: row.key,
      displayName: row.configured ? row.displayName : '',
      description: row.description || '',
      visibilityScope: row.visibilityScope || 'ENTITY',
      entityCodes: row.entityCodes || [],
      enabled: row.configured ? row.enabled !== false : false
    })
  } else if (row.capabilityType === 'PERSON_RESOLVER') {
    resetEditor({
      kind: 'PERSON_RESOLVER',
      configured: row.configured,
      key: row.key,
      displayName: row.displayName || '',
      description: row.description || '',
      supportedUsages: [...(row.supportedUsages || [])],
      enabled: row.configured ? row.enabled !== false : false
    })
  } else {
    resetEditor({
      kind: row.capabilityType,
      id: row.id,
      configured: row.configured,
      key: row.key,
      displayName: row.displayName || '',
      extensionType: row.capabilityType.replace(/^UI_/, ''),
      implementationVersion: row.implementationVersion || 1,
      snapshotVersion: row.snapshotVersion || 1,
      supportedModes: [...(row.supportedModes || [])],
      supportedNodeTypes: [...(row.supportedNodeTypes || [])],
      supportedBindings: [...(row.supportedBindings || [])],
      configSchemaText: formatJson(row.configSchema || []),
      capabilitiesText: formatJson(row.capabilities || {}),
      status: row.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE',
      revision: row.revision
    })
  }
  editorVisible.value = true
}

function openCreateUi() {
  resetEditor()
  editorVisible.value = true
}

async function saveEditor() {
  if (!editor.displayName?.trim()) {
    ElMessage.warning('请填写显示名称')
    return
  }
  saving.value = true
  try {
    if (editor.kind === 'FLOW_ACTION') {
      if (editor.visibilityScope === 'ENTITY' && !editor.entityCodes.length) {
        ElMessage.warning('指定实体范围至少选择一个实体')
        return
      }
      await processActionApi.saveHandlerConfig(editor.sourceName, {
        displayName: editor.displayName.trim(),
        description: editor.description?.trim() || '',
        visibilityScope: editor.visibilityScope,
        entityCodes: editor.visibilityScope === 'ENTITY' ? editor.entityCodes : [],
        enabled: editor.enabled
      })
    } else if (editor.kind === 'PERSON_RESOLVER') {
      await personResolverApi.saveConfig(editor.key, {
        displayName: editor.displayName.trim(),
        description: editor.description?.trim() || '',
        enabled: editor.enabled
      })
    } else {
      if (!editor.key?.trim()) {
        ElMessage.warning('请填写扩展注册名')
        return
      }
      const payload = {
        extensionType: editor.extensionType,
        extensionKey: editor.key.trim(),
        displayName: editor.displayName.trim(),
        version: editor.implementationVersion,
        snapshotVersion: editor.snapshotVersion,
        supportedModes: editor.supportedModes,
        supportedNodeTypes: editor.supportedNodeTypes,
        supportedBindings: editor.supportedBindings,
        configSchema: parseJson(editor.configSchemaText, []),
        capabilities: parseJson(editor.capabilitiesText, {}),
        status: editor.status,
        expectedRevision: editor.revision
      }
      if (editor.id) {
        await uiExtensionApi.update(editor.id, payload)
      } else {
        await uiExtensionApi.create(payload)
      }
    }
    ElMessage.success('扩展目录已保存')
    editorVisible.value = false
    await load()
  } catch (error) {
    if (error instanceof SyntaxError) {
      ElMessage.error('Schema 或能力声明不是合法 JSON')
    } else {
      ElMessage.error(error?.message || '扩展目录保存失败')
    }
  } finally {
    saving.value = false
  }
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

function isUi(row) {
  return String(row?.capabilityType || '').startsWith('UI_')
}

function typeLabel(value) {
  return typeMap[value] || value || '-'
}

function sourceLabel(value) {
  return value === 'FRONTEND_BUNDLE' ? '前端构建' : '后端 Bean'
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
  if (row.capabilityType === 'FLOW_ACTION') {
    if (row.visibilityScope === 'GLOBAL') return '全部实体'
    return row.entityCodes?.length ? `${row.entityCodes.length} 个实体` : '尚未指定实体'
  }
  if (row.capabilityType === 'PERSON_RESOLVER') return '全局受控接口'
  return '前端构建范围'
}

function capabilitySummary(row) {
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
  const schema = row.configSchema ?? row.extraParamSchema
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
  return {
    contractVersion: row.contractVersion || 1,
    supportedUsages: row.supportedUsages || [],
    supportedTriggerTimings: row.supportedTriggerTimings || [],
    supportedExecutionModes: row.supportedExecutionModes || [],
    supportedModes: row.supportedModes || [],
    supportedNodeTypes: row.supportedNodeTypes || [],
    supportedBindings: row.supportedBindings || [],
    dynamicExtraParams: row.dynamicExtraParams === true
  }
}

function formatJson(value) {
  return JSON.stringify(value ?? {}, null, 2)
}

function parseJson(value, fallback) {
  if (!value?.trim()) return fallback
  return JSON.parse(value)
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
