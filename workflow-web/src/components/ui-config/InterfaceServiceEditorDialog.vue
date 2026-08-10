<template>
  <el-dialog
    v-model="visible"
    :title="editor.id ? '编辑接口服务' : '新增接口服务'"
    width="920px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-form :model="editor" label-width="96px">
      <div class="form-grid">
        <el-form-item label="服务名称" required>
          <el-input v-model="editor.sourceName" />
        </el-form-item>
        <el-form-item label="服务编码" required>
          <el-input v-model="editor.sourceCode" :disabled="Boolean(editor.id)" />
        </el-form-item>
        <el-form-item label="实现类型" required>
          <el-select
            v-model="editor.sourceType"
            @change="handleSourceTypeChange"
          >
            <el-option
              v-for="option in sourceTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="requiresProvider(editor.sourceType)"
          label="受控连接"
          required
        >
          <el-select
            v-model="editor.providerCode"
            filterable
            placeholder="选择后端注册 Provider 或 Connector"
          >
            <el-option
              v-for="option in providerOptions"
              :key="option.code"
              :label="option.name || option.code"
              :value="option.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="作用范围">
          <el-select v-model="editor.scopeType" @change="handleScopeTypeChange">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="实体" value="ENTITY" />
            <el-option label="表单" value="FORM" />
            <el-option label="列表" value="LIST" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editor.scopeType === 'ENTITY'" label="范围对象">
          <EntityDefinitionPicker
            v-model="editor.scopeId"
            value-key="id"
            title="选择接口服务作用实体"
            :query="{ storageMode: 'DYNAMIC' }"
          />
        </el-form-item>
        <el-form-item
          v-if="['FORM', 'LIST'].includes(editor.scopeType)"
          label="所属实体"
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
        <el-form-item label="启用">
          <el-switch v-model="editor.enabled" />
        </el-form-item>
      </div>

      <div class="advanced-config-summary">
        <div>
          <div class="section-title">高级 JSON 配置怎么分层</div>
          <div class="secondary-text">
            基础配置由全部操作共享，操作配置只覆盖当前操作的同名键；输入 Schema
            在执行前校验 input，输出 Schema 在执行后校验返回值，填写 {} 表示不校验。
          </div>
        </div>
        <el-button link type="primary" @click="openAdvancedConfigManual">
          <el-icon><Reading /></el-icon>
          完整配置说明
        </el-button>
      </div>

      <div
        v-if="providerConfigurationFields.length"
        class="provider-contract-guide"
      >
        <div class="provider-contract-guide__heading">
          <div>
            <div class="section-title">当前 Provider 可用配置</div>
            <div class="secondary-text">
              公共值放“基础配置”；只有某个操作不同的值放该操作的“操作配置”。
            </div>
          </div>
          <code>{{ editor.providerCode }}</code>
        </div>
        <div class="provider-contract-fields">
          <div
            v-for="field in providerConfigurationFields"
            :key="field.key"
            class="provider-contract-field"
          >
            <code>{{ field.key }}</code>
            <span>{{ field.title }}</span>
            <span v-if="field.hasDefault" class="provider-contract-default">
              默认：{{ formatDefaultValue(field.defaultValue) }}
            </span>
          </div>
        </div>
      </div>

      <div class="operations-heading">
        <div>
          <div class="section-title">服务操作</div>
          <div class="secondary-text">
            查询、详情、保存和校验等能力都作为同一服务下的操作维护。
          </div>
        </div>
        <el-button type="primary" plain @click="addOperation">
          <el-icon><Plus /></el-icon>
          增加操作
        </el-button>
      </div>

      <div
        v-for="(operation, index) in editor.operations"
        :key="operation.rowKey"
        class="operation-editor"
      >
        <div class="operation-heading">
          <span class="operation-index">{{ index + 1 }}</span>
          <el-input v-model="operation.name" placeholder="操作名称" />
          <el-button
            circle
            type="danger"
            title="删除操作"
            @click="editor.operations.splice(index, 1)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div class="operation-grid">
          <el-form-item label="操作编码" required>
            <el-input v-model="operation.code" />
          </el-form-item>
          <el-form-item label="数据影响">
            <el-segmented
              v-model="operation.kind"
              :options="[
                { label: '只读查询', value: 'READ' },
                { label: '修改数据', value: 'WRITE' }
              ]"
            />
          </el-form-item>
          <el-form-item label="上下文类型" required>
            <el-segmented
              v-model="operation.contextType"
              :options="[
                { label: '表单', value: 'FORM' },
                { label: '列表', value: 'LIST' },
                { label: '实体', value: 'ENTITY' }
              ]"
            />
          </el-form-item>
        </div>
        <el-collapse>
          <el-collapse-item title="高级配置（仅当前操作）" name="advanced">
            <div class="config-scope-note">
              当前操作执行时，平台先读取服务基础配置，再用这里的操作配置覆盖同名键，
              输入和输出 Schema 只属于当前操作。
            </div>
            <div class="json-grid">
              <div class="json-editor-field">
                <ConfigHelpLabel
                  label="操作配置"
                  help-key="interfaceService.operationConfig"
                />
                <el-input
                  v-model="operation.configText"
                  type="textarea"
                  :rows="7"
                  spellcheck="false"
                />
                <div class="json-editor-hint">
                  适合查询模式、目标字段、固定参数等当前操作独有的配置，不是运行时输入。
                </div>
              </div>
              <div class="json-editor-field">
                <ConfigHelpLabel
                  label="输入 Schema"
                  help-key="interfaceService.operationInputSchema"
                />
                <el-input
                  v-model="operation.inputSchemaText"
                  type="textarea"
                  :rows="7"
                  spellcheck="false"
                />
                <div class="json-editor-hint">
                  校验事件映射或运行组件最终传入的 input；缺少必填项或类型错误时不执行 Provider。
                </div>
              </div>
              <div class="json-editor-field">
                <ConfigHelpLabel
                  label="输出 Schema"
                  help-key="interfaceService.operationOutputSchema"
                />
                <el-input
                  v-model="operation.outputSchemaText"
                  type="textarea"
                  :rows="7"
                  spellcheck="false"
                />
                <div class="json-editor-hint">
                  校验 Provider、Connector 或平台数据源的最终返回值，也会校验缓存结果。
                </div>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <el-collapse>
        <el-collapse-item title="服务级高级配置" name="service-advanced">
          <div class="config-scope-note">
            基础配置会传给全部操作，适合放 Provider 公共参数；每个操作再覆盖自己的同名键。
          </div>
          <div class="service-config-grid">
            <div class="json-editor-field">
              <ConfigHelpLabel
                label="基础配置"
                help-key="interfaceService.baseConfig"
              />
              <el-input
                v-model="editor.configText"
                type="textarea"
                :rows="7"
                spellcheck="false"
              />
              <div class="json-editor-hint">
                适合同一服务全部操作共享的字典编码、Provider 参数或 Connector 配置编码。
              </div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        保存接口服务
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Delete, Plus, Reading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import { getFormById, getFormsByEntity } from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiDataSourceApi } from '@/api/uiConfig'
import {
  executionPolicy,
  parseEditorJson,
  parseJson,
  requiresProvider,
  serviceOperations
} from './interfaceServiceModel'

