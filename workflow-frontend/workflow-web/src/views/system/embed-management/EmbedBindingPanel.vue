<template>
  <div class="binding-panel">
    <div class="filter-bar">
      <el-select
        v-model="filters.applicationId"
        clearable
        filterable
        remote
        reserve-keyword
        :remote-method="loadApplicationOptions"
        :loading="applicationOptionsLoading"
        placeholder="按应用名称筛选"
      >
        <el-option
          v-for="application in generalApplicationOptions"
          :key="application.id"
          :label="applicationOptionLabel(application)"
          :value="application.id"
        />
      </el-select>
      <el-select
        v-model="filters.identityProviderId"
        clearable
        filterable
        remote
        reserve-keyword
        :remote-method="loadProviderOptions"
        :loading="providerOptionsLoading"
        placeholder="按 Provider 名称筛选"
      >
        <el-option
          v-for="provider in generalProviderOptions"
          :key="provider.id"
          :label="providerOptionLabel(provider)"
          :value="provider.id"
        />
      </el-select>
      <UserSelector
        v-model="filters.flowUserId"
        value-key="id"
        placeholder="按姓名选择 Flow 用户"
        title="选择要筛选的 Flow 用户"
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
      <el-table-column label="接入应用" min-width="210">
        <template #default="{ row }">
          <div>{{ applicationLabel(row.applicationId) }}</div>
          <small class="resource-id">{{ row.applicationId }}</small>
        </template>
      </el-table-column>
      <el-table-column label="身份提供方" min-width="210">
        <template #default="{ row }">
          <div>{{ providerLabel(row.identityProviderId) }}</div>
          <small class="resource-id">{{ row.identityProviderId }}</small>
        </template>
      </el-table-column>
      <el-table-column prop="subjectHint" min-width="120">
        <template #header>
          <ConfigHelpLabel label="Subject Hint" help-key="embed.binding.subjectHint" />
        </template>
      </el-table-column>
      <el-table-column label="Flow 用户" min-width="170">
        <template #default="{ row }">
          <div>{{ flowUserLabel(row.flowUserId) }}</div>
          <small class="resource-id">{{ row.flowUserId }}</small>
        </template>
      </el-table-column>
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
      <el-alert
        v-if="optionError"
        type="warning"
        :title="optionError"
        show-icon
        :closable="false"
        class="panel-alert"
      />
      <el-form label-position="top" autocomplete="off">
        <div class="form-grid">
          <el-form-item label="接入应用" required>
            <template #label>
              <ConfigHelpLabel
                label="接入应用"
                help-key="embed.application.internalId"
              />
            </template>
            <el-select
              v-model="createForm.applicationId"
              filterable
              remote
              reserve-keyword
              :remote-method="loadActiveApplicationOptions"
              :loading="applicationOptionsLoading"
              placeholder="按应用名称搜索"
              style="width: 100%"
            >
              <el-option
                v-for="application in activeApplicationOptions"
                :key="application.id"
                :label="applicationOptionLabel(application)"
                :value="application.id"
              />
            </el-select>
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
              remote
              reserve-keyword
              :remote-method="loadActiveProviderOptions"
              :loading="providerOptionsLoading"
              placeholder="按 Provider 名称搜索"
              style="width: 100%"
            >
              <el-option
                v-for="provider in activeProviderOptions"
                :key="provider.id"
                :label="providerOptionLabel(provider)"
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
          <el-form-item label="Flow 用户" required>
            <template #label>
              <ConfigHelpLabel
                label="Flow 用户"
                help-key="embed.binding.flowUserId"
              />
            </template>
            <UserSelector
              v-model="createForm.flowUserId"
              value-key="id"
              placeholder="按姓名或账号选择用户"
              title="选择映射到的 Flow 用户"
              @selected="handleFlowUserSelected"
            />
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
        <el-form-item label="接入应用" required>
          <template #label>
            <ConfigHelpLabel
              label="接入应用"
              help-key="embed.application.internalId"
            />
          </template>
          <el-select
            v-model="lookupForm.applicationId"
            filterable
            remote
            reserve-keyword
            :remote-method="loadApplicationOptions"
            :loading="applicationOptionsLoading"
            placeholder="按应用名称搜索"
            style="width: 100%"
          >
            <el-option
              v-for="application in generalApplicationOptions"
              :key="application.id"
              :label="applicationOptionLabel(application)"
              :value="application.id"
            />
          </el-select>
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
            remote
            reserve-keyword
            :remote-method="loadProviderOptions"
            :loading="providerOptionsLoading"
            placeholder="按 Provider 名称搜索"
            style="width: 100%"
          >
            <el-option
              v-for="provider in generalProviderOptions"
              :key="provider.id"
              :label="providerOptionLabel(provider)"
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
import UserSelector from '@/components/UserSelector.vue'
import request from '@/utils/request'
import {
  buildExpectedVersionPayload,
  describeEmbedManagementError,
  toInstant
} from './embedManagementModel'

