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
      element._modeler = modeler.value
      emit('element-click', element)
    }
  })

  modeler.value.on('elements.changed', () => {
    if (commandStack) {
      emit('command-stack-changed', {
        canUndo: commandStack.canUndo(),
        canRedo: commandStack.canRedo()
      })
    }
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

watch(() => props.xml, (newXml) => {
  if (newXml) loadXml(newXml)
}, { immediate: false })

onMounted(() => {
  initModeler()
  if (props.xml) loadXml(props.xml)
})

onUnmounted(() => {
  if (modeler.value) {
    modeler.value.destroy()
  }
})

defineExpose({
  loadXml,
  getXml,
  undo,
  redo,
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
.djs-container {
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
.djs-overlay-context-pad {
  display: none;
}
</style>
