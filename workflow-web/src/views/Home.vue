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
        <el-table-column prop="startUserName" label="发起人" width="100" />
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
        <el-table-column prop="startUserName" label="发起人" width="100" />
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

    <!-- 审批弹窗 -->
    <el-dialog v-model="dialogVisible" :title="`${currentTask?.name || '任务审批'}（${getProcessStatusText(currentTask?.processStatus)}）`" width="700px">
      <el-tabs v-model="activeDialogTab" type="border-card">
        <!-- 审批信息 -->
        <el-tab-pane label="审批" name="approval">
          <!-- 实体数据表单 -->
          <div v-if="entityData" class="entity-form-section">
            <template v-if="formConfig && formConfig.fields && formConfig.fields.length > 0">
              <!-- 使用节点配置的表单渲染 -->
              <FormPreviewLinkage
                :form="formConfig"
                v-model="entityData"
                :readonly="true"
                :show-header="false"
              />
            </template>
            <template v-else>
              <!-- 默认显示 -->
              <el-form :model="entityData" label-width="100px" class="entity-form">
                <el-row :gutter="20">
                  <el-col v-for="(value, key) in entityData" :key="key" :span="12">
                    <el-form-item :label="key">
                      <el-input v-model="entityData[key]" :readonly="true" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </template>
          </div>
          
          <el-divider v-if="entityData" />
          
          <template v-if="effectiveApprovalConfig.enabled !== false">
            <div class="section-title">审批意见</div>
            <el-form :model="approveForm" label-width="80px">
              <el-form-item label="审批操作" required>
                <el-radio-group v-model="approveForm.action">
                  <el-radio-button 
                    v-for="option in effectiveApprovalConfig.options" 
                    :key="option.value" 
                    :label="option.value"
                  >
                    {{ option.label }}
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item 
                v-if="effectiveApprovalConfig.options.find(o => o.value === approveForm.action)?.showComment !== false"
                :label="effectiveApprovalConfig.commentLabel || '审批备注'"
              >
                <el-input 
                  v-model="approveForm.comment" 
                  type="textarea" 
                  :rows="3" 
                  :placeholder="`请输入${effectiveApprovalConfig.commentLabel || '审批备注'}`" 
                />
              </el-form-item>
            </el-form>
          </template>
        </el-tab-pane>

        <!-- 流程图 -->
        <el-tab-pane label="流程图" name="diagram">
          <div style="height: 400px;">
            <VueBpmnViewer 
              v-if="bpmnXml && progressData" 
              :key="currentTask?.processInstanceId"
              :xml="bpmnXml" 
              :progress-data="progressData"
              style="height: 100%;" 
            />
            <el-empty v-else description="暂无流程图" />
          </div>
        </el-tab-pane>

        <!-- 审批历史 -->
        <el-tab-pane label="审批历史" name="history">
          <el-timeline v-if="processHistory.length > 0">
            <el-timeline-item
              v-for="(item, index) in processHistory"
              :key="index"
              :type="item.type"
              :timestamp="item.time"
            >
              <div class="history-item">
                <span class="history-title">{{ item.title }}</span>
                <el-tag size="small" :type="item.status === 'COMPLETED' ? 'success' : 'warning'">
                  {{ item.status === 'COMPLETED' ? '已完成' : '进行中' }}
                </el-tag>
              </div>
              <div class="history-desc">{{ item.description }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审批历史" />
        </el-tab-pane>
      </el-tabs>
      
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button v-if="activeDialogTab === 'approval'" type="primary" @click="submitApprove" :loading="submitLoading">确认</el-button>
      </template>
    </el-dialog>

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
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Check, Share, Timer } from '@element-plus/icons-vue'
import VueBpmnViewer from '@/components/VueBpmnViewer.vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import { getTodoList, getDoneList, getStatistics, completeTask, getMyStartedList, terminateProcess, getProcessHistory } from '@/api/processTask'
import { getUserList } from '@/api/system/user'
import request from '@/utils/request'

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
const dialogVisible = ref(false)
const activeDialogTab = ref('approval')
const submitLoading = ref(false)
const currentTask = ref(null)
const approveForm = reactive({
  action: 'approve',
  comment: '',
  transferTo: ''
})
const bpmnXml = ref('')
const processHistory = ref([])
const progressData = ref({
  completedNodes: [],
  activeNodes: [],
  executedSequenceFlows: [],
  nodeAssigneeMap: {}
})
const entityData = ref(null)
const formConfig = ref(null)
const approvalConfig = ref(null)

