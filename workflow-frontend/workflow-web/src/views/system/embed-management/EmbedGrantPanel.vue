<template>
  <div class="grant-panel">
    <div class="panel-toolbar">
      <div>
        <strong>Application Grants</strong>
        <span class="toolbar-note">每个 View + Application 仅保留一份精确授权</span>
      </div>
      <div>
        <el-button :loading="loading" @click="loadGrants">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">
          新建 Grant
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="panel-alert"
    />

    <el-table v-loading="loading" :data="grants" border>
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
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Origin" min-width="220">
        <template #default="{ row }">
          <div v-for="origin in row.allowedOrigins" :key="origin" class="origin-line">
            {{ origin }}
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="version" width="76">
        <template #header>
          <ConfigHelpLabel label="CAS" help-key="embed.version.cas" />
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" fixed="right" width="230">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.status === 'REVOKED'"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
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

    <el-dialog
      v-if="canManage"
      v-model="editorVisible"
      :title="editingGrant ? '编辑 Grant' : '新建 Grant'"
      width="min(860px, 96vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="clearEditor"
    >
      <el-alert
        v-if="editorError"
        type="error"
        :title="editorError"
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
      <el-form label-position="top">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="panel-alert"
          title="Grant 只授权应用、来源与能力；每次新 Launch 自动使用目标资源最新 ACTIVE 版本，已打开 Session 不会中途漂移。"
        />
        <div class="form-grid">
          <el-form-item label="接入应用" required>
            <template #label>
              <ConfigHelpLabel
                label="接入应用"
                help-key="embed.application.internalId"
              />
            </template>
            <el-select
              v-model="grantForm.applicationId"
              filterable
              remote
              reserve-keyword
              :remote-method="loadActiveApplicationOptions"
              :loading="applicationOptionsLoading"
              :disabled="Boolean(editingGrant)"
              placeholder="按应用名称搜索"
              style="width: 100%"
            >
              <el-option
                v-for="application in selectableApplications"
                :key="application.id"
                :label="applicationOptionLabel(application)"
                :value="application.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="身份提供方" required>
            <template #label>
              <ConfigHelpLabel
                label="身份提供方"
                help-key="embed.grant.identityProvider"
              />
            </template>
            <el-select
              v-model="grantForm.identityProviderId"
              filterable
              remote
              reserve-keyword
              :remote-method="loadActiveProviderOptions"
              :loading="providerOptionsLoading"
              placeholder="按 Provider 名称搜索"
              style="width: 100%"
              @change="handleProviderChange"
            >
              <el-option
                v-for="provider in selectableProviders"
                :key="provider.id"
                :label="providerOptionLabel(provider)"
                :value="provider.id"
              />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="状态">
            <el-select v-model="grantForm.status" style="width: 100%">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item>
          <div class="checkbox-help-line">
            <el-checkbox v-model="grantForm.trustedSubjectAssertion">
              允许宿主直接声明可信外部用户 ID
            </el-checkbox>
            <ConfigHelpLabel
              label="可信外部用户 ID"
              :show-label="false"
              help-key="embed.grant.trustedSubjectAssertion"
            />
          </div>
          <div class="field-help">仅 TRUSTED_EXTERNAL_ID Provider 必须启用，SIGNED_JWT 必须关闭。</div>
        </el-form-item>
        <el-form-item label="Allowed Origins" required>
          <template #label>
            <ConfigHelpLabel
              label="Allowed Origins"
              help-key="embed.grant.allowedOrigins"
            />
          </template>
          <el-input
            v-model="grantForm.allowedOriginsText"
            type="textarea"
            :rows="4"
            placeholder="每行一个，例如 https://portal.example.com"
          />
          <div class="field-help">不接受路径、Query、Fragment、通配符或 HTTP。</div>
        </el-form-item>
        <el-form-item label="Capability Ceiling" required>
          <template #label>
            <ConfigHelpLabel
              label="Capability Ceiling"
              help-key="embed.grant.capabilityCeiling"
            />
          </template>
          <el-select
            v-model="grantForm.capabilityCeiling"
            multiple
            style="width: 100%"
          >
            <el-option
              v-for="capability in capabilities"
              :key="capability"
              :label="capability"
              :value="capability"
              :disabled="blockedCapabilities.includes(capability)"
            />
          </el-select>
        </el-form-item>
        <div class="form-grid three-columns">
          <el-form-item label="每用户最大活跃 Session">
            <template #label>
              <ConfigHelpLabel
                label="每用户最大活跃 Session"
                help-key="embed.grant.maxActiveSessionsPerUser"
              />
            </template>
            <el-input-number
              v-model="grantForm.maxActiveSessionsPerUser"
              :min="1"
              :max="10000"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="Session 最长秒数">
            <template #label>
              <ConfigHelpLabel
                label="Session 最长秒数"
                help-key="embed.grant.maxSessionSeconds"
              />
            </template>
            <el-input-number
              v-model="grantForm.maxSessionSeconds"
              :min="60"
              :max="86400"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="最大并发">
            <template #label>
              <ConfigHelpLabel
                label="最大并发"
                help-key="embed.grant.maxConcurrency"
              />
            </template>
            <el-input-number
              v-model="grantForm.maxConcurrency"
              :min="1"
              :max="1000"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="Launch /分钟">
            <template #label>
              <ConfigHelpLabel label="Launch /分钟" help-key="embed.grant.launchRate" />
            </template>
            <el-input-number
              v-model="grantForm.launchLimitPerMinute"
              :min="1"
              :max="10000"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="Runtime /分钟">
            <template #label>
              <ConfigHelpLabel label="Runtime /分钟" help-key="embed.grant.runtimeRate" />
            </template>
            <el-input-number
              v-model="grantForm.runtimeLimitPerMinute"
              :min="1"
              :max="100000"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="授权过期时间">
            <template #label>
              <ConfigHelpLabel
                label="授权过期时间"
                help-key="embed.grant.expiresAt"
              />
            </template>
            <el-date-picker
              v-model="grantForm.expiresAt"
              type="datetime"
              placeholder="不设置表示不过期"
              style="width: 100%"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button v-if="canManage" type="primary" :loading="saving" @click="saveGrant">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { embedManagementApi } from '@/api/system/embedManagement'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import { useUserStore } from '@/stores/user'
