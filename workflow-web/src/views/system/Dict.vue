<template>
  <div class="dict-management">
    <!-- 左侧：字典类型列表 -->
    <div class="dict-type-panel">
      <div class="panel-header">
        <h3>字典类型</h3>
        <el-button type="primary" size="small" @click="handleAddDict">
          <el-icon><Plus /></el-icon>
          新增
        </el-button>
      </div>

      <!-- 查询条件 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.dictName"
          placeholder="字典名称"
          size="small"
          clearable
          style="width: 120px"
        />
        <el-input
          v-model="searchForm.dictCode"
          placeholder="字典编码"
          size="small"
          clearable
          style="width: 120px"
        />
        <el-button type="primary" size="small" @click="fetchDictPage">查询</el-button>
        <el-button size="small" @click="handleResetSearch">重置</el-button>
      </div>

      <!-- 字典类型表格 -->
      <el-table
        v-loading="dictLoading"
        :data="dictList"
        border
        stripe
        size="small"
        highlight-current-row
        @current-change="handleDictSelect"
        style="flex: 1; overflow: auto"
      >
        <el-table-column prop="dictCode" label="字典编码" min-width="100" show-overflow-tooltip />
        <el-table-column prop="dictName" label="字典名称" min-width="100" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="'0'"
              :inactive-value="'1'"
              inline-prompt
              active-text="启"
              inactive-text="禁"
              size="small"
              @change="handleDictStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleEditDict(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click.stop="handleDeleteDict(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pageInfo.pageNum"
        v-model:page-size="pageInfo.pageSize"
        :total="pageInfo.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        small
        @size-change="fetchDictPage"
        @current-change="fetchDictPage"
        style="margin-top: 10px; justify-content: center"
      />
    </div>

    <!-- 右侧：字典项树形表格 -->
    <div class="dict-item-panel">
      <div class="panel-header">
        <h3>
          字典项
          <el-tag v-if="currentDict" size="small" type="info" style="margin-left: 8px">
            {{ currentDict.dictName }} ({{ currentDict.dictCode }})
          </el-tag>
        </h3>
        <el-button
          type="primary"
          size="small"
          :disabled="!currentDict"
          @click="handleAddItem"
        >
          <el-icon><Plus /></el-icon>
          新增项
        </el-button>
      </div>

      <el-table
        v-if="currentDict"
        v-loading="itemLoading"
        :data="itemTree"
        border
        stripe
        size="small"
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        default-expand-all
        style="flex: 1; overflow: auto"
      >
        <el-table-column prop="itemCode" label="项编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="itemLabel" label="标签" min-width="120" show-overflow-tooltip />
        <el-table-column prop="itemValue" label="值" min-width="120" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="60" align="center" />
        <el-table-column prop="status" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="'0'"
              :inactive-value="'1'"
              inline-prompt
              active-text="启"
              inactive-text="禁"
              size="small"
              @change="handleItemStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditItem(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="请先选择左侧字典类型" style="flex: 1" />
    </div>

    <!-- 字典类型编辑对话框 -->
    <el-dialog
      v-model="dictDialogVisible"
      :title="dictDialogTitle"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="dictFormRef"
        :model="dictForm"
        :rules="dictRules"
        label-width="100px"
      >
        <el-form-item label="字典编码" prop="dictCode">
          <el-input
            v-model="dictForm.dictCode"
            placeholder="请输入字典编码，如：status"
            :disabled="!!dictForm.id"
          />
        </el-form-item>
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="dictForm.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="dictForm.description"
            type="textarea"
            :rows="2"
            placeholder="请输入描述"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排序" prop="sort">
              <el-input-number
                v-model="dictForm.sort"
                :min="0"
                :max="9999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="dictForm.status">
                <el-radio label="0">启用</el-radio>
                <el-radio label="1">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitDict" :loading="dictSubmitLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典项编辑对话框 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="itemDialogTitle"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="itemFormRef"
        :model="itemForm"
        :rules="itemRules"
        label-width="100px"
      >
        <el-form-item label="父级项" prop="parentId">
          <el-tree-select
            v-model="itemForm.parentId"
            :data="itemSelectTree"
            :props="{ label: 'itemLabel', value: 'id', children: 'children' }"
            :render-after-expand="false"
            placeholder="不选则为顶级"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="项编码" prop="itemCode">
          <el-input v-model="itemForm.itemCode" placeholder="请输入项编码" />
        </el-form-item>
        <el-form-item label="标签" prop="itemLabel">
          <el-input v-model="itemForm.itemLabel" placeholder="请输入显示标签" />
        </el-form-item>
        <el-form-item label="值" prop="itemValue">
          <el-input v-model="itemForm.itemValue" placeholder="请输入项值" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="排序" prop="sort">
              <el-input-number
                v-model="itemForm.sort"
                :min="0"
                :max="9999"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="itemForm.status">
                <el-radio label="0">启用</el-radio>
                <el-radio label="1">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="itemForm.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitItem" :loading="itemSubmitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getDictPage,
  createDict,
  updateDict,
  deleteDict,
  updateDictStatus,
  getItemTreeByDictId,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  updateDictItemStatus
} from '@/api/system/dict'