// 计算属性：获取当前有效的审批配置（带默认值 fallback）
const effectiveApprovalConfig = computed(() => {
  if (approvalConfig.value) {
    return approvalConfig.value
  }
  // 默认配置
  return {
    enabled: true,
    commentLabel: '审批意见',
    options: [
      { value: 'approve', label: '通过', type: 'primary', showComment: true },
      { value: 'reject', label: '驳回', type: 'danger', showComment: true }
    ]
  }
})

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

// 监听审批弹窗 Tab 切换，切换到流程图时重新触发渲染
watch(activeDialogTab, (newVal) => {
  if (newVal === 'diagram' && bpmnXml.value && progressData.value) {
    // 强制重新渲染流程图
    nextTick(() => {
      const tempXml = bpmnXml.value
      bpmnXml.value = ''
      nextTick(() => {
        bpmnXml.value = tempXml
      })
    })
  }
})

// 获取流程状态标签类型
function getProcessStatusType(status) {
  const typeMap = {
    'RUNNING': 'primary',
    'COMPLETED': 'success',
    'SUSPENDED': 'warning',
    'TERMINATED': 'danger'
  }
  return typeMap[status] || 'info'
}

// 获取流程状态显示文本
function getProcessStatusText(status) {
  const textMap = {
    'RUNNING': '运行中',
    'COMPLETED': '已完成',
    'SUSPENDED': '已挂起',
    'TERMINATED': '已终止'
  }
  return textMap[status] || status || '-'
}

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
    startedList.value = res.records || []
    startedTotal.value = res.total || 0
  } catch (e) {
    console.error('加载我发起的失败:', e)
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
  currentTask.value = row
  approveForm.action = 'approve'
  approveForm.comment = ''
  approveForm.transferTo = ''
  activeDialogTab.value = 'approval'
  loadProcessDetail(row.processInstanceId)
  dialogVisible.value = true
}

// 查看进度
function viewProgress(row) {
  currentTask.value = row
  activeDialogTab.value = 'diagram'
  loadProcessDetail(row.processInstanceId)
  dialogVisible.value = true
}