const emit = defineEmits(['changed'])

const bindings = ref([])
const applicationOptions = ref([])
const providerOptions = ref([])
const applicationSearchOptions = ref([])
const providerSearchOptions = ref([])
const activeApplicationSearchOptions = ref([])
const activeProviderSearchOptions = ref([])
const flowUserNames = ref(new Map())
const loading = ref(false)
const applicationOptionsLoading = ref(false)
const providerOptionsLoading = ref(false)
const loadError = ref('')
const optionError = ref('')
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
let bindingLoadSequence = 0
let applicationOptionsSequence = 0
let providerOptionsSequence = 0

const generalApplicationOptions = computed(() => mergeSelectedOptions(
  applicationSearchOptions.value,
  [filters.applicationId, lookupForm.applicationId],
  applicationOptions.value
))
const generalProviderOptions = computed(() => mergeSelectedOptions(
  providerSearchOptions.value,
  [filters.identityProviderId, lookupForm.identityProviderId],
  providerOptions.value
))
const activeApplicationOptions = computed(() => mergeSelectedOptions(
  activeApplicationSearchOptions.value.filter(optionAvailable),
  [createForm.applicationId],
  applicationOptions.value.filter(optionAvailable),
  false
))
const activeProviderOptions = computed(() => mergeSelectedOptions(
  activeProviderSearchOptions.value.filter(optionAvailable),
  [createForm.identityProviderId],
  providerOptions.value.filter(optionAvailable),
  false
))

onMounted(() => {
  loadBindings()
  loadApplicationOptions()
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
  const sequence = ++bindingLoadSequence
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
    if (sequence !== bindingLoadSequence) return
    bindings.value = result?.list || result?.records || []
    pagination.total = Number(result?.total || 0)
    await Promise.all([
      loadBindingOptionLabels(bindings.value),
      loadFlowUserNames(bindings.value.map(item => item.flowUserId))
    ])
  } catch (error) {
    if (sequence !== bindingLoadSequence) return
    loadError.value = describeEmbedManagementError(error)
  } finally {
    if (sequence === bindingLoadSequence) loading.value = false
  }
}

async function loadApplicationOptions(keyword = '', status) {
  const sequence = ++applicationOptionsSequence
  applicationOptionsLoading.value = true
  optionError.value = ''
  try {
    const result = await embedManagementApi.options.applications({
      keyword: String(keyword || '').trim() || undefined,
      status,
      pageNum: 1,
      pageSize: 100
    })
    if (sequence !== applicationOptionsSequence) return
    const rows = result?.list || result?.records || []
    applicationSearchOptions.value = rows
    applicationOptions.value = mergeOptionRows(
      applicationOptions.value,
      rows
    )
  } catch (error) {
    if (sequence !== applicationOptionsSequence) return
    applicationSearchOptions.value = []
    optionError.value = `接入应用加载失败：${describeEmbedManagementError(error)}`
  } finally {
    if (sequence === applicationOptionsSequence) applicationOptionsLoading.value = false
  }
}

/** 创建态只查询 ACTIVE 应用，并替换当前远程结果，避免旧搜索项持续留在下拉中。 */
async function loadActiveApplicationOptions(keyword = '') {
  const sequence = ++applicationOptionsSequence
  applicationOptionsLoading.value = true
  optionError.value = ''
  try {
    const result = await embedManagementApi.options.applications({
      keyword: String(keyword || '').trim() || undefined,
      status: 'ACTIVE',
      pageNum: 1,
      pageSize: 100
    })
    if (sequence !== applicationOptionsSequence) return
    const rows = result?.list || result?.records || []
    activeApplicationSearchOptions.value = rows
    applicationOptions.value = mergeOptionRows(applicationOptions.value, rows)
  } catch (error) {
    if (sequence !== applicationOptionsSequence) return
    activeApplicationSearchOptions.value = []
    optionError.value = `接入应用加载失败：${describeEmbedManagementError(error)}`
  } finally {
    if (sequence === applicationOptionsSequence) applicationOptionsLoading.value = false
  }
}

