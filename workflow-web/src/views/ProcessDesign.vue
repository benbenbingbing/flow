<template>
  <div class="process-design">
    <div class="design-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="process-name">{{ processData.processName || '新建流程' }}</span>
      </div>
      <div class="header-right">
        <!-- 撤销/重做按钮 -->
        <el-button-group class="history-actions">
          <el-button 
            :type="canUndo ? 'primary' : 'default'"
            :disabled="!canUndo" 
            @click="handleUndo"
            title="撤销 (Ctrl+Z)"
          >
            <el-icon><Back /></el-icon>
          </el-button>
          <el-button 
            :type="canRedo ? 'primary' : 'default'"
            :disabled="!canRedo" 
            @click="handleRedo"
            title="重做 (Ctrl+Y)"
          >
            <el-icon><Right /></el-icon>
          </el-button>
        </el-button-group>
        
        <el-divider direction="vertical" />
        
        <el-button @click="handleSaveXML">
          <el-icon><Document /></el-icon>查看XML
        </el-button>
        <el-button type="primary" @click="handleSave">
          <el-icon><Check /></el-icon>保存流程
        </el-button>
      </div>
    </div>
    
    <div class="design-container">
      <div ref="canvasRef" class="canvas"></div>
      <div class="config-panel">
        <div class="panel-title">节点配置</div>
        <div class="panel-content">
          <p class="tip">点击流程节点进行配置</p>
          <NodeConfigPanel
            v-if="selectedElement"
            :element="selectedElement"
            :process-id="processId"
            @save="handleNodeConfigSave"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, Check, Back, Right } from '@element-plus/icons-vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { layoutProcess } from 'bpmn-auto-layout'
import { processApi } from '@/api/process'
import NodeConfigPanel from '@/components/NodeConfigPanel.vue'

import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'

// BPMN 汉化配置
const translations = {
  // 小扳手菜单
  'Append end event': '追加结束事件',
  'Append gateway': '追加网关',
  'Append task': '追加用户任务',
  'Append user task': '追加用户任务',
  'Append intermediate/boundary event': '追加中间/边界事件',
  'Change type': '更改类型',
  'Remove': '删除',
  'Connect using sequence/message flow or association': '连接',
  'Activate the global connect tool': '全局连接工具',
  
  // 左侧工具栏
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
  
  // 替换面板标题
  'Change element': '更改元素类型',
  
  // 元素类型
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
  
  // 事件类型
  'Timer': '定时器',
  'Message': '消息',
  'Signal': '信号',
  'Error': '错误',
  'Escalation': '升级',
  'Compensation': '补偿',
  'Link': '链接',
  'Condition': '条件',
  'Cancel': '取消',
  
  // 工具提示
  'Activate hand tool': '手型工具',
  'Activate lasso tool': '套索工具',
  'Activate create/remove space tool': '空间工具',
  'Global connect tool': '全局连接',
  
  // 其他
  'Sequence flow': '顺序流',
  'Default flow': '默认流',
  'Conditional flow': '条件流'
}

// 自定义翻译函数
const customTranslate = (template, replacements = {}) => {
  let result = translations[template] || template
  Object.keys(replacements).forEach(key => {
    result = result.replace(new RegExp('{' + key + '}', 'g'), replacements[key])
  })
  return result
}

// 汉化模块定义 - 覆盖内置 translate 服务
const customTranslateModule = {
  translate: ['value', customTranslate]
}

/**
 * 检查并修复XML布局
 */
const fixXmlLayout = async (xml) => {
  // 如果已有DI，直接返回
  if (xml.includes('BPMNDiagram') && xml.includes('BPMNShape')) {
    return xml
  }
  
  console.log('XML缺少布局信息，使用bpmn-auto-layout生成...')
  try {
    const layoutedXml = await layoutProcess(xml)
    console.log('布局生成成功，新XML长度:', layoutedXml.length)
    return layoutedXml
  } catch (error) {
    console.error('布局生成失败:', error)
    return xml
  }
}

