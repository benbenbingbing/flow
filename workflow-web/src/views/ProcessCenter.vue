<template>
  <div class="process-center">
    <!-- 统计卡片 -->
    <div class="statistics-cards">
      <el-card class="stat-card" shadow="hover" @click="switchTab('todo')">
        <div class="stat-icon bg-warning">
          <el-icon><Bell /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ statistics.todoCount || 0 }}</div>
          <div class="stat-label">待办任务</div>
        </div>
      </el-card>
      
      <el-card class="stat-card" shadow="hover" @click="switchTab('done')">
        <div class="stat-icon bg-success">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ statistics.doneTodayCount || 0 }}</div>
          <div class="stat-label">今日已办</div>
        </div>
      </el-card>
      
      <el-card class="stat-card" shadow="hover" @click="switchTab('cc')">
        <div class="stat-icon bg-info">
          <el-icon><Message /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ statistics.unreadCcCount || 0 }}</div>
          <div class="stat-label">未读抄送</div>
        </div>
      </el-card>
      
      <el-card class="stat-card" shadow="hover" @click="switchTab('draft')">
        <div class="stat-icon bg-purple">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ statistics.draftCount || 0 }}</div>
          <div class="stat-label">草稿箱</div>
        </div>
      </el-card>
    </div>
    
    <!-- 主内容区 -->
    <el-card class="main-content">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- 待办任务 -->
        <el-tab-pane label="待办任务" name="todo">
          <TodoList ref="todoListRef" @view="handleViewTask" @approve="handleApproveTask" />
        </el-tab-pane>
        
        <!-- 已办任务 -->
        <el-tab-pane label="已办任务" name="done">
          <DoneList ref="doneListRef" @view="handleViewTask" />
        </el-tab-pane>
        
        <!-- 抄送/知会 -->
        <el-tab-pane label="抄送/知会" name="cc">
          <CcList ref="ccListRef" @view="handleViewTask" />
        </el-tab-pane>
        
        <!-- 草稿箱 -->
        <el-tab-pane label="草稿箱" name="draft">
          <DraftList ref="draftListRef" @edit="handleEditDraft" @submit="handleSubmitDraft" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
    
    <!-- 审批弹窗 -->
    <ApproveDialog 
      v-model="approveDialogVisible" 
      :task="currentTask"
      @success="handleApproveSuccess"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Bell, CircleCheck, Message, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getStatistics, submitDraft } from '@/api/process-center'
import TodoList from './process-center/TodoList.vue'
import DoneList from './process-center/DoneList.vue'
import CcList from './process-center/CcList.vue'
import DraftList from './process-center/DraftList.vue'
import ApproveDialog from './process-center/ApproveDialog.vue'

const activeTab = ref('todo')
const statistics = ref({})
const approveDialogVisible = ref(false)
const currentTask = ref(null)

const todoListRef = ref()
const doneListRef = ref()
const ccListRef = ref()
const draftListRef = ref()

// 加载统计数据
const loadStatistics = async () => {
  try {
    const res = await getStatistics()
    statistics.value = res
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 切换标签页
const switchTab = (tab) => {
  activeTab.value = tab
}

// 查看任务
const handleViewTask = (task) => {
  // 跳转到流程进度页面或打开详情弹窗
  console.log('查看任务:', task)
}

// 审批任务
const handleApproveTask = (task) => {
  currentTask.value = task
  approveDialogVisible.value = true
}

// 编辑草稿
const handleEditDraft = (draft) => {
  console.log('编辑草稿:', draft)
}

// 提交草稿
const handleSubmitDraft = async (draft) => {
  try {
    await submitDraft(draft.id)
    ElMessage.success('提交成功')
    draftListRef.value?.loadData()
    loadStatistics()
  } catch (error) {
    ElMessage.error(error.message || '提交失败')
  }
}

// 审批成功回调
const handleApproveSuccess = () => {
  loadStatistics()
  todoListRef.value?.loadData()
  doneListRef.value?.loadData()
}

onMounted(() => {
  loadStatistics()
})
</script>

<style scoped lang="scss">
.process-center {
  padding: 20px;
  
  .statistics-cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 20px;
    margin-bottom: 20px;
    
    .stat-card {
      cursor: pointer;
      transition: transform 0.3s;
      
      &:hover {
        transform: translateY(-2px);
      }
      
      :deep(.el-card__body) {
        display: flex;
        align-items: center;
        padding: 20px;
      }
      
      .stat-icon {
        width: 60px;
        height: 60px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 28px;
        color: #fff;
        margin-right: 15px;
        
        &.bg-warning { background: linear-gradient(135deg, #f6d365 0%, #fda085 100%); }
        &.bg-success { background: linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%); }
        &.bg-info { background: linear-gradient(135deg, #a1c4fd 0%, #c2e9fb 100%); }
        &.bg-purple { background: linear-gradient(135deg, #e0c3fc 0%, #8ec5fc 100%); }
      }
      
      .stat-info {
        flex: 1;
        
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
      }
    }
  }
  
  .main-content {
    min-height: 500px;
  }
}
</style>
