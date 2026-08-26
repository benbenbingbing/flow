<template>
  <div class="entity-form-list">
    <div class="page-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="title">{{ entityInfo.entityName }} - 表单管理</span>
      </div>
      <el-button
        type="primary"
        :disabled="!entityInfo.entityCode"
        @click="handleCreate"
      >
        <el-icon><Plus /></el-icon>新建表单
      </el-button>
    </div>

    <el-alert
      v-if="entityInfo.storageMode === 'SYSTEM'"
      title="平台系统表结构只读，当前表单仅用于详情查看布局，不会开放新增或编辑数据。"
      type="warning"
      :closable="false"
      show-icon
      class="system-config-alert"
    />

    <el-card shadow="never">
      <PageState
        v-if="loadError"
        type="error"
        title="表单列表加载失败"
        :description="loadError"
        retryable
        @retry="loadForms"
      />
      <el-table v-else :data="formList" v-loading="loading" stripe>
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
        <el-table-column prop="isDefault" label="默认表单" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success">默认</el-tag>
            <el-tag v-else type="info">-</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success">启用</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateValue(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="table-row-actions">
              <el-button type="primary" link size="small" @click="handleDesign(row)">设计</el-button>
              <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
              <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button
                v-if="!row.isDefault"
                type="primary"
                link
                size="small"
                @click="handleSetDefault(row)"
              >
                默认
              </el-button>
              <el-button type="primary" link size="small" @click="handleCopy(row)">复制</el-button>
              <el-tooltip
                v-if="entityInfo.storageMode !== 'SYSTEM'"
                :content="formDataConfigSummary(row)"
                placement="top"
              >
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="handleDataConfig(row)"
                >
                  数据配置<span v-if="formDataConfigCount(row)">({{ formDataConfigCount(row) }})</span>
                </el-button>
              </el-tooltip>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="formList.length === 0 && !loading" description="暂无表单，点击右上角新建表单" />
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑表单' : '新建表单'"
      width="min(680px, 92vw)"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="表单名称" prop="formName">
          <el-input v-model="form.formName" placeholder="请输入表单名称" />
        </el-form-item>
        <el-form-item label="表单标识" prop="formKey">
          <el-input
            v-if="isEdit"
            v-model="form.formKey"
            placeholder="请输入表单标识"
            disabled
          />
          <el-input
            v-else
            v-model="form.formKey"
            :maxlength="formKeySuffixMaxLength"
            placeholder="如：detail、approval"
          >
            <template #prepend>{{ formKeyPrefix }}</template>
          </el-input>
          <div class="field-help">
            {{ isEdit
              ? '用于流程节点绑定和发布版本识别，创建后不可修改。'
              : '实体编码为固定前缀，将与输入内容一起保存，创建后不可修改。' }}
          </div>
        </el-form-item>
        <el-form-item label="布局类型">
          <el-radio-group v-model="form.layoutType">
            <el-radio value="vertical">垂直</el-radio>
            <el-radio value="horizontal">水平</el-radio>
            <el-radio value="grid">网格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
          {{ isEdit ? '保存基本信息' : '创建表单' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyDialogVisible" title="复制表单" width="500px">
      <el-form ref="copyFormRef" :model="copyFormData" :rules="copyRules" label-width="100px">
        <el-form-item label="表单名称" prop="formName">
          <el-input v-model="copyFormData.formName" placeholder="请输入新表单名称" />
        </el-form-item>
        <el-form-item label="表单标识" prop="formKey">
          <el-input
            v-model="copyFormData.formKey"
            maxlength="100"
            placeholder="请输入新表单标识"
          />
          <div class="field-help">复制后将作为流程和发布引用的稳定标识，请在创建前确认。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="copyLoading" @click="submitCopy">
          复制表单
        </el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewForm ? `表单预览 | ${previewForm.formName}` : '表单预览'" width="800px">
      <FormPreviewLinkage v-if="previewForm" :form="previewForm" :showHeader="false" />
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import { entityApi } from '@/api/entity'
import { getFormsByEntity, getFormById, createForm, updateForm, deleteForm, getFormFields, setDefaultForm, copyForm } from '@/api/entityForm'
import {
  formatFormDataSourceBindingSummary,
  totalFormDataSourceBindings
} from '@/shared/form-runtime'
import { formatDateValue } from '@/shared/list-runtime'
import PageState from '@/components/PageState.vue'
import {
  buildEntityConfigKey,
  getEntityConfigKeyPrefix,
  getEntityConfigKeySuffixMaxLength
} from '@/shared/entity-config-key'

const route = useRoute()
const router = useRouter()
const entityId = route.params.entityId

const loading = ref(false)
const loadError = ref('')
const submitLoading = ref(false)
const dialogVisible = ref(false)
const copyDialogVisible = ref(false)
const copyLoading = ref(false)
const previewVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const copyFormRef = ref(null)
const copySourceFormId = ref('')

const entityInfo = ref({})
const formList = ref([])
const previewForm = ref(null)
const formKeyPrefix = computed(() =>
  getEntityConfigKeyPrefix(entityInfo.value.entityCode)
)
const formKeySuffixMaxLength = computed(() =>
  getEntityConfigKeySuffixMaxLength(entityInfo.value.entityCode)
)

const form = reactive({
  id: '',
  entityId: entityId,
  formName: '',
  formKey: '',
  layoutType: 'vertical',
  status: 1,
  description: ''
})

const copyFormData = reactive({
  formName: '',
  formKey: ''
})

const rules = {
  formName: [{ required: true, message: '请输入表单名称', trigger: 'blur' }],
  formKey: [
    { required: true, message: '请输入表单标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '必须以字母开头，只能包含字母、数字、下划线', trigger: 'blur' }
  ]
}

const copyRules = {
  formName: [{ required: true, message: '请输入新表单名称', trigger: 'blur' }],
  formKey: [
    { required: true, message: '请输入新表单标识', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_-]{0,99}$/,
      message: '必须以字母开头，只能包含字母、数字、下划线和短横线，最长 100 个字符',
      trigger: 'blur'
    }
  ]
}

// 加载实体信息
async function loadEntityInfo() {
  try {
    entityInfo.value = await entityApi.getById(entityId)
  } catch (e) {
    console.error('加载实体信息失败:', e)
  }
}

// 加载表单列表
async function loadForms() {
  loading.value = true
  loadError.value = ''
  try {
    formList.value = await getFormsByEntity(entityId)
  } catch (e) {
    console.error('加载表单列表失败:', e)
    loadError.value = e?.message || '无法读取表单列表，请重试'
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  if (!entityInfo.value.entityCode) {
    ElMessage.warning('实体编码尚未加载，暂时无法新建表单')
    return
  }
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

/**
 * 列表只提供统一数据配置的深链入口，实际编辑始终在表单设计器完成。
 */
function handleDataConfig(row) {
  router.push({
    name: 'EntityFormDesign',
    params: { id: row.id },
    query: {
      entityId,
      settings: 'data-events',
      section: 'data-source'
    }
  })
}

function formDataConfigSummary(row) {
  return formatFormDataSourceBindingSummary(
    row.dataSourceBindingsDocument || row.dataSourceBindings
  )
}

function formDataConfigCount(row) {
  return totalFormDataSourceBindings(
    row.dataSourceBindingsDocument || row.dataSourceBindings
  )
}

async function handlePreview(row) {
  try {
    // 同时加载表单信息和字段
    const [formData, fields] = await Promise.all([
      getFormById(row.id),
      getFormFields(row.id)
    ])
    previewForm.value = {
      ...formData,
      fields: fields || []
    }
    previewVisible.value = true
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
    if (isEdit.value) {
      await updateForm(form.id, form)
      ElMessage.success('更新成功')
    } else {
      // 新增时只让用户维护后缀，提交前再合成稳定的实体级表单标识。
      await createForm({
        ...form,
        formKey: buildEntityConfigKey(entityInfo.value.entityCode, form.formKey)
      })
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

async function handleSetDefault(row) {
  try {
    await setDefaultForm(row.id)
    ElMessage.success(`已将 "${row.formName}" 设为默认表单`)
    loadForms()
  } catch (e) {
    console.error('设置默认表单失败:', e)
    ElMessage.error(e.message || '设置默认表单失败')
  }
}

function handleCopy(row) {
  copySourceFormId.value = row.id
  copyFormData.formName = `${row.formName} copy`
  copyFormData.formKey = nextCopyFormKey(row.formKey)
  copyDialogVisible.value = true
}

function nextCopyFormKey(sourceKey) {
  const maxLength = 100
  const existingKeys = new Set(formList.value.map(item => item.formKey))
  const appendSuffix = (key, suffix) =>
    `${key.slice(0, maxLength - suffix.length)}${suffix}`
  const baseKey = appendSuffix(sourceKey || 'form', '_copy')
  if (!existingKeys.has(baseKey)) return baseKey
  for (let sequence = 2; ; sequence += 1) {
    const candidate = appendSuffix(sourceKey || 'form', `_copy_${sequence}`)
    if (!existingKeys.has(candidate)) return candidate
  }
}

async function submitCopy() {
  const valid = await copyFormRef.value?.validate().catch(() => false)
  if (!valid || !copySourceFormId.value) return

  copyLoading.value = true
  try {
    await copyForm(copySourceFormId.value, {
      formName: copyFormData.formName.trim(),
      formKey: copyFormData.formKey.trim()
    })
    ElMessage.success(`表单 "${copyFormData.formName}" 复制成功`)
    copyDialogVisible.value = false
    await loadForms()
  } catch (e) {
    console.error('复制失败:', e)
    ElMessage.error(e.message || '复制失败')
  } finally {
    copyLoading.value = false
  }
}

async function handleDelete(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `删除前系统会校验流程节点、子表单引用和发布版本。该操作不可恢复，请输入表单名称“${row.formName}”确认。`,
      '删除表单',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
        inputPlaceholder: row.formName,
        inputValidator: value => value === row.formName || '输入的表单名称不一致'
      }
    )
    if (value !== row.formName) return
    try {
      await deleteForm(row.id)
      ElMessage.success('删除成功')
      loadForms()
    } catch (e) {
      console.error('删除失败:', e)
      ElMessage.error(e.message || '删除失败')
    }
  } catch {}
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

.system-config-alert {
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.field-help {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.title {
  font-size: 18px;
  font-weight: 500;
}

</style>
