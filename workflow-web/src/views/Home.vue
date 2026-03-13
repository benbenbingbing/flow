<template>
  <div class="home-container">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="statistics-row">
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon" style="background-color: #f56c6c;">
            <el-icon><Bell /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.todoCount }}</div>
            <div class="stat-label">待办任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon" style="background-color: #67c23a;">
            <el-icon><Check /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.doneCount }}</div>
            <div class="stat-label">已办任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon" style="background-color: #409eff;">
            <el-icon><Share /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.processCount }}</div>
            <div class="stat-label">我的流程</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
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

    <!-- 任务列表 -->
    <el-card class="task-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-tabs v-model="activeTab" class="task-tabs">
            <el-tab-pane name="todo">
              <template #label>
                <span>
                  <el-icon><Bell /></el-icon>
                  待办任务
                  <el-badge v-if="todoTotal > 0" :value="todoTotal" class="tab-badge" />
                </span>
              </template>
            </el-tab-pane>
            <el-tab-pane name="done">
              <template #label>
                <span>
                  <el-icon><Check /></el-icon>
                  已办任务
                </span>
              </template>
            </el-tab-pane>
          </el-tabs>
          <el-radio-group v-model="queryParams.timeRange" size="small" @change="handleTimeRangeChange">
            <el-radio-button label="week">本周</el-radio-button>
            <el-radio-button label="month">本月</el-radio-button>
            <el-radio-button label="year">本年</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :model="queryParams" inline size="small" class="search-form">
        <el-form-item label="流程名称">
          <el-input v-model="queryParams.processName" placeholder="请输入流程名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.taskName" placeholder="请输入任务名称" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="resetQuery">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 待办列表 -->
      <el-table v-if="activeTab === 'todo'" :data="todoList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="processName" label="流程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="taskName" label="任务名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="processName" label="流程实例" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="viewProcessProgress(row)">
              {{ row.processName || '未知流程' }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="startUserName" label="发起人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column prop="priority" label="优先级" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.priority >= 80" type="danger" size="small">紧急</el-tag>
            <el-tag v-else-if="row.priority >= 50" type="warning" size="small">高</el-tag>
            <el-tag v-else type="info" size="small">普通</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleApprove(row)">
              <el-icon><Check /></el-icon>审批
            </el-button>
            <el-button type="info" size="small" @click="viewProcessProgress(row)">
              <el-icon><View /></el-icon>进度
            </el-button>
            <el-button type="warning" size="small" @click="handleWithdraw(row)">
              <el-icon><Back /></el-icon>撤回
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已办列表 -->
      <el-table v-else :data="doneList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="processName" label="流程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="taskName" label="任务名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="processInstanceId" label="流程实例" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="viewProcessProgress(row)">
              {{ row.processInstanceId }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="startUserName" label="发起人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column prop="endTime" label="处理时间" width="160" />
        <el-table-column prop="duration" label="耗时" width="100">
          <template #default="{ row }">
            <span>{{ formatDuration(row.duration) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="处理结果" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.result === 'approve'" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="row.result === 'reject'" type="danger" size="small">驳回</el-tag>
            <el-tag v-else-if="row.result === 'transfer'" type="warning" size="small">转办</el-tag>
            <el-tag v-else type="info" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" @click="viewProcessProgress(row)">
              <el-icon><View /></el-icon>进度
            </el-button>
            <el-button type="primary" size="small" @click="viewProcessHistory(row)">
              <el-icon><List /></el-icon>历史
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="activeTab === 'todo' ? todoTotal : doneTotal"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 审批弹窗 -->
    <el-dialog
      v-model="approveDialogVisible"
      title="任务审批"
      width="800px"
      :close-on-click-modal="false"
      class="approve-dialog"
    >
      <!-- 上半部分：申请信息 -->
      <el-card shadow="never" class="form-card">
        <template #header>
          <div class="card-header">
            <span>申请信息</span>
            <el-tag v-if="taskDetail?.formConfig?.isReadonly" type="info" size="small">只读</el-tag>
          </div>
        </template>
        <!-- 调试信息（开发时使用） -->
        <div v-if="false" style="background: #f5f7fa; padding: 10px; margin-bottom: 10px; font-size: 12px;">
          <div>taskDetail: {{ taskDetail }}</div>
          <div>entityData: {{ taskDetail?.entityData }}</div>
          <div>formConfig: {{ taskDetail?.formConfig }}</div>
        </div>
        <div v-if="taskDetail?.entityData && Object.keys(taskDetail.entityData).length > 0" class="entity-data-form">
          <FormPreview 
            v-if="taskDetail?.formConfig?.fields && taskDetail.formConfig.fields.length > 0"
            :form="previewFormConfig" 
            v-model="taskDetail.entityData"
            :readonly="true"
            :show-header="false"
          />
          <div v-else class="entity-data-simple">
            <el-descriptions :column="2" border>
              <el-descriptions-item 
                v-for="(value, key) in taskDetail.entityData" 
                :key="key"
                :label="key"
              >
                {{ value }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </div>
        <el-empty v-else description="暂无申请数据" />
      </el-card>
      
      <!-- 下半部分：审批操作 -->
      <el-card shadow="never" class="approve-card" style="margin-top: 20px;">
        <template #header>
          <span>审批操作</span>
        </template>
        <el-form :model="approveForm" label-width="80px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="流程">
                <span>{{ currentTask?.processName || taskDetail?.processTask?.processName }}</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="当前节点">
                <span>{{ currentTask?.taskName || taskDetail?.processTask?.nodeName }}</span>
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider />
          <el-form-item label="审批意见" required>
            <el-radio-group v-model="approveForm.action">
              <el-radio-button label="approve">通过</el-radio-button>
              <el-radio-button label="reject">驳回</el-radio-button>
              <el-radio-button label="transfer">转办</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="approveForm.action === 'transfer'" label="转办人" required>
            <el-select-v2
              v-model="approveForm.transferTo"
              :options="userOptions"
              placeholder="选择转办人"
              filterable
              clearable
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="审批备注">
            <el-input
              v-model="approveForm.comment"
              type="textarea"
              :rows="3"
              placeholder="请输入审批备注"
            />
          </el-form-item>
        </el-form>
      </el-card>
      
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitApprove" :loading="submitLoading">
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- 流程详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="流程详情"
      width="900px"
      :close-on-click-modal="false"
    >
      <ProcessDetail
        v-if="detailDialogVisible && currentInstanceId"
        :instance-id="currentInstanceId"
      />
    </el-dialog>

    <!-- 流程历史弹窗 -->
    <el-dialog
      v-model="historyDialogVisible"
      title="审批历史"
      width="800px"
    >
      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in historyList"
          :key="index"
          :type="item.endTime ? 'success' : 'primary'"
          :timestamp="formatDate(item.endTime || item.createTime)"
        >
          <div class="history-item">
            <div class="history-title">{{ item.taskName }}</div>
            <div class="history-info">
              <span>处理人: {{ item.assignee || '待处理' }}</span>
              <el-tag v-if="item.result" :type="getResultType(item.result)" size="small">
                {{ getResultText(item.result) }}
              </el-tag>
            </div>
            <div v-if="item.comment" class="history-comment">
              {{ item.comment }}
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Check, Share, Timer, Search, Refresh, View, Back, List } from '@element-plus/icons-vue'
import ProcessDetail from '@/components/ProcessDetail.vue'
import FormPreview from '@/components/FormPreview.vue'

const router = useRouter()

// 统计数据
const statistics = reactive({
  todoCount: 0,
  doneCount: 0,
  processCount: 0,
  avgProcessTime: 0
})

// 当前激活的标签
const activeTab = ref('todo')

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  processName: '',
  taskName: '',
  timeRange: 'month'
})

// 列表数据
const todoList = ref([])
const doneList = ref([])
const todoTotal = ref(0)
const doneTotal = ref(0)
const loading = ref(false)

// 用户选项
const userOptions = ref([])

// 审批弹窗
const approveDialogVisible = ref(false)
const submitLoading = ref(false)
const currentTask = ref(null)
const approveForm = reactive({
  action: 'approve',
  comment: '',
  transferTo: ''
})

// 任务详情（包含表单和实体数据）
const taskDetail = ref(null)
const previewFormConfig = computed(() => {
  if (!taskDetail.value?.formConfig) return null
  return {
    formName: taskDetail.value.formConfig.formName || '表单',
    layoutType: taskDetail.value.formConfig.layoutType || 'vertical',
    fields: taskDetail.value.formConfig.fields || []
  }
})

// 详情弹窗
const detailDialogVisible = ref(false)
const currentInstanceId = ref('')

// 流程历史弹窗
const historyDialogVisible = ref(false)
const historyList = ref([])

// 获取统计数据
async function loadStatistics() {
  try {
    const res = await fetch('/api/process-task/statistics').then(r => r.json())
    if (res.code === 200) {
      Object.assign(statistics, res.data)
    }
  } catch (e) {
    console.error('加载统计数据失败:', e)
  }
}

// 获取待办列表
async function loadTodoList() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    const res = await fetch(`/api/process-task/todo?${params}`).then(r => r.json())
    if (res.code === 200) {
      todoList.value = res.data.records || []
      todoTotal.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载待办列表失败:', e)
    ElMessage.error('加载待办列表失败')
  } finally {
    loading.value = false
  }
}

// 获取已办列表
async function loadDoneList() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    const res = await fetch(`/api/process-task/done?${params}`).then(r => r.json())
    if (res.code === 200) {
      doneList.value = res.data.records || []
      doneTotal.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载已办列表失败:', e)
    ElMessage.error('加载已办列表失败')
  } finally {
    loading.value = false
  }
}

