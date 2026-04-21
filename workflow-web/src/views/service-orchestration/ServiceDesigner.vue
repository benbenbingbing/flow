<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑服务' : '新建服务'"
    width="1000px"
    :close-on-click-modal="false"
    top="5vh"
  >
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 基础配置 -->
      <el-tab-pane label="基础配置" name="basic">
        <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="服务名称" prop="serviceName">
                <el-input v-model="form.serviceName" placeholder="请输入服务名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="服务编码" prop="serviceCode">
                <el-input v-model="form.serviceCode" placeholder="请输入服务编码" :disabled="isEdit" />
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="服务类型" prop="serviceType">
                <el-select v-model="form.serviceType" placeholder="选择服务类型" style="width: 100%">
                  <el-option label="服务编排" value="ORCHESTRATION" />
                  <el-option label="脚本服务" value="SCRIPT" />
                  <el-option label="代理服务" value="PROXY" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="分类" prop="categoryId">
                <el-select v-model="form.categoryId" placeholder="选择分类" style="width: 100%">
                  <el-option
                    v-for="cat in categories"
                    :key="cat.id"
                    :label="cat.categoryName"
                    :value="cat.id"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-form-item label="服务描述">
            <el-input v-model="form.description" type="textarea" rows="3" placeholder="服务描述" />
          </el-form-item>
          
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="超时时间">
                <el-input-number v-model="form.timeoutMs" :min="1000" :max="300000" :step="1000" style="width: 100%" />
                <span class="form-tip">毫秒</span>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-tab-pane>
      
      <!-- 流程编排（简化版） -->
      <el-tab-pane label="流程编排" name="flow" v-if="form.serviceType === 'ORCHESTRATION'">
        <div class="flow-designer">
          <div class="flow-toolbar">
            <el-button-group>
              <el-button @click="handleAddNode('START')">开始</el-button>
              <el-button @click="handleAddNode('ENTITY_CRUD')">实体操作</el-button>
              <el-button @click="handleAddNode('HTTP')">HTTP</el-button>
              <el-button @click="handleAddNode('SQL')">SQL</el-button>
              <el-button @click="handleAddNode('SCRIPT')">脚本</el-button>
              <el-button @click="handleAddNode('CONDITION')">条件</el-button>
              <el-button @click="handleAddNode('END')">结束</el-button>
            </el-button-group>
          </div>
          
          <div class="flow-canvas">
            <el-empty v-if="!nodes.length" description="点击上方按钮添加节点" />
            <div v-else class="node-list">
              <div 
                v-for="(node, index) in nodes" 
                :key="node.nodeId"
                class="flow-node"
                :class="'node-' + node.nodeType.toLowerCase()"
              >
                <div class="node-header">
                  <span class="node-type">{{ getNodeTypeLabel(node.nodeType) }}</span>
                  <el-button link type="danger" size="small" @click="handleRemoveNode(index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <div class="node-content">
                  <el-input v-model="node.nodeName" placeholder="节点名称" size="small" />
                </div>
                <div class="node-config" v-if="node.nodeType !== 'START' && node.nodeType !== 'END'">
                  <el-button link type="primary" size="small" @click="handleConfigNode(node)">
                    配置
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
    
    <!-- 节点配置弹窗 -->
    <el-dialog
      v-model="nodeConfigVisible"
      title="节点配置"
      width="600px"
      append-to-body
    >
      <el-form :model="currentNode" label-width="100px">
        <el-form-item label="节点ID">
          <el-input v-model="currentNode.nodeId" disabled />
        </el-form-item>
        <el-form-item label="节点名称">
          <el-input v-model="currentNode.nodeName" />
        </el-form-item>
        <el-form-item label="配置JSON">
          <el-input
            v-model="currentNode.config"
            type="textarea"
            rows="10"
            placeholder='{"key": "value"}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeConfigVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveNodeConfig">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { saveService, getServiceConfig, getServiceCategories } from '@/api/service-orchestration'

