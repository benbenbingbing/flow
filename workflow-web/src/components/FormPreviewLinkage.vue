/**
 * 带字段联动功能的表单渲染组件
 * 支持显隐控制、值联动、选项联动、计算字段
 */

<template>
  <div class="linkage-form-preview" :class="form?.layoutType">
    <div class="preview-header" v-if="showHeader">
      <h3>{{ form?.formName }}</h3>
    </div>
    
    <template v-if="form?.customComponent && hasCustomFormComponent(form.customComponent)">
      <component
        :is="getCustomFormComponent(form.customComponent)"
        :form="form"
        :modelValue="formData"
        @update:modelValue="handleCustomFormUpdate"
        :readonly="readonly"
        :fields="processedFields"
        :linkageState="linkageState"
      />
    </template>
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
          :style="getFieldStyle(field)"
          v-show="linkageState.visibility[getFieldKey(field)] !== false"
        >
          <el-form-item
            :label="field.fieldLabel || field.fieldName"
            :prop="getFieldKey(field)"
            :rules="getFieldRules(field)"
            :required="linkageState.required[getFieldKey(field)]"
          >
            <FormFieldRendererLinkage
              :field="field"
              v-model="formData[getFieldKey(field)]"
              :disabled="readonly || field.isReadonly === 1 || linkageState.disabled[getFieldKey(field)]"
              :options="linkageState.options[getFieldKey(field)] || field.options"
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
                :style="getFieldStyle(field)"
                v-show="linkageState.visibility[getFieldKey(field)] !== false"
              >
                <el-form-item
                  :label="field.fieldLabel || field.fieldName"
                  :prop="getFieldKey(field)"
                  :rules="getFieldRules(field)"
                  :required="linkageState.required[getFieldKey(field)]"
                >
                  <FormFieldRendererLinkage
                    :field="field"
                    v-model="formData[getFieldKey(field)]"
                    :disabled="readonly || field.isReadonly === 1 || linkageState.disabled[getFieldKey(field)]"
                    :options="linkageState.options[getFieldKey(field)] || field.options"
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
                :disabled="readonly || field.isReadonly === 1 || linkageState.disabled[getFieldKey(field)]"
                :options="linkageState.options[getFieldKey(field)] || field.options"
              />
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, watch, watchEffect, onMounted } from 'vue'
import FormFieldRendererLinkage from './FormFieldRendererLinkage.vue'
import LinkageEngine from '../utils/linkageEngine'
import { getCustomFormComponent, hasCustomFormComponent } from '@/utils/customComponentRegistry.js'

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
  showHeader: {
    type: Boolean,
    default: true
  },
  // 是否禁用内部 Tab 组织（用于外部接管 tabs，如审批弹窗）
  noInternalTabs: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

// 自定义表单组件数据更新回调
function handleCustomFormUpdate(val) {
  formData.value = { ...val }
  emit('update:modelValue', formData.value)
}

const formRef = ref(null)
const formData = ref({ ...props.modelValue })
const linkageState = ref({
  visibility: {},
  disabled: {},
  required: {},
  options: {}
})
const activeTabSubForm = ref('')

// 判断是否为 Tab 模式的子表单
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
  if (type !== 'SUB_FORM') return false
  return result
}

// 同步外部数据
watch(() => props.modelValue, (val) => {
  formData.value = { ...val }
  updateLinkageState()
}, { deep: true })

// 同步内部数据到外部，并触发联动更新
watch(formData, (val) => {

  emit('update:modelValue', val)
  updateLinkageState()
  // 计算字段自动赋值
  applyCalculatedValues()
}, { deep: true })

// 处理后的字段列表（过滤掉隐藏的）
const processedFields = computed(() => {
  const fields = props.form?.fields || []
  return [...fields].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
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

// 获取字段唯一键（兼容多种字段结构）
function getFieldKey(field) {
  return String(field.fieldCode || field.fieldKey || field.fieldId || field.id || '')
}

// 获取字段验证规则
function getFieldRules(field) {
  const fieldKey = getFieldKey(field)
  const rules = []

  // 检查联动必填
  if (linkageState.value.required[fieldKey]) {
    rules.push({
      required: true,
      message: `请输入${field.fieldLabel || field.fieldName}`,
      trigger: 'blur'
    })
  }

  return rules
}

// 更新联动状态
function updateLinkageState() {
  const fields = props.form?.fields || []

  linkageState.value = LinkageEngine.processAllLinkages(fields, formData.value)
  // 应用值联动和计算字段的结果
  if (linkageState.value.values) {
    Object.entries(linkageState.value.values).forEach(([key, val]) => {
      if (val !== null && val !== undefined && formData.value[key] !== val) {
        formData.value[key] = val
      }
    })
  }
}

// 应用计算字段值
function applyCalculatedValues() {
  const fields = props.form?.fields || []
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
}

// 处理字段值变化
function handleFieldChange(fieldKey, value) {
  // 更新当前字段值
  formData.value[fieldKey] = value

  // 重新计算联动状态
  updateLinkageState()

  // 处理计算字段自动赋值
  applyCalculatedValues()
}

// 初始化
onMounted(() => {
  updateLinkageState()
  // 初始化计算字段
  applyCalculatedValues()
})

// 验证表单
async function validate() {
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

.preview-field-wrapper[v-show="false"] {
  display: none !important;
}
</style>
