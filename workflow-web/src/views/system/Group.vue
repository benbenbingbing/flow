<template>
  <div class="group-management">
    <div class="page-header">
      <h2>流程用户组</h2>
      <el-button v-if="canManage" type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增用户组
      </el-button>
    </div>
    
    <PageState
      v-if="loadError"
      type="error"
      title="用户组加载失败"
      :description="loadError"
      retryable
      @retry="fetchGroupList"
    />
    <!-- 组表格 -->
    <el-table v-else v-loading="loading" :data="groupList" border stripe>
      <el-table-column type="index" label="#" width="60" align="center" />
      
      <el-table-column prop="groupName" label="组名称" min-width="150" />
      
      <el-table-column prop="groupCode" label="组编码" min-width="150" />
      
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />

      <el-table-column label="成员数" width="90" align="center">
        <template #default="{ row }">{{ row.userIds?.length || 0 }}</template>
      </el-table-column>
      
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
            :loading="isStatusPending(row.id)"
            :disabled="!canManage || isStatusPending(row.id)"
            @change="handleStatusChange(row)"
          />
        </template>
      </el-table-column>
      
      <el-table-column prop="createTime" label="创建时间" width="160" />
      
      <el-table-column v-if="canManage" label="操作" width="240" fixed="right">
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
          <el-input
            v-model.trim="formData.groupName"
            maxlength="50"
            show-word-limit
            placeholder="请输入组名称"
          />
        </el-form-item>
        
        <el-form-item label="组编码" prop="groupCode">
          <el-input 
            v-model.trim="formData.groupCode"
            maxlength="50"
            show-word-limit
            placeholder="请输入组编码，如：dept_manager"
            :disabled="!!formData.id"
          />
          <div class="field-help">创建后不可修改，可用于审批规则和外部集成引用。</div>
        </el-form-item>
        
        <el-form-item label="描述" prop="description">
          <el-input 
            v-model.trim="formData.description"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
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
                <el-radio value="0">启用</el-radio>
                <el-radio value="1">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
          {{ formData.id ? '保存用户组' : '创建用户组' }}
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 分配成员对话框 -->
    <el-dialog
      v-model="userDialogVisible"
      title="分配组成员"
      width="600px"
      :close-on-click-modal="false"
    >
      <UserSelector
        v-model="selectedUserIds"
        multiple
        value-key="id"
        placeholder="请选择组成员"
        title="选择组成员"
      />
      
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveUsers" :loading="userSubmitLoading">
          保存成员（{{ selectedUserIds.length }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getGroupList, createGroup, updateGroup, deleteGroup, updateGroupStatus, saveGroupUsers } from '@/api/system/group'
import PageState from '@/components/PageState.vue'
import UserSelector from '@/components/UserSelector.vue'
import { useUserStore } from '@/stores/user'
import {
  normalizeGroupMemberIds,
  prepareGroupMemberChange,
  runGroupStatusChange,
  submitGroupForm
} from '@/shared/group-management'

const userStore = useUserStore()
const canManage = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('system:user:manage'))

const loading = ref(false)
const loadError = ref('')
const groupList = ref<any[]>([])

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
  groupName: [
    { required: true, message: '请输入组名称', trigger: 'blur' },
    { max: 50, message: '组名称不能超过50个字符', trigger: 'blur' }
  ],
  groupCode: [
    { required: true, message: '请输入组编码', trigger: 'blur' },
    { max: 50, message: '组编码不能超过50个字符', trigger: 'blur' }
  ],
  description: [{ max: 200, message: '描述不能超过200个字符', trigger: 'blur' }]
}

// 用户分配对话框
const userDialogVisible = ref(false)
const userSubmitLoading = ref(false)
const selectedUserIds = ref<string[]>([])
const currentGroupId = ref('')
// 每个组独立加锁，避免一个状态请求阻塞其他行，也避免同一行重复提交。
const statusPendingIds = reactive(new Set<string>())
const isStatusPending = (id: unknown) => statusPendingIds.has(String(id ?? '').trim())

