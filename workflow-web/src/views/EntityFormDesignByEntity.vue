<template>
  <div class="entity-form-design">
    <div class="design-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span class="title">表单设计 - {{ form.formName || '新建表单' }}</span>
      </div>
      <div class="header-right">
        <el-button @click="showPreview = true">
          <el-icon><View /></el-icon>预览
        </el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          <el-icon><Check /></el-icon>保存
        </el-button>
      </div>
    </div>

    <div class="design-body">
      <!-- 左侧：实体字段 -->
      <div class="field-panel">
        <div class="panel-title">实体字段</div>
        <div class="field-search">
          <el-input v-model="fieldSearch" placeholder="搜索字段" size="small" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <div class="field-list">
          <div
            v-for="field in filteredEntityFields"
            :key="field.id"
            class="field-item"
            :class="{ disabled: isFieldInForm(field) }"
            @click="addField(field)"
          >
            <el-icon><Document /></el-icon>
            <div class="field-info">
              <div class="field-name">{{ field.fieldName }}</div>
              <div class="field-code">{{ field.fieldCode }}</div>
            </div>
            <el-tag v-if="isFieldInForm(field)" type="info" size="small">已添加</el-tag>
            <el-tag v-else size="small">{{ field.fieldType }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 中间：表单画布 -->
      <div class="canvas-panel">
        <div class="panel-title">
          <span>表单设计（所见即所得）</span>
          <div class="layout-selector">
            <el-radio-group v-model="form.layoutType" size="small">
              <el-radio-button label="vertical">垂直</el-radio-button>
              <el-radio-button label="horizontal">水平</el-radio-button>
              <el-radio-button label="grid">网格</el-radio-button>
            </el-radio-group>
          </div>
        </div>
        
        <!-- 表单基本信息 -->
        <div class="form-basic-info">
          <el-form inline size="small">
            <el-form-item label="表单名称">
              <el-input v-model="form.formName" placeholder="请输入表单名称" style="width: 200px" />
            </el-form-item>
            <el-form-item label="表单标识">
              <el-input v-model="form.formKey" placeholder="表单标识" style="width: 150px" :disabled="isEdit" />
            </el-form-item>
          </el-form>
        </div>

        <!-- 表单画布 - 所见即所得 -->
        <div class="form-canvas-wrapper">
          <div class="form-canvas" :class="form.layoutType">
            <div v-if="formFields.length === 0" class="empty-tip">
              <el-empty description="点击左侧字段添加到表单">
                <template #image>
                  <el-icon :size="60" color="#dcdfe6"><DocumentAdd /></el-icon>
                </template>
              </el-empty>
            </div>
            
            <!-- 使用 el-form 包裹，与预览保持一致 -->
            <el-form v-else :label-width="formLabelWidth" :label-position="formLabelPosition" class="design-form">
              <div 
                v-for="(field, index) in formFields" 
                :key="field.id || index"
                class="form-field-wrapper"
                :class="{ 
                  active: selectedField?.id === field.id,
                  'grid-item': form.layoutType === 'grid'
                }"
                :style="getGridStyle(field)"
                @click="selectField(field)"
              >
                <!-- 顺序标记 -->
                <div class="field-order">{{ index + 1 }}</div>
                
                <!-- 字段内容 - 与预览完全一致的渲染 -->
                <div class="field-content">
                  <el-form-item 
                    :label="field.fieldLabel || field.fieldName"
                    :required="field.isRequired === 1"
                    class="design-form-item"
                  >
                    <FormFieldRenderer :field="field" :disabled="true" />
                  </el-form-item>
                </div>
                
                <!-- 操作按钮 -->
                <div class="field-actions" @click.stop>
                  <el-button-group size="small">
                    <el-button @click="moveUp(index)" :disabled="index === 0">
                      <el-icon><ArrowUp /></el-icon>
                    </el-button>
                    <el-button @click="moveDown(index)" :disabled="index === formFields.length - 1">
                      <el-icon><ArrowDown /></el-icon>
                    </el-button>
                    <el-button type="danger" @click="removeField(index)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </el-button-group>
                </div>
              </div>
            </el-form>
          </div>
        </div>
      </div>

      <!-- 右侧：属性配置 -->
      <div class="property-panel">
        <div class="panel-title">属性配置</div>
        
        <template v-if="selectedField">
          <!-- 添加联动配置按钮 -->
          <div class="linkage-config-header">
            <el-button type="primary" size="small" @click="showLinkageConfig = true">
              <el-icon><Connection /></el-icon> 字段联动配置
            </el-button>
          </div>
          
          <el-scrollbar height="calc(100vh - 180px)">
            <el-form label-width="90px" size="small" class="property-form">
              <el-form-item label="字段名称">
                <el-input v-model="selectedField.fieldName" disabled />
              </el-form-item>
              <el-form-item label="显示标签">
                <el-input v-model="selectedField.fieldLabel" />
              </el-form-item>
              <el-form-item label="组件类型">
                <el-select v-model="selectedField.componentType" style="width: 100%">
                  <el-option label="文本输入" value="input" />
                  <el-option label="多行文本" value="textarea" />
                  <el-option label="数字" value="number" />
                  <el-option label="日期" value="date" />
                  <el-option label="日期时间" value="datetime" />
                  <el-option label="下拉选择" value="select" />
                  <el-option label="单选" value="radio" />
                  <el-option label="多选" value="checkbox" />
                  <el-option label="开关" value="switch" />
                  <el-option label="文件" value="file" />
                  <el-option label="级联选择" value="cascader" />
                  <el-option label="子表单" value="SUB_FORM" />
                </el-select>
              </el-form-item>
              <el-form-item label="属性">
                <div class="checkbox-group">
                  <el-checkbox v-model="selectedField.isRequired" :true-label="1" :false-label="0">必填</el-checkbox>
                  <el-checkbox v-model="selectedField.isReadonly" :true-label="1" :false-label="0">只读</el-checkbox>
                  <el-checkbox v-model="selectedField.isHidden" :true-label="1" :false-label="0">隐藏</el-checkbox>
                </div>
              </el-form-item>
              <el-form-item label="默认值">
                <el-input v-model="selectedField.defaultValue" placeholder="默认值" />
              </el-form-item>
              <el-form-item label="占位提示">
                <el-input v-model="selectedField.placeholder" placeholder="提示文字" />
              </el-form-item>
              <el-form-item label="栅格宽度" v-if="form.layoutType === 'grid'">
                <el-slider v-model="selectedField.gridSpan" :min="1" :max="24" show-stops />
                <span class="slider-value">{{ selectedField.gridSpan }}/24</span>
              </el-form-item>
              
              <!-- 子表单特殊配置 -->
              <template v-if="selectedField.componentType === 'SUB_FORM'">
                <el-divider>子表单配置</el-divider>
                
                <el-form-item label="最少行数">
                  <el-input-number v-model="selectedField.minRows" :min="0" />
                </el-form-item>
                
                <el-form-item label="最多行数">
                  <el-input-number v-model="selectedField.maxRows" :min="1" />
                </el-form-item>
                
                <el-form-item label="显示汇总">
                  <el-switch v-model="selectedField.showSummary" />
                </el-form-item>
                
                <el-form-item label="子表字段">
                  <div class="sub-form-fields">
                    <div v-for="(subField, idx) in selectedField.subFields" :key="idx" class="sub-field-item">
                      <el-input v-model="subField.fieldName" placeholder="字段名" size="small" style="width: 100px" />
                      <el-select v-model="subField.fieldType" placeholder="类型" size="small" style="width: 90px">
                        <el-option label="文本" value="TEXT" />
                        <el-option label="数字" value="NUMBER" />
                        <el-option label="日期" value="DATE" />
                        <el-option label="下拉" value="SELECT" />
                      </el-select>
                      
                      <el-button type="danger" size="small" text @click="removeSubField(idx)">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                    
                    <el-button type="primary" size="small" text @click="addSubField">
                      <el-icon><Plus /></el-icon> 添加子字段
                    </el-button>
                  </div>
                </el-form-item>
              </template>
            </el-form>
          </el-scrollbar>
        </template>
        
        <div v-else class="empty-property">
          <el-empty description="点击字段进行配置">
            <template #image>
              <el-icon :size="48" color="#dcdfe6"><Edit /></el-icon>
            </template>
          </el-empty>
        </div>
      </div>
    </div>

    <!-- 预览弹窗 - 所见即所得 -->
    <el-dialog v-model="showPreview" title="表单预览" width="800px" destroy-on-close>
      <div class="preview-container">
        <FormPreview :form="previewForm" />
      </div>
    </el-dialog>
    
    <!-- 联动配置弹窗 -->
    <el-dialog 
      v-model="showLinkageConfig" 
      title="字段联动配置" 
      width="700px" 
      destroy-on-close
      :close-on-click-modal="false"
    >
      <LinkageConfigPanel
        v-if="selectedField"
        :field="selectedField"
        :all-fields="formFields"
        @save="handleSaveLinkage"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, View, Search, Document, ArrowUp, ArrowDown, Delete, Edit, DocumentAdd, Plus, Connection } from '@element-plus/icons-vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'
