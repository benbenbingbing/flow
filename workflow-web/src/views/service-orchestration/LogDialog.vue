<template>
  <el-dialog
    v-model="visible"
    title="执行日志"
    width="900px"
    :close-on-click-modal="false"
  >
    <el-table :data="logs" v-loading="loading" stripe>
      <el-table-column prop="executionId" label="执行ID" min-width="180" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
      <el-table-column prop="startTime" label="开始时间" width="160">
        <template #default="{ row }">
          {{ formatDate(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.errorMessage || '-' }}
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadLogs"
        @current-change="loadLogs"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getExecutionLogs } from '@/api/service-orchestration'

const props = defineProps({
  modelValue: Boolean,
  serviceId: String
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const logs = ref([])
const total = ref(0)

const query = ref({
  pageNum: 1,
  pageSize: 10
})

// 加载日志
const loadLogs = async () => {
  if (!props.serviceId) return
  
  loading.value = true
  try {
    const res = await getExecutionLogs(props.serviceId, query.value)
    logs.value = res.records || []
    total.value = res.total || 0
  } catch (error) {
    console.error('加载日志失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取状态类型
const getStatusType = (status) => {
  const map = {
    'SUCCESS': 'success',
    'FAILED': 'danger',
    'RUNNING': 'warning',
    'TIMEOUT': 'info'
  }
  return map[status] || ''
}

// 获取状态标签
const getStatusLabel = (status) => {
  const map = {
    'SUCCESS': '成功',
    'FAILED': '失败',
    'RUNNING': '运行中',
    'TIMEOUT': '超时'
  }
  return map[status] || status
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

watch(() => props.modelValue, (val) => {
  if (val && props.serviceId) {
    loadLogs()
  }
})
</script>

<style scoped lang="scss">
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
