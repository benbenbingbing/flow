<template>
  <div class="process-progress">
    <div class="progress-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="process-name">{{ progressData.processName || '流程进度' }}</span>
        <el-tag :type="getStatusType(progressData.status)" size="small">
          {{ getStatusText(progressData.status) }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button v-if="userStore.isSuperAdmin" @click="actionExecutionVisible = true">动作执行记录</el-button>
        <div class="legend">
          <div class="legend-item">
            <span class="legend-color completed"></span>
            <span>已完成</span>
          </div>
          <div class="legend-item">
            <span class="legend-color active"></span>
            <span>进行中</span>
          </div>
          <div class="legend-item">
            <span class="legend-color pending"></span>
            <span>未开始</span>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="actionExecutionVisible" title="流程动作执行记录" width="1100px">
      <FlowActionExecutionLog
        :process-instance-id="processInstanceId"
        :active="actionExecutionVisible"
      />
    </el-dialog>
    
    <div class="progress-container">
      <div class="canvas-wrapper">
        <VueBpmnViewer ref="viewerRef" :xml="bpmnXml" navigated @imported="onViewerImported" class="canvas" />
        
        <!-- 节点悬停提示框 -->
        <div v-if="tooltip.visible" 
             class="node-tooltip" 
             :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }">
          <div class="tooltip-header">
            <span class="node-status-dot" :class="tooltip.status"></span>
            <span class="node-name">{{ tooltip.nodeName }}</span>
          </div>
          <div class="tooltip-content">
            <template v-if="tooltip.assigneeInfo">
              <div class="info-row">
                <span class="label">{{ tooltip.status === 'active' ? '当前处理人' : '审批人' }}：</span>
                <span class="value">{{ tooltip.assigneeInfo.assigneeName || tooltip.assigneeInfo.assigneeId || '未分配' }}</span>
              </div>
              <div class="info-row">
                <span class="label">{{ tooltip.status === 'active' ? '到达时间' : '处理时间' }}：</span>
                <span class="value">{{ tooltip.assigneeInfo.handleTime || '-' }}</span>
              </div>
              <template v-if="tooltip.historyInfo">
                <div v-if="tooltip.historyInfo.startTime && tooltip.historyInfo.startTime !== tooltip.assigneeInfo.handleTime" class="info-row">
                  <span class="label">开始时间：</span>
                  <span class="value">{{ tooltip.historyInfo.startTime }}</span>
                </div>
                <div v-if="tooltip.historyInfo.endTime" class="info-row">
                  <span class="label">结束时间：</span>
                  <span class="value">{{ tooltip.historyInfo.endTime }}</span>
                </div>
                <div v-if="tooltip.historyInfo.duration" class="info-row">
                  <span class="label">耗时：</span>
                  <span class="value">{{ formatDuration(tooltip.historyInfo.duration) }}</span>
                </div>
              </template>
              <div v-if="tooltip.assigneeInfo.action && tooltip.assigneeInfo.action !== 'PROCESSING'" class="info-row">
                <span class="label">处理结果：</span>
                <el-tag size="small" :type="getActionType(tooltip.assigneeInfo.action)">
                  {{ getActionText(tooltip.assigneeInfo.action, tooltip.assigneeInfo.actionLabel) }}
                </el-tag>
              </div>
              <div v-if="tooltip.assigneeInfo.comment" class="info-row">
                <span class="label">审批意见：</span>
                <span class="value comment">{{ tooltip.assigneeInfo.comment }}</span>
              </div>
            </template>
            <template v-else>
              <div class="info-row">
                <span class="label">状态：</span>
                <span class="value">{{ tooltip.status === 'completed' ? '已完成' : tooltip.status === 'active' ? '进行中' : '未开始' }}</span>
              </div>
              <template v-if="tooltip.historyInfo">
                <div v-if="tooltip.historyInfo.startTime" class="info-row">
                  <span class="label">开始时间：</span>
                  <span class="value">{{ tooltip.historyInfo.startTime }}</span>
                </div>
                <div v-if="tooltip.historyInfo.endTime" class="info-row">
                  <span class="label">结束时间：</span>
                  <span class="value">{{ tooltip.historyInfo.endTime }}</span>
                </div>
                <div v-if="tooltip.historyInfo.duration" class="info-row">
                  <span class="label">耗时：</span>
                  <span class="value">{{ formatDuration(tooltip.historyInfo.duration) }}</span>
                </div>
              </template>
            </template>
          </div>
        </div>
      </div>
      
      <!-- 右侧任务信息面板 -->
      <div class="info-panel">
        <div class="panel-title">流程信息</div>
        <div class="panel-content">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="流程实例">{{ progressData.processInstanceId }}</el-descriptions-item>
            <el-descriptions-item label="流程标识">{{ progressData.processKey }}</el-descriptions-item>
            <el-descriptions-item label="流程名称">{{ progressData.processName }}</el-descriptions-item>
          </el-descriptions>
          
          <div class="section-title">当前任务</div>
          <div v-if="progressData.tasks && progressData.tasks.length > 0">
            <el-card v-for="task in progressData.tasks" :key="task.taskId" class="task-card" shadow="hover">
              <div class="task-name">{{ task.taskName }}</div>
              <div class="task-info">
                <span>处理人: {{ task.assigneeName || task.assignee || '未分配' }}</span>
                <span class="task-time">{{ task.createTime }}</span>
              </div>
            </el-card>
          </div>
          <el-empty v-else description="暂无进行中的任务" />
          
          <div class="section-title">执行历史</div>
          <el-timeline v-if="progressData.nodeHistory && progressData.nodeHistory.length > 0">
            <el-timeline-item
              v-for="(node, index) in progressData.nodeHistory"
              :key="index"
              :type="getNodeTimelineType(node)"
              :icon="getNodeTimelineIcon(node)"
              :timestamp="node.endTime || node.startTime"
            >
              <div class="history-item">
                <span class="node-name">{{ node.nodeName || node.nodeId }}</span>
                <el-tag size="small" :type="node.status === 'COMPLETED' ? 'success' : 'warning'">
                  {{ node.status === 'COMPLETED' ? '已完成' : '进行中' }}
                </el-tag>
              </div>
              <div v-if="node.assignee || node.assigneeName" class="assignee-info">
                执行人: {{ node.assigneeName || node.assignee }}
              </div>
              <div v-if="node.duration" class="duration-info">
                耗时: {{ formatDuration(node.duration) }}
              </div>
              <div v-if="node.action" class="action-info">
                处理结果:
                <el-tag size="small" :type="getActionType(node.action)">
                  {{ getActionText(node.action, node.actionLabel) }}
                </el-tag>
              </div>
              <div v-if="node.comment" class="comment-info">
                <span class="comment-label">审批意见:</span>
                <span>{{ node.comment }}</span>
              </div>
              <div v-if="Object.keys(getDisplayVariables(node)).length > 0" class="node-variables">
                <el-tag v-for="(val, key) in getDisplayVariables(node)" :key="key" size="small" type="info" class="var-tag">
                  {{ key }}: {{ val }}
                </el-tag>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无执行历史" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, toRaw } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { layoutProcess } from 'bpmn-auto-layout'
