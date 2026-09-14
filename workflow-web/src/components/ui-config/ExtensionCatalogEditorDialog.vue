<template>
  <el-dialog
    v-model="visible"
    :title="title"
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
          <el-segmented v-model="editor.visibilityScope" :options="visibilityOptions" />
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
          <el-col v-if="editor.extensionType === 'FORM'" :span="24">
            <el-form-item label="适用范围" required>
              <el-segmented v-model="editor.visibilityScope" :options="visibilityOptions" />
            </el-form-item>
          </el-col>
          <el-col
            v-if="editor.extensionType === 'FORM' && editor.visibilityScope === 'ENTITY'"
            :span="24"
          >
            <el-form-item label="指定实体" required>
              <EntityDefinitionPicker
                v-model="editor.entityCodes"
                multiple
                value-key="entityCode"
                title="选择表单组件适用实体"
                placeholder="选择一个或多个已发布实体"
                :query="{ status: 'PUBLISHED', storageMode: 'DYNAMIC' }"
              />
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
              <el-segmented v-model="editor.status" :options="uiStatusOptions" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import { personResolverApi } from '@/api/system/extension'
import { processActionApi } from '@/api/processAction'
import { uiExtensionApi } from '@/api/uiConfig'

const emit = defineEmits(['saved'])

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

const visible = ref(false)
const saving = ref(false)
const editor = reactive(emptyEditor())
const title = computed(() => {
  if (editor.kind === 'FLOW_ACTION') {
    return editor.configured ? '编辑流程动作目录' : '纳管流程动作'
  }
  if (editor.kind === 'PERSON_RESOLVER') {
    return editor.configured ? '编辑人员接口目录' : '纳管人员接口'
  }
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
    visibilityScope: 'GLOBAL',
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

function reset(value = {}) {
  Object.assign(editor, emptyEditor(), value)
}

/** 打开原有非接口目录项；扩展接口由专用编辑器负责。 */
function open(row) {
  if (row.capabilityType === 'FLOW_ACTION') {
    reset({
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
    reset({
      kind: 'PERSON_RESOLVER',
      configured: row.configured,
      key: row.key,
      displayName: row.displayName || '',
      description: row.description || '',
      supportedUsages: [...(row.supportedUsages || [])],
      enabled: row.configured ? row.enabled !== false : false
    })
  } else {
    reset({
      kind: row.capabilityType,
      id: row.id,
      configured: row.configured,
      key: row.key,
      displayName: row.displayName || '',
      extensionType: row.capabilityType.replace(/^UI_/, ''),
      implementationVersion: row.implementationVersion || 1,
      snapshotVersion: row.snapshotVersion || 1,
      visibilityScope: row.visibilityScope || 'GLOBAL',
      entityCodes: [...(row.entityCodes || [])],
      supportedModes: [...(row.supportedModes || [])],
      supportedNodeTypes: [...(row.supportedNodeTypes || [])],
      supportedBindings: [...(row.supportedBindings || [])],
      configSchemaText: formatJson(row.configSchema || []),
      capabilitiesText: formatJson(row.capabilities || {}),
      status: row.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE',
      revision: row.revision
    })
  }
  visible.value = true
}

function openCreateUi() {
  reset()
  visible.value = true
}

/** 按目录类型保存原有动作、人员接口或 UI 扩展配置。 */
async function save() {
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
      if (!(await saveUiExtension())) return
    }
    ElMessage.success('扩展目录已保存')
    visible.value = false
    emit('saved')
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

async function saveUiExtension() {
  if (!editor.key?.trim()) {
    ElMessage.warning('请填写扩展注册名')
    return false
  }
  if (editor.extensionType === 'FORM'
      && editor.visibilityScope === 'ENTITY'
      && !editor.entityCodes.length) {
    ElMessage.warning('指定实体范围至少选择一个实体')
    return false
  }
  const payload = {
    extensionType: editor.extensionType,
    extensionKey: editor.key.trim(),
    displayName: editor.displayName.trim(),
    version: editor.implementationVersion,
    snapshotVersion: editor.snapshotVersion,
    visibilityScope: editor.extensionType === 'FORM'
      ? editor.visibilityScope
      : 'GLOBAL',
    entityCodes: editor.extensionType === 'FORM'
      && editor.visibilityScope === 'ENTITY'
      ? editor.entityCodes
      : [],
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
  return true
}

function formatJson(value) {
  return JSON.stringify(value ?? {}, null, 2)
}

function parseJson(value, fallback) {
  if (!value?.trim()) return fallback
  return JSON.parse(value)
}

defineExpose({ open, openCreateUi })
</script>
