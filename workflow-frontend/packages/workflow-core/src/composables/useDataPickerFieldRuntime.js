import { computed, onBeforeUnmount, shallowRef, watch } from 'vue'
import { safeParseConfig } from '../shared/config-runtime/index.js'
import { getFormDataSourceBindings } from '../shared/form-runtime/dataSourceRuntime.js'
import { createDataPickerRuntime, preparePickerPatch } from '../shared/data-picker.js'

/**
 * 让 PC、移动端共用字段的查询、回显和回填生命周期；组件仅展示会话并发出一次 patch-form。
 * 当前记录和绑定参数变化会撤销旧面板，异步确认后也不能把上一记录的数据写入新表单。
 */
export function useDataPickerFieldRuntime(props, emit, confirmOverwrite) {
  const state = shallowRef({})
  const runtime = createDataPickerRuntime({ onChange: value => { state.value = value } })
  const config = computed(() => safeParseConfig(props.field.componentProps))
  const record = () => props.context?.getFormData?.() || props.formData || props.context?.formData || props.context?.record?.data || props.context?.record || {}
  const disabled = () => props.disabled || props.readonly
  // 只保存普通业务对象，宿主函数、组件实例和校验状态不参与失效判断。
  const identity = computed(() => {
    const context = props.context || {}
    const mappingValues = ['FIELD_PICKER_SCHEMA', 'FIELD_PICKER_QUERY', 'FIELD_PICKER_RESOLVE'].flatMap(usage =>
      getFormDataSourceBindings(props.field, usage).flatMap(binding => Object.values(binding.inputMapping || {}))
        .filter(value => typeof value === 'string' && value.startsWith('context.'))
        .map(value => value.slice(8).split('.').reduce((current, key) => current?.[key], context.context)))
    const seen = new WeakSet()
    return JSON.stringify({ field: props.field, record: record(), mode: props.mode, mappingValues,
      form: { id: context.form?.id, configHash: context.form?.configHash },
      scope: Object.fromEntries(['recordId', 'taskId', 'processInstanceId', 'initializationKey', 'params', 'parameters', 'parent', 'row', 'relation', 'runtimeContext', 'compositionContext']
        .map(key => [key, context[key]])) }, (_key, value) => {
      if (typeof value === 'function') return undefined
      if (value && typeof value === 'object') { if (seen.has(value)) return undefined; seen.add(value) }
      return value
    })
  })
  async function restore() { await runtime.restore(props.modelValue) }
  watch([identity, () => props.dataSourceRuntime], () => {
    runtime.configure({ schema: config.value.pickerSchema, pageSize: config.value.pickerPageSize || 10,
      load: async (usage, picker) => {
        const bindings = getFormDataSourceBindings(props.field, usage)
        if (bindings.length !== 1) throw new Error(`数据选择器必须配置一个 ${usage} 接口`)
        if (!props.dataSourceRuntime?.executeOwnerUsage) throw new Error('当前页面未提供数据接口执行能力')
        const context = props.context || {}
        const results = await props.dataSourceRuntime.executeOwnerUsage(props.field, usage, {
          ...context, form: context.form, record: record(), context,
          input: { picker }
        })
        // executeOwnerUsage 已处理 transport 与输出映射，不再按业务字段 data 猜测外层包装。
        return results[0]
      } })
    restore()
  }, { immediate: true })
  watch(() => props.modelValue, restore)
  watch(() => disabled(), value => { if (value) runtime.close() })
  onBeforeUnmount(() => runtime.configure({ schema: null }))

  async function commit(row) {
    if (disabled()) return
    const patch = await runtime.confirm(row, (selection, isCurrent) =>
      preparePickerPatch(props.field, selection, record(), confirmOverwrite, () => isCurrent() && !disabled()))
    if (!patch || disabled()) return
    emit('patch-form', patch)
    runtime.close()
  }
  async function clear() {
    if (disabled() || state.value.confirming) return
    runtime.close()
    const previousIdentity = identity.value
    const patch = await preparePickerPatch(props.field, null, record(), confirmOverwrite,
      () => !disabled() && previousIdentity === identity.value)
    if (patch) emit('patch-form', patch)
  }
  return { state, config, open: () => !disabled() && runtime.open(), close: runtime.close, query: runtime.query,
    commit, clear, retry: restore,
    label: computed(() => state.value.selected?.label || (props.modelValue == null ? '' : String(props.modelValue))),
    validate: () => disabled() || (!state.value.loading && !state.value.confirming && !state.value.resolving
      && Boolean(state.value.schema) && !state.value.restoreError) }
}
