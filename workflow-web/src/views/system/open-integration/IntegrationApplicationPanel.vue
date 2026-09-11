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
        <div class="application-identifier">
          <span>Application ID：{{ application.id }}</span>
          <el-button text size="small" @click="copyApplicationId">复制</el-button>
        </div>
        <div class="client-id">Client ID：{{ application.clientId }}</div>
      </div>
      <el-dropdown
        v-if="application.status !== 'REVOKED' && (canManage || canRotate)"
        trigger="click"
        @command="handleCommand"
      >
        <el-button aria-label="应用操作">
          操作
          <el-icon><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
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
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApplicationApi } from '@/api/system/openIntegration'

const props = defineProps({
  application: { type: Object, required: true },
  permissions: { type: Array, default: () => [] },
  superAdmin: { type: Boolean, default: false }
})
const emit = defineEmits(['refresh', 'secret-issued'])

const descriptionColumns = ref(3)
const canManage = computed(() => hasPermission('system:integration:manage'))
const canRotate = computed(() => hasPermission('system:integration:secret-rotate'))

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
  if (command === 'status') toggleStatus()
  if (command === 'rotate') rotateCredential()
  if (command === 'revoke') revokeCredential()
}

async function copyApplicationId() {
  try {
    await navigator.clipboard.writeText(props.application.id)
    ElMessage.success('Application ID 已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择 Application ID')
  }
}

async function toggleStatus() {
  const target = props.application.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await ElMessageBox.confirm(
    target === 'ACTIVE'
      ? '启用后该应用可以重新获取令牌并发起接入请求。'
      : '停用后将立即阻止新令牌签发和新的接入请求。',
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
      { label: 'Application ID', value: issued.application.id },
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
.title-row {
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

.client-id,
.application-identifier {
  margin-top: 5px;
  color: #737985;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.application-identifier {
  display: flex;
  align-items: center;
  gap: 4px;
}

.application-identifier :deep(.el-button) {
  height: auto;
  padding: 0 4px;
  font-family: inherit;
}

.application-summary {
  margin-top: 16px;
}

@media (max-width: 720px) {
  .detail-header {
    align-items: flex-start;
  }
}
</style>
