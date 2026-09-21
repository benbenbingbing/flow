import { computed, getCurrentInstance, nextTick, onScopeDispose, provide, ref, watch } from 'vue'
import { collectCrossFieldRuntimeFields, resolveCrossFieldRuntimeState } from '../shared/form-cross-field-runtime.js'
import { createCustomValidationController } from '../shared/form-custom-validation-runtime.js'
import { CUSTOM_VALIDATION_CONTEXT_KEY } from '../shared/form-custom-validation.js'

/**
 * 将校验控制器接入标准表单、自定义整表单和子表行。复用发布字段投影和权限判断，
 * 子表提供自己的实体/记录上下文，不继承父表字段的绑定或异步错误。
 */
export function useFormCustomValidation(options) {
  const errors = ref({})
  const submissionError = ref('')
  const fields = computed(() => collectCrossFieldRuntimeFields(options.getForm() || {}, options.getEntityFields?.() || [], {
    rootParentId: options.getRootParentId?.() || ''
  }))
  const runtimeOptions = () => ({
    form: options.getForm() || {}, record: options.getRecord() || {}, mode: options.getMode(),
    readonly: options.getReadonly?.() || false, context: options.getContext?.() || {},
    rootParentId: options.getRootParentId?.() || '', excludedNodeIds: options.getExcludedNodeIds?.() || []
  })
  const context = () => {
    const runtime = runtimeOptions()
    const raw = runtime.context
    return {
      entityCode: options.getEntityCode?.() || raw.entityCode || runtime.form.entityCode || '',
      mode: runtime.mode, form: runtime.form,
      record: { id: raw.recordId || raw.record?.id || null },
      parent: raw.parent || {}, row: raw.row || {}, pageParams: raw.params || raw.parameters || {}
    }
  }
  const controller = createCustomValidationController({
    getFields: () => fields.value, getRecord: options.getRecord, getContext: context,
    getState: field => resolveCrossFieldRuntimeState(field, runtimeOptions()),
    onErrorsChange: value => { errors.value = value }
  })
  const runtime = {
    get errors() { return errors.value },
    checkField: controller.check,
    onFieldBlur: field => controller.check(field, 'BLUR'),
    errorFor: field => errors.value[typeof field === 'string' ? field : field?.fieldCode]?.message || ''
  }
  /** 自定义整表单只发 model 更新时，按实际变化字段触发 CHANGE；等待宿主赋值落地。 */
  async function touchChanged(before, after) {
    const changed = fields.value.filter(field => JSON.stringify(before?.[field.fieldCode]) !== JSON.stringify(after?.[field.fieldCode]))
    await nextTick()
    await Promise.all(changed.map(field => controller.check(field, 'CHANGE')))
  }
  if (getCurrentInstance()) provide(CUSTOM_VALIDATION_CONTEXT_KEY, runtime)
  watch([
    () => options.getForm()?.id,
    () => options.getForm()?.runtimeReleaseId || options.getForm()?.formReleaseId,
    () => options.getForm()?.runtimeReleaseVersion || options.getForm()?.formReleaseVersion,
    () => context().entityCode,
    () => options.getContext?.()?.recordId || options.getContext?.()?.record?.id,
    () => options.getContext?.()?.initializationKey
  ], () => { controller.reset(); submissionError.value = '' })
  watch(() => [fields.value, runtimeOptions(), context()], () => controller.refresh(), { deep: true, flush: 'post' })
  onScopeDispose(() => controller.dispose())
  return {
    errors, runtime, touchChanged, submissionError,
    firstError: computed(() => submissionError.value || Object.values(errors.value)[0]?.message || ''),
    async validate() {
      await nextTick()
      submissionError.value = ''
      const result = await controller.validate()
      submissionError.value = result.message
      return result
    }
  }
}
