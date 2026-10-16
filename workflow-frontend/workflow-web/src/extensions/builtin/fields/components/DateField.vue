<template>
  <div class="date-field">
    <el-date-picker
      v-model="fieldValue"
      :type="pickerType"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :value-format="valueFormat"
      :format="displayFormat"
      style="width: 100%"
      clearable
      v-on="customEventListeners"
      @change="handleChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
  </div>
</template>

<script setup>

// 仅声明此实现已接入的前端事件，供字段配置和运行时共用。
defineOptions({ scriptEvents: [
    { name: 'onChange', label: '值变化', description: '用户修改值后触发，完成后执行值变化事件链' },
    { name: 'onFocus', label: '聚焦', description: '控件获得焦点时触发' },
    { name: 'onBlur', label: '失焦', description: '控件失去焦点时触发' },
    { name: 'onClear', label: '清空', description: '点击清空按钮后触发' },
    { name: 'onVisibleChange', label: '面板显隐', description: '选择面板打开或关闭时触发', parameters: [{ name: 'visible', type: 'boolean', description: '面板是否显示' }] },
    { name: 'onPanelChange', label: '切换日期面板', description: '切换日期面板时触发', parameters: [
      { name: 'date', type: 'string', description: '面板日期，ISO 格式' },
      { name: 'mode', type: 'string', description: '选择模式' },
      { name: 'view', type: 'string', description: '当前面板视图' }
    ] }
] })
import { computed } from 'vue'
import { useFormField } from '@flow/workflow-core/vue/useFormField'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Date], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, placeholder, isDisabled, handleChange, handleBlur, handleFocus, customEventListeners } =
  useFormField(props, emit, { eventPayloads: {
    onVisibleChange: visible => ({ visible }),
    onPanelChange: (date, mode, view) => ({ date: date?.toISOString?.() || '', mode: String(mode || ''), view: String(view || '') })
  } })

const pickerType = computed(() => {
  const type = (props.field?.componentType || props.field?.fieldType || '').toLowerCase()
  return type === 'datetime' ? 'datetime' : 'date'
})

const valueFormat = computed(() => {
  return pickerType.value === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'
})

const displayFormat = computed(() => {
  return valueFormat.value
})
</script>

<style scoped>
.date-field {
  width: 100%;
}
</style>
