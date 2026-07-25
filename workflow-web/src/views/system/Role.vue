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
      
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button type="primary" link size="small" @click="handleAssignMenu(row)">
            分配权限
          </el-button>
          <el-button type="primary" link size="small" @click="handleRoleUsers(row)">
            用户
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
      width="66.6667vw"
      top="5vh"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetPermissionTransferState"
    >
      <div v-loading="menuLoading" class="permission-transfer">
        <div class="permission-tree-transfer">
          <section class="permission-tree-panel">
            <header class="permission-tree-panel__header">
              <div class="permission-tree-panel__title">
                <span>未分配</span>
                <span class="permission-tree-panel__count">{{ availablePermissionCount }}</span>
              </div>
              <el-input
                v-model="availablePermissionQuery"
                :prefix-icon="Search"
                clearable
                placeholder="搜索未分配权限"
              />
            </header>

            <div class="permission-tree-panel__body">
              <el-tree
                ref="availableTreeRef"
                :data="availablePermissionTree"
                :props="permissionTreeProps"
                node-key="id"
                show-checkbox
                highlight-current
                default-expand-all
                :expand-on-click-node="false"
                :filter-node-method="filterPermissionTreeNode"
                empty-text="暂无未分配权限"
                @check="syncAvailableCheckedKeys"
              >
                <template #default="{ data }">
                  <div
                    class="permission-tree-node"
                    :class="{ 'is-context': data.contextOnly }"
                    :title="data.fullPath"
                  >
                    <span class="permission-tree-node__label">{{ data.menuName }}</span>
                    <el-tag
                      class="permission-tree-node__type"
                      size="small"
                      effect="plain"
                      :type="getMenuTypeTag(data.menuType)"
                    >
                      {{ data.menuTypeLabel }}
                    </el-tag>
                  </div>
                </template>
              </el-tree>
            </div>

            <footer class="permission-tree-panel__footer">
              {{ availableCheckedIds.length ? `已勾选 ${availableCheckedIds.length} 项` : '勾选权限后移入右侧' }}
            </footer>
          </section>

          <div class="permission-tree-transfer__actions">
            <el-tooltip content="分配选中权限" placement="right">
              <span>
                <el-button
                  type="primary"
                  circle
                  size="large"
                  :disabled="availableCheckedIds.length === 0"
                  aria-label="分配选中权限"
                  @click="movePermissionsToAssigned"
                >
                  <el-icon><ArrowRightBold /></el-icon>
                </el-button>
              </span>
            </el-tooltip>
            <el-tooltip content="移除选中权限" placement="right">
              <span>
                <el-button
                  circle
                  size="large"
                  :disabled="assignedCheckedIds.length === 0"
                  aria-label="移除选中权限"
                  @click="movePermissionsToAvailable"
                >
                  <el-icon><ArrowLeftBold /></el-icon>
                </el-button>
              </span>
            </el-tooltip>
          </div>

          <section class="permission-tree-panel">
            <header class="permission-tree-panel__header">
              <div class="permission-tree-panel__title">
                <span>已分配</span>
                <span class="permission-tree-panel__count is-assigned">{{ selectedMenuIds.length }}</span>
              </div>
              <el-input
                v-model="assignedPermissionQuery"
                :prefix-icon="Search"
                clearable
                placeholder="搜索已分配权限"
              />
            </header>

            <div class="permission-tree-panel__body">
              <el-tree
                ref="assignedTreeRef"
                :data="assignedPermissionTree"
                :props="permissionTreeProps"
                node-key="id"
                show-checkbox
                highlight-current
                default-expand-all
                :expand-on-click-node="false"
                :filter-node-method="filterPermissionTreeNode"
                empty-text="暂无已分配权限"
                @check="syncAssignedCheckedKeys"
              >
                <template #default="{ data }">
                  <div
                    class="permission-tree-node"
                    :class="{ 'is-context': data.contextOnly }"
                    :title="data.fullPath"
                  >
                    <span class="permission-tree-node__label">{{ data.menuName }}</span>
                    <el-tag
                      class="permission-tree-node__type"
                      size="small"
                      effect="plain"
                      :type="getMenuTypeTag(data.menuType)"
                    >
                      {{ data.menuTypeLabel }}
                    </el-tag>
                  </div>
                </template>
              </el-tree>
            </div>

            <footer class="permission-tree-panel__footer">
              {{ assignedCheckedIds.length ? `已勾选 ${assignedCheckedIds.length} 项` : '勾选权限后移回左侧' }}
            </footer>
          </section>
        </div>
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

    <!-- 角色用户对话框 -->
    <el-dialog
      v-model="roleUserDialogVisible"
      :title="currentRoleName ? `角色用户 - ${currentRoleName}` : '角色用户'"
      class="role-user-dialog"
      width="960px"
      top="7vh"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetRoleUserState"
    >
      <div class="role-user-content">
        <div class="role-user-toolbar">
          <el-input
            v-model="roleUserSearch.keyword"
            :prefix-icon="Search"
            clearable
            placeholder="用户名、昵称、邮箱或手机号"
            class="role-user-toolbar__search"
            @keyup.enter="handleRoleUserSearch"
            @clear="handleRoleUserSearch"
          />
          <el-button type="primary" @click="handleRoleUserSearch">查询</el-button>
          <el-button @click="handleRoleUserReset">重置</el-button>
          <span class="role-user-toolbar__spacer" />
          <el-button type="primary" @click="handleAddRoleUser">
            <el-icon><Plus /></el-icon>
            新增用户
          </el-button>
        </div>

        <el-table
          v-loading="roleUserLoading"
          :data="roleUserList"
          border
          stripe
          height="420"
          empty-text="当前角色暂无用户"
        >
          <el-table-column type="index" label="#" width="58" align="center" />
          <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column label="组织 / 部门" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              {{ [row.orgName, row.deptName].filter(Boolean).join(' / ') || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="82" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small">
                {{ row.status === '0' ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="165" />
        </el-table>

        <el-pagination
          v-model:current-page="roleUserPage.pageNum"
          v-model:page-size="roleUserPage.pageSize"
          :total="roleUserPage.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          class="role-user-pagination"
          @size-change="handleRoleUserPageSizeChange"
          @current-change="fetchRoleUsers"
        />
      </div>

      <template #footer>
        <el-button @click="roleUserDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 从角色中新增用户 -->
    <el-dialog
      v-model="newRoleUserDialogVisible"
      :title="currentRoleName ? `新增用户 - ${currentRoleName}` : '新增用户'"
      width="620px"
      :close-on-click-modal="false"
      append-to-body
      destroy-on-close
    >
      <el-form
        ref="roleUserFormRef"
        :model="roleUserForm"
        :rules="roleUserFormRules"
        label-width="92px"
      >
        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="roleUserForm.username" placeholder="请输入用户名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="roleUserForm.nickname" placeholder="请输入昵称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="roleUserForm.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="roleUserForm.phone" placeholder="请输入手机号" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="组织">
              <el-tree-select
                v-model="roleUserForm.orgId"
                :data="roleUserOrgOptions"
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
                v-model="roleUserForm.deptId"
                :data="roleUserDeptOptions"
                :props="{ label: 'orgName', value: 'id' }"
                placeholder="请选择部门"
                clearable
                check-strictly
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="roleUserForm.status">
                <el-radio label="0">启用</el-radio>
                <el-radio label="1">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属角色">
              <el-tag type="primary" effect="plain">{{ currentRoleName }}</el-tag>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="newRoleUserDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="roleUserSubmitLoading"
          @click="handleCreateRoleUser"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeftBold, ArrowRightBold, Plus, Search } from '@element-plus/icons-vue'
import {
  getRoleList,
  createRole,
  updateRole,
  deleteRole,
  updateRoleStatus,
  getMenuTree,
  getRoleUsers,
  saveRoleMenus
} from '@/api/system/role'
import { createUser } from '@/api/system/user'
import request from '@/utils/request'
import {
  applyPermissionTransferChange,
  buildPermissionTreeView,
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
const availableTreeRef = ref<any>()
const assignedTreeRef = ref<any>()
const availablePermissionQuery = ref('')
const assignedPermissionQuery = ref('')
const availableCheckedIds = ref<string[]>([])
const assignedCheckedIds = ref<string[]>([])
const selectedMenuIdSet = computed(() => new Set(selectedMenuIds.value))
const availablePermissionCount = computed(() => permissionOptions.value.length - selectedMenuIds.value.length)
const availablePermissionTree = computed(() => buildPermissionTreeView(
  menuTree.value,
  selectedMenuIds.value,
  'available'
))
const assignedPermissionTree = computed(() => buildPermissionTreeView(
  menuTree.value,
  selectedMenuIds.value,
  'assigned'
))
const permissionTreeProps = {
  children: 'children',
  label: 'menuName',
  disabled: 'transferDisabled'
}

// 角色用户对话框
const roleUserDialogVisible = ref(false)
const roleUserLoading = ref(false)
const roleUserList = ref<any[]>([])
const roleUserSearch = reactive({
  keyword: ''
})
const roleUserPage = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 从角色中新增用户
const newRoleUserDialogVisible = ref(false)
const roleUserFormRef = ref()
const roleUserSubmitLoading = ref(false)
const roleUserOrgOptions = ref<any[]>([])
const roleUserDeptOptions = ref<any[]>([])
const roleUserForm = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  status: '0',
  roleIds: [] as string[],
  orgId: '',
  deptId: ''
})
const roleUserFormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
}

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

// 查询角色用户
const fetchRoleUsers = async () => {
  if (!currentRoleId.value) return

  roleUserLoading.value = true
  try {
    const res = await getRoleUsers(currentRoleId.value, {
      pageNum: roleUserPage.pageNum,
      pageSize: roleUserPage.pageSize,
      keyword: roleUserSearch.keyword.trim() || undefined
    })
    roleUserList.value = res?.records || []
    roleUserPage.total = Number(res?.total || 0)
    roleUserPage.pageNum = Number(res?.pageNum || roleUserPage.pageNum)
    roleUserPage.pageSize = Number(res?.pageSize || roleUserPage.pageSize)
  } finally {
    roleUserLoading.value = false
  }
}

const handleRoleUsers = (row: any) => {
  currentRoleId.value = row.id
  currentRoleName.value = row.roleName || ''
  roleUserSearch.keyword = ''
  roleUserPage.pageNum = 1
  roleUserPage.total = 0
  roleUserDialogVisible.value = true
  fetchRoleUsers()
}

const handleRoleUserSearch = () => {
  roleUserPage.pageNum = 1
  fetchRoleUsers()
}

const handleRoleUserReset = () => {
  roleUserSearch.keyword = ''
  roleUserPage.pageNum = 1
  fetchRoleUsers()
}

const handleRoleUserPageSizeChange = () => {
  roleUserPage.pageNum = 1
  fetchRoleUsers()
}

const resetRoleUserState = () => {
  roleUserSearch.keyword = ''
  roleUserList.value = []
  roleUserPage.pageNum = 1
  roleUserPage.total = 0
}

const fetchRoleUserOrgOptions = async () => {
  if (roleUserOrgOptions.value.length || roleUserDeptOptions.value.length) return

  try {
    const res = await request.get('/system/org/enabled')
    const options = Array.isArray(res) ? res : []
    roleUserOrgOptions.value = options.filter((item: any) => item.type === 'org')
    roleUserDeptOptions.value = options.filter((item: any) => item.type === 'dept')
  } catch (error) {
    console.error('获取组织部门列表失败', error)
  }
}

const resetRoleUserForm = () => {
  Object.assign(roleUserForm, {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    status: '0',
    roleIds: currentRoleId.value ? [currentRoleId.value] : [],
    orgId: '',
    deptId: ''
  })
}

const handleAddRoleUser = async () => {
  resetRoleUserForm()
  await fetchRoleUserOrgOptions()
  newRoleUserDialogVisible.value = true
  await nextTick()
  roleUserFormRef.value?.clearValidate()
}

const handleCreateRoleUser = async () => {
  await roleUserFormRef.value?.validate()
  if (!currentRoleId.value) return

  roleUserSubmitLoading.value = true
  try {
    await createUser({
      ...roleUserForm,
      roleIds: [currentRoleId.value]
    })
    ElMessage.success('用户创建成功，已分配当前角色')
    newRoleUserDialogVisible.value = false
    roleUserSearch.keyword = ''
    roleUserPage.pageNum = 1
    fetchRoleUsers()
  } finally {
    roleUserSubmitLoading.value = false
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
  await nextTick()
  refreshPermissionTrees()
}

const filterPermissionTreeNode = (query: string, data: any) => {
  return !query || data.searchText.includes(query.trim().toLowerCase())
}

const getMenuTypeTag = (menuType: string) => {
  if (menuType === 'M') return 'warning'
  if (menuType === 'C') return 'success'
  return 'info'
}

const syncAvailableCheckedKeys = () => {
  availableCheckedIds.value = (availableTreeRef.value?.getCheckedKeys(false) || [])
    .map(String)
    .filter(id => !selectedMenuIdSet.value.has(id))
}

const syncAssignedCheckedKeys = () => {
  assignedCheckedIds.value = (assignedTreeRef.value?.getCheckedKeys(false) || [])
    .map(String)
    .filter(id => selectedMenuIdSet.value.has(id))
}

const refreshPermissionTrees = async () => {
  await nextTick()
  availableTreeRef.value?.setCheckedKeys([])
  assignedTreeRef.value?.setCheckedKeys([])
  availableTreeRef.value?.filter(availablePermissionQuery.value)
  assignedTreeRef.value?.filter(assignedPermissionQuery.value)
  availableCheckedIds.value = []
  assignedCheckedIds.value = []
}

const movePermissions = async (direction: 'left' | 'right', movedKeys: string[]) => {
  selectedMenuIds.value = applyPermissionTransferChange(
    selectedMenuIds.value,
    direction,
    movedKeys,
    permissionOptions.value
  )
  await refreshPermissionTrees()
}

const movePermissionsToAssigned = () => {
  if (!availableCheckedIds.value.length) return
  movePermissions('right', availableCheckedIds.value)
}

const movePermissionsToAvailable = () => {
  if (!assignedCheckedIds.value.length) return
  movePermissions('left', assignedCheckedIds.value)
}

const resetPermissionTransferState = () => {
  availablePermissionQuery.value = ''
  assignedPermissionQuery.value = ''
  availableCheckedIds.value = []
  assignedCheckedIds.value = []
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

watch(availablePermissionQuery, async query => {
  await nextTick()
  availableTreeRef.value?.filter(query)
})

watch(assignedPermissionQuery, async query => {
  await nextTick()
  assignedTreeRef.value?.filter(query)
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

.permission-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: min(100%, 1500px);
  margin: 0 auto;
}

.permission-dialog-footer__count {
  color: #909399;
  font-size: 13px;
}

.permission-dialog-footer__actions {
  display: flex;
  gap: 8px;
}

.role-user-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.role-user-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-user-toolbar__search {
  width: 320px;
}

.role-user-toolbar__spacer {
  flex: 1 1 auto;
}

.role-user-pagination {
  justify-content: flex-end;
}

:global(.role-user-dialog) {
  max-width: calc(100vw - 32px);
}

:global(.role-user-dialog .el-dialog__body) {
  padding: 18px 24px 20px;
}

:global(.permission-transfer-dialog) {
  display: flex;
  flex-direction: column;
  height: 90vh;
  max-width: calc(100vw - 32px);
  margin-bottom: 0;
  overflow: hidden;
}

:global(.permission-transfer-dialog .el-dialog__header) {
  flex: 0 0 auto;
  margin-right: 0;
  padding: 18px 28px 16px;
  border-bottom: 1px solid #ebeef5;
}

:global(.permission-transfer-dialog .el-dialog__body) {
  flex: 1 1 auto;
  box-sizing: border-box;
  min-height: 0;
  padding: 18px 28px;
  overflow: hidden;
}

:global(.permission-transfer-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  padding: 14px 28px 16px;
  border-top: 1px solid #ebeef5;
}

.permission-transfer {
  height: 100%;
  width: min(100%, 1500px);
  margin: 0 auto;
}

.permission-tree-transfer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 80px minmax(0, 1fr);
  align-items: stretch;
  height: 100%;
}

.permission-tree-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
}

.permission-tree-panel__header {
  flex: 0 0 auto;
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #f8f9fb;
}

.permission-tree-panel__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.permission-tree-panel__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: #e9ecf2;
  color: #606266;
  font-size: 12px;
  font-weight: 500;
}

.permission-tree-panel__count.is-assigned {
  background: #ecf5ff;
  color: #409eff;
}

.permission-tree-panel__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 8px 10px 12px;
}

