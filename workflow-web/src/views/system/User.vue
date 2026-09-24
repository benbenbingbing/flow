<template>
  <div class="user-management system-management">
    <el-card>
      <el-form :model="queryParams" class="search-form" label-width="80px" @submit.prevent="handleSearch">
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="账号、姓名、邮箱或手机号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="组织/部门">
          <SystemFilterPicker
            v-model="organizationFilter"
            :options="organizationFilterOptions"
            title="选择组织/部门"
            entity-label="组织/部门"
            placeholder="全部组织/部门"
            tree
            :loading="filterLoading.organization"
            :error="filterErrors.organization"
            @retry="fetchOrgOptions"
          />
        </el-form-item>
        <el-form-item label="角色">
          <SystemFilterPicker
            v-model="queryParams.roleId"
            :options="roleFilterOptions"
            title="选择角色"
            entity-label="角色"
            placeholder="全部角色"
            :loading="filterLoading.role"
            :error="filterErrors.role"
            @retry="fetchRoleOptions"
          />
        </el-form-item>
        <template v-if="filtersExpanded">
          <el-form-item v-if="canViewPosition" label="职务">
            <SystemFilterPicker
              v-model="queryParams.positionCode"
              :options="positionFilterOptions"
              title="选择职务"
              entity-label="职务"
              placeholder="全部职务"
              :loading="filterLoading.position"
              :error="filterErrors.position"
              @retry="fetchPositionOptions"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="queryParams.status" clearable placeholder="全部状态">
              <el-option label="启用" value="0" />
              <el-option label="禁用" value="1" />
            </el-select>
          </el-form-item>
        </template>
        <div class="search-actions">
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button link type="primary" :aria-expanded="filtersExpanded" @click="filtersExpanded = !filtersExpanded">
            {{ filtersExpanded ? '收起' : '展开' }}{{ advancedFilterCount ? `（${advancedFilterCount}）` : '' }}
            <el-icon><ArrowUp v-if="filtersExpanded" /><ArrowDown v-else /></el-icon>
          </el-button>
        </div>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增用户</el-button>
        <!-- 批量入口常驻，未选择时禁用，方便用户发现功能并保持工具栏位置稳定。 -->
        <el-button :disabled="!selectedUsers.length" @click="openBatchRoleDialog">批量分配角色</el-button>
        <el-button :disabled="!selectedUsers.length" @click="handleBatchStatus('0')">批量启用</el-button>
        <el-button type="danger" plain :disabled="!selectedUsers.length" @click="handleBatchStatus('1')">批量禁用</el-button>
      </div>

      <PageState
        v-if="loadError"
        type="error"
        title="用户列表加载失败"
        :description="loadError"
        retryable
        @retry="fetchUserList"
      />

      <!-- 与流程管理保持一致：无竖向边框，长内容省略，次要操作收进菜单。 -->
      <el-table
        v-else
        v-loading="loading"
        :data="userList"
        stripe
        row-key="id"
        empty-text="当前条件下没有用户"
        @selection-change="selectedUsers = $event"
      >
        <el-table-column type="selection" width="44" :selectable="row => row.username !== 'admin'" />
        <el-table-column type="index" label="#" width="60" align="center" :index="index => (queryParams.pageNum - 1) * queryParams.pageSize + index + 1" />

        <el-table-column prop="username" label="用户名" min-width="150" show-overflow-tooltip />

        <el-table-column prop="nickname" label="昵称" min-width="130" show-overflow-tooltip />

        <el-table-column prop="email" label="邮箱" min-width="190" show-overflow-tooltip />

        <el-table-column prop="phone" label="手机号" width="130" show-overflow-tooltip />

        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="'0'"
              :inactive-value="'1'"
              inline-prompt
              active-text="启"
              inactive-text="禁"
              :disabled="row.username === 'admin'"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="组织/部门" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ [row.orgName, row.deptName].filter(Boolean).join(' / ') || '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="roles" label="角色" min-width="200">
          <template #default="{ row }">
            <div v-if="row.roles?.length" class="role-list">
              <el-tooltip :content="row.roles[0].roleName" placement="top">
                <el-tag size="small" class="role-tag">
                  <span class="role-tag__text">{{ row.roles[0].roleName }}</span>
                </el-tag>
              </el-tooltip>
              <el-tooltip
                v-if="row.roles.length > 1"
                :content="row.roles.slice(1).map(role => role.roleName).join('、')"
                placement="top"
              >
                <el-tag size="small" type="info">+{{ row.roles.length - 1 }}</el-tag>
              </el-tooltip>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column v-if="canViewPosition" label="当前任职" min-width="220">
          <template #default="{ row }">
            <div v-if="currentPositionAssignments(row).length" class="position-list">
              <el-tooltip
                v-for="assignment in currentPositionAssignments(row).slice(0, 2)"
                :key="assignment.id || `${assignment.positionCode}-${assignment.organizationUnitId}`"
                :content="positionAssignmentLabel(assignment)"
                placement="top"
              >
                <el-tag size="small" type="success" effect="plain" class="position-tag">
                  {{ assignment.positionName || assignment.positionCode }}
                </el-tag>
              </el-tooltip>
              <el-tag v-if="currentPositionAssignments(row).length > 2" size="small" type="info">
                +{{ currentPositionAssignments(row).length - 2 }}
              </el-tag>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="180" :formatter="formatDateColumn" show-overflow-tooltip />

        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <div class="table-row-actions">
              <el-button type="primary" link @click="handleView(row)">查看</el-button>
              <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
              <el-dropdown trigger="click" placement="bottom-end">
                <el-button link type="primary" :icon="MoreFilled" aria-label="更多用户操作" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="handleResetPassword(row)">重置密码</el-dropdown-item>
                    <el-dropdown-item v-if="canAssignPosition" @click="openPositionAssignment(row)">职务任命</el-dropdown-item>
                    <el-dropdown-item divided :disabled="row.username === 'admin'" @click="handleDelete(row)">
                      <span :class="{ 'danger-action': row.username !== 'admin' }">删除用户</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handlePageSizeChange"
        @current-change="fetchUserList"
      />
    </el-card>

    <el-dialog v-model="viewDialogVisible" title="查看用户" width="min(720px, 94vw)" destroy-on-close>
      <el-descriptions v-if="viewUser" :column="2" border class="user-details">
        <el-descriptions-item label="用户名">{{ viewUser.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ viewUser.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ viewUser.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ viewUser.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="viewUser.status === '0' ? 'success' : 'info'" size="small">
            {{ viewUser.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="组织/部门" :span="2">
          {{ [viewUser.orgName, viewUser.deptName].filter(Boolean).join(' / ') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="角色" :span="2">
          <div v-if="viewUser.roles?.length" class="user-detail-roles">
            <el-tag v-for="role in viewUser.roles" :key="role.id" size="small">{{ role.roleName }}</el-tag>
          </div>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="canViewPosition" label="当前任职" :span="2">
          <template v-if="currentPositionAssignments(viewUser).length">
            <div v-for="(assignment, index) in currentPositionAssignments(viewUser)" :key="assignment.assignmentId || assignment.id || index">
              {{ positionAssignmentLabel(assignment) }}
            </div>
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateValue(viewUser.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateValue(viewUser.updateTime) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 用户编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input 
                v-model="formData.username" 
                placeholder="请输入用户名"
                :disabled="!!formData.id"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="formData.nickname" placeholder="请输入昵称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="!formData.id" :gutter="20">
          <el-col :span="12">
            <el-form-item label="初始密码" prop="password">
              <el-input
                v-model="formData.password"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="10-72位，含大小写字母和数字"
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="formData.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="formData.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="formData.status">
                <el-radio value="0">启用</el-radio>
                <el-radio value="1">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="组织">
              <el-tree-select
                v-model="formData.orgId"
                :data="orgOptions"
                :props="{ label: 'orgName', value: 'id' }"
                placeholder="请选择组织"
                clearable
                check-strictly
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门">
              <el-tree-select
                v-model="formData.deptId"
                :data="deptOptions"
                :props="{ label: 'orgName', value: 'id' }"
                placeholder="请选择部门"
                clearable
                check-strictly
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="角色" prop="roleIds">
          <el-select
            v-model="formData.roleIds"
            multiple
            placeholder="请选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
          {{ formData.id ? '保存用户' : '创建用户' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchRoleDialogVisible" title="批量分配角色" width="520px">
      <el-alert
        :title="`将覆盖 ${selectedUsers.length} 个用户当前的角色配置`"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form label-width="90px" class="batch-role-form">
        <el-form-item label="新角色">
          <el-select v-model="batchRoleIds" multiple filterable style="width: 100%" placeholder="请选择角色">
            <el-option v-for="role in roleOptions" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchRoleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchLoading" @click="submitBatchRoles">确认分配</el-button>
      </template>
    </el-dialog>

    <PositionAssignmentDialog
      v-model="positionAssignmentVisible"
      :locked-user="positionAssignmentUser"
      @saved="handlePositionAssignmentSaved"
    />

  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp, MoreFilled, Search } from '@element-plus/icons-vue'
import {
  getUserPage,
  createUser,
  updateUser,
  deleteUser,
  updateUserStatus,
  batchUpdateUserStatus,
  batchAssignUserRoles,
  resetPassword,
  getRoles
} from '@/api/system/user'
import request from '@/utils/request'
import PageState from '@/components/PageState.vue'
import { formatDateColumn, formatDateValue } from '@/shared/list-runtime'
import PositionAssignmentDialog from '@/views/system/components/PositionAssignmentDialog.vue'
import SystemFilterPicker from '@/views/system/components/SystemFilterPicker.vue'
import { getEnabledPositions } from '@/api/system/position'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const canViewPosition = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:position:view')
  || userStore.permissions.includes('system:position:assign'))
const canAssignPosition = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:position:assign'))

const loading = ref(false)
const loadError = ref('')
const userList = ref<any[]>([])
const selectedUsers = ref<any[]>([])
const total = ref(0)
const roleOptions = ref<any[]>([])
const positionOptions = ref<any[]>([])
const orgOptions = ref<any[]>([])
const deptOptions = ref<any[]>([])
const organizationOptions = ref<any[]>([])
const filtersExpanded = ref(false)
const filterLoading = reactive({ organization: false, role: false, position: false })
const filterErrors = reactive({ organization: '', role: '', position: '' })
const queryParams = reactive({
  keyword: '',
  orgId: '',
  deptId: '',
  roleId: '',
  positionCode: '',
  status: '',
  pageNum: 1,
  pageSize: 20
})
// 合并控件只允许一个组织节点生效；切换组织/部门或清空时同时清除另一类旧条件。
const organizationFilter = computed({
  get: () => queryParams.deptId || queryParams.orgId,
  set: (value: string) => {
    const node = organizationOptions.value.find(item => String(item.id) === value)
    queryParams.orgId = node?.type === 'org' ? value : ''
    queryParams.deptId = node?.type === 'dept' ? value : ''
  }
})
const organizationFilterOptions = computed(() => {
  const nodes = new Map(organizationOptions.value.map(item => [String(item.id), item]))
  return organizationOptions.value.map(item => {
    const names = [item.orgName]
    const visited = new Set([String(item.id)])
    let parent = nodes.get(String(item.parentId))
    // 展示完整名称路径，帮助区分不同组织下的同名部门；不可见祖先不参与补全。
    while (parent && !visited.has(String(parent.id))) {
      names.unshift(parent.orgName)
      visited.add(String(parent.id))
      parent = nodes.get(String(parent.parentId))
    }
    return { value: String(item.id), label: item.orgName, code: item.orgCode,
      parentValue: String(item.parentId), kind: item.type, path: names.join(' / ') }
  })
})
const roleFilterOptions = computed(() => roleOptions.value.map(item => ({
  value: String(item.id), label: item.roleName, code: item.roleCode, description: item.description
})))
const positionFilterOptions = computed(() => positionOptions.value.map(item => ({
  value: item.positionCode, label: item.positionName, code: item.positionCode, description: item.description
})))
// 折叠不清空条件，用数量提示仍在生效的高级筛选；重置才恢复全部条件。
const advancedFilterCount = computed(() => Number(!!queryParams.status)
  + Number(canViewPosition.value && !!queryParams.positionCode))
const batchRoleDialogVisible = ref(false)
const batchRoleIds = ref<string[]>([])
const batchLoading = ref(false)
const positionAssignmentVisible = ref(false)
const positionAssignmentUser = ref<any>(null)
const viewDialogVisible = ref(false)
const viewUser = ref<any>(null)
// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const submitLoading = ref(false)

const formData = reactive({
  id: '',
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  status: '0',
  roleIds: [],
  orgId: '',
  deptId: ''
})

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      if (formData.id) return callback()
      const error = validateManagedPassword(value)
      callback(error ? new Error(error) : undefined)
    },
    trigger: 'blur'
  }],
  roleIds: [{ required: true, message: '请选择角色', trigger: 'change', type: 'array' }]
}

// 获取用户列表
const fetchUserList = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getUserPage({
      ...queryParams,
      keyword: queryParams.keyword.trim() || undefined,
      orgId: queryParams.orgId || undefined,
      deptId: queryParams.deptId || undefined,
      roleId: queryParams.roleId || undefined,
      positionCode: queryParams.positionCode || undefined,
      status: queryParams.status || undefined
    })
    userList.value = res?.records || []
    total.value = Number(res?.total || 0)
    queryParams.pageNum = Number(res?.pageNum || queryParams.pageNum)
    queryParams.pageSize = Number(res?.pageSize || queryParams.pageSize)
    selectedUsers.value = []
  } catch (error: any) {
    loadError.value = error?.message || '无法读取用户，请重试。'
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  fetchUserList()
}

const handleReset = () => {
  Object.assign(queryParams, {
    keyword: '',
    orgId: '',
    deptId: '',
    roleId: '',
    positionCode: '',
    status: '',
    pageNum: 1
  })
  fetchUserList()
}

const handlePageSizeChange = () => {
  queryParams.pageNum = 1
  fetchUserList()
}

// 获取角色选项，同时为选择弹窗提供加载状态和失败重试。
const fetchRoleOptions = async () => {
  filterLoading.role = true
  filterErrors.role = ''
  try {
    roleOptions.value = await getRoles() || []
  } catch (error) {
    filterErrors.role = '无法读取角色，请重试。'
    console.error('获取角色列表失败', error)
  } finally {
    filterLoading.role = false
  }
}

const fetchPositionOptions = async () => {
  if (!canViewPosition.value) return
  filterLoading.position = true
  filterErrors.position = ''
  try {
    const result = await getEnabledPositions()
    positionOptions.value = Array.isArray(result)
      ? result
      : result?.records || result?.list || []
  } catch (error) {
    filterErrors.position = '无法读取职务，请重试。'
    console.error('获取职务列表失败', error)
  } finally {
    filterLoading.position = false
  }
}

/** 兼容用户分页逐步上线期间的任职摘要字段命名。 */
const currentPositionAssignments = (row: any) => {
  const assignments = row?.currentPositionAssignments
    || row?.positionAssignments
    || row?.positions
    || []
  return Array.isArray(assignments) ? assignments : []
}

const positionAssignmentLabel = (assignment: any) => {
  const position = assignment.positionName || assignment.positionCode || '未知职务'
  const unit = assignment.organizationPath
    || assignment.organizationUnitPath
    || assignment.organizationUnitName
    || assignment.orgName
    || '未知组织节点'
  return `${position} · ${unit}`
}

const openPositionAssignment = (row: any) => {
  positionAssignmentUser.value = row
  positionAssignmentVisible.value = true
}

const handlePositionAssignmentSaved = async () => {
  await fetchUserList()
}

// 获取启用的平铺节点：查询选择器按 parentId 还原组织与部门的混合树。
const fetchOrgOptions = async () => {
  filterLoading.organization = true
  filterErrors.organization = ''
  try {
    const res = await request.get('/system/org/enabled')
    if (res && Array.isArray(res)) {
      organizationOptions.value = res
      orgOptions.value = res.filter((item: any) => item.type === 'org')
      deptOptions.value = res.filter((item: any) => item.type === 'dept')
    }
  } catch (error) {
    filterErrors.organization = '无法读取组织/部门，请重试。'
    console.error('获取组织部门列表失败', error)
  } finally {
    filterLoading.organization = false
  }
}

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    id: '',
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    status: '0',
    roleIds: [],
    orgId: '',
    deptId: ''
  })
}

