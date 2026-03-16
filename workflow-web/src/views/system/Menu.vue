<template>
  <div class="menu-management">
    <div class="page-header">
      <h2>菜单管理</h2>
      <el-button type="primary" @click="handleAddTopLevel">
        <el-icon><Plus /></el-icon>
        新增顶级菜单
      </el-button>
    </div>
    
    <!-- 菜单表格 -->
    <el-table
      v-loading="loading"
      :data="flattenMenuTree"
      row-key="id"
      border
      stripe
    >
      <el-table-column prop="menuName" label="菜单名称" min-width="220">
        <template #default="{ row, $index }">
          <div class="menu-name-cell" :style="{ paddingLeft: getPaddingLeft(row, $index) + 'px' }">
            <!-- 展开/收起图标 -->
            <span 
              v-if="row.children && row.children.length > 0" 
              class="expand-icon"
              @click.stop="toggleExpand(row)"
            >
              <el-icon><ArrowRight v-if="!isExpanded(row)" /><ArrowDown v-else /></el-icon>
            </span>
            <span v-else class="expand-placeholder"></span>
            <el-icon v-if="row.icon" class="menu-icon">
              <component :is="getIconComponent(row.icon)" />
            </el-icon>
            <span>{{ row.menuName }}</span>
          </div>
        </template>
      </el-table-column>
      
      <el-table-column prop="menuType" label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.menuType === 'M'" type="warning">目录</el-tag>
          <el-tag v-else-if="row.menuType === 'C'" type="success">菜单</el-tag>
          <el-tag v-else-if="row.menuType === 'F'" type="info">按钮</el-tag>
        </template>
      </el-table-column>
      
      <el-table-column prop="perm" label="权限标识" min-width="160" show-overflow-tooltip />
      
      <el-table-column prop="path" label="路由路径" min-width="140" show-overflow-tooltip />
      
      <el-table-column prop="component" label="组件路径" min-width="160" show-overflow-tooltip />
      
      <el-table-column prop="sort" label="排序" width="70" align="center">
        <template #default="{ row }">
          <el-input-number
            v-model="row.sort"
            :min="0"
            :max="9999"
            size="small"
            controls-position="right"
            style="width: 60px"
            @change="handleSortChange(row)"
          />
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
            @change="handleStatusChange(row)"
          />
        </template>
      </el-table-column>
      
      <el-table-column prop="visible" label="显示" width="70" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.visible"
            :active-value="'0'"
            :inactive-value="'1'"
            @change="handleVisibleChange(row)"
          />
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleAddChild(row)">
            新增
          </el-button>
          <el-button type="primary" link size="small" @click="handleEdit(row)">
            编辑
          </el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 菜单编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
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
            <el-form-item label="上级菜单">
              <el-tree-select
                v-model="formData.parentId"
                :data="menuTreeOptions"
                :props="{ label: 'menuName', value: 'id' }"
                placeholder="选择上级菜单"
                clearable
                :disabled="isTopLevelMenu"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单类型" prop="menuType">
              <el-radio-group v-model="formData.menuType">
                <el-radio label="M">目录</el-radio>
                <el-radio label="C">菜单</el-radio>
                <el-radio label="F">按钮</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="sort">
              <el-input-number
                v-model="formData.sort"
                :min="0"
                :max="9999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" v-if="formData.menuType !== 'F'">
          <el-col :span="12">
            <el-form-item label="菜单图标" prop="icon">
              <icon-picker v-model="formData.icon" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路由地址" prop="path">
              <el-input v-model="formData.path" placeholder="如: /system/menu" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" v-if="formData.menuType === 'C'">
          <el-col :span="12">
            <el-form-item label="组件路径" prop="component">
              <el-input v-model="formData.component" placeholder="如: system/menu/index" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路由参数" prop="query">
              <el-input v-model="formData.query" placeholder="如: ?id=1" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="权限标识" prop="perm">
              <el-input v-model="formData.perm" placeholder="如: system:menu:list" />
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
        
        <el-row :gutter="20" v-if="formData.menuType !== 'F'">
          <el-col :span="12">
            <el-form-item label="显示状态" prop="visible">
              <el-radio-group v-model="formData.visible">
                <el-radio label="0">显示</el-radio>
                <el-radio label="1">隐藏</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否外链" prop="isFrame">
              <el-radio-group v-model="formData.isFrame">
                <el-radio label="1">是</el-radio>
                <el-radio label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" v-if="formData.menuType !== 'F'">
          <el-col :span="12">
            <el-form-item label="是否缓存" prop="isCache">
              <el-radio-group v-model="formData.isCache">
                <el-radio label="0">缓存</el-radio>
                <el-radio label="1">不缓存</el-radio>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowRight, ArrowDown } from '@element-plus/icons-vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import IconPicker from '@/components/IconPicker.vue'
import { getMenuTree, createMenu, updateMenu, deleteMenu, updateStatus, updateVisible } from '@/api/system/menu'

const loading = ref(false)
const menuTree = ref<any[]>([])
const expandedKeys = ref<string[]>([])

