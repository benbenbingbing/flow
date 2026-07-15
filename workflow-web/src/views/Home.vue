<template>
  <div class="home-container">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="statistics-row">
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover" @click="activeTab = 'todo'">
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
        <el-card class="stat-card" shadow="hover" @click="activeTab = 'done'">
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
        <el-card class="stat-card" shadow="hover" @click="activeTab = 'started'">
          <div class="stat-icon" style="background-color: #409eff;">
            <el-icon><Share /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.processCount }}</div>
            <div class="stat-label">我发起的</div>
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
            <el-tab-pane name="started">
              <template #label>
                <span>
                  <el-icon><Share /></el-icon>
                  我发起的
                  <el-badge v-if="startedTotal > 0" :value="startedTotal" class="tab-badge" />
                </span>
              </template>
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>

      <!-- 待办列表 -->
      <el-table v-if="activeTab === 'todo'" :data="todoList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="processName" label="流程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="currentTaskName" label="任务名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="startUserName" label="发起人" width="140" />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.priority >= 80" type="danger" size="small">紧急</el-tag>
            <el-tag v-else-if="row.priority >= 50" type="warning" size="small">高</el-tag>
            <el-tag v-else type="info" size="small">普通</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleApprove(row)">审批</el-button>
            <el-button type="warning" size="small" @click="openTransferDialog(row)">转办</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已办列表 -->
      <el-table v-else-if="activeTab === 'done'" :data="doneList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="processName" label="流程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="currentTaskName" label="任务名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="startUserName" label="发起人" width="140" />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="处理时间" width="160">
          <template #default="{ row }">{{ formatDate(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="result" label="处理结果" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.result === 'approve'" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="row.result === 'reject'" type="danger" size="small">驳回</el-tag>
            <el-tag v-else-if="row.result === 'transfer'" type="warning" size="small">转办</el-tag>
            <el-tag v-else type="info" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" @click="viewProgress(row)">进度</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 我发起的列表 -->
      <el-table v-else-if="activeTab === 'started'" :data="startedList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="processName" label="流程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="startUserName" label="发起人" width="140" />
        <el-table-column prop="currentNodeName" label="当前节点" min-width="120" show-overflow-tooltip />
        <el-table-column label="发起时间" width="160">
          <template #default="{ row }">{{ formatDate(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" @click="viewProgress(row)">查看</el-button>
            <el-button v-if="row.status === 'RUNNING'" type="danger" size="small" @click="handleTerminate(row)">终止</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 审批/查看弹窗 -->
    <EntityApprovalDialog
      ref="approvalDialogRef"
      @success="onApprovalSuccess"
    />

    <!-- 转办弹窗 -->
    <el-dialog v-model="transferDialogVisible" title="任务转办" width="400px" :close-on-click-modal="false">
      <el-form :model="transferForm" label-width="80px">
        <el-form-item label="转办人" required>
          <el-select v-model="transferForm.transferTo" placeholder="请选择转办人" filterable clearable style="width: 100%">
            <el-option v-for="user in userOptions" :key="user.value" :label="user.label" :value="user.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="transferForm.comment" type="textarea" :rows="3" placeholder="请输入转办备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTransfer" :loading="transferLoading">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Check, Share, Timer } from '@element-plus/icons-vue'
import EntityApprovalDialog from '@/views/entity/components/approval/EntityApprovalDialog.vue'
import { getTodoList, getDoneList, getStatistics, completeTask, getMyStartedList, terminateProcess } from '@/api/processTask'
import { getUserList } from '@/api/system/user'

// 统计数据
const statistics = reactive({
  todoCount: 0,
  doneCount: 0,
  processCount: 0,
  avgProcessTime: 0
})

// Tab 和分页
const activeTab = ref('todo')
const loading = ref(false)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

// 列表数据
const todoList = ref([])
const doneList = ref([])
const startedList = ref([])
const todoTotal = ref(0)
const doneTotal = ref(0)
const startedTotal = ref(0)

const total = computed(() => {
  if (activeTab.value === 'todo') return todoTotal.value
  if (activeTab.value === 'done') return doneTotal.value
  return startedTotal.value
})

// 用户选项
const userOptions = ref([])

// 审批弹窗
const approvalDialogRef = ref(null)

// 转办弹窗
const transferDialogVisible = ref(false)
const transferLoading = ref(false)
const transferForm = reactive({
  taskId: '',
  transferTo: '',
  comment: ''
})

// 初始化
onMounted(() => {
  loadStatistics()
  loadTodoList()
  loadDoneList()
  loadStartedList()
  loadUsers()
})