// 新增用户
const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新增用户'
  dialogVisible.value = true
}

/** 展示列表记录的只读详情，不复用可写的编辑表单，避免查看操作覆盖编辑状态。 */
const handleView = (row: any) => {
  viewUser.value = { ...row }
  viewDialogVisible.value = true
}

// 编辑用户
const handleEdit = (row: any) => {
  resetForm()
  Object.assign(formData, {
    id: row.id,
    username: row.username,
    nickname: row.nickname,
    email: row.email,
    phone: row.phone,
    status: row.status,
    roleIds: row.roles?.map((r: any) => r.id) || [],
    orgId: row.orgId,
    deptId: row.deptId
  })
  dialogTitle.value = '编辑用户'
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.id) {
      // 更新用户
      await updateUser(formData.id, formData)
    } else {
      // 创建用户（只传 data，不传 id）
      await createUser(formData)
    }
    ElMessage.success(formData.id ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchUserList()
  } finally {
    submitLoading.value = false
  }
}

// 删除用户
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.prompt(
      `删除后账号将无法登录，角色和组织关系也会解除。请输入用户名「${row.username}」确认。`,
      '删除用户',
      {
        type: 'warning',
        inputPlaceholder: row.username,
        inputValidator: value => value === row.username || '输入的用户名不一致',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchUserList()
  } catch {
    // 取消删除
  }
}