async function loadProviderOptions(keyword = '', status) {
  const sequence = ++providerOptionsSequence
  providerOptionsLoading.value = true
  optionError.value = ''
  try {
    const result = await embedManagementApi.options.identityProviders({
      keyword: String(keyword || '').trim() || undefined,
      status,
      pageNum: 1,
      pageSize: 100
    })
    if (sequence !== providerOptionsSequence) return
    const rows = result?.list || result?.records || []
    providerSearchOptions.value = rows
    providerOptions.value = mergeOptionRows(
      providerOptions.value,
      rows
    )
  } catch (error) {
    if (sequence !== providerOptionsSequence) return
    providerSearchOptions.value = []
    optionError.value = `身份提供方加载失败：${describeEmbedManagementError(error)}`
  } finally {
    if (sequence === providerOptionsSequence) providerOptionsLoading.value = false
  }
}

/** 创建态只查询 ACTIVE Provider；筛选、Lookup 与历史名称回显仍查询全部状态。 */
async function loadActiveProviderOptions(keyword = '') {
  const sequence = ++providerOptionsSequence
  providerOptionsLoading.value = true
  optionError.value = ''
  try {
    const result = await embedManagementApi.options.identityProviders({
      keyword: String(keyword || '').trim() || undefined,
      status: 'ACTIVE',
      pageNum: 1,
      pageSize: 100
    })
    if (sequence !== providerOptionsSequence) return
    const rows = result?.list || result?.records || []
    activeProviderSearchOptions.value = rows
    providerOptions.value = mergeOptionRows(providerOptions.value, rows)
  } catch (error) {
    if (sequence !== providerOptionsSequence) return
    activeProviderSearchOptions.value = []
    optionError.value = `身份提供方加载失败：${describeEmbedManagementError(error)}`
  } finally {
    if (sequence === providerOptionsSequence) providerOptionsLoading.value = false
  }
}

/**
 * 当前页可能包含已停用或撤销资源，名称回显按稳定 ID 无状态补拉，避免 ACTIVE 查询丢失历史值。
 */
async function loadBindingOptionLabels(rows) {
  const applicationIds = [...new Set(rows.map(row => row.applicationId).filter(Boolean))]
  const providerIds = [...new Set(rows.map(row => row.identityProviderId).filter(Boolean))]
  await Promise.all([
    ...applicationIds.map(loadApplicationOptionById),
    ...providerIds.map(loadProviderOptionById)
  ])
}

async function loadApplicationOptionById(applicationId) {
  try {
    const result = await embedManagementApi.options.applications({
      keyword: applicationId,
      status: undefined,
      pageNum: 1,
      pageSize: 100
    })
    const option = (result?.list || result?.records || [])
      .find(item => item.id === applicationId)
    if (option) applicationOptions.value = mergeOptionRows(applicationOptions.value, [option])
  } catch {
    // 名称补拉失败不影响 Binding 管理，稳定 ID 仍留在当前页。
  }
}

async function loadProviderOptionById(providerId) {
  try {
    const result = await embedManagementApi.options.identityProviders({
      keyword: providerId,
      status: undefined,
      pageNum: 1,
      pageSize: 100
    })
    const option = (result?.list || result?.records || [])
      .find(item => item.id === providerId)
    if (option) providerOptions.value = mergeOptionRows(providerOptions.value, [option])
  } catch {
    // 名称补拉失败不影响 Binding 管理，稳定 ID 仍留在当前页。
  }
}

