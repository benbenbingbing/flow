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
import { formatListFieldValue, parseDataSourceConfig } from '@/shared/list-runtime'
import { safeParseConfig } from '@/shared/config-runtime'

const props = defineProps({
  value: { type: [String, Number, Boolean, Object, Array], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) }
})

// 解析数据源配置 JSON
const parsedConfig = computed(() => {
  const config = props.field?.renderConfig
    ? safeParseConfig(props.field.renderConfig)
    : parseDataSourceConfig(props.field?.dataSourceConfig)
  if (String(props.field?.fieldCode || '').toLowerCase() !== 'status') {
    return config
  }
  return {
    ...config,
    labelMap: {
      ...(config?.labelMap || {}),
      ...(props.context?.entityStatusMap || {})
    }
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

// 列表组件接收展示值；原始 ID 仍保留在 row 中供自定义组件按需读取。
const cellValue = computed(() => {
  return formatListFieldValue(
    props.row,
    props.field,
    props.context?.refEntityNameMap || {},
    props.context?.entityStatusMap || {}
  )
})
</script>
