<template>
  <div class="entity-form-list">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="title">{{ entityInfo.entityName }} - 表单管理</span>
      </div>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>新建表单
      </el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="formList" v-loading="loading" stripe>
        <el-table-column type="index" width="50" />
        <el-table-column prop="formName" label="表单名称" min-width="150" />
        <el-table-column prop="formKey" label="表单标识" min-width="150" />
        <el-table-column prop="layoutType" label="布局" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.layoutType === 'vertical'">垂直</el-tag>
            <el-tag v-else-if="row.layoutType === 'horizontal'" type="success">水平</el-tag>
            <el-tag v-else-if="row.layoutType === 'grid'" type="warning">网格</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">启用</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDesign(row)">设计</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="info" link size="small" @click="handleCopy(row)">复制</el-button>
            <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="formList.length === 0 && !loading" description="暂无表单，点击右上角新建表单" />
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑表单' : '新建表单'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import FormPreview from '@/components/FormPreview.vue'

const route = useRoute()
const router = useRouter()
const entityId = route.params.entityId

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const entityInfo = ref({})
const formList = ref([])
const previewForm = ref(null)

const form = reactive({
  id: '',
  entityId: entityId,
  formName: '',
  formKey: '',
  layoutType: 'vertical',
  status: 1,
  description: ''
})

const rules = {
  formName: [{ required: true, message: '请输入表单名称', trigger: 'blur' }],
  formKey: [
    { required: true, message: '请输入表单标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '必须以字母开头，只能包含字母、数字、下划线', trigger: 'blur' }
  ]
}

// 加载实体信息
async function loadEntityInfo() {
  try {
    const res = await fetch(`/api/entity/${entityId}`).then(r => r.json())
    if (res.code === 200) {
      entityInfo.value = res.data
    }
  } catch (e) {
    console.error('加载实体信息失败:', e)
  }
}

// 加载表单列表
async function loadForms() {
  loading.value = true
  try {
    const res = await fetch(`/api/entity-form/entity/${entityId}`).then(r => r.json())
    if (res.code === 200) {
      formList.value = res.data || []
    }
  } catch (e) {
    console.error('加载表单列表失败:', e)
    ElMessage.error('加载表单列表失败')
  } finally {
    loading.value = false
  }
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
  // 跳转到表单设计页面，传入表单ID
  router.push(`/entity-form/design/${row.id}?entityId=${entityId}`)
}

async function handlePreview(row) {
  try {
    // 同时加载表单信息和字段
    const [formRes, fieldsRes] = await Promise.all([
      fetch(`/api/entity-form/${row.id}`).then(r => r.json()),
      fetch(`/api/entity-form/${row.id}/fields`).then(r => r.json())
    ])
    if (formRes.code === 200) {
      previewForm.value = {
        ...formRes.data,
        fields: fieldsRes.data || []
      }
      previewVisible.value = true
    }
  } catch (e) {
    console.error('加载表单详情失败:', e)
    ElMessage.error('加载预览失败')
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    const url = isEdit.value ? `/api/entity-form/${form.id}` : '/api/entity-form'
    const method = isEdit.value ? 'PUT' : 'POST'
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form)
    }).then(r => r.json())

    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadForms()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    console.error('提交失败:', e)
    ElMessage.error('提交失败')
  } finally {
    submitLoading.value = false
  }
}

async function handleCopy(row) {
  try {
    const res = await fetch(`/api/entity-form/${row.id}/copy`, { 
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }).then(r => r.json())
    
    if (res.code === 200) {
      ElMessage.success(`表单 "${row.formName}" 复制成功`)
      loadForms()
    } else {
      ElMessage.error(res.message || '复制失败')
    }
  } catch (e) {
    console.error('复制失败:', e)
    ElMessage.error('复制失败')
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除表单 "${row.formName}" 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await fetch(`/api/entity-form/${row.id}`, { method: 'DELETE' }).then(r => r.json())
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadForms()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (e) {
      console.error('删除失败:', e)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

function resetForm() {
  form.id = ''
  form.entityId = entityId
  form.formName = ''
  form.formKey = ''
  form.layoutType = 'vertical'
  form.status = 1
  form.description = ''
}

onMounted(() => {
  loadEntityInfo()
  loadForms()
})
</script>

<style scoped>
.entity-form-list {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.title {
  font-size: 18px;
  font-weight: 500;
}
</style>
