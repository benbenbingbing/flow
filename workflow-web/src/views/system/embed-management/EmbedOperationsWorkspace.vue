<template>
  <div class="operations-workspace">
    <div class="operations-toolbar">
      <el-alert
        title="查询结果仅包含运维安全投影，不含 Token、Subject、Context 或摘要。"
        type="info"
        show-icon
        :closable="false"
      />
      <el-button v-if="canRevoke" type="danger" plain @click="openBulkRevoke">
        按 View / Application 批量撤销 Session
      </el-button>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="Launches" name="launches">
        <div class="filter-bar">
          <ConfigHelpLabel
            label="Launch 查询范围"
            :show-label="false"
            help-key="embed.operations.queryScope"
          />
          <el-input
            v-model="launchFilters.applicationId"
            clearable
            placeholder="Application ID（至少填一项）"
          />
          <el-input
            v-model="launchFilters.viewId"
            clearable
            placeholder="View ID（至少填一项）"
          />
          <el-select v-model="launchFilters.status" clearable placeholder="状态">
            <el-option
              v-for="status in launchStatuses"
              :key="status"
              :label="status"
              :value="status"
            />
          </el-select>
          <el-date-picker
            v-model="launchFilters.createdRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="至"
          />
          <el-select v-model="launchFilters.limit" placeholder="每页" class="limit-select">
            <el-option :label="50" :value="50" />
            <el-option :label="100" :value="100" />
            <el-option :label="200" :value="200" />
          </el-select>
          <el-button type="primary" :loading="launchLoading" @click="loadLaunches(false)">
            查询
          </el-button>
        </div>
        <el-alert
          v-if="launchError"
          type="error"
          :title="launchError"
          show-icon
          :closable="false"
          class="panel-alert"
        />
        <el-table v-loading="launchLoading" :data="launches" border>
          <el-table-column prop="id" label="Launch ID" min-width="190" />
          <el-table-column prop="applicationId" label="Application" min-width="150" />
          <el-table-column prop="viewId" label="View ID" min-width="150" />
          <el-table-column prop="entryMode" label="Entry" width="90" />
          <el-table-column width="105">
            <template #header>
              <ConfigHelpLabel label="状态" help-key="embed.operations.launchStatus" />
            </template>
            <template #default="{ row }">
              <el-tag :type="operationStatusType(row.status)" effect="plain">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="过期时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.expiresAt) }}</template>
          </el-table-column>
          <el-table-column v-if="canRevoke" label="操作" fixed="right" width="90">
            <template #default="{ row }">
              <el-button
                link
                type="danger"
                :disabled="row.status !== 'ISSUED'"
                @click="revokeLaunch(row)"
              >
                撤销
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="load-more-row">
          <span>已加载 {{ launches.length }} 条</span>
          <el-button
            v-if="launchCursor"
            :loading="launchLoading"
            @click="loadLaunches(true)"
          >
            加载下一页
          </el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Sessions" name="sessions">
        <div class="filter-bar">
          <ConfigHelpLabel
            label="Session 查询范围"
            :show-label="false"
            help-key="embed.operations.queryScope"
          />
          <el-input
            v-model="sessionFilters.applicationId"
            clearable
            placeholder="Application ID（至少填一项）"
          />
          <el-input
            v-model="sessionFilters.viewId"
            clearable
            placeholder="View ID（至少填一项）"
          />
          <el-select v-model="sessionFilters.status" clearable placeholder="状态">
            <el-option
              v-for="status in sessionStatuses"
              :key="status"
              :label="status"
              :value="status"
            />
          </el-select>
          <el-date-picker
            v-model="sessionFilters.createdRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="至"
          />
          <el-select v-model="sessionFilters.limit" placeholder="每页" class="limit-select">
            <el-option :label="50" :value="50" />
            <el-option :label="100" :value="100" />
            <el-option :label="200" :value="200" />
          </el-select>
          <el-button type="primary" :loading="sessionLoading" @click="loadSessions(false)">
            查询
          </el-button>
        </div>
        <el-alert
          v-if="sessionError"
          type="error"
          :title="sessionError"
          show-icon
          :closable="false"
          class="panel-alert"
        />
        <el-table v-loading="sessionLoading" :data="sessions" border>
          <el-table-column prop="id" label="Session ID" min-width="190" />
          <el-table-column prop="applicationId" label="Application" min-width="150" />
          <el-table-column prop="viewId" label="View ID" min-width="150" />
          <el-table-column prop="entryMode" label="Entry" width="90" />
          <el-table-column width="110">
            <template #header>
              <ConfigHelpLabel label="状态" help-key="embed.operations.sessionStatus" />
            </template>
            <template #default="{ row }">
              <el-tag :type="operationStatusType(row.status)" effect="plain">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最后活动" min-width="170">
            <template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template>
          </el-table-column>
          <el-table-column min-width="170">
            <template #header>
              <ConfigHelpLabel
                label="绝对过期"
                help-key="embed.operations.absoluteExpiry"
              />
            </template>
            <template #default="{ row }">{{ formatTime(row.absoluteExpiresAt) }}</template>
          </el-table-column>
          <el-table-column prop="revokeReason" label="撤销原因" min-width="150">
            <template #default="{ row }">{{ row.revokeReason || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="canRevoke" label="操作" fixed="right" width="90">
            <template #default="{ row }">
              <el-button
                link
                type="danger"
                :disabled="row.status !== 'ACTIVE'"
                @click="revokeSession(row)"
              >
                撤销
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="load-more-row">
          <span>已加载 {{ sessions.length }} 条</span>
          <el-button
            v-if="sessionCursor"
            :loading="sessionLoading"
            @click="loadSessions(true)"
          >
            加载下一页
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="bulkVisible"
      title="批量撤销活跃 Session"
      width="min(620px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="clearBulkForm"
    >
      <el-alert
        title="每次最多处理 200 条；若还有数据，对话框会保留 Cursor 供手动继续下一批。"
        type="warning"
        show-icon
        :closable="false"
        class="panel-alert"
      />
      <el-alert
        v-if="bulkError"
        type="error"
        :title="bulkError"
        show-icon
        :closable="false"
        class="panel-alert"
      />
      <el-form label-position="top">
        <el-form-item label="撤销范围">
          <template #label>
            <ConfigHelpLabel label="撤销范围" help-key="embed.operations.bulkScope" />
          </template>
          <el-radio-group v-model="bulkForm.scope" :disabled="Boolean(bulkForm.cursor)">
            <el-radio value="VIEW">View</el-radio>
            <el-radio value="APPLICATION">Application</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="bulkForm.scope === 'VIEW' ? 'View ID' : 'Application ID'" required>
          <el-input v-model="bulkForm.scopeId" :disabled="Boolean(bulkForm.cursor)" maxlength="64" />
        </el-form-item>
        <el-form-item label="Reason" required>
          <el-input
            v-model="bulkForm.reason"
            :disabled="Boolean(bulkForm.cursor)"
            maxlength="128"
            placeholder="例如 SECURITY_INCIDENT_2026_08"
          />
          <div class="field-help">只允许英文字母、数字及 . _ : -。</div>
        </el-form-item>
        <el-form-item label="本批上限">
          <el-input-number
            v-model="bulkForm.limit"
            :min="1"
            :max="200"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
      <el-descriptions v-if="bulkTotals.processed" :column="3" border size="small">
        <el-descriptions-item label="已处理">{{ bulkTotals.processed }}</el-descriptions-item>
        <el-descriptions-item label="已撤销">{{ bulkTotals.revoked }}</el-descriptions-item>
        <el-descriptions-item label="已终止">{{ bulkTotals.alreadyTerminal }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="bulkVisible = false">关闭</el-button>
        <el-button type="danger" :loading="bulkLoading" @click="executeBulkRevoke">
          {{ bulkForm.cursor ? '继续撤销下一批' : '撤销本批' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { embedManagementApi } from '@/api/system/embedManagement'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import { useUserStore } from '@/stores/user'
import {
  EMBED_PERMISSIONS,
  describeEmbedManagementError,
  hasEmbedPermission,
  normalizeRevokeReason,
  toInstant
} from './embedManagementModel'

const launchStatuses = ['ISSUED', 'CONSUMED', 'EXPIRED', 'REVOKED']
const sessionStatuses = ['ACTIVE', 'LOGGED_OUT', 'EXPIRED', 'REVOKED']
const userStore = useUserStore()
const activeTab = ref('launches')
const launchFilters = reactive(defaultQueryFilters())
const sessionFilters = reactive(defaultQueryFilters())
const launches = ref([])
const sessions = ref([])
const launchCursor = ref('')
const sessionCursor = ref('')
const launchLoading = ref(false)
const sessionLoading = ref(false)
const launchError = ref('')
const sessionError = ref('')
const bulkVisible = ref(false)
const bulkLoading = ref(false)
const bulkError = ref('')
const bulkForm = reactive(defaultBulkForm())
const bulkTotals = reactive({ processed: 0, revoked: 0, alreadyTerminal: 0 })

const canRevoke = computed(() => hasEmbedPermission(
  userStore.permissions,
  EMBED_PERMISSIONS.sessionRevoke,
  userStore.isSuperAdmin
))

// Cursor 只能与生成它的原查询窗口共用；筛选条件变更后立即使它失效。
watch(launchFilters, () => { launchCursor.value = '' }, { deep: true })
watch(sessionFilters, () => { sessionCursor.value = '' }, { deep: true })

function defaultQueryFilters() {
  return {
    applicationId: '',
    viewId: '',
    status: '',
    createdRange: [],
    limit: 50
  }
}

function defaultBulkForm() {
  return {
    scope: 'VIEW',
    scopeId: '',
    reason: 'ADMINISTRATIVE_REVOKE',
    limit: 100,
    cursor: ''
  }
}

function queryParams(filters, cursor) {
  const applicationId = filters.applicationId.trim()
  const viewId = filters.viewId.trim()
  if (!applicationId && !viewId) {
    throw new Error('Application ID 或 View ID 至少填写一项')
  }
  const range = Array.isArray(filters.createdRange)
    ? filters.createdRange
    : []
  return {
    applicationId: applicationId || undefined,
    viewId: viewId || undefined,
    status: filters.status || undefined,
    createdFrom: range[0] ? toInstant(range[0]) : undefined,
    createdTo: range[1] ? toInstant(range[1]) : undefined,
    cursor: cursor || undefined,
    limit: filters.limit
  }
}

async function loadLaunches(append) {
  let params
  try {
    params = queryParams(launchFilters, append ? launchCursor.value : '')
  } catch (error) {
    launchError.value = error.message
    return
  }
  launchLoading.value = true
  launchError.value = ''
  try {
    const result = await embedManagementApi.operations.launches(params)
    launches.value = append
      ? [...launches.value, ...(result?.items || [])]
      : result?.items || []
    launchCursor.value = result?.nextCursor || ''
  } catch (error) {
    launchError.value = describeEmbedManagementError(error)
  } finally {
    launchLoading.value = false
  }
}

async function loadSessions(append) {
  let params
  try {
    params = queryParams(sessionFilters, append ? sessionCursor.value : '')
  } catch (error) {
    sessionError.value = error.message
    return
  }
  sessionLoading.value = true
  sessionError.value = ''
  try {
    const result = await embedManagementApi.operations.sessions(params)
    sessions.value = append
      ? [...sessions.value, ...(result?.items || [])]
      : result?.items || []
    sessionCursor.value = result?.nextCursor || ''
  } catch (error) {
    sessionError.value = describeEmbedManagementError(error)
  } finally {
    sessionLoading.value = false
  }
}

async function revokeLaunch(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤销未消费 Launch ${row.id} ？`,
      '撤销 Launch',
      {
        type: 'warning',
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  try {
    const result = await embedManagementApi.operations.revokeLaunch(row.id)
    ElMessage.success(result?.idempotent ? 'Launch 早已撤销' : 'Launch 已撤销')
    await loadLaunches(false)
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

async function revokeSession(row) {
  let reason
  try {
    const result = await ElMessageBox.prompt(
      `确认撤销 Session ${row.id} ？`,
      '撤销 Session',
      {
        type: 'warning',
        inputValue: 'ADMINISTRATIVE_REVOKE',
        inputValidator: value => {
          try {
            normalizeRevokeReason(value)
            return true
          } catch (error) {
            return error.message
          }
        },
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消'
      }
    )
    reason = normalizeRevokeReason(result.value)
  } catch {
    return
  }
  try {
    const result = await embedManagementApi.operations.revokeSession(row.id, { reason })
    ElMessage.success(result?.idempotent ? 'Session 已处于终止状态' : 'Session 已撤销')
    await loadSessions(false)
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

function openBulkRevoke() {
  clearBulkForm()
  bulkVisible.value = true
}

function clearBulkForm() {
  bulkError.value = ''
  Object.assign(bulkForm, defaultBulkForm())
  Object.assign(bulkTotals, { processed: 0, revoked: 0, alreadyTerminal: 0 })
}

async function executeBulkRevoke() {
  const scopeId = bulkForm.scopeId.trim()
  let reason
  try {
    if (!/^[A-Za-z0-9._:-]{1,64}$/.test(scopeId)) {
      throw new Error('范围 ID 格式不合法')
    }
    reason = normalizeRevokeReason(bulkForm.reason)
    await ElMessageBox.confirm(
      `本批最多撤销 ${bulkForm.limit} 个 ${bulkForm.scope} ${scopeId} 的活跃 Session，是否继续？`,
      bulkForm.cursor ? '继续批量撤销' : '确认批量撤销',
      {
        type: 'warning',
        confirmButtonText: '确认撤销本批',
        cancelButtonText: '取消'
      }
    )
  } catch (error) {
    if (error instanceof Error) bulkError.value = error.message
    return
  }
  bulkLoading.value = true
  bulkError.value = ''
  try {
    const payload = {
      reason,
      cursor: bulkForm.cursor || undefined,
      limit: bulkForm.limit
    }
    const result = bulkForm.scope === 'VIEW'
      ? await embedManagementApi.operations.revokeViewSessions(scopeId, payload)
      : await embedManagementApi.operations.revokeApplicationSessions(scopeId, payload)
    bulkTotals.processed += result?.processed || 0
    bulkTotals.revoked += result?.revoked || 0
    bulkTotals.alreadyTerminal += result?.alreadyTerminal || 0
    bulkForm.cursor = result?.nextCursor || ''
    if (bulkForm.cursor) {
      ElMessage.warning('本批已完成，仍有活跃 Session，请手动继续下一批')
    } else {
      ElMessage.success('指定范围的活跃 Session 已处理完成')
    }
  } catch (error) {
    bulkError.value = describeEmbedManagementError(error)
  } finally {
    bulkLoading.value = false
  }
}

function operationStatusType(status) {
  return {
    ACTIVE: 'success',
    ISSUED: 'success',
    CONSUMED: 'info',
    LOGGED_OUT: 'info',
    EXPIRED: 'warning',
    REVOKED: 'danger'
  }[status] || 'info'
}

function formatTime(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}
</script>

<style scoped>
.operations-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0 12px;
}

.operations-toolbar .el-alert {
  flex: 1;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.filter-bar > .el-input,
.filter-bar > .el-select {
  width: 180px;
}

.filter-bar > .el-date-editor {
  width: 360px;
}

.filter-bar > .limit-select {
  width: 90px;
}

.panel-alert {
  margin-bottom: 12px;
}

.load-more-row {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  color: #909399;
  font-size: 13px;
}

.field-help {
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 760px) {
  .operations-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-bar > .el-date-editor {
    width: 100%;
  }
}
</style>
