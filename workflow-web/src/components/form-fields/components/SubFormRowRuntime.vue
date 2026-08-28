<template>
  <FormNodeRenderer
    ref="nodeFormRef"
    :nodes="nodes"
    :root-parent-id="rootParentId"
    :fields="fields"
    :model-value="row"
    :readonly="readonly"
    :mode="mode"
    :context="context"
    :data-source-runtime="dataSourceRuntime"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>

<script setup>
import { provide, ref, watch } from 'vue'
import FormNodeRenderer from '@/components/FormNodeRenderer.vue'
import { precheckFormFieldUnique } from '@/api/entityForm'
import {
  createFormUniquePrecheckController,
  resolveFormFieldUniqueness,
  resolveFormUniqueRuntimeIdentity
} from '@/shared/form-field-uniqueness'
import { FORM_UNIQUE_PRECHECK_CONTEXT_KEY } from '@/shared/form-runtime/uniquePrecheckContext'

const props = defineProps({
  form: { type: Object, required: true },
  fields: { type: Array, default: () => [] },
  nodes: { type: Array, default: () => [] },
  row: { type: Object, required: true },
  rootParentId: { type: [String, Number], default: '' },
  readonly: { type: Boolean, default: false },
  mode: { type: String, default: 'edit' },
  context: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null }
})

defineEmits(['update:modelValue'])

const nodeFormRef = ref(null)
const uniqueErrors = ref({})
const uniquePrecheckController = createFormUniquePrecheckController({
  request: precheckFormFieldUnique,
  getIdentity: () => resolveFormUniqueRuntimeIdentity(
    props.form,
    {
      ...props.context,
      recordId: props.row?.id || props.context?.recordId,
      record: {
        id: props.row?.id || props.context?.record?.id || null,
        data: props.row
      }
    }
  ),
  onErrorsChange: errors => {
    uniqueErrors.value = errors
  }
})

// 覆盖父表 provide：子节点的 BLUR 校验必须使用子表发布身份和当前行数据。
provide(FORM_UNIQUE_PRECHECK_CONTEXT_KEY, {
  resolveRule: resolveFormFieldUniqueness,
  check: (field, reason) => uniquePrecheckController.check(
    field,
    props.row,
    { reason }
  ),
  errorFor: fieldCode => uniqueErrors.value[String(fieldCode || '')] || ''
})

watch(
  () => props.row,
  row => uniquePrecheckController.handleRecordChange(
    props.fields,
    row || {}
  ),
  { deep: true, immediate: true }
)

watch(
  () => [
    props.form?.id,
    props.form?.runtimeReleaseId || props.form?.formReleaseId,
    props.form?.runtimeReleaseVersion ?? props.form?.formReleaseVersion,
    props.form?.releaseResolutionToken,
    props.row?.id
  ],
  () => uniquePrecheckController.reset(props.row)
)

/**
 * 先强制刷新当前子表发布版的提交预检，再执行子行常规字段/嵌套子表校验。
 * Element Form 会在整表校验时再执行 BLUR 规则；如果先跑它，历史“重复”
 * 结果可能拦住本次 SUBMIT 强制预检，导致冲突已解除仍无法提交。
 */
async function validate() {
  const uniqueResult = await uniquePrecheckController.checkAll(
    props.fields,
    () => props.row
  )
  if (!uniqueResult.valid) {
    await nodeFormRef.value?.revealValidationField?.(
      Object.keys(uniqueErrors.value)[0] || ''
    )
    return false
  }
  return (await nodeFormRef.value?.validate()) !== false
}

defineExpose({ validate })
</script>
