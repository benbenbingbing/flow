<script setup>
import { Bell, Check, Share, Timer } from '@element-plus/icons-vue'
// 展示统计并发出页签意图，实际加载和失效刷新仍归首页会话管理。
defineProps({ statistics: { type: Object, required: true } })
const emit = defineEmits(['select'])
</script>
<template>
    <el-row :gutter="20" class="statistics-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card
          class="stat-card stat-card--clickable"
          shadow="hover"
          role="button"
          tabindex="0"
          aria-label="查看待办任务"
          @click="emit('select', 'todo')"
          @keyup.enter="emit('select', 'todo')"
        >
          <div class="stat-icon" style="background-color: #f56c6c;">
            <el-icon><Bell /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.todoCount }}</div>
            <div class="stat-label">待办任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card
          class="stat-card stat-card--clickable"
          shadow="hover"
          role="button"
          tabindex="0"
          aria-label="查看已办任务"
          @click="emit('select', 'done')"
          @keyup.enter="emit('select', 'done')"
        >
          <div class="stat-icon" style="background-color: #67c23a;">
            <el-icon><Check /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.doneCount }}</div>
            <div class="stat-label">已办任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card
          class="stat-card stat-card--clickable"
          shadow="hover"
          role="button"
          tabindex="0"
          aria-label="查看我发起的流程"
          @click="emit('select', 'started')"
          @keyup.enter="emit('select', 'started')"
        >
          <div class="stat-icon" style="background-color: #409eff;">
            <el-icon><Share /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.processCount }}</div>
            <div class="stat-label">我发起的</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card stat-card--static" shadow="never">
          <div class="stat-icon" style="background-color: #e6a23c;">
            <el-icon><Timer /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.avgProcessTime }}</div>
            <div class="stat-label">平均处理时长(小时)</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
</template>
<style scoped>
.statistics-row { margin-bottom: 20px; }
.stat-card--clickable {
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card--clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.stat-card--static {
  cursor: default;
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
}

.stat-icon .el-icon {
  font-size: 28px;
  color: #fff;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}


@media (max-width: 760px) {
  .statistics-row {
    margin: 0 0 8px !important;
    padding: 8px;
  }

  .statistics-row .el-col {
    margin-bottom: 8px;
  }

  .stat-card :deep(.el-card__body) {
    padding: 12px;
  }

  .stat-icon {
    width: 44px;
    height: 44px;
  }

  .stat-value {
    font-size: 22px;
  }

}
</style>
