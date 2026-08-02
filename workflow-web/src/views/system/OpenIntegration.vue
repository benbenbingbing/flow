<template>
  <div class="integration-page">
    <div class="page-toolbar">
      <div>
        <h2>开放集成</h2>
        <div class="page-meta">
          {{ applications.length }} 个接入应用
        </div>
      </div>
      <div class="toolbar-actions">
        <el-button :loading="loading" @click="loadApplications">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新建应用
        </el-button>
      </div>
    </div>

    <PageState
      v-if="loadError"
      type="error"
      title="接入应用加载失败"
      :description="loadError"
      retryable
      @retry="loadApplications"
    />

    <div v-else class="workspace">
      <section class="application-list" aria-label="接入应用列表">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索名称或 Client ID"
          class="application-search"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div v-loading="loading" class="application-scroll">
          <button
            v-for="application in filteredApplications"
            :key="application.id"
            type="button"
            class="application-item"
            :class="{ active: application.id === selectedId }"
            @click="selectedId = application.id"
          >
            <span class="application-heading">
              <span>{{ application.applicationName }}</span>
              <el-tag
                size="small"
                :type="statusType(application.status)"
                effect="plain"
              >
                {{ statusLabel(application.status) }}
              </el-tag>
            </span>
            <span class="application-client">{{ application.clientId }}</span>
            <span class="application-time">
              最近使用 {{ formatTime(application.activeCredentialLastUsedAt) }}
            </span>
          </button>
          <el-empty
            v-if="!loading && filteredApplications.length === 0"
            description="暂无接入应用"
            :image-size="72"
          />
        </div>
      </section>

      <section class="application-detail" aria-label="接入应用详情">
        <IntegrationApplicationPanel
          v-if="selectedApplication"
          :application="selectedApplication"
          :permissions="userStore.permissions"
          :super-admin="userStore.isSuperAdmin"
          @refresh="loadApplications(true)"
          @secret-issued="showSecret"
        />
        <el-empty
          v-else
          description="请选择一个接入应用"
          :image-size="96"
        />
      </section>
    </div>

    <el-dialog
      v-model="createVisible"
      title="新建接入应用"
      width="min(680px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <div class="form-grid">
          <el-form-item label="应用名称" prop="applicationName">
            <el-input v-model="createForm.applicationName" maxlength="128" />
          </el-form-item>
          <el-form-item label="责任组织">
            <el-input v-model="createForm.ownerOrganizationId" maxlength="64" />
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="Scope" prop="scopes">
          <el-select v-model="createForm.scopes" multiple style="width: 100%">
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
            v-model="createForm.processKeys"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="输入已发布流程 Key"
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="每分钟请求上限">
            <el-input-number
              v-model="createForm.rateLimitPerMinute"
              :min="1"
              :max="10000"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="并发上限">
            <el-input-number
              v-model="createForm.maxConcurrency"
              :min="1"
              :max="1000"
              controls-position="right"
            />
          </el-form-item>
        </div>
        <el-form-item label="来源 CIDR">
          <el-input
            v-model="createForm.allowedSourceCidrs"
            type="textarea"
            :rows="2"
            placeholder="每行一个 CIDR；留空表示不限制"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createApplication">
          创建并签发凭据
        </el-button>
      </template>
    </el-dialog>

    <OneTimeSecretDialog
      v-model="secretVisible"
      :title="secretTitle"
      :fields="secretFields"
      @closed="clearSecret"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { useUserStore } from '@/stores/user'
import { integrationApplicationApi } from '@/api/system/openIntegration'
import IntegrationApplicationPanel from './open-integration/IntegrationApplicationPanel.vue'
import OneTimeSecretDialog from './open-integration/OneTimeSecretDialog.vue'

const scopeOptions = [
  'process.definition.read',
  'process.instance.start',
  'process.instance.read',
  'process.task.read',
  'process.message.correlate',
  'process.instance.cancel'
]

const userStore = useUserStore()
const applications = ref([])
const selectedId = ref('')
const keyword = ref('')
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const createVisible = ref(false)
const createFormRef = ref()
const secretVisible = ref(false)
const secretTitle = ref('')
const secretFields = ref([])
const createForm = reactive(defaultCreateForm())
const createRules = {
  applicationName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  scopes: [{ required: true, type: 'array', min: 1, message: '至少选择一个 Scope', trigger: 'change' }]
}

