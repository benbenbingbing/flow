<template>
  <div class="bpmn-viewer-wrapper">
    <div ref="canvasRef" class="vue-bpmn-viewer-canvas"></div>
    
    <!-- 节点悬停提示框 -->
    <div v-if="tooltip.visible" 
         class="node-tooltip" 
         :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }">
      <div class="tooltip-header">
        <span class="node-status-dot" :class="tooltip.status"></span>
        <span class="node-name">{{ tooltip.nodeName }}</span>
      </div>
      <div class="tooltip-content">
        <template v-if="tooltip.assigneeList && tooltip.assigneeList.length > 0">
          <div class="info-row">
            <span class="label">处理人：</span>
          </div>
          <div v-for="(item, idx) in tooltip.assigneeList" :key="idx" class="assignee-item">
            <span class="assignee-status-dot" :class="item.status?.toLowerCase() || 'pending'"></span>
            <span class="value">{{ item.assigneeName || item.assigneeId || '未分配' }}</span>
            <span class="assignee-action" v-if="item.status === 'COMPLETED'">{{ item.actionLabel || (item.action === 'REJECTED' ? '驳回' : item.action === 'TRANSFERRED' ? '转办' : item.action === 'APPROVED' ? '通过' : item.action) }}</span>
            <span class="assignee-time">{{ item.handleTime || '' }}</span>
          </div>
        </template>
        <template v-else-if="tooltip.assigneeInfo">
          <div class="info-row">
            <span class="label">{{ tooltip.status === 'active' ? '当前处理人' : '审批人' }}：</span>
            <span class="value">{{ tooltip.assigneeInfo.assigneeName || tooltip.assigneeInfo.assigneeId || '未分配' }}</span>
          </div>
          <div class="info-row">
            <span class="label">{{ tooltip.status === 'active' ? '到达时间' : '处理时间' }}：</span>
            <span class="value">{{ tooltip.assigneeInfo.handleTime || '-' }}</span>
          </div>
          <div v-if="tooltip.assigneeInfo.comment" class="info-row">
            <span class="label">审批意见：</span>
            <span class="value comment">{{ tooltip.assigneeInfo.comment }}</span>
          </div>
        </template>
        <template v-else>
          <div class="info-row">
            <span class="label">状态：</span>
            <span class="value">{{ tooltip.status === 'completed' ? '已完成' : tooltip.status === 'active' ? '进行中' : tooltip.status === 'terminated' ? '已终止' : '未开始' }}</span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick, toRaw } from 'vue'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import BpmnNavigatedViewer from 'bpmn-js/lib/NavigatedViewer'

import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'

const props = defineProps({
  xml: { type: String, default: '' },
  navigated: { type: Boolean, default: false },
  fitViewport: { type: Boolean, default: false },
  // 进度相关数据
  progressData: {
    type: Object,
    default: () => ({
      completedNodes: [],
      activeNodes: [],
      terminatedNodes: [],
      executedSequenceFlows: [],
      nodeAssigneeMap: {},
      nodeAssigneesMap: {}
    })
  }
})

const emit = defineEmits(['imported', 'error'])

const canvasRef = ref()
const viewer = ref(null)

