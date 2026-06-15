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
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDesign(row)">设计</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button 
              v-if="!row.isDefault" 
              type="warning" 
              link 
              size="small" 
              @click="handleSetDefault(row)"
            >
              设为默认
            </el-button>
            <el-button v-else type="info" link size="small" disabled>已是默认</el-button>
            <el-button type="info" link size="small" @click="handleCopy(row)">复制</el-button>
            <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
            <el-button type="primary" link size="small" @click="handleInitConfig(row)">初始化</el-button>
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
          <el-input v-model="form.formKey" placeholder="请输入表单标识" />
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
    <el-dialog v-model="previewVisible" :title="previewForm ? `表单预览 | ${previewForm.formName}` : '表单预览'" width="800px">
      <FormPreviewLinkage v-if="previewForm" :form="previewForm" :showHeader="false" />
    </el-dialog>

    <!-- 初始化配置弹窗 -->
    <el-dialog v-model="initConfigVisible" title="表单初始化配置" width="700px">
      <div class="init-config-wrapper">
        <el-radio-group v-model="initConfigType" size="small" @change="onInitConfigTypeChange">
          <el-radio-button label="">无</el-radio-button>
          <el-radio-button label="api">API</el-radio-button>
          <el-radio-button label="entity">实体</el-radio-button>
          <el-radio-button label="static">静态</el-radio-button>
          <el-radio-button label="custom">自定义</el-radio-button>
        </el-radio-group>

        <div v-if="initConfigType === 'api'" class="init-config-section">
          <el-form inline size="small">
            <el-form-item label="请求地址">
              <el-input v-model="initConfigData.api.url" placeholder="/api/xxx 或完整 URL" style="width: 260px" />
            </el-form-item>
            <el-form-item label="请求方式">
              <el-select v-model="initConfigData.api.method" style="width: 100px">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
              </el-select>
            </el-form-item>
            <el-form-item label="响应路径">
              <el-input v-model="initConfigData.api.responsePath" placeholder="如 data，留空取根" style="width: 140px" />
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="Query 参数(JSON)">
              <el-input v-model="initConfigData.api.paramsText" type="textarea" :rows="3" placeholder='{"projectId":"{{routeQuery.projectId}}"}' style="width: 260px" />
            </el-form-item>
            <el-form-item label="请求体(JSON)">
              <el-input v-model="initConfigData.api.dataText" type="textarea" :rows="3" placeholder='{"key":"value"}' style="width: 260px" />
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="字段映射(JSON)">
              <el-input v-model="initConfigData.api.mappingText" type="textarea" :rows="3" placeholder='{"name":"projectName","code":"projectCode"}' style="width: 540px" />
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="initConfigType === 'entity'" class="init-config-section">
          <el-form inline size="small">
            <el-form-item label="目标实体">
              <el-select v-model="initConfigData.entity.entityCode" placeholder="选择实体" filterable style="width: 200px">
                <el-option
                  v-for="item in allEntityList"
                  :key="item.entityCode"
                  :label="item.entityName"
                  :value="item.entityCode"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="取第几条">
              <el-input-number v-model="initConfigData.entity.index" :min="0" :max="100" style="width: 100px" />
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="过滤参数(JSON)">
              <el-input v-model="initConfigData.entity.paramsText" type="textarea" :rows="3" placeholder='{"status":"APPROVED"}' style="width: 540px" />
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="字段映射(JSON)">
              <el-input v-model="initConfigData.entity.mappingText" type="textarea" :rows="3" placeholder='{"name":"name","code":"code"}' style="width: 540px" />
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="initConfigType === 'static'" class="init-config-section">
          <el-form inline size="small">
            <el-form-item label="静态值(JSON)">
              <el-input v-model="initConfigData.staticText" type="textarea" :rows="4" placeholder='{"status":"DRAFT","reqType":"重大"}' style="width: 540px" />
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="initConfigType === 'custom'" class="init-config-section">
          <el-form inline size="small">
            <el-form-item label="初始化器名称">
              <el-input v-model="initConfigData.custom.name" placeholder="已注册的自定义初始化器名" style="width: 260px" />
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="参数(JSON)">
              <el-input v-model="initConfigData.custom.paramsText" type="textarea" :rows="3" placeholder='{"key":"value"}' style="width: 540px" />
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <el-button @click="initConfigVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveInitConfig" :loading="initConfigLoading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import { entityApi } from '@/api/entity'
import { getFormsByEntity, getFormById, createForm, updateForm, deleteForm, getFormFields, setDefaultForm, copyForm, updateFormInitConfig } from '@/api/entityForm'

const allEntityList = ref([])

const route = useRoute()
const router = useRouter()
const entityId = route.params.entityId

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const initConfigVisible = ref(false)
const initConfigLoading = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const currentInitFormId = ref('')

const entityInfo = ref({})
const formList = ref([])
const previewForm = ref(null)

