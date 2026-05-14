<template>
  <div class="entity-data-list">
    <div class="page-header">
      <h2>{{ entityName || '数据列表' }}</h2>
      <el-button type="primary" @click="handleCreate" v-if="entityCode">
        <el-icon><Plus /></el-icon>新增数据
      </el-button>
    </div>
    
    <!-- 加载中 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>
    
    <!-- 实体未配置提示 -->
    <el-empty v-else-if="!entityCode" description="未配置实体编码" />
    
    <!-- 实体不存在提示 -->
    <el-empty v-else-if="!entityDefinition.id" description="实体不存在或未发布" />
    
    <template v-else>
      <!-- 查询条件 -->
      <el-card class="search-card" v-if="queryFields.length > 0">
        <el-form :model="queryForm" inline>
          <!-- 默认显示前4个查询字段 -->
          <el-form-item v-for="field in visibleQueryFields" :key="field.fieldCode" :label="field.fieldName">
            <!-- BETWEEN 范围查询 -->
            <template v-if="field.queryType === 'BETWEEN' && useListConfig">
              <el-date-picker v-if="field.fieldType === 'DATE' || field.fieldType === 'DATETIME'"
                v-model="queryForm[field.fieldCode + '_start']" type="date" :placeholder="`开始${field.fieldName}`" style="width: 140px" value-format="YYYY-MM-DD" />
              <el-input v-else v-model="queryForm[field.fieldCode + '_start']" :placeholder="`开始${field.fieldName}`" style="width: 120px" />
              <span style="margin: 0 4px">~</span>
              <el-date-picker v-if="field.fieldType === 'DATE' || field.fieldType === 'DATETIME'"
                v-model="queryForm[field.fieldCode + '_end']" type="date" :placeholder="`结束${field.fieldName}`" style="width: 140px" value-format="YYYY-MM-DD" />
              <el-input v-else v-model="queryForm[field.fieldCode + '_end']" :placeholder="`结束${field.fieldName}`" style="width: 120px" />
            </template>
            <!-- 普通查询 -->
            <template v-else>
              <el-input v-if="field.fieldType === 'STRING' || field.fieldType === 'TEXT'" 
                        v-model="queryForm[field.fieldCode]" :placeholder="`请输入${field.fieldName}`" />
              <el-select v-else-if="field.fieldType === 'SELECT'" 
                         v-model="queryForm[field.fieldCode]" :placeholder="`请选择${field.fieldName}`" clearable>
                <el-option v-for="opt in parseOptions(field.optionsJson)" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-date-picker v-else-if="field.fieldType === 'DATE' || field.fieldType === 'DATETIME'" 
                             v-model="queryForm[field.fieldCode]" type="date" :placeholder="`选择${field.fieldName}`" value-format="YYYY-MM-DD" />
            </template>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button v-if="queryFields.length > 4" link type="primary" @click="searchExpanded = !searchExpanded">
              <span>{{ searchExpanded ? '收起' : '展开' }}</span>
              <el-icon><ArrowUp v-if="searchExpanded" /><ArrowDown v-else /></el-icon>
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 数据列表 -->
      <el-card>
        <el-table :data="dataList" v-loading="tableLoading" stripe>
          <el-table-column type="index" width="50" />
          <!-- 使用列表配置时：完全动态列 -->
          <template v-if="useListConfig">
            <el-table-column v-for="field in listFields" :key="field.fieldCode"
              :prop="getListFieldProp(field.fieldCode)"
              :label="field.fieldName"
              :width="field.width > 0 ? field.width : undefined"
              :align="field.align"
              :min-width="field.width > 0 ? undefined : 100"
              show-overflow-tooltip>
              <template #default="{ row }">
                <!-- 自定义渲染组件 -->
                <ListCellRenderer
                  v-if="field.renderComponent || (field.dataSourceType && field.dataSourceType !== 'ENTITY_FIELD')"
                  :row="row"
                  :field="field"
                />
                <!-- 状态字段特殊渲染 -->
                <el-tag v-else-if="field.fieldCode === 'status'" :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
                <!-- 日期字段格式化 -->
                <span v-else-if="field.fieldCode === 'createdAt' || field.fieldCode === 'processStartTime' || field.fieldCode === 'processEndTime' || field.fieldCode === 'submitTime'">
                  {{ formatDate(row[field.fieldCode]) }}
                </span>
                <!-- 默认显示 -->
                <span v-else>{{ getFieldDisplayValue(row, field) }}</span>
              </template>
            </el-table-column>
          </template>
          <!-- 默认列 -->
          <template v-else>
            <el-table-column prop="dataNo" label="编号" width="150" />
            <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
            <el-table-column v-for="field in listFields" :key="field.fieldCode" 
                            :prop="`data.${field.fieldCode}`" :label="field.fieldName" min-width="120" show-overflow-tooltip />
            <el-table-column prop="submitterName" label="提交人" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="150">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
          </template>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleView(row)">查看</el-button>
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button v-if="canApprove(row)" link type="warning" @click="handleApprove(row)">审批</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>
    </template>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form ref="formRef" :model="formData" label-width="100px">
        <el-form-item label="数据名称" prop="name" :rules="[{ required: true, message: '请输入数据名称', trigger: 'blur' }]">
          <el-input v-model="formData.name" placeholder="请输入数据名称" />
        </el-form-item>
        <el-form-item v-for="field in formFields" :key="field.fieldCode"
                     v-show="isFieldVisible(field)"
                     :label="field.fieldName" :prop="`data.${field.fieldCode}`"
                     :rules="getFieldRules(field)">
          <!-- 根据字段类型渲染不同组件 -->
          <el-input v-if="field.fieldType === 'STRING'"
                   v-model="formData.data[field.fieldCode]"
                   :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                   :placeholder="`请输入${field.fieldName}`" />
          <el-input v-else-if="field.fieldType === 'TEXT'"
                   v-model="formData.data[field.fieldCode]"
                   :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                   type="textarea" rows="3" :placeholder="`请输入${field.fieldName}`" />
          <el-input-number v-else-if="field.fieldType === 'INTEGER' || field.fieldType === 'DECIMAL'"
                          v-model="formData.data[field.fieldCode]"
                          :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                          style="width: 100%" />
          <el-date-picker v-else-if="field.fieldType === 'DATE'"
                         v-model="formData.data[field.fieldCode]"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                         type="date" style="width: 100%"
                         value-format="YYYY-MM-DD" />
          <el-date-picker v-else-if="field.fieldType === 'DATETIME'"
                         v-model="formData.data[field.fieldCode]"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                         type="datetime" style="width: 100%"
                         value-format="YYYY-MM-DD HH:mm:ss" />
          <el-switch v-else-if="field.fieldType === 'BOOLEAN'"
                    v-model="formData.data[field.fieldCode]"
                    :disabled="isFieldDisabled(field) || field.isReadonly === 1" />
          <el-select v-else-if="field.fieldType === 'SELECT'"
                    v-model="formData.data[field.fieldCode]"
                    :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                    style="width: 100%" clearable>
            <el-option v-for="opt in getFieldOptions(field)" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-checkbox-group v-else-if="field.fieldType === 'MULTI_SELECT' || field.fieldType === 'CHECKBOX'"
                            v-model="formData.data[field.fieldCode]"
                            :disabled="isFieldDisabled(field) || field.isReadonly === 1">
            <el-checkbox v-for="opt in getFieldOptions(field)" :key="opt.value" :label="opt.value">{{ opt.label }}</el-checkbox>
          </el-checkbox-group>
          <el-radio-group v-else-if="field.fieldType === 'RADIO'"
                         v-model="formData.data[field.fieldCode]"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1">
            <el-radio v-for="opt in getFieldOptions(field)" :key="opt.value" :label="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
          <!-- 用户选择 -->
          <EntitySelector v-else-if="field.fieldType === 'USER' || (field.fieldType === 'REFERENCE' && field.refEntityType === 'USER')"
                         v-model="formData.data[field.fieldCode]"
                         entity-type="USER"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                         :placeholder="`请选择${field.fieldName}`" />
          <!-- 部门选择 -->
          <EntitySelector v-else-if="field.fieldType === 'DEPT' || (field.fieldType === 'REFERENCE' && field.refEntityType === 'DEPT')"
                         v-model="formData.data[field.fieldCode]"
                         entity-type="DEPT"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                         :placeholder="`请选择${field.fieldName}`" />
          <!-- 文件上传 -->
          <FileUploader
            v-else-if="field.fieldType === 'FILE' || field.fieldType === 'IMAGE'"
            v-model="formData.data[field.fieldCode]"
            :field="field"
            :disabled="isFieldDisabled(field) || field.isReadonly === 1"
            :is-image="field.fieldType === 'IMAGE'"
          />
          <!-- 通用实体引用 -->
          <EntitySelector v-else-if="field.fieldType === 'REFERENCE' || field.fieldType === 'MULTI_REFERENCE'"
                         v-model="formData.data[field.fieldCode]"
                         :entity-type="field.refEntityType || 'CUSTOM'"
                         :entity-code="field.refEntityId"
                         :ref-entity-id="field.refEntityId"
                         :api-url="getFieldApiUrl(field)"
                         :multiple="field.fieldType === 'MULTI_REFERENCE'"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                         :placeholder="`请选择${field.fieldName}`" />
        </el-form-item>
        
        <el-divider v-if="entityDefinition.enableProcess" />
        <el-form-item v-if="entityDefinition.enableProcess" label="发起流程">
          <el-switch v-model="formData.startProcess" />
          <span class="form-tip">保存数据同时发起流程</span>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
    
    <!-- 审批/查看弹窗 -->
    <el-dialog v-model="processDialogVisible" :title="`${currentTask?.name || '任务审批'}${currentTask?.processStatus ? '（' + getProcessStatusText(currentTask?.processStatus) + '）' : ''}`" width="700px" destroy-on-close>
      <el-tabs v-model="activeDialogTab" type="border-card">
        <!-- 审批信息 -->
        <el-tab-pane label="审批" name="approval">
          <!-- 实体数据表单 -->
          <div v-if="entityData" class="entity-form-section">
            <template v-if="formConfig && formConfig.fields && formConfig.fields.length > 0">
              <FormPreviewLinkage
                :form="formConfig"
                v-model="entityData"
                :readonly="true"
                :show-header="false"
              />
            </template>
            <template v-else>
              <el-form :model="entityData" label-width="100px" class="entity-form">
                <el-row :gutter="20">
                  <el-col v-for="(value, key) in entityData" :key="key" :span="12">
                    <el-form-item :label="key">
                      <el-input v-model="entityData[key]" :readonly="true" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </template>
          </div>
          
          <el-divider v-if="entityData" />
          
          <!-- 审批操作区（非查看模式） -->
          <template v-if="!isViewMode && effectiveApprovalConfig.enabled !== false">
            <div class="section-title">审批意见</div>
            <el-form :model="approveForm" label-width="80px">
              <el-form-item label="审批操作" required>
                <el-radio-group v-model="approveForm.action">
                  <el-radio-button 
                    v-for="option in effectiveApprovalConfig.options" 
                    :key="option.value" 
                    :label="option.value"
                  >
                    {{ option.label }}
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item 
                v-if="effectiveApprovalConfig.options.find(o => o.value === approveForm.action)?.showComment !== false"
                :label="effectiveApprovalConfig.commentLabel || '审批备注'"
              >
                <el-input 
                  v-model="approveForm.comment" 
                  type="textarea" 
                  :rows="3" 
                  :placeholder="`请输入${effectiveApprovalConfig.commentLabel || '审批备注'}`" 
                />
              </el-form-item>
            </el-form>
          </template>
        </el-tab-pane>

        <!-- 流程图 -->
        <el-tab-pane label="流程图" name="diagram">
          <div style="height: 400px;">
            <VueBpmnViewer 
              v-if="bpmnXml && progressData" 
              :key="currentTask?.processInstanceId"
              :xml="bpmnXml" 
              :progress-data="progressData"
              style="height: 100%;" 
            />
            <el-empty v-else description="暂无流程图" />
          </div>
        </el-tab-pane>

        <!-- 审批历史 -->
        <el-tab-pane label="审批历史" name="history">
          <el-timeline v-if="processHistory.length > 0">
            <el-timeline-item
              v-for="(item, index) in processHistory"
              :key="index"
              :type="item.type"
              :timestamp="item.time"
            >
              <div class="history-item">
                <span class="history-title">{{ item.title }}</span>
                <el-tag size="small" :type="item.status === 'COMPLETED' ? 'success' : (item.status === 'TERMINATED' ? 'danger' : 'warning')">
                  {{ item.status === 'COMPLETED' ? '已完成' : (item.status === 'TERMINATED' ? '已终止' : '进行中') }}
                </el-tag>
              </div>
              <div class="history-desc">{{ item.description }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审批历史" />
        </el-tab-pane>
      </el-tabs>
      
      <template #footer>
        <el-button @click="processDialogVisible = false">关闭</el-button>
        <el-button v-if="!isViewMode && activeDialogTab === 'approval'" type="primary" @click="submitApprove" :loading="approveSubmitLoading">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { entityApi, entityDataApi } from '@/api/entity'
import { entityListConfigApi } from '@/api/entityListConfig'
import { useUserStore } from '@/stores/user'
import { completeTask, getProcessHistory } from '@/api/processTask'
import request from '@/utils/request'
import EntitySelector from '@/components/EntitySelector.vue'
import FileUploader from '@/components/FileUploader.vue'
import { LinkageEngine } from '@/utils/linkageEngine'
import VueBpmnViewer from '@/components/VueBpmnViewer.vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import ListCellRenderer from '@/components/ListCellRenderer.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 当前登录用户
const currentUserId = computed(() => userStore.userInfo?.id || userStore.userInfo?.username || '')

// 从路由参数获取实体编码
const entityCode = computed(() => route.params.entityCode as string || route.query.entityCode as string)

// 状态
const loading = ref(false)
const tableLoading = ref(false)
const searchExpanded = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const currentRow = ref<any>({})

// 实体定义
const entityDefinition = ref<any>({})
const entityFields = ref<any[]>([])

// 列表配置（entity_list_config）
const listConfig = ref<any>(null)
const listConfigFields = ref<any[]>([])

// 数据列表
const dataList = ref<any[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 查询条件
const queryForm = reactive<Record<string, any>>({})

// 表单数据
const formData = reactive({
  id: '',
  name: '',
  data: {} as Record<string, any>,
  startProcess: false
})

const formRef = ref()

// 字段联动状态
const linkageState = ref({
  visibility: {} as Record<string, boolean>,
  disabled: {} as Record<string, boolean>,
  required: {} as Record<string, boolean>,
  options: {} as Record<string, any[]>,
  values: {} as Record<string, any>
})

// 更新联动状态
function updateLinkageState() {
  const fields = formFields.value || []
  linkageState.value = LinkageEngine.processAllLinkages(fields, formData.data || {})
  // 应用计算字段值
  if (linkageState.value.values) {
    Object.entries(linkageState.value.values).forEach(([key, val]) => {
      if (val !== null && formData.data[key] !== val) {
        formData.data[key] = val
      }
    })
  }
}

// 判断字段是否可见
function isFieldVisible(field: any) {
  return linkageState.value.visibility[field.fieldCode] !== false
}

// 判断字段是否禁用
function isFieldDisabled(field: any) {
  return linkageState.value.disabled[field.fieldCode] === true
}

// 从 componentProps 或字段属性中获取数据接口URL
function getFieldApiUrl(field: any) {
  if (field.componentProps) {
    try {
      const cp = typeof field.componentProps === 'string' ? JSON.parse(field.componentProps) : field.componentProps
      if (cp.refConfig?.apiUrl) return cp.refConfig.apiUrl
    } catch (e) {}
  }
  return field.apiUrl || null
}

// 获取字段验证规则（含联动必填）
function getFieldRules(field: any) {
  const isRequired = linkageState.value.required[field.fieldCode] !== undefined
    ? linkageState.value.required[field.fieldCode]
    : field.isRequired
  if (isRequired) {
    return [{ required: true, message: `请输入${field.fieldName}`, trigger: 'blur' }]
  }
  return []
}

// 获取字段选项（含联动过滤）
function getFieldOptions(field: any) {
  if (linkageState.value.options[field.fieldCode]) {
    return linkageState.value.options[field.fieldCode]
  }
  // 优先从 optionsJson 解析
  if (field.optionsJson) {
    return parseOptions(field.optionsJson)
  }
  // 再从 componentProps 解析
  if (field.componentProps) {
    try {
      const compProps = JSON.parse(field.componentProps)
      if (compProps.options && Array.isArray(compProps.options)) {
        return compProps.options
      }
    } catch (e) {}
  }
  return []
}

// 监听表单数据变化，触发联动
watch(() => formData.data, () => {
  updateLinkageState()
}, { deep: true })

// 计算属性
const entityName = computed(() => entityDefinition.value?.entityName)

// 查询字段（优先使用列表配置，否则用默认的isQuery）
const queryFields = computed(() => {
  if (listConfigFields.value.length > 0) {
    // 使用列表配置的查询字段
    return listConfigFields.value
      .filter((f: any) => f.isQuery && f.showInList)
      .map((f: any) => {
        const originField = entityFields.value.find((ef: any) => ef.fieldCode === f.fieldCode)
        return { ...f, fieldType: originField?.fieldType || 'STRING', optionsJson: originField?.optionsJson, queryType: f.queryType || 'LIKE' }
      })
  }
  return entityFields.value.filter((f: any) => f.isQuery && !f.isSystem)
})

// 可见的查询字段（默认只显示前4个，展开后显示全部）
const visibleQueryFields = computed(() => {
  if (searchExpanded.value || queryFields.value.length <= 4) {
    return queryFields.value
  }
  return queryFields.value.slice(0, 4)
})

// 列表显示字段（优先使用列表配置，否则用默认的showInList）
const listFields = computed(() => {
  if (listConfigFields.value.length > 0) {
    return listConfigFields.value
      .filter((f: any) => f.showInList)
      .map((f: any) => {
        const originField = entityFields.value.find((ef: any) => ef.fieldCode === f.fieldCode)
        return { ...f, fieldType: originField?.fieldType || 'STRING', optionsJson: originField?.optionsJson }
      })
  }
  return entityFields.value.filter((f: any) => f.showInList && !f.isSystem)
})

// 是否使用列表配置
const useListConfig = computed(() => listConfigFields.value.length > 0)

// 表单字段（配置了showInForm的字段）
const formFields = computed(() => {
  return entityFields.value.filter((f: any) => f.showInForm && !f.isSystem)
})

// 获取列表字段对应的 prop（系统字段在 row 顶层，自定义字段在 row.data 中）
const SYSTEM_FIELDS = new Set(['id', 'dataNo', 'name', 'code', 'status', 'processInstanceId', 'processStartTime', 'processEndTime', 'currentTaskId', 'currentTaskName', 'currentTaskAssignee', 'submitterId', 'submitterName', 'submitTime', 'createdAt', 'updatedAt', 'createdBy', 'updatedBy'])
const getListFieldProp = (fieldCode: string) => {
  return SYSTEM_FIELDS.has(fieldCode) ? fieldCode : `data.${fieldCode}`
}

// 获取字段显示值（用于默认渲染）
const getFieldDisplayValue = (row: any, field: any) => {
  const fieldCode = field.fieldCode
  if (SYSTEM_FIELDS.has(fieldCode)) {
    return row[fieldCode] ?? '-'
  }
  return row.data?.[fieldCode] ?? '-'
}

// 解析选项
const parseOptions = (optionsJson: string) => {
  if (!optionsJson) return []
  try {
    return JSON.parse(optionsJson)
  } catch {
    return []
  }
}

// 获取状态样式
const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    'DRAFT': 'info',
    'PENDING': 'warning',
    'APPROVED': 'success',
    'REJECTED': 'danger',
    'COMPLETED': 'success'
  }
  return map[status] || ''
}

