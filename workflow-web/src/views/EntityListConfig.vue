<template>
  <div class="entity-list-config-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button link @click="goBack">
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
            <span>实体列表配置：{{ entityName }}</span>
          </div>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>新建列表配置
          </el-button>
        </div>
      </template>

      <el-table :data="configList" v-loading="loading" stripe border>
        <el-table-column type="index" width="50" />
        <el-table-column prop="listName" label="列表名称" min-width="150" />
        <el-table-column prop="listKey" label="列表标识" min-width="120" />
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDesign(row)">设计</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && configList.length === 0" description="暂无列表配置" />
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="列表名称" prop="listName">
          <el-input v-model="formData.listName" placeholder="请输入列表名称" />
        </el-form-item>
        <el-form-item label="列表标识" prop="listKey">
          <el-input v-model="formData.listKey" placeholder="如：default、myList" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="formData.description" type="textarea" rows="3" placeholder="请输入说明" />
        </el-form-item>
        <el-form-item label="默认列表">
          <el-switch v-model="formData.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, ArrowLeft } from '@element-plus/icons-vue'
import { entityListConfigApi } from '@/api/entityListConfig'
import { entityApi } from '@/api/entity'

const route = useRoute()
const router = useRouter()
const entityId = route.params.entityId

const loading = ref(false)
const configList = ref([])
const entityName = ref('')

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const formData = ref({
  id: '',
  entityId: entityId,
  entityCode: '',
  listKey: '',
  listName: '',
  description: '',
  isDefault: false,
  fields: []
})

const formRules = {
  listName: [{ required: true, message: '请输入列表名称', trigger: 'blur' }],
  listKey: [
    { required: true, message: '请输入列表标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '只能包含字母、数字和下划线', trigger: 'blur' }
  ]
}

onMounted(() => {
  loadEntityInfo()
  loadConfigList()
})

async function loadEntityInfo() {
  try {
    const res = await entityApi.getById(entityId)
    if (res) {
      entityName.value = res.entityName || ''
      formData.value.entityCode = res.entityCode || ''
    }
  } catch (e) {
    console.error('加载实体信息失败:', e)
  }
}

async function loadConfigList() {
  loading.value = true
  try {
    const res = await entityListConfigApi.getByEntityId(entityId)
    configList.value = res || []
  } catch (e) {
    console.error('加载列表配置失败:', e)
    ElMessage.error('加载列表配置失败')
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEdit.value = false
  dialogTitle.value = '新建列表配置'
  formData.value = {
    id: '',
    entityId: entityId,
    entityCode: formData.value.entityCode,
    listKey: '',
    listName: '',
    description: '',
    isDefault: false,
    fields: []
  }
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  dialogTitle.value = '编辑列表配置'
  formData.value = {
    id: row.id,
    entityId: row.entityId,
    entityCode: row.entityCode,
    listKey: row.listKey,
    listName: row.listName,
    description: row.description,
    isDefault: row.isDefault,
    fields: row.fields || []
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await entityListConfigApi.save(formData.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadConfigList()
  } catch (e) {
    console.error('保存列表配置失败:', e)
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除列表配置「${row.listName}」吗？`, '提示', { type: 'warning' })
    await entityListConfigApi.delete(row.id)
    ElMessage.success('删除成功')
    loadConfigList()
  } catch {
    // 取消
  }
}

function handleDesign(row) {
  router.push(`/entity-list-config/design/${row.id}`)
}

function goBack() {
  router.back()
}
</script>

<style scoped>
.entity-list-config-page {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
}
</style>
