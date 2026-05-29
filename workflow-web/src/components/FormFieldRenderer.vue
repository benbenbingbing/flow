<template>
  <div class="form-field-renderer">
    <!-- 使用 componentType 或 fieldType 来判断 -->
    
    <!-- 文本输入 -->
    <el-input
      v-if="renderType === 'input'"
      v-model="fieldValue"
      :placeholder="field.placeholder || `请输入${fieldLabel}`"
      :disabled="disabled"
      clearable
    />
    
    <!-- 多行文本 -->
    <el-input
      v-else-if="renderType === 'textarea'"
      v-model="fieldValue"
      type="textarea"
      :rows="3"
      :placeholder="field.placeholder || `请输入${fieldLabel}`"
      :disabled="disabled"
    />

    <!-- 富文本 -->
    <RichTextEditor
      v-else-if="renderType === 'rich_text'"
      v-model="fieldValue"
      :disabled="disabled"
      :height="200"
    />
    
    <!-- 数字 -->
    <el-input-number
      v-else-if="renderType === 'number'"
      v-model="fieldValue"
      :placeholder="field.placeholder || `请输入${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
    />
    
    <!-- 日期 -->
    <el-date-picker
      v-else-if="renderType === 'date'"
      v-model="fieldValue"
      type="date"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
    />
    
    <!-- 日期时间 -->
    <el-date-picker
      v-else-if="renderType === 'datetime'"
      v-model="fieldValue"
      type="datetime"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
    />
    
    <!-- 下拉选择（单选） -->
    <el-select
      v-else-if="renderType === 'select'"
      v-model="fieldValue"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
      clearable
    >
      <el-option
        v-for="opt in options"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
    
    <!-- 下拉选择（多选） -->
    <el-select
      v-else-if="renderType === 'select_multiple'"
      v-model="fieldValue"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      style="width: 100%"
      multiple
      clearable
    >
      <el-option
        v-for="opt in options"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
    
    <!-- 单选 -->
    <el-radio-group
      v-else-if="renderType === 'radio'"
      v-model="fieldValue"
      :disabled="disabled"
    >
      <el-radio
        v-for="opt in options"
        :key="opt.value"
        :label="opt.value"
      >
        {{ opt.label }}
      </el-radio>
    </el-radio-group>
    
    <!-- 多选 -->
    <el-checkbox-group
      v-else-if="renderType === 'checkbox'"
      v-model="fieldValue"
      :disabled="disabled"
    >
      <el-checkbox
        v-for="opt in options"
        :key="opt.value"
        :label="opt.value"
      >
        {{ opt.label }}
      </el-checkbox>
    </el-checkbox-group>
    
    <!-- 开关 -->
    <el-switch
      v-else-if="renderType === 'switch'"
      v-model="fieldValue"
      :disabled="disabled"
    />
    
    <!-- 文件上传 -->
    <FileUploader
      v-else-if="renderType === 'file' || renderType === 'image'"
      v-model="fieldValue"
      :field="field"
      :disabled="disabled"
      :is-image="renderType === 'image'"
    />
    
    <!-- 子表单 -->
    <SubFormRenderer
      v-else-if="renderType === 'sub_form' || renderType === 'sub_form_list' || renderType === 'SUB_FORM'"
      v-model="fieldValue"
      :config="subFormConfig"
      :title="field.fieldName"
      :readonly="disabled"
      :disabled="disabled"
    />
    
    <!-- 单选实体 -->
    <EntitySelector
      v-else-if="renderType === 'reference' || renderType === 'REFERENCE'"
      v-model="fieldValue"
      :entity-type="getRefEntityType()"
      :entity-code="field.refEntityId"
      :ref-entity-id="field.refEntityId"
      :api-url="getApiUrl(field)"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      @change="handleEntitySelect"
    />
    
    <!-- 多选实体 -->
    <EntitySelector
      v-else-if="renderType === 'multi_reference' || renderType === 'MULTI_REFERENCE'"
      v-model="fieldValue"
      :entity-type="getRefEntityType()"
      :entity-code="field.refEntityId"
      :ref-entity-id="field.refEntityId"
      :api-url="field.apiUrl"
      :multiple="true"
      :placeholder="field.placeholder || `请选择${fieldLabel}`"
      :disabled="disabled"
      @change="handleEntitySelect"
    />
    
    <!-- 默认文本输入 -->
    <el-input
      v-else
      v-model="fieldValue"
      :placeholder="field.placeholder || `请输入${fieldLabel}`"
      :disabled="disabled"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import SubFormRenderer from './SubFormRenderer.vue'
