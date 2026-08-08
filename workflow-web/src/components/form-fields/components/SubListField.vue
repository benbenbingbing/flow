<template>
  <div class="sub-list-field">
    <el-alert
      v-if="!targetEntityCode || !targetListKey"
      type="warning"
      :closable="false"
      title="子列表尚未完成配置"
      description="请在表单设计器中选择目标实体的已发布列表。"
    />
    <el-alert
      v-else-if="parameterResolution.missingRequired.length > 0"
      type="info"
      :closable="false"
      title="等待主表单参数"
      :description="missingParameterDescription"
    />
    <EntityDataList
      v-else
      embedded
      :entity-code="targetEntityCode"
      :list-key="targetListKey"
      :scene="SUB_LIST_RUNTIME_SCENE"
      :context="runtimeContext"
      :fixed-filters="parameterResolution.queryFilters"
      :create-initial-data="parameterResolution.createValues"
      :create-context="createRuntimeContext"
      :show-search="config.showSearch"
      :show-pagination="config.showPagination"
      :show-toolbar="config.showToolbar"
      :show-row-actions="config.showRowActions"
      :page-size="config.pageSize"
      :max-height="config.maxHeight"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import EntityDataList from '@/views/entity/EntityDataList.vue'
import { safeParseConfig } from '@/shared/config-runtime'
import {
  normalizeSubListDisplayConfig,
  normalizeSubListParameterContract,
  resolveSubListParameterContract,
  SUB_LIST_RUNTIME_SCENE
} from '@/shared/sub-list'
import { buildSubFormParentContext } from '@/shared/subform-parameter-contract'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Array, Object, Boolean], default: null },
  disabled: { type: Boolean, default: false },
  context: { type: Object, default: () => ({}) }
})

const componentProps = computed(() =>
  safeParseConfig(props.field?.componentProps)
)

const config = computed(() =>
  normalizeSubListDisplayConfig({
    ...(componentProps.value.subListConfig || {}),
    ...(props.field?.subListConfig || {})
  })
)

const targetEntityCode = computed(() =>
  String(
    config.value.targetEntityCode
      || props.field?.refEntityCode
      || ''
  ).trim()
)

const targetListKey = computed(() =>
  String(
    config.value.listKey
      || props.field?.refListKey
      || ''
  ).trim()
)

const parameterContract = computed(() =>
  normalizeSubListParameterContract(
    config.value.parameterContract
  )
)

const parameterResolution = computed(() =>
  resolveSubListParameterContract(
    parameterContract.value,
    props.context
  )
)

const missingParameterDescription = computed(() => {
  const names = parameterResolution.value.missingRequired
    .map(item => item.targetFieldName || item.targetField)
    .join('、')
  return `请先填写或保存主表单中的参数：${names}。为避免误查全部数据，参数就绪前不会加载子列表。`
})

const runtimeContext = computed(() => {
  const record = props.context?.record || {}
  const recordData = record?.data && typeof record.data === 'object'
    ? record.data
    : record
  return {
    ...(config.value.context || {}),
    ...(props.context?.listContext || {}),
    sourceEntityCode: props.context?.entityCode || '',
    sourceRecordId:
      props.context?.recordId
      || record?.id
      || recordData?.id
      || null,
    relationKey: config.value.relationKey || undefined,
    parameters: {
      ...(config.value.context?.parameters || {}),
      ...(props.context?.listContext?.parameters || {}),
      ...parameterResolution.value.parameters
    }
  }
})

const createRuntimeContext = computed(() => ({
  parent: buildSubFormParentContext(props.context),
  context: props.context,
  params: parameterResolution.value.parameters,
  parameters: parameterResolution.value.parameters,
  sourceEntityCode: runtimeContext.value.sourceEntityCode,
  sourceRecordId: runtimeContext.value.sourceRecordId
}))
</script>

<style scoped>
.sub-list-field {
  width: 100%;
  min-width: 0;
}
</style>