// 获取组列表
const fetchGroupList = async () => {
  loading.value = true
  loadError.value = ''
  try {
    groupList.value = await getGroupList() || []
  } catch (error: any) {
    loadError.value = error?.message || '无法读取用户组，请检查权限或稍后重试。'
  } finally {
    loading.value = false
  }
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
  if (!canManage.value) return
  resetForm()
  dialogTitle.value = '新增用户组'
  dialogVisible.value = true
}

// 编辑组
const handleEdit = (row: any) => {
  if (!canManage.value) return
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
  if (!canManage.value) return
  // Element Plus 校验失败会拒绝 Promise；在事件入口消费它，避免产生未处理异常。
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  const isUpdate = Boolean(formData.id)
  try {
    // 新增和更新接口签名不同，统一由已测试的提交函数构造白名单请求体并明确分派。
    await submitGroupForm(formData, { createGroup, updateGroup })
    ElMessage.success(isUpdate ? '更新成功' : '创建成功')
    dialogVisible.value = false
    await fetchGroupList()
  } finally {
    submitLoading.value = false
  }
}

// 删除组
const handleDelete = async (row: any) => {
  if (!canManage.value) return
  try {
    const confirmation = await ElMessageBox.prompt(
      `删除后，${row.userIds?.length || 0} 名成员将失去通过该组获得的流程候选资格。请输入组名称「${row.groupName}」确认。`,
      '删除用户组',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        inputPlaceholder: row.groupName,
        inputValidator: value => value === row.groupName || '组名称不匹配'
      }
    )
    if (confirmation.value !== row.groupName) return
    await deleteGroup(row.id)
    ElMessage.success('删除成功')
    await fetchGroupList()
  } catch {
    // 取消删除
  }
}

// 状态变更
const handleStatusChange = async (row: any) => {
  if (!canManage.value) return
  const memberCount = normalizeGroupMemberIds(row.userIds).length
  const action = row.status === '0' ? '启用' : '禁用'
  const result = await runGroupStatusChange({
    row,
    pendingIds: statusPendingIds,
    confirmChange: () => ElMessageBox.confirm(
      `${action}用户组「${row.groupName}」将影响 ${memberCount} 名成员后续通过该组参与流程审批。历史任务不会自动改派。`,
      `${action}用户组`,
      {
        type: row.status === '0' ? 'info' : 'warning',
        confirmButtonText: `确认${action}`,
        cancelButtonText: '取消'
      }
    ),
    updateStatus: updateGroupStatus
  })
  if (result.updated) {
    ElMessage.success(`用户组已${action}`)
  }
}

// 分配成员
const handleAssignUsers = (row: any) => {
  if (!canManage.value) return
  currentGroupId.value = String(row.id ?? '').trim()
  if (!currentGroupId.value) return
  selectedUserIds.value = normalizeGroupMemberIds(row.userIds)
  userDialogVisible.value = true
}

// 保存组成员
const handleSaveUsers = async () => {
  if (!canManage.value || !currentGroupId.value) return

  const group = groupList.value.find(
    item => String(item.id ?? '').trim() === currentGroupId.value
  )
  const change = await prepareGroupMemberChange({
    currentUserIds: group?.userIds,
    selectedUserIds: selectedUserIds.value,
    confirmChange: ({ beforeCount, afterCount, addedCount, removedCount }) => ElMessageBox.confirm(
      `新增 ${addedCount}、移除 ${removedCount}（总数 ${beforeCount}→${afterCount}）。变更会影响后续按用户组选人的流程节点。`,
      '确认成员变更',
      { type: 'warning', confirmButtonText: '保存成员' }
    )
  })
  selectedUserIds.value = change.userIds
  if (!change.shouldSave) {
    if (change.reason === 'unchanged') {
      ElMessage.info('成员未发生变化')
      userDialogVisible.value = false
    }
    return
  }

  userSubmitLoading.value = true
  try {
    await saveGroupUsers(currentGroupId.value, change.userIds)
    ElMessage.success('成员分配成功')
    userDialogVisible.value = false
    await fetchGroupList()
  } catch {
    // 请求层已统一提示错误；保留弹窗和当前选择，便于用户修正后重试。
  } finally {
    userSubmitLoading.value = false
  }
}

onMounted(() => {
  fetchGroupList()
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

.field-help {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 760px) {
  .group-management {
    padding: 12px;

    .page-header {
      align-items: flex-start;
      flex-direction: column;
    }
  }
}
</style>
