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
            <div class="field-tags">
              <el-tag v-if="isFieldInForm(field)" type="info" size="small" class="added-tag">已添加</el-tag>
              <el-tag size="small" class="type-tag">{{ field.fieldType }}</el-tag>
            </div>
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
            <el-button type="primary" size="small" style="margin-left: 12px" @click="addSection">
              <el-icon><Plus /></el-icon>添加节
            </el-button>
          </div>
        </div>
        
        <!-- 表单基本信息 -->
        <div class="form-basic-info">
          <el-form inline size="small">
            <el-form-item label="表单名称">
              <el-input v-model="form.formName" placeholder="请输入表单名称" style="width: 200px" />
            </el-form-item>
            <el-form-item label="表单标识">
              <el-input v-model="form.formKey" placeholder="表单标识" style="width: 150px" />
            </el-form-item>
            <el-form-item label="自定义组件">
              <el-input
                v-model="form.customComponent"
                placeholder="输入已注册的自定义表单组件名"
                style="width: 260px"
                clearable
              />
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
                  'grid-item': form.layoutType === 'grid',
                  'tab-subform': isTabSubForm(field),
                  'section-field-wrapper': isSectionField(field)
                }"
                :style="isSectionField(field) ? { width: '100%' } : getGridStyle(field)"
                @click="selectField(field)"
              >
                <!-- 顺序标记 -->
                <div class="field-order">{{ index + 1 }}</div>
                
                <!-- 字段内容 - 与预览完全一致的渲染 -->
                <div class="field-content">
                  <template v-if="isSectionField(field)">
                    <SectionField :field="field" />
                  </template>
                  <el-form-item 
                    v-else
                    :label="field.fieldLabel || field.fieldName"
                    :required="field.isRequired === 1"
                    class="design-form-item"
                  >
                    <el-tag v-if="isTabSubForm(field)" type="warning" size="small" class="tab-badge">Tab 子表单</el-tag>
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

              <!-- Tab 子表单预览 -->
              <div v-if="formFields.filter(f => isTabSubForm(f)).length > 0" class="tab-subforms-preview" style="width: 100%; margin-top: 16px;">
                <el-tabs v-model="activeDesignTab" type="border-card">
                  <el-tab-pane
                    v-for="field in formFields.filter(f => isTabSubForm(f))"
                    :key="field.id || field.fieldId"
                    :label="field.fieldLabel || field.fieldName"
                    :name="field.fieldCode || field.fieldId || field.id"
                  >
                    <div 
                      @click="selectField(field)" 
                      class="tab-form-field-wrapper"
                      :class="{ active: selectedField?.id === field.id }"
                    >
                      <FormFieldRenderer :field="field" :disabled="true" />
                    </div>
                  </el-tab-pane>
                </el-tabs>
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
              <el-form-item :label="isSelectedSection ? '节标题' : '字段名称'">
                <el-input v-model="selectedField.fieldName" :disabled="!isSelectedSection" />
              </el-form-item>
              <el-form-item label="显示标签">
                <el-input v-model="selectedField.fieldLabel" />
              </el-form-item>
              <template v-if="!isSelectedSection">
                <el-form-item label="组件类型">
                  <el-select v-model="selectedField.componentType" style="width: 100%">
                    <el-option label="文本输入" value="input" />
                    <el-option label="多行文本" value="textarea" />
                    <el-option label="数字" value="number" />
                    <el-option label="日期" value="date" />
                    <el-option label="日期时间" value="datetime" />
                    <el-option label="下拉选择（单选）" value="select" />
                    <el-option label="下拉选择（多选）" value="select_multiple" />
                    <el-option label="单选框" value="radio" />
                    <el-option label="复选框" value="checkbox" />
                    <el-option label="开关" value="switch" />
                    <el-option label="文件" value="file" />
                    <el-option label="级联选择" value="cascader" />
                    <el-option label="实体引用（单选）" value="reference" />
                    <el-option label="实体引用（多选）" value="multi_reference" />
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
              </template>
              <el-form-item label="栅格宽度" v-if="form.layoutType === 'grid'">
                <el-slider v-model="selectedField.gridSpan" :min="1" :max="24" show-stops />
                <span class="slider-value">{{ selectedField.gridSpan }}/24</span>
              </el-form-item>
              
              <!-- 子表单特殊配置 -->
              <template v-if="(selectedField.componentType || '').toUpperCase() === 'SUB_FORM'">
                <el-divider>子表单</el-divider>

                <div class="relation-summary">
                  <div>
                    <span>子实体</span>
                    <strong>{{ getEntityNameById(selectedField.childEntityId || selectedField.refEntityId) || '-' }}</strong>
                  </div>
                  <div>
                    <span>关系</span>
                    <strong>{{ selectedField.relationType === 'ONE_TO_ONE' ? '一对一' : '一对多' }}</strong>
                  </div>
                  <div>
                    <span>外键</span>
                    <strong>{{ selectedField.childRefFieldCode || selectedField.refFieldCode || '-' }}</strong>
                  </div>
                </div>

                <el-form-item label="显示">
                  <el-radio-group v-model="selectedField.displayMode">
                    <el-radio-button label="embedded">嵌入</el-radio-button>
                    <el-radio-button label="tab">页签</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <el-form-item label="布局">
                  <el-radio-group v-model="selectedField.layout">
                    <el-radio-button label="form">分行</el-radio-button>
                    <el-radio-button label="table">表格</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <el-form-item label="子表表单">
                  <el-select
                    v-model="selectedField.refFormId"
                    placeholder="默认表单"
                    clearable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="fm in formListByEntity"
                      :key="fm.id"
                      :label="fm.formName"
                      :value="fm.id"
                    />
                  </el-select>
                </el-form-item>
              </template>

              <!-- 实体引用字段配置 -->
              <template v-if="(selectedField.componentType || '').toUpperCase() === 'REFERENCE' || (selectedField.componentType || '').toUpperCase() === 'MULTI_REFERENCE'">
                <el-divider>实体引用配置</el-divider>
                <el-form-item label="引用类型">
                  <el-select v-model="selectedField.refEntityType" :disabled="!!selectedField.fieldId" placeholder="选择引用类型" style="width: 100%">
                    <el-option label="用户自定义实体" value="CUSTOM" />
                    <el-option label="系统用户" value="USER" />
                    <el-option label="系统部门" value="DEPT" />
                    <el-option label="系统角色" value="ROLE" />
                    <el-option label="系统用户组" value="GROUP" />
                  </el-select>
                </el-form-item>
                <el-form-item label="关联实体" v-if="(selectedField.refEntityType || '').toUpperCase() === 'CUSTOM'">
                  <el-select
                    v-model="selectedField.refEntityId"
                    :disabled="!!selectedField.fieldId"
                    placeholder="选择关联实体"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="ent in entityList"
                      :key="ent.id"
                      :label="ent.entityName"
                      :value="ent.id"
                    />
                  </el-select>
                  <div v-if="selectedField.refEntityId" class="form-tip">
                    当前关联：{{ getEntityNameById(selectedField.refEntityId) }}
                  </div>
                </el-form-item>
                <el-form-item label="数据接口">
                  <el-input v-model="selectedField.apiUrl" placeholder="可选：定制数据查询接口URL" />
                  <div class="form-tip">填写接口地址可定制返回数据范围，为空则使用默认查询</div>
                </el-form-item>
              </template>

              <!-- 字段事件配置 -->
              <template v-if="selectedField">
                <el-divider>事件配置</el-divider>
                <el-form-item>
                  <el-button type="primary" text @click="openEventConfig">
                    <el-icon><Edit /></el-icon>
                    配置字段事件
                  </el-button>
                  <el-tag v-if="hasEventConfig" type="success" size="small" style="margin-left: 8px">已配置</el-tag>
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
        <FormPreviewLinkage :form="previewForm" />
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
        :all-fields="entityFields.filter(f => !f.isSystem)"
        @save="handleSaveLinkage"
      />
    </el-dialog>

    <!-- 事件配置弹窗 -->
    <EventConfigPanel
      v-model:visible="showEventConfig"
      :model-value="currentEventValues"
      @save="handleSaveEvent"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Check, View, Search, Document, ArrowUp, ArrowDown, Delete, Edit, DocumentAdd, Plus, Connection } from '@element-plus/icons-vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'
