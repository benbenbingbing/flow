<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import flowableModdle from '@/assets/flowable.json'

import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'

const props = defineProps({
  xml: { type: String, default: '' }
})

const emit = defineEmits(['imported', 'element-click', 'command-stack-changed'])

const canvasRef = ref()
const modeler = ref(null)
let commandStack = null
let actionOverlayIds = []
let actionOverlayElementIds = []
let currentActionCounts = {}
let actionOverlayRefreshFrame = null

const translations = {
  'Append end event': '追加结束事件',
  'Append gateway': '追加网关',
  'Append task': '追加用户任务',
  'Append user task': '追加用户任务',
  'Append intermediate/boundary event': '追加中间/边界事件',
  'Change type': '更改类型',
  'Remove': '删除',
  'Connect using sequence/message flow or association': '连接',
  'Activate the global connect tool': '全局连接工具',
  'Create start event': '创建开始事件',
  'Create intermediate event': '创建中间事件',
  'Create end event': '创建结束事件',
  'Create task': '创建用户任务',
  'Create user task': '创建用户任务',
  'Create gateway': '创建网关',
  'Create pool/participant': '创建泳道',
  'Create expanded sub-process': '创建子流程',
  'Create data object reference': '创建数据对象',
  'Create data store reference': '创建数据存储',
  'Change element': '更改元素类型',
  'Start event': '开始事件',
  'Intermediate throw event': '中间抛出事件',
  'Intermediate catch event': '中间捕获事件',
  'End event': '结束事件',
  'Task': '任务',
  'User task': '用户任务',
  'Service task': '服务任务',
  'Send task': '发送任务',
  'Receive task': '接收任务',
  'Manual task': '手动任务',
  'Business rule task': '业务规则任务',
  'Script task': '脚本任务',
  'Call activity': '调用活动',
  'Sub-process (collapsed)': '子流程（折叠）',
  'Sub-process (expanded)': '子流程（展开）',
  'Gateway': '网关',
  'Exclusive gateway': '排他网关',
  'Parallel gateway': '并行网关',
  'Inclusive gateway': '包容网关',
  'Event-based gateway': '基于事件的网关',
  'Timer': '定时器',
  'Message': '消息',
  'Signal': '信号',
  'Error': '错误',
  'Escalation': '升级',
  'Compensation': '补偿',
  'Link': '链接',
  'Condition': '条件',
  'Cancel': '取消',
  'Activate hand tool': '手型工具',
  'Activate lasso tool': '套索工具',
  'Activate create/remove space tool': '空间工具',
  'Global connect tool': '全局连接',
  'Sequence flow': '顺序流',
  'Default flow': '默认流',
  'Conditional flow': '条件流'
}

const customTranslate = (template, replacements = {}) => {
  let result = translations[template] || template
  Object.keys(replacements).forEach(key => {
    result = result.replace(new RegExp('{' + key + '}', 'g'), replacements[key])
  })
  return result
}

const customTranslateModule = {
  translate: ['value', customTranslate]
}

const activateElement = (element) => {
  if (!element || !modeler.value) return
  try {
    modeler.value.get('selection').select(element)
  } catch (error) {}
  element._modeler = modeler.value
  emit('element-click', element)
}

