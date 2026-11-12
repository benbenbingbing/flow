<template>
  <div class="organization-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>组织部门管理</span>
          <div class="header-actions">
            <el-button size="small" @click="handleAdd('org')">
              <el-icon><Plus /></el-icon>新增顶级组织
            </el-button>
            <el-button type="primary" size="small" @click="handleAdd('dept')">
              <el-icon><Plus /></el-icon>新增部门
            </el-button>
          </div>
        </div>
      </template>

      <ResizableTreeLayout class="organization-layout" sidebar-label="组织树宽度">
        <template #sidebar>
          <aside class="organization-sidebar">
            <div class="sidebar-title">组织架构</div>
            <OrganizationNavigationTree
              ref="navigationRef"
              :model-value="currentId"
              @select="navigateTo"
            />
          </aside>
        </template>
        <main class="organization-content">
          <el-breadcrumb separator="/" class="organization-breadcrumb">
            <el-breadcrumb-item @click="navigateTo({ id: '0' })">全部组织</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in currentPath" :key="item.id" @click="navigateTo(item)">
              {{ item.orgName }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <div class="list-toolbar">
            <el-input v-model="searchInput" placeholder="搜索当前节点及下级的名称或编码" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-radio-group v-model="filterType" size="small" @change="handleFilterChange">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="org">组织</el-radio-button>
              <el-radio-button value="dept">部门</el-radio-button>
            </el-radio-group>
          </div>
          <p class="list-description">{{ keyword ? '搜索当前节点及全部下级，点击名称可进入对应层级。' : '展示当前节点的直属组织、部门，点击名称可进入下级。' }}</p>
          <PageState
            v-if="loadError"
            type="error"
            title="组织部门加载失败"
            :description="loadError"
            retryable
            @retry="refreshOrganizations"
          />
          <el-table v-else :data="rows" row-key="id" v-loading="loading">
            <el-table-column prop="orgName" label="名称" min-width="200" fixed="left">
              <template #default="{ row }">
                <el-icon v-if="row.type === 'org'"><OfficeBuilding /></el-icon>
                <el-icon v-else><House /></el-icon>
                <el-tooltip
                  :content="row.displayPath"
                  placement="top-start"
                  :show-after="200"
                  :popper-style="{ maxWidth: '480px' }"
                >
                  <el-button type="primary" link @click="navigateTo(row)">
                    <span class="org-name">{{ row.orgName }}</span>
                  </el-button>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column v-if="keyword" prop="displayPath" label="所属路径" min-width="220" show-overflow-tooltip />
            <el-table-column prop="orgCode" label="编码" width="150" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.type === 'org'" type="primary">组织</el-tag>
                <el-tag v-else type="success">部门</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="业务层级" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ businessLevelLabel(row.businessLevelCode) }}
              </template>
            </el-table-column>
            <el-table-column prop="leaderName" label="负责人" width="120" />
            <el-table-column prop="phone" label="联系电话" width="150" />
            <el-table-column prop="sortOrder" label="排序" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.status === '0'" type="success">启用</el-tag>
                <el-tag v-else type="danger">禁用</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" :width="keyword ? 350 : 275" fixed="right">
              <template #default="{ row }">
                <el-button v-if="keyword" type="primary" link size="small" @click="navigateTo(row)">定位到树</el-button>
                <el-button type="primary" link size="small" @click="handleAddChild(row)">
                  新增下级
                </el-button>
                <el-button type="primary" link size="small" @click="handleEdit(row)">
                  编辑
                </el-button>
                <el-button
                  v-if="canAssignPosition"
                  type="primary"
                  link
                  size="small"
                  @click="openPositionAssignment(row)"
                >
                  设置职务人员
                </el-button>
                <el-button type="danger" link size="small" @click="handleDelete(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            class="organization-pagination"
            :total="pagination.total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadPage"
            @size-change="handlePageSizeChange"
          />
        </main>
      </ResizableTreeLayout>
    </el-card>
    
    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑组织部门' : '新增组织部门'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="上级" prop="parentId">
          <div class="parent-picker-field">
            <el-input :model-value="parentDisplayPath" readonly placeholder="请选择上级" :disabled="!!form.sourceInfo" @click="openParentPicker" />
            <el-button :disabled="!!form.sourceInfo" @click="openParentPicker">选择上级</el-button>
            <el-button v-if="form.type === 'org'" :disabled="!!form.sourceInfo" @click="selectParent({ id: '0' })">设为顶级</el-button>
          </div>
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type" :disabled="!!form.sourceInfo" @change="handleTypeChange">
            <el-radio-button value="org" :disabled="parentIsDepartment">组织</el-radio-button>
            <el-radio-button value="dept">部门</el-radio-button>
          </el-radio-group>
          <div class="field-help">{{ hierarchyHelp }}</div>
        </el-form-item>
        <el-form-item label="编码" prop="orgCode">
          <el-input v-model="form.orgCode" :disabled="!!form.sourceInfo" placeholder="请输入编码" />
        </el-form-item>
        <el-form-item label="名称" prop="orgName">
          <el-input v-model="form.orgName" :disabled="!!form.sourceInfo" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="业务层级">
          <el-select
            v-model="form.businessLevelCode" :disabled="!!form.sourceInfo"
            clearable
            filterable
            placeholder="请选择业务层级（可选）"
            style="width: 100%"
          >
            <el-option
              v-for="item in businessLevelOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
              :disabled="item.disabled"
            />
          </el-select>
          <div class="field-help">业务层级用于流程按语义定位组织，不等同于系统计算的物理层级 level。</div>
        </el-form-item>
        <el-form-item label="负责人">
          <template v-if="isEdit">
            <div class="leader-editor">
              <UserSelector
                v-model="form.leaderId"
                value-key="id"
                placeholder="清空可移除负责人"
                title="选择负责人"
                :disabled="!canAssignPosition || leaderSubmitting"
              />
              <el-button
                type="primary"
                plain
                :loading="leaderSubmitting"
                :disabled="!canAssignPosition || !leaderDirty"
                @click="saveOrganizationLeader"
              >
                保存负责人
              </el-button>
            </div>
            <div class="field-help">
              设置后立即生效，已生成的审批任务保持原办理人。
            </div>
          </template>
          <div v-else class="field-help">请先保存组织，再设置负责人。</div>
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" :disabled="!!form.sourceInfo" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" :disabled="!!form.sourceInfo" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" :disabled="!!form.sourceInfo" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :disabled="!!form.sourceInfo" :min="0" style="width: 100%" />
        </el-form-item>
        <el-alert v-if="form.sourceInfo" title="基础资料由业务系统维护；本地可维护扩展资料和本地启用状态。业务停用时本地启用不会使其生效。" type="info" :closable="false" style="margin-bottom: 16px" />
        <el-form-item :label="form.sourceInfo ? '本地启用' : '状态'">
          <el-radio-group v-model="form.status">
            <el-radio value="0">启用</el-radio>
            <el-radio value="1">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" :disabled="!!form.sourceInfo" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <IdentitySourceSnapshotViewer v-if="dialogVisible && form.sourceInfo" target-type="ORGANIZATION" :target-id="form.id" />
        <IdentityExtensionForm v-if="dialogVisible" ref="extensionFormRef" target-type="ORGANIZATION" :target-id="form.id" />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading" :disabled="leaderDirty">
          {{ isEdit ? '保存组织部门' : form.type === 'org' ? '创建组织' : '创建部门' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="parentPickerVisible" title="选择上级组织部门" width="600px" append-to-body destroy-on-close>
      <p class="field-help">{{ form.type === 'org' ? '请选择组织，或点击全部组织设为顶级。' : '请选择上级组织或部门。' }}当前节点及其下级已排除。</p>
      <OrganizationNavigationTree
        v-if="parentPickerVisible"
        :model-value="form.parentId || '0'"
        :type="form.type === 'org' ? 'org' : ''"
        :exclude-id="form.id"
        :selectable-root="form.type === 'org'"
        searchable
        @select="selectParent"
      />
    </el-dialog>

    <PositionAssignmentDialog
      v-model="positionAssignmentVisible"
      :locked-unit="positionAssignmentUnit"
      @saved="handlePositionAssignmentSaved"
    />
  </div>
</template>

<script setup>
import IdentityExtensionForm from '@/views/system/components/IdentityExtensionForm.vue'
import IdentitySourceSnapshotViewer from '@/views/system/components/IdentitySourceSnapshotViewer.vue'
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, OfficeBuilding, House } from '@element-plus/icons-vue'
import request from '@/utils/request'
import PageState from '@/components/PageState.vue'
import UserSelector from '@/components/UserSelector.vue'
import PositionAssignmentDialog from '@/views/system/components/PositionAssignmentDialog.vue'
import { useUserStore } from '@/stores/user'
import { updateOrganizationLeader } from '@/api/system/position'
import { getOrganizationBusinessLevelOptions, getOrganizationPage, getOrganizationAncestors } from '@/api/system/org'
import OrganizationNavigationTree from '@/views/system/components/OrganizationNavigationTree.vue'
import ResizableTreeLayout from '@/views/system/components/ResizableTreeLayout.vue'

const userStore = useUserStore()
const canAssignPosition = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:position:assign'))

const loading = ref(false)
const loadError = ref('')
const rows = ref([])
const navigationRef = ref(null)
const currentId = ref('0')
const currentPath = ref([])
const searchInput = ref('')
const keyword = ref('')
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const parentPickerVisible = ref(false)
const selectedParent = ref(null)
const parentDisplayPath = computed(() => form.parentId === '0' ? '顶级组织' : selectedParent.value?.displayPath || selectedParent.value?.orgName || '')
let pageSequence = 0
let navigationSequence = 0
let editSequence = 0
const filterType = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const extensionFormRef = ref(null)
const positionAssignmentVisible = ref(false)
const positionAssignmentUnit = ref(null)
const businessLevelOptions = ref([])
const originalLeaderId = ref('')
const leaderSubmitting = ref(false)
const leaderDirty = computed(() => isEdit.value
  && String(form.leaderId || '') !== String(originalLeaderId.value || ''))

const parentIsDepartment = computed(() => selectedParent.value?.type === 'dept')

const hierarchyHelp = computed(() => {
  if (form.type === 'org') return '组织可作为顶级节点，也可隶属于另一个组织。'
  return '部门必须选择上级组织或上级部门，不能创建为顶级节点。'
})

const form = reactive({
  id: '',
  parentId: '0',
  type: 'org',
  orgCode: '',
  orgName: '',
  businessLevelCode: '',
  leaderId: '',
  phone: '',
  email: '',
  address: '',
  sortOrder: 0,
  status: '0',
  description: '',
  sourceInfo: null
})

const rules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  parentId: [{
    validator: (_rule, value, callback) => {
      if (form.type === 'dept' && (!value || value === '0')) {
        callback(new Error('部门必须选择上级'))
        return
      }
      callback()
    },
    trigger: 'change'
  }],
  orgCode: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '编码只能包含字母、数字、下划线和横线', trigger: 'blur' }
  ],
  orgName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