import SectionField from '@/components/form-fields/components/SectionField.vue'
import FormPreviewLinkage from '@/components/FormPreviewLinkage.vue'
import LinkageConfigPanel from '@/components/LinkageConfigPanel.vue'
import EventConfigPanel from '@/components/EventConfigPanel.vue'
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
const showEventConfig = ref(false)
const currentEventField = ref(null)
const activeDesignTab = ref('')

// 判断是否为 Tab 模式的子表单
function isTabSubForm(field) {
  const type = (field.componentType || field.fieldType || '').toUpperCase()
  if (!['SUB_FORM', 'SUB_FORM_LIST'].includes(type)) return false
  if (field.displayMode === 'tab') return true
  if (field.componentProps) {
    try {
      const compProps = typeof field.componentProps === 'string'
        ? JSON.parse(field.componentProps)
        : field.componentProps
      return compProps.subFormConfig?.displayMode === 'tab'
    } catch (e) {}
  }
  return false
}

const entityInfo = ref({})
const entityFields = ref([])
const formFields = ref([])
const selectedField = ref(null)
const fieldSearch = ref('')
const entityList = ref([])
const formListByEntity = ref([])

// Tab 模式的子表单字段（必须在 formFields 定义之后）
const tabSubFormFields = ref([])
watch(formFields, (fields) => {
  tabSubFormFields.value = fields.filter(f => isTabSubForm(f))
}, { deep: true, immediate: true })