// 扁平化菜单树（根据展开状态显示/隐藏子菜单）
const flattenMenuTree = computed(() => {
  const result: any[] = []
  const flatten = (menus: any[]) => {
    menus.forEach(menu => {
      result.push(menu)
      // 如果菜单已展开，递归添加子菜单
      if (menu.children?.length && expandedKeys.value.includes(menu.id)) {
        flatten(menu.children)
      }
    })
  }
  flatten(menuTree.value)
  return result
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const submitLoading = ref(false)
const isTopLevelMenu = ref(false)

// 菜单树选项（用于上级菜单选择）
const menuTreeOptions = computed(() => {
  const addTopOption = [{ id: '0', menuName: '顶级菜单', children: [] }]
  return [...addTopOption, ...menuTree.value]
})

const formData = reactive({
  id: '',
  parentId: '0',
  menuName: '',
  menuType: 'M',
  icon: '',
  path: '',
  component: '',
  query: '',
  perm: '',
  sort: 0,
  status: '0',
  visible: '0',
  isFrame: '0',
  isCache: '0'
})

const formRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  path: [{ required: true, message: '请输入路由地址', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

// 获取菜单树
const fetchMenuTree = async () => {
  loading.value = true
  try {
    menuTree.value = await getMenuTree() || []
    // 默认展开所有
    expandedKeys.value = getAllMenuIds(menuTree.value)
  } finally {
    loading.value = false
  }
}

// 获取图标组件
const getIconComponent = (iconName: string) => {
  return (ElementPlusIconsVue as any)[iconName] || 'CircleCheck'
}

// 计算左侧缩进（根据层级）
const getPaddingLeft = (row: any, index: number) => {
  // 计算层级：在原始 menuTree 中找到该菜单的层级
  const getLevel = (menus: any[], targetId: string, currentLevel: number = 0): number => {
    for (const menu of menus) {
      if (menu.id === targetId) {
        return currentLevel
      }
      if (menu.children?.length) {
        const level = getLevel(menu.children, targetId, currentLevel + 1)
        if (level >= 0) return level
      }
    }
    return -1
  }
  const level = getLevel(menuTree.value, row.id)
  return level > 0 ? level * 20 : 0
}

// 根据ID查找菜单
const findMenuById = (menus: any[], id: string): any => {
  for (const menu of menus) {
    if (menu.id === id) return menu
    if (menu.children?.length) {
      const found = findMenuById(menu.children, id)
      if (found) return found
    }
  }
  return null
}

// 判断是否已展开
const isExpanded = (row: any) => {
  return expandedKeys.value.includes(row.id)
}

// 切换展开/收起
const toggleExpand = (row: any) => {
  const index = expandedKeys.value.indexOf(row.id)
  if (index > -1) {
    // 收起时同时收起所有子级
    removeChildrenKeys(row)
  } else {
    expandedKeys.value.push(row.id)
  }
}

// 递归移除子级key
const removeChildrenKeys = (row: any) => {
  const index = expandedKeys.value.indexOf(row.id)
  if (index > -1) {
    expandedKeys.value.splice(index, 1)
  }
  if (row.children?.length) {
    row.children.forEach((child: any) => removeChildrenKeys(child))
  }
}



// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    id: '',
    parentId: '0',
    menuName: '',
    menuType: 'M',
    icon: '',
    path: '',
    component: '',
    query: '',
    perm: '',
    sort: 0,
    status: '0',
    visible: '0',
    isFrame: '0',
    isCache: '0'
  })
}

// 新增顶级菜单
const handleAddTopLevel = () => {
  resetForm()
  isTopLevelMenu.value = false
  dialogTitle.value = '新增顶级菜单'
  dialogVisible.value = true
}

// 新增子菜单
const handleAddChild = (row: any) => {
  resetForm()
  isTopLevelMenu.value = true
  formData.parentId = row.id
  dialogTitle.value = `新增子菜单 - ${row.menuName}`
  dialogVisible.value = true
}

// 编辑菜单
const handleEdit = (row: any) => {
  Object.assign(formData, { ...row })
  isTopLevelMenu.value = row.parentId === '0' || !row.parentId
  dialogTitle.value = '编辑菜单'
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const api = formData.id ? updateMenu : createMenu
    await api(formData.id, formData)
    ElMessage.success(formData.id ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchMenuTree()
  } finally {
    submitLoading.value = false
  }
}

// 删除菜单
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除菜单 "${row.menuName}" 吗？将同时删除其子菜单。`, '提示', {
      type: 'warning'
    })
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    fetchMenuTree()
  } catch {
    // 取消删除
  }
}

// 状态变更
const handleStatusChange = async (row: any) => {
  try {
    await updateStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch {
    row.status = row.status === '0' ? '1' : '0'
  }
}

// 显示状态变更
const handleVisibleChange = async (row: any) => {
  try {
    await updateVisible(row.id, row.visible)
    ElMessage.success('显示状态更新成功')
  } catch {
    row.visible = row.visible === '0' ? '1' : '0'
  }
}

// 排序变更
const handleSortChange = async (row: any) => {
  // 可以批量更新排序，或者防抖处理
}

// 获取所有菜单ID
const getAllMenuIds = (menus: any[]): string[] => {
  const ids: string[] = []
  menus.forEach(menu => {
    ids.push(menu.id)
    if (menu.children?.length) {
      ids.push(...getAllMenuIds(menu.children))
    }
  })
  return ids
}

onMounted(() => {
  fetchMenuTree()
})
</script>

<style scoped lang="scss">
.menu-management {
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
  
  .menu-name-cell {
    display: flex;
    align-items: center;
    gap: 4px;
    
    .expand-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 16px;
      height: 16px;
      cursor: pointer;
      color: #909399;
      transition: color 0.3s;
      
      &:hover {
        color: #409eff;
      }
    }
    
    .expand-placeholder {
      display: inline-block;
      width: 16px;
    }
    
    .menu-icon {
      font-size: 16px;
      color: #409eff;
      margin-left: 4px;
    }
  }
}

// 修改 switch 样式，使文字不换行
:deep(.el-switch) {
  .el-switch__label {
    white-space: nowrap;
  }
  
  &.el-switch--default {
    .el-switch__label {
      font-size: 12px;
    }
  }
}
</style>
