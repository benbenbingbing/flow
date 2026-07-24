<template>
  <div class="role-management">
    <div class="page-header">
      <h2>角色管理</h2>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增角色
      </el-button>
    </div>
    
    <!-- 角色表格 -->
    <el-table v-loading="loading" :data="roleList" border stripe>
      <el-table-column type="index" label="#" width="60" align="center" />
      
      <el-table-column prop="roleName" label="角色名称" min-width="150" />
      
      <el-table-column prop="roleCode" label="角色编码" min-width="150" />
      
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
            :disabled="row.roleCode === 'super_admin'"
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
          <el-button type="primary" link size="small" @click="handleAssignMenu(row)">
            分配权限
          </el-button>
          <el-button 
            type="danger" 
            link 
            size="small"
            :disabled="row.roleCode === 'super_admin'"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 角色编辑对话框 -->
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
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        
        <el-form-item label="角色编码" prop="roleCode">
          <el-input 
            v-model="formData.roleCode" 
            placeholder="请输入角色编码，如：admin"
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
    
    <!-- 分配权限对话框 -->
    <el-dialog
      v-model="menuDialogVisible"
      :title="currentRoleName ? `分配权限 - ${currentRoleName}` : '分配权限'"
      class="permission-transfer-dialog"
      width="960px"
      top="5vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-loading="menuLoading" class="permission-transfer">
        <el-transfer
          v-model="selectedMenuIds"
          :data="permissionOptions"
          :titles="['未分配', '已分配']"
          :props="{ key: 'id', label: 'menuName' }"
          :format="{ noChecked: '${total} 项', hasChecked: '${checked}/${total} 项' }"
          filterable
          filter-placeholder="搜索权限"
          target-order="original"
          :filter-method="filterPermission"
          @change="handleMenuTransferChange"
        >
          <template #default="{ option }">
            <div class="permission-option" :title="option.fullPath">
              <span class="permission-option__path">{{ option.fullPath }}</span>
              <el-tag
                class="permission-option__type"
                size="small"
                effect="plain"
                :type="getMenuTypeTag(option.menuType)"
              >
                {{ option.menuTypeLabel }}
              </el-tag>
            </div>
          </template>
        </el-transfer>
      </div>
      
      <template #footer>
        <div class="permission-dialog-footer">
          <span class="permission-dialog-footer__count">
            已分配 {{ selectedMenuIds.length }} / {{ permissionOptions.length }} 项
          </span>
          <div class="permission-dialog-footer__actions">
            <el-button @click="menuDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSaveMenus" :loading="menuSubmitLoading">确定</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getRoleList, createRole, updateRole, deleteRole, updateRoleStatus, getMenuTree, saveRoleMenus } from '@/api/system/role'
import {
  applyPermissionTransferChange,
  flattenPermissionMenuTree,
  sanitizePermissionKeys
} from '@/shared/role-permission-transfer'

const loading = ref(false)
const roleList = ref<any[]>([])
const menuTree = ref<any[]>([])
const menuLoading = ref(false)
const permissionOptions = computed(() => flattenPermissionMenuTree(menuTree.value))

// 角色对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const submitLoading = ref(false)

const formData = reactive({
  id: '',
  roleName: '',
  roleCode: '',
  description: '',
  sort: 0,
  status: '0'
})

const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

// 权限对话框
const menuDialogVisible = ref(false)
const menuSubmitLoading = ref(false)
const selectedMenuIds = ref<string[]>([])
const currentRoleId = ref('')
const currentRoleName = ref('')

// 获取角色列表
const fetchRoleList = async () => {
  loading.value = true
  try {
    roleList.value = await getRoleList() || []
  } finally {
    loading.value = false
  }
}

// 获取菜单树
const fetchMenuTree = async () => {
  menuLoading.value = true
  try {
    menuTree.value = await getMenuTree() || []
  } catch (error) {
    console.error('获取菜单树失败', error)
  } finally {
    menuLoading.value = false
  }
}

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    id: '',
    roleName: '',
    roleCode: '',
    description: '',
    sort: 0,
    status: '0'
  })
}

// 新增角色
const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新增角色'
  dialogVisible.value = true
}

