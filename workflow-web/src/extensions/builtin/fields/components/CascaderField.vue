<template>
  <div class="cascader-field">
    <el-cascader
      v-model="fieldValue"
      :options="cascaderOptions"
      :placeholder="placeholder"
      :disabled="isDisabled"
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
import { computed } from 'vue'
import { useFormField } from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Array], default: () => [] },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, placeholder, isDisabled, handleChange, handleBlur, handleFocus, customEventListeners, parsedComponentProps } =
  useFormField(props, emit)

const cascaderOptions = computed(() => {
  if (parsedComponentProps.value.cascaderOptions) {
    return parsedComponentProps.value.cascaderOptions
  }
  if (props.field?.cascaderOptions) {
    return props.field.cascaderOptions
  }
  return getDefaultRegionData()
})

function getDefaultRegionData() {
  return [
    {
      value: 'beijing',
      label: '北京',
      children: [
        { value: 'dongcheng', label: '东城区' },
        { value: 'xicheng', label: '西城区' },
        { value: 'chaoyang', label: '朝阳区' },
        { value: 'haidian', label: '海淀区' }
      ]
    },
    {
      value: 'shanghai',
      label: '上海',
      children: [
        { value: 'huangpu', label: '黄浦区' },
        { value: 'xuhui', label: '徐汇区' },
        { value: 'changning', label: '长宁区' },
        { value: 'jingan', label: '静安区' }
      ]
    },
    {
      value: 'guangdong',
      label: '广东',
      children: [
        {
          value: 'guangzhou',
          label: '广州',
          children: [
            { value: 'tianhe', label: '天河区' },
            { value: 'yuexiu', label: '越秀区' },
            { value: 'liwan', label: '荔湾区' }
          ]
        },
        {
          value: 'shenzhen',
          label: '深圳',
          children: [
            { value: 'futian', label: '福田区' },
            { value: 'nanshan', label: '南山区' },
            { value: 'luohu', label: '罗湖区' }
          ]
        }
      ]
    },
    {
      value: 'zhejiang',
      label: '浙江',
      children: [
        {
          value: 'hangzhou',
          label: '杭州',
          children: [
            { value: 'gongshu', label: '拱墅区' },
            { value: 'xihu', label: '西湖区' },
            { value: 'binjiang', label: '滨江区' }
          ]
        }
      ]
    }
  ]
}
</script>

<style scoped>
.cascader-field {
  width: 100%;
}
</style>
