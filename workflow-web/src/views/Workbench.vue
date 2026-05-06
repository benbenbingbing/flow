<template>
  <div class="workbench">
    <!-- 欢迎区域 -->
    <div class="welcome-section">
      <div class="welcome-info">
        <h2>欢迎回来，{{ userStore.nickname || '用户' }}</h2>
        <p>{{ currentDate }}</p>
      </div>
      <div class="quick-stats">
        <div class="stat-item" @click="$router.push('/home')">
          <div class="stat-value">{{ statistics.todoCount || 0 }}</div>
          <div class="stat-label">待办任务</div>
        </div>
        <div class="stat-item" @click="$router.push('/home')">
          <div class="stat-value">{{ statistics.doneTodayCount || 0 }}</div>
          <div class="stat-label">今日已办</div>
        </div>
        <div class="stat-item" @click="$router.push('/home')">
          <div class="stat-value">{{ statistics.unreadCcCount || 0 }}</div>
          <div class="stat-label">未读抄送</div>
        </div>
      </div>
    </div>
    
    <!-- 工作台网格 -->
    <div class="workbench-grid">
      <!-- 快捷入口 -->
      <el-card class="widget-card shortcut-widget" shadow="hover">
        <template #header>
          <div class="widget-header">
            <span>快捷入口</span>
          </div>
        </template>
        <div class="shortcut-list">
          <div 
            v-for="item in shortcuts" 
            :key="item.name"
            class="shortcut-item"
            :style="{ backgroundColor: item.color + '20' }"
            @click="$router.push(item.url)"
          >
            <div class="shortcut-icon" :style="{ backgroundColor: item.color }">
              <el-icon>
                <Bell v-if="item.icon === 'process-center'" />
                <Box v-else-if="item.icon === 'entity'" />
                <View v-else-if="item.icon === 'view'" />
                <DataLine v-else-if="item.icon === 'report'" />
                <TrendCharts v-else />
              </el-icon>
            </div>
            <span class="shortcut-name">{{ item.name }}</span>
          </div>
        </div>
      </el-card>
      
      <!-- 待办任务 -->
      <el-card class="widget-card todo-widget" shadow="hover">
        <template #header>
          <div class="widget-header">
            <span>待办任务</span>
            <el-button link type="primary" @click="$router.push('/home')">
              查看更多
            </el-button>
          </div>
        </template>
        <div class="todo-list">
          <div v-if="!todoList.length" class="empty-text">暂无待办任务</div>
          <div 
            v-for="item in todoList" 
            :key="item.id"
            class="todo-item"
            @click="handleTodoClick(item)"
          >
            <div class="todo-info">
              <div class="todo-title">{{ item.processName }} - {{ item.taskName }}</div>
              <div class="todo-time">{{ formatTime(item.startTime) }}</div>
            </div>
            <el-tag size="small" :type="getPriorityType(item.priority)">
              {{ getPriorityLabel(item.priority) }}
            </el-tag>
          </div>
        </div>
      </el-card>
      
      <!-- 系统公告 -->
      <el-card class="widget-card notice-widget" shadow="hover">
        <template #header>
          <div class="widget-header">
            <span>系统公告</span>
          </div>
        </template>
        <div class="notice-list">
          <div 
            v-for="item in notices" 
            :key="item.title"
            class="notice-item"
          >
            <div class="notice-dot" />
            <div class="notice-content">
              <div class="notice-title">{{ item.title }}</div>
              <div class="notice-desc">{{ item.content }}</div>
            </div>
            <div class="notice-date">{{ item.date }}</div>
          </div>
        </div>
      </el-card>
      
      <!-- 数据统计 -->
      <el-card class="widget-card chart-widget" shadow="hover">
        <template #header>
          <div class="widget-header">
            <span>流程统计</span>
          </div>
        </template>
        <div class="chart-container">
          <div class="chart-placeholder">
        <el-icon size="48" color="#dcdfe6"><TrendCharts /></el-icon>
        <p>图表功能开发中</p>
      </div>
        </div>
      </el-card>
      
      <!-- 最近使用 -->
      <el-card class="widget-card recent-widget" shadow="hover">
        <template #header>
          <div class="widget-header">
            <span>最近使用</span>
          </div>
        </template>
        <div class="recent-list">
          <div class="recent-item" @click="$router.push('/process')">
            <el-icon><Share /></el-icon>
            <span>流程管理</span>
          </div>
          <div class="recent-item" @click="$router.push('/entity')">
            <el-icon><Box /></el-icon>
            <span>实体管理</span>
          </div>

        </div>
      </el-card>
      
      <!-- 日历 -->
      <el-card class="widget-card calendar-widget" shadow="hover">
        <template #header>
          <div class="widget-header">
            <span>日历</span>
          </div>
        </template>
        <el-calendar v-model="calendarDate" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Box, TrendCharts, Share } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getWorkbenchData } from '@/api/workbench'

// 图标组件映射
const iconComponents = {
  Box,
  TrendCharts,
  Share
}


const router = useRouter()
const userStore = useUserStore()