const initModeler = () => {
  modeler.value = new BpmnModeler({
    container: canvasRef.value,
    additionalModules: [customTranslateModule],
    moddleExtensions: { flowable: flowableModdle }
  })

  try {
    const injector = modeler.value.get('injector')
    if (injector) {
      injector._instances.translate = customTranslate
    }
  } catch (e) {}

  try {
    commandStack = modeler.value.get('commandStack')
    if (commandStack) {
      commandStack.on('changed', () => {
        emit('command-stack-changed', {
          canUndo: commandStack.canUndo(),
          canRedo: commandStack.canRedo()
        })
      })
    }
  } catch (e) {}

  try {
    const palette = modeler.value.get('palette')
    const originalGetEntries = palette._providers[0].getPaletteEntries
    palette._providers[0].getPaletteEntries = function() {
      const entries = originalGetEntries.apply(this, arguments)
      delete entries['create-task']
      const elementFactory = modeler.value.get('elementFactory')
      const create = modeler.value.get('create')
      entries['create-user-task'] = {
        group: 'activity',
        className: 'bpmn-icon-user-task',
        title: '创建用户任务',
        action: {
          dragstart: function(event) {
            const userTask = elementFactory.createShape({ type: 'bpmn:UserTask' })
            create.start(event, userTask)
          },
          click: function(event) {
            const userTask = elementFactory.createShape({ type: 'bpmn:UserTask' })
            create.start(event, userTask, { hints: { autoActivate: true } })
          }
        }
      }
      return entries
    }
  } catch (e) {}

  try {
    const contextPad = modeler.value.get('contextPad')
    const elementFactory = modeler.value.get('elementFactory')
    const autoPlace = modeler.value.get('autoPlace')
    contextPad.registerProvider({
      getContextPadEntries: function(element) {
        return function(entries) {
          if (entries['append.append-task']) {
            entries['append.append-task'].title = '追加用户任务'
            entries['append.append-task'].className = 'bpmn-icon-user-task'
            entries['append.append-task'].action = {
              click: function(event, element) {
                const modeling = modeler.value.get('modeling')
                const userTask = elementFactory.createShape({ type: 'bpmn:UserTask' })
                if (autoPlace) {
                  autoPlace.append(element, userTask)
                } else {
                  modeling.appendShape(element, userTask)
                }
              }
            }
          }
          return entries
        }
      }
    })
  } catch (e) {}

  modeler.value.on('element.click', (e) => {
    const element = e.element
    const configurableTypes = [
      'bpmn:UserTask', 'bpmn:ServiceTask', 'bpmn:ManualTask',
      'bpmn:ScriptTask', 'bpmn:BusinessRuleTask', 'bpmn:SendTask', 'bpmn:ReceiveTask',
      'bpmn:StartEvent', 'bpmn:EndEvent',
      'bpmn:ExclusiveGateway', 'bpmn:ParallelGateway', 'bpmn:InclusiveGateway', 'bpmn:EventBasedGateway',
      'bpmn:SequenceFlow'
    ]
    if (configurableTypes.some(type => element.type?.includes(type))) {
      activateElement(element)
    }
  })

  modeler.value.on('elements.changed', () => {
    if (commandStack) {
      emit('command-stack-changed', {
        canUndo: commandStack.canUndo(),
        canRedo: commandStack.canRedo()
      })
    }
    scheduleActionOverlayRefresh()
  })
}

const importXML = async (xml) => {
  if (!modeler.value) return
  try {
    await modeler.value.importXML(xml)
    const canvas = modeler.value.get('canvas')
    // 先 fit-viewport 保证所有节点在可视区域内
    canvas.zoom('fit-viewport', 'auto')
    // 限制最小缩放比例，避免节点缩得太小；若超出画布则通过滚动查看
    const currentZoom = canvas.zoom()
    if (currentZoom < 0.7) {
      canvas.zoom(0.7)
    }
    if (commandStack) {
      emit('command-stack-changed', {
        canUndo: commandStack.canUndo(),
        canRedo: commandStack.canRedo()
      })
    }
    emit('imported', { modeler: modeler.value })
  } catch (error) {
    console.error('导入XML失败:', error)
  }
}

const loadXml = async (xml) => {
  if (!xml) return
  if (!modeler.value) {
    await nextTick()
  }
  await importXML(xml)
}

const getXml = async () => {
  if (!modeler.value) return null
  const { xml } = await modeler.value.saveXML({ format: true })
  return xml
}