/** 翻页、搜索和切换层级共享数据库分页，过期响应不能覆盖较新的选择。 */
async function loadPage() {
  const sequence = ++pageSequence
  loading.value = true
  loadError.value = ''
  try {
    const page = await getOrganizationPage({
      parentId: currentId.value,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: keyword.value,
      type: filterType.value
    })
    if (sequence !== pageSequence) return
    pagination.total = page.total
    // 删除当前末页最后一条后退到有效页，避免留下空白列表。
    const lastPage = Math.max(1, Math.ceil(page.total / pagination.pageSize))
    if (pagination.pageNum > lastPage) {
      pagination.pageNum = lastPage
      return await loadPage()
    }
    rows.value = page.records
  } catch (e) {
    if (sequence === pageSequence) loadError.value = e?.message || '无法读取组织部门，请重试。'
  } finally {
    if (sequence === pageSequence) loading.value = false
  }
}

/** 服务端返回真实父链，搜索命中深层节点时也可同步定位到尚未展开的树。 */
async function navigateTo(row) {
  const sequence = ++navigationSequence
  ++pageSequence
  loading.value = true
  loadError.value = ''
  try {
    const path = row.id === '0' ? [] : await getOrganizationAncestors(row.id)
    if (sequence !== navigationSequence) return
    currentId.value = row.id
    currentPath.value = path
    searchInput.value = ''
    keyword.value = ''
    pagination.pageNum = 1
    await loadPage()
  } catch (e) {
    if (sequence === navigationSequence) {
      loading.value = false
      loadError.value = e?.message || '无法定位组织部门'
    }
  }
}

