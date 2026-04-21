<template>
  <div class="done-list">
    <!-- 查询条件 -->
    <div class="search-bar">
      <el-input 
        v-model="query.keyword" 
        placeholder="搜索流程名称/业务数据" 
        clearable
        style="width: 250px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.actionType" placeholder="操作类型" clearable style="width: 140px">
        <el-option label="同意" value="APPROVE" />
        <el-option label="驳回" value="REJECT" />
        <el-option label="转办" value="TRANSFER" />
        <el-option label="退回" value="RETURN" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>
    
    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column type="index" width="50" />
      
      <el-table-column label="流程信息" min-width="200">
        <template #default="{ row }">
          <div class="process-info">
            <div class="process-name">{{ row.processName }}</div>
            <div class="task-name">{{ row.taskName }}</div>
          </div>
        </template>
      </el-table-column>
      
      <el-table-column label="业务数据" min-width="180">
        <template #default="{ row }">
          <div class="business-summary" v-if="row.businessSummary">
            {{ row.businessSummary }}
          </div>
          <span v-else class="text-gray">-</span>
        </template>
      </el-table-column>
      
      <el-table-column label="我的操作" width="100">
        <template #default="{ row }">
          <el-tag :type="getActionType(row.actionType)">
            {{ getActionLabel(row.actionType) }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="审批意见" min-width="150">
        <template #default="{ row }">
          {{ row.actionComment || '-' }}
        </template>
      </el-table-column>
      
      <el-table-column label="处理时间" width="160">
        <template #default="{ row }">
          {{ formatDate(row.endTime) }}
        </template>
      </el-table-column>
      
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">
          {{ formatDuration(row.durationMs) }}
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getDoneList } from '@/api/process-center'

const emit = defineEmits(['view'])

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  keyword: '',
  actionType: null,
  pageNum: 1,
  pageSize: 10
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getDoneList(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载已办列表失败:', error)
    ElMessage.error('加载已办列表失败')
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
  query.actionType = null
  handleSearch()
}

// 查看
const handleView = (row) => {
  emit('view', row)
}

// 获取操作类型
const getActionType = (type) => {
  const map = {
    'APPROVE': 'success',
    'REJECT': 'danger',
    'TRANSFER': 'warning',
    'RETURN': 'info'
  }
  return map[type] || ''
}

// 获取操作标签
const getActionLabel = (type) => {
  const map = {
    'APPROVE': '同意',
    'REJECT': '驳回',
    'TRANSFER': '转办',
    'RETURN': '退回'
  }
  return map[type] || type
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

// 格式化耗时
const formatDuration = (ms) => {
  if (!ms) return '-'
  const minutes = Math.floor(ms / (1000 * 60))
  if (minutes < 60) return `${minutes}分钟`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时${minutes % 60}分`
  const days = Math.floor(hours / 24)
  return `${days}天${hours % 24}小时`
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped lang="scss">
.done-list {
  .search-bar {
    margin-bottom: 20px;
    display: flex;
    gap: 10px;
    align-items: center;
  }
  
  .process-info {
    .process-name {
      font-weight: bold;
      color: #303133;
    }
    .task-name {
      font-size: 12px;
      color: #909399;
      margin-top: 4px;
    }
  }
  
  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
