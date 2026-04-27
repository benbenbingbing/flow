/**
 * 带字段联动功能的表单渲染组件
 * 支持显隐控制、值联动、选项联动、计算字段
 */

<template>
  <div class="linkage-form-preview" :class="form?.layoutType">
    <div class="preview-header" v-if="showHeader">
      <h3>{{ form?.formName }}</h3>
    </div>
    
    <el-form 
      ref="formRef"
      :model="formData"
      :label-width="labelWidth"
      :label-position="labelPosition"
      class="preview-form"
    >
      <div 
        v-for="field in processedFields" 
        :key="field.id"
        class="preview-field-wrapper"
        :style="getFieldStyle(field)"
        v-show="linkageState.visibility[field.fieldCode || field.fieldKey] !== false"
      >
        <el-form-item
          :label="field.fieldLabel || field.fieldName"
          :prop="field.fieldCode || field.fieldKey"
          :rules="getFieldRules(field)"
          :required="linkageState.required[field.fieldCode || field.fieldKey]"
        >
          <FormFieldRendererLinkage
            :field="field" 
            v-model="formData[field.fieldCode || field.fieldKey]"
            :disabled="readonly || field.isReadonly === 1 || linkageState.disabled[field.fieldCode || field.fieldKey]"
            :options="linkageState.options[field.fieldCode || field.fieldKey] || field.options"
          />
        </el-form-item>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import FormFieldRendererLinkage from './FormFieldRendererLinkage.vue'
import LinkageEngine from '../utils/linkageEngine'

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
  }
})

const emit = defineEmits(['update:modelValue'])

const formRef = ref(null)
const formData = ref({ ...props.modelValue })
const linkageState = ref({
  visibility: {},
  disabled: {},
  required: {},
  options: {}
})

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

// 获取字段验证规则
function getFieldRules(field) {
  const fieldKey = field.fieldCode || field.fieldKey
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

<script>
// 导出联动引擎供外部使用
export { LinkageEngine }
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

.preview-field-wrapper {
  transition: all 0.3s ease;
}

.preview-field-wrapper[v-show="false"] {
  display: none !important;
}
</style>
