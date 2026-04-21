<template>
  <div class="service-list">
    <div class="page-header">
      <h2>服务编排</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>新建服务
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
        <el-form-item label="服务类型">
          <el-select v-model="query.serviceType" placeholder="选择类型" clearable style="width: 150px">
            <el-option label="服务编排" value="ORCHESTRATION" />
            <el-option label="脚本服务" value="SCRIPT" />
            <el-option label="代理服务" value="PROXY" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="服务名称/编码" clearable />
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
        <el-table-column prop="serviceCode" label="服务编码" min-width="150" />
        <el-table-column prop="serviceName" label="服务名称" min-width="180" />
        <el-table-column label="服务类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getServiceTypeType(row.serviceType)">
              {{ getServiceTypeLabel(row.serviceType) }}
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
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="success" @click="handleExecute(row)">执行</el-button>
            <el-button link type="info" @click="handleLogs(row)">日志</el-button>
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
    <ServiceDesigner
      v-model="designerVisible"
      :service-id="currentServiceId"
      @success="handleDesignerSuccess"
    />
    
    <!-- 执行弹窗 -->
    <ExecuteDialog
      v-model="executeVisible"
      :service="currentService"
      @success="handleExecuteSuccess"
    />
    
    <!-- 日志弹窗 -->
    <LogDialog
      v-model="logsVisible"
      :service-id="currentServiceId"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getServiceList, deleteService, getServiceCategories } from '@/api/service-orchestration'
import ServiceDesigner from './ServiceDesigner.vue'
import ExecuteDialog from './ExecuteDialog.vue'
import LogDialog from './LogDialog.vue'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const categories = ref([])
const designerVisible = ref(false)
const executeVisible = ref(false)
const logsVisible = ref(false)
const currentServiceId = ref(null)
const currentService = ref(null)

const query = reactive({
  keyword: '',
  serviceType: null,
  categoryId: null,
  pageNum: 1,
  pageSize: 10
})

// 加载分类
const loadCategories = async () => {
  try {
    const res = await getServiceCategories()
    categories.value = res || []
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

// 加载服务列表
const loadData = async () => {
  loading.value = true
  try {
    const res = await getServiceList(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载服务列表失败:', error)
    ElMessage.error('加载服务列表失败')
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
  query.serviceType = null
  query.categoryId = null
  handleSearch()
}

// 新建
const handleCreate = () => {
  currentServiceId.value = null
  designerVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  currentServiceId.value = row.id
  designerVisible.value = true
}

// 执行
const handleExecute = (row) => {
  currentService.value = row
  executeVisible.value = true
}

// 日志
const handleLogs = (row) => {
  currentServiceId.value = row.id
  logsVisible.value = true
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除服务 "${row.serviceName}" 吗？`, '提示', { type: 'warning' })
    await deleteService(row.id)
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

// 执行成功回调
const handleExecuteSuccess = () => {
  // 刷新数据
}

// 获取服务类型标签
const getServiceTypeLabel = (type) => {
  const map = {
    'ORCHESTRATION': '服务编排',
    'SCRIPT': '脚本服务',
    'PROXY': '代理服务'
  }
  return map[type] || type
}

// 获取服务类型样式
const getServiceTypeType = (type) => {
  const map = {
    'ORCHESTRATION': 'primary',
    'SCRIPT': 'success',
    'PROXY': 'info'
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
.service-list {
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