const initConfigType = ref('')
const initConfigData = reactive({
  api: { url: '', method: 'GET', responsePath: '', paramsText: '', dataText: '', mappingText: '' },
  entity: { entityCode: '', index: 0, paramsText: '', mappingText: '' },
  staticText: '',
  custom: { name: '', paramsText: '' }
})

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
    entityInfo.value = await entityApi.getById(entityId)
  } catch (e) {
    console.error('加载实体信息失败:', e)
  }
}

// 加载表单列表
async function loadForms() {
  loading.value = true
  try {
    formList.value = await getFormsByEntity(entityId)
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

async function handleCopy(row) {
  try {
    await copyForm(row.id)
    ElMessage.success(`表单 "${row.formName}" 复制成功`)
    loadForms()
  } catch (e) {
    console.error('复制失败:', e)
    ElMessage.error(e.message || '复制失败')
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
  loadAllEntities()
})

async function loadAllEntities() {
  try {
    const res = await entityApi.getList()
    allEntityList.value = res || []
  } catch (e) {
    console.error('加载实体列表失败:', e)
    allEntityList.value = []
  }
}

function safeJsonParse(text) {
  if (!text) return {}
  try {
    return JSON.parse(text)
  } catch (e) {
    return {}
  }
}

function safeJsonStringify(obj, space = 0) {
  if (obj == null) return ''
  try {
    return JSON.stringify(obj, null, space)
  } catch (e) {
    return ''
  }
}

function parseInitConfigToUI(config) {
  if (typeof config === 'string' && config) {
    try {
      config = JSON.parse(config)
    } catch (e) {
      config = null
    }
  }
  if (!config || !config.type) {
    initConfigType.value = ''
    return
  }
  initConfigType.value = config.type
  if (config.type === 'api' && config.api) {
    initConfigData.api.url = config.api.url || ''
    initConfigData.api.method = config.api.method || 'GET'
    initConfigData.api.responsePath = config.api.responsePath || ''
    initConfigData.api.paramsText = safeJsonStringify(config.api.params, 2)
    initConfigData.api.dataText = safeJsonStringify(config.api.data, 2)
    initConfigData.api.mappingText = safeJsonStringify(config.api.mapping, 2)
  } else if (config.type === 'entity' && config.entity) {
    initConfigData.entity.entityCode = config.entity.entityCode || ''
    initConfigData.entity.index = config.entity.index ?? 0
    initConfigData.entity.paramsText = safeJsonStringify(config.entity.params, 2)
    initConfigData.entity.mappingText = safeJsonStringify(config.entity.mapping, 2)
  } else if (config.type === 'static') {
    initConfigData.staticText = safeJsonStringify(config.static, 2)
  } else if (config.type === 'custom' && config.custom) {
    initConfigData.custom.name = config.custom.name || ''
    initConfigData.custom.paramsText = safeJsonStringify(config.custom.params, 2)
  }
}

function buildInitConfigFromUI() {
  const type = initConfigType.value
  if (!type) return null
  const config = { type }
  if (type === 'api') {
    config.api = {
      url: initConfigData.api.url,
      method: initConfigData.api.method || 'GET',
      responsePath: initConfigData.api.responsePath,
      params: safeJsonParse(initConfigData.api.paramsText),
      data: safeJsonParse(initConfigData.api.dataText),
      mapping: safeJsonParse(initConfigData.api.mappingText)
    }
  } else if (type === 'entity') {
    config.entity = {
      entityCode: initConfigData.entity.entityCode,
      index: initConfigData.entity.index,
      params: safeJsonParse(initConfigData.entity.paramsText),
      mapping: safeJsonParse(initConfigData.entity.mappingText)
    }
  } else if (type === 'static') {
    config.static = safeJsonParse(initConfigData.staticText)
  } else if (type === 'custom') {
    config.custom = {
      name: initConfigData.custom.name,
      params: safeJsonParse(initConfigData.custom.paramsText)
    }
  }
  return config
}

function onInitConfigTypeChange() {
  // 切换类型时清空其他类型的数据，保留当前类型的默认值
}

function handleInitConfig(row) {
  currentInitFormId.value = row.id
  parseInitConfigToUI(row.initConfig)
  initConfigVisible.value = true
}

async function handleSaveInitConfig() {
  if (!currentInitFormId.value) return
  initConfigLoading.value = true
  try {
    const initConfig = buildInitConfigFromUI()
    await updateFormInitConfig(currentInitFormId.value, initConfig ? JSON.stringify(initConfig) : null)
    ElMessage.success('初始化配置保存成功')
    initConfigVisible.value = false
    loadForms()
  } catch (e) {
    console.error('保存初始化配置失败:', e)
    ElMessage.error(e.message || '保存失败')
  } finally {
    initConfigLoading.value = false
  }
}
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

.init-config-wrapper {
  padding: 10px 0;
}

.init-config-wrapper .el-radio-group {
  margin-bottom: 20px;
}

.init-config-section {
  padding: 10px 0;
}
</style>
