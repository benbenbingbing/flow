import { computed, nextTick, ref, watch } from 'vue'
import { collectCrossFieldRuntimeFields, createCrossFieldController, resolveCrossFieldRuntimeState } from '@/shared/form-cross-field-runtime'

/** 为标准/自定义表单提供同一套跨字段校验，输入均为 getter，始终读取最新记录和权限状态。 */
export function useFormCrossFieldValidation(options) {
  const errors = ref({})
  const fields = computed(() => collectCrossFieldRuntimeFields(options.getForm() || {}, options.getEntityFields?.() || []))
  const runtimeOptions = () => ({
    form: options.getForm() || {},
    record: options.getRecord() || {},
    mode: options.getMode(),
    readonly: options.getReadonly?.() || false,
    context: options.getContext?.() || {},
    rootParentId: options.getRootParentId?.() || '',
    excludedNodeIds: options.getExcludedNodeIds?.() || []
  })
  const controller = createCrossFieldController({
    getFields: () => fields.value,
    getRecord: () => options.getRecord() || {},
    getState: field => resolveCrossFieldRuntimeState(field, runtimeOptions()),
    onErrorsChange: value => { errors.value = value }
  })
  watch(() => [options.getForm()?.id, options.getForm()?.runtimeReleaseId || options.getForm()?.formReleaseId,
    options.getContext?.()?.recordId || options.getContext?.()?.record?.id, options.getContext?.()?.initializationKey],
  () => controller.reset())
  watch(() => [fields.value, runtimeOptions()], () => controller.refresh(), { deep: true, flush: 'post' })
  return {
    errors,
    firstError: computed(() => Object.values(errors.value)[0]?.message || ''),
    errorFor: field => errors.value[typeof field === 'string' ? field : field.fieldCode]?.message || '',
    touch: controller.touch,
    /** 对组件一次提交的字段对象做差异检查；初始化同步不走此入口。 */
    touchChanged(before = {}, after = {}) {
      controller.touch([...new Set([...Object.keys(before), ...Object.keys(after)])]
        .filter(code => JSON.stringify(before[code]) !== JSON.stringify(after[code])))
    },
    async validate() { await nextTick(); return controller.validate() },
    applyServerErrors: controller.applyServerErrors
  }
}
