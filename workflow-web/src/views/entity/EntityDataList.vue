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
        <el-table :data="dataList" v-loading="tableLoading" stripe>
          <el-table-column type="index" width="50" />
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
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleView(row)">查看</el-button>
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
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
          <!-- 用户选择 -->
          <EntitySelector v-else-if="field.fieldType === 'USER' || (field.fieldType === 'REFERENCE' && field.refEntityType === 'USER')"
                         v-model="formData.data[field.fieldCode]"
                         entity-type="USER"
                         :placeholder="`请选择${field.fieldName}`" />
          <!-- 部门选择 -->
          <EntitySelector v-else-if="field.fieldType === 'DEPT' || (field.fieldType === 'REFERENCE' && field.refEntityType === 'DEPT')"
                         v-model="formData.data[field.fieldCode]"
                         entity-type="DEPT"
                         :placeholder="`请选择${field.fieldName}`" />
          <!-- 通用实体引用 -->
          <EntitySelector v-else-if="field.fieldType === 'REFERENCE' || field.fieldType === 'MULTI_REFERENCE'"
                         v-model="formData.data[field.fieldCode]"
                         :entity-type="field.refEntityType || 'CUSTOM'"
                         :entity-code="field.refEntityId"
                         :multiple="field.fieldType === 'MULTI_REFERENCE'"
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
    
    <!-- 查看详情对话框 -->
    <el-dialog v-model="viewDialogVisible" title="数据详情" width="700px">
      <el-descriptions :column="2" border>
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
      
      <el-divider>自定义字段</el-divider>
      
      <el-descriptions :column="2" border>
        <el-descriptions-item :label="field.fieldName" v-for="field in formFields.filter(f => !f.isSystem)" :key="field.fieldCode">
          {{ currentRow.data?.[field.fieldCode] || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { entityApi, entityDataApi } from '@/api/entity'
import EntitySelector from '@/components/EntitySelector.vue'

const route = useRoute()
const router = useRouter()

// 从路由参数获取实体编码
const entityCode = computed(() => route.params.entityCode as string || route.query.entityCode as string)

// 状态
const loading = ref(false)
const tableLoading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const dialogTitle = ref('')
const currentRow = ref<any>({})

// 实体定义
const entityDefinition = ref<any>({})
const entityFields = ref<any[]>([])

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

// 计算属性
const entityName = computed(() => entityDefinition.value?.entityName)

// 查询字段（配置了isQuery的字段）
const queryFields = computed(() => {
  return entityFields.value.filter((f: any) => f.isQuery && !f.isSystem)
})

// 列表显示字段（配置了showInList的字段）
const listFields = computed(() => {
  return entityFields.value.filter((f: any) => f.showInList && !f.isSystem)
})

// 表单字段（配置了showInForm的字段）
const formFields = computed(() => {
  return entityFields.value.filter((f: any) => f.showInForm && !f.isSystem)
})

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
    
    // 初始化查询表单
    entityFields.value.forEach((field: any) => {
      if (field.isQuery) {
        queryForm[field.fieldCode] = ''
      }
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

// 加载数据列表
const loadDataList = async () => {
  if (!entityCode.value) return
  
  tableLoading.value = true
  try {
    const res = await entityDataApi.getList(entityCode.value)
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
}

// 编辑
const handleEdit = (row: any) => {
  formData.id = row.id
  formData.name = row.name
  formData.data = { ...row.data }
  dialogTitle.value = '编辑数据'
  dialogVisible.value = true
}

// 查看
const handleView = (row: any) => {
  currentRow.value = row
  viewDialogVisible.value = true
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
</style>
