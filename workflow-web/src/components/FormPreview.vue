<template>
  <div class="form-preview" :class="form?.layoutType">
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
        v-for="field in sortedFields" 
        :key="field.id"
        class="preview-field-wrapper"
        :style="getFieldStyle(field)"
      >
        <el-form-item
          :label="field.fieldLabel || field.fieldName"
          :prop="field.fieldCode || `field_${field.id}`"
          :rules="getFieldRules(field)"
          :required="field.isRequired === 1"
          class="preview-form-item"
        >
          <FormFieldRenderer 
            :field="field" 
            v-model="formData[field.fieldCode || `field_${field.id}`]"
            :disabled="readonly || field.isReadonly === 1"
          />
        </el-form-item>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import FormFieldRenderer from './FormFieldRenderer.vue'

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

// 同步外部数据
watch(() => props.modelValue, (val) => {
  formData.value = { ...val }
}, { deep: true })

// 同步内部数据到外部
watch(formData, (val) => {
  emit('update:modelValue', val)
}, { deep: true })

// 排序后的字段
const sortedFields = computed(() => {
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
  const rules = []
  if (field.isRequired === 1) {
    rules.push({
      required: true,
      message: `请输入${field.fieldLabel || field.fieldName}`,
      trigger: 'blur'
    })
  }
  return rules
}

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

// 获取表单数据
function getFormData() {
  return { ...formData.value }
}

// 设置表单数据
function setFormData(data) {
  formData.value = { ...data }
}

// 重置表单
function resetFields() {
  formRef.value?.resetFields()
}

defineExpose({
  validate,
  getFormData,
  setFormData,
  resetFields
})
</script>

<style scoped>
.form-preview {
  background-color: #fff;
  padding: 24px;
}

.preview-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.preview-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  color: #303133;
}

.preview-form {
  display: flex;
  flex-wrap: wrap;
}

/* 垂直布局 - 每行一个字段 */
.form-preview.vertical .preview-form {
  flex-direction: column;
}

.form-preview.vertical .preview-field-wrapper {
  width: 100%;
}

/* 水平布局 - 每行两个字段 */
.form-preview.horizontal .preview-form {
  gap: 20px;
}

/* 网格布局 */
.form-preview.grid .preview-form {
  margin: 0;
}

.preview-form-item {
  margin-bottom: 18px;
}

.preview-form-item :deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}

/* 设计模式下的样式 */
:deep(.el-form-item__content) {
  display: flex;
  align-items: center;
}

:deep(.el-form-item__content > *) {
  flex: 1;
}

/* 确保组件宽度一致 */
:deep(.el-input),
:deep(.el-select),
:deep(.el-date-editor),
:deep(.el-input-number) {
  width: 100% !important;
}
</style>