import FormPreview from '@/components/FormPreview.vue'
import LinkageConfigPanel from '@/components/LinkageConfigPanel.vue'
import { entityApi } from '@/api/entity'
import { getFormById, createForm, saveFormFields, getEntityFields, getFormFields } from '@/api/entityForm'

const route = useRoute()
const router = useRouter()
const formId = route.params.id
const entityId = route.query.entityId || ''

const isEdit = ref(!!formId)
const saving = ref(false)
const showPreview = ref(false)
const showLinkageConfig = ref(false)
const entityInfo = ref({})
const entityFields = ref([])
const formFields = ref([])
const selectedField = ref(null)
const fieldSearch = ref('')

const form = ref({
  id: formId,
  entityId: entityId,
  formName: '',
  formKey: '',
  layoutType: 'vertical',
  status: 1
})

// 预览数据
const previewForm = computed(() => {
  const sortedFields = [...formFields.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  return {
    ...form.value,
    fields: sortedFields
  }
})

// 表单标签宽度 - 与预览保持一致
const formLabelWidth = computed(() => {
  switch (form.value.layoutType) {
    case 'horizontal':
      return '120px'
    case 'vertical':
      return 'auto'
    default:
      return '120px'
  }
})

// 表单标签位置 - 与预览保持一致
const formLabelPosition = computed(() => {
  switch (form.value.layoutType) {
    case 'horizontal':
      return 'right'
    case 'vertical':
      return 'top'
    default:
      return 'right'
  }
})

// 过滤后的字段
const filteredEntityFields = computed(() => {
  if (!fieldSearch.value) return entityFields.value
  return entityFields.value.filter(f => 
    f.fieldName.includes(fieldSearch.value) || 
    f.fieldCode.includes(fieldSearch.value)
  )
})

// 检查字段是否已在表单中
function isFieldInForm(entityField) {
  return formFields.value.some(f => f.fieldId === entityField.id)
}

// 获取栅格样式
function getGridStyle(field) {
  if (form.value.layoutType === 'grid') {
    const span = field.gridSpan || 24
    return {
      width: `${(span / 24) * 100}%`,
      flex: `0 0 ${(span / 24) * 100}%`
    }
  }
  return {}
}

// 加载实体信息
async function loadEntityInfo() {
  if (!entityId) return
  try {
    const data = await entityApi.getById(entityId)
    entityInfo.value = data
    if (!isEdit.value) {
      form.value.formName = data.entityName + '表单'
      form.value.formKey = data.entityCode + '_form'
    }
  } catch (e) {
    console.error('加载实体信息失败:', e)
  }
}

// 加载实体字段
async function loadEntityFields() {
  const eid = entityId || form.value.entityId
  if (!eid) return
  
  try {
    entityFields.value = await getEntityFields(eid)
  } catch (e) {
    console.error('加载实体字段失败:', e)
  }
}

// 加载表单信息
async function loadFormInfo() {
  if (!isEdit.value) return
  
  try {
    const data = await getFormById(formId)
    form.value = { ...form.value, ...data }
    if (data.entityId && !entityId) {
      form.value.entityId = data.entityId
    }
  } catch (e) {
    console.error('加载表单信息失败:', e)
  }
}

// 加载表单字段
async function loadFormFields() {
  if (!isEdit.value) return
  
  try {
    formFields.value = await getFormFields(formId)
  } catch (e) {
    console.error('加载表单字段失败:', e)
  }
}

// 添加字段到表单
function addField(entityField) {
  // 检查是否已存在
  if (isFieldInForm(entityField)) {
    ElMessage.warning('该字段已添加到表单')
    return
  }
  
  const newField = {
    formId: formId,
    fieldId: entityField.id,
    fieldName: entityField.fieldName,
    fieldLabel: entityField.fieldName,
    fieldType: entityField.fieldType,
    componentType: getDefaultComponentType(entityField.fieldType),
    isRequired: entityField.isRequired ? 1 : 0,
    isReadonly: 0,
    isHidden: 0,
    gridSpan: 24,
    sortOrder: formFields.value.length
  }
  
  formFields.value.push(newField)
  selectedField.value = newField
  ElMessage.success('字段已添加')
}

// 获取默认组件类型
function getDefaultComponentType(fieldType) {
  const typeMap = {
    'STRING': 'input',
    'TEXT': 'textarea',
    'INTEGER': 'number',
    'LONG': 'number',
    'DOUBLE': 'number',
    'DECIMAL': 'number',
    'DATE': 'date',
    'DATETIME': 'datetime',
    'BOOLEAN': 'switch'
  }
  return typeMap[fieldType] || 'input'
}

// 选择字段
function selectField(field) {
  selectedField.value = field
}

// 移除字段
function removeField(index) {
  formFields.value.splice(index, 1)
  if (selectedField.value && !formFields.value.includes(selectedField.value)) {
    selectedField.value = null
  }
}

// 上移
function moveUp(index) {
  if (index === 0) return
  const temp = formFields.value[index]
  formFields.value[index] = formFields.value[index - 1]
  formFields.value[index - 1] = temp
  formFields.value.forEach((f, i) => f.sortOrder = i)
}

// 下移
function moveDown(index) {
  if (index === formFields.value.length - 1) return
  const temp = formFields.value[index]
  formFields.value[index] = formFields.value[index + 1]
  formFields.value[index + 1] = temp
  formFields.value.forEach((f, i) => f.sortOrder = i)
}

// 保存联动配置
function handleSaveLinkage(linkageRules) {
  if (selectedField.value) {
    selectedField.value.linkageRules = linkageRules
    // 将联动规则展开到字段根属性，便于引擎直接读取
    Object.keys(linkageRules).forEach(key => {
      selectedField.value[key] = linkageRules[key]
    })
    // 将联动规则保存到扩展属性中（持久化到数据库）
    selectedField.value.componentProps = JSON.stringify({
      ...parseComponentProps(selectedField.value.componentProps),
      linkageRules
    })
    ElMessage.success('联动配置已保存到字段')
    showLinkageConfig.value = false
  }
}

// 解析 componentProps
function parseComponentProps(propsStr) {
  if (!propsStr) return {}
  try {
    return JSON.parse(propsStr)
  } catch (e) {
    return {}
  }
}

// 添加子表单字段
function addSubField() {
  if (!selectedField.value) return
  if (!selectedField.value.subFields) {
    selectedField.value.subFields = []
  }
  selectedField.value.subFields.push({
    fieldName: '',
    fieldType: 'TEXT',
    isRequired: false,
    isEditable: true
  })
}

// 移除子表单字段
function removeSubField(index) {
  if (selectedField.value && selectedField.value.subFields) {
    selectedField.value.subFields.splice(index, 1)
  }
}

// 保存表单
async function handleSave() {
  if (!form.value.formName) {
    ElMessage.warning('请输入表单名称')
    return
  }
  if (!form.value.formKey) {
    ElMessage.warning('请输入表单标识')
    return
  }
  
  const eid = entityId || form.value.entityId
  if (!form.value.entityId && eid) {
    form.value.entityId = eid
  }
  
  if (formFields.value.length === 0) {
    ElMessage.warning('请至少添加一个字段')
    return
  }
  
  saving.value = true
  try {
    // 1. 创建/更新表单
    const formData = await createForm(form.value)
    
    const newFormId = formData.id
    
    // 2. 保存表单字段
    const fieldsToSave = formFields.value.map((f, index) => ({
      ...f,
      formId: newFormId,
      sortOrder: index
    }))
    
    await saveFormFields(newFormId, fieldsToSave)
    
    ElMessage.success('表单保存成功')
    const backEntityId = form.value.entityId || entityId
    if (backEntityId) {
      router.push(`/entity-form/list-by-entity/${backEntityId}`)
    } else {
      router.back()
    }
  } catch (e) {
    console.error('保存失败:', e)
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadEntityInfo()
  await loadFormInfo()
  await loadEntityFields()
  await loadFormFields()
})
</script>

<style scoped>
.entity-form-design {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}

.design-header {
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #dcdfe6;
  background-color: #fff;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.title {
  font-size: 16px;
  font-weight: 500;
}

.design-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧字段面板 */
.field-panel {
  width: 260px;
  border-right: 1px solid #dcdfe6;
  background-color: #fff;
  display: flex;
  flex-direction: column;
}

.panel-title {
  height: 44px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  font-weight: 500;
  font-size: 14px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #f5f7fa;
}

.field-search {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.field-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 6px;
  background-color: #fff;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #e4e7ed;
}

.field-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.field-item.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background-color: #f5f7fa;
}

