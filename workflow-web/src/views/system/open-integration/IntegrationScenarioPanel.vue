<template>
  <section class="scenario-panel" aria-label="流程场景">
    <div class="scenario-toolbar">
      <div>
        <strong>流程场景</strong>
        <span class="scenario-count">{{ scenarios.length }} 个</span>
      </div>
      <div class="scenario-actions">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建场景
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="error"
      type="error"
      :closable="false"
      :title="error"
      show-icon
    />
    <el-table v-loading="loading" :data="scenarios" row-key="id" size="small">
      <el-table-column prop="scenarioKey" label="场景 Key" min-width="150" />
      <el-table-column prop="processKey" label="流程 Key" min-width="150" />
      <el-table-column label="版本" width="110">
        <template #default="{ row }">
          {{ row.processDefinitionVersion || '跟随发布' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ row.status === 'ACTIVE' ? '已发布' : row.status === 'DRAFT' ? '草稿' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="revision" label="修订" width="80" />
      <el-table-column v-if="canManage" label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="canManage && row.draftRevision"
            link
            type="success"
            @click="publish(row)"
          >
            发布
          </el-button>
          <el-button
            v-if="row.status === 'ACTIVE'"
            link
            type="danger"
            @click="disable(row)"
          >
            停用
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无流程场景" :image-size="64" />
      </template>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑流程场景' : '新建流程场景'"
      width="min(820px, 96vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="场景 Key" prop="scenarioKey">
            <el-input v-model="form.scenarioKey" :disabled="editing" maxlength="100" />
          </el-form-item>
          <el-form-item label="显示名称" prop="displayName">
            <el-input v-model="form.displayName" maxlength="128" />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="流程 Key" prop="processKey">
            <el-select v-model="form.processKey" filterable allow-create style="width: 100%">
              <el-option
                v-for="key in application.processKeys || []"
                :key="key"
                :label="key"
                :value="key"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="固定流程版本">
            <el-input-number v-model="form.processDefinitionVersion" :min="1" :max="999999" />
          </el-form-item>
        </div>
        <el-form-item label="输入 Schema" prop="inputSchemaText">
          <el-input v-model="form.inputSchemaText" type="textarea" :rows="7" spellcheck="false" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="结果映射" prop="outcomeMappingText">
            <el-input v-model="form.outcomeMappingText" type="textarea" :rows="5" spellcheck="false" />
          </el-form-item>
          <el-form-item label="身份映射" prop="identityMappingText">
            <el-input v-model="form.identityMappingText" type="textarea" :rows="5" spellcheck="false" />
          </el-form-item>
        </div>
        <el-form-item label="事件白名单" prop="eventTypes">
          <el-checkbox-group v-model="form.eventTypes" class="event-types">
            <el-checkbox v-for="eventType in eventTypeOptions" :key="eventType" :label="eventType">
              {{ eventType }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="validating" @click="validate">校验配置</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationScenarioApi } from '@/api/system/openIntegration'

const props = defineProps({
  application: { type: Object, required: true },
  canManage: { type: Boolean, default: false }
})

const eventTypeOptions = [
  'com.flow.process.started.v1',
  'com.flow.task.created.v1',
  'com.flow.task.completed.v1',
  'com.flow.process.completed.v1',
  'com.flow.process.terminated.v1',
  'com.flow.process.failed.v1'
]
const scenarios = ref([])
const loading = ref(false)
const saving = ref(false)
const validating = ref(false)
const error = ref('')
const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive(defaultForm())
const rules = {
  scenarioKey: [{ required: true, pattern: /^[A-Za-z][A-Za-z0-9._-]{0,99}$/, message: '请输入合法场景 Key', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  processKey: [{ required: true, message: '请选择流程 Key', trigger: 'change' }],
  inputSchemaText: [{ required: true, message: '请输入输入 Schema', trigger: 'blur' }],
  outcomeMappingText: [{ required: true, message: '请输入结果映射', trigger: 'blur' }],
  identityMappingText: [{ required: true, message: '请输入身份映射', trigger: 'blur' }],
  eventTypes: [{ required: true, type: 'array', min: 1, message: '至少选择一个事件', trigger: 'change' }]
}

watch(() => props.application.id, load)
onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    scenarios.value = await integrationScenarioApi.list(props.application.id) || []
  } catch (cause) {
    error.value = cause.message || '流程场景加载失败'
  } finally {
    loading.value = false
  }
}

function defaultForm() {
  return {
    scenarioKey: '',
    displayName: '',
    processKey: '',
    processDefinitionVersion: null,
    expectedRevision: null,
    inputSchemaText: JSON.stringify({ type: 'object', maxProperties: 20, additionalProperties: false }, null, 2),
    outcomeMappingText: '{}',
    identityMappingText: JSON.stringify({ namespace: 'external', initiator: 'variables.requesterId' }, null, 2),
    eventTypes: ['com.flow.process.started.v1', 'com.flow.task.completed.v1']
  }
}

function openCreate() {
  Object.assign(form, defaultForm(), { processKey: props.application.processKeys?.[0] || '' })
  editing.value = false
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    scenarioKey: row.scenarioKey,
    displayName: row.displayName,
    processKey: row.processKey,
    processDefinitionVersion: row.processDefinitionVersion,
    inputSchemaText: JSON.stringify(row.inputSchema, null, 2),
    outcomeMappingText: JSON.stringify(row.outcomeMapping, null, 2),
    identityMappingText: JSON.stringify(row.identityMapping, null, 2),
    eventTypes: [...row.eventTypes]
  })
  form.expectedRevision = row.revision
  editing.value = true
  dialogVisible.value = true
}

