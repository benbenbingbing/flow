/**
 * 带字段联动功能的表单渲染组件
 * 支持显隐控制、值联动、选项联动、计算字段
 */

<template>
  <div
    class="linkage-form-preview"
    :class="[form?.layoutType, { 'has-tabs': tabSubForms.length > 0 && !noInternalTabs }]"
    :style="previewStyle"
  >
    <div class="preview-header" v-if="showHeader">
      <h3>{{ form?.formName }}</h3>
    </div>
    
    <template v-if="form?.customComponent && hasCustomFormComponent(form.customComponent)">
      <component
        ref="customFormRef"
        :is="getCustomFormComponent(form.customComponent)"
        :form="form"
        :modelValue="formData"
        @update:modelValue="handleCustomFormUpdate"
        :readonly="readonly"
        :fields="processedFields"
        :linkageState="linkageState"
        :mode="mode"
        :config="formViewConfig.customComponentProps || {}"
        :context="runtimeContext"
        :entity-code="entityCode"
        :entity-definition="entityDefinition"
        :entity-fields="entityFields"
        :data-source-runtime="dataSourceRuntime"
      />
    </template>
    <FormNodeRenderer
      v-else-if="hasNodeTree"
      ref="nodeFormRef"
      :nodes="form.nodes"
      :fields="processedFields"
      :model-value="formData"
      :linkage-state="linkageState"
      :readonly="readonly"
      :mode="mode"
      :context="runtimeContext"
      :data-source-runtime="dataSourceRuntime"
      :label-width="labelWidth"
      :label-position="labelPosition"
      @update:model-value="handleCustomFormUpdate"
    />
    <el-form
      v-else
      ref="formRef"
      :model="formData"
      :label-width="labelWidth"
      :label-position="labelPosition"
      class="preview-form"
    >
      <!-- 没有 Tab 子表单，或外部接管 tabs 时，只渲染普通字段 -->
      <template v-if="tabSubForms.length === 0 || noInternalTabs">
        <div
          v-for="field in (noInternalTabs ? normalFields : processedFields)"
          :key="field.id"
          class="preview-field-wrapper"
          :class="{ 'section-preview': isSectionField(field) }"
          :style="isSectionField(field) ? { width: '100%' } : getFieldStyle(field)"
          v-show="linkageState.visibility[getFieldKey(field)] !== false"
        >
          <template v-if="isSectionField(field)">
            <SectionField :field="field" />
          </template>
          <el-form-item
            v-else
            :label="field.fieldLabel || field.fieldName"
            :prop="getFieldKey(field)"
            :rules="getFieldRules(field)"
            :required="isFieldRequired(field)"
          >
            <FormFieldRendererLinkage
              :field="field"
              v-model="formData[getFieldKey(field)]"
              :disabled="isFieldDisabled(field)"
              :options="linkageState.options[getFieldKey(field)] || field.options"
              :context="{ ...runtimeContext, field }"
              :data-source-runtime="dataSourceRuntime"
            />
          </el-form-item>
        </div>
      </template>

      <!-- 有 Tab 子表单时，整个表单用 Tab 组织 -->
      <template v-else>
        <div class="form-tabs-wrapper">
          <el-tabs
            v-model="activeTabSubForm"
            type="border-card"
          >
            <!-- 普通字段放在"基本信息"Tab -->
            <el-tab-pane
              v-if="normalFields.length > 0"
              label="基本信息"
              name="basic"
              key="pane-basic"
            >
              <div
                v-for="field in normalFields"
                :key="'basic-' + (field.id || field.fieldCode || field.fieldKey)"
                class="preview-field-wrapper"
                :class="{ 'section-preview': isSectionField(field) }"
                :style="isSectionField(field) ? { width: '100%' } : getFieldStyle(field)"
                v-show="linkageState.visibility[getFieldKey(field)] !== false"
              >
                <template v-if="isSectionField(field)">
                  <SectionField :field="field" />
                </template>
                <el-form-item
                  v-else
                  :label="field.fieldLabel || field.fieldName"
                  :prop="getFieldKey(field)"
                  :rules="getFieldRules(field)"
                  :required="isFieldRequired(field)"
                >
                  <FormFieldRendererLinkage
                    :field="field"
                    v-model="formData[getFieldKey(field)]"
                    :disabled="isFieldDisabled(field)"
                    :options="linkageState.options[getFieldKey(field)] || field.options"
                    :context="{ ...runtimeContext, field }"
                    :data-source-runtime="dataSourceRuntime"
                  />
                </el-form-item>
              </div>
            </el-tab-pane>

            <!-- Tab 子表单 -->
            <el-tab-pane
              v-for="(field, idx) in tabSubForms"
              :key="'pane-subform-' + idx + '-' + (field.id || field.fieldCode || field.fieldKey || '')"
              :label="field.fieldLabel || field.fieldName"
              :name="'tab_' + idx"
            >
              <FormFieldRendererLinkage
                :field="field"
                v-model="formData[getFieldKey(field)]"
                :disabled="isFieldDisabled(field)"
                :options="linkageState.options[getFieldKey(field)] || field.options"
                :context="{ ...runtimeContext, field }"
                :data-source-runtime="dataSourceRuntime"
              />
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, watch, watchEffect, onMounted, nextTick } from 'vue'
import FormFieldRendererLinkage from './FormFieldRendererLinkage.vue'
import FormNodeRenderer from './FormNodeRenderer.vue'
import SectionField from './form-fields/components/SectionField.vue'
import LinkageEngine from '../utils/linkageEngine'
import { getCustomFormComponent, hasCustomFormComponent } from '@/utils/customComponentRegistry.js'
import { buildRuntimeFieldRules, getFieldKey } from '@/shared/form-runtime'
import {
  isFieldReadonlyForMode,
  isFieldVisibleForMode,
  safeParseConfig
} from '@/shared/config-runtime'