.field-item.disabled:hover {
  border-color: #e4e7ed;
  box-shadow: none;
}

.field-info {
  flex: 1;
  min-width: 0;
}

.field-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.field-code {
  font-size: 11px;
  color: #909399;
}

/* 中间画布 */
.canvas-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.layout-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-basic-info {
  padding: 12px 20px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.form-canvas-wrapper {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background-color: #f0f2f5;
}

.form-canvas {
  min-height: 400px;
  background-color: #fff;
  border-radius: 4px;
  padding: 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

/* 设计表单样式 */
.design-form {
  display: flex;
  flex-wrap: wrap;
}

.form-field-wrapper {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  margin-bottom: 8px;
  background-color: #fff;
  border: 2px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.form-field-wrapper:hover {
  border-color: #c0c4cc;
}

.form-field-wrapper.active {
  border-color: #409eff;
  background-color: #f5f7fa;
}

/* 垂直布局 */
.form-canvas.vertical .form-field-wrapper {
  width: 100%;
}

/* 水平布局 */
.form-canvas.horizontal .design-form {
  gap: 20px;
}

.form-canvas.horizontal .form-field-wrapper {
  width: calc(50% - 10px);
}

/* 网格布局 */
.form-canvas.grid .design-form {
  gap: 0;
}

.form-canvas.grid .form-field-wrapper.grid-item {
  padding: 8px;
  margin-bottom: 0;
}

.field-order {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #409eff;
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  margin-right: 12px;
  flex-shrink: 0;
  margin-top: 8px;
}

.field-content {
  flex: 1;
  min-width: 0;
}

.design-form-item {
  margin-bottom: 0 !important;
}

.design-form-item :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}

.field-actions {
  margin-left: 12px;
  opacity: 0;
  transition: opacity 0.2s;
  padding-top: 4px;
}

.form-field-wrapper:hover .field-actions,
.form-field-wrapper.active .field-actions {
  opacity: 1;
}

.empty-tip {
  padding: 80px 0;
}

/* 右侧属性面板 */
.property-panel {
  width: 280px;
  border-left: 1px solid #dcdfe6;
  background-color: #fff;
  display: flex;
  flex-direction: column;
}

.property-form {
  padding: 16px;
}

.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slider-value {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

.empty-property {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 预览容器 */
.preview-container {
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}
</style>
