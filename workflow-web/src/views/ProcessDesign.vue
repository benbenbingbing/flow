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
    
    <!-- XML 查看弹窗 -->
    <el-dialog
      v-model="xmlDialogVisible"
      title="BPMN XML"
      class="xml-fullscreen-dialog"
      :close-on-click-modal="false"
      :fullscreen="true"
    >
      <div class="xml-editor-wrapper">
        <Codemirror
          v-model="xmlContent"
          :extensions="xmlExtensions"
          :style="xmlEditorStyle"
          :autofocus="false"
          :indent-with-tab="true"
          :tab-size="2"
          disabled
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="xmlDialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="copyXML">
            <el-icon><Document /></el-icon>复制
          </el-button>
        </div>
      </template>
    </el-dialog>
    
    <div class="design-container">
      <VueBpmnDesigner
        ref="designerRef"
        class="canvas"
        @element-click="onElementClick"
        @command-stack-changed="onCommandStackChanged"
        @imported="onImported"
      />
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, Check, Back, Right } from '@element-plus/icons-vue'
import { layoutProcess } from 'bpmn-auto-layout'
import { processApi } from '@/api/process'
import formatXML from 'xml-formatter'
import NodeConfigPanel from '@/components/NodeConfigPanel.vue'
import VueBpmnDesigner from '@/components/VueBpmnDesigner.vue'
import { Codemirror } from 'vue-codemirror'
import { xml } from '@codemirror/lang-xml'
import { oneDark } from '@codemirror/theme-one-dark'
import { EditorView } from '@codemirror/view'

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
    
    // 确保生成的 XML 包含 flowable 命名空间
    if (!layoutedXml.includes('xmlns:flowable')) {
      return layoutedXml.replace(
        '<bpmn:definitions',
        '<bpmn:definitions xmlns:flowable="http://flowable.org/bpmn"'
      )
    }
    return layoutedXml
  } catch (error) {
    console.error('布局生成失败:', error)
    return xml
  }
}

const route = useRoute()
const router = useRouter()
const processId = route.params.id

const designerRef = ref()
const processData = ref({})
const selectedElement = ref(null)

// 撤销/重做状态
const canUndo = ref(false)
const canRedo = ref(false)

// XML 查看弹窗
const xmlDialogVisible = ref(false)
const xmlContent = ref('')
const xmlExtensions = [xml(), oneDark, EditorView.lineWrapping]
const xmlEditorStyle = { height: '100%', fontSize: '14px' }

// 节点配置是否有未落库的暂存变更（写入了 bpmn-js 内存但还没点"保存流程"）
const hasUnsavedNodeChanges = ref(false)

// 默认空白流程（包含 flowable 命名空间）
const defaultXML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:flowable="http://flowable.org/bpmn"
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

// 撤销
const handleUndo = () => {
  designerRef.value?.undo()
}