// 获取状态文本
const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    'DRAFT': '草稿',
    'PENDING': '审批中',
    'APPROVED': '已通过',
    'REJECTED': '已驳回',
    'COMPLETED': '已完成'
  }
  return map[status] || status
}

// 格式化日期
const formatDate = (date: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

// 加载实体定义
const loadEntityDefinition = async () => {
  if (!entityCode.value) return
  
  loading.value = true
  try {
    const res = await entityApi.getByCode(entityCode.value)
    entityDefinition.value = res || {}
    entityFields.value = res?.fields || []
    
    // 加载列表配置
    await loadListConfig()
    
    // 初始化查询表单
    queryFields.value.forEach((field: any) => {
      queryForm[field.fieldCode] = ''
    })
    
    // 加载数据列表
    await loadDataList()
  } catch (error) {
    console.error('加载实体定义失败:', error)
    ElMessage.error('加载实体定义失败')
  } finally {
    loading.value = false
  }
}

// 加载列表配置
const loadListConfig = async () => {
  if (!entityDefinition.value?.id) return
  try {
    const configs = await entityListConfigApi.getByEntityId(entityDefinition.value.id)
    if (configs && configs.length > 0) {
      // 优先使用默认配置，如果没有则使用第一个
      const config = configs.find((c: any) => c.isDefault) || configs[0]
      const detail = await entityListConfigApi.getById(config.id)
      if (detail) {
        listConfig.value = detail
        listConfigFields.value = detail.fields || []
      }
    } else {
      listConfig.value = null
      listConfigFields.value = []
    }
  } catch (e) {
    console.error('加载列表配置失败:', e)
    listConfig.value = null
    listConfigFields.value = []
  }
}

// 加载数据列表
const loadDataList = async () => {
  if (!entityCode.value) return
  
  tableLoading.value = true
  try {
    // 构建查询参数，过滤空值
    const params: Record<string, any> = {}
    Object.entries(queryForm).forEach(([key, value]) => {
      if (value !== '' && value !== null && value !== undefined) {
        params[key] = value
      }
    })
    
    // 有列表配置时调用带扩展字段的接口
    let res
    if (listConfig.value?.id) {
      res = await entityDataApi.getListWithConfig(entityCode.value, listConfig.value.listKey, params)
    } else {
      res = await entityDataApi.getList(entityCode.value, params)
    }
    
    // 后端返回列表，前端做分页
    const allData = res || []
    total.value = allData.length
    
    // 前端分页
    const start = (pageNum.value - 1) * pageSize.value
    const end = start + pageSize.value
    dataList.value = allData.slice(start, end)
  } catch (error) {
    console.error('加载数据列表失败:', error)
  } finally {
    tableLoading.value = false
  }
}

// 查询
const handleSearch = () => {
  pageNum.value = 1
  loadDataList()
}

// 重置
const handleReset = () => {
  Object.keys(queryForm).forEach(key => {
    queryForm[key] = ''
  })
  handleSearch()
}

// 分页
const handleSizeChange = (val: number) => {
  pageSize.value = val
  pageNum.value = 1
  loadDataList()
}

const handlePageChange = (val: number) => {
  pageNum.value = val
  loadDataList()
}

// 重置表单
const resetForm = () => {
  formData.id = ''
  formData.name = ''
  formData.data = {}
  formData.startProcess = false
  
  // 初始化字段默认值
  formFields.value.forEach((field: any) => {
    if (field.defaultValue) {
      try {
        formData.data[field.fieldCode] = JSON.parse(field.defaultValue)
      } catch {
        formData.data[field.fieldCode] = field.defaultValue
      }
    } else {
      formData.data[field.fieldCode] = ''
    }
  })
}

// 新增
const handleCreate = () => {
  resetForm()
  dialogTitle.value = '新增数据'
  dialogVisible.value = true
  nextTick(() => {
    updateLinkageState()
  })
}

// 编辑
const handleEdit = (row: any) => {
  formData.id = row.id
  formData.name = row.name
  formData.data = { ...row.data }
  dialogTitle.value = '编辑数据'
  dialogVisible.value = true
  nextTick(() => {
    updateLinkageState()
  })
}

// 删除
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该数据吗？', '提示', { type: 'warning' })
    await entityDataApi.delete(row.id)
    ElMessage.success('删除成功')
    loadDataList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 判断是否可审批
const canApprove = (row: any) => {
  if (!row.processInstanceId || row.processEndTime) return false
  const userId = userStore.userInfo?.id
  const username = userStore.userInfo?.username
  return row.currentTaskAssignee === String(userId) || row.currentTaskAssignee === username
}

// 审批/查看弹窗
const processDialogVisible = ref(false)
const activeDialogTab = ref('approval')
const approveSubmitLoading = ref(false)
const currentTask = ref<any>(null)
const isViewMode = ref(false)

const approveForm = reactive({
  action: 'approve',
  comment: '',
  transferTo: ''
})

const bpmnXml = ref('')
const processHistory = ref<any[]>([])
const progressData = ref<any>({
  completedNodes: [],
  activeNodes: [],
  executedSequenceFlows: [],
  nodeAssigneeMap: {}
})
const entityData = ref<any>(null)
const formConfig = ref<any>(null)
const approvalConfig = ref<any>(null)

// 计算属性：获取当前有效的审批配置
const effectiveApprovalConfig = computed(() => {
  if (approvalConfig.value) {
    return approvalConfig.value
  }
  return {
    enabled: true,
    commentLabel: '审批意见',
    options: [
      { value: 'approve', label: '通过', type: 'primary', showComment: true },
      { value: 'reject', label: '驳回', type: 'danger', showComment: true }
    ]
  }
})

// 监听审批弹窗 Tab 切换，切换到流程图时重新触发渲染
watch(activeDialogTab, (newVal) => {
  if (newVal === 'diagram' && bpmnXml.value && progressData.value) {
    nextTick(() => {
      const tempXml = bpmnXml.value
      bpmnXml.value = ''
      nextTick(() => {
        bpmnXml.value = tempXml
      })
    })
  }
})

// 获取流程状态显示文本
function getProcessStatusText(status: string) {
  const textMap: Record<string, string> = {
    'RUNNING': '运行中',
    'COMPLETED': '已完成',
    'SUSPENDED': '已挂起',
    'TERMINATED': '已终止'
  }
  return textMap[status] || status || '-'
}

// 加载流程详情
async function loadProcessDetail(instanceId: string) {
  try {
    const progressRes = await request.get(`/process-instance/${instanceId}/progress`)
    if (progressRes) {
      bpmnXml.value = progressRes.bpmnXml || ''
      progressData.value = {
        completedNodes: progressRes.completedNodes || [],
        activeNodes: progressRes.activeNodes || [],
        terminatedNodes: progressRes.terminatedNodes || [],
        executedSequenceFlows: progressRes.executedSequenceFlows || [],
        nodeAssigneeMap: progressRes.nodeAssigneeMap || {},
        status: progressRes.status
      }
      entityData.value = progressRes.entityData || null
      if (entityData.value && entityData.value.status) {
        const statusMap: Record<string, string> = {
          'DRAFT': '草稿',
          'PENDING': '审批中',
          'APPROVED': '已通过',
          'REJECTED': '已驳回',
          'COMPLETED': '已完成',
          'WITHDRAWN': '已撤回'
        }
        entityData.value._statusText = statusMap[entityData.value.status] || entityData.value.status
      }
      formConfig.value = progressRes.formConfig || null
      approvalConfig.value = progressRes.approvalConfig || null
      const config = progressRes.approvalConfig
      if (config && Array.isArray(config.options) && config.options.length > 0) {
        const firstOption = config.options[0]
        if (firstOption && firstOption.value) {
          approveForm.action = firstOption.value
        }
      }
      if (currentTask.value) {
        currentTask.value.processStatus = progressRes.status
        if (progressRes.processName) {
          currentTask.value.processName = progressRes.processName
        }
      }
    }

    // 加载历史
    if (progressRes?.nodeHistory && progressRes.nodeHistory.length > 0) {
      processHistory.value = progressRes.nodeHistory.map((node: any) => {
        const isStartNode = node.nodeId?.toLowerCase().includes('start') || node.nodeName === '开始'
        let actionText = ''
        if (node.action === 'APPROVED') actionText = '通过'
        else if (node.action === 'REJECTED') actionText = '驳回'
        else if (node.action === 'TRANSFERRED') actionText = '转办'
        else if (node.action === 'TERMINATED') actionText = '终止'
        else if (node.status === 'COMPLETED') actionText = '完成'
        else if (node.status === 'TERMINATED') actionText = '终止'
        else actionText = '进行中'
        const commentText = node.comment ? `（${node.comment}）` : ''
        return {
          title: node.nodeName || node.nodeId,
          description: isStartNode
            ? `发起人: ${node.assignee || currentTask.value?.startUserName || 'admin'}`
            : (node.assignee ? `执行人: ${node.assignee} ${actionText}${commentText}` : `${actionText}${commentText}`),
          time: node.endTime || node.startTime,
          type: node.action === 'TRANSFERRED' ? 'warning' : (node.status === 'TERMINATED' ? 'danger' : (node.status === 'COMPLETED' ? 'success' : 'primary')),
          status: node.status,
          action: node.action
        }
      }).reverse()
    } else {
      const historyRes = await getProcessHistory(instanceId)
      processHistory.value = (historyRes || []).map((h: any) => {
        const isStart = h.action === '发起' || h.taskName?.toLowerCase().includes('start')
        const isTransfer = h.result === 'transfer' || (h.comment && h.comment.includes('转办'))
        return {
          title: h.taskName || '流程节点',
          description: isStart
            ? `发起人: ${h.assignee || currentTask.value?.startUserName || 'admin'}`
            : `${h.assignee || '系统'} ${isTransfer ? '转办' : (h.action || '处理')}`,
          time: h.endTime || h.startTime,
          type: isStart ? 'primary' : (isTransfer ? 'warning' : (h.action === '通过' ? 'success' : 'info')),
          status: h.endTime ? 'COMPLETED' : 'ACTIVE',
          action: h.result
        }
      }).reverse()
    }
  } catch (e) {
    console.error('加载流程详情失败:', e)
  }
}

// 打开审批弹窗
const handleApprove = async (row: any) => {
  isViewMode.value = false
  currentTask.value = {
    taskId: row.currentTaskId,
    processInstanceId: row.processInstanceId,
    name: row.currentTaskName || '任务审批'
  }
  approveForm.action = 'approve'
  approveForm.comment = ''
  activeDialogTab.value = 'approval'
  await loadProcessDetail(row.processInstanceId)
  processDialogVisible.value = true
}

// 打开查看弹窗（只读模式）
const handleView = (row: any) => {
  isViewMode.value = true
  currentTask.value = {
    processInstanceId: row.processInstanceId,
    name: row.name || '数据详情'
  }
  activeDialogTab.value = 'approval'
  if (row.processInstanceId) {
    loadProcessDetail(row.processInstanceId)
  }
  processDialogVisible.value = true
}

// 提交审批
const submitApprove = async () => {
  if (!currentTask.value?.taskId) return
  approveSubmitLoading.value = true
  try {
    await completeTask({
      taskId: currentTask.value.taskId,
      action: approveForm.action,
      comment: approveForm.comment
    })
    ElMessage.success('审批成功')
    processDialogVisible.value = false
    loadDataList()
  } catch (e) {
    console.error('审批失败:', e)
    ElMessage.error('审批失败')
  } finally {
    approveSubmitLoading.value = false
  }
}

// 提交
const handleSubmit = async () => {
  await formRef.value?.validate()
  
  submitLoading.value = true
  try {
    const data = {
      entityCode: entityCode.value,
      id: formData.id,
      name: formData.name,
      data: formData.data,
      startProcess: formData.startProcess
    }
    
    if (formData.id) {
      await entityDataApi.update(formData.id, data)
      ElMessage.success('更新成功')
    } else {
      await entityDataApi.save(data, data.startProcess)
      ElMessage.success('创建成功')
    }
    
    dialogVisible.value = false
    loadDataList()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

// 监听实体编码变化
watch(() => entityCode.value, () => {
  if (entityCode.value) {
    loadEntityDefinition()
  }
}, { immediate: true })

onMounted(() => {
  if (entityCode.value) {
    loadEntityDefinition()
  }
})
</script>

<style scoped lang="scss">
.entity-data-list {
  padding: 20px;
  
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 500;
    }
  }
  
  .loading-container {
    padding: 20px;
  }
  
  .search-card {
    margin-bottom: 20px;
  }
  
  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
  
  .form-tip {
    margin-left: 10px;
    color: #909399;
    font-size: 12px;
  }
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.history-title {
  font-weight: 600;
  color: #303133;
}

.history-desc {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-left: 8px;
  border-left: 4px solid #409eff;
}
</style>