// ==================== 字典类型 ====================
const dictLoading = ref(false)
const dictList = ref<any[]>([])
const currentDict = ref<any>(null)

const searchForm = reactive({
  dictName: '',
  dictCode: ''
})

const pageInfo = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 字典类型对话框
const dictDialogVisible = ref(false)
const dictDialogTitle = ref('')
const dictFormRef = ref()
const dictSubmitLoading = ref(false)

const dictForm = reactive({
  id: '',
  dictCode: '',
  dictName: '',
  description: '',
  sort: 0,
  status: '0'
})

const dictRules = {
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }]
}

// 分页查询字典类型
const fetchDictPage = async () => {
  dictLoading.value = true
  try {
    const res = await getDictPage({
      pageNum: pageInfo.pageNum,
      pageSize: pageInfo.pageSize,
      dictName: searchForm.dictName || undefined,
      dictCode: searchForm.dictCode || undefined
    })
    dictList.value = res?.records || []
    pageInfo.total = res?.total || 0
  } finally {
    dictLoading.value = false
  }
}

const handleResetSearch = () => {
  searchForm.dictName = ''
  searchForm.dictCode = ''
  pageInfo.pageNum = 1
  fetchDictPage()
}

const resetDictForm = () => {
  Object.assign(dictForm, {
    id: '',
    dictCode: '',
    dictName: '',
    description: '',
    sort: 0,
    status: '0'
  })
}

const handleAddDict = () => {
  resetDictForm()
  dictDialogTitle.value = '新增字典'
  dictDialogVisible.value = true
}

const handleEditDict = (row: any) => {
  resetDictForm()
  Object.assign(dictForm, {
    id: row.id,
    dictCode: row.dictCode,
    dictName: row.dictName,
    description: row.description,
    sort: row.sort,
    status: row.status
  })
  dictDialogTitle.value = '编辑字典'
  dictDialogVisible.value = true
}

const handleSubmitDict = async () => {
  await dictFormRef.value.validate()
  dictSubmitLoading.value = true
  try {
    if (dictForm.id) {
      await updateDict(dictForm.id, { ...dictForm })
      ElMessage.success('更新成功')
    } else {
      await createDict({ ...dictForm })
      ElMessage.success('创建成功')
    }
    dictDialogVisible.value = false
    fetchDictPage()
  } finally {
    dictSubmitLoading.value = false
  }
}

