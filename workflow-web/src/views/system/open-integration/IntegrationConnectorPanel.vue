<template>
  <div class="resource-panel">
    <div class="resource-toolbar">
      <div class="resource-count">{{ connectors.length }} 个 Connector 配置</div>
      <div class="resource-actions">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建配置
        </el-button>
      </div>
    </div>

    <PageState
      v-if="error"
      type="error"
      title="Connector 配置加载失败"
      :description="error"
      retryable
      compact
      @retry="load"
    />

    <el-table
      v-else
      v-loading="loading"
      :data="connectors"
      stripe
      border
      row-key="id"
    >
      <el-table-column label="配置" min-width="190">
        <template #default="{ row }">
          <div class="primary-line">{{ row.configName }}</div>
          <div class="secondary-line">{{ row.connectorCode }} · {{ row.id }}</div>
        </template>
      </el-table-column>
      <el-table-column label="允许主机" min-width="180">
        <template #default="{ row }">
          <el-tag
            v-for="host in row.allowedHosts"
            :key="host"
            size="small"
            effect="plain"
            class="host-tag"
          >
            {{ host }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作数" width="90" align="center">
        <template #default="{ row }">
          {{ Object.keys(row.configuration?.operations || {}).length }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canManage" link type="primary" @click="openEdit(row)">
            编辑
          </el-button>
          <el-button v-if="canManage" link type="primary" @click="openTest(row)">
            测试
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无 HTTP Connector 配置" :image-size="64" />
      </template>
    </el-table>

    <el-dialog
      v-model="editorVisible"
      :title="editing ? '编辑 Connector 配置' : '新建 Connector 配置'"
      width="min(820px, 96vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="配置名称" prop="configName">
            <el-input v-model="form.configName" maxlength="128" />
          </el-form-item>
          <el-form-item v-if="editing" label="状态">
            <el-switch
              v-model="form.active"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </div>
        <el-form-item label="允许主机" prop="allowedHosts">
          <el-input
            v-model="form.allowedHosts"
            type="textarea"
            :rows="2"
            placeholder="每行一个精确域名，不支持通配符或 IP"
          />
        </el-form-item>
        <el-form-item label="声明式配置" prop="configuration">
          <el-input
            v-model="form.configuration"
            type="textarea"
            :rows="18"
            spellcheck="false"
            class="json-editor"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="testVisible"
      title="Connector 连接测试"
      width="min(680px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="操作" required>
          <el-select v-model="testForm.operation" style="width: 100%">
            <el-option
              v-for="operation in testOperations"
              :key="operation"
              :label="operation"
              :value="operation"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="脱敏测试输入" required>
          <el-input
            v-model="testForm.input"
            type="textarea"
            :rows="10"
            spellcheck="false"
            class="json-editor"
          />
        </el-form-item>
        <el-form-item v-if="testResult" label="测试结果">
          <el-input
            :model-value="testResult"
            type="textarea"
            :rows="8"
            readonly
            class="json-editor"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="runTest">
          执行测试
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { integrationConnectorApi } from '@/api/system/openIntegration'

const props = defineProps({
  applicationId: { type: String, required: true },
  canManage: { type: Boolean, default: false }
})

const connectors = ref([])
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const error = ref('')
const editorVisible = ref(false)
const testVisible = ref(false)
const editing = ref(null)
const testingConnector = ref(null)
const formRef = ref()
const testResult = ref('')
const form = reactive({
  configName: '',
  active: true,
  allowedHosts: '',
  configuration: ''
})
const testForm = reactive({ operation: '', input: '{\n  \n}' })
const rules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  allowedHosts: [{
    validator: (_rule, value, callback) => {
      const hosts = hostLines(value)
      if (!hosts.length) callback(new Error('至少配置一个允许主机'))
      else if (hosts.some(host => !/^(?=.{1,253}$)(?!-)[A-Za-z0-9.-]+(?<!-)$/.test(host))) {
        callback(new Error('允许主机必须是精确域名'))
      } else callback()
    },
    trigger: 'blur'
  }],
  configuration: [{
    validator: (_rule, value, callback) => {
      try {
        const parsed = JSON.parse(value)
        if (!parsed.baseUrl || !parsed.operations || Array.isArray(parsed.operations)) throw new Error()
        callback()
      } catch {
        callback(new Error('配置必须是包含 baseUrl 和 operations 的 JSON 对象'))
      }
    },
    trigger: 'blur'
  }]
}
const testOperations = computed(() =>
  Object.keys(testingConnector.value?.configuration?.operations || {})
)

onMounted(load)
watch(() => props.applicationId, load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    connectors.value = await integrationConnectorApi.list(props.applicationId) || []
  } catch (requestError) {
    error.value = requestError.message || '请求失败'
  } finally {
    loading.value = false
  }
}

