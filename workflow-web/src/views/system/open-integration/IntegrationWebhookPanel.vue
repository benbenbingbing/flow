<template>
  <div class="resource-panel">
    <div class="resource-toolbar">
      <el-segmented
        v-model="view"
        :options="[
          { label: '订阅端点', value: 'endpoints' },
          { label: '投递记录', value: 'deliveries' }
        ]"
      />
      <div class="resource-actions">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button
          v-if="view === 'endpoints' && canManage"
          type="primary"
          @click="openCreate"
        >
          <el-icon><Plus /></el-icon>
          新建端点
        </el-button>
      </div>
    </div>

    <PageState
      v-if="error"
      type="error"
      title="Webhook 数据加载失败"
      :description="error"
      retryable
      compact
      @retry="load"
    />

    <template v-else-if="view === 'endpoints'">
      <el-table v-loading="loading" :data="endpoints" stripe border row-key="id">
        <el-table-column label="端点" min-width="190">
          <template #default="{ row }">
            <div class="primary-line">{{ row.endpointName }}</div>
            <div class="secondary-line" :title="row.endpointUrl">{{ row.endpointUrl }}</div>
          </template>
        </el-table-column>
        <el-table-column label="事件" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="eventType in row.eventTypes"
              :key="eventType"
              size="small"
              effect="plain"
              class="event-tag"
            >
              {{ shortEventType(eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签名密钥" width="140">
          <template #default="{ row }">
            <div>v{{ row.secretVersion }}</div>
            <div class="secondary-line">{{ row.secretHint }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canManage && row.status === 'ACTIVE'"
              link
              type="primary"
              @click="validateEndpoint(row)"
            >
              验证
            </el-button>
            <el-button v-if="canManage" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-if="canRotate" link type="primary" @click="rotate(row)">
              轮换密钥
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无 Webhook 端点" :image-size="64" />
        </template>
      </el-table>
    </template>

    <template v-else>
      <el-table v-loading="loading" :data="deliveries" stripe border row-key="id">
        <el-table-column label="事件" min-width="190">
          <template #default="{ row }">
            <div class="primary-line">{{ shortEventType(row.eventType) }}</div>
            <div class="secondary-line">{{ row.eventId }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="endpointName" label="端点" min-width="140" />
        <el-table-column label="状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="deliveryStatusType(row.status)">
              {{ deliveryStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="尝试" width="90" align="center">
          <template #default="{ row }">{{ row.attemptCount }}/{{ row.maxAttempts }}</template>
        </el-table-column>
        <el-table-column label="HTTP" width="80" align="center">
          <template #default="{ row }">{{ row.responseStatus || '-' }}</template>
        </el-table-column>
        <el-table-column label="最后尝试" min-width="155">
          <template #default="{ row }">{{ formatTime(row.lastAttemptAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canReplay && row.status === 'DEAD'"
              link
              type="primary"
              @click="replay(row)"
            >
              重放
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无投递记录" :image-size="64" />
        </template>
      </el-table>
    </template>

    <el-dialog
      v-model="editorVisible"
      :title="editing ? '编辑 Webhook 端点' : '新建 Webhook 端点'"
      width="min(680px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="端点名称" prop="endpointName">
          <el-input v-model="form.endpointName" maxlength="128" />
        </el-form-item>
        <el-form-item label="目标 URL" prop="endpointUrl">
          <el-input v-model="form.endpointUrl" placeholder="https://api.example.com/webhooks/flow" />
        </el-form-item>
        <el-form-item label="事件类型" prop="eventTypes">
          <el-select v-model="form.eventTypes" multiple style="width: 100%">
            <el-option
              v-for="eventType in eventTypeOptions"
              :key="eventType"
              :label="eventType"
              :value="eventType"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-switch
            v-model="form.active"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { integrationWebhookApi } from '@/api/system/openIntegration'

const eventTypeOptions = [
  'com.flow.process.started.v1',
  'com.flow.task.created.v1',
  'com.flow.task.completed.v1',
  'com.flow.process.completed.v1',
  'com.flow.process.terminated.v1',
  'com.flow.process.failed.v1'
]

const props = defineProps({
  applicationId: { type: String, required: true },
  canManage: { type: Boolean, default: false },
  canRotate: { type: Boolean, default: false },
  canReplay: { type: Boolean, default: false }
})
const emit = defineEmits(['secret-issued'])

const view = ref('endpoints')
const endpoints = ref([])
const deliveries = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editorVisible = ref(false)
const editing = ref(null)
const formRef = ref()
const form = reactive({
  endpointName: '',
  endpointUrl: '',
  eventTypes: [],
  active: true
})
const rules = {
  endpointName: [{ required: true, message: '请输入端点名称', trigger: 'blur' }],
  endpointUrl: [
    { required: true, message: '请输入目标 URL', trigger: 'blur' },
    { pattern: /^https:\/\/[^@\s]+$/i, message: '目标 URL 必须使用 HTTPS 且不能包含用户信息', trigger: 'blur' }
  ],
  eventTypes: [{ required: true, type: 'array', min: 1, message: '至少选择一种事件', trigger: 'change' }]
}

onMounted(load)
watch(() => props.applicationId, load)
watch(view, load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (view.value === 'endpoints') {
      endpoints.value = await integrationWebhookApi.list(props.applicationId) || []
    } else {
      deliveries.value = await integrationWebhookApi.listDeliveries(props.applicationId) || []
    }
  } catch (requestError) {
    error.value = requestError.message || '请求失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    endpointName: '',
    endpointUrl: '',
    eventTypes: ['com.flow.process.completed.v1'],
    active: true
  })
  editorVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    endpointName: row.endpointName,
    endpointUrl: row.endpointUrl,
    eventTypes: [...row.eventTypes],
    active: row.status === 'ACTIVE'
  })
  editorVisible.value = true
}

async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    if (editing.value) {
      await integrationWebhookApi.update(
        props.applicationId,
        editing.value.id,
        {
          expectedVersion: editing.value.version,
          endpointName: form.endpointName,
          endpointUrl: form.endpointUrl,
          status: form.active ? 'ACTIVE' : 'DISABLED',
          eventTypes: form.eventTypes
        }
      )
      ElMessage.success('Webhook 端点已更新')
    } else {
      const issued = await integrationWebhookApi.create(props.applicationId, {
        endpointName: form.endpointName,
        endpointUrl: form.endpointUrl,
        eventTypes: form.eventTypes
      })
      emit('secret-issued', {
        title: 'Webhook 签名密钥',
        fields: [{ label: 'Signing Secret', value: issued.signingSecret }]
      })
      ElMessage.success('Webhook 端点已创建')
    }
    editorVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function rotate(row) {
  await ElMessageBox.confirm(
    '轮换后旧密钥仅在服务端配置的重叠窗口内继续有效。',
    '轮换 Webhook 签名密钥',
    { type: 'warning' }
  )
  const issued = await integrationWebhookApi.rotateSecret(
    props.applicationId,
    row.id,
    { expectedVersion: row.version }
  )
  emit('secret-issued', {
    title: '新 Webhook 签名密钥',
    fields: [{ label: 'Signing Secret', value: issued.signingSecret }]
  })
  await load()
}

async function validateEndpoint(row) {
  try {
    const result = await integrationWebhookApi.validate(props.applicationId, row.id)
    if (result.result === 'SUCCEEDED') {
      ElMessage.success(`验证事件已送达，HTTP ${result.responseStatus}`)
      return
    }
    if (result.result === 'HTTP_ERROR') {
      ElMessage.warning(`端点返回 HTTP ${result.responseStatus}`)
      return
    }
    ElMessage.error('验证事件发送失败，请检查网络和端点配置')
  } catch {
    // The shared request layer presents the server error.
  }
}

async function replay(row) {
  const { value } = await ElMessageBox.prompt(
    '请填写本次人工重放的原因。',
    '重放 Webhook 投递',
    {
      inputType: 'textarea',
      inputValidator: input => {
        const length = String(input || '').trim().length
        return (length >= 3 && length <= 256) || '原因长度需为 3 至 256 个字符'
      },
      confirmButtonText: '确认重放'
    }
  )
  await integrationWebhookApi.replay(props.applicationId, row.id, {
    reason: value.trim()
  })
  ElMessage.success('已创建新的投递尝试')
  await load()
}

function shortEventType(value) {
  return String(value || '').replace(/^com\.flow\./, '').replace(/\.v1$/, '')
}

function deliveryStatusType(status) {
  return {
    SUCCEEDED: 'success',
    DEAD: 'danger',
    PENDING: 'warning',
    IN_PROGRESS: 'primary'
  }[status] || 'info'
}

function deliveryStatusLabel(status) {
  return {
    SUCCEEDED: '成功',
    DEAD: '死信',
    PENDING: '待投递',
    IN_PROGRESS: '投递中',
    RETRY: '待重试'
  }[status] || status
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
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

.primary-line {
  color: #252a31;
  font-weight: 600;
}

.secondary-line {
  overflow: hidden;
  color: #737985;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-tag {
  margin: 2px 4px 2px 0;
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
}
</style>
