<template>
  <span v-if="maskingEnabled">{{ maskedValue }}</span>
  <component
    v-else
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
import { getCellComponent, hasCellComponent } from '@/extensions/core/registries/listCellRegistry.js'
import DefaultText from '@/extensions/builtin/list-cells/DefaultText.vue'
import { formatListFieldValue, parseDataSourceConfig } from '@/shared/list-runtime'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { formatMaskedListCellValue, isListCellMaskingEnabled } from '@/shared/list-cell-masking'

const props = defineProps({
  value: { type: [String, Number, Boolean, Object, Array], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) }
})

// 脱敏统一在扩展之前处理，防止自定义渲染组件重新读取 row 中的原始内容。
const maskingEnabled = computed(() => isListCellMaskingEnabled(props.field))
const maskedValue = computed(() => formatMaskedListCellValue(
  props.row, props.field, props.context?.refEntityNameMap || {}, props.context?.entityStatusMap || {}
))

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
