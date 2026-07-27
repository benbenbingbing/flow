<template>
  <div class="ui-extension-manager">
    <el-alert
      :title="`当前构建已加载 ${localManifest.length} 个扩展实现；目录状态与本地实现会分别校验。`"
      type="info"
      :closable="false"
      show-icon
      class="catalog-summary"
    />
    <div class="toolbar">
      <el-select v-model="filters.extensionType" clearable placeholder="全部类型" @change="load">
        <el-option v-for="type in extensionTypes" :key="type" :label="type" :value="type" />
      </el-select>
      <el-input
        v-model="filters.extensionKey"
        clearable
        placeholder="注册名"
        @keyup.enter="load"
      />
      <el-button @click="load">查询</el-button>
      <el-button type="primary" @click="openCreate">新增扩展版本</el-button>
    </div>

    <PageState
      v-if="loadError"
      type="error"
      title="扩展目录加载失败"
      :description="loadError"
      compact
      retryable
      @retry="load"
    />

    <el-table v-else :data="items" v-loading="loading" size="small" empty-text="当前条件下没有扩展定义">
      <el-table-column prop="extensionType" label="类型" width="100">
        <template #default="{ row }">{{ extensionTypeLabel(row.extensionType) }}</template>
      </el-table-column>
      <el-table-column prop="extensionKey" label="注册名" min-width="160" />
      <el-table-column prop="displayName" label="名称" min-width="140" />
      <el-table-column prop="version" label="实现版本" width="90" />
      <el-table-column prop="snapshotVersion" label="快照版本" width="90" />
      <el-table-column prop="status" label="目录状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前构建" width="120">
        <template #default="{ row }">
          <el-tag :type="findLocalImplementation(row) ? 'success' : 'warning'" size="small" effect="plain">
            {{ findLocalImplementation(row) ? '已加载' : '未加载' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="revision" label="修订" width="70" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="editorVisible"
      :title="editor.id ? '编辑扩展定义' : '新增扩展版本'"
      width="760px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="扩展类型" required>
              <el-select v-model="editor.extensionType" :disabled="Boolean(editor.id)" style="width: 100%">
                <el-option v-for="type in extensionTypes" :key="type" :label="type" :value="type" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="注册名" required>
              <el-input v-model="editor.extensionKey" :disabled="Boolean(editor.id)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示名称" required>
              <el-input v-model="editor.displayName" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="实现版本" required>
              <el-input-number v-model="editor.version" :min="1" :disabled="Boolean(editor.id)" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="快照版本" required>
              <el-input-number v-model="editor.snapshotVersion" :min="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="editor.status">
                <el-radio-button value="ACTIVE">启用</el-radio-button>
                <el-radio-button value="DISABLED">禁用</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="运行模式">
              <el-select v-model="editor.supportedModes" multiple clearable style="width: 100%">
                <el-option v-for="mode in modes" :key="mode" :label="mode" :value="mode" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="节点类型">
              <el-select v-model="editor.supportedNodeTypes" multiple clearable style="width: 100%">
                <el-option v-for="type in nodeTypes" :key="type" :label="type" :value="type" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="绑定类型">
              <el-select v-model="editor.supportedBindings" multiple clearable style="width: 100%">
                <el-option v-for="type in bindingTypes" :key="type" :label="type" :value="type" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="配置 Schema">
              <el-input v-model="editor.configSchemaText" type="textarea" :rows="5" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="能力声明">
              <el-input v-model="editor.capabilitiesText" type="textarea" :rows="4" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uiExtensionApi } from '@/api/uiConfig'
import PageState from '@/components/PageState.vue'
import {
  getManagedExtensionManifest,
  isPlatformBuiltInUiExtension,
  validateBundledExtensionManifest
} from '@/extensions/manifest'

const emit = defineEmits(['changed'])
const extensionTypes = ['FORM', 'NODE', 'FIELD', 'LIST']
const modes = ['CREATE', 'EDIT', 'APPROVE', 'VIEW']
const nodeTypes = [
  'SECTION', 'GRID', 'TAB_SET', 'TAB', 'COLLAPSE',
  'TEXT', 'FIELD', 'SUB_FORM', 'REPEATER', 'ACTION_SLOT'
]
const bindingTypes = ['ENTITY_FIELD', 'RELATION', 'COMPUTED', 'CONTEXT', 'NONE']
const filters = reactive({ extensionType: '', extensionKey: '' })
const items = ref([])
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const editorVisible = ref(false)
const editor = reactive(emptyEditor())
const localManifest = getManagedExtensionManifest()
const manifestIssues = validateBundledExtensionManifest(localManifest)
if (manifestIssues.length) {
  console.error('当前构建的扩展清单无效:', manifestIssues)
}
const extensionTypeLabels = {
  FORM: '表单',
  NODE: '表单节点',
  FIELD: '表单字段',
  LIST: '列表'
}

function emptyEditor() {
  return {
    id: null,
    extensionType: 'FORM',
    extensionKey: '',
    displayName: '',
    version: 1,
    snapshotVersion: 1,
    supportedModes: [],
    supportedNodeTypes: [],
    supportedBindings: [],
    configSchemaText: '{}',
    capabilitiesText: '{}',
    status: 'ACTIVE',
    expectedRevision: null
  }
}

function parseDocument(value, fallback) {
  if (!value) return fallback
  if (typeof value === 'object') return value
  return JSON.parse(value)
}

function resetEditor(value = emptyEditor()) {
  Object.assign(editor, value)
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const definitions = await uiExtensionApi.list({
      extensionType: filters.extensionType || undefined,
      extensionKey: filters.extensionKey || undefined
    })
    items.value = (definitions || []).filter(item =>
      !isPlatformBuiltInUiExtension(
        item.extensionType, item.extensionKey))
  } catch (error) {
    loadError.value = error?.message || '无法读取扩展目录，请重试。'
  } finally {
    loading.value = false
  }
}