async function loadFlowUserNames(userIds) {
  const ids = [...new Set(userIds.filter(Boolean).map(String))]
  if (!ids.length) {
    flowUserNames.value = new Map()
    return
  }
  try {
    const users = await request.get('/entity-selector/USER/batch', {
      params: { ids: ids.join(','), valueKey: 'id' },
      silentError: true
    })
    flowUserNames.value = new Map((users || []).map(user => [
      String(user.id),
      user.name || user.code || String(user.id)
    ]))
  } catch {
    // 名称回显失败不影响 Binding 管理，表格仍保留稳定 ID 便于排查。
    flowUserNames.value = new Map()
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
  createForm.applicationId = selectedAvailableId(applicationOptions.value, filters.applicationId)
  createForm.identityProviderId = selectedAvailableId(
    providerOptions.value,
    filters.identityProviderId
  )
  createVisible.value = true
  loadActiveApplicationOptions()
  loadActiveProviderOptions()
}

function clearCreate() {
  createError.value = ''
  Object.assign(createForm, defaultBindingForm())
}

/** 从接入向导进入时按已经确定的 Application 与 Provider 收窄列表。 */
async function configure({ applicationId = '', providerId = '' } = {}) {
  filters.applicationId = applicationId
  filters.identityProviderId = providerId
  filters.flowUserId = ''
  filters.status = ''
  pagination.pageNum = 1
  await Promise.all([
    applicationId ? loadApplicationOptions(applicationId) : Promise.resolve(),
    providerId ? loadProviderOptions(providerId) : Promise.resolve(),
    loadBindings()
  ])
}

function handleFlowUserSelected(user) {
  if (user && String(user.status) !== '0') {
    createForm.flowUserId = ''
    createError.value = '只能映射到已启用的 Flow 用户'
    return
  }
  createError.value = ''
}

function buildBindingPayload() {
  if (
    !createForm.applicationId.trim()
    || !createForm.identityProviderId
    || !createForm.externalSubject.trim()
    || !createForm.flowUserId.trim()
  ) {
    throw new Error('接入应用、身份提供方、External Subject 和 Flow 用户为必填')
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
    emit('changed')
  } catch (error) {
    createForm.externalSubject = ''
    createError.value = describeEmbedManagementError(error)
  } finally {
    creating.value = false
  }
}

function openLookup() {
  clearLookup()
  lookupForm.applicationId = filters.applicationId
  lookupForm.identityProviderId = filters.identityProviderId
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
    lookupError.value = '接入应用、身份提供方和 External Subject 为必填'
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
    emit('changed')
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
    emit('changed')
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

function optionAvailable(option) {
  if (option?.status !== 'ACTIVE') return false
  return !option.expiresAt || new Date(option.expiresAt).getTime() > Date.now()
}

function selectedAvailableId(options, selectedId) {
  return options.find(option => option.id === selectedId && optionAvailable(option))?.id || ''
}

function applicationOptionLabel(application) {
  const name = application?.name || application?.applicationName || application?.id
  return application?.clientId
    ? `${name}（${application.clientId}）`
    : name
}

function providerOptionLabel(provider) {
  const name = provider?.name || provider?.id
  return provider?.type ? `${name}（${provider.type}）` : name
}

function applicationLabel(applicationId) {
  const application = applicationOptions.value.find(item => item.id === applicationId)
  return application?.name || application?.applicationName || '未加载应用名称'
}

function providerLabel(providerId) {
  return providerOptions.value.find(item => item.id === providerId)?.name
    || '未加载 Provider 名称'
}

function flowUserLabel(flowUserId) {
  return flowUserNames.value.get(String(flowUserId)) || '未加载用户名称'
}

/** 当前搜索结果仅保留正在使用的值；名称缓存不会重新污染远程筛选列表。 */
function mergeSelectedOptions(options, selectedIds, allOptions, includeFallback = true) {
  const merged = [...options]
  for (const selectedId of new Set(selectedIds.filter(Boolean))) {
    if (merged.some(option => option.id === selectedId)) continue
    const known = allOptions.find(option => option.id === selectedId)
    if (known) merged.push(known)
    else if (includeFallback) merged.push({ id: selectedId, name: selectedId })
  }
  return merged
}

function mergeOptionRows(current, incoming) {
  const byId = new Map([...current, ...incoming].map(item => [item.id, item]))
  return [...byId.values()]
}

defineExpose({ configure, refresh: loadBindings })
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
.filter-bar > .el-select,
.filter-bar > :deep(.user-selector) {
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

.resource-id {
  color: #909399;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
