<template>
  <div class="node-config-panel">
    <!-- 节点类型标识 -->
    <div class="node-type-header">
      <el-tag :type="getNodeTypeTag(element?.type)" size="large">
        {{ nodeTypeText }}
      </el-tag>
      <span class="node-id">{{ element?.id }}</span>
    </div>
    
    <div v-if="!element" class="no-selection">
      <el-empty description="请点击流程节点进行配置" />
    </div>
    
    <el-tabs v-else v-model="activeTab" class="config-tabs">
      <!-- ========== 基本信息 ========== -->
      <el-tab-pane label="基本信息" name="basic">
        <el-form :model="basicForm" label-width="100px" size="small">
          <el-form-item label="节点名称">
            <el-input 
              v-model="basicForm.name" 
              placeholder="请输入节点名称"
              @blur="updateProperty('name', basicForm.name)"
            />
          </el-form-item>
          
          <el-form-item label="节点ID">
            <el-input v-model="basicForm.id" disabled />
          </el-form-item>
          
          <el-form-item label="说明文档">
            <el-input 
              v-model="basicForm.documentation" 
              type="textarea"
              :rows="3"
              placeholder="输入节点说明..."
              @blur="updateDocumentation"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 执行人配置（用户任务） ========== -->
      <el-tab-pane v-if="isUserTask" label="执行人" name="assignee">
        <el-form :model="assigneeForm" label-width="100px" size="small">
          <el-form-item label="执行人">
            <el-input 
              v-model="assigneeForm.assignee" 
              placeholder="如：zhangsan 或 ${userId}"
              @blur="updateProperty('assignee', assigneeForm.assignee)"
            />
            <div class="form-tip">直接指定一个用户，支持表达式</div>
          </el-form-item>
          
          <el-form-item label="候选人">
            <el-input 
              v-model="assigneeForm.candidateUsers" 
              type="textarea"
              :rows="2"
              placeholder="多个用户用逗号分隔，如：zhangsan,lisi"
              @blur="updateProperty('candidateUsers', assigneeForm.candidateUsers)"
            />
            <div class="form-tip">多个候选人，任务可被其中任意一人认领</div>
          </el-form-item>
          
          <el-form-item label="候选组">
            <el-input 
              v-model="assigneeForm.candidateGroups" 
              type="textarea"
              :rows="2"
              placeholder="多个组用逗号分隔，如：manager,hr"
              @blur="updateProperty('candidateGroups', assigneeForm.candidateGroups)"
            />
            <div class="form-tip">组成员都可处理任务</div>
          </el-form-item>
          
          <el-divider>多实例配置</el-divider>
          
          <el-form-item label="启用多实例">
            <el-switch v-model="assigneeForm.isMultiInstance" @change="onMultiInstanceChange" />
          </el-form-item>
          
          <template v-if="assigneeForm.isMultiInstance">
            <el-form-item label="执行方式">
              <el-radio-group v-model="assigneeForm.multiInstanceType">
                <el-radio-button label="parallel">并行</el-radio-button>
                <el-radio-button label="sequential">串行</el-radio-button>
              </el-radio-group>
            </el-form-item>
            
            <el-form-item label="集合变量">
              <el-input 
                v-model="assigneeForm.collection" 
                placeholder="如：${assigneeList}"
                @blur="updateMultiInstance"
              />
            </el-form-item>
            
            <el-form-item label="元素变量">
              <el-input 
                v-model="assigneeForm.elementVariable" 
                placeholder="如：assignee"
                @blur="updateMultiInstance"
              />
            </el-form-item>
            
            <el-form-item label="完成条件">
              <el-input 
                v-model="assigneeForm.completionCondition" 
                placeholder="如：${nrOfCompletedInstances >= 2}"
                @blur="updateMultiInstance"
              />
            </el-form-item>
          </template>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 服务配置（服务任务） ========== -->
      <el-tab-pane v-if="isServiceTask" label="服务" name="service">
        <el-form :model="serviceForm" label-width="100px" size="small">
          <el-form-item label="实现类型">
            <el-radio-group v-model="serviceForm.implementationType" @change="onServiceTypeChange">
              <el-radio-button label="class">Java类</el-radio-button>
              <el-radio-button label="expression">表达式</el-radio-button>
              <el-radio-button label="delegateExpression">委托</el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <el-form-item label="实现">
            <el-input 
              v-model="serviceForm.implementation" 
              :placeholder="servicePlaceholder"
              @blur="updateServiceImplementation"
            />
          </el-form-item>
          
          <el-form-item label="结果变量">
            <el-input 
              v-model="serviceForm.resultVariable" 
              placeholder="存储结果到变量"
              @blur="updateProperty('resultVariable', serviceForm.resultVariable)"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 条件配置（顺序流） ========== -->
      <el-tab-pane v-if="isSequenceFlow" label="条件" name="condition">
        <el-form :model="conditionForm" label-width="100px" size="small">
          <el-form-item label="条件类型">
            <el-radio-group v-model="conditionForm.type" @change="onConditionTypeChange">
              <el-radio-button label="">无条件</el-radio-button>
              <el-radio-button label="expression">表达式</el-radio-button>
              <el-radio-button label="default">默认流</el-radio-button>
            </el-radio-group>
          </el-form-item>
          
          <el-form-item v-if="conditionForm.type === 'expression'" label="表达式">
            <el-input 
              v-model="conditionForm.expression" 
              type="textarea"
              :rows="3"
              placeholder="如：${amount > 1000 && approved == true}"
              @blur="updateCondition"
            />
            <div class="form-tip">返回true时执行此分支</div>
          </el-form-item>
          
          <el-alert 
            v-if="conditionForm.type === 'default'" 
            type="warning" 
            :closable="false"
          >
            默认流：当其他条件都不满足时执行
          </el-alert>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 流程动作（顺序流） ========== -->
      <el-tab-pane v-if="isSequenceFlow" label="流程动作" name="actions">
        <div class="actions-section">
          <div class="actions-header">
            <span>接口动作列表</span>
            <el-button type="primary" size="small" @click="showActionDialog()">
              <el-icon><Plus /></el-icon>添加动作
            </el-button>
          </div>
          
          <el-alert type="info" :closable="false" class="action-alert">
            <template #title>
              <div class="alert-content">
                <span>流程发布后动作才生效</span>
                <el-tag v-if="hasDraftChanges" type="warning" size="small">有未发布变更</el-tag>
              </div>
            </template>
          </el-alert>
          
          <div class="actions-list">
            <div 
              v-for="(action, index) in sortedActions" 
              :key="action.id"
              class="action-item"
              :class="{ disabled: !action.enabled }"
            >
              <div class="action-sort">
                <el-button link size="small" :disabled="index === 0" @click="moveAction(index, -1)">
                  <el-icon><ArrowUp /></el-icon>
                </el-button>
                <span class="sort-number">{{ index + 1 }}</span>
                <el-button link size="small" :disabled="index === sortedActions.length - 1" @click="moveAction(index, 1)">
                  <el-icon><ArrowDown /></el-icon>
                </el-button>
              </div>
              
              <div class="action-content">
                <div class="action-name">{{ action.actionName }}</div>
                <div class="action-detail">
                  <el-tag size="small" :type="action.enabled ? 'success' : 'info'">
                    {{ action.enabled ? '启用' : '禁用' }}
                  </el-tag>
                  <span class="interface-info">{{ action.interfaceName }}</span>
                </div>
              </div>
              
              <div class="action-ops">
                <el-button link type="primary" size="small" @click="showActionDialog(action)">
                  编辑
                </el-button>
                <el-button link type="warning" size="small" @click="toggleActionEnabled(action)">
                  {{ action.enabled ? '禁用' : '启用' }}
                </el-button>
                <el-button link type="danger" size="small" @click="deleteAction(action)">
                  删除
                </el-button>
              </div>
            </div>
            
            <el-empty v-if="sortedActions.length === 0" description="暂无流程动作" />
          </div>
        </div>
        
        <!-- 动作编辑对话框 -->
        <el-dialog
          v-model="actionDialogVisible"
          :title="editingAction.id ? '编辑动作' : '添加动作'"
          width="500px"
          append-to-body
        >
          <el-form :model="editingAction" label-width="100px" size="small">
            <el-form-item label="动作名称" required>
              <el-input v-model="editingAction.actionName" placeholder="如：发送通知" />
            </el-form-item>
            
            <el-form-item label="描述">
              <el-input 
                v-model="editingAction.description" 
                type="textarea"
                :rows="2"
                placeholder="动作描述..."
              />
            </el-form-item>
            
            <el-form-item label="接口名称" required>
              <el-input 
                v-model="editingAction.interfaceName" 
                placeholder="Spring Bean名称或类名"
              />
              <div class="form-tip">如：notificationService 或 com.example.Service</div>
            </el-form-item>
            
            <el-form-item label="方法名">
              <el-input 
                v-model="editingAction.methodName" 
                placeholder="execute"
              />
              <div class="form-tip">默认为 execute</div>
            </el-form-item>
            
            <el-form-item label="参数JSON">
              <el-input 
                v-model="editingAction.paramsJson" 
                type="textarea"
                :rows="4"
                placeholder='{"key": "value"}'
              />
            </el-form-item>
            
            <el-form-item label="是否启用">
              <el-switch v-model="editingAction.enabled" />
            </el-form-item>
          </el-form>
          
          <template #footer>
            <el-button @click="actionDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveAction">确定</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
      
      <!-- ========== 表单配置（任务/开始事件） ========== -->
      <el-tab-pane v-if="isTask || isStartEvent" label="表单" name="form">
        <el-form :model="formConfig" label-width="100px" size="small">
          <el-form-item label="表单Key">
            <el-input 
              v-model="formConfig.formKey" 
              placeholder="如：leave_apply_form"
              @blur="updateProperty('formKey', formConfig.formKey)"
            />
            <div class="form-tip">关联外部表单，留空表示无表单</div>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- ========== 高级配置 ========== -->
      <el-tab-pane v-if="isTask || isGateway" label="高级" name="advanced">
        <el-form :model="advancedForm" label-width="120px" size="small">
          <el-form-item label="异步执行">
            <el-switch v-model="advancedForm.async" @change="onAsyncChange" />
          </el-form-item>
          
          <template v-if="advancedForm.async">
            <el-form-item label="异步前">
              <el-switch v-model="advancedForm.asyncBefore" @change="updateAsync" />
            </el-form-item>
            <el-form-item label="异步后">
              <el-switch v-model="advancedForm.asyncAfter" @change="updateAsync" />
            </el-form-item>
          </template>
          
          <el-form-item label="跳过表达式">
            <el-input 
              v-model="advancedForm.skipExpression" 
              placeholder="如：${skip}"
              @blur="updateSkipExpression"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Plus, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { flowActionApi } from '@/api/flowAction'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  element: {
    type: Object,
    required: true
  },
  processId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['save'])

