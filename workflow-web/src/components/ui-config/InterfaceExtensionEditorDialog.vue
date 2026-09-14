<template>
  <el-dialog
    v-model="visible"
    :title="editor.id ? '编辑扩展接口' : '新增扩展接口'"
    width="900px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-alert
      v-if="editor.id"
      class="edit-risk-alert"
      type="warning"
      :closable="false"
      show-icon
      title="修改已发布接口可能影响现有页面"
      description="实现、作用范围、Schema 或启停状态保存后会形成新修订；已发布页面仍使用其固定快照。"
    />

    <el-form :model="editor" label-width="112px">
      <div class="form-grid">
        <el-form-item label="接口名称" required>
          <el-input v-model="editor.displayName" />
        </el-form-item>
        <el-form-item label="接口编码" required>
          <el-input v-model="editor.extensionKey" :disabled="Boolean(editor.id)" />
        </el-form-item>
        <el-form-item label="实现类型" required>
          <el-select v-model="editor.implementationType" @change="handleImplementationTypeChange">
            <el-option
              v-for="option in interfaceImplementationTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="requiresInterfaceProvider(editor.implementationType)"
          label="Provider"
          required
        >
          <template #label>
            <ConfigHelpLabel
              label="Provider"
              help-key="extensionInterface.backendImplementation"
            />
          </template>
          <el-select
            v-model="editor.providerCode"
            filterable
            placeholder="选择后端已注册 Provider"
          >
            <el-option
              v-for="option in providerOptions"
              :key="option.code"
              :label="option.name || option.code"
              :value="option.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="requiresInterfaceProvider(editor.implementationType)"
          required
        >
          <template #label>
            <ConfigHelpLabel
              label="实现入口"
              help-key="extensionInterface.providerOperationCode"
            />
          </template>
          <el-input
            v-model="editor.providerOperationCode"
            placeholder="Provider 内部能力编码"
          />
        </el-form-item>
        <el-form-item label="数据影响" required>
          <el-segmented
            v-model="editor.interfaceKind"
            :options="[
              { label: '只读查询', value: 'READ' },
              { label: '修改数据', value: 'WRITE' }
            ]"
          />
        </el-form-item>
        <el-form-item label="业务上下文" required>
          <el-segmented
            v-model="editor.interfaceContextType"
            :options="[
              { label: '表单', value: 'FORM' },
              { label: '列表', value: 'LIST' },
              { label: '实体', value: 'ENTITY' }
            ]"
          />
        </el-form-item>
        <el-form-item label="作用范围">
          <el-select v-model="editor.scopeType" @change="handleScopeTypeChange">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="实体" value="ENTITY" />
            <el-option label="表单" value="FORM" />
            <el-option label="列表" value="LIST" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editor.scopeType === 'ENTITY'" label="范围对象" required>
          <EntityDefinitionPicker
            v-model="editor.scopeId"
            value-key="id"
            title="选择扩展接口作用实体"
            :query="{ storageMode: 'DYNAMIC' }"
          />
        </el-form-item>
        <el-form-item
          v-if="['FORM', 'LIST'].includes(editor.scopeType)"
          label="所属实体"
          required
        >
          <EntityDefinitionPicker
            v-model="scopeEntityId"
            value-key="id"
            title="选择作用对象所属实体"
            :query="{ storageMode: 'DYNAMIC' }"
            @selected="handleScopeEntitySelected"
          />
        </el-form-item>
        <el-form-item
          v-if="['FORM', 'LIST'].includes(editor.scopeType)"
          label="范围对象"
          required
        >
          <el-select
            v-model="editor.scopeId"
            :loading="scopeLoading"
            filterable
            placeholder="选择具体表单或列表"
          >
            <el-option
              v-for="option in scopeObjects"
              :key="option.id"
              :label="option.label"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="超时">
          <el-input-number
            v-model="editor.timeoutMs"
            :min="100"
            :max="30000"
            :step="100"
            controls-position="right"
          />
          <span class="unit">ms</span>
        </el-form-item>
        <el-form-item label="缓存">
          <el-input-number
            v-model="editor.cacheSeconds"
            :min="0"
            :max="86400"
            controls-position="right"
          />
          <span class="unit">秒</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-segmented
            v-model="editor.status"
            :options="[
              { label: '启用', value: 'ACTIVE' },
              { label: '停用', value: 'DISABLED' }
            ]"
          />
        </el-form-item>
      </div>

      <el-collapse class="advanced-config">
        <el-collapse-item title="实现配置与输入输出契约" name="interface-contract">
          <div class="config-scope-note">
            一条扩展记录就是一个可调用接口。复杂步骤由 Provider 在后端封装，页面不组合后端方法。
          </div>
          <div class="json-grid">
            <div class="json-editor-field">
              <ConfigHelpLabel
                label="实现配置"
                help-key="extensionInterface.implementationConfig"
              />
              <el-input
                v-model="editor.implementationConfigText"
                type="textarea"
                :rows="8"
                spellcheck="false"
              />
            </div>
            <div class="json-editor-field">
              <ConfigHelpLabel
                label="输入 Schema"
                help-key="extensionInterface.inputSchema"
              />
              <el-input
                v-model="editor.inputSchemaText"
                type="textarea"
                :rows="8"
                spellcheck="false"
              />
            </div>
            <div class="json-editor-field">
              <ConfigHelpLabel
                label="输出 Schema"
                help-key="extensionInterface.outputSchema"
              />
              <el-input
                v-model="editor.outputSchemaText"
                type="textarea"
                :rows="8"
                spellcheck="false"
              />
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        保存扩展接口
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import { getFormById, getFormsByEntity } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiExtensionApi } from '@/api/uiConfig'
import {
  interfaceImplementationTypeOptions,
  mergeInterfaceExecutionPolicy,
  normalizeInterfaceExtension,
  parseInterfaceEditorJson,
  requiresInterfaceProvider
} from './interfaceExtensionModel'

