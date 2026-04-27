<template>
  <div class="entity-data-manage">
    <div class="page-header">
      <el-button @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>返回
      </el-button>
      <span class="title">{{ entityName }} - 数据管理</span>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>新增数据
      </el-button>
    </div>

    <!-- 查询条件 -->
    <el-card class="search-card" v-if="queryFields.length > 0">
      <el-form :model="queryForm" inline>
        <el-form-item v-for="field in queryFields" :key="field.fieldCode" :label="field.fieldName">
          <el-input v-if="field.fieldType === 'STRING' || field.fieldType === 'TEXT'" 
                    v-model="queryForm[field.fieldCode]" :placeholder="`请输入${field.fieldName}`" />
          <el-select v-else-if="field.fieldType === 'SELECT'" 
                     v-model="queryForm[field.fieldCode]" :placeholder="`请选择${field.fieldName}`" clearable>
            <el-option v-for="opt in parseOptions(field.optionsJson)" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-date-picker v-else-if="field.fieldType === 'DATE'" 
                         v-model="queryForm[field.fieldCode]" type="date" :placeholder="`选择${field.fieldName}`" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据列表 -->
    <el-card>
      <el-table :data="dataList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="dataNo" label="编号" width="150" />
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
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <!-- 显示审批按钮的条件：有流程实例ID且当前登录人是审批人 -->
            <el-button 
              v-if="row.processInstanceId && row.currentTaskAssignee === userStore.username" 
              link 
              type="warning" 
              @click="handleApprove(row)"
            >
              审批
            </el-button>
            <el-button v-if="row.processInstanceId" link type="success" @click="handleViewProcess(row)">流程</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item v-for="field in formFields" :key="field.fieldId"
                     v-show="isFieldVisible(field)"
                     :label="field.fieldLabel || field.fieldName" :prop="`data.${field.fieldCode || field.fieldId}`"
                     :rules="getFieldRules(field)">
          <!-- 根据字段类型渲染不同组件 -->
          <el-input v-if="field.fieldType === 'STRING'"
                   v-model="formData.data[field.fieldCode || field.fieldId]"
                   :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                   :placeholder="`请输入${field.fieldLabel || field.fieldName}`" />
          <el-input v-else-if="field.fieldType === 'TEXT'"
                   v-model="formData.data[field.fieldCode || field.fieldId]"
                   :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                   type="textarea" rows="3" :placeholder="`请输入${field.fieldLabel || field.fieldName}`" />
          <el-input-number v-else-if="field.fieldType === 'INTEGER' || field.fieldType === 'DECIMAL'"
                          v-model="formData.data[field.fieldCode || field.fieldId]"
                          :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                          style="width: 100%" />
          <el-date-picker v-else-if="field.fieldType === 'DATE'"
                         v-model="formData.data[field.fieldCode || field.fieldId]"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                         type="date" style="width: 100%" />
          <el-date-picker v-else-if="field.fieldType === 'DATETIME'"
                         v-model="formData.data[field.fieldCode || field.fieldId]"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                         type="datetime" style="width: 100%" />
          <el-switch v-else-if="field.fieldType === 'BOOLEAN'"
                    v-model="formData.data[field.fieldCode || field.fieldId]"
                    :disabled="isFieldDisabled(field) || field.isReadonly === 1" />
          <el-select v-else-if="field.fieldType === 'SELECT'"
                    v-model="formData.data[field.fieldCode || field.fieldId]"
                    :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                    style="width: 100%" clearable>
            <el-option v-for="opt in getFieldOptions(field)" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-checkbox-group v-else-if="field.fieldType === 'MULTI_SELECT' || field.fieldType === 'CHECKBOX'"
                            v-model="formData.data[field.fieldCode || field.fieldId]"
                            :disabled="isFieldDisabled(field) || field.isReadonly === 1">
            <el-checkbox v-for="opt in getFieldOptions(field)" :key="opt.value" :label="opt.value">{{ opt.label }}</el-checkbox>
          </el-checkbox-group>
          <el-radio-group v-else-if="field.fieldType === 'RADIO'"
                         v-model="formData.data[field.fieldCode || field.fieldId]"
                         :disabled="isFieldDisabled(field) || field.isReadonly === 1">
            <el-radio v-for="opt in getFieldOptions(field)" :key="opt.value" :label="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
          <!-- 文件上传 -->
          <el-upload v-else-if="field.fieldType === 'FILE'"
                    :file-list="getFileList(field.fieldCode || field.fieldId)"
                    :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                    :auto-upload="false"
                    :limit="1"
                    :before-upload="(file) => beforeFileUpload(file, 'FILE')"
                    :on-change="(file) => handleFileUpload(file, field.fieldCode || field.fieldId)"
                    :on-remove="() => handleFileRemove(field.fieldCode || field.fieldId)"
                    accept="*/*"
                    v-loading="uploadLoading"
                    class="file-upload"
                    action="#">
            <el-button type="primary" :icon="Upload">
              <span v-if="formData.data[field.fieldCode || field.fieldId]">更换文件</span>
              <span v-else>选择文件</span>
            </el-button>
            <template #tip>
              <div class="el-upload__tip">支持任意格式文件，大小不超过20MB</div>
            </template>
          </el-upload>
          
          <!-- 图片上传 -->
          <el-upload v-else-if="field.fieldType === 'IMAGE'"
                    :file-list="getFileList(field.fieldCode || field.fieldId)"
                    :disabled="isFieldDisabled(field) || field.isReadonly === 1"
                    :auto-upload="false"
                    :limit="1"
                    :before-upload="(file) => beforeFileUpload(file, 'IMAGE')"
                    :on-change="(file) => handleImageUpload(file, field.fieldCode || field.fieldId)"
                    :on-remove="() => handleFileRemove(field.fieldCode || field.fieldId)"
                    accept="image/*"
                    list-type="picture-card"
                    v-loading="uploadLoading"
                    class="image-upload"
                    action="#"
                    @preview="handlePictureCardPreview"
                    >
            <div v-if="!formData.data[field.fieldCode || field.fieldId]">
              <el-icon><Plus /></el-icon>
              <div class="el-upload__text">点击上传</div>
            </div>
            <template #tip>
              <div class="el-upload__tip">支持 JPG/PNG/GIF 格式，大小不超过5MB</div>
            </template>
          </el-upload>
        </el-form-item>
        
        <el-divider v-if="entityDefinition.enableProcess" />
        <el-form-item v-if="entityDefinition.enableProcess" label="发起流程">
          <el-switch v-model="formData.startProcess" />
          <span class="tip-text">保存数据时同时发起审批流程</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog v-model="viewDialogVisible" :title="viewResolvedForm?.formName || '数据详情'" width="700px">
      <el-descriptions :column="2" border v-loading="viewFormLoading">
        <!-- 基础信息 -->
        <el-descriptions-item label="编号">{{ currentRow.dataNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据名称">{{ currentRow.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据编码">{{ currentRow.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentRow.status)">{{ getStatusText(currentRow.status) }}</el-tag>
        </el-descriptions-item>
        
        <!-- 流程信息 -->
        <el-descriptions-item label="流程实例ID">{{ currentRow.processInstanceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="流程开始时间">{{ formatDate(currentRow.processStartTime) }}</el-descriptions-item>
        <el-descriptions-item label="流程结束时间">{{ formatDate(currentRow.processEndTime) }}</el-descriptions-item>
        <el-descriptions-item label="当前任务">{{ currentRow.currentTaskName || '-' }}</el-descriptions-item>
        
        <!-- 提交信息 -->
        <el-descriptions-item label="提交人ID">{{ currentRow.submitterId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交人">{{ currentRow.submitterName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatDate(currentRow.submitTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(currentRow.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(currentRow.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ currentRow.createdBy || '-' }}</el-descriptions-item>
      </el-descriptions>
      
      <el-divider>表单字段</el-divider>
      
      <!-- 使用流程节点表单字段显示 -->
      <el-descriptions v-if="viewResolvedForm && viewResolvedForm.fields" :column="2" border>
        <el-descriptions-item 
          v-for="field in viewResolvedForm.fields" 
          :key="field.id" 
          :label="field.fieldName"
        >
          <!-- 文件类型显示下载链接 -->
          <a v-if="field.fieldType === 'FILE' && currentRow.data?.[field.fieldCode]" 
             :href="currentRow.data[field.fieldCode]" 
             target="_blank"
             class="file-link">
            <el-icon><Document /></el-icon>
            {{ currentRow.data[field.fieldCode].split('/').pop() }}
          </a>
          <!-- 图片类型显示缩略图 -->
          <el-image v-else-if="field.fieldType === 'IMAGE' && currentRow.data?.[field.fieldCode]"
                   :src="currentRow.data[field.fieldCode]" 
                   :preview-src-list="[currentRow.data[field.fieldCode]]"
                   fit="cover"
                   style="width: 100px; height: 100px;"
                   class="preview-image" />
          <!-- 其他类型正常显示 -->
          <span v-else>{{ formatFieldValue(field, currentRow.data?.[field.fieldCode]) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      
      <!-- 没有解析到表单时，使用实体字段显示 -->
      <el-descriptions v-else :column="2" border>
        <el-descriptions-item v-for="field in formFields.filter(f => !f.isSystem)" :key="field.fieldCode" :label="field.fieldName">
          <!-- 文件类型显示下载链接 -->
          <a v-if="field.fieldType === 'FILE' && currentRow.data?.[field.fieldCode]" 
             :href="currentRow.data[field.fieldCode]" 
             target="_blank"
             class="file-link">
            <el-icon><Document /></el-icon>
            {{ currentRow.data[field.fieldCode].split('/').pop() }}
          </a>
          <!-- 图片类型显示缩略图 -->
          <el-image v-else-if="field.fieldType === 'IMAGE' && currentRow.data?.[field.fieldCode]"
                   :src="currentRow.data[field.fieldCode]" 
                   :preview-src-list="[currentRow.data[field.fieldCode]]"
                   fit="cover"
                   style="width: 100px; height: 100px;"
                   class="preview-image" />
          <!-- 其他类型正常显示 -->
          <span v-else>{{ formatFieldValue(field, currentRow.data?.[field.fieldCode]) }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 图片预览对话框 -->
    <el-dialog v-model="previewImageVisible" title="图片预览" width="800px" append-to-body>
      <div style="text-align: center;">
        <img :src="previewImageUrl" style="max-width: 100%; max-height: 600px;" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { Plus, Upload, Document } from '@element-plus/icons-vue'
import { entityApi, entityDataApi } from '@/api/entity'
import { processTaskApi } from '@/api/processTask'
import { fileApi } from '@/api/file'
import { getFormForNewData, getFormForViewData } from '@/api/entityFormResolve'
import { useUserStore } from '@/stores/user'
import { LinkageEngine } from '@/utils/linkageEngine'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const entityCode = route.params.code

const loading = ref(false)
const entityDefinition = ref({})
const fields = ref([])
const dataList = ref([])
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitting = ref(false)
const currentRow = ref({})
const formRef = ref()

// 新增/查看数据时解析的表单（流程节点表单或默认表单）
const resolvedForm = ref(null)
const formLoading = ref(false)
const viewFormLoading = ref(false)
const viewResolvedForm = ref(null)

// 图片预览相关
const previewImageVisible = ref(false)
const previewImageUrl = ref('')

const queryForm = ref({})
const formData = ref({
  entityCode: entityCode,
  title: '',
  data: {},
  submitterId: userStore.username,
  submitterName: userStore.nickname,
  startProcess: false
})

// 字段联动状态
const linkageState = ref({
  visibility: {},
  disabled: {},
  required: {},
  options: {},
  values: {}
})

// 更新联动状态
function updateLinkageState() {
  const fields = formFields.value || []
  // 将 formData.data 作为表单数据传入
  linkageState.value = LinkageEngine.processAllLinkages(fields, formData.value.data || {})
  // 应用计算字段值
  if (linkageState.value.values) {
    Object.entries(linkageState.value.values).forEach(([key, val]) => {
      if (val !== null && formData.value.data[key] !== val) {
        formData.value.data[key] = val
      }
    })
  }
}

// 判断字段是否可见
function isFieldVisible(field) {
  const key = field.fieldCode || field.fieldId
  return linkageState.value.visibility[key] !== false
}

// 判断字段是否禁用
function isFieldDisabled(field) {
  const key = field.fieldCode || field.fieldId
  return linkageState.value.disabled[key] === true
}

// 获取字段验证规则（含联动必填）
function getFieldRules(field) {
  const key = field.fieldCode || field.fieldId
  const isRequired = linkageState.value.required[key] !== undefined
    ? linkageState.value.required[key]
    : field.isRequired
  if (isRequired) {
    return [{ required: true, message: `请输入${field.fieldLabel || field.fieldName}`, trigger: 'blur' }]
  }
  return []
}

// 获取字段选项（含联动过滤）
function getFieldOptions(field) {
  const key = field.fieldCode || field.fieldId
  if (linkageState.value.options[key]) {
    return linkageState.value.options[key]
  }
  return parseOptions(field.optionsJson)
}

// 监听表单数据变化，触发联动
watch(() => formData.value.data, () => {
  updateLinkageState()
}, { deep: true })

// 列表显示字段
const listFields = computed(() => {
  return fields.value.filter(f => f.showInList !== false).slice(0, 5) // 最多显示5个
})

// 查询字段
const queryFields = computed(() => {
  return fields.value.filter(f => f.isQuery)
})

// 表单字段 - 新增时使用解析后的表单字段，编辑时使用实体字段
const formFields = computed(() => {
  // 如果是新增且有解析的表单，使用解析的表单字段
  if (!isEdit.value && resolvedForm.value && resolvedForm.value.fields) {
    return resolvedForm.value.fields.filter(f => f.status !== 0)
  }
  // 否则使用实体字段
  return fields.value.filter(f => f.showInForm !== false).sort((a, b) => a.sortOrder - b.sortOrder)
})

const uploadLoading = ref(false)

// 加载实体定义
const loadEntity = async () => {
  try {
    const data = await entityApi.getByCode(entityCode)
    entityDefinition.value = data
    fields.value = data.fields || []
  } catch (error) {
    console.error(error)
  }
}

// 加载数据列表
const loadData = async () => {
  loading.value = true
  try {
    dataList.value = await entityDataApi.getList(entityCode)
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 解析选项
const parseOptions = (json) => {
  if (!json) return []
  try {
    return JSON.parse(json)
  } catch (e) {
    return []
  }
}

// 处理文件上传
const handleFileUpload = async (file, fieldCode) => {
  try {
    uploadLoading.value = true
    const res = await fileApi.upload(file.raw)
    // 保存文件URL到表单数据
    formData.value.data[fieldCode] = res.url
    ElMessage.success('文件上传成功')
  } catch (error) {
    console.error('文件上传失败:', error)
    ElMessage.error('文件上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// 处理图片上传
const handleImageUpload = async (file, fieldCode) => {
  try {
    uploadLoading.value = true
    const res = await fileApi.uploadImage(file.raw)
    // 保存图片URL到表单数据
    formData.value.data[fieldCode] = res.url
    ElMessage.success('图片上传成功')
  } catch (error) {
    console.error('图片上传失败:', error)
    ElMessage.error('图片上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// 处理文件移除
const handleFileRemove = (fieldCode) => {
  formData.value.data[fieldCode] = null
}

// 上传前校验
const beforeFileUpload = (file, fieldType) => {
  if (fieldType === 'IMAGE') {
    const isImage = file.type.startsWith('image/')
    if (!isImage) {
      ElMessage.error('只能上传图片文件')
      return false
    }
    const isLt5M = file.size / 1024 / 1024 < 5
    if (!isLt5M) {
      ElMessage.error('图片大小不能超过5MB')
      return false
    }
  } else {
    const isLt20M = file.size / 1024 / 1024 < 20
    if (!isLt20M) {
      ElMessage.error('文件大小不能超过20MB')
      return false
    }
  }
  return true
}

// 图片预览
const handlePictureCardPreview = (file) => {
  previewImageUrl.value = file.url || file.response?.url || ''
  previewImageVisible.value = true
}

// 获取文件列表（用于回显）
const getFileList = (fieldCode) => {
  const url = formData.value.data[fieldCode]
  if (!url) return []
  return [{
    name: url.split('/').pop() || 'file',
    url: url
  }]
}

// 格式化字段值（处理文件/图片显示）
const formatFieldValue = (field, value) => {
  if (value === null || value === undefined) return '-'
  if (field.fieldType === 'FILE') {
    return value ? `<a href="${value}" target="_blank">${value.split('/').pop()}</a>` : '-'
  }
  if (field.fieldType === 'IMAGE') {
    return value ? `<img src="${value}" style="max-width: 100px; max-height: 100px;" />` : '-'
  }
  if (['SELECT', 'RADIO'].includes(field.fieldType)) {
    const options = parseOptions(field.optionsJson)
    const opt = options.find(o => o.value === value)
    return opt?.label || value
  }
  if (['MULTI_SELECT', 'CHECKBOX'].includes(field.fieldType)) {
    if (!Array.isArray(value)) return value
    const options = parseOptions(field.optionsJson)
    return value.map(v => options.find(o => o.value === v)?.label || v).join(', ')
  }
  if (field.fieldType === 'DATE') {
    return dayjs(value).format('YYYY-MM-DD')
  }
  if (field.fieldType === 'DATETIME') {
    return dayjs(value).format('YYYY-MM-DD HH:mm')
  }
  return value
}

const getStatusType = (status) => {
  const types = { 'DRAFT': 'info', 'PENDING': 'warning', 'APPROVED': 'success', 'REJECTED': 'danger', 'WITHDRAWN': 'info' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 'DRAFT': '草稿', 'PENDING': '审批中', 'APPROVED': '已通过', 'REJECTED': '已驳回', 'WITHDRAWN': '已撤回' }
  return texts[status] || status
}

const formatDate = (date) => {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '-'
}

const handleSearch = () => {
  // 实现查询逻辑
  loadData()
}

const handleReset = () => {
  queryForm.value = {}
  loadData()
}

const handleCreate = async () => {
  isEdit.value = false
  dialogTitle.value = '新增数据'
  formLoading.value = true
  
  // 调用接口获取解析后的表单（流程节点表单或默认表单）
  try {
    const form = await getFormForNewData(entityCode)
    resolvedForm.value = form
    
    // 如果有解析的表单，初始化表单数据
    if (form && form.fields) {
      const initialData = {}
      form.fields.forEach(field => {
        // 设置默认值 - 使用 fieldCode 作为数据的 key
        const key = field.fieldCode || field.fieldId
        if (field.defaultValue) {
          initialData[key] = field.defaultValue
        }
      })
      formData.value = {
        entityCode: entityCode,
        title: '',
        data: initialData,
        submitterId: userStore.username,
        submitterName: userStore.nickname,
        startProcess: entityDefinition.value.enableProcess
      }
    } else {
      // 没有表单时，使用空表单
      formData.value = {
        entityCode: entityCode,
        title: '',
        data: {},
        submitterId: userStore.username,
        submitterName: userStore.nickname,
        startProcess: entityDefinition.value.enableProcess
      }
      if (!form) {
        ElMessage.warning('未配置表单，请先在实体表单管理中配置默认表单')
      }
    }
  } catch (error) {
    console.error('获取表单失败:', error)
    ElMessage.error('获取表单失败')
    // 失败时使用空表单
    formData.value = {
      entityCode: entityCode,
      title: '',
      data: {},
      submitterId: userStore.username,
      submitterName: userStore.nickname,
      startProcess: entityDefinition.value.enableProcess
    }
  } finally {
    formLoading.value = false
  }
  
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑数据'
  resolvedForm.value = null // 编辑时不使用解析的表单，使用实体字段
  formData.value = {
    id: row.id,
    entityCode: entityCode,
    title: row.title,
    data: { ...row.data },
    submitterId: row.submitterId,
    submitterName: row.submitterName,
    startProcess: false
  }
  dialogVisible.value = true
}

const handleView = async (row) => {
  currentRow.value = row
  viewDialogVisible.value = true
  viewFormLoading.value = true
  
  // 调用接口获取当前流程节点对应的表单
  try {
    const form = await getFormForViewData(entityCode, row.id)
    viewResolvedForm.value = form
  } catch (error) {
    console.error('获取查看表单失败:', error)
    viewResolvedForm.value = null
  } finally {
    viewFormLoading.value = false
  }
}

const handleViewProcess = (row) => {
  // 跳转到流程进度查看页
  router.push(`/process/progress/${row.processInstanceId}`)
}

// 处理审批
const handleApprove = async (row) => {
  try {
    // 使用输入框获取审批意见
    const { value: action } = await ElMessageBox.confirm(
      `确定要处理任务 "${row.currentTaskName}" 吗？`,
      '任务审批',
      {
        confirmButtonText: '通过',
        cancelButtonText: '驳回',
        distinguishCancelAndClose: true,
        type: 'warning'
      }
    ).catch((action) => {
      if (action === 'cancel') {
        return { value: 'reject' }
      }
      return null
    })
    
    if (!action) return
    
    const actionType = action === 'confirm' ? 'approve' : 'reject'
    const actionText = actionType === 'approve' ? '通过' : '驳回'
    
    // 输入审批备注
    const { value: comment } = await ElMessageBox.prompt(
      `请输入审批备注（${actionText}）`,
      '审批备注',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入审批备注（可选）'
      }
    ).catch(() => null)
    
    if (comment === null) return
    
    // 调用审批接口
    await processTaskApi.completeTask({
      taskId: row.currentTaskId,
      action: actionType,
      comment: comment || ''
    })
    
    ElMessage.success(`审批${actionText}成功`)
    loadData() // 刷新列表
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('审批失败')
    }
  }
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await entityDataApi.update(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await entityDataApi.save(formData.value, formData.value.startProcess)
      ElMessage.success(formData.value.startProcess ? '保存并发起流程成功' : '保存成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error(error)
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该数据吗？', '提示', { type: 'warning' })
    await entityDataApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

onMounted(() => {
  loadEntity()
  loadData()
})
</script>

<style scoped>
.entity-data-manage {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.title {
  font-size: 18px;
  font-weight: bold;
}

.search-card {
  margin-bottom: 20px;
}

.tip-text {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

/* 文件上传样式 */
.file-upload {
  width: 100%;
}

.file-upload :deep(.el-upload-list) {
  width: 100%;
}

/* 图片上传样式 */
.image-upload :deep(.el-upload--picture-card) {
  width: 120px;
  height: 120px;
  line-height: 120px;
}

.image-upload :deep(.el-upload-list--picture-card .el-upload-list__item) {
  width: 120px;
  height: 120px;
}

/* 文件链接样式 */
.file-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #409eff;
  text-decoration: none;
  padding: 5px 10px;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  background-color: #ecf5ff;
  transition: all 0.3s;
}

.file-link:hover {
  background-color: #409eff;
  color: #fff;
}

/* 图片预览样式 */
.preview-image {
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  cursor: pointer;
  transition: all 0.3s;
}

.preview-image:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.3);
}
</style>