// 状态变更
const handleStatusChange = async (row: any) => {
  const nextStatus = row.status
  const previousStatus = nextStatus === '0' ? '1' : '0'
  try {
    await ElMessageBox.confirm(
      nextStatus === '1'
        ? `禁用后，用户「${row.nickname || row.username}」将无法登录，正在处理的任务不会自动转交。`
        : `启用后，用户「${row.nickname || row.username}」将恢复登录和现有角色权限。`,
      nextStatus === '1' ? '禁用用户' : '启用用户',
      {
        type: nextStatus === '1' ? 'warning' : 'info',
        confirmButtonText: nextStatus === '1' ? '确认禁用' : '确认启用',
        cancelButtonText: '取消'
      }
    )
    await updateUserStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === '0' ? '用户已启用' : '用户已禁用')
  } catch {
    row.status = previousStatus
  }
}

const handleBatchStatus = async (status: string) => {
  if (!selectedUsers.value.length) return
  const action = status === '0' ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      status === '1'
        ? `将禁用 ${selectedUsers.value.length} 个用户。他们会立即失去登录能力，待办不会自动转交。`
        : `将启用 ${selectedUsers.value.length} 个用户，并恢复其现有角色权限。`,
      `批量${action}用户`,
      {
        type: status === '1' ? 'warning' : 'info',
        confirmButtonText: `确认${action}`,
        cancelButtonText: '取消'
      }
    )
    batchLoading.value = true
    await batchUpdateUserStatus(selectedUsers.value.map(user => user.id), status)
    ElMessage.success(`已${action} ${selectedUsers.value.length} 个用户`)
    await fetchUserList()
  } catch (error) {
    if (error !== 'cancel') console.error(`批量${action}失败`, error)
  } finally {
    batchLoading.value = false
  }
}