const activeTab = ref('basic')

// 节点类型判断
const isUserTask = computed(() => props.element?.type === 'bpmn:UserTask')
const isServiceTask = computed(() => props.element?.type === 'bpmn:ServiceTask')
const isTask = computed(() => props.element?.type?.includes('Task'))
const isStartEvent = computed(() => props.element?.type === 'bpmn:StartEvent')
const isSequenceFlow = computed(() => props.element?.type === 'bpmn:SequenceFlow')
const isGateway = computed(() => props.element?.type?.includes('Gateway'))

const nodeTypeText = computed(() => {
  const types = {
    'bpmn:StartEvent': '开始事件',
    'bpmn:EndEvent': '结束事件',
    'bpmn:UserTask': '用户任务',
    'bpmn:ServiceTask': '服务任务',
    'bpmn:ManualTask': '手动任务',
    'bpmn:ScriptTask': '脚本任务',
    'bpmn:BusinessRuleTask': '业务规则任务',
    'bpmn:SendTask': '发送任务',
    'bpmn:ReceiveTask': '接收任务',
    'bpmn:ExclusiveGateway': '排他网关',
    'bpmn:ParallelGateway': '并行网关',
    'bpmn:InclusiveGateway': '包容网关',
    'bpmn:SequenceFlow': '顺序流'
  }
  return types[props.element?.type] || props.element?.type || '未知'
})