const form = ref({
  id: formId,
  entityId: entityId,
  formName: '',
  formKey: '',
  layoutType: 'vertical',
  status: 1,
  initConfig: null
})

// 当前选中字段的事件配置值
const currentEventValues = computed(() => {
  if (!currentEventField.value) return {}
  const result = {}
  // 读取所有以 eventOn 开头的根属性
  Object.keys(currentEventField.value).forEach(key => {
    if (key.startsWith('eventOn')) {
      const eventName = 'on' + key.slice(7)
      result[eventName] = currentEventField.value[key] || ''
    }
  })
  // 再从 componentProps 解析补充
  if (currentEventField.value.componentProps) {
    try {
      const compProps = JSON.parse(currentEventField.value.componentProps)
      if (compProps.events) {
        Object.keys(compProps.events).forEach(key => {
          if (!result[key]) {
            result[key] = compProps.events[key] || ''
          }
        })
      }
    } catch (e) {}
  }
  return result
})

// 当前选中字段是否已配置事件
const hasEventConfig = computed(() => {
  if (!selectedField.value) return false
  return Object.keys(selectedField.value).some(key => key.startsWith('eventOn') && selectedField.value[key])
})

// 预览数据
const previewForm = computed(() => {
  enrichFieldCodes()
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

// 当前选中的是否为节
const isSelectedSection = computed(() => isSectionField(selectedField.value))

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

// 根据实体ID获取实体名称
function getEntityNameById(id) {
  if (!id) return '-'
  const ent = entityList.value.find(e => String(e.id) === String(id))
  return ent?.entityName || String(id)
}

// 加载所有实体列表（用于子表单引用选择）
async function loadEntityList() {
  try {
    const res = await entityApi.getList()
    // request 拦截器已提取 response.data.data，res 直接是数组
    const list = Array.isArray(res) ? res : (res.data || [])
    // 统一将 id 转为字符串，避免 el-select value 类型不匹配显示 raw value
    entityList.value = list.map(ent => ({ ...ent, id: String(ent.id) }))
  } catch (e) {
    console.error('加载实体列表失败:', e)
  }
}

// 加载指定实体的表单列表（排除当前正在编辑的表单）
async function loadFormListByEntity(targetEntityId) {
  if (!targetEntityId) {
    formListByEntity.value = []
    return
  }
  try {
    const res = await entityApi.getEntityForms(targetEntityId)
    // 兼容直接返回数组或 { data: [...] } 两种格式
    const list = Array.isArray(res) ? res : (Array.isArray(res.data) ? res.data : [])
    // 排除当前正在编辑的表单（避免循环引用）
    formListByEntity.value = list.filter(fm => String(fm.id) !== String(formId))
  } catch (e) {
    console.error('加载表单列表失败:', e)
    formListByEntity.value = []
  }
}

// 检查字段的 componentProps 中是否已有选项
function hasOptionsInComponentProps(field) {
  if (!field.componentProps) return false
  try {
    const compProps = typeof field.componentProps === 'string'
      ? JSON.parse(field.componentProps)
      : field.componentProps
    return compProps && compProps.options && compProps.options.length > 0
  } catch (e) {
    return false
  }
}

// 给表单字段补充 fieldCode 和选项数据
function enrichFieldCodes() {
  if (entityFields.value.length === 0 || formFields.value.length === 0) return
  formFields.value.forEach(field => {
    if (!field.fieldCode && field.fieldId) {
      // 使用字符串比较，避免数字/字符串类型不匹配
      const fieldIdStr = String(field.fieldId)
      const entityField = entityFields.value.find(ef => String(ef.id) === fieldIdStr)
      if (entityField && entityField.fieldCode) {
        field.fieldCode = entityField.fieldCode
      }
    }
    // 补充选项数据（用于选项联动等）
    if (!field.optionsJson && !field.options && !hasOptionsInComponentProps(field) && field.fieldId) {
      const fieldIdStr = String(field.fieldId)
      const entityField = entityFields.value.find(ef => String(ef.id) === fieldIdStr)
      if (entityField) {
        if (entityField.optionsJson) field.optionsJson = entityField.optionsJson
        if (entityField.componentProps) field.componentProps = entityField.componentProps
        if (entityField.options) field.options = entityField.options
      }
    }
  })
}

// 加载实体字段
async function loadEntityFields() {
  const eid = entityId || form.value.entityId
  if (!eid) return

  try {
    entityFields.value = await getEntityFields(eid)
    enrichFieldCodes()
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

// 从 componentProps 恢复子表单和事件配置
function restoreFieldConfig(field) {
  if (!field.componentProps) return
  try {
    const compProps = typeof field.componentProps === 'string'
      ? JSON.parse(field.componentProps)
      : field.componentProps

    // 恢复子表单配置
    if (compProps.subFormConfig) {
      field.displayMode = compProps.subFormConfig.displayMode || 'embedded'
      field.layout = compProps.subFormConfig.layout || 'form'
      field.refEntityId = compProps.subFormConfig.refEntityId || field.childEntityId || field.refEntityId || ''
      field.refFormId = compProps.subFormConfig.refFormId || ''
      field.repeatable = field.relationType !== 'ONE_TO_ONE'
      field.childEntityId = field.childEntityId || field.refEntityId || ''
      field.childRefFieldCode = field.childRefFieldCode || field.refFieldCode || ''
    }
    // 恢复实体引用配置
    if (compProps.refConfig) {
      field.refEntityType = compProps.refConfig.refEntityType || ''
      field.refEntityId = String(compProps.refConfig.refEntityId || '')
      field.apiUrl = compProps.refConfig.apiUrl || ''
    }

    // 恢复事件配置
    if (compProps.events) {
      Object.keys(compProps.events).forEach(key => {
        const rootKey = 'eventOn' + key.charAt(2).toUpperCase() + key.slice(3)
        field[rootKey] = compProps.events[key] || ''
      })
    }
  } catch (e) {
    // 忽略解析错误
  }
}

// 将子表单和事件配置序列化到 componentProps
function serializeFieldConfig(field) {
  try {
    const compProps = field.componentProps
      ? (typeof field.componentProps === 'string' ? JSON.parse(field.componentProps) : field.componentProps)
      : {}

    // 序列化子表单配置
    if ((field.componentType || '').toUpperCase() === 'SUB_FORM') {
      compProps.subFormConfig = {
        displayMode: field.displayMode || 'embedded',
        layout: field.layout || 'form',
        refEntityId: field.childEntityId || field.refEntityId || '',
        refFormId: field.refFormId || '',
        repeatable: field.relationType !== 'ONE_TO_ONE',
        relationType: field.relationType || 'ONE_TO_MANY',
        childRefFieldCode: field.childRefFieldCode || field.refFieldCode || ''
      }
      delete compProps.fields
      delete compProps.subFields
    }
    // 序列化实体引用配置
    if ((field.componentType || '').toUpperCase() === 'REFERENCE' || (field.componentType || '').toUpperCase() === 'MULTI_REFERENCE') {
      compProps.refConfig = {
        refEntityType: field.refEntityType || '',
        refEntityId: field.refEntityId || '',
        apiUrl: field.apiUrl || ''
      }
    }

    // 序列化事件配置
    const events = {}
    Object.keys(field).forEach(key => {
      if (key.startsWith('eventOn') && field[key]) {
        const eventName = 'on' + key.slice(7)
        events[eventName] = field[key]
      }
    })
    if (Object.keys(events).length > 0) {
      compProps.events = events
    } else {
      delete compProps.events
    }

    // 序列化选项配置（optionsJson → componentProps.options）
    if (field.optionsJson) {
      try {
        const options = JSON.parse(field.optionsJson)
        if (Array.isArray(options) && options.length > 0) {
          compProps.options = options
        }
      } catch (e) {}
    }

    field.componentProps = JSON.stringify(compProps)
  } catch (e) {
    console.error('序列化字段配置失败:', e)
  }
}

// 加载表单字段
async function loadFormFields() {
  if (!isEdit.value) return

  try {
    formFields.value = await getFormFields(formId)
    // 统一将 refEntityId 转为字符串，避免 el-select 类型不匹配显示原始值
    formFields.value.forEach(field => {
      if (field.refEntityId != null) {
        field.refEntityId = String(field.refEntityId)
      }
      if (field.childEntityId != null) {
        field.childEntityId = String(field.childEntityId)
      }
      if ((field.componentType || '').toUpperCase() === 'SUB_FORM') {
        field.childEntityId = field.childEntityId || field.refEntityId || ''
        field.childRefFieldCode = field.childRefFieldCode || field.refFieldCode || ''
        field.relationType = field.relationType || 'ONE_TO_MANY'
        field.repeatable = field.relationType !== 'ONE_TO_ONE'
      }
    })
    formFields.value.forEach(restoreFieldConfig)
    enrichFieldCodes()
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
    fieldCode: entityField.fieldCode,
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

  // 复制实体引用配置（统一将 refEntityId 转为字符串，避免 el-select 类型不匹配）
  if (entityField.refEntityId) {
    newField.refEntityId = String(entityField.refEntityId)
  }
  if (entityField.refEntityType) {
    newField.refEntityType = entityField.refEntityType
  }
  if (entityField.apiUrl) {
    newField.apiUrl = entityField.apiUrl
  }
  if (entityField.childEntityId) {
    newField.childEntityId = String(entityField.childEntityId)
    newField.refEntityId = String(entityField.childEntityId)
  }
  if (entityField.childRefFieldCode) {
    newField.childRefFieldCode = entityField.childRefFieldCode
    newField.refFieldCode = entityField.childRefFieldCode
  }
  if (entityField.relationType) {
    newField.relationType = entityField.relationType
  }
  // 子表单默认展示
  if (newField.componentType === 'sub_form' || newField.componentType === 'SUB_FORM') {
    newField.layout = 'form'
    newField.displayMode = 'embedded'
    newField.repeatable = newField.relationType !== 'ONE_TO_ONE'
    if (newField.refEntityId) {
      loadFormListByEntity(newField.refEntityId)
    }
  }

  // 复制选项数据（用于选项联动等）
  if (entityField.optionsJson) {
    newField.optionsJson = entityField.optionsJson
  }
  if (entityField.componentProps) {
    newField.componentProps = entityField.componentProps
  }
  if (entityField.options) {
    newField.options = entityField.options
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
    'BOOLEAN': 'switch',
    'SUB_FORM': 'sub_form',
    'SUB_FORM_LIST': 'sub_form',
    'REFERENCE': 'reference',
    'MULTI_REFERENCE': 'multi_reference',
    'SELECT': 'select',
    'MULTI_SELECT': 'select_multiple',
    'RADIO': 'radio',
    'CHECKBOX': 'checkbox'
  }
  return typeMap[fieldType] || 'input'
}

// 判断是否为节字段
function isSectionField(field) {
  return (field?.fieldType || '').toUpperCase() === 'SECTION' ||
    (field?.componentType || '').toLowerCase() === 'section'
}

// 添加节
function addSection() {
  const ts = Date.now()
  const section = {
    id: `section_${ts}`,
    formId: formId,
    fieldId: `section_${ts}`,
    fieldCode: `section_${ts}`,
    fieldName: '新节',
    fieldLabel: '新节',
    fieldType: 'SECTION',
    componentType: 'section',
    isRequired: 0,
    isReadonly: 1,
    isHidden: 0,
    gridSpan: 24,
    sortOrder: formFields.value.length
  }
  formFields.value.push(section)
  selectedField.value = section
  ElMessage.success('节已添加')
}

// 选择字段
function selectField(field) {
  selectedField.value = field
  if (field && (field.componentType || '').toUpperCase() === 'SUB_FORM') {
    field.childEntityId = field.childEntityId || field.refEntityId || ''
    field.childRefFieldCode = field.childRefFieldCode || field.refFieldCode || ''
    field.relationType = field.relationType || 'ONE_TO_MANY'
    field.repeatable = field.relationType !== 'ONE_TO_ONE'
    const refEntityId = field.childEntityId || field.refEntityId || entityInfo.value.id
    loadFormListByEntity(refEntityId)
  }
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

// 打开事件配置弹框
function openEventConfig() {
  if (!selectedField.value) return
  currentEventField.value = selectedField.value
  showEventConfig.value = true
}

// 保存事件配置
function handleSaveEvent(events) {
  if (!currentEventField.value) return
  // 清除旧的事件根属性
  Object.keys(currentEventField.value).forEach(key => {
    if (key.startsWith('eventOn')) {
      delete currentEventField.value[key]
    }
  })
  // 保存所有事件（包括自定义事件）
  Object.keys(events).forEach(key => {
    if (events[key]) {
      const rootKey = 'eventOn' + key.charAt(2).toUpperCase() + key.slice(3)
      currentEventField.value[rootKey] = events[key]
    }
  })
  ElMessage.success('事件配置已保存')
}

// 保存联动配置
function handleSaveLinkage(linkageRules) {
  if (selectedField.value) {
    // 先清除旧的联动规则根属性，避免切换类型后残留
    const allRuleKeys = ['visibilityRule', 'disabledRule', 'requiredRule', 'calculationFormula',
      'calculationPrecision', 'calculationEditable', 'optionsLinkage', 'valueFormula', 'valueMapping', 'valueApi']
    allRuleKeys.forEach(key => {
      delete selectedField.value[key]
    })

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

// 引用实体变化时加载表单列表
function handleRefEntityChange(entityId) {
  loadFormListByEntity(entityId || entityInfo.value.id)
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
    
    // 2. 保存表单字段（先序列化子表单和事件配置到 componentProps）
    formFields.value.forEach(serializeFieldConfig)
    const fieldsToSave = formFields.value.map((f, index) => ({
      ...f,
      formId: newFormId,
      sortOrder: index
    }))

    await saveFormFields(newFormId, fieldsToSave)
    
    ElMessage.success('表单保存成功')
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
  // 先加载实体列表，确保 el-select 有选项后再加载并恢复表单字段
  await loadEntityList()
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

.field-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}

.field-tags .el-tag {
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 16px;
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

/* 节字段在画布中占满一行，且不显示表单项标签 */
.form-field-wrapper.section-field-wrapper {
  width: 100% !important;
  align-items: center;
}

.form-field-wrapper.section-field-wrapper .field-content {
  flex: 1;
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

.relation-summary {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
  padding: 8px 0 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.relation-summary div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  line-height: 20px;
}

.relation-summary span {
  color: #909399;
}

.relation-summary strong {
  max-width: 150px;
  overflow: hidden;
  color: #303133;
  font-weight: 500;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
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