function parseObject(value, label) {
  let parsed
  try {
    parsed = JSON.parse(value)
  } catch {
    throw new Error(`${label}必须是合法 JSON`)
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(`${label}必须是 JSON 对象`)
  return parsed
}

async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  let inputSchema; let outcomeMapping; let identityMapping
  try {
    inputSchema = parseObject(form.inputSchemaText, '输入 Schema')
    outcomeMapping = parseObject(form.outcomeMappingText, '结果映射')
    identityMapping = parseObject(form.identityMappingText, '身份映射')
  } catch (cause) {
    ElMessage.error(cause.message)
    return
  }
  saving.value = true
  try {
    const configuration = {
      scenarioKey: form.scenarioKey.trim(),
      displayName: form.displayName.trim(),
      processKey: form.processKey.trim(),
      processDefinitionVersion: form.processDefinitionVersion || null,
      inputSchema,
      outcomeMapping,
      identityMapping,
      eventTypes: form.eventTypes
    }
    if (editing.value) {
      await integrationScenarioApi.update(props.application.id, form.scenarioKey, {
        expectedRevision: form.expectedRevision,
        configuration
      })
    } else {
      await integrationScenarioApi.create(props.application.id, configuration)
    }
    dialogVisible.value = false
    await load()
    ElMessage.success(editing.value ? '流程场景已更新' : '流程场景已创建')
  } catch (cause) {
    ElMessage.error(cause.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function validate() {
  if (!await formRef.value?.validate().catch(() => false)) return
  let inputSchema; let outcomeMapping; let identityMapping
  try {
    inputSchema = parseObject(form.inputSchemaText, '输入 Schema')
    outcomeMapping = parseObject(form.outcomeMappingText, '结果映射')
    identityMapping = parseObject(form.identityMappingText, '身份映射')
  } catch (cause) {
    ElMessage.error(cause.message)
    return
  }
  validating.value = true
  try {
    const result = await integrationScenarioApi.validate(props.application.id, {
      scenarioKey: form.scenarioKey.trim(),
      displayName: form.displayName.trim(),
      processKey: form.processKey.trim(),
      processDefinitionVersion: form.processDefinitionVersion || null,
      inputSchema,
      outcomeMapping,
      identityMapping,
      eventTypes: form.eventTypes
    })
    ElMessage.success(`配置校验通过，摘要 ${result.configHash}`)
  } catch (cause) {
    ElMessage.error(cause.message || '配置校验失败')
  } finally {
    validating.value = false
  }
}

async function disable(row) {
  await ElMessageBox.confirm('停用后不能再用此场景发起新流程，历史实例保留原配置。', '停用流程场景', { type: 'warning' })
  try {
    await integrationScenarioApi.disable(props.application.id, row.scenarioKey, { expectedRevision: row.revision })
    await load()
    ElMessage.success('流程场景已停用')
  } catch (cause) {
    ElMessage.error(cause.message || '停用失败')
  }
}

async function publish(row) {
  try {
    await ElMessageBox.confirm(
      `确认发布场景 ${row.scenarioKey} 的 revision ${row.draftRevision}？`,
      '发布流程场景',
      { type: 'warning' }
    )
    await integrationScenarioApi.publish(props.application.id, row.scenarioKey, {
      expectedRevision: row.draftRevision
    })
    ElMessage.success('流程场景已发布')
    await load()
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') ElMessage.error(cause.message || '发布失败')
  }
}
</script>

<style scoped>
.scenario-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.scenario-count {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
.scenario-actions {
  display: flex;
  gap: 8px;
}
.event-types {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}
@media (max-width: 720px) {
  .scenario-toolbar { align-items: flex-start; flex-direction: column; }
  .event-types { grid-template-columns: 1fr; }
}
</style>