import { processApi } from '@/api/process'
import VueBpmnViewer from '@/components/VueBpmnViewer.vue'
import FlowActionExecutionLog from '@/components/FlowActionExecutionLog.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const processInstanceId = route.params.instanceId

const viewerRef = ref()
const actionExecutionVisible = ref(false)
const bpmnXml = ref('')
let currentViewer = null
const progressData = ref({
  completedNodes: [],
  activeNodes: [],
  executedSequenceFlows: [],
  nodeHistory: [],
  tasks: [],
  nodeAssigneeMap: {}
})

// 提示框状态
const tooltip = ref({
  visible: false,
  x: 0,
  y: 0,
  nodeId: '',
  nodeName: '',
  status: '', // completed, active, pending
  assigneeInfo: null,
  historyInfo: null
})

// 颜色配置
const COLORS = {
  completed: {
    fill: '#f6ffed',
    stroke: '#52c41a',
    strokeWidth: 3
  },
  active: {
    fill: '#e6f7ff',
    stroke: '#1890ff',
    strokeWidth: 3
  },
  pending: {
    fill: '#f5f5f5',
    stroke: '#d9d9d9',
    strokeWidth: 1
  },
  executedFlow: {
    stroke: '#52c41a',
    strokeWidth: 2
  },
  pendingFlow: {
    stroke: '#d9d9d9',
    strokeWidth: 1
  }
}

