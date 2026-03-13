<template>
  <div class="process-detail">
    <!-- 流程基本信息 -->
    <el-card shadow="never" class="detail-section">
      <template #header>
        <div class="section-header">
          <span>流程信息</span>
          <el-tag :type="statusType">{{ statusText }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="流程名称">{{ processInfo.processName }}</el-descriptions-item>
        <el-descriptions-item label="流程实例">{{ processInfo.instanceId }}</el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ processInfo.currentNode }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ processInfo.startUser }}</el-descriptions-item>
        <el-descriptions-item label="发起时间">{{ processInfo.startTime }}</el-descriptions-item>
        <el-descriptions-item label="业务Key">{{ processInfo.businessKey || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 审批历史 -->
    <el-card shadow="never" class="detail-section">
      <template #header>
        <span>审批历史</span>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in historyList"
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
    </el-card>

    <!-- 流程图 -->
    <el-card shadow="never" class="detail-section">
      <template #header>
        <span>流程图</span>
      </template>
      <div ref="bpmnViewer" class="bpmn-viewer"></div>
    </el-card>

    <!-- 表单数据 -->
    <el-card v-if="formData && Object.keys(formData).length > 0" shadow="never" class="detail-section">
      <template #header>
        <span>表单数据</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item 
          v-for="(value, key) in formData" 
          :key="key"
          :label="key"
        >
          {{ value }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'
import Viewer from 'bpmn-js/lib/Viewer'

const props = defineProps({
  instanceId: { type: String, required: true }
})

// 流程信息
const processInfo = ref({
  processName: '',
  instanceId: '',
  currentNode: '',
  startUser: '',
  startTime: '',
  businessKey: '',
  status: ''
})

const statusText = ref('')
const statusType = ref('')

// 历史记录
const historyList = ref([])

// 表单数据
const formData = ref({})

// BPMN Viewer
const bpmnViewer = ref(null)
let viewer = null

// 加载流程详情
async function loadProcessDetail() {
  try {
    const res = await fetch(`/api/process-instance/${props.instanceId}/detail`).then(r => r.json())
    if (res.code === 200) {
      const data = res.data
      processInfo.value = {
        processName: data.processName,
        instanceId: data.instanceId,
        currentNode: data.currentNode || '-',
        startUser: data.startUser,
        startTime: data.startTime,
        businessKey: data.businessKey,
        status: data.status
      }
      
      // 设置状态显示
      const statusMap = {
        'RUNNING': { text: '运行中', type: 'primary' },
        'COMPLETED': { text: '已完成', type: 'success' },
        'SUSPENDED': { text: '已挂起', type: 'warning' },
        'TERMINATED': { text: '已终止', type: 'danger' }
      }
      const status = statusMap[data.status] || { text: data.status, type: 'info' }
      statusText.value = status.text
      statusType.value = status.type
      
      // 加载历史记录
      historyList.value = (data.history || []).map(h => ({
        title: `${h.assignee || '系统'} ${h.action}`,
        description: h.taskName || '流程发起',
        time: h.endTime || h.startTime,
        comment: h.comment,
        type: h.action === '发起' ? 'primary' : h.action === '通过' ? 'success' : h.action === '驳回' ? 'danger' : 'info',
        color: h.action === '发起' ? '#409EFF' : h.action === '通过' ? '#67C23A' : h.action === '驳回' ? '#F56C6C' : '#909399'
      }))
      
      // 加载表单数据
      formData.value = data.formData || {}
      
      // 渲染流程图
      if (data.bpmnXml) {
        renderBpmn(data.bpmnXml, data.completedNodes || [], data.currentNodeId)
      }
    }
  } catch (e) {
    console.error('加载流程详情失败:', e)
  }
}

// 渲染BPMN流程图
async function renderBpmn(xml, completedNodes, currentNodeId) {
  if (!viewer) {
    viewer = new Viewer({
      container: bpmnViewer.value
    })
  }
  
  try {
    await viewer.importXML(xml)
    
    const canvas = viewer.get('canvas')
    const overlays = viewer.get('overlays')
    
    // 高亮已完成的节点
    completedNodes.forEach(nodeId => {
      canvas.addMarker(nodeId, 'completed')
    })
    
    // 高亮当前节点
    if (currentNodeId) {
      canvas.addMarker(currentNodeId, 'active')
    }
    
    // 添加节点样式
    const elementRegistry = viewer.get('elementRegistry')
    completedNodes.forEach(nodeId => {
      const element = elementRegistry.get(nodeId)
      if (element) {
        overlays.add(nodeId, {
          position: { top: -10, right: -10 },
          html: '<div class="node-badge completed">✓</div>'
        })
      }
    })
    
    if (currentNodeId) {
      const element = elementRegistry.get(currentNodeId)
      if (element) {
        overlays.add(currentNodeId, {
          position: { top: -10, right: -10 },
          html: '<div class="node-badge active">●</div>'
        })
      }
    }
    
    // 适应视口
    canvas.zoom('fit-viewport', 'auto')
  } catch (err) {
    console.error('渲染流程图失败:', err)
  }
}

onMounted(() => {
  loadProcessDetail()
})
</script>

<style scoped>
.process-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-section {
  margin-bottom: 10px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bpmn-viewer {
  height: 400px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

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
/* BPMN 样式覆盖 */
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

.node-badge {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
}

.node-badge.completed {
  background-color: #67c23a;
}

.node-badge.active {
  background-color: #409eff;
}
</style>
