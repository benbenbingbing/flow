<template>
  <div class="binding-panel">
    <div class="filter-bar">
      <el-input
        v-model="filters.applicationId"
        clearable
        placeholder="Application ID"
        @keyup.enter="search"
      />
      <el-select
        v-model="filters.identityProviderId"
        clearable
        filterable
        allow-create
        default-first-option
        placeholder="Identity Provider"
      >
        <el-option
          v-for="provider in providerOptions"
          :key="provider.id"
          :label="`${provider.name} (${provider.id})`"
          :value="provider.id"
        />
      </el-select>
      <el-input
        v-model="filters.flowUserId"
        clearable
        placeholder="Flow User ID"
        @keyup.enter="search"
      />
      <el-select v-model="filters.status" clearable placeholder="状态">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="停用" value="DISABLED" />
        <el-option label="已撤销" value="REVOKED" />
      </el-select>
      <el-button :loading="loading" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <span class="filter-spacer" />
      <el-button @click="openLookup">精确查找</el-button>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建 Binding
      </el-button>
    </div>

    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="panel-alert"
    />

    <el-table v-loading="loading" :data="bindings" border stripe>
      <el-table-column prop="applicationId" label="Application ID" min-width="175" />
      <el-table-column prop="identityProviderId" label="Provider ID" min-width="175" />
      <el-table-column prop="subjectHint" min-width="120">
        <template #header>
          <ConfigHelpLabel label="Subject Hint" help-key="embed.binding.subjectHint" />
        </template>
      </el-table-column>
      <el-table-column prop="flowUserId" label="Flow User ID" min-width="150" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生效 / 过期" min-width="180">
        <template #default="{ row }">
          {{ formatTime(row.effectiveAt) }}<br>
          <small>{{ row.expiresAt ? formatTime(row.expiresAt) : '不过期' }}</small>
        </template>
      </el-table-column>
      <el-table-column prop="version" width="76">
        <template #header>
          <ConfigHelpLabel label="CAS" help-key="embed.version.cas" />
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="160">
        <template #default="{ row }">
          <el-button
            link
            :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
            :disabled="row.status === 'REVOKED'"
            @click="toggleStatus(row)"
          >
            {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status === 'REVOKED'"
            @click="revoke(row)"
          >
            撤销
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-row">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadBindings"
        @size-change="handlePageSizeChange"
      />
    </div>

    <el-dialog
      v-model="createVisible"
      title="新建精确身份 Binding"
      width="min(720px, 96vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="clearCreate"
    >
      <el-alert
        v-if="createError"
        type="error"
        :title="createError"
        show-icon
        :closable="false"
        class="panel-alert"
      />
      <el-form label-position="top" autocomplete="off">
        <div class="form-grid">
          <el-form-item label="Application ID" required>
            <template #label>
              <ConfigHelpLabel
                label="Application ID"
                help-key="embed.application.internalId"
              />
            </template>
            <el-input v-model="createForm.applicationId" maxlength="64" />
          </el-form-item>
          <el-form-item label="Identity Provider" required>
            <template #label>
              <ConfigHelpLabel
                label="Identity Provider"
                help-key="embed.binding.identityProvider"
              />
            </template>
            <el-select
              v-model="createForm.identityProviderId"
              filterable
              allow-create
              default-first-option
              style="width: 100%"
            >
              <el-option
                v-for="provider in activeProviderOptions"
                :key="provider.id"
                :label="`${provider.name} (${provider.id})`"
                :value="provider.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="External Subject" required>
            <template #label>
              <ConfigHelpLabel
                label="External Subject"
                help-key="embed.binding.externalSubject"
              />
            </template>
            <el-input
              v-model="createForm.externalSubject"
              type="password"
              autocomplete="new-password"
              maxlength="128"
            />
            <div class="field-help">原始 Subject 只用于本次 HMAC 摘要，不会回显、记录或持久化在浏览器。</div>
          </el-form-item>
          <el-form-item label="Flow User ID" required>
            <template #label>
              <ConfigHelpLabel
                label="Flow User ID"
                help-key="embed.binding.flowUserId"
              />
            </template>
            <el-input v-model="createForm.flowUserId" maxlength="64" />
          </el-form-item>
          <el-form-item label="生效时间">
            <el-date-picker
              v-model="createForm.effectiveAt"
              type="datetime"
              placeholder="默认立即生效"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="过期时间">
            <el-date-picker
              v-model="createForm.expiresAt"
              type="datetime"
              placeholder="不设置表示不过期"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createBinding">
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="lookupVisible"
      title="按外部 Subject 精确查找"
      width="min(620px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="clearLookup"
    >
      <el-alert
        v-if="lookupError"
        type="error"
        :title="lookupError"
        show-icon
        :closable="false"
        class="panel-alert"
      />
      <el-form label-position="top" autocomplete="off">
        <el-form-item label="Application ID" required>
          <template #label>
            <ConfigHelpLabel
              label="Application ID"
              help-key="embed.application.internalId"
            />
          </template>
          <el-input v-model="lookupForm.applicationId" maxlength="64" />
        </el-form-item>
        <el-form-item label="Identity Provider" required>
          <template #label>
            <ConfigHelpLabel
              label="Identity Provider"
              help-key="embed.binding.identityProvider"
            />
          </template>
          <el-select
            v-model="lookupForm.identityProviderId"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option
              v-for="provider in providerOptions"
              :key="provider.id"
              :label="`${provider.name} (${provider.id})`"
              :value="provider.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="External Subject" required>
          <template #label>
            <ConfigHelpLabel
              label="External Subject"
              help-key="embed.binding.externalSubject"
            />
          </template>
          <el-input
            v-model="lookupForm.externalSubject"
            type="password"
            autocomplete="new-password"
            maxlength="128"
          />
        </el-form-item>
      </el-form>
      <el-descriptions
        v-if="lookupResult"
        :column="1"
        border
        size="small"
        class="lookup-result"
      >
        <el-descriptions-item label="Binding ID">{{ lookupResult.id }}</el-descriptions-item>
        <el-descriptions-item label="Subject Hint">{{ lookupResult.subjectHint }}</el-descriptions-item>
        <el-descriptions-item label="Flow User ID">{{ lookupResult.flowUserId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(lookupResult.status) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty
        v-else-if="lookupCompleted"
        description="未找到精确 Binding"
        :image-size="64"
      />
      <template #footer>
        <el-button @click="lookupVisible = false">关闭</el-button>
        <el-button type="primary" :loading="lookingUp" @click="lookupBinding">
          查找
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import dayjs from 'dayjs'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { embedManagementApi } from '@/api/system/embedManagement'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  buildExpectedVersionPayload,
  describeEmbedManagementError,
  toInstant
} from './embedManagementModel'

const bindings = ref([])
const providerOptions = ref([])
const loading = ref(false)
const loadError = ref('')
const filters = reactive({
  applicationId: '', identityProviderId: '', flowUserId: '', status: ''
})
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive(defaultBindingForm())
const lookupVisible = ref(false)
const lookingUp = ref(false)
const lookupError = ref('')
const lookupCompleted = ref(false)
const lookupResult = ref(null)
const lookupForm = reactive(defaultLookupForm())

const activeProviderOptions = computed(() =>
  providerOptions.value.filter(provider => provider.status === 'ACTIVE')
)

onMounted(() => {
  loadBindings()
  loadProviderOptions()
})

function defaultBindingForm() {
  return {
    applicationId: '',
    identityProviderId: '',
    externalSubject: '',
    flowUserId: '',
    effectiveAt: null,
    expiresAt: null,
    remark: ''
  }
}

function defaultLookupForm() {
  return { applicationId: '', identityProviderId: '', externalSubject: '' }
}

async function loadBindings() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await embedManagementApi.bindings.page({
      applicationId: filters.applicationId.trim() || undefined,
      identityProviderId: filters.identityProviderId || undefined,
      flowUserId: filters.flowUserId.trim() || undefined,
      status: filters.status || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    bindings.value = result?.list || result?.records || []
    pagination.total = Number(result?.total || 0)
  } catch (error) {
    loadError.value = describeEmbedManagementError(error)
  } finally {
    loading.value = false
  }
}

async function loadProviderOptions() {
  try {
    const result = await embedManagementApi.providers.page({ pageNum: 1, pageSize: 100 })
    providerOptions.value = result?.list || result?.records || []
  } catch {
    providerOptions.value = []
  }
}

function search() {
  pagination.pageNum = 1
  loadBindings()
}

function resetFilters() {
  Object.assign(filters, {
    applicationId: '', identityProviderId: '', flowUserId: '', status: ''
  })
  search()
}

function handlePageSizeChange() {
  pagination.pageNum = 1
  loadBindings()
}

function openCreate() {
  clearCreate()
  createVisible.value = true
}

function clearCreate() {
  createError.value = ''
  Object.assign(createForm, defaultBindingForm())
}

function buildBindingPayload() {
  if (
    !createForm.applicationId.trim()
    || !createForm.identityProviderId
    || !createForm.externalSubject.trim()
    || !createForm.flowUserId.trim()
  ) {
    throw new Error('Application、Provider、External Subject 和 Flow User 为必填')
  }
  return {
    applicationId: createForm.applicationId.trim(),
    identityProviderId: createForm.identityProviderId,
    externalSubject: createForm.externalSubject.trim(),
    flowUserId: createForm.flowUserId.trim(),
    effectiveAt: toInstant(createForm.effectiveAt),
    expiresAt: toInstant(createForm.expiresAt),
    remark: createForm.remark.trim() || null
  }
}

async function createBinding() {
  let payload
  try {
    payload = buildBindingPayload()
  } catch (error) {
    createError.value = error.message
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await embedManagementApi.bindings.create(payload)
    // 原始 Subject 不进入任何持久状态，请求完成后立即清除。
    createForm.externalSubject = ''
    createVisible.value = false
    ElMessage.success('Identity Binding 已创建')
    await loadBindings()
  } catch (error) {
    createForm.externalSubject = ''
    createError.value = describeEmbedManagementError(error)
  } finally {
    creating.value = false
  }
}

function openLookup() {
  clearLookup()
  lookupVisible.value = true
}

function clearLookup() {
  lookupError.value = ''
  lookupCompleted.value = false
  lookupResult.value = null
  Object.assign(lookupForm, defaultLookupForm())
}

async function lookupBinding() {
  if (
    !lookupForm.applicationId.trim()
    || !lookupForm.identityProviderId
    || !lookupForm.externalSubject.trim()
  ) {
    lookupError.value = 'Application、Provider 和 External Subject 为必填'
    return
  }
  lookingUp.value = true
  lookupError.value = ''
  lookupCompleted.value = false
  lookupResult.value = null
  try {
    lookupResult.value = await embedManagementApi.bindings.lookup({
      applicationId: lookupForm.applicationId.trim(),
      identityProviderId: lookupForm.identityProviderId,
      externalSubject: lookupForm.externalSubject.trim()
    })
    lookupCompleted.value = true
  } catch (error) {
    lookupError.value = describeEmbedManagementError(error)
  } finally {
    lookupForm.externalSubject = ''
    lookingUp.value = false
  }
}

async function toggleStatus(row) {
  const status = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await embedManagementApi.bindings.changeStatus(
      row.id,
      buildExpectedVersionPayload(row.version, {
        status,
        reason: '管理台状态变更'
      })
    )
    ElMessage.success(status === 'ACTIVE' ? 'Binding 已启用' : 'Binding 已停用')
    await loadBindings()
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

async function revoke(row) {
  let reason
  try {
    const result = await ElMessageBox.prompt(
      '撤销为终态，请填写原因。',
      '撤销 Identity Binding',
      {
        type: 'warning',
        inputValidator: value => Boolean(value?.trim()) || '请填写撤销原因',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消'
      }
    )
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    await embedManagementApi.bindings.revoke(
      row.id,
      buildExpectedVersionPayload(row.version, { reason })
    )
    ElMessage.success('Binding 已撤销')
    await loadBindings()
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

function statusType(status) {
  return { ACTIVE: 'success', DISABLED: 'warning', REVOKED: 'danger' }[status] || 'info'
}

function statusLabel(status) {
  return { ACTIVE: '启用', DISABLED: '停用', REVOKED: '已撤销' }[status] || status
}

function formatTime(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 4px 0 14px;
}

.filter-bar > .el-input,
.filter-bar > .el-select {
  width: 180px;
}

.filter-spacer {
  flex: 1;
}

.panel-alert,
.lookup-result {
  margin-bottom: 12px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.field-help {
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