import {
  EMBED_CAPABILITIES,
  EMBED_PERMISSIONS,
  EMBED_V1_BLOCKED_CAPABILITIES,
  buildExpectedVersionPayload,
  describeEmbedManagementError,
  hasEmbedPermission,
  normalizeExactOrigins,
  toInstant,
  toLineValues
} from './embedManagementModel'

const props = defineProps({
  view: { type: Object, required: true }
})
const emit = defineEmits(['changed'])

const userStore = useUserStore()
const capabilities = EMBED_CAPABILITIES
const blockedCapabilities = computed(() => [
  ...EMBED_V1_BLOCKED_CAPABILITIES,
  ...(props.view.surfaceType === 'LIST' ? ['ACTION_EXECUTE'] : [])
])
const grants = ref([])
const applicationOptions = ref([])
const providerOptions = ref([])
const applicationSearchOptions = ref([])
const providerSearchOptions = ref([])
const loading = ref(false)
const saving = ref(false)
const applicationOptionsLoading = ref(false)
const providerOptionsLoading = ref(false)
const loadError = ref('')
const editorError = ref('')
const optionError = ref('')
const editorVisible = ref(false)
const editingGrant = ref(null)
const grantForm = reactive(defaultGrantForm())
let applicationOptionsSequence = 0
let providerOptionsSequence = 0

const canManage = computed(() => hasEmbedPermission(
  userStore.permissions,
  EMBED_PERMISSIONS.manage,
  userStore.isSuperAdmin
))
watch(canManage, allowed => {
  if (!allowed) editorVisible.value = false
})
const selectableApplications = computed(() => mergeSelectedOption(
  applicationSearchOptions.value.filter(optionAvailable),
  editingGrant.value ? grantForm.applicationId : '',
  applicationOptions.value,
  { id: grantForm.applicationId, name: grantForm.applicationId }
))
const selectableProviders = computed(() => mergeSelectedOption(
  providerSearchOptions.value.filter(optionAvailable),
  editingGrant.value ? grantForm.identityProviderId : '',
  providerOptions.value,
  { id: grantForm.identityProviderId, name: grantForm.identityProviderId }
))

