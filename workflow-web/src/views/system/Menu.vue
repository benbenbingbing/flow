<template>
  <div class="menu-management">
    <div class="page-header">
      <el-button type="primary" @click="handleAddTopLevel">
        <el-icon><Plus /></el-icon>
        新增顶级菜单
      </el-button>
    </div>
    
    <!-- 菜单表格 -->
    <el-table
      v-loading="loading"
      :data="flattenMenuTree"
      :key="'table-' + expandedKeys.join(',')"
      row-key="id"
      border
      stripe
    >
      <el-table-column prop="menuName" label="菜单名称" min-width="220">
        <template #default="{ row, $index }">
          <div class="menu-name-cell" :style="{ paddingLeft: getPaddingLeft(row, $index) + 'px' }" :key="'cell-' + row.id + '-' + expandedKeys.includes(row.id)">
            <!-- 展开/收起图标 -->
            <span
              v-if="row.hasChildren || (row.children && row.children.length > 0)"
              class="expand-icon"
              :class="{ 'is-loading': row.loading }"
              @click.stop="toggleExpand(row)"
            >
              <el-icon>
                <ArrowRight v-if="!expandedKeys.includes(row.id)" />
                <ArrowDown v-else />
              </el-icon>
            </span>
            <span v-else class="expand-placeholder"></span>
            <el-icon v-if="row.icon && getIconComponent(row.icon)" class="menu-icon">
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

    <!-- 顶层分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="topPageNum"
        v-model:page-size="topPageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="topTotal"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
    
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
        
        <!-- 实体数据配置（仅菜单类型为C时显示） -->
        <el-row :gutter="20" v-if="formData.menuType === 'C'">
          <el-col :span="24">
            <el-divider>实体数据配置</el-divider>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联实体">
              <el-select 
                v-model="formData.entityCode" 
                placeholder="选择关联实体（可选）"
                clearable
                style="width: 100%"
                @change="handleEntityChange"
              >
                <el-option
                  v-for="entity in entityList"
                  :key="entity.entityCode"
                  :label="entity.entityName"
                  :value="entity.entityCode"
                />
              </el-select>
              <div class="form-tip">选择实体后，点击菜单将直接显示该实体的数据列表</div>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="formData.entityCode">
            <el-form-item label="关联列表">
              <el-select
                v-model="formData.listKey"
                placeholder="请选择已配置列表"
                style="width: 100%"
                @change="autoSetEntityPath"
              >
                <el-option
                  v-for="list in entityListConfigs"
                  :key="list.listKey"
                  :label="`${list.listName} (${list.listKey})`"
                  :value="list.listKey"
                />
              </el-select>
              <div class="form-tip">同一实体可由不同菜单展示不同列表和数据范围</div>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="formData.entityCode && formData.listKey">
            <el-form-item label="自动设置">
              <el-button type="primary" link @click="autoSetEntityPath">
                自动设置路由和组件
              </el-button>
              <div class="form-tip">根据实体自动填充路由地址和组件路径</div>
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
import { getMenuTree, getMenuChildren, getMenuSubtree, createMenu, updateMenu, deleteMenu, updateStatus, updateVisible } from '@/api/system/menu'
import { entityApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'

const loading = ref(false)
const menuTree = ref<any[]>([])
const expandedKeys = ref<string[]>([])

// 顶层分页
const topPageNum = ref(1)
const topPageSize = ref(10)
const topTotal = ref(0)

// 扁平化菜单树（根据展开状态显示/隐藏子菜单）
const flattenMenuTree = computed(() => {
  const result: any[] = []
  const flatten = (menus: any[]) => {
    menus.forEach(menu => {
      // 使用浅拷贝，确保 el-table 能正确识别行数据变化并重新渲染
      result.push({ ...menu, _expanded: expandedKeys.value.includes(menu.id) })
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

// 菜单树选项（用于上级菜单选择），打开对话框时懒加载整棵树
const menuTreeOptions = ref<any[]>([])

const fetchMenuTreeOptions = async () => {
  try {
    const tree = await getMenuTree() || []
    menuTreeOptions.value = [{ id: '0', menuName: '顶级菜单', children: [] }, ...tree]
  } catch (error) {
    console.error('获取菜单树选项失败:', error)
    menuTreeOptions.value = [{ id: '0', menuName: '顶级菜单', children: [] }]
  }
}

// 实体列表
const entityList = ref<any[]>([])
const entityListConfigs = ref<any[]>([])

// 加载实体列表
const fetchEntityList = async () => {
  try {
    entityList.value = await entityApi.getAll()
  } catch (error) {
    console.error('获取实体列表失败:', error)
  }
}

// 自动设置实体路由
const autoSetEntityPath = () => {
  if (!formData.entityCode || !formData.listKey) return
  const entity = entityList.value.find(e => e.entityCode === formData.entityCode)
  if (entity) {
    const list = entityListConfigs.value.find(item => item.listKey === formData.listKey)
    formData.resourceType = 'ENTITY_LIST'
    formData.path = `/entity-list/${formData.entityCode}/${formData.listKey}`
    formData.component = 'entity/EntityListRuntime'
    formData.perm = list?.accessPermissionCode
      || `entity:${String(formData.entityCode).toLowerCase()}:list`
    ElMessage.success(`已自动设置路由：${formData.path}`)
  }
}

const handleEntityChange = async () => {
  formData.listKey = ''
  entityListConfigs.value = []
  if (!formData.entityCode) return
  const entity = entityList.value.find(item => item.entityCode === formData.entityCode)
  if (!entity) return
  entityListConfigs.value = await entityListConfigApi.getByEntityId(entity.id).catch(() => [])
  const defaultList = entityListConfigs.value.find(item => item.isDefault)
    || entityListConfigs.value[0]
  if (defaultList) {
    formData.listKey = defaultList.listKey
    autoSetEntityPath()
  }
}

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
  isCache: '0',
  entityCode: '',
  resourceType: '',
  listKey: ''
})

const formRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  path: [{ required: true, message: '请输入路由地址', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

// 加载顶层菜单（分页）
const fetchTopMenus = async (pageNum = 1) => {
  loading.value = true
  try {
    const res = await getMenuChildren('0', pageNum, topPageSize.value)
    const records = res.records || []
    records.forEach((menu: any) => { menu.depth = 0 })
    menuTree.value = records
    topPageNum.value = res.pageNum || pageNum
    topTotal.value = res.total || 0
    expandedKeys.value = []
  } finally {
    loading.value = false
  }
}

// 加载指定节点的完整子树（展开时一次性加载所有后代）
const loadSubtree = async (row: any) => {
  if (row.loading) return
  row.loading = true
  try {
    const children = await getMenuSubtree(row.id) || []
    const setDepth = (menus: any[], depth: number) => {
      menus.forEach(menu => {
        menu.depth = depth
        if (menu.children?.length) {
          setDepth(menu.children, depth + 1)
        }
      })
    }
    setDepth(children, (row.depth || 0) + 1)
    row.children = children
  } finally {
    row.loading = false
  }
}

// 获取图标组件
const getIconComponent = (iconName: string) => {
  if (!iconName) return null
  const name = iconName.trim()
  // 精确匹配
  const exact = (ElementPlusIconsVue as any)[name]
  if (exact) return exact
  // 尝试首字母大写（处理全小写情况）
  const capitalized = name.charAt(0).toUpperCase() + name.slice(1)
  const capitalizedMatch = (ElementPlusIconsVue as any)[capitalized]
  if (capitalizedMatch) return capitalizedMatch
  // 调试：打印相近的图标名
  if (typeof window !== 'undefined') {
    const similar = Object.keys(ElementPlusIconsVue).filter(k =>
      k.toLowerCase().includes(name.toLowerCase())
    ).slice(0, 5)
    console.warn(`[Menu] Icon not found: "${name}", similar:`, similar)
  }
  return null
}

// 计算左侧缩进（根据层级）
const getPaddingLeft = (row: any, index: number) => {
  return (row.depth || 0) * 20
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
const toggleExpand = async (row: any) => {
  const index = expandedKeys.value.indexOf(row.id)
  if (index > -1) {
    // 收起时同时收起所有子级
    removeChildrenKeys(row)
  } else {
    // 展开时若尚未加载子菜单，先异步加载完整子树
    // 注意：row 是 flattenMenuTree 的浅拷贝，必须找到原始节点写入 children
    const originalRow = findMenuById(menuTree.value, row.id)
    if (originalRow && originalRow.hasChildren && (!originalRow.children || originalRow.children.length === 0)) {
      await loadSubtree(originalRow)
    }
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
    row.children.forEach((child: any) => {
      removeChildrenKeys(child)
    })
  }
}

// 顶层分页事件
const handleSizeChange = (size: number) => {
  topPageSize.value = size
  fetchTopMenus(1)
}

const handleCurrentChange = (page: number) => {
  fetchTopMenus(page)
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
    isCache: '0',
    entityCode: '',
    resourceType: '',
    listKey: ''
  })
}

// 新增顶级菜单
const handleAddTopLevel = async () => {
  resetForm()
  isTopLevelMenu.value = false
  dialogTitle.value = '新增顶级菜单'
  await fetchMenuTreeOptions()
  dialogVisible.value = true
}

// 新增子菜单
const handleAddChild = async (row: any) => {
  resetForm()
  isTopLevelMenu.value = true
  formData.parentId = row.id
  dialogTitle.value = `新增子菜单 - ${row.menuName}`
  await fetchMenuTreeOptions()
  dialogVisible.value = true
}

// 编辑菜单
const handleEdit = async (row: any) => {
  Object.assign(formData, { ...row })
  if (formData.entityCode) {
    const entity = entityList.value.find(item => item.entityCode === formData.entityCode)
    if (entity) {
      entityListConfigApi.getByEntityId(entity.id)
        .then(data => { entityListConfigs.value = data || [] })
        .catch(() => { entityListConfigs.value = [] })
    }
  }
  isTopLevelMenu.value = row.parentId === '0' || !row.parentId
  dialogTitle.value = '编辑菜单'
  await fetchMenuTreeOptions()
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.id) {
      // 更新
      await updateMenu(formData.id, formData)
    } else {
      // 创建
      await createMenu(formData)
    }
    ElMessage.success(formData.id ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchTopMenus(topPageNum.value)
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
    fetchTopMenus(topPageNum.value)
  } catch {
    // 取消删除
  }
}

// 状态变更
const handleStatusChange = async (row: any) => {
  const isEnable = row.status === '0'
  try {
    await updateStatus(row.id, row.status)
    if (isEnable) {
      ElMessage.success(`菜单「${row.menuName}」已启用，将恢复显示在导航栏且可正常访问`)
    } else {
      ElMessage.warning(`菜单「${row.menuName}」已禁用，将从导航栏移除且无法通过URL访问`)
    }
  } catch {
    row.status = row.status === '0' ? '1' : '0'
  }
}

// 显示状态变更
const handleVisibleChange = async (row: any) => {
  const isShow = row.visible === '0'
  try {
    await updateVisible(row.id, row.visible)
    if (isShow) {
      ElMessage.success(`菜单「${row.menuName}」已显示，将重新出现在导航栏`)
    } else {
      ElMessage.info(`菜单「${row.menuName}」已隐藏，不会显示在导航栏但可通过URL直接访问`)
    }
  } catch {
    row.visible = row.visible === '0' ? '1' : '0'
  }
}

// 排序变更
const handleSortChange = async (row: any) => {
  // 可以批量更新排序，或者防抖处理
}

onMounted(() => {
  fetchTopMenus(1)
  fetchEntityList()
})
</script>

<style scoped lang="scss">
.menu-management {
  padding: 20px;
  
  .page-header {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    margin-bottom: 20px;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
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

      &.is-loading {
        cursor: not-allowed;
        opacity: 0.6;
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

    &.load-more-cell {
      color: #409eff;
    }
  }
}

// 隐藏 el-table 内置的树形展开图标（使用自定义展开图标代替）
:deep(.el-table__expand-icon) {
  display: none !important;
}

// 隐藏展开图标的占位元素
:deep(.el-table__placeholder) {
  display: none !important;
}

// 隐藏展开列的 cell  padding 让自定义图标更贴边
:deep(.el-table__cell.el-table__expand-column) {
  padding: 0 !important;
  width: 0 !important;
  min-width: 0 !important;
  border: none !important;
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
