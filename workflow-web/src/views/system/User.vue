<template>
  <div class="user-management">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增用户
      </el-button>
    </div>

    <el-form :model="queryParams" inline class="user-filters">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          placeholder="账号、姓名、邮箱或手机号"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="组织">
        <el-select v-model="queryParams.orgId" clearable filterable placeholder="全部组织">
          <el-option v-for="item in orgOptions" :key="item.id" :label="item.orgName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="部门">
        <el-select v-model="queryParams.deptId" clearable filterable placeholder="全部部门">
          <el-option v-for="item in deptOptions" :key="item.id" :label="item.orgName" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="queryParams.roleId" clearable filterable placeholder="全部角色">
          <el-option v-for="role in roleOptions" :key="role.id" :label="role.roleName" :value="role.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部状态">
          <el-option label="启用" value="0" />
          <el-option label="禁用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="batch-toolbar">
      <span>已选择 {{ selectedUsers.length }} 人</span>
      <el-button :disabled="!selectedUsers.length" @click="openBatchRoleDialog">批量分配角色</el-button>
      <el-button :disabled="!selectedUsers.length" @click="handleBatchStatus('0')">批量启用</el-button>
      <el-button type="danger" plain :disabled="!selectedUsers.length" @click="handleBatchStatus('1')">
        批量禁用
      </el-button>
    </div>

    <PageState
      v-if="loadError"
      type="error"
      title="用户列表加载失败"
      :description="loadError"
      retryable
      @retry="fetchUserList"
    />
    
    <!-- 用户表格 -->
    <el-table
      v-else
      v-loading="loading"
      :data="userList"
      border
      stripe
      empty-text="当前条件下没有用户"
      @selection-change="selectedUsers = $event"
    >
      <el-table-column type="selection" width="44" :selectable="row => row.username !== 'admin'" />
      <el-table-column type="index" label="#" width="60" align="center" />
      
      <el-table-column prop="username" label="用户名" min-width="120" />
      
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      
      <el-table-column prop="phone" label="手机号" min-width="120" />
      
      <el-table-column prop="orgName" label="组织" min-width="120" />
      
      <el-table-column prop="deptName" label="部门" min-width="120" />
      
      <el-table-column prop="roles" label="角色" min-width="180">
        <template #default="{ row }">
          <el-tag 
            v-for="role in row.roles" 
            :key="role.id"
            size="small"
            style="margin-right: 4px; margin-bottom: 2px"
          >
            {{ role.roleName }}
          </el-tag>
        </template>
      </el-table-column>
      
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
      
      <el-table-column prop="createTime" label="创建时间" width="170" :formatter="formatDateColumn" />
      
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button type="primary" link size="small" @click="handleResetPassword(row)">
            重置密码
          </el-button>
          <el-button 
            type="danger" 
            link 
            size="small" 
            :disabled="row.username === 'admin'"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
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

  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
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
import { formatDateColumn } from '@/shared/list-runtime'

const loading = ref(false)
const loadError = ref('')
const userList = ref<any[]>([])
const selectedUsers = ref<any[]>([])
const total = ref(0)
const roleOptions = ref<any[]>([])
const orgOptions = ref<any[]>([])
const deptOptions = ref<any[]>([])
const queryParams = reactive({
  keyword: '',
  orgId: '',
  deptId: '',
  roleId: '',
  status: '',
  pageNum: 1,
  pageSize: 20
})
const batchRoleDialogVisible = ref(false)
const batchRoleIds = ref<string[]>([])
const batchLoading = ref(false)
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
    status: '',
    pageNum: 1
  })
  fetchUserList()
}

const handlePageSizeChange = () => {
  queryParams.pageNum = 1
  fetchUserList()
}

// 获取角色选项
const fetchRoleOptions = async () => {
  try {
    roleOptions.value = await getRoles() || []
  } catch (error) {
    console.error('获取角色列表失败', error)
  }
}

// 获取组织部门选项
const fetchOrgOptions = async () => {
  try {
    const res = await request.get('/system/org/enabled')
    if (res && Array.isArray(res)) {
      orgOptions.value = res.filter((item: any) => item.type === 'org')
      deptOptions.value = res.filter((item: any) => item.type === 'dept')
    }
  } catch (error) {
    console.error('获取组织部门列表失败', error)
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
  fetchOrgOptions()
})
</script>

<style scoped lang="scss">
.user-management {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 20px;
  
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 500;
    }
  }
}

.user-management :deep(.el-table) {
  width: 100%;
  max-width: 100%;
}

.user-filters {
  margin-bottom: 8px;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  margin-bottom: 12px;
  color: #606266;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.batch-role-form {
  margin-top: 18px;
}

@media (max-width: 760px) {
  .user-management {
    padding: 12px;
  }

  .batch-toolbar {
    flex-wrap: wrap;
  }

  .user-filters :deep(.el-form-item),
  .user-filters :deep(.el-input),
  .user-filters :deep(.el-select) {
    width: 100%;
  }
}
</style>
