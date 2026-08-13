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
              <el-button
                v-if="entityInfo.storageMode !== 'SYSTEM'"
                type="primary"
                link
                size="small"
                @click="handleInitConfig(row)"
              >配置</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
            </div>
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
          <div class="field-help">用于流程节点绑定和发布版本识别，创建后不可修改。</div>
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

    <!-- 初始化配置弹窗 -->
    <el-dialog v-model="initConfigVisible" title="表单初始化配置" width="700px">
      <div class="init-config-wrapper">
        <el-radio-group v-model="initConfigType" size="small" @change="onInitConfigTypeChange">
          <el-radio-button value="">无</el-radio-button>
          <el-radio-button value="api">API</el-radio-button>
          <el-radio-button value="entity">实体</el-radio-button>
          <el-radio-button value="static">静态</el-radio-button>
          <el-radio-button value="custom">自定义</el-radio-button>
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
              <template #label>
                <JsonConfigLabel
                  label="Query 参数(JSON)"
                  help-key="entityForm.init.apiQuery"
                />
              </template>
              <el-input v-model="initConfigData.api.paramsText" type="textarea" :rows="3" placeholder='{"projectId":"{{routeQuery.projectId}}"}' style="width: 260px" />
            </el-form-item>
            <el-form-item label="请求体(JSON)">
              <template #label>
                <JsonConfigLabel
                  label="请求体(JSON)"
                  help-key="entityForm.init.apiBody"
                />
              </template>
              <el-input v-model="initConfigData.api.dataText" type="textarea" :rows="3" placeholder='{"key":"value"}' style="width: 260px" />
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="字段映射(JSON)">
              <template #label>
                <JsonConfigLabel
                  label="字段映射(JSON)"
                  help-key="entityForm.init.apiMapping"
                />
              </template>
              <el-input v-model="initConfigData.api.mappingText" type="textarea" :rows="3" placeholder='{"projectName":"name","projectCode":"code"}' style="width: 540px" />
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="initConfigType === 'entity'" class="init-config-section">
          <el-form inline size="small">
            <el-form-item label="目标实体">
              <EntityDefinitionPicker
                v-model="initConfigData.entity.entityCode"
                value-key="entityCode"
                title="选择初始化数据实体"
                placeholder="选择实体"
                :query="{ storageMode: 'DYNAMIC' }"
              />
            </el-form-item>
            <el-form-item label="取第几条">
              <el-input-number v-model="initConfigData.entity.index" :min="0" :max="100" style="width: 100px" />
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="过滤参数(JSON)">
              <template #label>
                <JsonConfigLabel
                  label="过滤参数(JSON)"
                  help-key="entityForm.init.entityFilters"
                />
              </template>
              <el-input v-model="initConfigData.entity.paramsText" type="textarea" :rows="3" placeholder='{"status":"APPROVED"}' style="width: 540px" />
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="字段映射(JSON)">
              <template #label>
                <JsonConfigLabel
                  label="字段映射(JSON)"
                  help-key="entityForm.init.entityMapping"
                />
              </template>
              <el-input v-model="initConfigData.entity.mappingText" type="textarea" :rows="3" placeholder='{"projectName":"name","projectCode":"code"}' style="width: 540px" />
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="initConfigType === 'static'" class="init-config-section">
          <el-form inline size="small">
            <el-form-item label="静态值(JSON)">
              <template #label>
                <JsonConfigLabel
                  label="静态值(JSON)"
                  help-key="entityForm.init.staticValues"
                />
              </template>
              <el-input v-model="initConfigData.staticText" type="textarea" :rows="4" placeholder='{"status":"DRAFT","reqType":"重大"}' style="width: 540px" />
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="initConfigType === 'custom'" class="init-config-section">
          <el-form inline size="small">
            <el-form-item label="初始化器名称">
              <el-select
                v-model="initConfigData.custom.name"
                placeholder="选择已注册初始化器"
                filterable
                clearable
                style="width: 320px"
              >
                <el-option
                  v-for="name in registeredInitializers"
                  :key="name"
                  :label="name"
                  :value="name"
                />
                <el-option
                  v-if="initConfigData.custom.name && !registeredInitializers.includes(initConfigData.custom.name)"
                  :label="`${initConfigData.custom.name}（当前未注册）`"
                  :value="initConfigData.custom.name"
                  disabled
                />
              </el-select>
              <div v-if="registeredInitializers.length === 0" class="field-help">
                当前环境没有已注册的自定义初始化器，请联系开发人员先完成扩展注册。
              </div>
            </el-form-item>
          </el-form>
          <el-form inline size="small">
            <el-form-item label="参数(JSON)">
              <template #label>
                <JsonConfigLabel
                  label="参数(JSON)"
                  help-key="entityForm.init.customParams"
                />
              </template>
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
import EntityDefinitionPicker from '@/components/EntityDefinitionPicker.vue'
import JsonConfigLabel from '@/components/JsonConfigLabel.vue'
import { getFormsByEntity, getFormById, createForm, updateForm, deleteForm, getFormFields, setDefaultForm, copyForm, updateFormInitConfig } from '@/api/entityForm'
import { parseJsonConfig } from '@/utils/jsonConfig'
import { getRegisteredFormInitializerNames } from '@/utils/formInitializerRegistry'
import { formatDateValue } from '@/shared/list-runtime'
import PageState from '@/components/PageState.vue'

const registeredInitializers = getRegisteredFormInitializerNames()

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
const initConfigVisible = ref(false)
const initConfigLoading = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const copyFormRef = ref(null)
const currentInitFormId = ref('')
const copySourceFormId = ref('')

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
      params: parseJsonConfig(initConfigData.api.paramsText, { fieldName: 'Query 参数' }),
      data: parseJsonConfig(initConfigData.api.dataText, { fieldName: '请求体' }),
      mapping: parseJsonConfig(initConfigData.api.mappingText, { fieldName: '字段映射' })
    }
  } else if (type === 'entity') {
    config.entity = {
      entityCode: initConfigData.entity.entityCode,
      index: initConfigData.entity.index,
      params: parseJsonConfig(initConfigData.entity.paramsText, { fieldName: '过滤参数' }),
      mapping: parseJsonConfig(initConfigData.entity.mappingText, { fieldName: '字段映射' })
    }
  } else if (type === 'static') {
    config.static = parseJsonConfig(initConfigData.staticText, { fieldName: '静态值' })
  } else if (type === 'custom') {
    config.custom = {
      name: initConfigData.custom.name,
      params: parseJsonConfig(initConfigData.custom.paramsText, { fieldName: '自定义参数' })
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
  if (initConfigType.value === 'custom'
      && !registeredInitializers.includes(initConfigData.custom.name)) {
    ElMessage.error('请选择当前环境已注册的自定义初始化器')
    return
  }
  initConfigLoading.value = true
  try {
    const initConfig = buildInitConfigFromUI()
    await updateFormInitConfig(currentInitFormId.value, initConfig || null)
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
