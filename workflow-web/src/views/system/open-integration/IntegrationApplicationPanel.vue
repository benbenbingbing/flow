<template>
  <div class="application-panel">
    <div class="detail-header">
      <div class="detail-title">
        <div class="title-row">
          <h3>{{ application.applicationName }}</h3>
          <el-tag :type="statusType(application.status)">
            {{ statusLabel(application.status) }}
          </el-tag>
        </div>
        <div class="client-id">{{ application.clientId }}</div>
      </div>
      <el-dropdown v-if="canManage || canRotate" trigger="click" @command="handleCommand">
        <el-button aria-label="应用操作">
          操作
          <el-icon><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="canManage" command="access">访问策略</el-dropdown-item>
            <el-dropdown-item v-if="canManage" command="contracts">输入契约</el-dropdown-item>
            <el-dropdown-item v-if="canManage" command="status">
              {{ application.status === 'ACTIVE' ? '停用应用' : '启用应用' }}
            </el-dropdown-item>
            <el-dropdown-item v-if="canRotate" command="rotate" divided>
              轮换凭据
            </el-dropdown-item>
            <el-dropdown-item
              v-if="canRotate && application.activeCredentialHint"
              command="revoke"
            >
              吊销凭据
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-descriptions :column="descriptionColumns" border class="application-summary">
      <el-descriptions-item label="责任组织">
        {{ application.ownerOrganizationId || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="请求上限">
        {{ application.rateLimitPerMinute }}/分钟
      </el-descriptions-item>
      <el-descriptions-item label="并发上限">
        {{ application.maxConcurrency }}
      </el-descriptions-item>
      <el-descriptions-item label="凭据">
        {{ application.activeCredentialHint || '无活跃凭据' }}
      </el-descriptions-item>
      <el-descriptions-item label="最后使用">
        {{ formatTime(application.activeCredentialLastUsedAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="版本">
        v{{ application.version }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="policy-row">
      <div>
        <span class="policy-label">Scope</span>
        <el-tag
          v-for="scope in application.scopes"
          :key="scope"
          size="small"
          effect="plain"
        >
          {{ scope }}
        </el-tag>
      </div>
      <div>
        <span class="policy-label">允许流程</span>
        <el-tag
          v-for="processKey in application.processKeys"
          :key="processKey"
          size="small"
          type="info"
          effect="plain"
        >
          {{ processKey }}
        </el-tag>
        <span v-if="!application.processKeys?.length" class="empty-inline">未授权</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="resource-tabs">
      <el-tab-pane label="Webhook" name="webhooks">
        <IntegrationWebhookPanel
          v-if="capabilities.webhookEnabled"
          :application-id="application.id"
          :can-manage="canManage"
          :can-rotate="canRotate"
          :can-replay="canReplay"
          @secret-issued="$emit('secret-issued', $event)"
        />
        <PageState
          v-else
          type="empty"
          title="Webhook 能力未启用"
          description="当前环境未启用 Webhook 管理与投递能力。"
          compact
        />
      </el-tab-pane>
      <el-tab-pane label="Secret" name="secrets">
        <IntegrationSecretPanel
          v-if="capabilities.httpConnectorEnabled"
          :application-id="application.id"
          :can-rotate="canRotate"
          @secret-issued="$emit('secret-issued', $event)"
        />
        <PageState
          v-else
          type="empty"
          title="集成 Secret 能力未启用"
          description="启用 HTTP Connector 后可以管理集成 Secret。"
          compact
        />
      </el-tab-pane>
      <el-tab-pane label="Connector" name="connectors">
        <IntegrationConnectorPanel
          v-if="capabilities.httpConnectorEnabled"
          :application-id="application.id"
          :can-manage="canManage"
        />
        <PageState
          v-else
          type="empty"
          title="HTTP Connector 能力未启用"
          description="当前环境未启用 HTTP Connector 配置与连接测试能力。"
          compact
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="accessVisible"
      title="访问策略"
      width="min(680px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="Scope" required>
          <el-select v-model="accessForm.scopes" multiple style="width: 100%">
            <el-option
              v-for="scope in scopeOptions"
              :key="scope"
              :label="scope"
              :value="scope"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="允许流程">
          <el-select
            v-model="accessForm.processKeys"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accessVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAccess">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="contractsVisible"
      title="流程输入契约"
      width="min(780px, 96vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-input
        v-model="contractsDocument"
        type="textarea"
        :rows="18"
        spellcheck="false"
        class="json-editor"
      />
      <template #footer>
        <el-button @click="contractsVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveContracts">保存契约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApplicationApi } from '@/api/system/openIntegration'
import PageState from '@/components/PageState.vue'
import IntegrationWebhookPanel from './IntegrationWebhookPanel.vue'
import IntegrationSecretPanel from './IntegrationSecretPanel.vue'
import IntegrationConnectorPanel from './IntegrationConnectorPanel.vue'

const scopeOptions = [
  'process.definition.read',
  'process.instance.start',
  'process.instance.read',
  'process.task.read',
  'process.message.correlate'
]

const props = defineProps({
  application: { type: Object, required: true },
  capabilities: {
    type: Object,
    default: () => ({
      openApiEnabled: false,
      webhookEnabled: false,
      httpConnectorEnabled: false
    })
  },
  permissions: { type: Array, default: () => [] },
  superAdmin: { type: Boolean, default: false }
})
const emit = defineEmits(['refresh', 'secret-issued'])

const activeTab = ref('webhooks')
const accessVisible = ref(false)
const contractsVisible = ref(false)
const saving = ref(false)
const contractsDocument = ref('[]')
const accessForm = ref({ scopes: [], processKeys: [] })
const descriptionColumns = ref(3)
const canManage = computed(() => hasPermission('system:integration:manage'))
const canRotate = computed(() => hasPermission('system:integration:secret-rotate'))
const canReplay = computed(() => hasPermission('system:integration:delivery-replay'))

function updateDescriptionColumns() {
  descriptionColumns.value = window.innerWidth < 720 ? 1 : 3
}

onMounted(() => {
  updateDescriptionColumns()
  window.addEventListener('resize', updateDescriptionColumns)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateDescriptionColumns)
})

function hasPermission(permission) {
  return props.superAdmin
    || props.permissions.includes('*')
    || props.permissions.includes(permission)
}

function handleCommand(command) {
  if (command === 'access') openAccess()
  if (command === 'contracts') openContracts()
  if (command === 'status') toggleStatus()
  if (command === 'rotate') rotateCredential()
  if (command === 'revoke') revokeCredential()
}

function openAccess() {
  accessForm.value = {
    scopes: [...(props.application.scopes || [])],
    processKeys: [...(props.application.processKeys || [])]
  }
  accessVisible.value = true
}

async function saveAccess() {
  if (!accessForm.value.scopes.length) {
    ElMessage.warning('至少选择一个 Scope')
    return
  }
  saving.value = true
  try {
    await integrationApplicationApi.updateAccess(props.application.id, {
      ...accessForm.value,
      expectedVersion: props.application.version
    })
    accessVisible.value = false
    emit('refresh')
    ElMessage.success('访问策略已更新')
  } finally {
    saving.value = false
  }
}

async function openContracts() {
  saving.value = true
  try {
    const contracts = await integrationApplicationApi.listProcessContracts(
      props.application.id
    )
    contractsDocument.value = JSON.stringify(contracts || [], null, 2)
    contractsVisible.value = true
  } finally {
    saving.value = false
  }
}

async function saveContracts() {
  let contracts
  try {
    contracts = JSON.parse(contractsDocument.value)
    if (!Array.isArray(contracts)) throw new Error()
  } catch {
    ElMessage.error('契约必须是 JSON 数组')
    return
  }
  saving.value = true
  try {
    await integrationApplicationApi.updateProcessContracts(props.application.id, {
      contracts,
      expectedVersion: props.application.version
    })
    contractsVisible.value = false
    emit('refresh')
    ElMessage.success('流程输入契约已更新')
  } finally {
    saving.value = false
  }
}

async function toggleStatus() {
  const target = props.application.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await ElMessageBox.confirm(
    target === 'ACTIVE'
      ? '启用后该应用可以重新获取令牌并访问授权接口。'
      : '停用后将立即阻止新令牌签发和新的开放接口调用。',
    target === 'ACTIVE' ? '启用应用' : '停用应用',
    { type: target === 'ACTIVE' ? 'info' : 'warning' }
  )
  await integrationApplicationApi.updateStatus(props.application.id, {
    status: target,
    expectedVersion: props.application.version
  })
  emit('refresh')
  ElMessage.success(target === 'ACTIVE' ? '应用已启用' : '应用已停用')
}

async function rotateCredential() {
  await ElMessageBox.confirm(
    '新凭据签发后，旧凭据会立即失效。',
    '轮换应用凭据',
    { type: 'warning' }
  )
  const issued = await integrationApplicationApi.rotateCredential(
    props.application.id,
    { expiresAt: null, expectedVersion: props.application.version }
  )
  emit('refresh')
  emit('secret-issued', {
    title: '新应用凭据',
    fields: [
      { label: 'Client ID', value: issued.application.clientId },
      { label: 'Client Secret', value: issued.clientSecret }
    ]
  })
}

async function revokeCredential() {
  await ElMessageBox.confirm(
    '吊销后该应用无法继续获取令牌，且不会自动签发新凭据。',
    '吊销应用凭据',
    { type: 'warning', confirmButtonText: '确认吊销' }
  )
  await integrationApplicationApi.revokeCredential(props.application.id, {
    expectedVersion: props.application.version
  })
  emit('refresh')
  ElMessage.success('应用凭据已吊销')
}

function statusType(status) {
  return status === 'ACTIVE' ? 'success' : status === 'DISABLED' ? 'warning' : 'danger'
}

function statusLabel(status) {
  return { ACTIVE: '启用', DISABLED: '停用', REVOKED: '已吊销' }[status] || status
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '从未'
}
</script>

<style scoped>
.detail-header,
.title-row,
.policy-row {
  display: flex;
  align-items: center;
}

.detail-header {
  justify-content: space-between;
  gap: 16px;
}

.title-row {
  gap: 10px;
}

.title-row h3 {
  margin: 0;
  font-size: 18px;
  letter-spacing: 0;
}

.client-id {
  margin-top: 5px;
  color: #737985;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.application-summary {
  margin-top: 16px;
}

.policy-row {
  align-items: flex-start;
  gap: 22px;
  padding: 14px 0 4px;
  border-bottom: 1px solid #ebeef2;
}

.policy-row > div {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.policy-label {
  width: 72px;
  color: #606773;
  font-size: 13px;
  line-height: 24px;
}

.empty-inline {
  color: #9098a3;
  font-size: 13px;
  line-height: 24px;
}

.resource-tabs {
  margin-top: 12px;
}

.json-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

@media (max-width: 720px) {
  .detail-header {
    align-items: flex-start;
  }

  .policy-row {
    flex-direction: column;
    gap: 10px;
  }

  .policy-label {
    width: 100%;
  }
}
</style>