const statistics = ref({})
const todoList = ref([])
const shortcuts = ref([])
const notices = ref([])
const calendarDate = ref(new Date())
// 当前日期
const currentDate = computed(() => {
  const date = new Date()
  const weekDays = ['日', '一', '二', '三', '四', '五', '六']
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 星期${weekDays[date.getDay()]}`
})

// 加载工作台数据
const loadData = async () => {
  try {
    const res = await getWorkbenchData()
    statistics.value = res.statistics || {}
    todoList.value = res.todoList || []
    shortcuts.value = res.shortcuts || []
    notices.value = res.notices || []
    

  } catch (error) {
    console.error('加载工作台数据失败:', error)
  }
}



// 图标已改为使用 v-if 条件渲染，避免 Vue 3 动态组件问题

// 待办点击
const handleTodoClick = (item) => {
  router.push('/home')
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

// 获取优先级类型
const getPriorityType = (priority) => {
  if (priority >= 90) return 'danger'
  if (priority >= 70) return 'warning'
  return ''
}

// 获取优先级标签
const getPriorityLabel = (priority) => {
  if (priority >= 90) return '紧急'
  if (priority >= 70) return '高'
  return '普通'
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.workbench {
  padding: 20px;
  
  .welcome-section {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding: 20px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 8px;
    color: white;
    
    .welcome-info {
      h2 {
        margin: 0 0 8px 0;
        font-size: 24px;
      }
      p {
        margin: 0;
        opacity: 0.9;
      }
    }
    
    .quick-stats {
      display: flex;
      gap: 30px;
      
      .stat-item {
        text-align: center;
        cursor: pointer;
        transition: transform 0.3s;
        
        &:hover {
          transform: translateY(-2px);
        }
        
        .stat-value {
          font-size: 32px;
          font-weight: bold;
        }
        .stat-label {
          font-size: 14px;
          opacity: 0.9;
        }
      }
    }
  }
  
  .workbench-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    grid-template-rows: repeat(2, auto);
    gap: 20px;
    
    .widget-card {
      &.shortcut-widget {
        grid-column: span 1;
        grid-row: span 1;
      }
      &.todo-widget {
        grid-column: span 2;
        grid-row: span 1;
      }
      &.notice-widget {
        grid-column: span 1;
        grid-row: span 2;
      }
      &.chart-widget {
        grid-column: span 2;
        grid-row: span 1;
      }
      &.recent-widget {
        grid-column: span 1;
        grid-row: span 1;
      }
      &.calendar-widget {
        grid-column: span 1;
        grid-row: span 1;
      }
      
      .widget-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-weight: bold;
      }
    }
  }
  
  .shortcut-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 15px;
    
    .shortcut-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 20px;
      border-radius: 8px;
      cursor: pointer;
      transition: transform 0.3s, box-shadow 0.3s;
      
      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
      }
      
      .shortcut-icon {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 24px;
        color: white;
        margin-bottom: 10px;
      }
      
      .shortcut-name {
        font-size: 14px;
        color: #606266;
      }
    }
  }
  
  .todo-list {
    .empty-text {
      text-align: center;
      color: #909399;
      padding: 30px;
    }
    
    .todo-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px;
      border-bottom: 1px solid #ebeef5;
      cursor: pointer;
      transition: background 0.3s;
      
      &:hover {
        background: #f5f7fa;
      }
      
      &:last-child {
        border-bottom: none;
      }
      
      .todo-info {
        flex: 1;
        
        .todo-title {
          font-size: 14px;
          color: #303133;
          margin-bottom: 4px;
        }
        
        .todo-time {
          font-size: 12px;
          color: #909399;
        }
      }
    }
  }
  
  .notice-list {
    .notice-item {
      display: flex;
      align-items: flex-start;
      padding: 12px 0;
      border-bottom: 1px solid #ebeef5;
      
      &:last-child {
        border-bottom: none;
      }
      
      .notice-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #f56c6c;
        margin-top: 6px;
        margin-right: 10px;
        flex-shrink: 0;
      }
      
      .notice-content {
        flex: 1;
        
        .notice-title {
          font-size: 14px;
          color: #303133;
          margin-bottom: 4px;
        }
        
        .notice-desc {
          font-size: 12px;
          color: #909399;
          overflow: hidden;
          text-overflow: ellipsis;
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
        }
      }
      
      .notice-date {
        font-size: 12px;
        color: #c0c4cc;
        white-space: nowrap;
        margin-left: 10px;
      }
    }
  }
  
  .chart-container {
    height: 200px;
    
    .chart-placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: #909399;
      
      p {
        margin-top: 10px;
      }
    }
  }
  
  .recent-list {
    .recent-item {
      display: flex;
      align-items: center;
      padding: 12px;
      cursor: pointer;
      transition: background 0.3s;
      border-radius: 4px;
      
      &:hover {
        background: #f5f7fa;
      }
      
      .el-icon {
        margin-right: 10px;
        font-size: 18px;
        color: #409eff;
      }
    }
  }
  
  :deep(.el-calendar) {
    .el-calendar-day {
      height: 40px;
    }
  }
}
</style>