/** 修改或任职后刷新当前页和已选路径，并使树中的下级标记与最新结构一致。 */
async function refreshOrganizations() {
  const scopeId = currentId.value
  try {
    const path = scopeId === '0' ? [] : await getOrganizationAncestors(scopeId)
    if (scopeId !== currentId.value) return
    currentPath.value = path
    await Promise.all([loadPage(), navigationRef.value?.reload()])
  } catch (e) {
    // 写入可能已成功，刷新失败只进入列表重试状态，避免提示提交失败而诱发重复创建。
    if (scopeId === currentId.value) loadError.value = e?.message || '组织部门刷新失败，请重新加载。'
  }
}

function handleSearch() {
  keyword.value = searchInput.value.trim()
  pagination.pageNum = 1
  loadPage()
}

function handlePageSizeChange() {
  pagination.pageNum = 1
  loadPage()
}

function openParentPicker() {
  if (!form.sourceInfo) parentPickerVisible.value = true
}

function selectParent(row) {
  form.parentId = row.id
  selectedParent.value = row.id === '0' ? null : row
  parentPickerVisible.value = false
  formRef.value?.clearValidate('parentId')
}

async function loadBusinessLevels() {
  try {
    const items = await getOrganizationBusinessLevelOptions()
    businessLevelOptions.value = (items || []).map(item => ({
      value: item.code,
      label: item.name || item.code,
      disabled: false
    }))
  } catch (error) {
    businessLevelOptions.value = []
    console.error('加载组织业务层级失败:', error)
  }
}