watch(() => props.view.id, loadGrants, { immediate: true })
onMounted(() => Promise.all([
  loadActiveApplicationOptions(),
  loadActiveProviderOptions()
]))

function defaultGrantForm() {
  return {
    applicationId: '',
    identityProviderId: '',
    status: 'ACTIVE',
    trustedSubjectAssertion: false,
    allowedOriginsText: '',
    capabilityCeiling: [],
    maxActiveSessionsPerUser: 3,
    maxSessionSeconds: 3600,
    launchLimitPerMinute: 60,
    runtimeLimitPerMinute: 600,
    maxConcurrency: 10,
    expiresAt: null
  }
}

async function loadGrants() {
  loading.value = true
  loadError.value = ''
  try {
    grants.value = await embedManagementApi.grants.list(props.view.id) || []
    await loadGrantOptionLabels(grants.value)
  } catch (error) {
    loadError.value = describeEmbedManagementError(error)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  if (!canManage.value) return
  clearEditor()
  editorVisible.value = true
  loadActiveApplicationOptions()
  loadActiveProviderOptions()
}

function openEdit(row) {
  if (!canManage.value || row?.status === 'REVOKED') return
  editingGrant.value = row
  Object.assign(grantForm, {
    applicationId: row.applicationId,
    identityProviderId: row.identityProviderId,
    status: row.status,
    trustedSubjectAssertion: row.trustedSubjectAssertion,
    allowedOriginsText: toLineValues(row.allowedOrigins),
    capabilityCeiling: Array.isArray(row.capabilityCeiling)
      ? [...row.capabilityCeiling]
      : [],
    maxActiveSessionsPerUser: row.maxActiveSessionsPerUser,
    maxSessionSeconds: row.maxSessionSeconds,
    launchLimitPerMinute: row.launchLimitPerMinute,
    runtimeLimitPerMinute: row.runtimeLimitPerMinute,
    maxConcurrency: row.maxConcurrency,
    expiresAt: utcDate(row.expiresAt)
  })
  editorVisible.value = true
  loadApplicationOptionById(row.applicationId)
  loadProviderOptionById(row.identityProviderId)
}

function clearEditor() {
  editingGrant.value = null
  editorError.value = ''
  Object.assign(grantForm, defaultGrantForm())
}

/**
 * 从接入向导进入时，直接定位该应用已有的 Grant；尚未创建则预填名称选择结果。
 */
async function configure(applicationId, identityProviderId = '') {
  if (!canManage.value) return
  await loadGrants()
  const existing = grants.value.find(item => item.applicationId === applicationId)
  if (existing) {
    if (existing.status === 'REVOKED') return
    openEdit(existing)
    return
  }
  clearEditor()
  grantForm.applicationId = applicationId || ''
  grantForm.identityProviderId = identityProviderId || ''
  editorVisible.value = true
  await Promise.all([
    applicationId ? loadActiveApplicationOptions(applicationId) : Promise.resolve(),
    identityProviderId ? loadActiveProviderOptions(identityProviderId) : Promise.resolve()
  ])
}

/** 创建态只查询 ACTIVE 应用；当前搜索结果与历史名称缓存分离，避免旧结果干扰远程筛选。 */
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

/** 创建态只查询 ACTIVE Provider；历史 Grant 的当前值由无状态精确查询补回。 */
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

/**
 * 表格与编辑器按稳定 ID 补拉名称，不施加 ACTIVE 条件，以便已停用或撤销的历史值仍可辨认。
 */
async function loadGrantOptionLabels(rows) {
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
    // 名称补拉失败不影响 Grant 管理，稳定 ID 仍保留在表格和编辑器中。
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
    // 名称补拉失败不影响 Grant 管理，稳定 ID 仍保留在表格和编辑器中。
  }
}

