<template>
  <div class="cc-list">
    <!-- 查询条件 -->
    <div class="search-bar">
      <el-radio-group v-model="query.isRead" @change="handleSearch">
        <el-radio-button :label="null">全部</el-radio-button>
        <el-radio-button :label="false">未读</el-radio-button>
        <el-radio-button :label="true">已读</el-radio-button>
      </el-radio-group>
      
      <el-button type="primary" :disabled="!unreadCount" @click="handleMarkAllRead">
        全部标记为已读
      </el-button>
    </div>
    
    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column type="index" width="50" />
      
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isRead === 0" type="danger">未读</el-tag>
          <el-tag v-else type="info">已读</el-tag>
        </template>
      </el-table-column>
      
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
      
      <el-table-column label="发送人" width="120">
        <template #default="{ row }">
          {{ row.startUserName || '-' }}
        </template>
      </el-table-column>
      
      <el-table-column label="抄送时间" width="160">
        <template #default="{ row }">
          {{ formatDate(row.startTime) }}
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
          <el-button v-if="row.isRead === 0" link type="success" @click="handleMarkRead(row)">
            标记已读
          </el-button>
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
import { ElMessage } from 'element-plus'
import { getCcList, markCcAsRead } from '@/api/process-center'

const emit = defineEmits(['view'])

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const unreadCount = ref(0)

const query = reactive({
  isRead: null,
  pageNum: 1,
  pageSize: 10
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getCcList(query)
    tableData.value = res.records || []
    total.value = res.total || 0
    // 计算未读数量
    unreadCount.value = tableData.value.filter(item => item.isRead === 0).length
  } catch (error) {
    console.error('加载抄送列表失败:', error)
    ElMessage.error('加载抄送列表失败')
  } finally {
    loading.value = false
  }
}

// 查询
const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

// 查看
const handleView = (row) => {
  emit('view', row)
}

// 标记已读
const handleMarkRead = async (row) => {
  try {
    await markCcAsRead(row.id)
    ElMessage.success('已标记为已读')
    loadData()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 全部标记已读
const handleMarkAllRead = async () => {
  try {
    // 批量标记已读
    const unreadIds = tableData.value
      .filter(item => item.isRead === 0)
      .map(item => item.id)
    
    for (const id of unreadIds) {
      await markCcAsRead(id)
    }
    
    ElMessage.success('已全部标记为已读')
    loadData()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(() => {
  loadData()
})

defineExpose({ loadData })
</script>

<style scoped lang="scss">
.cc-list {
  .search-bar {
    margin-bottom: 20px;
    display: flex;
    justify-content: space-between;
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