// 提示框状态
const tooltip = ref({
  visible: false,
  x: 0,
  y: 0,
  nodeId: '',
  nodeName: '',
  status: '',
  assigneeInfo: null,
  assigneeList: null
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
  terminated: {
    fill: '#fff1f0',
    stroke: '#ff4d4f',
    strokeWidth: 3
  },
  executedFlow: {
    stroke: '#52c41a',
    strokeWidth: 2
  }
}

const initViewer = () => {
  const ViewerClass = props.navigated ? BpmnNavigatedViewer : BpmnViewer
  viewer.value = new ViewerClass({
    container: canvasRef.value
  })
}

const applyViewport = () => {
  if (!viewer.value || !canvasRef.value) return
  const canvas = viewer.value.get('canvas')
  canvas.resized()
  if (props.fitViewport) {
    canvas.zoom('fit-viewport', 'auto')
    // 自适应时让内部容器跟随 SVG 默认尺寸，不强制滚动条
    const djsContainer = canvasRef.value.querySelector('.djs-container')
    const svg = canvasRef.value.querySelector('.djs-container svg')
    if (djsContainer) {
      djsContainer.style.width = ''
      djsContainer.style.height = ''
      djsContainer.style.minWidth = ''
      djsContainer.style.minHeight = ''
    }
    if (svg) {
      svg.style.width = ''
      svg.style.height = ''
      svg.style.minWidth = ''
      svg.style.minHeight = ''
      svg.removeAttribute('viewBox')
    }
  } else {
    // 按 100% 显示，不缩放节点；超出容器时通过滚动条查看
    const svg = canvasRef.value.querySelector('.djs-container svg')
    const djsContainer = canvasRef.value.querySelector('.djs-container')
    if (!svg || !djsContainer) return

    const viewbox = canvas.viewbox()
    if (!viewbox || !viewbox.inner) return

    const padding = 60
    const inner = viewbox.inner

    // 优先读取 SVG 实际渲染包围盒（包含箭头 marker、标签、徽章等）
    let minX = inner.x
    let minY = inner.y
    let maxX = inner.x + inner.width
    let maxY = inner.y + inner.height
    try {
      const bbox = svg.getBBox()
      if (bbox && bbox.width > 0 && bbox.height > 0) {
        minX = Math.min(minX, bbox.x)
        minY = Math.min(minY, bbox.y)
        maxX = Math.max(maxX, bbox.x + bbox.width)
        maxY = Math.max(maxY, bbox.y + bbox.height)
      }
    } catch (e) {
      // ignore
    }

    const width = Math.ceil(maxX - minX + padding * 2)
    const height = Math.ceil(maxY - minY + padding * 2)

    // 先固定 SVG 像素尺寸，再按 1:1 缩放，最后把 viewBox 对准完整包围盒
    svg.style.width = width + 'px'
    svg.style.height = height + 'px'
    svg.style.minWidth = width + 'px'
    svg.style.minHeight = height + 'px'
    djsContainer.style.width = width + 'px'
    djsContainer.style.height = height + 'px'
    djsContainer.style.minWidth = width + 'px'
    djsContainer.style.minHeight = height + 'px'

    // 通过 bpmn-js API 设置 viewbox，同时更新根 group 的 transform，
    // 保证内容完整显示并且 1:1 不缩放
    canvas.viewbox({
      x: minX - padding,
      y: minY - padding,
      width,
      height
    })
  }
}

const importXML = async (xml) => {
  if (!viewer.value) return
  try {
    await viewer.value.importXML(xml)
    // 先确保 canvas 尺寸计算正确，再应用 viewport
    nextTick(() => applyViewport())
    // 延迟高亮，确保 bpmn-js 渲染完成
    setTimeout(() => {
      applyViewport()
      highlightProcess()
      addMouseEventListeners()
      startFlowFixTimer()
    }, 200)
    emit('imported', { viewer: viewer.value })
  } catch (error) {
    console.error('Viewer 导入XML失败:', error)
    emit('error', error)
  }
}

const loadXml = async (xml) => {
  if (!xml) return
  if (!viewer.value) {
    await nextTick()
    initViewer()
  }
  await importXML(xml)
}

/**
 * 高亮显示流程节点和连线
 */
const highlightProcess = () => {
  try {
    if (!viewer.value) return
    
    const canvas = viewer.value.get('canvas')
    const elementRegistry = viewer.value.get('elementRegistry')
    
    if (!canvas || !elementRegistry) return
    
    const { completedNodes, activeNodes } = props.progressData
    const executedSequenceFlows = toRaw(props.progressData.executedSequenceFlows) || []
    
    const allElements = elementRegistry.getAll()
    
    allElements.forEach(element => {
      const elementId = element.id
      const elementType = element.type
      
      // 处理连线
      if (elementType === 'bpmn:SequenceFlow') {
        const flows = executedSequenceFlows || []
        const isExecuted = flows.includes(elementId)
        setFlowStyle(canvas, element, isExecuted)
        return
      }
      
      // 处理节点
      if (elementType === 'bpmn:Process' || elementType === 'bpmn:Collaboration') {
        return
      }
      
      let status = 'pending'
      const rawCompletedNodes = toRaw(completedNodes) || []
      const rawActiveNodes = toRaw(activeNodes) || []
      const rawTerminatedNodes = toRaw(props.progressData?.terminatedNodes) || []
      // 优先判断活跃：回退后再次经过的节点应显示为进行中
      if (rawActiveNodes.includes(elementId)) {
        status = 'active'
      } else if (rawTerminatedNodes.includes(elementId)) {
        status = 'terminated'
      } else if (rawCompletedNodes.includes(elementId)) {
        status = 'completed'
      }
      
      // 终止流程的结束节点标记为红色
      if (props.progressData?.status === 'TERMINATED' && elementType === 'bpmn:EndEvent') {
        status = 'terminated'
      }
      
      setNodeStyle(canvas, element, COLORS[status], status)
    })
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
  
  const rect = gfx.querySelector('.djs-visual rect, .djs-visual circle, .djs-visual polygon, .djs-visual path')
  if (rect) {
    rect.style.fill = colorConfig.fill
    rect.style.stroke = colorConfig.stroke
    rect.style.strokeWidth = colorConfig.strokeWidth
  }
  
  gfx.classList.remove('status-completed', 'status-active', 'status-pending', 'status-terminated')
  gfx.classList.add(`status-${status}`)
  
  addNodeBadge(gfx, element, status)
}

/**
 * 添加节点状态标记
 */
const addNodeBadge = (gfx, element, status) => {
  const existingBadge = gfx.querySelector('.node-badge')
  if (existingBadge) {
    existingBadge.remove()
  }
  
  if (status === 'pending') return
  
  const visual = gfx.querySelector('.djs-visual')
  if (!visual) return
  
  const bbox = visual.getBBox()
  const badge = document.createElementNS('http://www.w3.org/2000/svg', 'g')
  badge.classList.add('node-badge')
  
  if (status === 'completed') {
    // 已完成 - 标记在右上角
    badge.innerHTML = `
      <circle cx="${bbox.x + bbox.width - 8}" cy="${bbox.y + 8}" r="8" fill="#52c41a" stroke="#fff" stroke-width="1"/>
      <path d="M${bbox.x + bbox.width - 12} ${bbox.y + 8} L${bbox.x + bbox.width - 9} ${bbox.y + 11} L${bbox.x + bbox.width - 4} ${bbox.y + 5}" 
            stroke="#fff" stroke-width="2" fill="none"/>
    `
  } else if (status === 'active') {
    // 进行中 - 标记在右上角
    const assigneeInfo = props.progressData.nodeAssigneeMap?.[element.id]
    const initial = assigneeInfo?.assigneeName?.charAt(0) || assigneeInfo?.assigneeId?.charAt(0) || '待'
    
    badge.innerHTML = `
      <circle cx="${bbox.x + bbox.width - 10}" cy="${bbox.y + 10}" r="10" fill="#1890ff" stroke="#fff" stroke-width="1"/>
      <text x="${bbox.x + bbox.width - 10}" y="${bbox.y + 14}" text-anchor="middle" fill="#fff" font-size="10">${initial}</text>
    `
  } else if (status === 'terminated') {
    // 已终止 - 红色X标记在右上角
    badge.innerHTML = `
      <circle cx="${bbox.x + bbox.width - 8}" cy="${bbox.y + 8}" r="8" fill="#ff4d4f" stroke="#fff" stroke-width="1"/>
      <path d="M${bbox.x + bbox.width - 11} ${bbox.y + 5} L${bbox.x + bbox.width - 5} ${bbox.y + 11}" stroke="#fff" stroke-width="2" fill="none"/>
      <path d="M${bbox.x + bbox.width - 5} ${bbox.y + 5} L${bbox.x + bbox.width - 11} ${bbox.y + 11}" stroke="#fff" stroke-width="2" fill="none"/>
    `
  }
  
  visual.appendChild(badge)
}

const applyFlowColor = (gfx, isExecuted) => {
  if (!gfx) return
  const paths = gfx.querySelectorAll('.djs-visual > path')
  paths.forEach(path => {
    if (isExecuted) {
      path.setAttribute('stroke', COLORS.executedFlow.stroke)
      path.setAttribute('stroke-width', COLORS.executedFlow.strokeWidth)
      path.style.stroke = COLORS.executedFlow.stroke
      path.style.strokeWidth = COLORS.executedFlow.strokeWidth
      path.style.setProperty('stroke', COLORS.executedFlow.stroke, 'important')
      path.style.setProperty('stroke-width', COLORS.executedFlow.strokeWidth + 'px', 'important')
    } else {
      path.setAttribute('stroke', 'black')
      path.setAttribute('stroke-width', '2')
      path.style.stroke = 'black'
      path.style.strokeWidth = '2px'
    }
  })
}

const drawGreenArrow = (gfx, element) => {
  if (!gfx || !element.waypoints || element.waypoints.length < 2) return
  const visual = gfx.querySelector('.djs-visual')
  if (!visual) return
  // 如果已经有自定义箭头先移除
  const existing = visual.querySelector('.custom-arrow-overlay')
  if (existing) existing.remove()

  // 隐藏原生黑色 marker
  const paths = visual.querySelectorAll('path')
  paths.forEach(p => p.setAttribute('marker-end', 'none'))

  const waypoints = element.waypoints
  const end = waypoints[waypoints.length - 1]
  const prev = waypoints[waypoints.length - 2]
  const dx = end.x - prev.x
  const dy = end.y - prev.y
  const angle = Math.atan2(dy, dx)
  const len = 10
  const halfW = 6

  const tipX = end.x - len * Math.cos(angle)
  const tipY = end.y - len * Math.sin(angle)
  const leftX = tipX + halfW * Math.cos(angle - Math.PI / 2)
  const leftY = tipY + halfW * Math.sin(angle - Math.PI / 2)
  const rightX = tipX + halfW * Math.cos(angle + Math.PI / 2)
  const rightY = tipY + halfW * Math.sin(angle + Math.PI / 2)

  const polygon = document.createElementNS('http://www.w3.org/2000/svg', 'polygon')
  polygon.setAttribute('points', `${end.x},${end.y} ${leftX},${leftY} ${rightX},${rightY}`)
  polygon.setAttribute('fill', COLORS.executedFlow.stroke)
  polygon.setAttribute('stroke', COLORS.executedFlow.stroke)
  polygon.setAttribute('stroke-width', '1')
  polygon.classList.add('custom-arrow-overlay')
  visual.appendChild(polygon)
}

const removeGreenArrow = (gfx) => {
  if (!gfx) return
  const visual = gfx.querySelector('.djs-visual')
  if (!visual) return
  const existing = visual.querySelector('.custom-arrow-overlay')
  if (existing) existing.remove()
}

const setFlowStyle = (canvas, element, isExecuted) => {
  const gfx = canvas.getGraphics(element)
  applyFlowColor(gfx, isExecuted)
  if (gfx) {
    gfx.classList.toggle('executed', isExecuted)
    if (isExecuted) {
      drawGreenArrow(gfx, element)
    } else {
      removeGreenArrow(gfx)
    }
  }
}

let flowFixTimer = null

/**
 * 计算节点旁边的固定 tooltip 位置（基于节点在页面中的包围盒）
 */
const computeTooltipPosition = (element) => {
  try {
    if (!viewer.value || !canvasRef.value) return { x: 0, y: 0 }
    const canvas = viewer.value.get('canvas')
    const gfx = canvas.getGraphics(element)
    if (!gfx) return { x: 0, y: 0 }

    const visual = gfx.querySelector('.djs-visual')
    if (!visual) return { x: 0, y: 0 }

    const rect = visual.getBoundingClientRect()
    const wrapperRect = canvasRef.value.getBoundingClientRect()

    // 以节点右上角为基准，留出一点间距
    const offsetX = 12
    const offsetY = -8
    let x = rect.right - wrapperRect.left + offsetX
    let y = rect.top - wrapperRect.top + offsetY

    // 简单边界保护：避免超出容器右侧
    const tooltipWidth = 260
    if (x + tooltipWidth > wrapperRect.width) {
      x = rect.left - wrapperRect.left - tooltipWidth - offsetX
    }
    if (y < 0) {
      y = 0
    }

    return { x, y }
  } catch (e) {
    return { x: 0, y: 0 }
  }
}

const fixFlowColors = () => {
  if (!viewer.value) return
  const canvas = viewer.value.get('canvas')
  const elementRegistry = viewer.value.get('elementRegistry')
  const executedSequenceFlows = toRaw(props.progressData?.executedSequenceFlows) || []
  elementRegistry.getAll().forEach(element => {
    if (element.type !== 'bpmn:SequenceFlow') return
    const gfx = canvas.getGraphics(element)
    const isExecuted = executedSequenceFlows.includes(element.id)
    applyFlowColor(gfx, isExecuted)
    if (gfx) {
      gfx.classList.toggle('executed', isExecuted)
      if (isExecuted) {
        drawGreenArrow(gfx, element)
      } else {
        removeGreenArrow(gfx)
      }
    }
  })
}

const startFlowFixTimer = () => {
  if (flowFixTimer) clearInterval(flowFixTimer)
  fixFlowColors()
  flowFixTimer = setInterval(fixFlowColors, 100)
}

/**
 * 添加鼠标事件监听
 */
const addMouseEventListeners = () => {
  try {
    if (!viewer.value) return
    
    const eventBus = viewer.value.get('eventBus')
    if (!eventBus) return
    
    eventBus.on('element.hover', (e) => {
      try {
        const element = e.element
        if (!element) return

        const elementType = element.type
        if (elementType === 'bpmn:Process' ||
            elementType === 'bpmn:Collaboration' ||
            elementType === 'bpmn:SequenceFlow' ||
            !elementType.startsWith('bpmn:')) {
          return
        }

        const elementId = element.id
        const { nodeAssigneeMap } = props.progressData
        const rawCompletedNodes = toRaw(props.progressData.completedNodes) || []
        const rawActiveNodes = toRaw(props.progressData.activeNodes) || []

        let status = 'pending'
        const rawTerminatedNodes2 = toRaw(props.progressData?.terminatedNodes) || []
        if (rawActiveNodes.includes(elementId)) {
          status = 'active'
        } else if (rawTerminatedNodes2.includes(elementId)) {
          status = 'terminated'
        } else if (rawCompletedNodes.includes(elementId)) {
          status = 'completed'
        }
        // 终止流程的结束节点标记为红色
        if (props.progressData?.status === 'TERMINATED' && elementType === 'bpmn:EndEvent') {
          status = 'terminated'
        }

        const assigneeInfo = nodeAssigneeMap?.[elementId] || null
        const assigneeList = props.progressData.nodeAssigneesMap?.[elementId] || null

        const position = computeTooltipPosition(element)

        tooltip.value = {
          visible: true,
          x: position.x,
          y: position.y,
          nodeId: elementId,
          nodeName: element.businessObject?.name || elementId,
          status: status,
          assigneeInfo: assigneeInfo,
          assigneeList: assigneeList
        }
      } catch (err) {
        console.error('处理悬停事件时出错:', err)
      }
    })

    eventBus.on('element.out', () => {
      tooltip.value.visible = false
    })
  } catch (error) {
    console.error('添加鼠标事件监听时出错:', error)
  }
}

watch(() => props.xml, (newXml) => {
  if (newXml) loadXml(newXml)
}, { immediate: false })

watch(() => props.progressData, () => {
  setTimeout(() => {
    nextTick(() => {
      applyViewport()
      highlightProcess()
      startFlowFixTimer()
    })
  }, 500)
}, { deep: true })

onMounted(() => {
  initViewer()
  if (props.xml) loadXml(props.xml)
})

onUnmounted(() => {
  if (flowFixTimer) {
    clearInterval(flowFixTimer)
    flowFixTimer = null
  }
  if (viewer.value) {
    viewer.value.destroy()
  }
})

defineExpose({
  loadXml,
  getViewer: () => viewer.value
})
</script>

<style scoped>
.bpmn-viewer-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

.vue-bpmn-viewer-canvas {
  width: 100%;
  height: 100%;
  overflow: auto !important;
  position: relative;
}

.vue-bpmn-viewer-canvas .djs-container {
  overflow: visible !important;
}

.vue-bpmn-viewer-canvas .djs-container svg {
  display: block;
}

/* 节点悬停提示框 */
.node-tooltip {
  position: absolute;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 220px;
  max-width: 300px;
  user-select: text;
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

.node-status-dot.terminated {
  background: #ff4d4f;
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

.assignee-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  padding: 4px 0;
  flex-wrap: nowrap;
}

.assignee-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.assignee-status-dot.completed {
  background: #52c41a;
}

.assignee-status-dot.processing {
  background: #1890ff;
}

.assignee-status-dot.pending {
  background: #d9d9d9;
}

.assignee-action {
  color: #52c41a;
  font-size: 12px;
  margin-left: auto;
  white-space: nowrap;
  flex-shrink: 0;
}

.assignee-time {
  color: #909399;
  font-size: 12px;
  white-space: nowrap;
  flex-shrink: 0;
}
</style>

<style>
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

:deep(.bjs-powered-by) {
  display: none !important;
}
</style>