// 加载用户列表
async function loadUsers() {
  try {
    const res = await fetch('/api/system/user/list').then(r => r.json())
    if (res.code === 200) {
      userOptions.value = res.data.map(user => ({
        label: `${user.nickname || user.username} (${user.username})`,
        value: user.username
      }))
    }
  } catch (e) {
    console.error('加载用户列表失败:', e)
  }
}

// 处理审批
async function handleApprove(row) {
  currentTask.value = row
  approveForm.action = 'approve'
  approveForm.comment = ''
  approveForm.transferTo = ''
  taskDetail.value = null
  
  // 加载任务详情（包含表单和实体数据）
  try {
    const taskId = row.taskId || row.id
    console.log('审批任务ID:', taskId, 'row数据:', row)
    if (taskId) {
      const res = await fetch(`/api/process-task/detail/${taskId}`).then(r => r.json())
      console.log('任务详情接口返回:', res)
      if (res.code === 200) {
        taskDetail.value = res.data
        console.log('taskDetail赋值后:', taskDetail.value)
        console.log('entityData:', taskDetail.value?.entityData)
        console.log('formConfig:', taskDetail.value?.formConfig)
      } else {
        console.error('获取任务详情失败:', res.message)
      }
    }
  } catch (e) {
    console.error('加载任务详情失败:', e)
    ElMessage.error('加载任务详情失败')
  }
  
  approveDialogVisible.value = true
}