const props = defineProps({
  form: {
    type: Object,
    required: true
  },
  modelValue: {
    type: Object,
    default: () => ({})
  },
  readonly: {
    type: Boolean,
    default: false
  },
  mode: {
    type: String,
    default: 'view'
  },
  showHeader: {
    type: Boolean,
    default: true
  },
  // 是否禁用内部 Tab 组织（用于外部接管 tabs，如审批弹窗）
  noInternalTabs: {
    type: Boolean,
    default: false
  },
  height: {
    type: String,
    default: '70vh'
  },
  entityCode: {
    type: String,
    default: ''
  },
  entityDefinition: {
    type: Object,
    default: null
  },
  entityFields: {
    type: Array,
    default: () => []
  },
  context: {
    type: Object,
    default: () => ({})
  },
  dataSourceRuntime: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])
const formViewConfig = computed(() => safeParseConfig(props.form?.viewConfig))

// 预览容器样式：支持自定义高度，默认 70vh
// 无 tab 时整体滚动；有 tab 时只滚动 tab content，tab header 固定
const previewStyle = computed(() => {
  const hasTabs = tabSubForms.value.length > 0 && !props.noInternalTabs
  return {
    height: props.height,
    overflowY: hasTabs ? 'hidden' : 'auto'
  }
})

// 自定义表单组件数据更新回调
function handleCustomFormUpdate(val) {
  formData.value = { ...val }
  emit('update:modelValue', formData.value)
}

const formRef = ref(null)
const customFormRef = ref(null)
const nodeFormRef = ref(null)
const formData = ref(props.modelValue || {})
const linkageState = ref({
  visibility: {},
  disabled: {},
  required: {},
  options: {}
})
const activeTabSubForm = ref('basic')
const hasNodeTree = computed(() => Array.isArray(props.form?.nodes) && props.form.nodes.length > 0)
const runtimeContext = computed(() => ({
  ...props.context,
  mode: props.mode,
  form: props.form,
  readonly: props.readonly,
  entityCode: props.entityCode,
  entityDefinition: props.entityDefinition
}))

