<template>
  <div class="home-container">
    <el-alert v-if="statisticsError" type="error" :closable="false" show-icon class="statistics-error">
      <template #title>
        <span>统计数据加载失败：{{ statisticsError }}</span>
        <el-button link type="primary" @click="loadStatistics">重新加载</el-button>
      </template>
    </el-alert>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="statistics-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card
          class="stat-card stat-card--clickable"
          shadow="hover"
          role="button"
          tabindex="0"
          aria-label="查看待办任务"
          @click="activeTab = 'todo'"
          @keyup.enter="activeTab = 'todo'"
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
          @click="activeTab = 'done'"
          @keyup.enter="activeTab = 'done'"
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
          @click="activeTab = 'started'"
          @keyup.enter="activeTab = 'started'"
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
                </span>
              </template>
            </el-tab-pane>
            <el-tab-pane name="cc">
              <template #label>
                <span>
                  <el-icon><Bell /></el-icon>
                  {{ PRODUCT_TERMS.notificationsForMe }}
                  <el-badge v-if="statistics.unreadCcCount > 0" :value="statistics.unreadCcCount" class="tab-badge" />
                </span>
              </template>
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>

      <el-form :model="queryParams" inline class="task-filters" size="small">
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="标题、编码、流程或任务"
            clearable
            @keyup.enter="handleFilterSearch"
          />
        </el-form-item>
        <el-form-item label="发起人">
          <el-input
            v-model="queryParams.startUserName"
            placeholder="姓名或账号"
            clearable
            @keyup.enter="handleFilterSearch"
          />
        </el-form-item>
        <el-form-item v-if="activeTab === 'todo'" label="优先级">
          <el-select v-model="queryParams.priority" placeholder="全部" clearable style="width: 110px">
            <el-option label="紧急" value="URGENT" />
            <el-option label="高" value="HIGH" />
            <el-option label="普通" value="NORMAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleFilterSearch">查询</el-button>
          <el-button @click="handleFilterReset">重置</el-button>
          <el-button
            v-if="activeTab === 'todo'"
            :disabled="claimableSelectedCount === 0"
            :loading="bulkClaimLoading"
            @click="handleBatchClaim"
          >
            批量认领{{ claimableSelectedCount ? ` (${claimableSelectedCount})` : '' }}
          </el-button>
        </el-form-item>
      </el-form>

      <PageState
        v-if="activeError"
        type="error"
        :title="`${activeTabLabel}加载失败`"
        :description="activeError"
        retryable
        compact
        @retry="loadActiveTab(true)"
      />

      <!-- 待办列表 -->
      <el-table
        v-else-if="activeTab === 'todo'"
        :data="todoList"
        v-loading="loading"
        stripe
        empty-text="当前条件下没有待办任务"
        @selection-change="selectedTodoRows = $event"
      >
        <el-table-column type="selection" width="44" :selectable="row => row.claimRequired" />
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
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.claimRequired"
              type="primary"
              size="small"
              :loading="claimingTaskId === row.taskId"
              @click="handleClaim(row)"
            >
              认领
            </el-button>
            <template v-else>
              <el-button type="primary" size="small" @click="handleApprove(row)">审批</el-button>
              <template v-if="row.nodeType !== 'ADD_SIGN'">
                <el-button v-if="row.taskOperations?.transfer !== false" type="warning" size="small" @click="openTransferDialog(row)">转办</el-button>
                <el-button v-if="row.taskOperations?.addSign !== false" type="success" size="small" @click="openAddSignDialog(row)">加签</el-button>
                <el-button
                  v-else-if="row.taskOperations?.activeAddSign?.id"
                  type="danger"
                  plain
                  size="small"
                  @click="handleCancelAddSign(row)"
                >
                  撤销加签
                </el-button>
                <el-button v-if="row.taskOperations?.manualCc !== false" type="info" size="small" @click="openCcDialog(row)">知会</el-button>
              </template>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <!-- 已办列表 -->
      <el-table v-else-if="activeTab === 'done'" :data="doneList" v-loading="loading" stripe empty-text="当前条件下没有已办任务">
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
            <el-tag :type="getResultType(row.result)" size="small">
              {{ getResultLabel(row.result) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" @click="viewProgress(row)">进度</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 我发起的列表 -->
      <el-table v-else-if="activeTab === 'started'" :data="startedList" v-loading="loading" stripe empty-text="当前条件下没有我发起的流程">
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
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ row.statusText || getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" @click="viewProgress(row)">查看</el-button>
            <el-button v-if="row.status === 'RUNNING'" type="danger" size="small" @click="handleTerminate(row)">终止</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="ccList" v-loading="loading" stripe empty-text="当前条件下没有知会记录">
        <el-table-column prop="processName" label="流程名称" min-width="150" />
        <el-table-column prop="nodeName" label="知会节点" min-width="130" />
        <el-table-column prop="operatorName" label="知会人" width="140" />
        <el-table-column prop="comment" label="知会说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="知会时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.readStatus === 'READ' ? 'info' : 'danger'" size="small">
              {{ row.readStatus === 'READ' ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button v-if="row.readStatus !== 'READ'" link type="primary" @click="readCc(row)">标记已读</el-button>
            <el-button link type="info" @click="viewCc(row)">查看流程</el-button>
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

    <el-dialog v-model="addSignDialogVisible" title="任务加签" width="520px">
      <el-form label-width="90px">
        <el-form-item label="加签人员" required>
          <el-select v-model="addSignForm.userIds" multiple filterable style="width:100%" placeholder="请选择加签人员" @change="loadAddSignPreview">
            <el-option v-for="user in userOptions" :key="user.value" :label="user.label" :value="user.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="加签方式" required>
          <el-radio-group v-model="addSignForm.type" @change="loadAddSignPreview">
            <el-radio-button value="BEFORE">前加签</el-radio-button>
            <el-radio-button value="PARALLEL">并行加签</el-radio-button>
            <el-radio-button value="AFTER">后加签</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理方式">
          <el-alert :title="addSignPreview.structure || addSignTypeSummary" type="info" :closable="false" />
        </el-form-item>
        <el-alert
          v-if="addSignPreview.duplicates?.length || addSignPreview.disabled?.length || addSignPreview.invalid?.length"
          type="warning"
          :closable="false"
          show-icon
          :title="`重复 ${addSignPreview.duplicates?.length || 0} 人，停用 ${addSignPreview.disabled?.length || 0} 人，无效 ${addSignPreview.invalid?.length || 0} 人`"
        />
        <el-form-item label="说明">
          <el-input v-model="addSignForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addSignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="operationLoading" @click="submitAddSign">确认加签</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ccDialogVisible" title="人工知会" width="520px">
      <el-form label-width="90px">
        <el-form-item label="知会人员" required>
          <el-select v-model="ccForm.userIds" multiple filterable style="width:100%" placeholder="请选择知会人员">
            <el-option v-for="user in userOptions" :key="user.value" :label="user.label" :value="user.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="知会说明">
          <el-input v-model="ccForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ccDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="operationLoading" @click="submitCc">确认知会</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Check, Share, Timer } from '@element-plus/icons-vue'
import EntityApprovalDialog from '@/views/entity/components/approval/EntityApprovalDialog.vue'
import PageState from '@/components/PageState.vue'
import { PRODUCT_TERMS } from '@/constants/productTerminology'
import { getTodoList, getDoneList, getStatistics, completeTask, claimTask, getMyStartedList, terminateProcess, getTaskOperations, addSignTask, previewAddSign, cancelAddSign, ccTask, getMyCcList, markCcRead } from '@/api/processTask'
import { getUserList } from '@/api/system/user'

// 统计数据
const statistics = reactive({
  todoCount: 0,
  doneCount: 0,
  processCount: 0,
  avgProcessTime: 0,
  unreadCcCount: 0
})
const statisticsError = ref('')

// Tab 和分页
const activeTab = ref('todo')
const loading = ref(false)
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  startUserName: '',
  priority: '',
  dateRange: []
})
const tabErrors = reactive({ todo: '', done: '', started: '', cc: '' })
const loadedTabs = reactive({ todo: false, done: false, started: false, cc: false })
const activeError = computed(() => tabErrors[activeTab.value])
const activeTabLabel = computed(() => ({
  todo: '待办任务',
  done: '已办任务',
  started: '我发起的流程',
  cc: '知会记录'
}[activeTab.value]))

// 列表数据
const todoList = ref([])
const doneList = ref([])
const startedList = ref([])
const ccList = ref([])
const todoTotal = ref(0)
const doneTotal = ref(0)
const startedTotal = ref(0)
const ccTotal = ref(0)

const total = computed(() => {
  if (activeTab.value === 'todo') return todoTotal.value
  if (activeTab.value === 'done') return doneTotal.value
  if (activeTab.value === 'started') return startedTotal.value
  return ccTotal.value
})

// 用户选项
const userOptions = ref([])

// 审批弹窗
const approvalDialogRef = ref(null)
const claimingTaskId = ref('')
const selectedTodoRows = ref([])
const bulkClaimLoading = ref(false)
const claimableSelectedCount = computed(() =>
  selectedTodoRows.value.filter(row => row.claimRequired && row.taskId).length
)

// 转办弹窗
const transferDialogVisible = ref(false)
const transferLoading = ref(false)
const transferForm = reactive({
  taskId: '',
  transferTo: '',
  comment: ''
})
const addSignDialogVisible = ref(false)
const ccDialogVisible = ref(false)
const operationLoading = ref(false)
const addSignForm = reactive({ taskId: '', type: 'BEFORE', userIds: [], comment: '' })
const addSignPreview = reactive({ structure: '', duplicates: [], disabled: [], invalid: [] })
const addSignTypeSummary = computed(() => ({
  BEFORE: '加签人员先处理；全部通过后原办理人继续审批',
  PARALLEL: '原办理人与加签人员可并行提交；全部完成后流程继续',
  AFTER: '原办理人先提交；随后激活加签任务；全部完成后流程继续'
}[addSignForm.type]))
const ccForm = reactive({ taskId: '', userIds: [], comment: '' })

// 初始化
onMounted(() => {
  loadStatistics()
  loadTodoList()
})

// 监听 Tab 切换
watch(activeTab, () => {
  queryParams.pageNum = 1
  selectedTodoRows.value = []
  loadActiveTab()
})

function buildQueryParams() {
  const [startDate, endDate] = queryParams.dateRange || []
  return {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword || undefined,
    processName: queryParams.keyword || undefined,
    startUserName: queryParams.startUserName || undefined,
    priority: queryParams.priority || undefined,
    startDate,
    endDate
  }
}

function loadActiveTab(force = false) {
  if (!force && loadedTabs[activeTab.value]) return
  if (activeTab.value === 'todo') return loadTodoList()
  if (activeTab.value === 'done') return loadDoneList()
  if (activeTab.value === 'started') return loadStartedList()
  return loadCcList()
}

function handleFilterSearch() {
  queryParams.pageNum = 1
  loadedTabs[activeTab.value] = false
  loadActiveTab(true)
}

function handleFilterReset() {
  queryParams.keyword = ''
  queryParams.startUserName = ''
  queryParams.priority = ''
  queryParams.dateRange = []
  handleFilterSearch()
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
  statisticsError.value = ''
  try {
    const res = await getStatistics()
    Object.assign(statistics, res)
  } catch (e) {
    console.error('加载统计数据失败:', e)
    statisticsError.value = e?.message || '无法读取工作统计，请重试'
  }
}

// 加载待办
async function loadTodoList() {
  loading.value = true
  tabErrors.todo = ''
  try {
    const res = await getTodoList(buildQueryParams())
    todoList.value = res.records || []
    todoTotal.value = res.total || 0
    await loadTaskOperations(todoList.value)
    loadedTabs.todo = true
  } catch (e) {
    console.error('加载待办失败:', e)
    tabErrors.todo = e?.message || '无法读取待办任务，请重试'
  } finally {
    loading.value = false
  }
}

async function loadTaskOperations(tasks) {
  await Promise.all(tasks
    .filter(row => row.nodeType !== 'ADD_SIGN' && row.taskId)
    .map(async row => {
      try {
        row.taskOperations = await getTaskOperations(row.taskId)
      } catch (error) {
        console.warn('加载任务操作能力失败:', row.taskId, error)
        row.taskOperations = {}
      }
    }))
}

// 加载已办
async function loadDoneList() {
  loading.value = true
  tabErrors.done = ''
  try {
    const res = await getDoneList(buildQueryParams())
    doneList.value = res.records || []
    doneTotal.value = res.total || 0
    loadedTabs.done = true
  } catch (e) {
    console.error('加载已办失败:', e)
    tabErrors.done = e?.message || '无法读取已办任务，请重试'
  } finally {
    loading.value = false
  }
}

// 加载我发起的
async function loadStartedList() {
  loading.value = true
  tabErrors.started = ''
  try {
    const res = await getMyStartedList(buildQueryParams())
    startedList.value = res.records || res.list || []
    startedTotal.value = res.total || 0
    loadedTabs.started = true
  } catch (e) {
    console.warn('加载我发起的失败:', e)
    startedList.value = []
    startedTotal.value = 0
    tabErrors.started = e?.message || '无法读取我发起的流程，请重试'
  } finally {
    loading.value = false
  }
}

async function loadCcList() {
  loading.value = true
  tabErrors.cc = ''
  try {
    const res = await getMyCcList(buildQueryParams())
    ccList.value = Array.isArray(res) ? res : (res.records || [])
    ccTotal.value = Array.isArray(res) ? res.length : (res.total || 0)
    loadedTabs.cc = true
  } catch (e) {
    console.error('加载知会列表失败:', e)
    tabErrors.cc = e?.message || '无法读取知会记录，请重试'
  } finally {
    loading.value = false
  }
}

// 加载用户列表
async function loadUsers() {
  if (userOptions.value.length > 0) return
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

async function handleBatchClaim() {
  const tasks = selectedTodoRows.value.filter(row => row.claimRequired && row.taskId)
  if (!tasks.length) return
  try {
    await ElMessageBox.confirm(
      `将认领选中的 ${tasks.length} 个候选任务。认领后任务会分配给当前账号，其他候选人不再处理这些任务。`,
      '批量认领任务',
      {
        type: 'warning',
        confirmButtonText: '确认认领',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  bulkClaimLoading.value = true
  try {
    const results = await Promise.allSettled(tasks.map(row => claimTask(row.taskId)))
    const succeeded = results.filter(result => result.status === 'fulfilled').length
    const failed = results.length - succeeded
    if (failed) {
      ElMessage.warning(`已认领 ${succeeded} 个任务，${failed} 个任务认领失败或已被他人处理`)
    } else {
      ElMessage.success(`已认领 ${succeeded} 个任务`)
    }
    selectedTodoRows.value = []
    loadedTabs.todo = false
    await Promise.all([loadTodoList(), loadStatistics()])
  } finally {
    bulkClaimLoading.value = false
  }
}

// 审批
function handleApprove(row) {
  approvalDialogRef.value?.openApprove(row)
}

async function handleClaim(row) {
  if (!row.taskId || claimingTaskId.value) return
  claimingTaskId.value = row.taskId
  try {
    await claimTask(row.taskId)
    ElMessage.success('任务认领成功')
    await Promise.all([loadTodoList(), loadStatistics()])
  } catch (error) {
    console.error('认领任务失败:', error)
  } finally {
    claimingTaskId.value = ''
  }
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
async function openTransferDialog(row) {
  await loadUsers()
  transferForm.taskId = row.taskId
  transferForm.transferTo = ''
  transferForm.comment = ''
  transferDialogVisible.value = true
}

async function openAddSignDialog(row) {
  await loadUsers()
  Object.assign(addSignForm, { taskId: row.taskId, type: 'BEFORE', userIds: [], comment: '' })
  Object.assign(addSignPreview, { structure: '', duplicates: [], disabled: [], invalid: [] })
  addSignDialogVisible.value = true
}

async function loadAddSignPreview() {
  if (!addSignForm.taskId || !addSignForm.userIds.length) return
  const result = await previewAddSign(addSignForm.taskId, addSignForm.userIds, addSignForm.type)
  Object.assign(addSignPreview, result || {})
}

async function openCcDialog(row) {
  await loadUsers()
  Object.assign(ccForm, { taskId: row.taskId, userIds: [], comment: '' })
  ccDialogVisible.value = true
}

async function submitAddSign() {
  if (!addSignForm.userIds.length) return ElMessage.warning('请选择加签人员')
  operationLoading.value = true
  try {
    await addSignTask(addSignForm.taskId, {
      type: addSignForm.type,
      userIds: addSignForm.userIds,
      comment: addSignForm.comment,
      completionPolicy: 'ALL'
    })
    ElMessage.success('加签成功')
    addSignDialogVisible.value = false
    loadTodoList()
  } finally {
    operationLoading.value = false
  }
}

async function handleCancelAddSign(row) {
  const addSignId = row.taskOperations?.activeAddSign?.id
  if (!addSignId) return
  try {
    await ElMessageBox.confirm('撤销后，尚未处理的加签待办会全部取消。确定继续吗？', '撤销加签', { type: 'warning' })
    operationLoading.value = true
    await cancelAddSign(addSignId)
    ElMessage.success('加签已撤销')
    await loadTodoList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('撤销加签失败:', error)
    }
  } finally {
    operationLoading.value = false
  }
}

async function submitCc() {
  if (!ccForm.userIds.length) return ElMessage.warning('请选择知会人员')
  operationLoading.value = true
  try {
    await ccTask(ccForm.taskId, { userIds: ccForm.userIds, comment: ccForm.comment })
    ElMessage.success('知会成功')
    ccDialogVisible.value = false
  } finally {
    operationLoading.value = false
  }
}

async function readCc(row) {
  await markCcRead(row.id)
  row.readStatus = 'READ'
  if (statistics.unreadCcCount > 0) statistics.unreadCcCount--
}

function viewCc(row) {
  approvalDialogRef.value?.openView(row, { defaultTab: 'diagram' })
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
    await ElMessageBox.confirm(
      '终止后流程将直接结束，相关待办也会取消，且不能从当前节点继续。确认终止吗？',
      '终止流程',
      {
        type: 'warning',
        confirmButtonText: '确认终止',
        cancelButtonText: '取消'
      }
    )
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

function getStatusLabel(status) {
  return {
    RUNNING: '进行中',
    COMPLETED: '已完成',
    TERMINATED: '已终止',
    SUSPENDED: '已暂停'
  }[status] || '未知状态'
}

function getResultLabel(result) {
  return {
    approve: '通过',
    reject: '驳回',
    transfer: '转办'
  }[result] || '其他结果'
}

function getResultType(result) {
  return {
    approve: 'success',
    reject: 'danger',
    transfer: 'warning'
  }[result] || 'info'
}

// 分页
function handleSizeChange(val) {
  queryParams.pageSize = val
  loadedTabs[activeTab.value] = false
  loadActiveTab(true)
}

function handleCurrentChange(val) {
  queryParams.pageNum = val
  loadedTabs[activeTab.value] = false
  loadActiveTab(true)
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

.statistics-error {
  margin-bottom: 12px;
}

.statistics-error :deep(.el-alert__title) {
  display: flex;
  align-items: center;
  gap: 8px;
}

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

/* 任务卡片 */
.task-card {
  margin-top: 20px;
}

.task-filters {
  margin-bottom: 12px;
}

@media (max-width: 760px) {
  .home-container {
    padding: 0;
  }

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

  .task-card {
    margin-top: 0;
  }

  .task-filters {
    display: grid;
    padding: 0 8px;
  }

  .task-filters :deep(.el-form-item),
  .task-filters :deep(.el-input),
  .task-filters :deep(.el-date-editor) {
    width: 100%;
  }
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
