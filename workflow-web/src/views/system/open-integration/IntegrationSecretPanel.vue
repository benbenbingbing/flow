<template>
  <div class="resource-panel">
    <div class="resource-toolbar">
      <div class="resource-count">{{ secrets.length }} 个 Secret 版本</div>
      <div class="resource-actions">
        <el-button :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button v-if="canRotate" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建 Secret
        </el-button>
      </div>
    </div>

    <PageState
      v-if="error"
      type="error"
      title="Secret 加载失败"
      :description="error"
      retryable
      compact
      @retry="load"
    />

    <el-table
      v-else
      v-loading="loading"
      :data="secrets"
      stripe
      border
      row-key="id"
    >
      <el-table-column label="名称" min-width="180">
        <template #default="{ row }">
          <div class="primary-line">{{ row.secretName }}</div>
          <div class="secondary-line">{{ row.secretHint }}</div>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="80" align="center">
        <template #default="{ row }">v{{ row.secretVersion }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="canRotate && row.status === 'ACTIVE'"
            link
            type="primary"
            @click="openRotate(row)"
          >
            轮换
          </el-button>
          <el-button
            v-if="canRotate && row.status === 'ACTIVE'"
            link
            type="warning"
            @click="revoke(row)"
          >
            吊销
          </el-button>
          <el-button
            v-if="canRotate && row.status === 'REVOKED'"
            link
            type="danger"
            @click="destroy(row)"
          >
            销毁
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无集成 Secret" :image-size="64" />
      </template>
    </el-table>

    <el-dialog
      v-model="editorVisible"
      :title="rotating ? `轮换 ${rotating.secretName}` : '新建集成 Secret'"
      width="min(580px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="resetEditor"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item v-if="!rotating" label="Secret 名称" prop="secretName">
          <el-input v-model="form.secretName" maxlength="64" />
        </el-form-item>
        <el-form-item label="Secret 值" prop="secretValue">
          <el-input
            v-model="form.secretValue"
            type="password"
            show-password
            maxlength="65536"
            autocomplete="new-password"
            placeholder="留空则由服务端生成"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeEditor">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          {{ rotating ? '确认轮换' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { integrationSecretApi } from '@/api/system/openIntegration'

const props = defineProps({
  applicationId: { type: String, required: true },
  canRotate: { type: Boolean, default: false }
})
const emit = defineEmits(['secret-issued'])

const secrets = ref([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const editorVisible = ref(false)
const rotating = ref(null)
const formRef = ref()
const form = reactive({ secretName: '', secretValue: '' })
const rules = {
  secretName: [
    { required: true, message: '请输入 Secret 名称', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9._-]*$/,
      message: '名称需以字母开头，仅允许字母、数字、点、下划线和短横线',
      trigger: 'blur'
    }
  ],
  secretValue: [{
    validator: (_rule, value, callback) => {
      if (!value || (value.length >= 8 && value.length <= 65536)) callback()
      else callback(new Error('Secret 值至少 8 个字符'))
    },
    trigger: 'blur'
  }]
}

onMounted(load)
watch(() => props.applicationId, load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    secrets.value = await integrationSecretApi.list(props.applicationId) || []
  } catch (requestError) {
    error.value = requestError.message || '请求失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  rotating.value = null
  Object.assign(form, { secretName: '', secretValue: '' })
  editorVisible.value = true
}

function openRotate(row) {
  rotating.value = row
  Object.assign(form, { secretName: row.secretName, secretValue: '' })
  editorVisible.value = true
}

function closeEditor() {
  form.secretValue = ''
  editorVisible.value = false
}

function resetEditor() {
  rotating.value = null
  Object.assign(form, { secretName: '', secretValue: '' })
}

async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    const wasRotation = Boolean(rotating.value)
    const secretValue = form.secretValue || null
    const issued = rotating.value
      ? await integrationSecretApi.rotate(
        props.applicationId,
        rotating.value.secretName,
        {
          expectedSecretVersion: rotating.value.secretVersion,
          secretValue
        }
      )
      : await integrationSecretApi.create(
        props.applicationId,
        { secretName: form.secretName, secretValue }
      )
    emit('secret-issued', {
      title: rotating.value ? '新 Secret 版本' : '新集成 Secret',
      fields: [
        { label: 'Secret 引用', value: issued.secretReference },
        { label: 'Secret 值', value: issued.secretValue }
      ]
    })
    closeEditor()
    await load()
    ElMessage.success(wasRotation ? 'Secret 已轮换' : 'Secret 已创建')
  } finally {
    saving.value = false
  }
}

async function revoke(row) {
  await ElMessageBox.confirm(
    '吊销后，引用该名称的 Connector 将无法解析此 Secret。',
    '吊销 Secret',
    { type: 'warning', confirmButtonText: '确认吊销' }
  )
  await integrationSecretApi.revoke(
    props.applicationId,
    row.secretName,
    { expectedSecretVersion: row.secretVersion }
  )
  await load()
  ElMessage.success('Secret 已吊销')
}

async function destroy(row) {
  await ElMessageBox.confirm(
    '销毁会永久删除该版本的密文和包裹密钥，操作不可恢复。',
    '永久销毁 Secret',
    { type: 'error', confirmButtonText: '永久销毁' }
  )
  await integrationSecretApi.destroy(props.applicationId, row.id)
  await load()
  ElMessage.success('Secret 密文已销毁')
}

function statusType(status) {
  return { ACTIVE: 'success', REVOKED: 'warning', DESTROYED: 'info' }[status] || 'info'
}

function statusLabel(status) {
  return { ACTIVE: '活跃', REVOKED: '已吊销', DESTROYED: '已销毁' }[status] || status
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

.resource-count,
.secondary-line {
  color: #737985;
  font-size: 12px;
}

.primary-line {
  color: #252a31;
  font-weight: 600;
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
