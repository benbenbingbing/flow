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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.processInstanceId" link type="success" @click="handleViewProcess(row)">流程</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item v-for="field in formFields" :key="field.fieldCode" 
                     :label="field.fieldName" :prop="`data.${field.fieldCode}`"
                     :rules="field.isRequired ? [{ required: true, message: `请输入${field.fieldName}`, trigger: 'blur' }] : []">
          <!-- 根据字段类型渲染不同组件 -->
          <el-input v-if="field.fieldType === 'STRING'" 
                   v-model="formData.data[field.fieldCode]" :placeholder="`请输入${field.fieldName}`" />
          <el-input v-else-if="field.fieldType === 'TEXT'" 
                   v-model="formData.data[field.fieldCode]" type="textarea" rows="3" :placeholder="`请输入${field.fieldName}`" />
          <el-input-number v-else-if="field.fieldType === 'INTEGER' || field.fieldType === 'DECIMAL'" 
                          v-model="formData.data[field.fieldCode]" style="width: 100%" />
          <el-date-picker v-else-if="field.fieldType === 'DATE'" 
                         v-model="formData.data[field.fieldCode]" type="date" style="width: 100%" />
          <el-date-picker v-else-if="field.fieldType === 'DATETIME'" 
                         v-model="formData.data[field.fieldCode]" type="datetime" style="width: 100%" />
          <el-switch v-else-if="field.fieldType === 'BOOLEAN'" 
                    v-model="formData.data[field.fieldCode]" />
          <el-select v-else-if="field.fieldType === 'SELECT'" 
                    v-model="formData.data[field.fieldCode]" style="width: 100%" clearable>
            <el-option v-for="opt in parseOptions(field.optionsJson)" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <el-checkbox-group v-else-if="field.fieldType === 'MULTI_SELECT' || field.fieldType === 'CHECKBOX'" 
                            v-model="formData.data[field.fieldCode]">
            <el-checkbox v-for="opt in parseOptions(field.optionsJson)" :key="opt.value" :label="opt.value">{{ opt.label }}</el-checkbox>
          </el-checkbox-group>
          <el-radio-group v-else-if="field.fieldType === 'RADIO'" 
                         v-model="formData.data[field.fieldCode]">
            <el-radio v-for="opt in parseOptions(field.optionsJson)" :key="opt.value" :label="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
          <el-upload v-else-if="field.fieldType === 'FILE' || field.fieldType === 'IMAGE'" 
                    action="#" :auto-upload="false">
            <el-button>选择文件</el-button>
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
    <el-dialog v-model="viewDialogVisible" title="数据详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="编号">{{ currentRow.dataNo }}</el-descriptions-item>
        <el-descriptions-item v-for="field in formFields" :key="field.fieldCode" :label="field.fieldName">
          {{ formatFieldValue(field, currentRow.data?.[field.fieldCode]) }}
        </el-descriptions-item>
        <el-descriptions-item label="提交人">{{ currentRow.submitterName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentRow.status)">{{ getStatusText(currentRow.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(currentRow.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { entityApi, entityDataApi } from '@/api/entity'

const route = useRoute()
const router = useRouter()
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

const queryForm = ref({})
const formData = ref({
  entityCode: entityCode,
  title: '',
  data: {},
  submitterId: 'admin',
  submitterName: '管理员',
  startProcess: false
})

// 列表显示字段
const listFields = computed(() => {
  return fields.value.filter(f => f.showInList !== false).slice(0, 5) // 最多显示5个
})

// 查询字段
const queryFields = computed(() => {
  return fields.value.filter(f => f.isQuery)
})

// 表单字段
const formFields = computed(() => {
  return fields.value.filter(f => f.showInForm !== false).sort((a, b) => a.sortOrder - b.sortOrder)
})

const formRules = {}

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

// 格式化字段值
const formatFieldValue = (field, value) => {
  if (value === null || value === undefined) return '-'
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

const handleCreate = () => {
  isEdit.value = false
  dialogTitle.value = '新增数据'
  formData.value = {
    entityCode: entityCode,
    title: '',
    data: {},
    submitterId: 'admin',
    submitterName: '管理员',
    startProcess: entityDefinition.value.enableProcess
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑数据'
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

const handleView = (row) => {
  currentRow.value = row
  viewDialogVisible.value = true
}

const handleViewProcess = (row) => {
  // 跳转到流程进度查看页
  router.push(`/process/progress/${row.processInstanceId}`)
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
</style>