import FileUploader from './FileUploader.vue'
import EntitySelector from './EntitySelector.vue'
import RichTextEditor from './RichTextEditor.vue'
import { getFormFields } from '@/api/entityForm'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  modelValue: {
    type: [String, Number, Array, Date, Object],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

// 渲染类型：优先使用 componentType，其次使用 fieldType
const renderType = computed(() => {
  const type = props.field.componentType || props.field.fieldType || ''
  return type.toLowerCase()
})

// 字段显示标签
const fieldLabel = computed(() => {
  return props.field.fieldLabel || props.field.fieldName || ''
})

const fieldValue = computed({
  get() {
    if ((renderType.value === 'checkbox' || renderType.value === 'CHECKBOX') && !Array.isArray(props.modelValue)) {
      return props.modelValue ? [props.modelValue] : []
    }
    // 子表单数据必须是数组
    if ((renderType.value === 'sub_form' || renderType.value === 'SUB_FORM') && !Array.isArray(props.modelValue)) {
      return []
    }
    return props.modelValue
  },
  set(val) {
    emit('update:modelValue', val)
  }
})

// 子表单元数据解析（兼容根属性和 componentProps）
const subFormMeta = computed(() => {
  const meta = {
    refFormId: props.field.refFormId,
    subFormType: props.field.subFormType,
    displayMode: props.field.displayMode
  }
  if (!meta.refFormId && props.field.componentProps) {
    try {
      const cp = typeof props.field.componentProps === 'string'
        ? JSON.parse(props.field.componentProps)
        : props.field.componentProps
      if (cp.subFormConfig) {
        meta.refFormId = cp.subFormConfig.refFormId
        meta.subFormType = cp.subFormConfig.type
        meta.displayMode = cp.subFormConfig.displayMode
      }
    } catch (e) {}
  }
  if ((props.field.componentType || '').toUpperCase() === 'SUB_FORM' || (props.field.fieldType || '').toUpperCase() === 'SUB_FORM') {
    console.log('[FormFieldRenderer] subFormMeta:', props.field.fieldName, meta, 'componentProps:', props.field.componentProps)
  }
  return meta
})

// 外部表单字段缓存
const externalFormFields = ref([])

// 监听 refFormId 变化，加载外部表单字段
watch(() => subFormMeta.value.refFormId, async (formId) => {
  console.log('[FormFieldRenderer] watch refFormId:', formId, 'subFormType:', subFormMeta.value.subFormType, 'field:', props.field.fieldName)
  if (formId && subFormMeta.value.subFormType === 'ref') {
    try {
      const res = await getFormFields(formId)
      const fields = Array.isArray(res) ? res : (Array.isArray(res.data) ? res.data : [])
      console.log('[FormFieldRenderer] 加载外部字段结果:', formId, 'count:', fields.length)
      externalFormFields.value = fields.map(f => ({
        fieldKey: f.fieldCode || f.fieldId || f.id,
        fieldName: f.fieldLabel || f.fieldName,
        fieldType: (f.componentType || f.fieldType || 'input').toLowerCase(),
        isEditable: true,
        isRequired: f.isRequired === 1,
        options: f.options
      }))
    } catch (e) {
      console.error('[FormFieldRenderer] 加载外部表单字段失败:', e)
      externalFormFields.value = []
    }
  } else {
    externalFormFields.value = []
  }
}, { immediate: true })

// 子表单配置转换
const subFormConfig = computed(() => {
  const field = props.field
  const isRef = subFormMeta.value.subFormType === 'ref' && externalFormFields.value.length > 0
  const fields = isRef
    ? externalFormFields.value
    : (field.subFields || field.fields || [])
  return {
    label: field.fieldName || '明细',
    fieldKey: field.fieldKey || 'detailList',
    required: field.required || false,
    minRows: field.minRows || 0,
    maxRows: field.maxRows || 100,
    fields,
    showSummary: field.showSummary || false,
    summaryFields: field.summaryFields || [],
    layout: isRef ? 'form' : 'table'
  }
})

const options = computed(() => {
  // 尝试从 componentProps 或 optionsJson 解析选项
  if (props.field.componentProps) {
    try {
      const props = JSON.parse(props.field.componentProps)
      if (props.options) return props.options
    } catch (e) {}
  }
  if (props.field.optionsJson) {
    try {
      return JSON.parse(props.field.optionsJson)
    } catch (e) {
      return []
    }
  }
  return []
})

const userOptions = ref([])

const searchUsers = (query) => {
  // 模拟用户搜索
  if (query) {
    userOptions.value = [
      { value: 'user1', label: `用户1 (${query})` },
      { value: 'user2', label: `用户2 (${query})` },
      { value: 'user3', label: `用户3 (${query})` }
    ]
  } else {
    userOptions.value = []
  }
}

// 设置默认值
watch(() => props.field.defaultValue, (val) => {
  if (val && !props.modelValue) {
    emit('update:modelValue', val)
  }
}, { immediate: true })

// 实体选择回调
const handleEntitySelect = (data) => {
  // 可以选择性地发射额外的事件或处理
  // 实体选择回调已处理
}

// 获取引用实体类型
const getRefEntityType = () => {
  // refEntityType: CUSTOM/USER/DEPT/ROLE/GROUP
  // 默认为 CUSTOM（用户自定义实体）
  return props.field.refEntityType || 'CUSTOM'
}

// 从 componentProps 或字段属性中获取数据接口URL
const getApiUrl = (field) => {
  if (field.componentProps) {
    try {
      const cp = typeof field.componentProps === 'string' ? JSON.parse(field.componentProps) : field.componentProps
      if (cp.refConfig?.apiUrl) return cp.refConfig.apiUrl
    } catch (e) {}
  }
  return field.apiUrl || null
}
</script>

<style scoped>
.form-field-renderer {
  width: 100%;
}
</style>