const props = defineProps({
  catalog: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
const scopeLoading = ref(false)
const scopeEntityId = ref('')
const scopeObjects = ref([])
const editor = reactive(emptyEditor())
const providerOptions = computed(() => props.catalog.providers || [])

function emptyEditor() {
  return {
    id: '',
    expectedRevision: null,
    extensionKey: '',
    displayName: '',
    implementationType: 'REGISTERED_PROVIDER',
    providerCode: '',
    providerOperationCode: '',
    interfaceKind: 'READ',
    interfaceContextType: 'FORM',
    scopeType: 'ENTITY',
    scopeId: '',
    timeoutMs: 3000,
    cacheSeconds: 0,
    executionPolicy: { failurePolicy: 'FAIL' },
    status: 'ACTIVE',
    implementationConfigText: '{}',
    inputSchemaText: '{}',
    outputSchemaText: '{}'
  }
}

function reset(value = {}) {
  Object.assign(editor, emptyEditor(), value)
}

function openCreate() {
  scopeEntityId.value = ''
  scopeObjects.value = []
  reset()
  visible.value = true
}

async function openEdit(value) {
  const item = normalizeInterfaceExtension(value)
  scopeEntityId.value = ''
  scopeObjects.value = []
  reset({
    id: item.extensionId,
    expectedRevision: item.revision,
    extensionKey: item.extensionKey,
    displayName: item.displayName,
    implementationType: item.implementationType,
    providerCode: item.providerCode || '',
    providerOperationCode: item.providerOperationCode || '',
    interfaceKind: item.interfaceKind,
    interfaceContextType: item.interfaceContextType,
    scopeType: item.scopeType || 'GLOBAL',
    scopeId: item.scopeId || '',
    timeoutMs: Number(item.executionPolicy.timeoutMs || 3000),
    cacheSeconds: Number(item.executionPolicy.cacheSeconds || 0),
    executionPolicy: { ...item.executionPolicy },
    status: item.status,
    implementationConfigText: JSON.stringify(item.implementationConfig, null, 2),
    inputSchemaText: JSON.stringify(item.inputSchema, null, 2),
    outputSchemaText: JSON.stringify(item.outputSchema, null, 2)
  })
  await resolveScopeObject(item)
  visible.value = true
}

function handleImplementationTypeChange(type) {
  if (!requiresInterfaceProvider(type)) {
    editor.providerCode = ''
    editor.providerOperationCode = ''
  }
}

function handleScopeTypeChange() {
  editor.scopeId = ''
  scopeEntityId.value = ''
  scopeObjects.value = []
}

async function handleScopeEntitySelected(entity) {
  scopeEntityId.value = entity?.id || ''
  editor.scopeId = ''
  await loadScopeObjects()
}

async function loadScopeObjects() {
  if (!scopeEntityId.value || !['FORM', 'LIST'].includes(editor.scopeType)) {
    scopeObjects.value = []
    return
  }
  scopeLoading.value = true
  try {
    const rows = editor.scopeType === 'FORM'
      ? await getFormsByEntity(scopeEntityId.value)
      : await entityListConfigApi.getByEntityId(scopeEntityId.value)
    scopeObjects.value = (Array.isArray(rows) ? rows : []).map(item => ({
      id: item.id,
      label: editor.scopeType === 'FORM'
        ? `${item.formName} (${item.formKey})`
        : `${item.listName} (${item.listKey})`
    }))
  } finally {
    scopeLoading.value = false
  }
}

async function resolveScopeObject(item) {
  if (item.scopeType === 'FORM' && item.scopeId) {
    const form = await getFormById(item.scopeId)
    scopeEntityId.value = form?.entityId || ''
    await loadScopeObjects()
  } else if (item.scopeType === 'LIST' && item.scopeId) {
    const list = await entityListConfigApi.getById(item.scopeId)
    scopeEntityId.value = list?.entityId || ''
    await loadScopeObjects()
  }
}

async function save() {
  if (!editor.displayName?.trim() || !editor.extensionKey?.trim()) {
    ElMessage.warning('请填写接口名称和编码')
    return
  }
  if (requiresInterfaceProvider(editor.implementationType)
      && !editor.providerCode) {
    ElMessage.warning('请选择后端 Provider')
    return
  }
  if (requiresInterfaceProvider(editor.implementationType)
      && !editor.providerOperationCode?.trim()) {
    ElMessage.warning('请填写 Provider 实现入口')
    return
  }
  if (editor.scopeType !== 'GLOBAL' && !editor.scopeId) {
    ElMessage.warning('请选择作用范围对象')
    return
  }
  saving.value = true
  try {
    const payload = {
      expectedRevision: editor.expectedRevision,
      extensionType: 'INTERFACE',
      extensionKey: editor.extensionKey.trim(),
      displayName: editor.displayName.trim(),
      implementationType: editor.implementationType,
      providerCode: requiresInterfaceProvider(editor.implementationType)
        ? editor.providerCode
        : null,
      // 内部实现路由属于这条接口记录，不会暴露为设计器的第二层选项。
      providerOperationCode: requiresInterfaceProvider(editor.implementationType)
        ? editor.providerOperationCode.trim()
        : null,
      scopeType: editor.scopeType,
      scopeId: editor.scopeType === 'GLOBAL' ? null : editor.scopeId,
      implementationConfig: parseInterfaceEditorJson(
        editor.implementationConfigText,
        '实现配置'
      ),
      executionPolicy: mergeInterfaceExecutionPolicy(editor.executionPolicy, {
        timeoutMs: editor.timeoutMs,
        cacheSeconds: editor.cacheSeconds
      }),
      inputSchema: parseInterfaceEditorJson(editor.inputSchemaText, '输入 Schema'),
      outputSchema: parseInterfaceEditorJson(editor.outputSchemaText, '输出 Schema'),
      interfaceKind: editor.interfaceKind,
      interfaceContextType: editor.interfaceContextType,
      status: editor.status
    }
    if (editor.id) await uiExtensionApi.update(editor.id, payload)
    else await uiExtensionApi.create(payload)
    visible.value = false
    ElMessage.success('扩展接口已保存')
    emit('saved')
  } catch (error) {
    ElMessage.error(error?.message || '保存扩展接口失败')
  } finally {
    saving.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<style scoped>
.form-grid,
.json-grid {
  display: grid;
  gap: 12px 20px;
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.json-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.edit-risk-alert,
.advanced-config {
  margin-bottom: 14px;
}

.config-scope-note {
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.7;
}

.json-editor-field {
  min-width: 0;
}

.json-editor-field :deep(.config-help-label) {
  margin-bottom: 6px;
}

.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 760px) {
  .form-grid,
  .json-grid {
    grid-template-columns: 1fr;
  }
}
</style>
