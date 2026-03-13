<template>
  <div class="user-management">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增用户
      </el-button>
    </div>
    
    <!-- 用户表格 -->
    <el-table v-loading="loading" :data="userList" border stripe>
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
      
      <el-table-column prop="createTime" label="创建时间" width="160" />
      
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
                <el-radio label="0">启用</el-radio>
                <el-radio label="1">禁用</el-radio>
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
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getUserList, createUser, updateUser, deleteUser, updateUserStatus, resetPassword, getRoles } from '@/api/system/user'

const loading = ref(false)
const userList = ref<any[]>([])
const roleOptions = ref<any[]>([])
const orgOptions = ref<any[]>([])
const deptOptions = ref<any[]>([])

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const submitLoading = ref(false)

const formData = reactive({
  id: '',
  username: '',
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
  roleIds: [{ required: true, message: '请选择角色', trigger: 'change', type: 'array' }]
}

// 获取用户列表
const fetchUserList = async () => {
  loading.value = true
  try {
    userList.value = await getUserList() || []
  } finally {
    loading.value = false
  }
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
    const res = await fetch('/api/system/org/enabled').then(r => r.json())
    if (res.code === 200) {
      const list = res.data || []
      orgOptions.value = list.filter((item: any) => item.type === 'org')
      deptOptions.value = list.filter((item: any) => item.type === 'dept')
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
    const api = formData.id ? updateUser : createUser
    await api(formData.id, formData)
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
    await ElMessageBox.confirm(`确定删除用户 "${row.username}" 吗？`, '提示', {
      type: 'warning'
    })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchUserList()
  } catch {
    // 取消删除
  }
}

// 状态变更
const handleStatusChange = async (row: any) => {
  try {
    await updateUserStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch {
    row.status = row.status === '0' ? '1' : '0'
  }
}

// 重置密码
const handleResetPassword = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定重置用户 "${row.username}" 的密码吗？<br>重置后密码为：<b>123456</b>`, '提示', {
      type: 'warning',
      dangerouslyUseHTMLString: true
    })
    await resetPassword(row.id)
    ElMessage.success('密码重置成功')
  } catch {
    // 取消
  }
}

onMounted(() => {
  fetchUserList()
  fetchRoleOptions()
  fetchOrgOptions()
})
</script>

<style scoped lang="scss">
.user-management {
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
</style>
