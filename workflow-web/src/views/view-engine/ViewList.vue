<template>
  <div class="view-list">
    <div class="page-header">
      <h2>视图管理</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>新建视图
      </el-button>
    </div>
    
    <!-- 查询条件 -->
    <el-card class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="实体">
          <el-select v-model="query.entityCode" placeholder="选择实体" clearable style="width: 200px">
            <el-option
              v-for="entity in entityList"
              :key="entity.entityCode"
              :label="entity.entityName"
              :value="entity.entityCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="视图类型">
          <el-select v-model="query.viewType" placeholder="选择类型" clearable style="width: 150px">
            <el-option label="列表" value="LIST" />
            <el-option label="图表" value="CHART" />
            <el-option label="看板" value="DASHBOARD" />
            <el-option label="详情" value="DETAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="视图名称/编码" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 数据表格 -->
    <el-card>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="viewCode" label="视图编码" min-width="150" />
        <el-table-column prop="viewName" label="视图名称" min-width="180" />
        <el-table-column label="视图类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getViewTypeType(row.viewType)">
              {{ getViewTypeLabel(row.viewType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联实体" min-width="150">
          <template #default="{ row }">
            {{ row.entityName || row.entityCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success">是</el-tag>
            <span v-else class="text-gray">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="success" @click="handlePreview(row)">预览</el-button>
            <el-button v-if="!row.isDefault" link type="warning" @click="handleSetDefault(row)">
              设为默认
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
    
    <!-- 编辑/新建弹窗 -->
    <ViewDesigner
      v-model="designerVisible"
      :view-id="currentViewId"
      @success="handleDesignerSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getViewList, deleteView, setDefaultView } from '@/api/view-engine'
import { entityApi } from '@/api/entity'
import ViewDesigner from './ViewDesigner.vue'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const entityList = ref([])
const designerVisible = ref(false)
const currentViewId = ref(null)

const query = reactive({
  keyword: '',
  viewType: null,
  entityCode: null,
  pageNum: 1,
  pageSize: 10
})

// 加载实体列表
const loadEntityList = async () => {
  try {
    const res = await entityApi.getList()
    entityList.value = res || []
  } catch (error) {
    console.error('加载实体列表失败:', error)
  }
}

// 加载视图列表
const loadData = async () => {
  loading.value = true
  try {
    const res = await getViewList(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载视图列表失败:', error)
    ElMessage.error('加载视图列表失败')
  } finally {
    loading.value = false
  }
}

// 查询
const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

// 重置
const handleReset = () => {
  query.keyword = ''
  query.viewType = null
  query.entityCode = null
  handleSearch()
}

// 新建
const handleCreate = () => {
  currentViewId.value = null
  designerVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  currentViewId.value = row.id
  designerVisible.value = true
}

// 预览
const handlePreview = (row) => {
  window.open(`#/view-preview/${row.id}`, '_blank')
}

// 设为默认
const handleSetDefault = async (row) => {
  try {
    await ElMessageBox.confirm(`确定将 "${row.viewName}" 设为默认视图吗？`, '提示')
    await setDefaultView(row.id, row.entityCode)
    ElMessage.success('设置成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '设置失败')
    }
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除视图 "${row.viewName}" 吗？`, '提示', { type: 'warning' })
    await deleteView(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 设计器成功回调
const handleDesignerSuccess = () => {
  loadData()
}

// 获取视图类型标签
const getViewTypeLabel = (type) => {
  const map = {
    'LIST': '列表',
    'CHART': '图表',
    'DASHBOARD': '看板',
    'DETAIL': '详情'
  }
  return map[type] || type
}

// 获取视图类型样式
const getViewTypeType = (type) => {
  const map = {
    'LIST': '',
    'CHART': 'success',
    'DASHBOARD': 'warning',
    'DETAIL': 'info'
  }
  return map[type] || ''
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  loadEntityList()
  loadData()
})
</script>

<style scoped lang="scss">
.view-list {
  padding: 20px;
  
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h2 {
      margin: 0;
    }
  }
  
  .search-card {
    margin-bottom: 20px;
  }
  
  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