// 监听 Tab 切换
watch(activeTab, () => {
  queryParams.pageNum = 1
  if (activeTab.value === 'todo') loadTodoList()
  else if (activeTab.value === 'done') loadDoneList()
  else loadStartedList()
})

// 格式化日期时间
function formatDate(dateStr) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return dateStr
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// 加载统计数据
async function loadStatistics() {
  try {
    const res = await getStatistics()
    Object.assign(statistics, res)
  } catch (e) {
    console.error('加载统计数据失败:', e)
  }
}

// 加载待办
async function loadTodoList() {
  loading.value = true
  try {
    const res = await getTodoList(queryParams)
    todoList.value = res.records || []
    todoTotal.value = res.total || 0
  } catch (e) {
    console.error('加载待办失败:', e)
  } finally {
    loading.value = false
  }
}

// 加载已办
async function loadDoneList() {
  loading.value = true
  try {
    const res = await getDoneList(queryParams)
    doneList.value = res.records || []
    doneTotal.value = res.total || 0
  } catch (e) {
    console.error('加载已办失败:', e)
  } finally {
    loading.value = false
  }
}

// 加载我发起的
async function loadStartedList() {
  loading.value = true
  try {
    const res = await getMyStartedList(queryParams)
    startedList.value = res.records || res.list || []
    startedTotal.value = res.total || 0
  } catch (e) {
    console.warn('加载我发起的失败，已显示空列表:', e)
    startedList.value = []
    startedTotal.value = 0
  } finally {
    loading.value = false
  }
}

// 加载用户列表
async function loadUsers() {
  try {
    const res = await getUserList()
    userOptions.value = res.map(user => ({
      label: `${user.nickname || user.username} (${user.username})`,
      value: user.username
    }))
  } catch (e) {
    console.error('加载用户列表失败:', e)
  }
}

// 审批
function handleApprove(row) {
  approvalDialogRef.value?.openApprove(row)
}

// 查看进度
function viewProgress(row) {
  approvalDialogRef.value?.openView(row, { defaultTab: 'diagram', startUserName: row.startUserName })
}

// 审批成功回调
function onApprovalSuccess() {
  loadTodoList()
  loadDoneList()
  loadStatistics()
}

// 打开转办弹窗
function openTransferDialog(row) {
  transferForm.taskId = row.taskId
  transferForm.transferTo = ''
  transferForm.comment = ''
  transferDialogVisible.value = true
}

// 提交转办
async function submitTransfer() {
  if (!transferForm.transferTo) {
    ElMessage.warning('请选择转办人')
    return
  }
  transferLoading.value = true
  try {
    await completeTask({
      taskId: transferForm.taskId,
      action: 'transfer',
      comment: transferForm.comment,
      transferTo: transferForm.transferTo
    })
    ElMessage.success('转办成功')
    transferDialogVisible.value = false
    loadTodoList()
    loadDoneList()
    loadStatistics()
  } catch (e) {
    console.error('转办失败:', e)
    ElMessage.error('转办失败')
  } finally {
    transferLoading.value = false
  }
}

// 终止流程
async function handleTerminate(row) {
  try {
    await ElMessageBox.confirm('确定要终止该流程吗？终止后流程将直接结束，相关待办也会取消。', '提示', { type: 'warning' })
    await terminateProcess(row.processInstanceId, '发起人主动终止')
    ElMessage.success('终止成功')
    loadStartedList()
    loadStatistics()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('终止失败:', e)
      ElMessage.error('终止失败')
    }
  }
}

// 获取状态类型
function getStatusType(status) {
  const types = { 'RUNNING': 'primary', 'COMPLETED': 'success', 'TERMINATED': 'danger', 'SUSPENDED': 'warning' }
  return types[status] || 'info'
}

// 分页
function handleSizeChange(val) {
  queryParams.pageSize = val
  if (activeTab.value === 'todo') loadTodoList()
  else if (activeTab.value === 'done') loadDoneList()
  else loadStartedList()
}

function handleCurrentChange(val) {
  queryParams.pageNum = val
  if (activeTab.value === 'todo') loadTodoList()
  else if (activeTab.value === 'done') loadDoneList()
  else loadStartedList()
}
</script>

<style scoped>
.home-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100%;
}

/* 统计卡片 */
.statistics-row {
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
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

/* 任务卡片 */
.task-card {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.tab-badge {
  margin-left: 5px;
}

.tab-badge :deep(.el-badge__content) {
  border: none;
}

/* 分页 */
.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