const undo = () => {
  if (commandStack && commandStack.canUndo()) {
    commandStack.undo()
    emit('command-stack-changed', {
      canUndo: commandStack.canUndo(),
      canRedo: commandStack.canRedo()
    })
  }
}

const redo = () => {
  if (commandStack && commandStack.canRedo()) {
    commandStack.redo()
    emit('command-stack-changed', {
      canUndo: commandStack.canUndo(),
      canRedo: commandStack.canRedo()
    })
  }
}

const getConnectionMidpoint = (waypoints = []) => {
  const segments = []
  let totalLength = 0
  for (let index = 1; index < waypoints.length; index += 1) {
    const start = waypoints[index - 1]
    const end = waypoints[index]
    const dx = end.x - start.x
    const dy = end.y - start.y
    const length = Math.hypot(dx, dy)
    if (length <= 0) continue
    segments.push({ start, dx, dy, length })
    totalLength += length
  }
  if (!segments.length) return null

  const midpointLength = totalLength / 2
  let travelled = 0
  for (const segment of segments) {
    if (travelled + segment.length >= midpointLength) {
      const ratio = (midpointLength - travelled) / segment.length
      return {
        x: segment.start.x + segment.dx * ratio,
        y: segment.start.y + segment.dy * ratio,
        dx: segment.dx,
        dy: segment.dy
      }
    }
    travelled += segment.length
  }

  const lastSegment = segments[segments.length - 1]
  return {
    x: lastSegment.start.x + lastSegment.dx,
    y: lastSegment.start.y + lastSegment.dy,
    dx: lastSegment.dx,
    dy: lastSegment.dy
  }
}

const getElementBounds = (element) => {
  if (!element || !Number.isFinite(element.x) || !Number.isFinite(element.y)
    || !Number.isFinite(element.width) || !Number.isFinite(element.height)) {
    return null
  }
  return {
    left: element.x,
    top: element.y,
    right: element.x + element.width,
    bottom: element.y + element.height
  }
}

const overlapsBounds = (rect, bounds, padding = 0) => {
  if (!bounds) return false
  return rect.left < bounds.right + padding
    && rect.right > bounds.left - padding
    && rect.top < bounds.bottom + padding
    && rect.bottom > bounds.top - padding
}

const getConnectionOverlayPlacement = (element, count, elementRegistry) => {
  const waypoints = element.waypoints || []
  const midpoint = getConnectionMidpoint(waypoints)
  if (!midpoint) {
    return {
      position: { top: -24, left: 0 },
      leaderX: 0,
      leaderY: 24
    }
  }

  const minX = Math.min(...waypoints.map(point => point.x))
  const minY = Math.min(...waypoints.map(point => point.y))
  const directionLength = Math.hypot(midpoint.dx, midpoint.dy) || 1
  let normalX = -midpoint.dy / directionLength
  let normalY = midpoint.dx / directionLength

  if (Math.abs(normalY) > 0.15) {
    if (normalY > 0) {
      normalX *= -1
      normalY *= -1
    }
  } else if (normalX < 0) {
    normalX *= -1
    normalY *= -1
  }

  const badgeWidth = count > 99 ? 42 : 28
  const badgeHeight = 24
  const allElements = elementRegistry.getAll()
  const labelBounds = allElements
    .filter(item => item.labelTarget?.id === element.id)
    .map(getElementBounds)
    .filter(Boolean)
  const shapeBounds = allElements
    .filter(item => !item.waypoints && !item.labelTarget && item.id !== element.id)
    .map(getElementBounds)
    .filter(Boolean)

  const candidates = [24, -24, 38, -38].map(distance => {
    const centerX = midpoint.x + normalX * distance
    const centerY = midpoint.y + normalY * distance
    const rect = {
      left: centerX - badgeWidth / 2,
      right: centerX + badgeWidth / 2,
      top: centerY - badgeHeight / 2,
      bottom: centerY + badgeHeight / 2
    }
    const labelCollisions = labelBounds.filter(bounds => overlapsBounds(rect, bounds, 6)).length
    const shapeCollisions = shapeBounds.filter(bounds => overlapsBounds(rect, bounds, 4)).length
    return {
      centerX,
      centerY,
      score: labelCollisions * 1000 + shapeCollisions * 100 + Math.abs(distance)
    }
  })

  const placement = candidates.sort((left, right) => left.score - right.score)[0]
  return {
    position: {
      left: placement.centerX - minX,
      top: placement.centerY - minY
    },
    leaderX: midpoint.x - placement.centerX,
    leaderY: midpoint.y - placement.centerY
  }
}

