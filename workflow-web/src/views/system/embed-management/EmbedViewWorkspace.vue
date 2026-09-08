<template>
  <div class="view-workspace">
    <div class="filter-bar">
      <el-input
        v-model="filters.keyword"
        clearable
        placeholder="搜索配置 Key 或名称"
        class="keyword-input"
        @keyup.enter="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="filters.surfaceType" clearable placeholder="类型">
        <el-option label="LIST" value="LIST" />
        <el-option label="FORM" value="FORM" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="状态">
        <el-option label="待配置" value="DRAFT" />
        <el-option label="启用" value="ACTIVE" />
        <el-option label="停用" value="DISABLED" />
        <el-option label="已退役" value="RETIRED" />
      </el-select>
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
          v-for="application in visibleApplicationOptions"
          :key="application.id"
          :label="applicationOptionLabel(application)"
          :value="application.id"
        />
      </el-select>
      <el-button :loading="loading" @click="search">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <span class="filter-spacer" />
      <el-button v-if="canManage" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建配置
      </el-button>
    </div>

    <el-alert
      v-if="loadError"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
      class="workspace-alert"
    />
    <el-alert
      v-if="applicationOptionError"
      type="warning"
      :title="applicationOptionError"
      show-icon
      :closable="false"
      class="workspace-alert"
    />

    <el-table v-loading="loading" :data="views" border stripe>
      <el-table-column prop="viewKey" label="配置 Key" min-width="170" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag effect="plain">{{ row.surfaceType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="plain">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" width="76">
        <template #header>
          <ConfigHelpLabel label="CAS" help-key="embed.version.cas" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="250">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">
            接入向导
          </el-button>
          <template v-if="canManage && row.status !== 'RETIRED'">
            <el-button
              v-if="row.status === 'ACTIVE'"
              link
              type="warning"
              @click="changeStatus(row, 'DISABLED')"
            >
              停用
            </el-button>
            <el-button
              v-if="row.status === 'DISABLED'"
              link
              type="success"
              @click="changeStatus(row, 'ACTIVE')"
            >
              启用
            </el-button>
            <el-button
              link
              type="danger"
              @click="changeStatus(row, 'RETIRED')"
            >
              退役
            </el-button>
          </template>
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
        @current-change="loadViews"
        @size-change="handlePageSizeChange"
      />
    </div>

    <el-dialog
      v-model="createVisible"
      title="新建嵌入配置"
      width="min(620px, 94vw)"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-alert
        v-if="createError"
        type="error"
        :title="createError"
        show-icon
        :closable="false"
        class="workspace-alert"
      />
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <el-form-item label="配置 Key" prop="viewKey">
          <template #label>
            <ConfigHelpLabel label="配置 Key" help-key="embed.view.key" />
          </template>
          <el-input v-model="createForm.viewKey" maxlength="100" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="名称" prop="name">
            <el-input v-model="createForm.name" maxlength="128" />
          </el-form-item>
          <el-form-item label="类型" prop="surfaceType">
            <template #label>
              <ConfigHelpLabel label="类型" help-key="embed.view.surface" />
            </template>
            <el-select v-model="createForm.surfaceType" style="width: 100%">
              <el-option label="LIST" value="LIST" />
              <el-option label="FORM" value="FORM" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createView">
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="detailVisible"
      :title="selectedView ? `${selectedView.name} (${selectedView.viewKey})` : '嵌入配置'"
      size="min(1120px, 98vw)"
      append-to-body
      destroy-on-close
    >
      <template v-if="selectedView">
        <el-descriptions :column="3" border size="small" class="view-summary">
          <el-descriptions-item label="类型">
            {{ selectedView.surfaceType }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ statusLabel(selectedView.status) }}
          </el-descriptions-item>
          <el-descriptions-item label="CAS">
            <template #label>
              <ConfigHelpLabel label="CAS" help-key="embed.version.cas" />
            </template>
            {{ selectedView.version }}
          </el-descriptions-item>
        </el-descriptions>
        <el-tabs v-model="detailTab">
          <el-tab-pane label="接入向导" name="guide">
            <EmbedSetupGuide
              ref="setupGuideRef"
              :view="selectedView"
              :can-manage="canManage"
              :can-manage-identity="canManageIdentity"
              @navigate="navigateFromGuide"
            />
          </el-tab-pane>
          <el-tab-pane label="集成配置" name="draft">
            <EmbedViewDraftPanel
              :view="selectedView"
              @refresh="refreshSelectedView"
            />
          </el-tab-pane>
          <el-tab-pane label="Application Grants" name="grants">
            <EmbedGrantPanel
              ref="grantPanelRef"
              :view="selectedView"
              @changed="handleSetupChanged"
            />
          </el-tab-pane>
          <el-tab-pane
            v-if="canManageIdentity"
            label="测试用户映射"
            name="bindings"
          >
            <EmbedBindingPanel
              ref="bindingPanelRef"
              @changed="handleSetupChanged"
            />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, onMounted } from 'vue'