function extensionTypeLabel(type) {
  return extensionTypeLabels[type] || type || '-'
}

function findLocalImplementation(row) {
  return localManifest.find(item =>
    item.type === row.extensionType
      && item.name === row.extensionKey
      && item.version === Number(row.version)
  )
}

function openCreate() {
  resetEditor()
  editorVisible.value = true
}

function openEdit(row) {
  resetEditor({
    id: row.id,
    extensionType: row.extensionType,
    extensionKey: row.extensionKey,
    displayName: row.displayName,
    version: row.version,
    snapshotVersion: row.snapshotVersion,
    supportedModes: parseDocument(row.supportedModesDocument, []),
    supportedNodeTypes: parseDocument(row.supportedNodeTypesDocument, []),
    supportedBindings: parseDocument(row.supportedBindingsDocument, []),
    configSchemaText: JSON.stringify(parseDocument(row.configSchemaDocument, {}), null, 2),
    capabilitiesText: JSON.stringify(parseDocument(row.capabilitiesDocument, {}), null, 2),
    status: row.status,
    expectedRevision: row.revision
  })
  editorVisible.value = true
}

async function save() {
  if (!editor.extensionKey || !editor.displayName) {
    ElMessage.warning('请填写注册名和显示名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...editor,
      configSchema: parseDocument(editor.configSchemaText, {}),
      capabilities: parseDocument(editor.capabilitiesText, {})
    }
    delete payload.configSchemaText
    delete payload.capabilitiesText
    if (editor.id) {
      await uiExtensionApi.update(editor.id, payload)
    } else {
      await uiExtensionApi.create(payload)
    }
    ElMessage.success('扩展定义已保存')
    editorVisible.value = false
    await load()
    emit('changed')
  } catch (error) {
    if (error instanceof SyntaxError) {
      ElMessage.error('Schema 或能力声明不是合法 JSON')
    } else {
      ElMessage.error(error?.message || '扩展定义保存失败')
    }
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.catalog-summary {
  margin-bottom: 12px;
}
.toolbar .el-select,
.toolbar .el-input {
  width: 180px;
}
</style>