const onViewerImported = ({ viewer }) => {
  currentViewer = viewer
  // 延迟高亮显示节点状态，确保 DOM 渲染完成
  setTimeout(() => {
    nextTick(() => {
      highlightProcess(viewer)
    })
  }, 300)
  // 添加鼠标事件监听
  addMouseEventListeners(viewer)
}

const loadProcessProgress = async () => {
  if (!processInstanceId) {
    ElMessage.error('流程实例ID不能为空')
    return
  }
  
  try {
    console.log('正在加载流程进度，实例ID:', processInstanceId)
    const data = await processApi.getProcessProgress(processInstanceId)
    console.log('获取到流程进度数据:', data)
    progressData.value = data
    
    if (data.bpmnXml) {
      console.log('BPMN XML长度:', data.bpmnXml.length)
      bpmnXml.value = data.bpmnXml
    } else {
      ElMessage.warning('无法获取流程图')
    }
  } catch (error) {
    console.error('加载流程进度失败:', error)
    ElMessage.error('加载流程进度失败: ' + (error.message || '未知错误'))
  }
}

/**
 * 为 BPMN XML 添加布局信息（DI）
 * 使用 bpmn-auto-layout 生成布局
 */
const addLayoutToXml = async (xml) => {
  try {
    // 检查是否已有布局信息
    if (xml.includes('BPMNDiagram') && xml.includes('BPMNShape')) {
      console.log('XML已包含布局信息，跳过自动生成')
      return xml
    }
    
    console.log('XML缺少布局信息，使用 bpmn-auto-layout 生成布局...')
    
    // 使用 bpmn-auto-layout 生成布局
    const layoutedXml = await layoutProcess(xml)
    
    console.log('布局生成完成，新XML长度:', layoutedXml.length)
    return layoutedXml
    
  } catch (error) {
    console.error('生成布局时出错:', error)
    return xml  // 返回原始 XML
  }
}



/**
 * 高亮显示流程节点和连线
 */
const highlightProcess = (viewerInstance = currentViewer) => {
  try {
    if (!viewerInstance) {
      console.warn('BPMN Viewer 未初始化，跳过高亮')
      return
    }
    
    const canvas = viewerInstance.get('canvas')
    const elementRegistry = viewerInstance.get('elementRegistry')
    
    if (!canvas || !elementRegistry) {
      console.warn('Canvas 或 ElementRegistry 未初始化')
      return
    }
    
    const { completedNodes, activeNodes } = progressData.value
    // 使用 toRaw 解除 Proxy，确保 includes 方法正常工作
    const executedSequenceFlows = toRaw(progressData.value.executedSequenceFlows) || []
    console.log('高亮节点:', { completedNodes: toRaw(completedNodes), activeNodes: toRaw(activeNodes), executedSequenceFlows })
    
    // 获取所有元素
    const allElements = elementRegistry.getAll()
    console.log('流程图中的所有元素:', allElements.map(e => ({ id: e.id, type: e.type })))
    
    allElements.forEach(element => {
      const elementId = element.id
      const elementType = element.type
      
      // 处理连线
      if (elementType === 'bpmn:SequenceFlow') {
        const flows = executedSequenceFlows || []
        const isExecuted = flows.includes(elementId)
        console.log('检查连线:', elementId, '是否已执行:', isExecuted, '在列表中:', flows)
        setFlowStyle(canvas, element, isExecuted ? COLORS.executedFlow : COLORS.pendingFlow, isExecuted)
        return
      }
      
      // 处理节点
      if (elementType === 'bpmn:Process' || elementType === 'bpmn:Collaboration') {
        return // 跳过容器元素
      }
      
      let status = 'pending' // 默认未开始
      const rawCompletedNodes = toRaw(completedNodes) || []
      const rawActiveNodes = toRaw(activeNodes) || []
      if (rawCompletedNodes.includes(elementId)) {
        status = 'completed'
      } else if (rawActiveNodes.includes(elementId)) {
        status = 'active'
      }
      
      setNodeStyle(canvas, element, COLORS[status], status)
    })
    
    console.log('高亮完成')
  } catch (error) {
    console.error('高亮节点时出错:', error)
  }
}

