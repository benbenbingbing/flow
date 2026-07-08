<template>
  <el-timeline v-if="processHistory.length > 0">
    <el-timeline-item
      v-for="(item, index) in processHistory"
      :key="index"
      :type="item.type"
      :timestamp="item.time"
    >
      <div class="history-item">
        <span class="history-title">{{ item.title }}</span>
        <el-tag size="small" :type="item.status === 'COMPLETED' ? 'success' : (item.status === 'TERMINATED' ? 'danger' : 'warning')">
          {{ item.status === 'COMPLETED' ? '已完成' : (item.status === 'TERMINATED' ? '已终止' : '进行中') }}
        </el-tag>
      </div>
      <div class="history-desc">{{ item.description }}</div>
    </el-timeline-item>
  </el-timeline>
  <el-empty v-else description="暂无审批历史" />
</template>

<script setup lang="ts">
defineProps<{
  processHistory: any[]
}>()
</script>

<style scoped lang="scss">
.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.history-title {
  font-weight: 600;
  color: #303133;
}

.history-desc {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
}
</style>