const getNodeTypeTag = (type) => {
  if (type?.includes('StartEvent')) return 'success'
  if (type?.includes('EndEvent')) return 'danger'
  if (type?.includes('UserTask')) return 'primary'
  if (type?.includes('Gateway')) return 'warning'
  return 'info'
}

const servicePlaceholder = computed(() => {
  const map = {
    'class': 'com.example.MyServiceTask',
    'expression': '${myService.execute()}',
    'delegateExpression': '${myDelegate}'
  }
  return map[serviceForm.value.implementationType] || ''
})

// 表单数据
const basicForm = ref({ id: '', name: '', documentation: '' })
const assigneeForm = ref({
  assignee: '',
  candidateUsers: '',
  candidateGroups: '',
  isMultiInstance: false,
  multiInstanceType: 'parallel',
  collection: '',
  elementVariable: 'assignee',
  completionCondition: ''
})
const serviceForm = ref({
  implementationType: 'class',
  implementation: '',
  resultVariable: ''
})
const conditionForm = ref({ type: '', expression: '' })
const formConfig = ref({ formKey: '' })
const advancedForm = ref({
  async: false,
  asyncBefore: false,
  asyncAfter: false,
  skipExpression: ''
})

// ========== 流程动作数据 ==========
const actions = ref([])
const actionDialogVisible = ref(false)
const editingAction = ref({
  id: null,
  actionName: '',
  description: '',
  interfaceName: '',
  methodName: 'execute',
  paramsJson: '',
  enabled: true
})
const hasDraftChanges = ref(false)