// 加载流程详情
async function loadProcessDetail(instanceId) {
  try {
    // 加载流程进度（包含XML、节点状态等）
    const progressRes = await request.get(`/process-instance/${instanceId}/progress`)
    if (progressRes) {
      bpmnXml.value = progressRes.bpmnXml || ''
      progressData.value = {
        completedNodes: progressRes.completedNodes || [],
        activeNodes: progressRes.activeNodes || [],
        executedSequenceFlows: progressRes.executedSequenceFlows || [],
        nodeAssigneeMap: progressRes.nodeAssigneeMap || {}
      }
      // 保存实体数据和表单配置
      entityData.value = progressRes.entityData || null
      // 状态编码转中文名称
      if (entityData.value && entityData.value.status) {
        const statusMap = {
          'DRAFT': '草稿',
          'PENDING': '审批中',
          'APPROVED': '已通过',
          'REJECTED': '已驳回',
          'COMPLETED': '已完成',
          'WITHDRAWN': '已撤回'
        }
        entityData.value.status = statusMap[entityData.value.status] || entityData.value.status
      }
      formConfig.value = progressRes.formConfig || null
      approvalConfig.value = progressRes.approvalConfig || null
      // 同步审批操作默认值为第一个选项
      const config = progressRes.approvalConfig
      if (config && Array.isArray(config.options) && config.options.length > 0) {
        const firstOption = config.options[0]
        if (firstOption && firstOption.value) {
          approveForm.action = firstOption.value
        }
      }
      // 更新当前任务的状态和流程名称
      if (currentTask.value) {
        currentTask.value.processStatus = progressRes.status
        if (progressRes.processName) {
          currentTask.value.processName = progressRes.processName
        }
      }
    }
    
    // 加载历史 - 使用 nodeHistory 如果存在
    if (progressRes?.nodeHistory && progressRes.nodeHistory.length > 0) {
      processHistory.value = progressRes.nodeHistory.map(node => {
        const isStartNode = node.nodeId?.toLowerCase().includes('start') || node.nodeName === '开始'
        // 判断操作类型
        let actionText = ''
        if (node.action === 'APPROVED') actionText = '通过'
        else if (node.action === 'REJECTED') actionText = '驳回'
        else if (node.action === 'TRANSFERRED') actionText = '转办'
        else if (node.status === 'COMPLETED') actionText = '完成'
        else actionText = '进行中'

        const commentText = node.comment ? `（${node.comment}）` : ''
        return {
          title: node.nodeName || node.nodeId,
          description: isStartNode
            ? `发起人: ${node.assignee || currentTask.value?.startUserName || 'admin'}`
            : (node.assignee ? `执行人: ${node.assignee} ${actionText}${commentText}` : `${actionText}${commentText}`),
          time: node.endTime || node.startTime,
          type: node.action === 'TRANSFERRED' ? 'warning' : (node.status === 'COMPLETED' ? 'success' : 'primary'),
          status: node.status,
          action: node.action
        }
      }).reverse()
    } else {
      // 回退到单独查询历史
      const historyRes = await getProcessHistory(instanceId)
      processHistory.value = (historyRes || []).map(h => {
        const isStart = h.action === '发起' || h.taskName?.toLowerCase().includes('start')
        const isTransfer = h.result === 'transfer' || (h.comment && h.comment.includes('转办'))
        return {
          title: h.taskName || '流程节点',
          description: isStart
            ? `发起人: ${h.assignee || currentTask.value?.startUserName || 'admin'}`
            : `${h.assignee || '系统'} ${isTransfer ? '转办' : (h.action || '处理')}`,
          time: h.endTime || h.startTime,
          type: isStart ? 'primary' : (isTransfer ? 'warning' : (h.action === '通过' ? 'success' : 'info')),
          status: h.endTime ? 'COMPLETED' : 'ACTIVE',
          action: h.result
        }
      }).reverse()
    }
  } catch (e) {
    console.error('加载流程详情失败:', e)
  }
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

// 提交审批
async function submitApprove() {
  submitLoading.value = true
  try {
    await completeTask({
      taskId: currentTask.value.taskId,
      action: approveForm.action,
      comment: approveForm.comment
    })
    ElMessage.success('审批成功')
    dialogVisible.value = false
    loadTodoList()
    loadDoneList()
    loadStatistics()
  } catch (e) {
    console.error('审批失败:', e)
    ElMessage.error('审批失败')
  } finally {
    submitLoading.value = false
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

/* 时间线 */
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
}

.timeline-title {
  font-weight: bold;
  color: #303133;
}

.timeline-desc {
  color: #606266;
  font-size: 14px;
  margin-top: 5px;
}

/* 实体表单样式 */
.entity-form-section {
  margin: 16px 0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-left: 12px;
  border-left: 4px solid #409eff;
}

.entity-form {
  background: #fafbfc;
  padding: 16px;
  border-radius: 8px;
}

.entity-form .el-form-item {
  margin-bottom: 16px;
}

.entity-form .el-input__wrapper,
.entity-form .el-input-number,
.entity-form .el-select {
  width: 100%;
}
</style>