const props = defineProps({
  modelValue: Boolean,
  serviceId: String
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!props.serviceId)

const formRef = ref()
const submitting = ref(false)
const activeTab = ref('basic')
const categories = ref([])
const nodes = ref([])
const nodeConfigVisible = ref(false)
const currentNode = ref({})
let nodeIdCounter = 1

const form = reactive({
  serviceName: '',
  serviceCode: '',
  serviceType: 'ORCHESTRATION',
  categoryId: '',
  description: '',
  timeoutMs: 30000
})

const rules = {
  serviceName: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  serviceType: [{ required: true, message: '请选择服务类型', trigger: 'change' }]
}

// 加载分类
const loadCategories = async () => {
  try {
    const res = await getServiceCategories()
    categories.value = res || []
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

// 加载服务详情
const loadServiceDetail = async () => {
  if (!props.serviceId) {
    resetForm()
    return
  }
  
  try {
    const res = await getServiceConfig(props.serviceId)
    const service = res.service
    Object.assign(form, service)
    nodes.value = res.nodes || []
  } catch (error) {
    console.error('加载服务详情失败:', error)
    ElMessage.error('加载服务详情失败')
  }
}

const resetForm = () => {
  form.serviceName = ''
  form.serviceCode = ''
  form.serviceType = 'ORCHESTRATION'
  form.categoryId = ''
  form.description = ''
  form.timeoutMs = 30000
  nodes.value = []
  nodeIdCounter = 1
}

// 添加节点
const handleAddNode = (type) => {
  const nodeId = `node_${nodeIdCounter++}`
  nodes.value.push({
    nodeId: nodeId,
    nodeType: type,
    nodeName: getNodeTypeLabel(type),
    config: '{}',
    nextNodes: '[]',
    positionX: 100,
    positionY: nodes.value.length * 100 + 50
  })
}

// 删除节点
const handleRemoveNode = (index) => {
  nodes.value.splice(index, 1)
}

// 配置节点
const handleConfigNode = (node) => {
  currentNode.value = { ...node }
  nodeConfigVisible.value = true
}

// 保存节点配置
const handleSaveNodeConfig = () => {
  const index = nodes.value.findIndex(n => n.nodeId === currentNode.value.nodeId)
  if (index >= 0) {
    nodes.value[index] = { ...currentNode.value }
  }
  nodeConfigVisible.value = false
}

// 获取节点类型标签
const getNodeTypeLabel = (type) => {
  const map = {
    'START': '开始',
    'END': '结束',
    'ENTITY_CRUD': '实体操作',
    'HTTP': 'HTTP调用',
    'SQL': 'SQL查询',
    'SCRIPT': '脚本执行',
    'CONDITION': '条件分支',
    'PARALLEL': '并行分支',
    'JOIN': '聚合',
    'MAPPING': '数据映射',
    'LOG': '日志记录'
  }
  return map[type] || type
}

// 提交
const handleSubmit = async () => {
  await formRef.value.validate()
  
  submitting.value = true
  try {
    await saveService({
      service: form,
      nodes: nodes.value
    })
    
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    emit('success')
    visible.value = false
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, (val) => {
  if (val) {
    loadCategories()
    loadServiceDetail()
  }
})
</script>

<style scoped lang="scss">
.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.flow-designer {
  .flow-toolbar {
    margin-bottom: 15px;
    padding: 10px;
    background: #f5f7fa;
    border-radius: 4px;
  }
  
  .flow-canvas {
    min-height: 400px;
    border: 1px dashed #dcdfe6;
    border-radius: 4px;
    padding: 20px;
    
    .node-list {
      display: flex;
      flex-direction: column;
      gap: 15px;
      
      .flow-node {
        width: 280px;
        border: 2px solid #dcdfe6;
        border-radius: 8px;
        padding: 10px;
        background: white;
        
        &.node-start {
          border-color: #67c23a;
        }
        &.node-end {
          border-color: #f56c6c;
        }
        &.node-condition {
          border-color: #e6a23c;
        }
        
        .node-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 10px;
          
          .node-type {
            font-weight: bold;
            color: #303133;
          }
        }
        
        .node-content {
          margin-bottom: 10px;
        }
        
        .node-config {
          text-align: right;
        }
      }
    }
  }
}
</style>