function handleProviderChange(providerId) {
  const provider = providerOptions.value.find(item => item.id === providerId)
  if (provider) {
    grantForm.trustedSubjectAssertion = provider.type === 'TRUSTED_EXTERNAL_ID'
  }
}

function buildGrantPayload() {
  if (!grantForm.applicationId.trim() || !grantForm.identityProviderId.trim()) {
    throw new Error('请选择接入应用和身份提供方')
  }
  if (!grantForm.capabilityCeiling.length) {
    throw new Error('至少选择一项 Capability Ceiling')
  }
  return {
    expectedVersion: editingGrant.value?.version,
    status: grantForm.status,
    identityProviderId: grantForm.identityProviderId.trim(),
    trustedSubjectAssertion: grantForm.trustedSubjectAssertion,
    allowedOrigins: normalizeExactOrigins(grantForm.allowedOriginsText),
    capabilityCeiling: [...grantForm.capabilityCeiling],
    maxActiveSessionsPerUser: grantForm.maxActiveSessionsPerUser,
    maxSessionSeconds: grantForm.maxSessionSeconds,
    launchLimitPerMinute: grantForm.launchLimitPerMinute,
    runtimeLimitPerMinute: grantForm.runtimeLimitPerMinute,
    maxConcurrency: grantForm.maxConcurrency,
    expiresAt: toInstant(grantForm.expiresAt)
  }
}

async function saveGrant() {
  if (!canManage.value) return
  let payload
  try {
    payload = buildGrantPayload()
  } catch (error) {
    editorError.value = error.message
    return
  }
  saving.value = true
  editorError.value = ''
  try {
    await embedManagementApi.grants.upsert(
      props.view.id,
      grantForm.applicationId.trim(),
      payload
    )
    ElMessage.success('Grant 已保存')
    editorVisible.value = false
    await loadGrants()
    emit('changed')
  } catch (error) {
    editorError.value = describeEmbedManagementError(error)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const target = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await embedManagementApi.grants.changeStatus(
      props.view.id,
      row.applicationId,
      buildExpectedVersionPayload(row.version, {
        status: target,
        reason: '管理台状态变更'
      })
    )
    ElMessage.success(target === 'ACTIVE' ? 'Grant 已启用' : 'Grant 已停用')
    await loadGrants()
    emit('changed')
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

async function revoke(row) {
  let reason
  try {
    const result = await ElMessageBox.prompt(
      '撤销为终态且会废弃已有会话，请填写原因。',
      '撤销 Grant',
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
    await embedManagementApi.grants.revoke(
      props.view.id,
      row.applicationId,
      buildExpectedVersionPayload(row.version, { reason })
    )
    ElMessage.success('Grant 已撤销')
    await loadGrants()
    emit('changed')
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

function utcDate(value) {
  if (!value) return null
  return new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}Z`)
}

function statusType(status) {
  return { ACTIVE: 'success', DISABLED: 'warning', REVOKED: 'danger' }[status] || 'info'
}

function statusLabel(status) {
  return { ACTIVE: '启用', DISABLED: '停用', REVOKED: '已撤销' }[status] || status
}

function optionAvailable(option) {
  if (option?.status !== 'ACTIVE') return false
  return !option.expiresAt || new Date(option.expiresAt).getTime() > Date.now()
}

function mergeSelectedOption(options, selectedId, allOptions, fallback) {
  if (!selectedId || options.some(option => option.id === selectedId)) return options
  const known = allOptions.find(option => option.id === selectedId)
  return [...options, known || fallback]
}

function mergeOptionRows(current, incoming) {
  const byId = new Map([...current, ...incoming].map(item => [item.id, item]))
  return [...byId.values()]
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

defineExpose({ configure, refresh: loadGrants })
</script>

<style scoped>
.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-note,
.field-help {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.field-help {
  margin: 5px 0 0;
}

.checkbox-help-line {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.panel-alert {
  margin-bottom: 12px;
}

.origin-line {
  overflow-wrap: anywhere;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.resource-id {
  color: #909399;
  overflow-wrap: anywhere;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.three-columns {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

@media (max-width: 720px) {
  .form-grid,
  .three-columns {
    grid-template-columns: 1fr;
  }
}
</style>