/**
 * 设置节点样式
 */
const setNodeStyle = (canvas, element, colorConfig, status) => {
  const gfx = canvas.getGraphics(element)
  if (!gfx) return
  
  // 获取主形状元素
  const rect = gfx.querySelector('.djs-visual rect, .djs-visual circle, .djs-visual polygon, .djs-visual path')
  if (rect) {
    rect.style.fill = colorConfig.fill
    rect.style.stroke = colorConfig.stroke
    rect.style.strokeWidth = colorConfig.strokeWidth
  }
  
  // 添加标记类名以便自定义样式
  gfx.classList.remove('status-completed', 'status-active', 'status-pending')
  gfx.classList.add(`status-${status}`)
  
  // 在节点上添加状态标记
  addNodeBadge(gfx, element, status)
}

/**
 * 添加节点状态标记
 */
const addNodeBadge = (gfx, element, status) => {
  // 移除已有的标记
  const existingBadge = gfx.querySelector('.node-badge')
  if (existingBadge) {
    existingBadge.remove()
  }
  
  // 只有已完成和进行中的节点才显示标记
  if (status === 'pending') return
  
  const visual = gfx.querySelector('.djs-visual')
  if (!visual) return
  
  // 获取节点位置信息
  const bbox = visual.getBBox()
  
  // 创建标记
  const badge = document.createElementNS('http://www.w3.org/2000/svg', 'g')
  badge.classList.add('node-badge')
  
  // 根据状态创建不同的标记
  if (status === 'completed') {
    // 已完成 - 显示对勾
    badge.innerHTML = `
      <circle cx="${bbox.x + bbox.width - 8}" cy="${bbox.y + 8}" r="8" fill="#52c41a" stroke="#fff" stroke-width="1"/>
      <path d="M${bbox.x + bbox.width - 12} ${bbox.y + 8} L${bbox.x + bbox.width - 9} ${bbox.y + 11} L${bbox.x + bbox.width - 4} ${bbox.y + 5}" 
            stroke="#fff" stroke-width="2" fill="none"/>
    `
  } else if (status === 'active') {
    // 进行中 - 显示人图标
    const assigneeInfo = progressData.value.nodeAssigneeMap?.[element.id]
    const initial = assigneeInfo?.assigneeName?.charAt(0) || assigneeInfo?.assigneeId?.charAt(0) || '待'
    
    badge.innerHTML = `
      <circle cx="${bbox.x + bbox.width - 10}" cy="${bbox.y + 10}" r="10" fill="#1890ff" stroke="#fff" stroke-width="1"/>
      <text x="${bbox.x + bbox.width - 10}" y="${bbox.y + 14}" text-anchor="middle" fill="#fff" font-size="10">${initial}</text>
    `
  }
  
  visual.appendChild(badge)
}

/**
 * 设置连线样式 - 不破坏箭头 marker
 */
const setFlowStyle = (canvas, element, colorConfig, isExecuted = false) => {
  // 只添加/移除 executed 类，样式由 CSS 处理
  // 这样不会破坏 bpmn-js 默认的 marker-end 箭头
  const gfx = canvas.getGraphics(element)
  if (!gfx) return
  
  if (isExecuted) {
    gfx.classList.add('executed')
  } else {
    gfx.classList.remove('executed')
  }
}

/**
 * 添加鼠标事件监听
 */
