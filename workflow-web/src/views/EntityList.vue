<template>
  <div class="entity-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>实体管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>新建实体
          </el-button>
        </div>
      </template>

      <el-table :data="entityList" v-loading="loading" stripe>
        <el-table-column prop="entityName" label="实体名称" min-width="150" />
        <el-table-column prop="entityCode" label="实体编码" min-width="120" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="enableProcess" label="启用流程" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.enableProcess" type="success">是</el-tag>
            <el-tag v-else type="info">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="processName" label="绑定流程" min-width="150">
          <template #default="{ row }">
            {{ row.processName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDesign(row)">设计</el-button>
            <el-button link type="primary" @click="handleData(row)">数据管理</el-button>
            <el-button link type="success" @click="handleBindProcess(row)">绑定流程</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="实体名称" prop="entityName">
          <el-input v-model="formData.entityName" placeholder="请输入实体名称" />
        </el-form-item>
        <el-form-item label="实体编码" prop="entityCode">
          <el-input v-model="formData.entityCode" placeholder="请输入实体编码" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 绑定流程对话框 -->
    <el-dialog v-model="bindDialogVisible" title="绑定流程" width="500px">
      <el-form label-width="100px">
        <el-form-item label="选择流程">
          <el-select v-model="selectedProcessId" placeholder="请选择要绑定的流程" style="width: 100%">
            <el-option
              v-for="process in processList"
              :key="process.id"
              :label="process.processName"
              :value="process.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmBind" :loading="bindLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityApi } from '@/api/entity'
import { processApi } from '@/api/process'

const router = useRouter()
const loading = ref(false)
const entityList = ref([])
const processList = ref([])
const dialogVisible = ref(false)
const bindDialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitting = ref(false)
const bindLoading = ref(false)
const selectedProcessId = ref('')
const currentEntity = ref(null)
const formRef = ref()

const formData = ref({
  entityName: '',
  entityCode: '',
  description: ''
})

const formRules = {
  entityName: [{ required: true, message: '请输入实体名称', trigger: 'blur' }],
  entityCode: [
    { required: true, message: '请输入实体编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '必须以字母开头，只能包含字母、数字、下划线', trigger: 'blur' }
  ]
}

const fetchData = async () => {
  loading.value = true
  try {
    entityList.value = await entityApi.getList()
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const fetchProcessList = async () => {
  try {
    // 使用专门获取已发布流程的接口
    processList.value = await processApi.getPublishedList()
  } catch (error) {
    console.error(error)
    ElMessage.error('获取流程列表失败')
  }
}

const getStatusType = (status) => {
  const types = { 'DRAFT': 'info', 'PUBLISHED': 'success', 'DISABLED': 'danger' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 'DRAFT': '草稿', 'PUBLISHED': '已发布', 'DISABLED': '已禁用' }
  return texts[status] || status
}

const handleCreate = () => {
  isEdit.value = false
  dialogTitle.value = '新建实体'
  formData.value = { entityName: '', entityCode: '', description: '' }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await entityApi.update(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await entityApi.create(formData.value)
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

const handleDesign = (row) => {
  router.push(`/entity/design/${row.id}`)
}

const handleData = (row) => {
  router.push(`/entity/data/${row.entityCode}`)
}

const handleBindProcess = (row) => {
  currentEntity.value = row
  selectedProcessId.value = row.processDefinitionId
  fetchProcessList()
  bindDialogVisible.value = true
}

const handleConfirmBind = async () => {
  if (!selectedProcessId.value) {
    ElMessage.warning('请选择流程')
    return
  }
  bindLoading.value = true
  try {
    await entityApi.bindProcess(currentEntity.value.id, selectedProcessId.value)
    ElMessage.success('绑定成功')
    bindDialogVisible.value = false
    fetchData()
  } catch (error) {
    console.error(error)
  } finally {
    bindLoading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该实体吗？', '提示', { type: 'warning' })
    await entityApi.delete(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.entity-list {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