const sortedActions = computed(() => {
  return [...actions.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
})

// 加载流程动作
async function loadActions() {
  if (!isSequenceFlow.value || !props.processId || !props.element?.id) return
  
  try {
    const res = await flowActionApi.findDraftActionsBySequenceFlow(props.processId, props.element.id)
    // 根据响应拦截器，res 直接是 ApiResponse 的 data 部分
    actions.value = res || []
    hasDraftChanges.value = false
  } catch (e) {
    console.error('加载流程动作失败:', e)
  }
}

// 显示动作对话框
function showActionDialog(action = null) {
  if (action) {
    editingAction.value = { ...action }
  } else {
    editingAction.value = {
      id: null,
      actionName: '',
      description: '',
      interfaceName: '',
      methodName: 'execute',
      paramsJson: '',
      enabled: true
    }
  }
  actionDialogVisible.value = true
}

// 保存动作
async function saveAction() {
  if (!editingAction.value.actionName || !editingAction.value.interfaceName) {
    ElMessage.warning('请填写必填项')
    return
  }
  
  try {
    const data = {
      ...editingAction.value,
      processConfigId: props.processId,
      sequenceFlowId: props.element.id,
      sortOrder: editingAction.value.id ? editingAction.value.sortOrder : actions.value.length
    }
    
    await flowActionApi.saveAction(data)
    ElMessage.success('保存成功')
    actionDialogVisible.value = false
    hasDraftChanges.value = true
    await loadActions()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

// 删除动作
async function deleteAction(action) {
  try {
    await ElMessageBox.confirm('确定删除该动作吗？', '提示', { type: 'warning' })
    await flowActionApi.deleteAction(action.id)
    ElMessage.success('删除成功')
    hasDraftChanges.value = true
    await loadActions()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 切换启用状态
async function toggleActionEnabled(action) {
  try {
    await flowActionApi.toggleEnabled(action.id)
    ElMessage.success('操作成功')
    hasDraftChanges.value = true
    await loadActions()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 移动动作排序
async function moveAction(index, direction) {
  const newIndex = index + direction
  if (newIndex < 0 || newIndex >= sortedActions.value.length) return
  
  const list = [...sortedActions.value]
  const temp = list[index]
  list[index] = list[newIndex]
  list[newIndex] = temp
  
  // 更新排序
  const ids = list.map(a => a.id)
  try {
    await flowActionApi.updateSortOrder(ids)
    await loadActions()
  } catch (e) {
    ElMessage.error('排序失败')
  }
}

// 监听元素变化，加载动作
watch(() => props.element, (newElement) => {
  if (newElement?.type === 'bpmn:SequenceFlow') {
    loadActions()
  }
}, { immediate: true })

// 获取建模服务
function getModeling() {
  return props.element?._modeler?.get('modeling')
}

function getModdle() {
  return props.element?._modeler?.get('moddle')
}

// 初始化表单
watch(() => props.element, (newElement) => {
  if (newElement?.businessObject) {
    const bo = newElement.businessObject
    
    basicForm.value = {
      id: bo.id || '',
      name: bo.name || '',
      documentation: bo.documentation?.[0]?.text || ''
    }
    
    if (isUserTask.value) {
      const loop = bo.loopCharacteristics
      assigneeForm.value = {
        assignee: bo.assignee || '',
        candidateUsers: bo.candidateUsers || '',
        candidateGroups: bo.candidateGroups || '',
        isMultiInstance: !!loop,
        multiInstanceType: loop?.isSequential ? 'sequential' : 'parallel',
        collection: loop?.collection || '',
        elementVariable: loop?.elementVariable || 'assignee',
        completionCondition: loop?.completionCondition?.body || ''
      }
    }
    
    if (isServiceTask.value) {
      serviceForm.value = {
        implementationType: bo.class ? 'class' : bo.expression ? 'expression' : bo.delegateExpression ? 'delegateExpression' : 'class',
        implementation: bo.class || bo.expression || bo.delegateExpression || '',
        resultVariable: bo.resultVariable || ''
      }
    }
    
    if (isSequenceFlow.value) {
      conditionForm.value = {
        type: bo.conditionExpression ? 'expression' : bo.sourceRef?.default === bo ? 'default' : '',
        expression: bo.conditionExpression?.body || ''
      }
    }
    
    if (isTask.value || isStartEvent.value) {
      formConfig.value = { formKey: bo.formKey || '' }
    }
    
    if (isTask.value || isGateway.value) {
      advancedForm.value = {
        async: bo.async || bo.asyncBefore || bo.asyncAfter,
        asyncBefore: bo.asyncBefore || false,
        asyncAfter: bo.asyncAfter || false,
        skipExpression: bo.skipExpression?.body || ''
      }
    }
  }
}, { immediate: true })

// 更新方法
function updateProperty(prop, value) {
  const modeling = getModeling()
  if (!modeling) return
  
  const updates = {}
  updates[prop] = value || undefined
  modeling.updateProperties(props.element, updates)
  emit('save')
}

function updateDocumentation() {
  const modeling = getModeling()
  const moddle = getModdle()
  if (!modeling || !moddle) return
  
  const docs = basicForm.value.documentation
    ? [moddle.create('bpmn:Documentation', { text: basicForm.value.documentation })]
    : []
  modeling.updateProperties(props.element, { documentation: docs })
  emit('save')
}

function onMultiInstanceChange(enabled) {
  const modeling = getModeling()
  const moddle = getModdle()
  if (!modeling || !moddle) return
  
  if (enabled) {
    const loop = moddle.create('bpmn:MultiInstanceLoopCharacteristics', {
      isSequential: assigneeForm.value.multiInstanceType === 'sequential'
    })
    modeling.updateProperties(props.element, { loopCharacteristics: loop })
  } else {
    modeling.updateProperties(props.element, { loopCharacteristics: undefined })
  }
  emit('save')
}

function updateMultiInstance() {
  if (!assigneeForm.value.isMultiInstance) return
  const modeling = getModeling()
  const moddle = getModdle()
  if (!modeling || !moddle) return
  
  const loop = moddle.create('bpmn:MultiInstanceLoopCharacteristics', {
    isSequential: assigneeForm.value.multiInstanceType === 'sequential',
    collection: assigneeForm.value.collection || undefined,
    elementVariable: assigneeForm.value.elementVariable || 'assignee'
  })
  
  if (assigneeForm.value.completionCondition) {
    loop.completionCondition = moddle.create('bpmn:FormalExpression', {
      body: assigneeForm.value.completionCondition
    })
  }
  
  modeling.updateProperties(props.element, { loopCharacteristics: loop })
  emit('save')
}

function onServiceTypeChange() {
  serviceForm.value.implementation = ''
  updateServiceImplementation()
}

function updateServiceImplementation() {
  const modeling = getModeling()
  if (!modeling) return
  
  const updates = {
    class: undefined,
    expression: undefined,
    delegateExpression: undefined
  }
  const type = serviceForm.value.implementationType
  if (serviceForm.value.implementation) {
    updates[type] = serviceForm.value.implementation
  }
  modeling.updateProperties(props.element, updates)
  emit('save')
}

function onConditionTypeChange(type) {
  const modeling = getModeling()
  if (!modeling) return
  
  if (type === 'expression') {
    updateCondition()
  } else if (type === 'default') {
    modeling.updateProperties(props.element, { conditionExpression: undefined })
    const source = props.element.businessObject.sourceRef
    if (source) {
      modeling.updateProperties(source.$parent, { default: props.element.businessObject })
    }
  } else {
    modeling.updateProperties(props.element, { conditionExpression: undefined })
  }
  emit('save')
}

function updateCondition() {
  if (conditionForm.value.type !== 'expression') return
  const modeling = getModeling()
  const moddle = getModdle()
  if (!modeling || !moddle) return
  
  const condition = moddle.create('bpmn:FormalExpression', {
    body: conditionForm.value.expression
  })
  modeling.updateProperties(props.element, { conditionExpression: condition })
  emit('save')
}

function onAsyncChange() {
  if (!advancedForm.value.async) {
    advancedForm.value.asyncBefore = false
    advancedForm.value.asyncAfter = false
    updateAsync()
  }
}

function updateAsync() {
  const modeling = getModeling()
  if (!modeling) return
  
  modeling.updateProperties(props.element, {
    async: advancedForm.value.async,
    asyncBefore: advancedForm.value.asyncBefore,
    asyncAfter: advancedForm.value.asyncAfter
  })
  emit('save')
}

function updateSkipExpression() {
  const modeling = getModeling()
  const moddle = getModdle()
  if (!modeling || !moddle) return
  
  if (advancedForm.value.skipExpression) {
    const expr = moddle.create('bpmn:FormalExpression', {
      body: advancedForm.value.skipExpression
    })
    modeling.updateProperties(props.element, { skipExpression: expr })
  } else {
    modeling.updateProperties(props.element, { skipExpression: undefined })
  }
  emit('save')
}
</script>

<style scoped>
.node-config-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.node-type-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 15px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #f5f7fa;
}

.node-id {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}

.no-selection {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.config-tabs {
  flex: 1;
}

.config-tabs :deep(.el-tabs__content) {
  padding: 15px;
  height: calc(100% - 40px);
  overflow-y: auto;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}

:deep(.el-divider__text) {
  font-size: 12px;
  color: #909399;
}

/* 流程动作样式 */
.actions-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.actions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-alert {
  margin-bottom: 10px;
}

.alert-content {
  display: flex;
  align-items: center;
  gap: 10px;
}

.actions-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 400px;
  overflow-y: auto;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background-color: #fafafa;
}

.action-item.disabled {
  opacity: 0.6;
  background-color: #f5f5f5;
}

.action-sort {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.sort-number {
  font-size: 12px;
  font-weight: bold;
  color: #606266;
}

.action-content {
  flex: 1;
  min-width: 0;
}

.action-name {
  font-weight: 500;
  font-size: 14px;
  margin-bottom: 5px;
}

.action-detail {
  display: flex;
  align-items: center;
  gap: 8px;
}

.interface-info {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}

.action-ops {
  display: flex;
  gap: 5px;
}
</style>
