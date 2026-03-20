<template>
  <div class="entity-form-manage">
    <div class="page-header">
      <h2>实体表单管理</h2>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建表单
      </el-button>
    </div>

    <!-- 实体选择 -->
    <el-card class="entity-select-card" shadow="never">
      <el-form inline>
        <el-form-item label="选择实体">
          <el-select v-model="selectedEntityId" placeholder="请选择实体" clearable @change="handleEntityChange">
            <el-option
              v-for="entity in entityList"
              :key="entity.id"
              :label="entity.entityName"
              :value="entity.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表单列表 -->
    <el-card shadow="never">
      <el-table :data="formList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="formName" label="表单名称" min-width="150" />
        <el-table-column prop="formKey" label="表单标识" min-width="150" />
        <el-table-column prop="entityName" label="所属实体" min-width="150">
          <template #default="{ row }">
            {{ row.entity?.entityName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="layoutType" label="布局" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.layoutType === 'vertical'">垂直</el-tag>
            <el-tag v-else-if="row.layoutType === 'horizontal'" type="success">水平</el-tag>
            <el-tag v-else-if="row.layoutType === 'grid'" type="warning">网格</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isDefault" label="默认表单" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="warning">默认</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">启用</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDesign(row)">
              设计
            </el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button type="success" link size="small" @click="handlePreview(row)">
              预览
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑表单' : '新建表单'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属实体" prop="entityId">
          <el-select v-model="form.entityId" placeholder="请选择实体" style="width: 100%" :disabled="isEdit">
            <el-option
              v-for="entity in entityList"
              :key="entity.id"
              :label="entity.entityName"
              :value="entity.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="表单名称" prop="formName">
          <el-input v-model="form.formName" placeholder="请输入表单名称" />
        </el-form-item>
        <el-form-item label="表单标识" prop="formKey">
          <el-input v-model="form.formKey" placeholder="请输入表单标识" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="布局类型">
          <el-radio-group v-model="form.layoutType">
            <el-radio label="vertical">垂直</el-radio>
            <el-radio label="horizontal">水平</el-radio>
            <el-radio label="grid">网格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="默认表单">
          <el-switch
            v-model="form.isDefault"
            :active-value="1"
            :inactive-value="0"
            active-text="设为默认表单"
          />
          <div class="form-tip">默认表单会在流程节点未选择表单时自动使用</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="表单预览" width="800px">
      <FormPreview v-if="previewForm" :form="previewForm" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import FormPreview from '@/components/FormPreview.vue'
import { entityApi } from '@/api/entity'
import { getFormsByEntity, getFormById, createForm, updateForm, deleteForm } from '@/api/entityForm'

const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const entityList = ref([])
const formList = ref([])
const selectedEntityId = ref('')
const previewForm = ref(null)

const form = reactive({
  id: '',
  entityId: '',
  formName: '',
  formKey: '',
  layoutType: 'vertical',
  isDefault: 0,
  status: 1,
  description: ''
})

const rules = {
  entityId: [{ required: true, message: '请选择实体', trigger: 'change' }],
  formName: [{ required: true, message: '请输入表单名称', trigger: 'blur' }],
  formKey: [
    { required: true, message: '请输入表单标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '标识必须以字母开头，只能包含字母、数字、下划线', trigger: 'blur' }
  ]
}

// 加载实体列表
async function loadEntities() {
  try {
    entityList.value = await entityApi.getList()
  } catch (e) {
    console.error('加载实体列表失败:', e)
  }
}

// 加载表单列表
async function loadForms() {
  loading.value = true
  try {
    if (selectedEntityId.value) {
      formList.value = await getFormsByEntity(selectedEntityId.value)
    } else {
      // 如果没有选择实体，获取所有表单（通过实体列表逐个获取或后端需要提供列表接口）
      formList.value = []
    }
  } catch (e) {
    console.error('加载表单列表失败:', e)
    ElMessage.error('加载表单列表失败')
  } finally {
    loading.value = false
  }
}

function handleEntityChange() {
  loadForms()
}

function handleCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  resetForm()
  Object.assign(form, row)
  dialogVisible.value = true
}

function handleDesign(row) {
  router.push(`/entity-form/design/${row.id}`)
}

async function handlePreview(row) {
  try {
    previewForm.value = await getFormById(row.id)
    previewVisible.value = true
  } catch (e) {
    console.error('加载表单详情失败:', e)
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateForm(form.id, form)
      ElMessage.success('更新成功')
    } else {
      await createForm(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadForms()
  } catch (e) {
    console.error('提交失败:', e)
    ElMessage.error(e.message || '提交失败')
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除表单 "${row.formName}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteForm(row.id)
      ElMessage.success('删除成功')
      loadForms()
    } catch (e) {
      console.error('删除失败:', e)
      ElMessage.error(e.message || '删除失败')
    }
  }).catch(() => {})
}

function resetForm() {
  form.id = ''
  form.entityId = selectedEntityId.value || ''
  form.formName = ''
  form.formKey = ''
  form.layoutType = 'vertical'
  form.isDefault = 0
  form.status = 1
  form.description = ''
}

onMounted(() => {
  loadEntities()
  loadForms()
})
</script>

<style scoped>
.entity-form-manage {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.entity-select-card {
  margin-bottom: 20px;
}
</style>
