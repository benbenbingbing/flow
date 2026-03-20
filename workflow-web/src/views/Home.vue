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
          <el-radio-group v-if="activeTab !== 'started'" v-model="queryParams.timeRange" size="small" @change="handleTimeRangeChange">
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
        <el-form-item v-if="activeTab !== 'started'" label="任务名称">
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleApprove(row)">
              <el-icon><Check /></el-icon>审批
            </el-button>
            <el-button type="info" size="small" @click="viewProcessProgress(row)">
              <el-icon><View /></el-icon>进度
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已办列表 -->
      <el-table v-else-if="activeTab === 'done'" :data="doneList" v-loading="loading" stripe>
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

      <!-- 我发起的列表 -->
      <el-table v-else-if="activeTab === 'started'" :data="startedList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="processName" label="流程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="businessKey" label="业务编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="processInstanceId" label="流程实例" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="viewStartedProcessDetail(row)">
              {{ row.processInstanceId }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="currentNodeName" label="当前节点" min-width="120" show-overflow-tooltip />
        <el-table-column prop="startTime" label="发起时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160">
          <template #default="{ row }">
            <span>{{ row.endTime || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" @click="viewStartedProcessDetail(row)">
              <el-icon><View /></el-icon>查看
            </el-button>
            <el-button 
              v-if="row.status === 'RUNNING'" 
              type="danger" 
              size="small" 
              @click="handleTerminate(row)"
            >
              <el-icon><CircleClose /></el-icon>终止
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="activeTab === 'todo' ? todoTotal : activeTab === 'done' ? doneTotal : startedTotal"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 综合审批弹窗（4个Tab） -->
    <el-dialog
      v-model="approveDialogVisible"
      :title="activeTab === 'started' ? '流程详情' : (currentTask?.taskName || '任务审批')"
      width="900px"
      :close-on-click-modal="false"
      class="approve-dialog"
    >
      <el-tabs v-model="activeApproveTab" type="border-card">
        <!-- Tab 1: 任务审批 -->
        <el-tab-pane label="任务审批" name="approval">
          <!-- 申请信息 -->
          <el-card shadow="never" class="form-card">
            <template #header>
              <div class="card-header">
                <span>申请信息</span>
                <el-tag v-if="activeTab === 'started' || taskDetail?.formConfig?.isReadonly" type="info" size="small">只读</el-tag>
              </div>
            </template>
            <div v-if="taskDetail?.entityData && Object.keys(taskDetail.entityData).length > 0" class="entity-data-form">
              <!-- 调试输出 -->
              <div v-if="true" style="background:#f5f7fa;padding:10px;margin-bottom:10px;font-size:12px;">
                <div><strong>调试信息:</strong></div>
                <div>entityData keys: {{ taskDetail.entityData ? Object.keys(taskDetail.entityData) : 'null' }}</div>
                <div>fieldNameMap: {{ taskDetail.fieldNameMap }}</div>
                <div>formFields: {{ taskDetail?.formConfig?.fields?.map(f => ({code: f.fieldCode, name: f.fieldName})) }}</div>
              </div>
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
                    :label="taskDetail?.fieldNameMap?.[key] || key"
                  >
                    {{ value }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </div>
            <el-empty v-else-if="!taskDetail?.formConfig?.fields || taskDetail.formConfig.fields.length === 0" description="暂无申请数据" />
            <!-- 有表单配置但没有entityData时，显示空表单 -->
            <div v-else class="entity-data-form">
              <FormPreview 
                :form="previewFormConfig" 
                :model-value="{}"
                :readonly="true"
                :show-header="false"
              />
            </div>
          </el-card>
          
          <!-- 审批操作 -->
          <el-card v-if="activeTab !== 'started'" shadow="never" class="approve-card" style="margin-top: 20px;">
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
        </el-tab-pane>

        <!-- Tab 2: 流程图 -->
        <el-tab-pane label="流程图" name="diagram">
          <div ref="bpmnViewer" class="bpmn-viewer"></div>
        </el-tab-pane>

        <!-- Tab 3: 审批历史 -->
        <el-tab-pane label="审批历史" name="history">
          <el-timeline v-if="processHistory.length > 0">
            <el-timeline-item
              v-for="(item, index) in processHistory"
              :key="index"
              :type="item.type"
              :color="item.color"
              :timestamp="item.time"
            >
              <div class="timeline-content">
                <div class="timeline-title">{{ item.title }}</div>
                <div class="timeline-desc">{{ item.description }}</div>
                <div v-if="item.comment" class="timeline-comment">
                  <el-icon><ChatDotRound /></el-icon>
                  {{ item.comment }}
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审批历史" />
        </el-tab-pane>

        <!-- Tab 4: 流程信息 -->
        <el-tab-pane label="流程信息" name="info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="流程名称">{{ processInfo.processName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="流程实例">{{ processInfo.instanceId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前节点">{{ processInfo.currentNode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发起人">{{ processInfo.startUser || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发起时间">{{ processInfo.startTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务Key">{{ processInfo.businessKey || '-' }}</el-descriptions-item>
            <el-descriptions-item label="流程状态">
              <el-tag :type="processInfo.statusType">{{ processInfo.statusText }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
      
      <template #footer>
        <el-button @click="approveDialogVisible = false">关闭</el-button>
        <el-button v-if="activeApproveTab === 'approval' && activeTab !== 'started'" type="primary" @click="submitApprove" :loading="submitLoading">
          确认审批
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Check, Share, Timer, Search, Refresh, View, List, ChatDotRound, CircleClose } from '@element-plus/icons-vue'
import FormPreview from '@/components/FormPreview.vue'
import Viewer from 'bpmn-js/lib/Viewer'
import request from '@/utils/request'
import { getTodoList, getDoneList, getStatistics, getTaskDetail, completeTask, getProcessHistory, getMyStartedList, terminateProcess } from '@/api/processTask'
import { getUserList } from '@/api/system/user'

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
const startedList = ref([])
const todoTotal = ref(0)
const doneTotal = ref(0)
const startedTotal = ref(0)
const loading = ref(false)

// 用户选项
const userOptions = ref([])

// 审批弹窗（4个Tab）
const approveDialogVisible = ref(false)
const activeApproveTab = ref('approval')
const submitLoading = ref(false)
const currentTask = ref(null)
const currentInstanceId = ref('')
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

// BPMN Viewer
const bpmnViewer = ref(null)
let viewer = null
let eventBusListeners = []  // 保存事件监听器引用，用于清理

// 流程信息
const processInfo = ref({
  processName: '',
  instanceId: '',
  currentNode: '',
  startUser: '',
  startTime: '',
  businessKey: '',
  status: '',
  statusText: '',
  statusType: ''
})

// 审批历史
const processHistory = ref([])

// 获取统计数据
async function loadStatistics() {
  try {
    const res = await getStatistics()
    Object.assign(statistics, res)
  } catch (e) {
    console.error('加载统计数据失败:', e)
  }
}

// 获取待办列表
async function loadTodoList() {
  loading.value = true
  try {
    const res = await getTodoList({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    todoList.value = res.records || []
    todoTotal.value = res.total || 0
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
    const res = await getDoneList({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    doneList.value = res.records || []
    doneTotal.value = res.total || 0
  } catch (e) {
    console.error('加载已办列表失败:', e)
    ElMessage.error('加载已办列表失败')
  } finally {
    loading.value = false
  }
}

// 获取我发起的流程列表
async function loadStartedList() {
  loading.value = true
  try {
    const res = await getMyStartedList({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      processName: queryParams.processName
    })
    // PageResult 结构: { records, total, pageNum, pageSize }
    // 注意：request.js 拦截器已经解包了 Result，所以 res 就是 PageResult
    startedList.value = res?.records || []
    startedTotal.value = res?.total || 0
  } catch (e) {
    console.error('加载我发起的流程列表失败:', e)
    ElMessage.error('加载我发起的流程列表失败')
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

// 处理审批
async function handleApprove(row) {
  currentTask.value = row
  currentInstanceId.value = row.processInstanceId
  approveForm.action = 'approve'
  approveForm.comment = ''
  approveForm.transferTo = ''
  taskDetail.value = null
  activeApproveTab.value = 'approval'
  
  // 加载任务详情（包含表单和实体数据）
  try {
    const taskId = row.taskId || row.id
    if (taskId) {
      const res = await getTaskDetail(taskId)
      taskDetail.value = res
    }
  } catch (e) {
    console.error('加载任务详情失败:', e)
    ElMessage.error('加载任务详情失败')
  }
  
  // 加载流程详情（用于流程图、历史、流程信息）
  await loadProcessDetail(row.processInstanceId)
  
  approveDialogVisible.value = true
  
  // 在弹窗显示后渲染流程图
  nextTick(() => {
    if (activeApproveTab.value === 'diagram') {
      renderBpmn()
    }
  })
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
    await completeTask({
      taskId: currentTask.value.taskId,
      action: approveForm.action,
      comment: approveForm.comment,
      transferTo: approveForm.transferTo
    })
    
    ElMessage.success('审批成功')
    approveDialogVisible.value = false
    // 同时刷新待办和已办列表
    loadTodoList()
    loadDoneList()
    loadStatistics()
  } catch (e) {
    console.error('审批失败:', e)
    ElMessage.error('审批失败: ' + (e.message || '未知错误'))
  } finally {
    submitLoading.value = false
  }
}

// 查看流程进度（现在合并到审批弹窗）
async function viewProcessProgress(row) {
  currentTask.value = row
  currentInstanceId.value = row.processInstanceId
  taskDetail.value = null
  activeApproveTab.value = 'diagram'
  
  // 尝试加载任务详情
  try {
    const taskId = row.taskId || row.id
    if (taskId) {
      const res = await getTaskDetail(taskId)
      taskDetail.value = res
    }
  } catch (e) {
    console.error('加载任务详情失败:', e)
  }
  
  // 加载流程详情
  await loadProcessDetail(row.processInstanceId)
  
  approveDialogVisible.value = true
  
  // 在弹窗显示后渲染流程图
  nextTick(() => {
    renderBpmn()
  })
}

// 查看我发起的流程详情（只读模式）
async function viewStartedProcessDetail(row) {
  currentTask.value = row
  currentInstanceId.value = row.processInstanceId
  taskDetail.value = null
  activeApproveTab.value = 'approval'
  
  // 加载流程详情
  await loadProcessDetail(row.processInstanceId)
  
  approveDialogVisible.value = true
  
  // 在弹窗显示后渲染流程图
  nextTick(() => {
    if (activeApproveTab.value === 'diagram') {
      renderBpmn()
    }
  })
}

// 终止流程
async function handleTerminate(row) {
  try {
    await ElMessageBox.confirm(
      '确定要终止该流程吗？终止后流程将结束且不可恢复。', 
      '提示', 
      { type: 'warning' }
    )
    
    await terminateProcess(row.processInstanceId, '发起人主动终止')
    
    ElMessage.success('流程终止成功')
    loadStartedList()
    loadStatistics()
  } catch (error) {
    // ElMessageBox.confirm 取消时会抛出 'cancel' 字符串
    if (error === 'cancel' || (error && error.message === 'cancel')) {
      return // 用户取消，不处理
    }
    console.error('终止失败:', error)
    ElMessage.error('终止失败: ' + (error.message || '未知错误'))
  }
}

// 获取状态类型
function getStatusType(status) {
  const types = {
    'RUNNING': 'primary',
    'COMPLETED': 'success',
    'TERMINATED': 'danger',
    'SUSPENDED': 'warning'
  }
  return types[status] || 'info'
}

// 加载流程详情
async function loadProcessDetail(instanceId) {
  if (!instanceId) return
  
  try {
    const res = await request.get(`/process-instance/${instanceId}/detail`)
    if (res) {
      // 流程信息
      const statusMap = {
        'RUNNING': { text: '运行中', type: 'primary' },
        'COMPLETED': { text: '已完成', type: 'success' },
        'SUSPENDED': { text: '已挂起', type: 'warning' },
        'TERMINATED': { text: '已终止', type: 'danger' }
      }
      const status = statusMap[res.status] || { text: res.status || '未知', type: 'info' }
      
      processInfo.value = {
        processName: res.processName || currentTask.value?.processName || '-',
        instanceId: res.instanceId || instanceId,
        currentNode: res.currentNode || currentTask.value?.taskName || '-',
        startUser: res.startUser || '-',
        startTime: res.startTime || '-',
        businessKey: res.businessKey || '-',
        status: res.status || 'UNKNOWN',
        statusText: status.text,
        statusType: status.type
      }
      
      // 审批历史
      processHistory.value = (res.history || []).map(h => ({
        title: `${h.assignee || '系统'} ${h.action}`,
        description: h.taskName || '流程发起',
        time: h.endTime || h.startTime,
        comment: h.comment,
        type: h.action === '发起' ? 'primary' : h.action === '通过' ? 'success' : h.action === '驳回' ? 'danger' : 'info',
        color: h.action === '发起' ? '#409EFF' : h.action === '通过' ? '#67C23A' : h.action === '驳回' ? '#F56C6C' : '#909399'
      }))
      
      // 保存BPMN XML用于渲染
      bpmnXml.value = res.bpmnXml || ''
      completedNodes.value = res.completedNodes || []
      currentNodeId.value = res.currentNodeId || ''
      nodeAssigneeMap.value = res.nodeAssigneeMap || {}
    }
  } catch (e) {
    console.error('加载流程详情失败:', e)
  }
}

// BPMN XML 和节点信息
const bpmnXml = ref('')
const completedNodes = ref([])
const currentNodeId = ref('')
const nodeAssigneeMap = ref({})

// 清理BPMN事件监听器
function clearBpmnEventListeners() {
  if (!viewer) return
  const eventBus = viewer.get('eventBus')
  if (eventBus) {
    // 移除所有已注册的事件监听器
    eventBusListeners.forEach(listener => {
      eventBus.off('element.hover', listener.hover)
      eventBus.off('element.out', listener.out)
    })
    eventBusListeners = []
  }
}

// 渲染BPMN流程图
async function renderBpmn() {
  if (!bpmnXml.value || !bpmnViewer.value) return
  
  if (!viewer) {
    viewer = new Viewer({
      container: bpmnViewer.value
    })
  }
  
  try {
    // 先清理旧的事件监听器
    clearBpmnEventListeners()
    
    await viewer.importXML(bpmnXml.value)
    
    const canvas = viewer.get('canvas')
    const overlays = viewer.get('overlays')
    const elementRegistry = viewer.get('elementRegistry')
    const eventBus = viewer.get('eventBus')
    
    // 高亮已完成的节点
    completedNodes.value.forEach(nodeId => {
      canvas.addMarker(nodeId, 'completed')
    })
    
    // 高亮当前节点
    if (currentNodeId.value) {
      canvas.addMarker(currentNodeId.value, 'active')
    }
    
    // 添加节点悬停提示
    const tooltipOverlayId = 'node-tooltip'
    let currentElement = null

    elementRegistry.forEach(element => {
      if (element.type.includes('Task') || element.type.includes('Activity') || element.type.includes('Gateway') || element.type.includes('Event')) {
        const gfx = canvas.getGraphics(element)
        if (!gfx) return

        // 创建事件监听函数
        const hoverListener = (e) => {
          if (e.element.id !== element.id) return

          const assigneeInfo = nodeAssigneeMap.value[element.id]
          if (!assigneeInfo) return

          currentElement = element
          const statusText = assigneeInfo.status === 'completed' ? '已审批' : '待审批'
          const tooltipContent = `
            <div class="bpmn-tooltip">
              <div class="tooltip-title">${element.businessObject.name || element.id}</div>
              <div class="tooltip-row"><span class="label">状态:</span> <span class="value ${assigneeInfo.status}">${statusText}</span></div>
              <div class="tooltip-row"><span class="label">处理人:</span> <span class="value">${assigneeInfo.assigneeName || '-'}</span></div>
              ${assigneeInfo.handleTime ? `<div class="tooltip-row"><span class="label">时间:</span> <span class="value">${assigneeInfo.handleTime}</span></div>` : ''}
              ${assigneeInfo.comment ? `<div class="tooltip-row"><span class="label">意见:</span> <span class="value">${assigneeInfo.comment}</span></div>` : ''}
            </div>
          `

          // 移除已有的tooltip
          try {
            overlays.remove({ element: element.id, type: tooltipOverlayId })
          } catch (e) {}

          overlays.add(element.id, tooltipOverlayId, {
            position: {
              top: -10,
              left: 50
            },
            html: tooltipContent
          })
        }

        const outListener = (e) => {
          if (e.element.id !== element.id) return
          if (currentElement && currentElement.id === element.id) {
            try {
              overlays.remove({ element: element.id, type: tooltipOverlayId })
            } catch (e) {}
            currentElement = null
          }
        }

        // 注册事件监听
        eventBus.on('element.hover', hoverListener)
        eventBus.on('element.out', outListener)

        // 保存监听器引用，便于后续清理
        eventBusListeners.push({
          elementId: element.id,
          hover: hoverListener,
          out: outListener
        })
      }
    })
    
    // 适应视口
    canvas.zoom('fit-viewport', 'auto')
  } catch (err) {
    console.error('渲染流程图失败:', err)
  }
}

// 监听Tab切换，当切换到流程图Tab时渲染
watch(activeApproveTab, (val) => {
  if (val === 'diagram') {
    nextTick(() => {
      renderBpmn()
    })
  }
})

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
  } else if (activeTab.value === 'done') {
    loadDoneList()
  } else {
    loadStartedList()
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
  } else if (activeTab.value === 'done') {
    loadDoneList()
  } else {
    loadStartedList()
  }
})

onMounted(() => {
  loadStatistics()
  loadTodoList()
  loadUsers()
  loadStartedList()
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

/* BPMN 流程图样式 */
.bpmn-viewer {
  height: 500px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

/* 时间线样式 */
.timeline-content {
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.timeline-title {
  font-weight: 500;
  margin-bottom: 5px;
}

.timeline-desc {
  font-size: 13px;
  color: #606266;
  margin-bottom: 5px;
}

.timeline-comment {
  font-size: 13px;
  color: #909399;
  font-style: italic;
  display: flex;
  align-items: center;
  gap: 5px;
}
</style>

<style>
/* BPMN 样式覆盖 - 全局 */
.bpmn-viewer .completed rect,
.bpmn-viewer .completed circle,
.bpmn-viewer .completed polygon {
  stroke: #67c23a !important;
  stroke-width: 2px !important;
}

.bpmn-viewer .active rect,
.bpmn-viewer .active circle,
.bpmn-viewer .active polygon {
  stroke: #409eff !important;
  stroke-width: 3px !important;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { stroke-opacity: 1; }
  50% { stroke-opacity: 0.5; }
  100% { stroke-opacity: 1; }
}

/* 去掉 bpmn.io 水印 */
.bjs-powered-by {
  display: none !important;
}

/* BPMN 节点悬停提示样式 */
.bpmn-tooltip {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  font-size: 13px;
  min-width: 160px;
}

.bpmn-tooltip .tooltip-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #ebeef5;
}

.bpmn-tooltip .tooltip-row {
  display: flex;
  margin-bottom: 4px;
  line-height: 1.5;
}

.bpmn-tooltip .tooltip-row .label {
  color: #909399;
  min-width: 50px;
}

.bpmn-tooltip .tooltip-row .value {
  color: #606266;
  flex: 1;
}

.bpmn-tooltip .tooltip-row .value.completed {
  color: #67c23a;
  font-weight: 500;
}

.bpmn-tooltip .tooltip-row .value.processing {
  color: #409eff;
  font-weight: 500;
}
</style>