const addMouseEventListeners = (viewerInstance = currentViewer) => {
  try {
    if (!viewerInstance) {
      console.warn('BPMN Viewer 未初始化，跳过事件监听')
      return
    }
    
    const eventBus = viewerInstance.get('eventBus')
    if (!eventBus) {
      console.warn('EventBus 未初始化')
      return
    }
    
    // 监听鼠标悬停事件
    eventBus.on('element.hover', (e) => {
      try {
        const element = e.element
        if (!element) return
        
        const elementType = element.type
        
        // 只处理节点，不处理连线和容器
        if (elementType === 'bpmn:Process' || 
            elementType === 'bpmn:Collaboration' ||
            elementType === 'bpmn:SequenceFlow' ||
            !elementType.startsWith('bpmn:')) {
          return
        }
        
        const elementId = element.id
        const { nodeAssigneeMap } = progressData.value
        const rawCompletedNodes = toRaw(progressData.value.completedNodes) || []
        const rawActiveNodes = toRaw(progressData.value.activeNodes) || []
        
        // 确定节点状态
        let status = 'pending'
        if (rawCompletedNodes.includes(elementId)) {
          status = 'completed'
        } else if (rawActiveNodes.includes(elementId)) {
          status = 'active'
        }
        
        // 获取处理人信息
        const assigneeInfo = nodeAssigneeMap?.[elementId] || null
        
        // 从历史记录中查找该节点的执行信息（用于脚本任务等自动节点显示时间）
        const nodeHistory = toRaw(progressData.value.nodeHistory) || []
        const historyInfo = nodeHistory.find(h => h.nodeId === elementId) || null
        
        // 更新提示框
        tooltip.value = {
          visible: true,
          x: e.originalEvent.pageX + 15,
          y: e.originalEvent.pageY + 15,
          nodeId: elementId,
          nodeName: element.businessObject?.name || elementId,
          status: status,
          assigneeInfo: assigneeInfo,
          historyInfo: historyInfo
        }
      } catch (err) {
        console.error('处理悬停事件时出错:', err)
      }
    })
    
    // 监听鼠标移动事件
    eventBus.on('element.mousemove', (e) => {
      if (tooltip.value.visible) {
        tooltip.value.x = e.originalEvent.pageX + 15
        tooltip.value.y = e.originalEvent.pageY + 15
      }
    })
    
    // 监听鼠标离开事件
    eventBus.on('element.out', () => {
      tooltip.value.visible = false
    })
    
    console.log('鼠标事件监听已添加')
  } catch (error) {
    console.error('添加鼠标事件监听时出错:', error)
  }
}

// 状态文本
const getStatusType = (status) => {
  const types = {
    'RUNNING': 'warning',
    'COMPLETED': 'success',
    'SUSPENDED': 'info'
  }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = {
    'RUNNING': '运行中',
    'COMPLETED': '已完成',
    'SUSPENDED': '已挂起'
  }
  return texts[status] || status
}

// 处理方式文本
const getActionType = (action) => {
  const types = {
    'APPROVED': 'success',
    'REJECTED': 'danger',
    'TRANSFERRED': 'warning',
    'PROCESSING': 'primary'
  }
  return types[action] || 'info'
}

const getActionText = (action, actionLabel) => {
  if (actionLabel) return actionLabel
  const texts = {
    'APPROVED': '同意',
    'REJECTED': '驳回',
    'TRANSFERRED': '转办',
    'PROCESSING': '处理中'
  }
  return texts[action] || action
}

const getDisplayVariables = (node) => {
  const variables = node?.variables || {}
  const hiddenKeys = new Set([
    'action',
    'actionLabel',
    'approved',
    'approver',
    'comment',
    'initiator',
    'skipNodeEnabled',
    'entityCode',
    'entityDataId',
    'dataNo',
    'submitterId',
    'submitterName',
    '_approvers_'
  ])
  return Object.fromEntries(
    Object.entries(variables).filter(([key]) => !hiddenKeys.has(key))
  )
}

// 时间线样式
const getNodeTimelineType = (node) => {
  if (node.status === 'COMPLETED') return 'success'
  if (node.status === 'ACTIVE') return 'primary'
  return 'info'
}

const getNodeTimelineIcon = (node) => {
  if (node.status === 'COMPLETED') return 'Check'
  if (node.status === 'ACTIVE') return 'Loading'
  return 'CircleCheck'
}

