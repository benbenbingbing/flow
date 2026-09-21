import { onScopeDispose, ref, watch } from 'vue'
import { createCustomValidationController } from '../shared/form-custom-validation-runtime.js'
import { resolveCrossFieldRuntimeState } from '../shared/form-cross-field-runtime.js'
import { getCustomValidationConfig } from '../shared/form-custom-validation.js'

/** fields-only 子表每行独立校验；节点子表由 SubFormRowRuntime 自己负责。 */
export function useSubFormCustomValidation(options) {
  const entries = new Map()
  const errors = ref({})
  const configuredFields = () => options.getFields().filter(field => {
    try { return getCustomValidationConfig(field) !== undefined } catch { return true }
  }).map(field => ({ ...field, fieldCode: field.fieldCode || field.fieldKey, fieldType: field.validationFieldType || field.fieldType }))
  function sync() {
    const rows = options.enabled() ? options.getRows() : []
    const fields = configuredFields()
    for (const [index, entry] of entries) {
      if (index >= rows.length || !fields.length) {
        entry.controller.dispose()
        entries.delete(index)
      }
    }
    if (!fields.length) return
    rows.forEach((row, index) => {
      let entry = entries.get(index)
      if (!entry) {
        entry = { row, identity: '', errors: {} }
        entry.controller = createCustomValidationController({
          getFields: configuredFields, getRecord: () => entry.row,
          getContext: () => {
            const raw = options.getContext(entry.row, index)
            return { entityCode: raw.entityCode || '', form: options.getForm(), mode: raw.mode,
              record: { id: entry.row?.id || null }, row: raw.row, parent: raw.parent, pageParams: raw.params }
          },
          getState: field => resolveCrossFieldRuntimeState(field, { form: options.getForm(), record: entry.row,
            mode: options.getContext(entry.row, index).mode, readonly: options.getReadonly(), context: options.getContext(entry.row, index) }),
          onErrorsChange: value => {
            entry.errors = value
            errors.value = Object.fromEntries([...entries].flatMap(([position, item]) =>
              Object.entries(item.errors).map(([code, error]) => [`${position}:${code}`, error.message])))
          }
        })
        entries.set(index, entry)
      }
      entry.row = row
      const form = options.getForm()
      const identity = JSON.stringify([form.id, form.runtimeReleaseId, form.runtimeReleaseVersion, row?.id, options.getContext(row, index).entityCode])
      if (identity !== entry.identity) { entry.controller.reset(); entry.identity = identity }
      entry.controller.refresh()
    })
  }
  watch(() => [options.enabled(), options.getRows(), options.getFields(), options.getForm(), options.getContext({}, -1), options.getReadonly()], sync, { deep: true, immediate: true, flush: 'post' })
  onScopeDispose(() => entries.forEach(entry => entry.controller.dispose()))
  return {
    errorFor: (index, field) => errors.value[`${index}:${field.fieldCode || field.fieldKey}`] || '',
    async check(payload, trigger) {
      sync()
      return entries.get(Number(payload?.index))?.controller.check(payload?.field?.fieldCode || payload?.field?.fieldKey, trigger)
    },
    async validate() {
      sync()
      const results = await Promise.all([...entries.values()].map(entry => entry.controller.validate()))
      return { valid: results.every(result => result.valid), message: results.find(result => !result.valid)?.message || '' }
    }
  }
}