function businessLevelLabel(code) {
  if (!code) return '-'
  return businessLevelOptions.value.find(item => item.value === code)?.label || code
}

function handleFilterChange() {
  pagination.pageNum = 1
  loadPage()
}

function handleAdd(type = 'org') {
  ++editSequence
  isEdit.value = false
  resetForm()
  form.type = type
  const current = currentPath.value.at(-1)
  if (type === 'dept' && current) selectParent(current)
  else form.parentId = type === 'org' ? '0' : ''
  dialogVisible.value = true
}

function handleAddChild(row) {
  ++editSequence
  isEdit.value = false
  resetForm()
  selectParent(row)
  form.type = 'dept'
  dialogVisible.value = true
}

async function handleEdit(row) {
  const sequence = ++editSequence
  try {
    row = await request.get('/system/org/' + encodeURIComponent(row.id))
    const parentPath = row.parentId && row.parentId !== '0' ? await getOrganizationAncestors(row.parentId) : []
    // 连续点击不同记录时，旧详情及旧父链不能覆盖最后一次编辑选择。
    if (sequence !== editSequence) return
    isEdit.value = true
    resetForm()
    Object.assign(form, row)
    selectedParent.value = parentPath.at(-1) || null
    form.status = row.sourceInfo ? (row.sourceInfo.localAllowed ? '0' : '1') : row.status
    originalLeaderId.value = String(row.leaderId || '')
    dialogVisible.value = true
  } catch (e) {
    if (sequence === editSequence) ElMessage.error(e?.message || '组织部门详情加载失败')
  }
}