const canManage = computed(() => hasPermission('system:integration:manage'))
const selectedApplication = computed(() =>
  applications.value.find(item => item.id === selectedId.value)
)
const filteredApplications = computed(() => {
  const term = keyword.value.trim().toLowerCase()
  if (!term) return applications.value
  return applications.value.filter(item =>
    `${item.applicationName} ${item.clientId}`.toLowerCase().includes(term)
  )
})

onMounted(loadApplications)

function hasPermission(permission) {
  return userStore.isSuperAdmin
    || userStore.permissions.includes('*')
    || userStore.permissions.includes(permission)
}

function defaultCreateForm() {
  return {
    applicationName: '',
    description: '',
    ownerOrganizationId: '',
    scopes: ['process.definition.read', 'process.instance.start', 'process.instance.read'],
    processKeys: [],
    rateLimitPerMinute: 60,
    maxConcurrency: 10,
    allowedSourceCidrs: ''
  }
}

async function loadApplications(preserveSelection = false) {
  loading.value = true
  loadError.value = ''
  try {
    applications.value = await integrationApplicationApi.list() || []
    if (!preserveSelection || !applications.value.some(item => item.id === selectedId.value)) {
      selectedId.value = applications.value[0]?.id || ''
    }
  } catch (error) {
    loadError.value = error.message || '请求失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, defaultCreateForm())
  createVisible.value = true
}

async function createApplication() {
  if (!await createFormRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    const issued = await integrationApplicationApi.create({
      ...createForm,
      ownerOrganizationId: createForm.ownerOrganizationId || null,
      allowedSourceCidrs: createForm.allowedSourceCidrs
        .split(/\r?\n/)
        .map(value => value.trim())
        .filter(Boolean),
      expiresAt: null
    })
    createVisible.value = false
    await loadApplications()
    selectedId.value = issued.application.id
    showSecret({
      title: '应用凭据',
      fields: [
        { label: 'Client ID', value: issued.application.clientId },
        { label: 'Client Secret', value: issued.clientSecret }
      ]
    })
    ElMessage.success('接入应用已创建')
  } finally {
    saving.value = false
  }
}

function showSecret(payload) {
  secretTitle.value = payload.title
  secretFields.value = payload.fields.map(field => ({ ...field }))
  secretVisible.value = true
}

function clearSecret() {
  secretFields.value = []
  secretTitle.value = ''
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
.integration-page {
  min-width: 0;
}

.page-toolbar,
.application-heading,
.toolbar-actions {
  display: flex;
  align-items: center;
}

.page-toolbar {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-toolbar h2 {
  margin: 0 0 4px;
  font-size: 20px;
  letter-spacing: 0;
}

.page-meta,
.application-client,
.application-time {
  color: #737985;
  font-size: 12px;
}

.toolbar-actions {
  gap: 8px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(240px, 300px) minmax(0, 1fr);
  min-height: calc(100vh - 150px);
  border: 1px solid #dfe3e8;
  background: #fff;
}

.application-list {
  min-width: 0;
  border-right: 1px solid #dfe3e8;
}

.application-search {
  padding: 12px;
  box-sizing: border-box;
}

.application-scroll {
  min-height: 240px;
  max-height: calc(100vh - 215px);
  overflow: auto;
}

.application-item {
  display: block;
  width: 100%;
  padding: 13px 14px;
  border: 0;
  border-top: 1px solid #eef0f2;
  background: #fff;
  text-align: left;
  cursor: pointer;
}

.application-item:hover {
  background: #f7f9fb;
}

.application-item.active {
  background: #ecf5ff;
  box-shadow: inset 3px 0 #409eff;
}

.application-heading {
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 7px;
  color: #20242a;
  font-size: 14px;
  font-weight: 600;
}

.application-client,
.application-time {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.application-time {
  margin-top: 4px;
}

.application-detail {
  min-width: 0;
  padding: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 900px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .application-list {
    border-right: 0;
    border-bottom: 1px solid #dfe3e8;
  }

  .application-scroll {
    max-height: 260px;
  }
}

@media (max-width: 600px) {
  .page-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-actions,
  .toolbar-actions :deep(.el-button) {
    width: 100%;
  }

  .application-detail {
    padding: 12px;
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
