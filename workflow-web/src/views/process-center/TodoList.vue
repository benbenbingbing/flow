<template>
  <div class="todo-list">
    <!-- 查询条件 -->
    <div class="search-bar">
      <el-input 
        v-model="query.keyword" 
        placeholder="搜索流程名称/业务数据" 
        clearable
        style="width: 250px"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="query.priority" placeholder="优先级" clearable style="width: 120px">
        <el-option label="紧急" :value="90" />
        <el-option label="高" :value="70" />
        <el-option label="普通" :value="50" />
        <el-option label="低" :value="30" />
      </el-select>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
      
      <el-button type="success" :disabled="!selectedTasks.length" @click="handleBatchApprove">
        批量审批({{ selectedTasks.length }})
      </el-button>
    </div>
    
    <!-- 数据表格 -->
    <el-table 
      :data="tableData" 
      v-loading="loading"
      @selection-change="handleSelectionChange"
      stripe
    >
      <el-table-column type="selection" width="55" />
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
      
      <el-table-column label="发起人" width="120">
        <template #default="{ row }">
          {{ row.startUserName || '-' }}
        </template>
      </el-table-column>
      
      <el-table-column label="到达时间" width="160">
        <template #default="{ row }">
          <div>{{ formatDate(row.startTime) }}</div>
          <el-tag size="small" :type="getDurationType(row.durationMs)">
            {{ formatDuration(row.durationMs) }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="优先级" width="90">
        <template #default="{ row }">
          <el-tag :type="getPriorityType(row.priority)">
            {{ getPriorityLabel(row.priority) }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
          <el-button link type="success" @click="handleApprove(row)">审批</el-button>
          <el-button link type="warning" @click="handleTransfer(row)">转办</el-button>
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
    
    <!-- 转办弹窗 -->
    <TransferDialog 
      v-model="transferDialogVisible"
      :task="currentTask"
      @success="handleTransferSuccess"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTodoList } from '@/api/process-center'
import TransferDialog from './TransferDialog.vue'

const emit = defineEmits(['view', 'approve'])

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const selectedTasks = ref([])
const currentTask = ref(null)
const transferDialogVisible = ref(false)

const query = reactive({
  keyword: '',
  priority: null,
  pageNum: 1,
  pageSize: 10
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getTodoList(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载待办列表失败:', error)
    ElMessage.error('加载待办列表失败')
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
  query.priority = null
  handleSearch()
}

// 选择变化
const handleSelectionChange = (selection) => {
  selectedTasks.value = selection
}

// 查看
const handleView = (row) => {
  emit('view', row)
}

// 审批
const handleApprove = (row) => {
  emit('approve', row)
}

// 转办
const handleTransfer = (row) => {
  currentTask.value = row
  transferDialogVisible.value = true
}

// 批量审批
const handleBatchApprove = () => {
  ElMessageBox.confirm(
    `确定批量审批选中的 ${selectedTasks.value.length} 个任务吗？`,
    '提示',
    { type: 'warning' }
  ).then(() => {
    // 批量审批逻辑
    ElMessage.success('批量审批成功')
    loadData()
  })
}

// 转办成功
const handleTransferSuccess = () => {
  loadData()
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

// 格式化停留时长
const formatDuration = (ms) => {
  if (!ms) return '刚刚'
  const hours = Math.floor(ms / (1000 * 60 * 60))
  if (hours < 1) return `${Math.floor(ms / (1000 * 60))}分钟`
  if (hours < 24) return `${hours}小时`
  return `${Math.floor(hours / 24)}天`
}

// 获取时长标签类型
const getDurationType = (ms) => {
  if (!ms) return 'info'
  const hours = Math.floor(ms / (1000 * 60 * 60))
  if (hours > 48) return 'danger'
  if (hours > 24) return 'warning'
  return 'info'
}

// 获取优先级类型
const getPriorityType = (priority) => {
  if (priority >= 90) return 'danger'
  if (priority >= 70) return 'warning'
  if (priority >= 50) return ''
  return 'info'
}

// 获取优先级标签
const getPriorityLabel = (priority) => {
  if (priority >= 90) return '紧急'
  if (priority >= 70) return '高'
  if (priority >= 50) return '普通'
  return '低'
}

onMounted(() => {
  loadData()
})

// 暴露方法供父组件调用
defineExpose({ loadData })
</script>

<style scoped lang="scss">
.todo-list {
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
  
  .business-summary {
    color: #606266;
    font-size: 13px;
  }
  
  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