// 重做
const handleRedo = () => {
  designerRef.value?.redo()
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

const onElementClick = (element) => {
  selectedElement.value = element
}

const onCommandStackChanged = ({ canUndo: undo, canRedo: redo }) => {
  canUndo.value = undo
  canRedo.value = redo
}

const onImported = () => {
  // 导入完成后的回调（如果需要）
}

const loadProcess = async () => {
  if (!processId) {
    await designerRef.value?.loadXml(defaultXML)
    return
  }
  
  try {
    const data = await processApi.getById(processId)
    processData.value = data
    let xml = data.bpmnXml || defaultXML
    
    // 确保 XML 包含 flowable 命名空间
    if (!xml.includes('xmlns:flowable')) {
      xml = xml.replace(
        '<bpmn:definitions',
        '<bpmn:definitions xmlns:flowable="http://flowable.org/bpmn"'
      )
    }
    
    // 如果XML缺少DI，自动生成布局
    if (!xml.includes('BPMNDiagram') || !xml.includes('BPMNShape')) {
      xml = await fixXmlLayout(xml)
    }
    
    await designerRef.value?.loadXml(xml)
  } catch (error) {
    console.error(error)
    ElMessage.error('加载流程失败')
  }
}

// 格式化 XML（使用 xml-formatter 做标准缩进和换行）
const formatXMLContent = (xml) => {
  try {
    return formatXML(xml, {
      indentation: '  ',
      collapseContent: false,
      lineSeparator: '\n'
    })
  } catch (e) {
    console.warn('XML 格式化失败，返回原文:', e)
    return xml
  }
}

// 复制 XML 到剪贴板
const copyXML = () => {
  navigator.clipboard.writeText(xmlContent.value).then(() => {
    ElMessage.success('XML 已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

const handleSaveXML = async () => {
  try {
    const xml = await designerRef.value?.getXml()
    if (xml) {
      xmlContent.value = formatXMLContent(xml)
      xmlDialogVisible.value = true
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('获取 XML 失败')
  }
}

const handleSave = async () => {
  try {
    const xml = await designerRef.value?.getXml()
    if (!xml) return
    
    // 确保 isExecutable="true"，否则 Flowable 无法发布流程
    let executableXml = xml.replace(/isExecutable="false"/g, 'isExecutable="true"')
    
    // 确保 XML 包含 flowable 命名空间
    if (!executableXml.includes('xmlns:flowable')) {
      executableXml = executableXml.replace(
        '<bpmn:definitions',
        '<bpmn:definitions xmlns:flowable="http://flowable.org/bpmn"'
      )
    }
    
    if (processId) {
      await processApi.update(processId, {
        ...processData.value,
        bpmnXml: executableXml
      })
    } else {
      ElMessage.info('请先填写流程基本信息')
      return
    }

    hasUnsavedNodeChanges.value = false
    ElMessage.success('保存成功')
  } catch (error) {
    console.error(error)
    ElMessage.error('保存失败')
  }
}

const handleNodeConfigSave = () => {
  // 节点配置只写入了 bpmn-js 内存模型，未落库。
  // 仅标记脏状态，不弹提示，避免一次保存弹出多个 toast。
  // 真正落库由顶部"保存流程"按钮完成，落库成功后才提示。
  hasUnsavedNodeChanges.value = true
}

onMounted(() => {
  loadProcess()
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
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
  overflow: auto;
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

/* 历史操作按钮组 */
.history-actions {
  margin-right: 8px;
}

/* 分割线 */
.header-right .el-divider {
  margin: 0 12px;
}

/* XML 全屏弹窗样式：标题栏/底部固定，仅编辑器内容区域滚动 */
/* 注意：fullscreen 模式下 xml-fullscreen-dialog 与 el-dialog 在同一元素，
   必须用同元素选择器（无空格）；header/body/footer 是 el-dialog 内部元素，
   需用 :deep() 穿透 scoped 作用域 */
.xml-fullscreen-dialog.el-dialog {
  margin: 0 !important;
  width: 100vw !important;
  height: 100vh !important;
  max-width: 100vw !important;
  max-height: 100vh !important;
  border-radius: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
}

.xml-fullscreen-dialog :deep(.el-dialog__header) {
  flex-shrink: 0 !important;
  padding: 16px 20px !important;
}

.xml-fullscreen-dialog :deep(.el-dialog__body) {
  flex: 1 !important;
  min-height: 0 !important;
  padding: 0 !important;
  overflow: hidden !important;
  display: flex !important;
  flex-direction: column !important;
}

.xml-fullscreen-dialog :deep(.el-dialog__footer) {
  flex-shrink: 0 !important;
  padding: 12px 20px !important;
}

.xml-editor-wrapper {
  height: 100%;
  width: 100%;
  overflow: hidden;
}

/* Codemirror 6：editor 用 flex 列布局，scroller 作为唯一滚动容器，
   这样标题栏/底部固定，只有编辑器内容区域滚动 */
.xml-editor-wrapper :deep(.cm-editor) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.xml-editor-wrapper :deep(.cm-scroller) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.xml-editor-wrapper :deep(.cm-editor.cm-focused) {
  outline: none;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

<!-- 全局兜底：确保 XML 全屏弹窗的标题栏/底部固定，仅编辑器区域滚动 -->
<!-- fullscreen 模式下 xml-fullscreen-dialog 与 el-dialog 同元素，须用同元素选择器 -->
<style>
.el-dialog.xml-fullscreen-dialog {
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
  margin: 0 !important;
  width: 100vw !important;
  height: 100vh !important;
  max-width: 100vw !important;
  max-height: 100vh !important;
  border-radius: 0 !important;
}

.xml-fullscreen-dialog .el-dialog__header {
  flex-shrink: 0 !important;
}

.xml-fullscreen-dialog .el-dialog__body {
  flex: 1 !important;
  min-height: 0 !important;
  overflow: hidden !important;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.xml-fullscreen-dialog .el-dialog__footer {
  flex-shrink: 0 !important;
}

/* Codemirror 6：editor 列布局，scroller 唯一滚动容器（全局兜底，防止 :deep 失效） */
.xml-fullscreen-dialog .xml-editor-wrapper {
  height: 100%;
  width: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.xml-fullscreen-dialog .xml-editor-wrapper .cm-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.xml-fullscreen-dialog .xml-editor-wrapper .cm-scroller {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