// 提交审批
async function submitApprove() {
  if (!approveForm.action) {
    ElMessage.warning('请选择审批意见')
    return
  }
  if (approveForm.action === 'transfer' && !approveForm.transferTo) {
    ElMessage.warning('请选择转办人')
    return
  }

  submitLoading.value = true
  try {
    const res = await fetch('/api/process-task/complete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        taskId: currentTask.value.taskId,
        action: approveForm.action,
        comment: approveForm.comment,
        transferTo: approveForm.transferTo
      })
    }).then(r => r.json())

    if (res.code === 200) {
      ElMessage.success('审批成功')
      approveDialogVisible.value = false
      // 同时刷新待办和已办列表
      loadTodoList()
      loadDoneList()
      loadStatistics()
    } else {
      ElMessage.error(res.message || '审批失败')
    }
  } catch (e) {
    console.error('审批失败:', e)
    ElMessage.error('审批失败: ' + (e.message || '未知错误'))
  } finally {
    submitLoading.value = false
  }
}

// 查看流程进度
function viewProcessProgress(row) {
  currentInstanceId.value = row.processInstanceId
  detailDialogVisible.value = true
}

// 撤回流程
async function handleWithdraw(row) {
  try {
    await ElMessageBox.confirm('确定要撤回该流程吗？撤回后流程将回到草稿状态', '提示', { type: 'warning' })
    
    const res = await fetch('/api/process-task/withdraw', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        processInstanceId: row.processInstanceId,
        reason: '发起人主动撤回'
      })
    }).then(r => r.json())
    
    if (res.code === 200) {
      ElMessage.success('流程撤回成功')
      loadTodoList()
      loadStatistics()
    } else {
      ElMessage.error(res.message || '撤回失败')
    }
  } catch (error) {
    // ElMessageBox.confirm 取消时会抛出 'cancel' 字符串
    if (error === 'cancel' || (error && error.message === 'cancel')) {
      return // 用户取消，不处理
    }
    console.error('撤回失败:', error)
    ElMessage.error('撤回失败: ' + (error.message || '未知错误'))
  }
}