const route = useRoute()
const router = useRouter()
const processId = route.params.id

const canvasRef = ref()
const bpmnModeler = ref(null)
const processData = ref({})
const selectedElement = ref(null)

// 撤销/重做状态
const canUndo = ref(false)
const canRedo = ref(false)

// 命令栈（用于撤销/重做）
let commandStack = null

// 默认空白流程
const defaultXML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  id="Definitions_1"
                  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="152" width="36" height="36"/>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

const initBpmnModeler = () => {
  try {
    bpmnModeler.value = new BpmnModeler({
      container: canvasRef.value,
      additionalModules: [customTranslateModule],
      // 修改Palette默认条目
      moddleExtensions: {
        // 可以在这里添加扩展
      }
    })
    
    // 尝试通过injector覆盖translate服务
    try {
      const injector = bpmnModeler.value.get('injector')
      if (injector) {
        // 覆盖translate服务实例
        injector._instances.translate = customTranslate
        console.log('翻译服务已覆盖')
      }
    } catch (e) {
      console.warn('翻译服务覆盖失败:', e)
    }
    
    // 获取命令栈服务
    try {
      commandStack = bpmnModeler.value.get('commandStack')
      
      // 监听命令栈变化，更新撤销/重做按钮状态
      if (commandStack) {
        commandStack.on('changed', () => {
          canUndo.value = commandStack.canUndo()
          canRedo.value = commandStack.canRedo()
        })
      }
    } catch (e) {
      console.warn('命令栈服务不可用:', e)
    }
    
    // 自定义Palette - 移除通用任务，改为用户任务
    try {
      const palette = bpmnModeler.value.get('palette')
      const originalGetEntries = palette._providers[0].getPaletteEntries
      palette._providers[0].getPaletteEntries = function() {
        const entries = originalGetEntries.apply(this, arguments)
        
        // 移除默认的通用任务创建按钮
        delete entries['create-task']
        
        // 添加用户任务创建按钮
        const elementFactory = bpmnModeler.value.get('elementFactory')
        const create = bpmnModeler.value.get('create')
        
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
    } catch (e) {
      console.warn('Palette自定义失败:', e)
    }
    
    // 自定义ContextPad - 修改追加任务为追加用户任务
    try {
      const contextPad = bpmnModeler.value.get('contextPad')
      const elementFactory = bpmnModeler.value.get('elementFactory')
      const autoPlace = bpmnModeler.value.get('autoPlace')
      
      contextPad.registerProvider({
        getContextPadEntries: function(element) {
          return function(entries) {
            // 修改追加任务的默认行为
            if (entries['append.append-task']) {
              entries['append.append-task'].title = '追加用户任务'
              entries['append.append-task'].className = 'bpmn-icon-user-task'
              entries['append.append-task'].action = {
                click: function(event, element) {
                  const modeling = bpmnModeler.value.get('modeling')
                  const userTask = elementFactory.createShape({
                    type: 'bpmn:UserTask'
                  })
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
    } catch (e) {
      console.warn('ContextPad自定义失败:', e)
    }
    
    // 监听元素点击事件 - 支持所有可配置节点
    bpmnModeler.value.on('element.click', (e) => {
      const element = e.element
      const configurableTypes = [
        'bpmn:UserTask', 'bpmn:ServiceTask', 'bpmn:ManualTask', 
        'bpmn:ScriptTask', 'bpmn:BusinessRuleTask', 'bpmn:SendTask', 'bpmn:ReceiveTask',
        'bpmn:StartEvent', 'bpmn:EndEvent',
        'bpmn:ExclusiveGateway', 'bpmn:ParallelGateway', 'bpmn:InclusiveGateway', 'bpmn:EventBasedGateway',
        'bpmn:SequenceFlow'
      ]
      if (configurableTypes.some(type => element.type?.includes(type))) {
        // 将modeler实例附加到元素上，供配置面板使用
        element._modeler = bpmnModeler.value
        selectedElement.value = element
      }
    })
    
    // 监听键盘事件（Ctrl+Z 撤销，Ctrl+Y 重做）
    document.addEventListener('keydown', handleKeydown)
  } catch (error) {
    console.error('初始化 BPMN Modeler 失败:', error)
    ElMessage.error('流程设计器初始化失败')
  }
}

// 撤销
const handleUndo = () => {
  if (commandStack && commandStack.canUndo()) {
    commandStack.undo()
  }
}

// 重做
const handleRedo = () => {
  if (commandStack && commandStack.canRedo()) {
    commandStack.redo()
  }
}

// 键盘快捷键处理
const handleKeydown = (e) => {
  // Ctrl+Z 撤销
  if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
    e.preventDefault()
    handleUndo()
  }
  // Ctrl+Y 或 Ctrl+Shift+Z 重做
  if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.key === 'z' && e.shiftKey))) {
    e.preventDefault()
    handleRedo()
  }
}

const loadProcess = async () => {
  if (!processId) {
    await importXML(defaultXML)
    return
  }
  
  try {
    const data = await processApi.getById(processId)
    processData.value = data
    let xml = data.bpmnXml || defaultXML
    
    // 如果XML缺少DI，自动生成布局
    if (!xml.includes('BPMNDiagram') || !xml.includes('BPMNShape')) {
      xml = await fixXmlLayout(xml)
    }
    
    await importXML(xml)
  } catch (error) {
    console.error(error)
    ElMessage.error('加载流程失败')
  }
}

const importXML = async (xml) => {
  try {
    await bpmnModeler.value.importXML(xml)
    const canvas = bpmnModeler.value.get('canvas')
    canvas.zoom('fit-viewport', 'auto')
  } catch (error) {
    console.error('导入XML失败:', error)
    ElMessage.error('导入流程图失败')
  }
}

const handleSaveXML = async () => {
  try {
    const { xml } = await bpmnModeler.value.saveXML({ format: true })
    console.log(xml)
    ElMessage.success('XML已输出到控制台')
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  }
}

const handleSave = async () => {
  try {
    const { xml } = await bpmnModeler.value.saveXML({ format: true })
    
    // 确保 isExecutable="true"，否则 Flowable 无法发布流程
    const executableXml = xml.replace(/isExecutable="false"/g, 'isExecutable="true"')
    
    if (processId) {
      await processApi.update(processId, {
        ...processData.value,
        bpmnXml: executableXml
      })
    } else {
      ElMessage.info('请先填写流程基本信息')
      return
    }
    
    ElMessage.success('保存成功')
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  }
}

const handleNodeConfigSave = () => {
  ElMessage.success('节点配置已保存')
}

onMounted(() => {
  nextTick(() => {
    initBpmnModeler()
    loadProcess()
  })
})

onUnmounted(() => {
  // 移除键盘事件监听
  document.removeEventListener('keydown', handleKeydown)
  
  if (bpmnModeler.value) {
    bpmnModeler.value.destroy()
  }
})
</script>

<style scoped>
.process-design {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.design-header {
  height: 50px;
  background: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.process-name {
  font-size: 16px;
  font-weight: bold;
}

.design-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.canvas {
  flex: 1;
  background: #f5f5f5;
}

.config-panel {
  width: 400px;
  background: #fff;
  border-left: 1px solid #dcdfe6;
  display: flex;
  flex-direction: column;
}

.panel-title {
  padding: 15px;
  font-weight: bold;
  border-bottom: 1px solid #e4e7ed;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 15px;
}

.tip {
  color: #909399;
  text-align: center;
  padding: 20px;
}

/* 隐藏 bpmn.io 水印 */
:deep(.bjs-powered-by) {
  display: none !important;
}

:deep(.djs-palette) {
  left: 20px;
  top: 20px;
}

:deep(.djs-overlay-context-pad) {
  display: none;
}

/* 历史操作按钮组 */
.history-actions {
  margin-right: 8px;
}

/* 分割线 */
.header-right .el-divider {
  margin: 0 12px;
}
</style>