import dayjs from 'dayjs'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { embedManagementApi } from '@/api/system/embedManagement'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import { useUserStore } from '@/stores/user'
import EmbedBindingPanel from './EmbedBindingPanel.vue'
import EmbedGrantPanel from './EmbedGrantPanel.vue'
import EmbedSetupGuide from './EmbedSetupGuide.vue'
import EmbedViewDraftPanel from './EmbedViewDraftPanel.vue'
import {
  EMBED_PERMISSIONS,
  buildExpectedVersionPayload,
  describeEmbedManagementError,
  hasEmbedPermission
} from './embedManagementModel'

const userStore = useUserStore()
const views = ref([])
const applicationOptions = ref([])
const applicationSearchOptions = ref([])
const loading = ref(false)
const applicationOptionsLoading = ref(false)
const loadError = ref('')
const applicationOptionError = ref('')
const createVisible = ref(false)
const createFormRef = ref()
const creating = ref(false)
const createError = ref('')
const detailVisible = ref(false)
const detailTab = ref('guide')
const selectedView = ref(null)
const setupGuideRef = ref()
const grantPanelRef = ref()
const bindingPanelRef = ref()
const filters = reactive({
  keyword: '',
  surfaceType: '',
  status: '',
  applicationId: ''
})
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const createForm = reactive(defaultCreateForm())
const createRules = {
  viewKey: [{ required: true, message: '请输入 View Key', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  surfaceType: [{ required: true, message: '请选择 Surface', trigger: 'change' }]
}
let applicationOptionsSequence = 0

const canManage = computed(() => hasEmbedPermission(
  userStore.permissions,
  EMBED_PERMISSIONS.manage,
  userStore.isSuperAdmin
))
const canManageIdentity = computed(() => hasEmbedPermission(
  userStore.permissions,
  EMBED_PERMISSIONS.identityManage,
  userStore.isSuperAdmin
))
const visibleApplicationOptions = computed(() => {
  const selected = applicationOptions.value.find(
    item => item.id === filters.applicationId
  )
  if (!selected || applicationSearchOptions.value.some(item => item.id === selected.id)) {
    return applicationSearchOptions.value
  }
  // 保留已选历史应用的名称，同时让远程搜索结果只反映当前关键字。
  return [selected, ...applicationSearchOptions.value]
})

onMounted(() => {
  loadViews()
  loadApplicationOptions()
})

function defaultCreateForm() {
  return { viewKey: '', name: '', surfaceType: 'LIST', description: '' }
}

async function loadViews() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await embedManagementApi.views.page({
      keyword: filters.keyword.trim() || undefined,
      status: filters.status || undefined,
      surfaceType: filters.surfaceType || undefined,
      applicationId: filters.applicationId.trim() || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    views.value = result?.list || result?.records || []
    pagination.total = Number(result?.total || 0)
    if (selectedView.value) {
      const current = views.value.find(row => row.id === selectedView.value.id)
      if (current) selectedView.value = current
    }
  } catch (error) {
    loadError.value = describeEmbedManagementError(error)
  } finally {
    loading.value = false
  }
}

async function loadApplicationOptions(keyword = '') {
  const sequence = ++applicationOptionsSequence
  applicationOptionsLoading.value = true
  applicationOptionError.value = ''
  try {
    const result = await embedManagementApi.options.applications({
      keyword: String(keyword || '').trim() || undefined,
      pageNum: 1,
      pageSize: 100
    })
    if (sequence !== applicationOptionsSequence) return
    const incoming = result?.list || result?.records || []
    applicationSearchOptions.value = incoming
    const byId = new Map([
      ...applicationOptions.value,
      ...incoming
    ].map(item => [item.id, item]))
    applicationOptions.value = [...byId.values()]
  } catch (error) {
    if (sequence !== applicationOptionsSequence) return
    applicationSearchOptions.value = []
    applicationOptionError.value = `接入应用加载失败：${describeEmbedManagementError(error)}`
  } finally {
    if (sequence === applicationOptionsSequence) applicationOptionsLoading.value = false
  }
}

function search() {
  pagination.pageNum = 1
  loadViews()
}

function resetFilters() {
  Object.assign(filters, {
    keyword: '', surfaceType: '', status: '', applicationId: ''
  })
  search()
}

function handlePageSizeChange() {
  pagination.pageNum = 1
  loadViews()
}

function openCreate() {
  Object.assign(createForm, defaultCreateForm())
  createError.value = ''
  createVisible.value = true
}

async function createView() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }
  creating.value = true
  createError.value = ''
  try {
    const result = await embedManagementApi.views.create({
      viewKey: createForm.viewKey.trim(),
      name: createForm.name.trim(),
      surfaceType: createForm.surfaceType,
      description: createForm.description.trim() || null
    })
    createVisible.value = false
    ElMessage.success('嵌入配置已创建')
    await loadViews()
    openDetail(result)
  } catch (error) {
    createError.value = describeEmbedManagementError(error)
  } finally {
    creating.value = false
  }
}

