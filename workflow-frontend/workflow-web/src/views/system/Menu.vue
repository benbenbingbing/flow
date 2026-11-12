<template>
  <div class="menu-management">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>菜单管理</span>
          <div class="header-actions">
            <el-switch v-model="showTechnicalColumns" inline-prompt active-text="技术列" inactive-text="业务列" />
            <el-button type="primary" size="small" @click="handleAddTopLevel">
              <el-icon><Plus /></el-icon>创建顶级菜单
            </el-button>
          </div>
        </div>
      </template>
      <ResizableTreeLayout class="menu-layout" sidebar-label="菜单树宽度">
        <template #sidebar>
          <aside class="menu-sidebar">
            <div class="sidebar-title">菜单架构</div>
            <ManagementNavigationTree
              ref="navigationRef"
              :model-value="currentId"
              :page-loader="getMenuManagementPage"
              :path-loader="getMenuAncestors"
              label-key="menuName"
              root-label="全部菜单"
              @select="navigateTo"
            />
          </aside>
        </template>
        <main class="menu-content">
          <el-breadcrumb separator="/" class="menu-breadcrumb">
            <el-breadcrumb-item @click="navigateTo({ id: '0' })">全部菜单</el-breadcrumb-item>
            <el-breadcrumb-item v-for="item in currentPath" :key="item.id" @click="navigateTo(item)">
              {{ item.menuName }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <div class="list-toolbar">
            <el-input v-model="searchInput" placeholder="搜索全部菜单的名称、路由或权限码" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-radio-group v-model="filterType" size="small" @change="handleFilterChange">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="M">目录</el-radio-button>
              <el-radio-button value="C">菜单</el-radio-button>
              <el-radio-button value="F">按钮</el-radio-button>
            </el-radio-group>
          </div>
          <p class="list-description">{{ keyword ? '展示全菜单搜索结果，点击名称可进入对应层级。' : '展示当前菜单的直属下级，点击名称可进入下级。' }}</p>
          <PageState
            v-if="loadError"
            type="error"
            title="菜单列表加载失败"
            :description="loadError"
            retryable
            @retry="refreshMenus"
          />
          <el-table v-else v-loading="loading" :data="rows" row-key="id">
            <el-table-column prop="menuName" label="菜单名称" min-width="220" fixed="left">
              <template #default="{ row }">
                <el-icon v-if="row.icon && getIconComponent(row.icon)" class="menu-icon">
                  <component :is="getIconComponent(row.icon)" />
                </el-icon>
                <el-tooltip :content="row.displayPath" placement="top-start" :show-after="200">
                  <el-button type="primary" link @click="navigateTo(row)"><span class="menu-name">{{ row.menuName }}</span></el-button>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column v-if="keyword" prop="displayPath" label="所属路径" min-width="220" show-overflow-tooltip />
            <el-table-column prop="menuType" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.menuType === 'M'" type="warning">目录</el-tag>
                <el-tag v-else-if="row.menuType === 'C'" type="success">菜单</el-tag>
                <el-tag v-else-if="row.menuType === 'F'" type="info">按钮</el-tag>
              </template>
            </el-table-column>
      
            <el-table-column v-if="showTechnicalColumns" prop="perm" label="权限标识" min-width="160" show-overflow-tooltip />
      
            <el-table-column v-if="showTechnicalColumns" prop="path" label="路由路径" min-width="140" show-overflow-tooltip />
      
            <el-table-column v-if="showTechnicalColumns" prop="component" label="组件路径" min-width="160" show-overflow-tooltip />
      
            <el-table-column prop="sort" label="排序" width="70" align="center">
              <template #default="{ row }">
                <el-input-number
                  :model-value="row.sort"
                  :disabled="pendingRows.has(row.id)"
                  :min="0"
                  :max="9999"
                  size="small"
                  controls-position="right"
                  style="width: 60px"
                  @change="value => handleSortChange(row, value)"
                />
              </template>
            </el-table-column>
      
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status"
                  :loading="pendingRows.has(row.id)"
                  :active-value="'0'"
                  :inactive-value="'1'"
                  inline-prompt
                  active-text="启"
                  inactive-text="禁"
                  @change="value => handleStatusChange(row, String(value))"
                />
              </template>
            </el-table-column>
      
            <el-table-column prop="visible" label="显示" width="70" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.visible"
                  :loading="pendingRows.has(row.id)"
                  :active-value="'0'"
                  :inactive-value="'1'"
                  @change="value => handleVisibleChange(row, String(value))"
                />
              </template>
            </el-table-column>
      
            <el-table-column label="操作" :width="keyword ? 280 : 200" fixed="right">
              <template #default="{ row }">
                <el-button v-if="keyword" type="primary" link size="small" @click="navigateTo(row)">定位到树</el-button>
                <el-button v-if="row.menuType !== 'F'" type="primary" link size="small" @click="handleAddChild(row)">
                  新增
                </el-button>
                <el-button type="primary" link size="small" :disabled="pendingRows.has(row.id)" @click="handleEdit(row)">
                  编辑
                </el-button>
                <el-button type="danger" link size="small" :disabled="pendingRows.has(row.id)" @click="handleDelete(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            class="pagination-wrapper"
            :page-sizes="[20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="loadPage"
          />
        </main>
      </ResizableTreeLayout>
    </el-card>

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
        <el-alert
          title="日常配置只需名称、类型、上级和状态。路由、组件与权限码属于技术配置，建议由开发或平台负责人维护。"
          type="info"
          :closable="false"
          show-icon
          class="menu-form-alert"
        />
        <div class="advanced-toggle">
          <span>技术配置</span>
          <el-switch v-model="showAdvancedFields" />
        </div>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="上级菜单">
              <div class="parent-picker-field">
                <el-input :model-value="parentDisplayPath" placeholder="选择上级菜单" readonly @click="parentPickerVisible = true" />
                <el-button @click="parentPickerVisible = true">选择上级</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单类型" prop="menuType">
              <el-radio-group v-model="formData.menuType">
                <el-radio value="M">目录</el-radio>
                <el-radio value="C">菜单</el-radio>
                <el-radio value="F">按钮</el-radio>
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
        
        <el-row :gutter="20" v-if="formData.menuType !== 'F' && showAdvancedFields">
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
        
        <el-row :gutter="20" v-if="formData.menuType === 'C' && showAdvancedFields">
          <el-col v-if="showAdvancedFields" :span="12">
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
          <el-col v-if="showAdvancedFields" :span="12">
            <el-form-item label="权限标识" prop="perm">
              <el-input
                v-model="formData.perm"
                placeholder="如: system:menu:list"
                :disabled="isEntityListMenu"
              />
              <div v-if="isEntityListMenu" class="form-tip">
                菜单显示由角色的菜单授权控制；列表数据访问使用列表设计中的访问权限码，此处无需重复填写。
              </div>
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
        
        <el-row :gutter="20" v-if="formData.menuType !== 'F' && showAdvancedFields">
          <el-col :span="12">
            <el-form-item label="显示状态" prop="visible">
              <el-radio-group v-model="formData.visible">
                <el-radio value="0">显示</el-radio>
                <el-radio value="1">隐藏</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否外链" prop="isFrame">
              <el-radio-group v-model="formData.isFrame">
                <el-radio value="1">是</el-radio>
                <el-radio value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20" v-if="formData.menuType !== 'F' && showAdvancedFields">
          <el-col :span="12">
            <el-form-item label="是否缓存" prop="isCache">
              <el-radio-group v-model="formData.isCache">
                <el-radio value="0">缓存</el-radio>
                <el-radio value="1">不缓存</el-radio>
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
              <EntityDefinitionPicker
                v-model="formData.entityCode" 
                placeholder="选择关联实体（可选）"
                clearable
                value-key="entityCode"
                title="选择菜单关联实体"
                @selected="handleEntitySelected"
              />
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
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
          {{ formData.id ? '保存菜单' : '创建菜单' }}
        </el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="parentPickerVisible" title="选择上级菜单" width="600px" append-to-body destroy-on-close>
      <p class="form-tip">请选择目录或菜单，点击全部菜单设为顶级。自身及其下级不可选择。</p>
      <ManagementNavigationTree
        v-if="parentPickerVisible"
        :model-value="formData.parentId || '0'"
        :page-loader="getMenuManagementPage"
        :path-loader="getMenuAncestors"
        :exclude-id="formData.id"
        parent-candidates
        searchable
        label-key="menuName"
        root-label="全部菜单"
        search-placeholder="搜索菜单名称、路由或权限码"
        empty-description="暂无匹配上级菜单"
        @select="selectParent"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import IconPicker from '@/components/IconPicker.vue'
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import PageState from '@/components/PageState.vue'
import ManagementNavigationTree from './components/ManagementNavigationTree.vue'
import ResizableTreeLayout from './components/ResizableTreeLayout.vue'
import { getMenuManagementPage, getMenuAncestors, getMenuById, createMenu, updateMenu, deleteMenu, updateStatus, updateVisible, updateMenuSortValue } from '@/api/system/menu'
import { entityApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import { normalizeMenuIconName, resolveMenuIcon } from '@/utils/menuIcons'
import { notifySidebarMenuChanged } from '@/utils/menuRefresh'

const loading = ref(false)
const loadError = ref('')
const rows = ref<any[]>([])
const navigationRef = ref()
const currentId = ref('0')
const currentPath = ref<any[]>([])
const searchInput = ref('')
const keyword = ref('')
const filterType = ref('')
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const pendingRows = ref(new Set<string>())
const showTechnicalColumns = ref(false)
const showAdvancedFields = ref(false)
const parentPickerVisible = ref(false)
const selectedParent = ref<any>(null)
const parentDisplayPath = computed(() => formData.parentId === '0' ? '顶级菜单' : selectedParent.value?.displayPath || selectedParent.value?.menuName || '')
let pageSequence = 0
let navigationSequence = 0
let editSequence = 0

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const submitLoading = ref(false)
const entityListConfigs = ref<any[]>([])
const isEntityListMenu = computed(() =>
  formData.menuType === 'C'
  && !!formData.entityCode
  && !!formData.listKey
)

// 自动设置实体路由
const autoSetEntityPath = (showMessage = true) => {
  if (!formData.entityCode || !formData.listKey) return
  formData.resourceType = 'ENTITY_LIST'
  formData.path = `/entity-list/${formData.entityCode}/${formData.listKey}`
  formData.component = 'entity/EntityListRuntime'
  formData.perm = ''
  if (showMessage) {
    ElMessage.success(`已自动设置路由：${formData.path}`)
  }
}

const handleEntitySelected = async (entity: any) => {
  formData.listKey = ''
  entityListConfigs.value = []
  if (!entity?.id) return
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

/** 数据库分页统一用于各层级浏览和全菜单搜索，过期响应不能覆盖新的范围。 */
const loadPage = async () => {
  const sequence = ++pageSequence
  loading.value = true
  loadError.value = ''
  try {
    const page = await getMenuManagementPage({
      parentId: currentId.value, pageNum: pagination.pageNum, pageSize: pagination.pageSize,
      keyword: keyword.value, type: filterType.value
    })
    if (sequence !== pageSequence) return
    pagination.total = page.total
    const lastPage = Math.max(1, Math.ceil(page.total / pagination.pageSize))
    // 删除末页最后一条后自动退页，保持当前范围及筛选条件。
    if (pagination.pageNum > lastPage) {
      pagination.pageNum = lastPage
      return await loadPage()
    }
    rows.value = page.records
  } catch (error: any) {
    if (sequence === pageSequence) loadError.value = error?.message || '无法读取菜单，请重试。'
  } finally {
    if (sequence === pageSequence) loading.value = false
  }
}

/** 通过真实父链更新面包屑，导航树会补齐未展开、未翻页的目标节点。 */
const navigateTo = async (row: any) => {
  const sequence = ++navigationSequence
  ++pageSequence
  loading.value = true
  loadError.value = ''
  try {
    const path = row.id === '0' ? [] : await getMenuAncestors(row.id)
    if (sequence !== navigationSequence) return
    currentId.value = row.id
    currentPath.value = path
    searchInput.value = ''
    keyword.value = ''
    pagination.pageNum = 1
    await loadPage()
  } catch (error: any) {
    if (sequence === navigationSequence) {
      loading.value = false
      loadError.value = error?.message || '菜单定位失败'
    }
  }
}

/** 写入后的刷新独立处理错误，避免把已成功的写入误报为失败。 */
const refreshMenus = async () => {
  const scopeId = currentId.value
  try {
    const path = scopeId === '0' ? [] : await getMenuAncestors(scopeId)
    if (scopeId !== currentId.value) return
    currentPath.value = path
    await Promise.all([loadPage(), navigationRef.value?.reload()])
  } catch (error: any) {
    if (scopeId === currentId.value) loadError.value = error?.message || '菜单刷新失败，请重试。'
  }
}

const handleSearch = () => {
  keyword.value = searchInput.value.trim()
  pagination.pageNum = 1
  loadPage()
}

const handleFilterChange = () => {
  pagination.pageNum = 1
  loadPage()
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadPage()
}

const getIconComponent = (iconName: string) => resolveMenuIcon(iconName)

const selectParent = (row: any) => {
  if (row.selectionDisabled || row.menuType === 'F') return
  formData.parentId = row.id
  selectedParent.value = row.id === '0' ? null : row
  parentPickerVisible.value = false
}

// 重置表单
const resetForm = () => {
  selectedParent.value = null
  entityListConfigs.value = []
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

// 新增顶级菜单无需预读全量目录。
const handleAddTopLevel = () => {
  ++editSequence
  resetForm()
  showAdvancedFields.value = false
  dialogTitle.value = '新增顶级菜单'
  dialogVisible.value = true
}

const handleAddChild = (row: any) => {
  if (row.menuType === 'F') return
  ++editSequence
  resetForm()
  showAdvancedFields.value = false
  selectParent(row)
  dialogTitle.value = `新增子菜单 - ${row.menuName}`
  dialogVisible.value = true
}

/** 详情及实体列表选项准备完成后一次回填，连续点击编辑时丢弃旧请求。 */
const handleEdit = async (row: any) => {
  const sequence = ++editSequence
  try {
    const detail = await getMenuById(row.id)
    const parentPath = detail.parentId && detail.parentId !== '0'
      ? await getMenuAncestors(detail.parentId).catch(() => []) : []
    let configs: any[] = []
    if (detail.entityCode) {
      const options = await entityApi.resolveOptions({ entityCodes: [detail.entityCode] }).catch(() => [])
      if (options?.[0]) configs = await entityListConfigApi.getByEntityId(options[0].id).catch(() => [])
    }
    if (sequence !== editSequence) return
    resetForm()
    Object.assign(formData, detail)
    formData.parentId = detail.parentId || '0'
    selectedParent.value = parentPath.at(-1) || { menuName: detail.parentName || detail.parentId }
    entityListConfigs.value = configs
    formData.icon = normalizeMenuIconName(detail.icon) || detail.icon?.trim() || ''
    if (isEntityListMenu.value) autoSetEntityPath(false)
    showAdvancedFields.value = false
    dialogTitle.value = '编辑菜单'
    dialogVisible.value = true
  } catch (error: any) {
    if (sequence === editSequence) ElMessage.error(error?.message || '菜单详情加载失败')
  }
}

// 提交表单
const handleSubmit = async () => {
  if (isEntityListMenu.value) {
    autoSetEntityPath(false)
  }
  if (formData.menuType === 'C' && !formData.path) {
    showAdvancedFields.value = true
    ElMessage.warning('页面菜单需要配置路由地址；已展开技术配置。')
    return
  }
  await formRef.value.validate()
  formData.icon = normalizeMenuIconName(formData.icon) || formData.icon?.trim() || ''
  submitLoading.value = true
  try {
    const payload = {
      parentId: formData.parentId, menuName: formData.menuName, menuType: formData.menuType,
      icon: formData.icon, path: formData.path, component: formData.component, query: formData.query,
      perm: formData.perm, sort: formData.sort, status: formData.status, visible: formData.visible,
      isFrame: formData.isFrame, isCache: formData.isCache, entityCode: formData.entityCode,
      resourceType: formData.resourceType, listKey: formData.listKey
    }
    if (formData.id) await updateMenu(formData.id, payload)
    else await createMenu(payload)
    ElMessage.success(formData.id ? '更新成功' : '创建成功')
    dialogVisible.value = false
    await refreshMenus()
    notifySidebarMenuChanged()
  } finally {
    submitLoading.value = false
  }
}

// 删除菜单
const handleDelete = async (row: any) => {
  if (pendingRows.value.has(row.id)) return
  pendingRows.value.add(row.id)
  try {
    await ElMessageBox.prompt(
      `删除后该入口、子菜单及相关角色授权都会失效。请输入菜单名称「${row.menuName}」确认。`,
      '删除菜单',
      {
        type: 'warning',
        inputPlaceholder: row.menuName,
        inputValidator: value => value === row.menuName || '输入的菜单名称不一致',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消'
      }
    )
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    // 全菜单搜索可命中当前节点的祖先，级联删除后返回仍存在的上级。
    const deletedIndex = currentPath.value.findIndex(item => item.id === row.id)
    if (deletedIndex >= 0) await navigateTo(currentPath.value[deletedIndex - 1] || { id: '0' })
    await refreshMenus()
    notifySidebarMenuChanged()
  } catch {
    // 取消删除
  } finally {
    pendingRows.value.delete(row.id)
  }
}

// 状态变更
const handleStatusChange = async (row: any, value: string) => {
  if (pendingRows.value.has(row.id)) return
  pendingRows.value.add(row.id)
  const isEnable = value === '0'
  try {
    await ElMessageBox.confirm(
      isEnable
        ? `启用后，拥有授权的用户将可访问菜单「${row.menuName}」。`
        : `禁用后，菜单「${row.menuName}」及其下级入口将无法访问。`,
      isEnable ? '启用菜单' : '禁用菜单',
      {
        type: isEnable ? 'info' : 'warning',
        confirmButtonText: isEnable ? '确认启用' : '确认禁用',
        cancelButtonText: '取消'
      }
    )
    await updateStatus(row.id, value)
    row.status = value
    notifySidebarMenuChanged()
    if (isEnable) {
      ElMessage.success(`菜单「${row.menuName}」已启用，将恢复显示在导航栏且可正常访问`)
    } else {
      ElMessage.warning(`菜单「${row.menuName}」已禁用，将从导航栏移除且无法通过URL访问`)
    }
  } catch {
    // 取消确认或请求失败时继续显示服务器回显的状态。
  } finally {
    pendingRows.value.delete(row.id)
  }
}

// 显示状态变更
const handleVisibleChange = async (row: any, value: string) => {
  if (pendingRows.value.has(row.id)) return
  pendingRows.value.add(row.id)
  const isShow = value === '0'
  try {
    await ElMessageBox.confirm(
      isShow
        ? `菜单「${row.menuName}」将重新出现在导航中。`
        : `菜单「${row.menuName}」会从导航隐藏，但已知地址仍可能直接访问。`,
      isShow ? '显示菜单' : '隐藏菜单',
      {
        type: 'info',
        confirmButtonText: isShow ? '确认显示' : '确认隐藏',
        cancelButtonText: '取消'
      }
    )
    await updateVisible(row.id, value)
    row.visible = value
    notifySidebarMenuChanged()
    if (isShow) {
      ElMessage.success(`菜单「${row.menuName}」已显示，将重新出现在导航栏`)
    } else {
      ElMessage.info(`菜单「${row.menuName}」已隐藏，不会显示在导航栏但可通过URL直接访问`)
    }
  } catch {
    // 隐藏确认取消及请求失败都不改变当前回显。
  } finally {
    pendingRows.value.delete(row.id)
  }
}

/** 排序仅提交新数值，成功后重新分页，使同级顺序和左侧树立即一致。 */
const handleSortChange = async (row: any, value: number | undefined) => {
  if (value == null || !Number.isInteger(value) || pendingRows.value.has(row.id)) return
  const previousSort = row.sort
  row.sort = value
  pendingRows.value.add(row.id)
  try {
    await updateMenuSortValue(row.id, value)
    notifySidebarMenuChanged()
    ElMessage.success(`菜单「${row.menuName}」排序已保存`)
    await refreshMenus()
  } catch (error: any) {
    row.sort = previousSort
    ElMessage.error(error?.message || '排序保存失败')
  } finally {
    pendingRows.value.delete(row.id)
  }
}

onMounted(() => {
  loadPage()
})
</script>

<style scoped>
.card-header,
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-header {
  justify-content: space-between;
}

.menu-sidebar {
  min-width: 0;
  padding-right: 16px;
}

.sidebar-title {
  font-weight: 600;
  margin-bottom: 12px;
}

.menu-content {
  min-width: 0;
}

.menu-breadcrumb {
  margin: 4px 0 18px;
  line-height: 1.8;
}

.menu-breadcrumb :deep(.el-breadcrumb__inner) {
  cursor: pointer;
}

.list-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.list-toolbar > .el-input {
  width: min(360px, 100%);
}

.list-description,
.form-tip {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  overflow: auto;
  margin-top: 16px;
}

.parent-picker-field {
  display: flex;
  gap: 8px;
  width: 100%;
}

.menu-icon {
  margin-right: 5px;
}

.menu-form-alert {
  margin-bottom: 12px;
}

.advanced-toggle {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
}

@media (max-width: 1000px) {
  .menu-sidebar {
    padding: 0 0 16px;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .menu-sidebar :deep(.tree-scroll) {
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