const openBatchRoleDialog = () => {
  batchRoleIds.value = []
  batchRoleDialogVisible.value = true
}

const submitBatchRoles = async () => {
  if (!selectedUsers.value.length) return
  if (!batchRoleIds.value.length) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  batchLoading.value = true
  try {
    await batchAssignUserRoles(
      selectedUsers.value.map(user => user.id),
      batchRoleIds.value
    )
    ElMessage.success(`已更新 ${selectedUsers.value.length} 个用户的角色`)
    batchRoleDialogVisible.value = false
    await fetchUserList()
  } finally {
    batchLoading.value = false
  }
}

// 重置密码
const handleResetPassword = async (row: any) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `为用户「${row.username}」设置一次性密码。密码不会在响应或日志中回显。`,
      '重置密码',
      {
        type: 'warning',
        inputType: 'password',
        inputPlaceholder: '10-72位，含大小写字母和数字',
        inputValidator: value => validateManagedPassword(value) || true,
        confirmButtonText: '设置密码',
        cancelButtonText: '取消',
        dangerouslyUseHTMLString: false
      }
    )
    await resetPassword(row.id, value)
    ElMessage.success('密码已重置，用户下次登录后必须修改')
  } catch {
    // 取消
  }
}

const validateManagedPassword = (value: string) => {
  if (!value || value.length < 10 || value.length > 72) return '密码长度必须为10到72位'
  if (!/[a-z]/.test(value) || !/[A-Z]/.test(value) || !/\d/.test(value)) {
    return '密码必须同时包含大写字母、小写字母和数字'
  }
  return ''
}

onMounted(() => {
  fetchUserList()
  fetchRoleOptions()
  fetchPositionOptions()
  fetchOrgOptions()
})
</script>

<style scoped lang="scss">
@use './system-management.scss';

.user-management {
  width: 100%;
  max-width: 100%;
  min-width: 0;
}

.danger-action { color: var(--el-color-danger); }

.user-details :deep(.el-descriptions__body) { overflow-wrap: anywhere; }
.user-detail-roles { display: flex; flex-wrap: wrap; gap: 6px; }
.user-detail-roles .el-tag { height: auto; white-space: normal; }

.role-list {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
}

.position-list {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
}

.position-tag {
  max-width: 95px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.role-tag {
  min-width: 0;
  max-width: 150px;
}

.role-tag__text {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.batch-role-form {
  margin-top: 18px;
}
</style>
