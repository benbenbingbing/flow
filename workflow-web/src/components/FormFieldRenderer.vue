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
    
    <!-- 下拉选择 -->
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
      v-else-if="renderType === 'sub_form' || renderType === 'sub_form_list'"
      :ref-entity-id="field.refEntityId"
      :display-mode="field.displayMode || 'embedded'"
      :sub-form-type="field.fieldType"
      :title="field.fieldName"
      v-model="fieldValue"
      :disabled="disabled"
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
    if (renderType.value === 'checkbox' && !Array.isArray(props.modelValue)) {
      return props.modelValue ? [props.modelValue] : []
    }
    return props.modelValue
  },
  set(val) {
    emit('update:modelValue', val)
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
</script>

<style scoped>
.form-field-renderer {
  width: 100%;
}
</style>