function defaultConfiguration() {
  return {
    baseUrl: 'https://api.example.com',
    operations: {
      lookup: {
        method: 'GET',
        path: '/v1/resources',
        query: { id: '$input.id' },
        response: { remoteId: '/data/id' },
        acceptedStatuses: [200],
    authentication: { type: 'NONE' },
        timeoutMs: 4000,
        maxAttempts: 2
      }
    }
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    configName: '',
    active: true,
    allowedHosts: 'api.example.com',
    configuration: JSON.stringify(defaultConfiguration(), null, 2)
  })
  editorVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    configName: row.configName,
    active: row.status === 'ACTIVE',
    allowedHosts: row.allowedHosts.join('\n'),
    configuration: JSON.stringify(row.configuration, null, 2)
  })
  editorVisible.value = true
}

async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    const payload = {
      configName: form.configName,
      configuration: JSON.parse(form.configuration),
      allowedHosts: hostLines(form.allowedHosts)
    }
    if (editing.value) {
      await integrationConnectorApi.update(
        props.applicationId,
        editing.value.id,
        {
          ...payload,
          expectedVersion: editing.value.version,
          status: form.active ? 'ACTIVE' : 'DISABLED'
        }
      )
      ElMessage.success('Connector 配置已更新')
    } else {
      await integrationConnectorApi.create(props.applicationId, payload)
      ElMessage.success('Connector 配置已创建')
    }
    editorVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openTest(row) {
  testingConnector.value = row
  testForm.operation = Object.keys(row.configuration?.operations || {})[0] || ''
  testForm.input = '{\n  \n}'
  testResult.value = ''
  testVisible.value = true
}

async function runTest() {
  let input
  try {
    input = JSON.parse(testForm.input)
    if (!input || Array.isArray(input) || typeof input !== 'object') throw new Error()
  } catch {
    ElMessage.error('测试输入必须是 JSON 对象')
    return
  }
  if (!testForm.operation) {
    ElMessage.warning('请选择操作')
    return
  }
  testing.value = true
  try {
    const result = await integrationConnectorApi.test(
      props.applicationId,
      testingConnector.value.id,
      { operation: testForm.operation, input }
    )
    testResult.value = JSON.stringify(result, null, 2)
    ElMessage.success('连接测试已完成')
  } finally {
    testing.value = false
  }
}

function hostLines(value) {
  return String(value || '')
    .split(/\r?\n/)
    .map(host => host.trim().toLowerCase())
    .filter(Boolean)
}
</script>

<style scoped>
.resource-toolbar,
.resource-actions {
  display: flex;
  align-items: center;
}

.resource-toolbar {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.resource-actions {
  gap: 8px;
}

.resource-count,
.secondary-line {
  color: #737985;
  font-size: 12px;
}

.primary-line {
  color: #252a31;
  font-weight: 600;
}

.host-tag {
  margin: 2px 4px 2px 0;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  gap: 16px;
}

.json-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

@media (max-width: 620px) {
  .resource-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .resource-actions,
  .resource-actions :deep(.el-button) {
    width: 100%;
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