const createActionBadge = (element, count, connectionPlacement) => {
  const isConnection = Boolean(element.waypoints?.length)
  const badge = document.createElement('button')
  const label = isConnection ? '连线' : '节点'
  badge.type = 'button'
  badge.className = `flow-action-count-badge flow-action-count-badge--${isConnection ? 'connection' : 'node'}`
  badge.title = `该${label}配置了 ${count} 个流程动作，点击查看`
  badge.setAttribute('aria-label', badge.title)

  const value = document.createElement('span')
  value.className = 'flow-action-count-badge__value'
  value.textContent = count > 99 ? '99+' : String(count)
  badge.appendChild(value)

  if (isConnection && connectionPlacement) {
    const leaderLength = Math.hypot(connectionPlacement.leaderX, connectionPlacement.leaderY)
    const leaderAngle = Math.atan2(connectionPlacement.leaderY, connectionPlacement.leaderX) * 180 / Math.PI
    badge.style.setProperty('--flow-action-leader-x', `${connectionPlacement.leaderX}px`)
    badge.style.setProperty('--flow-action-leader-y', `${connectionPlacement.leaderY}px`)
    badge.style.setProperty('--flow-action-leader-length', `${leaderLength}px`)
    badge.style.setProperty('--flow-action-leader-angle', `${leaderAngle}deg`)
    const anchor = document.createElement('span')
    anchor.className = 'flow-action-count-badge__anchor'
    badge.appendChild(anchor)
  }

  const canvas = modeler.value.get('canvas')
  badge.addEventListener('mouseenter', () => canvas.addMarker(element, 'flow-action-badge-highlight'))
  badge.addEventListener('mouseleave', () => canvas.removeMarker(element, 'flow-action-badge-highlight'))
  badge.addEventListener('click', event => {
    event.preventDefault()
    event.stopPropagation()
    activateElement(element)
  })
  return badge
}

const renderActionOverlays = () => {
  if (!modeler.value) return
  const overlays = modeler.value.get('overlays')
  const canvas = modeler.value.get('canvas')
  const elementRegistry = modeler.value.get('elementRegistry')
  actionOverlayIds.forEach(id => {
    if (overlays.get(id)) overlays.remove(id)
  })
  actionOverlayElementIds.forEach(id => {
    const element = elementRegistry.get(id)
    if (element) canvas.removeMarker(element, 'flow-action-badge-highlight')
  })
  actionOverlayIds = []
  actionOverlayElementIds = []
  Object.entries(currentActionCounts).forEach(([elementId, count]) => {
    if (!count) return
    try {
      const element = elementRegistry.get(elementId)
      if (!element) return
      const isConnection = Boolean(element.waypoints?.length)
      const connectionPlacement = isConnection
        ? getConnectionOverlayPlacement(element, count, elementRegistry)
        : null
      const overlayId = overlays.add(elementId, {
        position: connectionPlacement?.position || {
          top: -6,
          left: element.width + 6
        },
        html: createActionBadge(element, count, connectionPlacement)
      })
      actionOverlayIds.push(overlayId)
      actionOverlayElementIds.push(elementId)
    } catch (error) {
      console.debug('添加流程动作标记失败:', elementId, error)
    }
  })
}