.permission-tree-panel__body :deep(.el-tree) {
  min-width: max-content;
}

.permission-tree-panel__body :deep(.el-tree-node__content) {
  height: 36px;
  min-width: 300px;
  padding-right: 8px;
  border-radius: 4px;
}

.permission-tree-panel__body :deep(.el-tree-node__content:hover) {
  background: #f5f7fa;
}

.permission-tree-panel__footer {
  flex: 0 0 auto;
  min-height: 42px;
  padding: 11px 16px;
  border-top: 1px solid #ebeef5;
  color: #909399;
  font-size: 12px;
}

.permission-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  width: 100%;
}

.permission-tree-node.is-context {
  opacity: 0.62;
}

.permission-tree-node__label {
  min-width: 0;
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-tree-node__type {
  flex: 0 0 auto;
  margin-left: auto;
}

.permission-tree-transfer__actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 0 18px;
}

.permission-tree-transfer__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 760px) {
  .role-user-toolbar {
    flex-wrap: wrap;
  }

  .role-user-toolbar__search {
    width: 100%;
  }

  .role-user-toolbar__spacer {
    display: none;
  }

  :global(.permission-transfer-dialog .el-dialog__body) {
    padding-right: 12px;
    padding-left: 12px;
  }

  .permission-tree-transfer {
    grid-template-columns: minmax(0, 1fr) 56px minmax(0, 1fr);
  }

  .permission-tree-transfer__actions {
    padding: 0 8px;
  }

  .permission-tree-node__type {
    display: none;
  }
}
</style>
