<template>
  <component
    :is="renderComponent"
    :value="cellValue"
    :row="row"
    :field="field"
    :config="parsedConfig"
    :context="context"
  />
</template>

<script setup>
import { computed } from 'vue'
import { getCellComponent, hasCellComponent } from '@/utils/listCellRegistry.js'
import DefaultText from '@/components/list-cells/DefaultText.vue'
import { getCellValue, parseDataSourceConfig } from '@/shared/list-runtime'
import { safeParseConfig } from '@/shared/config-runtime'

const props = defineProps({
  value: { type: [String, Number, Boolean, Object, Array], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) }
})

// 解析数据源配置 JSON
const parsedConfig = computed(() => {
  return props.field?.renderConfig
    ? safeParseConfig(props.field.renderConfig)
    : parseDataSourceConfig(props.field?.dataSourceConfig)
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
  return getCellValue(props.row, props.field, props.value)
})
</script>