// 格式化耗时
const formatDuration = (ms) => {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  
  if (days > 0) return `${days}天${hours % 24}小时`
  if (hours > 0) return `${hours}小时${minutes % 60}分钟`
  if (minutes > 0) return `${minutes}分钟${seconds % 60}秒`
  return `${seconds}秒`
}

// 监听 progressData 变化，自动高亮
watch(() => progressData.value, (newVal) => {
  console.log('progressData 变化:', newVal)
  if (newVal && newVal.executedSequenceFlows) {
    console.log('检测到 executedSequenceFlows:', newVal.executedSequenceFlows)
    // 延迟执行，确保 DOM 完全渲染
    setTimeout(() => {
      nextTick(() => {
        highlightProcess()
      })
    }, 500)
  }
}, { deep: true })

onMounted(() => {
  nextTick(() => {
    loadProcessProgress()
  })
})
</script>

<style scoped>
.process-progress {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.process-name {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
}

.legend {
  display: flex;
  gap: 20px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.legend-color {
  width: 20px;
  height: 12px;
  border-radius: 2px;
  border: 2px solid;
}

.legend-color.completed {
  background: #f6ffed;
  border-color: #52c41a;
}

.legend-color.active {
  background: #e6f7ff;
  border-color: #1890ff;
}

.legend-color.pending {
  background: #f5f5f5;
  border-color: #d9d9d9;
}

.progress-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.canvas-wrapper {
  flex: 1;
  position: relative;
  background: #fff;
}

.canvas {
  width: 100%;
  height: 100%;
}

/* 节点悬停提示框 */
.node-tooltip {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 220px;
  max-width: 300px;
}

.tooltip-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.node-status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.node-status-dot.completed {
  background: #52c41a;
}

.node-status-dot.active {
  background: #1890ff;
}

.node-status-dot.pending {
  background: #d9d9d9;
}

.tooltip-header .node-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.tooltip-content {
  font-size: 13px;
}

.info-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 6px;
  line-height: 1.5;
}

.info-row .label {
  color: #909399;
  white-space: nowrap;
  flex-shrink: 0;
}

.info-row .value {
  color: #303133;
  margin-left: 4px;
  word-break: break-all;
}

.info-row .value.comment {
  color: #606266;
  font-style: italic;
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
  margin-top: 4px;
}

.info-panel {
  width: 350px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.panel-title {
  padding: 15px;
  font-weight: 600;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
}

.panel-content {
  flex: 1;
  padding: 15px;
  overflow-y: auto;
}

.section-title {
  margin-top: 20px;
  margin-bottom: 10px;
  font-weight: 600;
  color: #303133;
  border-left: 4px solid #409eff;
  padding-left: 10px;
}

.task-card {
  margin-bottom: 10px;
}

.task-name {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.task-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.node-name {
  font-weight: 500;
}

.assignee-info,
.duration-info {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* BPMN 高亮样式覆盖 */
:deep(.status-completed .djs-visual rect),
:deep(.status-completed .djs-visual circle),
:deep(.status-completed .djs-visual polygon) {
  fill: #f6ffed !important;
  stroke: #52c41a !important;
  stroke-width: 3px !important;
}

:deep(.status-active .djs-visual rect),
:deep(.status-active .djs-visual circle),
:deep(.status-active .djs-visual polygon) {
  fill: #e6f7ff !important;
  stroke: #1890ff !important;
  stroke-width: 3px !important;
}

:deep(.status-pending .djs-visual rect),
:deep(.status-pending .djs-visual circle),
:deep(.status-pending .djs-visual polygon) {
  fill: #f5f5f5 !important;
  stroke: #d9d9d9 !important;
  stroke-width: 1px !important;
}

/* 节点徽章动画 */
:deep(.node-badge) {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.node-variables {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.action-info,
.comment-info {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
}

.action-info :deep(.el-tag) {
  margin-left: 6px;
}

.comment-info {
  display: flex;
  gap: 6px;
  line-height: 1.5;
}

.comment-label {
  flex: none;
  color: #909399;
}

.var-tag {
  font-family: monospace;
  font-size: 12px;
}
</style>
