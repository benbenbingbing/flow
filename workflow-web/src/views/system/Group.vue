<template>
  <div class="group-management">
    <div class="page-header">
      <h2>用户组管理</h2>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增用户组
      </el-button>
    </div>
    
    <!-- 组表格 -->
    <el-table v-loading="loading" :data="groupList" border stripe>
      <el-table-column type="index" label="#" width="60" align="center" />
      
      <el-table-column prop="groupName" label="组名称" min-width="150" />
      
      <el-table-column prop="groupCode" label="组编码" min-width="150" />
      
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      
      <el-table-column prop="sort" label="排序" width="80" align="center" />
      
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="'0'"
            :inactive-value="'1'"
            inline-prompt
            active-text="启"
            inactive-text="禁"
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
          <el-button type="primary" link size="small" @click="handleAssignUsers(row)">
            分配成员
          </el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 组编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="组名称" prop="groupName">
          <el-input v-model="formData.groupName" placeholder="请输入组名称" />
        </el-form-item>
        
        <el-form-item label="组编码" prop="groupCode">
          <el-input 
            v-model="formData.groupCode" 
            placeholder="请输入组编码，如：dept_manager"
            :disabled="!!formData.id"
          />
        </el-form-item>
        
        <el-form-item label="描述" prop="description">
          <el-input 
            v-model="formData.description" 
            type="textarea"
            :rows="2"
            placeholder="请输入描述"
          />
        </el-form-item>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排序" prop="sort">
              <el-input-number
                v-model="formData.sort"
                :min="0"
                :max="9999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="formData.status">
                <el-radio label="0">启用</el-radio>
                <el-radio label="1">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 分配成员对话框 -->
    <el-dialog
      v-model="userDialogVisible"
      title="分配组成员"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-transfer
        v-model="selectedUserIds"
        :data="userOptions"
        :titles="['可选用户', '已选用户']"
        :props="{ key: 'id', label: 'username' }"
        filterable
        :filter-method="filterUser"
        filter-placeholder="请输入用户名搜索"
      >
        <template #default="{ option }">
          <div class="user-option">
            <span class="username">{{ option.username }}</span>
            <span class="nickname" v-if="option.nickname">({{ option.nickname }})</span>
          </div>
        </template>
      </el-transfer>
      
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveUsers" :loading="userSubmitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getGroupList, createGroup, updateGroup, deleteGroup, updateGroupStatus, saveGroupUsers, getUsers } from '@/api/system/group'

const loading = ref(false)
const groupList = ref<any[]>([])
const userOptions = ref<any[]>([])

// 组对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const submitLoading = ref(false)

const formData = reactive({
  id: '',
  groupName: '',
  groupCode: '',
  description: '',
  sort: 0,
  status: '0'
})

const formRules = {
  groupName: [{ required: true, message: '请输入组名称', trigger: 'blur' }],
  groupCode: [{ required: true, message: '请输入组编码', trigger: 'blur' }]
}

// 用户分配对话框
const userDialogVisible = ref(false)
const userSubmitLoading = ref(false)
const selectedUserIds = ref<string[]>([])
const currentGroupId = ref('')

// 获取组列表
const fetchGroupList = async () => {
  loading.value = true
  try {
    groupList.value = await getGroupList() || []
  } finally {
    loading.value = false
  }
}

// 获取用户列表
const fetchUserOptions = async () => {
  try {
    userOptions.value = await getUsers() || []
  } catch (error) {
    console.error('获取用户列表失败', error)
  }
}

// 用户搜索过滤
const filterUser = (query: string, item: any) => {
  return item.username?.includes(query) || item.nickname?.includes(query)
}

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    id: '',
    groupName: '',
    groupCode: '',
    description: '',
    sort: 0,
    status: '0'
  })
}

// 新增组
const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新增用户组'
  dialogVisible.value = true
}

// 编辑组
const handleEdit = (row: any) => {
  resetForm()
  Object.assign(formData, {
    id: row.id,
    groupName: row.groupName,
    groupCode: row.groupCode,
    description: row.description,
    sort: row.sort,
    status: row.status
  })
  dialogTitle.value = '编辑用户组'
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const api = formData.id ? updateGroup : createGroup
    await api(formData.id, formData)
    ElMessage.success(formData.id ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchGroupList()
  } finally {
    submitLoading.value = false
  }
}

// 删除组
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除用户组 "${row.groupName}" 吗？`, '提示', {
      type: 'warning'
    })
    await deleteGroup(row.id)
    ElMessage.success('删除成功')
    fetchGroupList()
  } catch {
    // 取消删除
  }
}

// 状态变更
const handleStatusChange = async (row: any) => {
  try {
    await updateGroupStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch {
    row.status = row.status === '0' ? '1' : '0'
  }
}

// 分配成员
const handleAssignUsers = async (row: any) => {
  currentGroupId.value = row.id
  selectedUserIds.value = row.userIds || []
  userDialogVisible.value = true
}

// 保存组成员
const handleSaveUsers = async () => {
  if (!currentGroupId.value) return
  
  userSubmitLoading.value = true
  try {
    await saveGroupUsers(currentGroupId.value, selectedUserIds.value)
    ElMessage.success('成员分配成功')
    userDialogVisible.value = false
    fetchGroupList()
  } finally {
    userSubmitLoading.value = false
  }
}

onMounted(() => {
  fetchGroupList()
  fetchUserOptions()
})
</script>

<style scoped lang="scss">
.group-management {
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

.user-option {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .username {
    font-weight: 500;
  }
  
  .nickname {
    color: #909399;
    font-size: 12px;
  }
}

:deep(.el-transfer) {
  display: flex;
  justify-content: center;
  align-items: center;
  
  .el-transfer-panel {
    width: 250px;
  }
}
</style>