const scheduleActionOverlayRefresh = () => {
  if (!modeler.value || !Object.keys(currentActionCounts).length) return
  if (actionOverlayRefreshFrame) cancelAnimationFrame(actionOverlayRefreshFrame)
  actionOverlayRefreshFrame = requestAnimationFrame(() => {
    actionOverlayRefreshFrame = null
    renderActionOverlays()
  })
}

const setActionCounts = (counts = {}) => {
  currentActionCounts = { ...counts }
  renderActionOverlays()
}

watch(() => props.xml, (newXml) => {
  if (newXml) loadXml(newXml)
}, { immediate: false })

onMounted(() => {
  initModeler()
  if (props.xml) loadXml(props.xml)
})

onUnmounted(() => {
  if (actionOverlayRefreshFrame) {
    cancelAnimationFrame(actionOverlayRefreshFrame)
  }
  if (modeler.value) {
    modeler.value.destroy()
  }
})

defineExpose({
  loadXml,
  getXml,
  undo,
  redo,
  setActionCounts,
  getModeler: () => modeler.value
})
</script>

<template>
  <div ref="canvasRef" class="vue-bpmn-designer-canvas"></div>
</template>

<style scoped>
.vue-bpmn-designer-canvas {
  width: 100%;
  height: 100%;
}
</style>
<style>
/* 仅设计器显示网格背景，查看器不显示 */
.vue-bpmn-designer-canvas.djs-container,
.vue-bpmn-designer-canvas .djs-container {
  background-image:
    linear-gradient(to right, #e0e0e0 1px, transparent 1px),
    linear-gradient(to bottom, #e0e0e0 1px, transparent 1px);
  background-size: 20px 20px;
}

.djs-container svg {
  background-color: transparent !important;
}

.bjs-powered-by {
  display: none !important;
}
.djs-palette {
  left: 20px;
  top: 20px;
}

.flow-action-count-badge {
  position: relative;
  z-index: 2;
  display: inline-flex;
  min-width: 24px;
  height: 24px;
  box-sizing: border-box;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  border: 2px solid #fff;
  border-radius: 12px;
  background: #7c3aed;
  box-shadow: 0 2px 7px rgb(0 0 0 / 24%);
  color: #fff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  transform: translate(-50%, -50%);
  transition: box-shadow 0.16s ease, transform 0.16s ease;
  isolation: isolate;
  overflow: visible;
}

.flow-action-count-badge:hover,
.flow-action-count-badge:focus-visible {
  box-shadow: 0 3px 10px rgb(124 58 237 / 42%);
  outline: none;
  transform: translate(-50%, -50%);
}

.flow-action-count-badge--node {
  background: #7c3aed;
}

.flow-action-count-badge--connection {
  background: #2563eb;
}

.flow-action-count-badge--connection::before {
  position: absolute;
  z-index: -1;
  top: 50%;
  left: 50%;
  width: var(--flow-action-leader-length);
  height: 2px;
  border-radius: 1px;
  background: #2563eb;
  content: '';
  pointer-events: none;
  transform: rotate(var(--flow-action-leader-angle));
  transform-origin: 0 50%;
}

.flow-action-count-badge__anchor {
  position: absolute;
  top: calc(50% + var(--flow-action-leader-y));
  left: calc(50% + var(--flow-action-leader-x));
  width: 7px;
  height: 7px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #2563eb;
  box-shadow: 0 1px 3px rgb(0 0 0 / 24%);
  pointer-events: none;
  transform: translate(-50%, -50%);
}

.flow-action-count-badge__value {
  position: relative;
  z-index: 1;
}

.djs-element.flow-action-badge-highlight .djs-visual > :first-child {
  stroke: #7c3aed !important;
  stroke-width: 3px !important;
  filter: drop-shadow(0 0 3px rgb(124 58 237 / 45%));
}
.djs-overlay-context-pad {
  display: none;
}
</style>