// 判断是否为 Tab 模式的子表单
function isSectionField(field) {
  return (field?.fieldType || '').toUpperCase() === 'SECTION' ||
    (field?.componentType || '').toLowerCase() === 'section'
}

function isTabSubForm(field) {
  const type = (field.componentType || field.fieldType || '').toUpperCase()
  const result = field.displayMode === 'tab' || (() => {
    if (field.componentProps) {
      try {
        const compProps = typeof field.componentProps === 'string'
          ? JSON.parse(field.componentProps)
          : field.componentProps
        return compProps.subFormConfig?.displayMode === 'tab'
      } catch (e) {}
    }
    return false
  })()
  if (!['SUB_FORM', 'SUB_FORM_LIST'].includes(type)) return false
  return result
}

// 同步外部数据（只在引用变化时同步，避免与内部 watcher 循环）
watch(() => props.modelValue, (val) => {
  if (val === formData.value) return
  formData.value = val || {}
  updateLinkageState()
})

// 同步内部数据到外部，并触发联动更新
watch(formData, (val) => {
  emit('update:modelValue', val)
  updateLinkageState()
  applyLinkageValues()
  applyCalculatedValues()
}, { deep: true })

// 处理后的字段列表（过滤掉隐藏的）
const processedFields = computed(() => {
  const fields = props.form?.fields || []
  return [...fields]
    .filter(field => isFieldVisibleForMode(field, props.mode))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
})

// 普通字段（嵌入表单或普通字段）
const normalFields = computed(() => {
  return processedFields.value.filter(f => !isTabSubForm(f))
})

// Tab 模式的子表单
const tabSubForms = computed(() => {
  return processedFields.value.filter(f => isTabSubForm(f))
})

// 自动设置第一个 tab 为激活状态
watchEffect(() => {
  const tabs = tabSubForms.value
  const normals = normalFields.value
  if (tabs.length > 0) {
    const allTabNames = [
      ...(normals.length > 0 ? ['basic'] : []),
      ...tabs.map((_, i) => 'tab_' + i)
    ]
    if (!allTabNames.includes(activeTabSubForm.value)) {
      activeTabSubForm.value = allTabNames[0] || ''
    }
  }
})

// 标签位置
const labelPosition = computed(() => {
  switch (props.form?.layoutType) {
    case 'horizontal':
      return 'right'
    case 'vertical':
      return 'top'
    default:
      return 'right'
  }
})

// 标签宽度
const labelWidth = computed(() => {
  if (formViewConfig.value.labelWidth) {
    return `${formViewConfig.value.labelWidth}px`
  }
  switch (props.form?.layoutType) {
    case 'horizontal':
      return '120px'
    case 'vertical':
      return 'auto'
    default:
      return '120px'
  }
})

// 获取字段样式
function getFieldStyle(field) {
  if (props.form?.layoutType === 'grid') {
    const span = field.gridSpan || 24
    return {
      width: `${(span / 24) * 100}%`,
      flex: `0 0 ${(span / 24) * 100}%`,
      padding: '8px',
      boxSizing: 'border-box'
    }
  }
  if (props.form?.layoutType === 'horizontal') {
    return {
      width: 'calc(50% - 10px)',
      flex: '0 0 calc(50% - 10px)'
    }
  }
  // vertical
  return {
    width: '100%',
    padding: '4px 0'
  }
}

// 判断字段是否禁用（实体引用字段不受 isReadonly 影响，需保持可交互以选择数据）
function isFieldDisabled(field) {
  const fieldKey = getFieldKey(field)
  if (linkageState.value.disabled[fieldKey]) return true
  return isFieldReadonlyForMode(field, props.mode, props.readonly)
}

// 判断字段是否必填（联动状态优先，未配置时回退到字段本身的 isRequired）
function isFieldRequired(field) {
  const fieldKey = getFieldKey(field)
  if (linkageState.value.required[fieldKey] !== undefined) {
    return linkageState.value.required[fieldKey]
  }
  return field.isRequired
}

