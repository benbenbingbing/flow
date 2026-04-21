<template>
  <div class="process-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>流程列表</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>新建流程
          </el-button>
        </div>
      </template>
      
      <el-table :data="processList" v-loading="loading" stripe>
        <el-table-column prop="processName" label="流程名称" min-width="150" />
        <el-table-column prop="processKey" label="流程标识" min-width="120" />
        <el-table-column prop="category" label="分类" min-width="100" />
        <el-table-column prop="version" label="当前版本" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.version > 0" type="info">v{{ row.version }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="150">
          <template #default="{ row }">
            {{ formatDate(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="350" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDesign(row)">设计</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="info" @click="handleViewVersions(row)">版本</el-button>
            <el-button 
              v-if="row.status === 'DRAFT' || row.status === 'DISABLED'" 
              link 
              type="success" 
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button 
              v-if="row.status === 'PUBLISHED' || row.status === 'DRAFT'" 
              link 
              type="warning" 
              @click="handleDisable(row)"
            >
              禁用
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="流程名称" prop="processName">
          <el-input v-model="formData.processName" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="流程标识" prop="processKey">
          <el-input 
            v-model="formData.processKey" 
            placeholder="请输入流程标识"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="formData.category" placeholder="请输入分类" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input 
            v-model="formData.description" 
            type="textarea" 
            rows="3"
            placeholder="请输入描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确定
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 版本历史对话框 -->
    <el-dialog
      v-model="versionDialogVisible"
      title="版本历史"
      width="900px"
    >
      <el-table :data="versionList" v-loading="versionLoading" stripe>
        <el-table-column prop="version" label="版本号" width="80">
          <template #default="{ row }">
            <el-tag type="info">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionDescription" label="版本说明" min-width="180">
          <template #default="{ row }">
            {{ row.versionDescription || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" min-width="140">
          <template #default="{ row }">
            {{ formatDate(row.publishedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="流程动作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewVersionActions(row)">
              查看({{ row.actionCount || 0 }})
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewVersionDetail(row)">查看</el-button>
            <el-button link type="danger" @click="handleDeleteVersion(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    
    <!-- 版本流程动作对话框 -->
    <el-dialog
      v-model="versionActionsVisible"
      title="版本流程动作"
      width="700px"
    >
      <el-table :data="versionActions" v-loading="versionActionsLoading" stripe>
        <el-table-column prop="sortOrder" label="顺序" width="60">
          <template #default="{ row }">
            {{ row.sortOrder + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="actionName" label="动作名称" min-width="120" />
        <el-table-column prop="interfaceName" label="接口" min-width="150">
          <template #default="{ row }">
            <code>{{ row.interfaceName }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="methodName" label="方法" width="100" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="versionActions.length === 0" class="empty-actions">
        <el-empty description="该版本暂无流程动作" />
      </div>
    </el-dialog>
    
    <!-- 版本详情对话框（只读） -->
    <el-dialog
      v-model="versionDetailVisible"
      title="版本详情（只读）"
      width="700px"
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item label="版本号">v{{ currentVersion?.version }}</el-descriptions-item>
        <el-descriptions-item label="流程名称">{{ currentVersion?.processName }}</el-descriptions-item>
        <el-descriptions-item label="流程标识">{{ currentVersion?.processKey }}</el-descriptions-item>
        <el-descriptions-item label="版本说明">{{ currentVersion?.versionDescription || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ formatDate(currentVersion?.publishedAt) }}</el-descriptions-item>
        <el-descriptions-item label="发布人">{{ currentVersion?.publishedBy || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="bpmn-preview">
        <div class="preview-title">流程图预览</div>
        <VueBpmnViewer :xml="versionBpmnXml" class="bpmn-container" />
      </div>
    </el-dialog>
    
    <!-- 发布对话框（带版本说明） -->
    <el-dialog
      v-model="publishDialogVisible"
      title="发布流程"
      width="500px"
    >
      <el-form :model="publishForm" label-width="100px">
        <el-form-item label="流程名称">
          <span>{{ currentProcess?.processName }}</span>
        </el-form-item>
        <el-form-item label="当前版本">
          <span>v{{ currentProcess?.version || 0 }}</span>
        </el-form-item>
        <el-form-item label="新版本">
          <span>v{{ (currentProcess?.version || 0) + 1 }}</span>
        </el-form-item>
        <el-form-item label="版本说明">
          <el-input 
            v-model="publishForm.versionDescription" 
            type="textarea" 
            rows="3"
            placeholder="请输入版本说明（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmPublish" :loading="publishing">
          发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { processApi } from '@/api/process'
import { flowActionApi } from '@/api/flowAction'
import VueBpmnViewer from '@/components/VueBpmnViewer.vue'

const router = useRouter()
const loading = ref(false)
const processList = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()

// 版本历史相关
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versionList = ref([])
const currentProcess = ref(null)

// 版本详情
const versionDetailVisible = ref(false)
const currentVersion = ref(null)
const versionBpmnXml = ref('')

// 发布对话框
const publishDialogVisible = ref(false)
const publishing = ref(false)
const publishForm = ref({
  versionDescription: ''
})

// 版本流程动作
const versionActionsVisible = ref(false)
const versionActionsLoading = ref(false)
const versionActions = ref([])
const currentVersionId = ref('')

const formData = ref({
  processName: '',
  processKey: '',
  category: '',
  description: ''
})

const formRules = {
  processName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
  processKey: [
    { required: true, message: '请输入流程标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '必须以字母开头，只能包含字母、数字、下划线', trigger: 'blur' }
  ]
}

const fetchData = async () => {
  loading.value = true
  try {
    processList.value = await processApi.getList()
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const getStatusType = (status) => {
  const types = {
    'DRAFT': 'info',
    'PUBLISHED': 'success',
    'DISABLED': 'danger'
  }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = {
    'DRAFT': '草稿',
    'PUBLISHED': '已发布',
    'DISABLED': '已禁用'
  }
  return texts[status] || status
}

const formatDate = (date) => {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '-'
}

const handleCreate = () => {
  isEdit.value = false
  dialogTitle.value = '新建流程'
  formData.value = {
    processName: '',
    processKey: '',
    category: '',
    description: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑流程'
  formData.value = { ...row }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  
  submitting.value = true
  try {
    if (isEdit.value) {
      await processApi.update(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await processApi.create(formData.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error(error)
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该流程吗？', '提示', { type: 'warning' })
    await processApi.delete(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

const handlePublish = (row) => {
  currentProcess.value = row
  publishForm.value.versionDescription = ''
  publishDialogVisible.value = true
}

const handleConfirmPublish = async () => {
  publishing.value = true
  try {
    await processApi.publish(currentProcess.value.id, publishForm.value.versionDescription)
    ElMessage.success('发布成功')
    publishDialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error(error)
    ElMessage.error(error.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

const handleDisable = async (row) => {
  try {
    await ElMessageBox.confirm('确定要禁用该流程吗？', '提示', { type: 'warning' })
    await processApi.disable(row.id)
    ElMessage.success('禁用成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

const handleDesign = (row) => {
  router.push(`/process/design/${row.id}`)
}

// 查看版本历史
const handleViewVersions = async (row) => {
  currentProcess.value = row
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    versionList.value = await processApi.getVersions(row.id)
  } catch (error) {
    console.error(error)
    ElMessage.error('获取版本历史失败')
  } finally {
    versionLoading.value = false
  }
}

// 查看版本详情
const handleViewVersionDetail = async (row) => {
  currentVersion.value = row
  versionBpmnXml.value = row.bpmnXml || ''
  versionDetailVisible.value = true
}

// 查看版本流程动作
const handleViewVersionActions = async (row) => {
  currentVersionId.value = row.id
  versionActionsVisible.value = true
  versionActionsLoading.value = true
  
  try {
    const res = await flowActionApi.findPublishedActions(row.id)
    versionActions.value = res || []
  } catch (error) {
    console.error(error)
    ElMessage.error('获取流程动作失败')
  } finally {
    versionActionsLoading.value = false
  }
}

// 删除版本
const handleDeleteVersion = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该版本吗？删除后不可恢复！', '警告', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
    
    await processApi.deleteVersion(row.id)
    ElMessage.success('版本已删除')
    // 刷新版本列表
    if (currentProcess.value) {
      handleViewVersions(currentProcess.value)
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.process-list {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bpmn-preview {
  margin-top: 20px;
}

.preview-title {
  font-weight: bold;
  margin-bottom: 10px;
}

.bpmn-container {
  height: 400px;
  background: #f5f5f5;
  border: 1px solid #dcdfe6;
}

.empty-actions {
  padding: 40px 0;
}

code {
  background-color: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  font-family: monospace;
}
</style>
