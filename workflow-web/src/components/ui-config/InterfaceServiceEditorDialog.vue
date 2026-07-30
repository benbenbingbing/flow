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
          <el-select v-model="editor.sourceType">
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
          <el-select v-model="editor.scopeType">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="实体" value="ENTITY" />
            <el-option label="表单" value="FORM" />
            <el-option label="列表" value="LIST" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editor.scopeType !== 'GLOBAL'" label="范围对象">
          <el-select
            v-if="editor.scopeType === 'ENTITY'"
            v-model="editor.scopeId"
            filterable
          >
            <el-option
              v-for="entity in entities"
              :key="entity.id"
              :label="`${entity.entityName} (${entity.entityCode})`"
              :value="entity.id"
            />
          </el-select>
          <el-input
            v-else
            v-model="editor.scopeId"
            placeholder="填写表单或列表 ID"
          />
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
        </div>
        <el-collapse>
          <el-collapse-item title="高级配置" name="advanced">
            <div class="json-grid">
              <el-form-item label="操作配置">
                <el-input v-model="operation.configText" type="textarea" :rows="6" />
              </el-form-item>
              <el-form-item label="输入 Schema">
                <el-input v-model="operation.inputSchemaText" type="textarea" :rows="6" />
              </el-form-item>
              <el-form-item label="输出 Schema">
                <el-input v-model="operation.outputSchemaText" type="textarea" :rows="6" />
              </el-form-item>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <el-collapse>
        <el-collapse-item title="服务级高级配置" name="service-advanced">
          <div class="json-grid">
            <el-form-item label="基础配置">
              <el-input v-model="editor.configText" type="textarea" :rows="6" />
            </el-form-item>
            <el-form-item label="输入 Schema">
              <el-input v-model="editor.inputSchemaText" type="textarea" :rows="6" />
            </el-form-item>
            <el-form-item label="输出 Schema">
              <el-input v-model="editor.outputSchemaText" type="textarea" :rows="6" />
            </el-form-item>
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
import { Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uiDataSourceApi } from '@/api/uiConfig'
import {
  executionPolicy,
  parseEditorJson,
  parseJson,
  requiresProvider,
  serviceOperations
} from './interfaceServiceModel'

const props = defineProps({
  entities: { type: Array, default: () => [] },
  catalog: { type: Object, default: () => ({}) },
  sourceTypeOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['saved'])
const visible = ref(false)
const saving = ref(false)
let rowSequence = 0
const editor = reactive(emptyEditor())

const providerOptions = computed(() =>
  editor.sourceType === 'INTEGRATION_CONNECTOR'
    ? props.catalog.connectors || []
    : props.catalog.providers || [])

function emptyEditor() {
  return {
    id: '',
    expectedRevision: null,
    sourceCode: '',
    sourceName: '',
    sourceType: 'REGISTERED_PROVIDER',
    providerCode: '',
    scopeType: 'GLOBAL',
    scopeId: '',
    timeoutMs: 3000,
    cacheSeconds: 0,
    enabled: true,
    configText: '{}',
    inputSchemaText: '{}',
    outputSchemaText: '{}',
    operations: []
  }
}

function operationEditor(operation = {}) {
  return {
    rowKey: `operation_${++rowSequence}`,
    code: operation.code || '',
    name: operation.name || '',
    kind: operation.kind || 'READ',
    configText: JSON.stringify(operation.config || {}, null, 2),
    inputSchemaText: JSON.stringify(operation.inputSchema || {}, null, 2),
    outputSchemaText: JSON.stringify(operation.outputSchema || {}, null, 2)
  }
}

function reset(value = {}) {
  Object.assign(editor, emptyEditor(), value)
}

function openCreate() {
  reset({
    operations: [operationEditor({ code: 'query', name: '查询数据' })]
  })
  visible.value = true
}

function openEdit(service) {
  const policy = executionPolicy(service)
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
    inputSchemaText: JSON.stringify(parseJson(service.inputSchemaDocument, {}), null, 2),
    outputSchemaText: JSON.stringify(parseJson(service.outputSchemaDocument, {}), null, 2),
    operations: serviceOperations(service).map(operationEditor)
  })
  visible.value = true
}

function addOperation() {
  editor.operations.push(operationEditor())
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
      if (!operation.code || !operation.name) {
        throw new Error('每个操作都必须填写名称和编码')
      }
      return {
        code: operation.code,
        name: operation.name,
        kind: operation.kind,
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
      inputSchema: parseEditorJson(editor.inputSchemaText, '输入 Schema'),
      outputSchema: parseEditorJson(editor.outputSchemaText, '输出 Schema'),
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
}
</style>
