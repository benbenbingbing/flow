<template>
  <div class="select-field">
    <el-select
      v-model="fieldValue"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :multiple="isMultiple"
      :clearable="!isMultiple"
      style="width: 100%"
      v-on="customEventListeners"
      @change="handleChange"
      @blur="handleBlur"
      @focus="handleFocus"
    >
      <el-option
        v-for="opt in currentOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
  </div>
</template>

<script setup>

// 仅声明此实现已接入的前端事件，供字段配置和运行时共用。
defineOptions({ scriptEvents: [
    { name: 'onChange', label: '值变化', description: '用户修改值后触发，完成后执行值变化事件链' },
    { name: 'onFocus', label: '聚焦', description: '控件获得焦点时触发' },
    { name: 'onBlur', label: '失焦', description: '控件失去焦点时触发' },
    { name: 'onClear', label: '清空', description: '点击清空按钮后触发', variants: ['select'] },
    { name: 'onVisibleChange', label: '面板显隐', description: '选择面板打开或关闭时触发', parameters: [{ name: 'visible', type: 'boolean', description: '面板是否显示' }] },
    { name: 'onRemoveTag', label: '移除标签', description: '移除已选标签时触发', variants: ['select_multiple'], parameters: [{ name: 'value', type: 'any', description: '被移除的选项值' }] }
] })
import { computed } from 'vue'
import { useFormField } from '@flow/workflow-core/vue/useFormField'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Array], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const {
  fieldValue,
  placeholder,
  isDisabled,
  handleChange,
  handleBlur,
  handleFocus,
  customEventListeners,
  currentOptions
} = useFormField(props, emit, { eventPayloads: {
  onVisibleChange: visible => ({ visible }), onRemoveTag: value => ({ value })
} })

const isMultiple = computed(() => {
  const type = (props.field?.componentType || props.field?.fieldType || '').toLowerCase()
  return type === 'select_multiple' || type === 'multi_select'
})
</script>

<style scoped>
.select-field {
  width: 100%;
}
</style>
