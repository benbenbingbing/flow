<template>
  <component
    :is="renderComponent"
    :value="cellValue"
    :row="row"
    :field="field"
    :config="parsedConfig"
  />
</template>

<script setup>
import { computed } from 'vue'
import { getCellComponent, hasCellComponent } from '@/utils/listCellRegistry.js'
import DefaultText from '@/components/list-cells/DefaultText.vue'

const props = defineProps({
  value: { type: [String, Number, Boolean, Object, Array], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) }
})

// 解析数据源配置 JSON
const parsedConfig = computed(() => {
  if (!props.field?.dataSourceConfig) return {}
  try {
    return JSON.parse(props.field.dataSourceConfig)
  } catch (e) {
    return {}
  }
})

// 确定渲染组件
const renderComponent = computed(() => {
  const componentName = props.field?.renderComponent
  if (componentName && hasCellComponent(componentName)) {
    return getCellComponent(componentName)
  }
  return DefaultText
})

// 确定单元格值：优先从 extData 取（自定义字段），否则从 data 或 row 顶层取
const cellValue = computed(() => {
  const fieldCode = props.field?.fieldCode
  if (!fieldCode) return props.value
  // 自定义字段数据在 extData 中
  if (props.row?.extData && fieldCode in props.row.extData) {
    return props.row.extData[fieldCode]
  }
  // 实体自定义字段在 data 中
  if (props.row?.data && fieldCode in props.row.data) {
    return props.row.data[fieldCode]
  }
  // 系统顶层字段
  if (props.row && fieldCode in props.row) {
    return props.row[fieldCode]
  }
  return props.value
})
</script>