function openPositionAssignment(row) {
  positionAssignmentUnit.value = row
  positionAssignmentVisible.value = true
}

async function handlePositionAssignmentSaved() {
  await refreshOrganizations()
}

/** 负责人直接保存当前关系；已读取的人员集合用于检测并发修改。 */
async function saveOrganizationLeader() {
  if (!isEdit.value || !leaderDirty.value || !canAssignPosition.value) return
  leaderSubmitting.value = true
  try {
    await updateOrganizationLeader(form.id, {
      userId: form.leaderId || null,
      expectedUserIds: originalLeaderId.value ? [originalLeaderId.value] : []
    })
    originalLeaderId.value = String(form.leaderId || '')
    ElMessage.success('负责人已保存')
    await refreshOrganizations()
  } finally { leaderSubmitting.value = false }
}

function handleTypeChange(type) {
  if (type === 'org' && parentIsDepartment.value) selectParent({ id: '0' })
  if (type === 'dept' && form.parentId === '0') {
    form.parentId = ''
    selectedParent.value = null
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    // 只发送可编辑元数据；leaderId、物理 level/path 和 children 都是服务端投影。
    const organizationPayload = {
      parentId: form.parentId,
      type: form.type,
      orgCode: form.orgCode,
      orgName: form.orgName,
      businessLevelCode: form.businessLevelCode || '',
      phone: form.phone,
      email: form.email,
      address: form.address,
      sortOrder: form.sortOrder,
      status: form.status,
      description: form.description,
      extensions: extensionFormRef.value?.getChanges() || {}
    }
    if (isEdit.value) {
      await request.post(`/system/org/${form.id}/update`, organizationPayload)
      ElMessage.success('更新成功')
    } else {
      await request.post('/system/org', organizationPayload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await refreshOrganizations()
  } catch (e) {
    console.error('提交失败:', e)
    ElMessage.error('提交失败')
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(
    `删除前必须确保没有下级节点和关联用户。确认删除「${row.orgName}」吗？`,
    '删除组织部门',
    {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.post(`/system/org/${row.id}/delete`)
      ElMessage.success('删除成功')
      // 搜索结果包含当前节点，删除当前叶节点后回到仍存在的上级。
      if (row.id === currentId.value) await navigateTo(currentPath.value.at(-2) || { id: '0' })
      await refreshOrganizations()
    } catch (e) {
      console.error('删除失败:', e)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

function resetForm() {
  selectedParent.value = null
  form.id = ''
  form.parentId = '0'
  form.type = 'org'
  form.orgCode = ''
  form.orgName = ''
  form.businessLevelCode = ''
  form.leaderId = ''
  form.phone = ''
  form.email = ''
  form.address = ''
  form.sortOrder = 0
  form.status = '0'
  form.description = ''
  form.sourceInfo = null
  originalLeaderId.value = ''
}

onMounted(() => {
  loadPage()
  loadBusinessLevels()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.organization-sidebar {
  padding-right: 16px;
  min-width: 0;
}

.sidebar-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.organization-content {
  min-width: 0;
}

.organization-breadcrumb {
  margin: 4px 0 18px;
  line-height: 1.8;
}

.organization-breadcrumb :deep(.el-breadcrumb__inner) {
  cursor: pointer;
}

.list-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.list-toolbar > .el-input {
  width: min(360px, 100%);
}

.list-description {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.organization-pagination {
  margin-top: 16px;
  justify-content: flex-end;
  overflow: auto;
}

.parent-picker-field {
  display: flex;
  gap: 8px;
  width: 100%;
}

.org-name {
  cursor: pointer;
  color: var(--el-color-primary);
  margin-left: 5px;
}

.field-help {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.leader-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  width: 100%;
}

@media (max-width: 1000px) {
  .organization-sidebar {
    border-bottom: 1px solid var(--el-border-color-light);
    padding: 0 0 16px;
  }

  .organization-sidebar :deep(.tree-scroll) {
    max-height: 220px;
  }
}

@media (max-width: 760px) {
  .card-header,
  .header-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