// 获取字段验证规则
function getFieldRules(field) {
  return buildRuntimeFieldRules(
    field,
    isFieldRequired(field),
    field.fieldLabel || field.fieldName
  )
}

function isLinkageStateEqual(a, b) {
  if (a === b) return true
  const keysA = Object.keys(a)
  const keysB = Object.keys(b)
  if (keysA.length !== keysB.length) return false
  return keysA.every(k => {
    const va = a[k]
    const vb = b[k]
    if (va === vb) return true
    if (typeof va === 'object' && typeof vb === 'object' && va !== null && vb !== null) {
      const subKeysA = Object.keys(va)
      const subKeysB = Object.keys(vb)
      if (subKeysA.length !== subKeysB.length) return false
      return subKeysA.every(sk => va[sk] === vb[sk])
    }
    return false
  })
}

// 更新联动状态（只更新 linkageState，不直接修改 formData，避免 watcher 递归）
function updateLinkageState() {
  const fields = props.form?.fields || []
  const newState = LinkageEngine.processAllLinkages(fields, formData.value)
  if (!isLinkageStateEqual(linkageState.value, newState)) {
    linkageState.value = newState
  }
}

// 应用值联动和计算字段的结果（使用 nextTick 避免在 watcher 回调中递归）
function applyLinkageValues() {
  const values = linkageState.value.values
  if (!values) return
  const entries = Object.entries(values).filter(([key, val]) => val !== null && val !== undefined && formData.value[key] !== val)
  if (entries.length > 0) {
    nextTick(() => {
      entries.forEach(([key, val]) => {
        formData.value[key] = val
      })
    })
  }
}

// 应用计算字段值
function applyCalculatedValues() {
  const fields = props.form?.fields || []
  nextTick(() => {
    fields.forEach(field => {
      const rules = LinkageEngine.getFieldLinkageRules(field)
      if (rules.calculationFormula) {
        const fieldKey = field.fieldCode || field.fieldKey
        const calculatedValue = LinkageEngine.calculate(rules.calculationFormula, formData.value)
        if (calculatedValue !== null && formData.value[fieldKey] !== calculatedValue) {
          formData.value[fieldKey] = calculatedValue
        }
      }
    })
  })
}

// 处理字段值变化
function handleFieldChange(fieldKey, value) {
  // 更新当前字段值
  formData.value[fieldKey] = value

  // 重新计算联动状态并应用
  updateLinkageState()
  applyLinkageValues()
  applyCalculatedValues()
}

// 初始化
onMounted(() => {
  updateLinkageState()
  applyLinkageValues()
  applyCalculatedValues()
})

// 验证表单
async function validate() {
  if (customFormRef.value?.validate) {
    return (await customFormRef.value.validate()) !== false
  }
  if (hasNodeTree.value) {
    return (await nodeFormRef.value?.validate()) !== false
  }
  if (!formRef.value) return true
  try {
    await formRef.value.validate()
    return true
  } catch {
    return false
  }
}

// 暴露方法
defineExpose({
  validate,
  getData: () => formData.value
})
</script>

<style scoped>
.linkage-form-preview {
  width: 100%;
}

.preview-header h3 {
  margin: 0 0 20px 0;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
}

.preview-form {
  display: flex;
  flex-wrap: wrap;
}

.preview-form > .el-tabs {
  width: 100%;
}

.preview-field-wrapper {
  transition: all 0.3s ease;
}

.form-tabs-wrapper {
  width: 100%;
  display: block;
}

/* 有 tab 时：整体 flex 布局，tab header 固定，tab body 滚动 */
.linkage-form-preview.has-tabs {
  display: flex;
  flex-direction: column;
}
.linkage-form-preview.has-tabs .preview-form {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  flex-wrap: nowrap;
}
.linkage-form-preview.has-tabs .form-tabs-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.linkage-form-preview.has-tabs :deep(.el-tabs) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.linkage-form-preview.has-tabs :deep(.el-tabs__header) {
  flex-shrink: 0;
}
.linkage-form-preview.has-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.preview-field-wrapper[v-show="false"] {
  display: none !important;
}
</style>