function openDetail(row) {
  selectedView.value = row
  detailTab.value = 'guide'
  detailVisible.value = true
}

async function refreshSelectedView() {
  try {
    selectedView.value = await embedManagementApi.views.get(selectedView.value.id)
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
  await loadViews()
  await setupGuideRef.value?.refresh?.()
}

async function handleSetupChanged() {
  await setupGuideRef.value?.refresh?.()
}

/** 向导只负责导航，实际编辑仍由原有 CAS 表单完成。 */
async function navigateFromGuide(tab, scope = {}) {
  detailTab.value = tab
  await nextTick()
  if (tab === 'grants') {
    await grantPanelRef.value?.configure?.(
      scope.applicationId || '',
      scope.providerId || ''
    )
  }
  if (tab === 'bindings') {
    await bindingPanelRef.value?.configure?.(scope)
  }
}

async function changeStatus(row, status) {
  const action = status === 'ACTIVE' ? '启用' : status === 'DISABLED' ? '停用' : '退役'
  let reason
  try {
    const result = await ElMessageBox.prompt(
      status === 'RETIRED'
        ? '退役为终态，不能恢复，请填写原因。'
        : `请填写${action}原因。`,
      `${action}嵌入配置`,
      {
        type: status === 'RETIRED' ? 'warning' : 'info',
        inputValidator: value => Boolean(value?.trim()) || `请填写${action}原因`,
        confirmButtonText: `确认${action}`,
        cancelButtonText: '取消'
      }
    )
    reason = result.value.trim()
  } catch {
    return
  }
  try {
    const result = await embedManagementApi.views.changeStatus(
      row.id,
      buildExpectedVersionPayload(row.version, { status, reason })
    )
    const affected = result?.affectedActiveSessions || 0
    ElMessage.success(
      affected > 0
        ? `${action}成功，已影响 ${affected} 个活跃会话`
        : `${action}成功`
    )
    await loadViews()
  } catch (error) {
    ElMessage.error(describeEmbedManagementError(error))
  }
}

function statusType(status) {
  return {
    DRAFT: 'info', ACTIVE: 'success', DISABLED: 'warning', RETIRED: 'danger'
  }[status] || 'info'
}

function statusLabel(status) {
  return {
    DRAFT: '待配置', ACTIVE: '启用', DISABLED: '停用', RETIRED: '已退役'
  }[status] || status
}

function formatTime(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}

function applicationOptionLabel(application) {
  const name = application?.name || application?.applicationName || application?.id
  return application?.clientId
    ? `${name}（${application.clientId}）`
    : name
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

.filter-bar > .el-select,
.filter-bar > .el-input {
  width: 170px;
}

.filter-bar > .keyword-input {
  width: 250px;
}

.filter-spacer {
  flex: 1;
}

.workspace-alert {
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

.view-summary {
  margin-bottom: 12px;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