const handleDeleteDict = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除字典 "${row.dictName}" 吗？该字典下的所有字典项也将被删除。`, '提示', {
      type: 'warning'
    })
    await deleteDict(row.id)
    ElMessage.success('删除成功')
    if (currentDict.value?.id === row.id) {
      currentDict.value = null
      itemTree.value = []
    }
    fetchDictPage()
  } catch {
    // 取消删除
  }
}

const handleDictStatusChange = async (row: any) => {
  try {
    await updateDictStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch {
    row.status = row.status === '0' ? '1' : '0'
  }
}

// ==================== 字典项 ====================
const itemLoading = ref(false)
const itemTree = ref<any[]>([])

// 字典项对话框
const itemDialogVisible = ref(false)
const itemDialogTitle = ref('')
const itemFormRef = ref()
const itemSubmitLoading = ref(false)
const itemSelectTree = ref<any[]>([])

const itemForm = reactive({
  id: '',
  dictId: '',
  dictCode: '',
  parentId: '0',
  itemCode: '',
  itemLabel: '',
  itemValue: '',
  sort: 0,
  status: '0',
  remark: ''
})

const itemRules = {
  itemCode: [{ required: true, message: '请输入项编码', trigger: 'blur' }],
  itemLabel: [{ required: true, message: '请输入标签', trigger: 'blur' }],
  itemValue: [{ required: true, message: '请输入值', trigger: 'blur' }]
}

// 选中字典类型
const handleDictSelect = (row: any) => {
  if (!row) return
  currentDict.value = row
  fetchItemTree()
}

// 查询字典项树
const fetchItemTree = async () => {
  if (!currentDict.value) return
  itemLoading.value = true
  try {
    const res = await getItemTreeByDictId(currentDict.value.id)
    itemTree.value = res || []
  } finally {
    itemLoading.value = false
  }
}

const resetItemForm = () => {
  Object.assign(itemForm, {
    id: '',
    dictId: currentDict.value?.id || '',
    dictCode: currentDict.value?.dictCode || '',
    parentId: '0',
    itemCode: '',
    itemLabel: '',
    itemValue: '',
    sort: 0,
    status: '0',
    remark: ''
  })
}

// 构建用于下拉选择的树（添加一个顶级选项）
const buildSelectTree = (items: any[]): any[] => {
  const topOption = { id: '0', itemLabel: '顶级', children: [] }
  const clone = JSON.parse(JSON.stringify(items || []))
  return [topOption, ...clone]
}

const handleAddItem = () => {
  resetItemForm()
  itemSelectTree.value = buildSelectTree(itemTree.value)
  itemDialogTitle.value = '新增字典项'
  itemDialogVisible.value = true
}

const handleEditItem = (row: any) => {
  resetItemForm()
  Object.assign(itemForm, {
    id: row.id,
    dictId: row.dictId,
    dictCode: row.dictCode,
    parentId: row.parentId || '0',
    itemCode: row.itemCode,
    itemLabel: row.itemLabel,
    itemValue: row.itemValue,
    sort: row.sort,
    status: row.status,
    remark: row.remark
  })
  itemSelectTree.value = buildSelectTree(itemTree.value)
  itemDialogTitle.value = '编辑字典项'
  itemDialogVisible.value = true
}

const handleSubmitItem = async () => {
  await itemFormRef.value.validate()
  itemSubmitLoading.value = true
  try {
    const data = { ...itemForm }
    if (data.parentId === '0') {
      data.parentId = '0'
    }
    if (itemForm.id) {
      await updateDictItem(itemForm.id, data)
      ElMessage.success('更新成功')
    } else {
      await createDictItem(data)
      ElMessage.success('创建成功')
    }
    itemDialogVisible.value = false
    fetchItemTree()
  } finally {
    itemSubmitLoading.value = false
  }
}

const handleDeleteItem = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除字典项 "${row.itemLabel}" 吗？其所有子项也将被删除。`, '提示', {
      type: 'warning'
    })
    await deleteDictItem(row.id)
    ElMessage.success('删除成功')
    fetchItemTree()
  } catch {
    // 取消删除
  }
}

const handleItemStatusChange = async (row: any) => {
  try {
    await updateDictItemStatus(row.id, row.status)
    ElMessage.success('状态更新成功')
  } catch {
    row.status = row.status === '0' ? '1' : '0'
  }
}

onMounted(() => {
  fetchDictPage()
})
</script>

<style scoped lang="scss">
.dict-management {
  display: flex;
  height: calc(100vh - 84px);
  gap: 16px;
  padding: 20px;
  box-sizing: border-box;

  .dict-type-panel {
    flex: 0 0 45%;
    display: flex;
    flex-direction: column;
    background: #fff;
    border-radius: 4px;
    padding: 16px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  }

  .dict-item-panel {
    flex: 1;
    display: flex;
    flex-direction: column;
    background: #fff;
    border-radius: 4px;
    padding: 16px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 500;
    }
  }

  .search-bar {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
    flex-wrap: wrap;
  }
}
</style>
