<template>
  <div class="draft-list">
    <!-- 查询条件 -->
    <div class="search-bar">
      <el-input 
        v-model="query.keyword" 
        placeholder="搜索流程名称/草稿标题" 
        clearable
        style="width: 250px"
        @keyup.enter="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>查询
      </el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>
    
    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column type="index" width="50" />
      
      <el-table-column label="流程名称" min-width="180">
        <template #default="{ row }">
          {{ row.processName || '-' }}
        </template>
      </el-table-column>
      
      <el-table-column label="草稿标题" min-width="200">
        <template #default="{ row }">
          <div class="draft-title">{{ row.draftTitle || '未命名草稿' }}</div>
          <div class="draft-summary" v-if="row.draftSummary">
            {{ row.draftSummary }}
          </div>
        </template>
      </el-table-column>
      
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      
      <el-table-column label="最后修改" width="160">
        <template #default="{ row }">
          {{ formatDate(row.updatedAt) }}
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="success" @click="handleSubmit(row)">提交</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDraftList, deleteDraft, submitDraft } from '@/api/process-center'

const emit = defineEmits(['edit', 'submit'])

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  keyword: '',
  status: 'ACTIVE',
  pageNum: 1,
  pageSize: 10
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getDraftList(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载草稿列表失败:', error)
    ElMessage.error('加载草稿列表失败')
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
  handleSearch()
}

// 编辑
const handleEdit = (row) => {
  emit('edit', row)
}

// 提交
const handleSubmit = async (row) => {
  try {
    await ElMessageBox.confirm('确定提交此草稿吗？', '提示', { type: 'warning' })
    await submitDraft(row.id)
    ElMessage.success('提交成功')
    emit('submit', row)
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '提交失败')
    }
  }
}

// 删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除此草稿吗？', '提示', { type: 'warning' })
    await deleteDraft(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
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
.draft-list {
  .search-bar {
    margin-bottom: 20px;
    display: flex;
    gap: 10px;
    align-items: center;
  }
  
  .draft-title {
    font-weight: 500;
    color: #303133;
  }
  
  .draft-summary {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  
  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