const props = defineProps({
  catalog: { type: Object, default: () => ({}) },
  sourceTypeOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['saved'])
const router = useRouter()
const visible = ref(false)
const saving = ref(false)
const scopeLoading = ref(false)
const scopeEntityId = ref('')
const scopeObjects = ref([])
let rowSequence = 0
const editor = reactive(emptyEditor())

const providerOptions = computed(() =>
  editor.sourceType === 'INTEGRATION_CONNECTOR'
    ? props.catalog.connectors || []
    : props.catalog.providers || [])

const selectedProvider = computed(() =>
  editor.sourceType === 'REGISTERED_PROVIDER'
    ? (props.catalog.providers || []).find(
      provider => provider.code === editor.providerCode
    )
    : null)

const providerConfigurationFields = computed(() => {
  const properties = selectedProvider.value?.schema?.properties
  if (!properties || typeof properties !== 'object') return []
  return Object.entries(properties).map(([key, schema]) => ({
    key,
    title: schema?.title || schema?.description || key,
    hasDefault: Object.prototype.hasOwnProperty.call(schema || {}, 'default'),
    defaultValue: schema?.default
  }))
})

function emptyEditor() {
  return {
    id: '',
    expectedRevision: null,
    sourceCode: '',
    sourceName: '',
    sourceType: 'REGISTERED_PROVIDER',
    providerCode: '',
    scopeType: 'ENTITY',
    scopeId: '',
    timeoutMs: 3000,
    cacheSeconds: 0,
    enabled: true,
    configText: '{}',
    operations: []
  }
}

function operationEditor(operation = {}) {
  return {
    rowKey: `operation_${++rowSequence}`,
    code: operation.code || '',
    name: operation.name || '',
    kind: operation.kind || 'READ',
    contextType: operation.contextType || 'FORM',
    configText: JSON.stringify(operation.config || {}, null, 2),
    inputSchemaText: JSON.stringify(operation.inputSchema || {}, null, 2),
    outputSchemaText: JSON.stringify(operation.outputSchema || {}, null, 2)
  }
}

function reset(value = {}) {
  Object.assign(editor, emptyEditor(), value)
}

function openCreate() {
  scopeEntityId.value = ''
  scopeObjects.value = []
  reset({
    operations: [operationEditor({
      code: 'query',
      name: '查询数据',
      contextType: 'FORM'
    })]
  })
  visible.value = true
}

async function openEdit(service) {
  const policy = executionPolicy(service)
  scopeEntityId.value = ''
  scopeObjects.value = []
  reset({
    id: service.id,
    expectedRevision: service.revision,
    sourceCode: service.sourceCode,
    sourceName: service.sourceName,
    sourceType: service.sourceType,
    providerCode: service.providerCode || '',
    scopeType: service.scopeType || 'GLOBAL',
    scopeId: service.scopeId || '',
    timeoutMs: Number(policy.timeoutMs || 3000),
    cacheSeconds: Number(policy.cacheSeconds || 0),
    enabled: service.enabled !== false,
    configText: JSON.stringify(parseJson(service.configDocument, {}), null, 2),
    operations: serviceOperations(service).map(operationEditor)
  })
  await resolveScopeObject(service)
  visible.value = true
}

function addOperation() {
  editor.operations.push(operationEditor({
    contextType: editor.scopeType === 'LIST'
      ? 'LIST'
      : editor.scopeType === 'FORM'
        ? 'FORM'
        : 'FORM'
  }))
}

async function handleScopeTypeChange(scopeType) {
  editor.scopeId = ''
  scopeEntityId.value = ''
  scopeObjects.value = []
  if (scopeType === 'FORM' || scopeType === 'LIST') {
    editor.operations.forEach(operation => {
      operation.contextType = scopeType
    })
  }
}

function handleSourceTypeChange(sourceType) {
  if (requiresProvider(sourceType)
    && editor.scopeType === 'GLOBAL') {
    editor.scopeType = 'ENTITY'
    editor.scopeId = ''
    scopeEntityId.value = ''
    scopeObjects.value = []
  }
}

async function handleScopeEntitySelected(entity) {
  scopeEntityId.value = entity?.id || ''
  editor.scopeId = ''
  await loadScopeObjects()
}

async function loadScopeObjects() {
  if (!scopeEntityId.value
    || !['FORM', 'LIST'].includes(editor.scopeType)) {
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

async function resolveScopeObject(service) {
  if (service.scopeType === 'FORM' && service.scopeId) {
    const form = await getFormById(service.scopeId)
    scopeEntityId.value = form?.entityId || ''
    await loadScopeObjects()
  } else if (service.scopeType === 'LIST' && service.scopeId) {
    const list = await entityListConfigApi.getById(service.scopeId)
    scopeEntityId.value = list?.entityId || ''
    await loadScopeObjects()
  }
}

function formatDefaultValue(value) {
  if (value === '') return '空'
  if (value === null) return 'null'
  return typeof value === 'object'
    ? JSON.stringify(value)
    : String(value)
}

async function openAdvancedConfigManual() {
  visible.value = false
  await router.push({
    path: '/manual/interface-service',
    hash: '#interface-service-advanced-config'
  })
}

async function save() {
  if (!editor.sourceName || !editor.sourceCode) {
    ElMessage.warning('请填写服务名称和编码')
    return
  }
  if (!editor.operations.length) {
    ElMessage.warning('接口服务至少需要一个操作')
    return
  }
  saving.value = true
  try {
    const operations = editor.operations.map(operation => {
      if (!operation.code || !operation.name || !operation.contextType) {
        throw new Error('每个操作都必须填写名称、编码和上下文类型')
      }
      return {
        code: operation.code,
        name: operation.name,
        kind: operation.kind,
        contextType: operation.contextType,
        config: parseEditorJson(operation.configText, `${operation.name}操作配置`),
        inputSchema: parseEditorJson(operation.inputSchemaText, `${operation.name}输入 Schema`),
        outputSchema: parseEditorJson(operation.outputSchemaText, `${operation.name}输出 Schema`)
      }
    })
    const payload = {
      expectedRevision: editor.expectedRevision,
      sourceCode: editor.sourceCode,
      sourceName: editor.sourceName,
      sourceType: editor.sourceType,
      providerCode: requiresProvider(editor.sourceType) ? editor.providerCode : null,
      scopeType: editor.scopeType,
      scopeId: editor.scopeType === 'GLOBAL' ? null : editor.scopeId,
      config: parseEditorJson(editor.configText, '基础配置'),
      executionPolicy: {
        timeoutMs: editor.timeoutMs,
        cacheSeconds: editor.cacheSeconds,
        failurePolicy: 'FAIL'
      },
      operations,
      enabled: editor.enabled
    }
    if (editor.id) {
      await uiDataSourceApi.update(editor.id, payload)
    } else {
      await uiDataSourceApi.create(payload)
    }
    visible.value = false
    ElMessage.success('接口服务已保存')
    emit('saved')
  } catch (error) {
    ElMessage.error(error.message || '保存接口服务失败')
  } finally {
    saving.value = false
  }
}

defineExpose({ openCreate, openEdit })
</script>

<style scoped>
.form-grid,
.operation-grid,
.json-grid {
  display: grid;
  gap: 12px;
}

.form-grid,
.operation-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.json-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.service-config-grid {
  max-width: 520px;
}

.operations-heading,
.operation-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.operations-heading {
  margin: 10px 0;
}

.advanced-config-summary,
.provider-contract-guide {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin: 12px 0;
  padding: 12px 0 12px 12px;
  border-left: 3px solid var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
}

.provider-contract-guide {
  display: block;
  border-left-color: var(--el-color-success-light-5);
}

.provider-contract-guide__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-right: 12px;
}

.provider-contract-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 10px;
  padding-right: 12px;
}

.provider-contract-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 220px;
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.provider-contract-field code,
.provider-contract-guide__heading code,
.config-scope-note code {
  padding: 1px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-bg-color);
  color: var(--el-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.provider-contract-default {
  color: var(--el-text-color-secondary);
}

.section-title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.secondary-text {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.operation-editor {
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
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

.json-editor-field > .config-help-label {
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.json-editor-hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.operation-heading {
  margin-bottom: 10px;
}

.operation-index {
  display: flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--el-color-primary);
  font-weight: 600;
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
}

@media (max-width: 1000px) {
  .form-grid,
  .operation-grid,
  .json-grid {
    grid-template-columns: 1fr;
  }

  .advanced-config-summary,
  .provider-contract-guide__heading {
    flex-direction: column;
  }
}
</style>