// 编辑角色
const handleEdit = (row: any) => {
  resetForm()
  Object.assign(formData, {
    id: row.id,
    roleName: row.roleName,
    roleCode: row.roleCode,
    description: row.description,
    sort: row.sort,
    status: row.status
  })
  dialogTitle.value = '编辑角色'
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const api = formData.id ? updateRole : createRole
    await api(formData.id, formData)
    ElMessage.success(formData.id ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchRoleList()
  } finally {
    submitLoading.value = false
  }
}

// 删除角色
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除角色 "${row.roleName}" 吗？`, '提示', {
      type: 'warning'
    })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchRoleList()
  } catch {
    // 取消删除
  }
}

// 状态变更
const handleStatusChange = async (row: any) => {
  try {
    await updateRoleStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch {
    row.status = row.status === '0' ? '1' : '0'
  }
}

// 分配权限
const handleAssignMenu = async (row: any) => {
  currentRoleId.value = row.id
  currentRoleName.value = row.roleName || ''

  if (!menuTree.value.length) {
    await fetchMenuTree()
  }

  selectedMenuIds.value = sanitizePermissionKeys(row.menuIds || [], permissionOptions.value)
  menuDialogVisible.value = true
}

const filterPermission = (query: string, option: any) => {
  return !query || option.searchText.includes(query.trim().toLowerCase())
}

const getMenuTypeTag = (menuType: string) => {
  if (menuType === 'M') return 'warning'
  if (menuType === 'C') return 'success'
  return 'info'
}

const handleMenuTransferChange = (keys: string[], direction: 'left' | 'right', movedKeys: string[]) => {
  selectedMenuIds.value = applyPermissionTransferChange(
    keys,
    direction,
    movedKeys,
    permissionOptions.value
  )
}

// 保存权限
const handleSaveMenus = async () => {
  if (!currentRoleId.value) return
  
  menuSubmitLoading.value = true
  try {
    await saveRoleMenus(currentRoleId.value, selectedMenuIds.value)
    ElMessage.success('权限分配成功')
    menuDialogVisible.value = false
    fetchRoleList()
  } finally {
    menuSubmitLoading.value = false
  }
}

onMounted(() => {
  fetchRoleList()
  fetchMenuTree()
})
</script>

<style scoped lang="scss">
.role-management {
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

.permission-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.permission-option__path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-option__type {
  flex: 0 0 auto;
  margin-left: auto;
}

.permission-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.permission-dialog-footer__count {
  color: #909399;
  font-size: 13px;
}

.permission-dialog-footer__actions {
  display: flex;
  gap: 8px;
}

:global(.permission-transfer-dialog) {
  display: flex;
  flex-direction: column;
  height: min(620px, 90vh);
  max-width: calc(100vw - 32px);
  overflow: hidden;
}

:global(.permission-transfer-dialog .el-dialog__header) {
  flex: 0 0 auto;
  margin-right: 0;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #ebeef5;
}

:global(.permission-transfer-dialog .el-dialog__body) {
  flex: 1 1 auto;
  box-sizing: border-box;
  min-height: 0;
  padding: 18px 24px;
  overflow: hidden;
}

:global(.permission-transfer-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  padding: 14px 24px 16px;
  border-top: 1px solid #ebeef5;
}

.permission-transfer {
  height: 100%;
}

.permission-transfer :deep(.el-transfer) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 72px minmax(0, 1fr);
  align-items: stretch;
  height: 100%;
}

.permission-transfer :deep(.el-transfer-panel) {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

.permission-transfer :deep(.el-transfer-panel__header) {
  flex: 0 0 auto;
}

.permission-transfer :deep(.el-transfer-panel__body) {
  flex: 1 1 auto;
  height: auto;
  min-height: 0;
}

.permission-transfer :deep(.el-transfer-panel__item) {
  margin-right: 0;
  padding-right: 12px;
}

.permission-transfer :deep(.el-transfer__buttons) {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
}

.permission-transfer :deep(.el-transfer__button:nth-child(2)) {
  margin: 10px 0 0;
}

@media (max-width: 760px) {
  .permission-transfer :deep(.el-transfer) {
    grid-template-columns: minmax(0, 1fr) 52px minmax(0, 1fr);
  }

  .permission-transfer :deep(.el-transfer__buttons) {
    padding: 0 8px;
  }

  .permission-option__type {
    display: none;
  }
}
</style>
