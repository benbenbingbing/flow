<template>
  <div class="report-list">
    <div class="page-header">
      <h2>报表管理</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>新建报表
      </el-button>
    </div>
    
    <!-- 查询条件 -->
    <el-card class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="分类">
          <el-select v-model="query.categoryId" placeholder="选择分类" clearable style="width: 150px">
            <el-option
              v-for="cat in categories"
              :key="cat.id"
              :label="cat.categoryName"
              :value="cat.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报表类型">
          <el-select v-model="query.reportType" placeholder="选择类型" clearable style="width: 150px">
            <el-option label="表格" value="TABLE" />
            <el-option label="图表" value="CHART" />
            <el-option label="大屏" value="DASHBOARD" />
            <el-option label="打印" value="PRINT" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="报表名称/编码" clearable />
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
        <el-table-column prop="reportCode" label="报表编码" min-width="150" />
        <el-table-column prop="reportName" label="报表名称" min-width="180" />
        <el-table-column label="报表类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getReportTypeType(row.reportType)">
              {{ getReportTypeLabel(row.reportType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            {{ row.categoryName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="success" @click="handlePreview(row)">预览</el-button>
            <el-button link type="warning" @click="handleExport(row)">导出</el-button>
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
    
    <!-- 设计器弹窗 -->
    <ReportDesigner
      v-model="designerVisible"
      :report-id="currentReportId"
      @success="handleDesignerSuccess"
    />
    
    <!-- 预览弹窗 -->
    <ReportPreview
      v-model="previewVisible"
      :report-id="currentReportId"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReportList, deleteReport, getReportCategories } from '@/api/report-engine'
import ReportDesigner from './ReportDesigner.vue'
import ReportPreview from './ReportPreview.vue'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const categories = ref([])
const designerVisible = ref(false)
const previewVisible = ref(false)
const currentReportId = ref(null)

const query = reactive({
  keyword: '',
  reportType: null,
  categoryId: null,
  pageNum: 1,
  pageSize: 10
})

// 加载分类
const loadCategories = async () => {
  try {
    const res = await getReportCategories()
    categories.value = res || []
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

// 加载报表列表
const loadData = async () => {
  loading.value = true
  try {
    const res = await getReportList(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载报表列表失败:', error)
    ElMessage.error('加载报表列表失败')
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
  query.reportType = null
  query.categoryId = null
  handleSearch()
}

// 新建
const handleCreate = () => {
  currentReportId.value = null
  designerVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  currentReportId.value = row.id
  designerVisible.value = true
}

// 预览
const handlePreview = (row) => {
  currentReportId.value = row.id
  previewVisible.value = true
}

// 导出
const handleExport = (row) => {
  ElMessage.info('导出功能开发中...')
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除报表 "${row.reportName}" 吗？`, '提示', { type: 'warning' })
    await deleteReport(row.id)
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

// 获取报表类型标签
const getReportTypeLabel = (type) => {
  const map = {
    'TABLE': '表格',
    'CHART': '图表',
    'DASHBOARD': '大屏',
    'PRINT': '打印'
  }
  return map[type] || type
}

// 获取报表类型样式
const getReportTypeType = (type) => {
  const map = {
    'TABLE': '',
    'CHART': 'success',
    'DASHBOARD': 'warning',
    'PRINT': 'info'
  }
  return map[type] || ''
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  loadCategories()
  loadData()
})
</script>

<style scoped lang="scss">
.report-list {
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