// 查看流程历史
async function viewProcessHistory(row) {
  try {
    const res = await fetch(`/api/process-task/history/${row.processInstanceId}`).then(r => r.json())
    if (res.code === 200) {
      historyList.value = res.data || []
      historyDialogVisible.value = true
    } else {
      ElMessage.error(res.message || '加载历史失败')
    }
  } catch (e) {
    console.error('加载历史失败:', e)
    ElMessage.error('加载历史失败: ' + (e.message || '未知错误'))
  }
}

// 格式化日期
function formatDate(date) {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleString('zh-CN')
}

// 格式化耗时
function formatDuration(ms) {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days}天${hours % 24}小时`
  if (hours > 0) return `${hours}小时${minutes % 60}分`
  if (minutes > 0) return `${minutes}分${seconds % 60}秒`
  return `${seconds}秒`
}

// 获取审批结果类型
function getResultType(result) {
  const types = {
    'approve': 'success',
    'reject': 'danger',
    'transfer': 'warning',
    'auto': 'info'
  }
  return types[result] || 'info'
}

// 获取审批结果文本
function getResultText(result) {
  const texts = {
    'approve': '通过',
    'reject': '驳回',
    'transfer': '转办',
    'auto': '自动完成'
  }
  return texts[result] || result
}

// 搜索
function handleQuery() {
  queryParams.pageNum = 1
  if (activeTab.value === 'todo') {
    loadTodoList()
  } else {
    loadDoneList()
  }
}

// 重置
function resetQuery() {
  queryParams.processName = ''
  queryParams.taskName = ''
  queryParams.pageNum = 1
  handleQuery()
}

// 时间范围切换
function handleTimeRangeChange() {
  handleQuery()
}

// 分页
function handleSizeChange(val) {
  queryParams.pageSize = val
  handleQuery()
}

function handleCurrentChange(val) {
  queryParams.pageNum = val
  handleQuery()
}

// 监听标签切换
watch(activeTab, () => {
  queryParams.pageNum = 1
  if (activeTab.value === 'todo') {
    loadTodoList()
  } else {
    loadDoneList()
  }
})

onMounted(() => {
  loadStatistics()
  loadTodoList()
  loadUsers()
})
</script>

<style scoped>
.home-container {
  padding: 0;
}

.statistics-row {
  margin-bottom: 20px;
}

.stat-card {
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
  font-size: 30px;
  color: #fff;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.task-card {
  min-height: 500px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.tab-badge :deep(.el-badge__content) {
  margin-left: 5px;
}

.search-form {
  margin: 15px 0;
  padding-bottom: 15px;
  border-bottom: 1px solid #ebeef5;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.history-item {
  margin-bottom: 10px;
}

.history-title {
  font-weight: bold;
  font-size: 14px;
  color: #303133;
  margin-bottom: 5px;
}

.history-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
  margin-bottom: 5px;
}

.history-comment {
  font-size: 13px;
  color: #909399;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  margin-top: 5px;
}
</style>
