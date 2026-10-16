<template>
  <div class="checkbox-field">
    <el-checkbox-group
      v-model="fieldValue"
      :disabled="isDisabled"
      v-on="customEventListeners"
      @change="handleChange"
    >
      <el-checkbox
        v-for="opt in currentOptions"
        :key="opt.value"
        :value="opt.value"
      >
        {{ opt.label }}
      </el-checkbox>
    </el-checkbox-group>
  </div>
</template>

<script setup>

// 仅声明此实现已接入的前端事件，供字段配置和运行时共用。
defineOptions({ scriptEvents: [
    { name: 'onChange', label: '值变化', description: '用户修改值后触发，完成后执行值变化事件链' }
] })
import { useFormField } from '@flow/workflow-core/vue/useFormField'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Array], default: () => [] },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, isDisabled, handleChange, customEventListeners, currentOptions } = useFormField(
  props,
  emit
)
</script>

<style scoped>
.checkbox-field {
  width: 100%;
}
</style>
