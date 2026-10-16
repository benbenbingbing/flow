import { computed, onBeforeUnmount, shallowRef, watch } from 'vue'
import { cascaderPath, createCascaderRuntime, readCascaderConfig } from '../shared/cascader-runtime.js'
import { getFormDataSourceBindings } from '../shared/form-runtime/dataSourceRuntime.js'

/** 序列化仅用于本地缓存失效；忽略函数和循环引用，不把其作为任何权限凭据。 */
function fingerprint(value) {
  const seen = new WeakSet()
  return JSON.stringify(value, (_key, item) => {
    if (typeof item === 'function') return undefined
    if (item && typeof item === 'object') {
      if (seen.has(item)) return undefined
      seen.add(item)
    }
    return item
  })
}

/** 字段组件只负责交互；配置、记录、依赖字段变更及已保存路径的回显由两端共用。 */
export function useCascaderFieldRuntime(props) {
  const state = shallowRef({ options: [], loading: false, error: '', revision: 0 })
  const runtime = createCascaderRuntime({ onChange: next => { state.value = next } })
  const config = computed(() => readCascaderConfig(props.field))
  const path = computed(() => cascaderPath(props.modelValue))
  const isLazy = computed(() => config.value.source === 'INTERFACE' && config.value.loadMode === 'CHILDREN')

  function requestContext() {
    const context = props.context || {}
    return {
      ...context,
      form: context.form,
      record: context.getFormData?.() || context.formData || context.record || {},
      context
    }
  }

  // 当前字段值只触发路径回显；其它业务字段可能参与接口 inputMapping，变化时必须清缓存。
  const dependencyKey = computed(() => {
    const context = requestContext()
    const fieldCode = props.field.fieldCode || props.field.fieldKey
    const record = { ...context.record }
    delete record[fieldCode]
    // 校验状态、组件实例和回调都不属于接口输入，不能让其变化造成循环加载。
    const businessContext = Object.fromEntries([
      'mode', 'recordId', 'taskId', 'processInstanceId', 'nodeId', 'rootFormId', 'rootRecordId',
      'componentPath', 'entityCode', 'runtimeContext', 'compositionContext', 'params', 'parameters',
      'parent', 'relation', 'initializationKey'
    ].map(key => [key, context[key]]))
    const row = context.row ? { ...context.row } : undefined
    if (row?.data) { row.data = { ...row.data }; delete row.data[fieldCode] }
    if (row) delete row[fieldCode]
    // 显式映射允许读取宿主的业务扩展键，这些键也必须进入依赖快照。
    const mappingInputs = getFormDataSourceBindings(props.field, 'FIELD_OPTIONS').flatMap(binding =>
      Object.values(binding.inputMapping || {}).filter(selector => typeof selector === 'string' && selector.startsWith('context.'))
        .map(selector => selector.slice('context.'.length).split('.').filter(Boolean)
          .reduce((current, key) => current?.[key], context.context)))
    return fingerprint({ field: props.field, context: businessContext, row, record, mappingInputs,
      form: { id: context.form?.id, formId: context.form?.formId, configHash: context.form?.configHash,
        runtimeContext: context.form?.runtimeContext } })
  })

  async function restore() {
    try { await runtime.ensurePath(path.value, { requireLeaf: true }) } catch { /* 错误保留在 state，字段值始终交还宿主管理。 */ }
  }
  watch([dependencyKey, () => props.dataSourceRuntime], () => {
    const bindings = getFormDataSourceBindings(props.field, 'FIELD_OPTIONS')
    runtime.configure({
      ...config.value,
      loadOptions: async cascader => {
        if (bindings.length !== 1) throw new Error('级联字段必须配置一个 FIELD_OPTIONS 数据接口')
        if (typeof props.dataSourceRuntime?.loadOptions !== 'function') throw new Error('当前页面未提供级联数据接口执行能力')
        const context = requestContext()
        return props.dataSourceRuntime.loadOptions(props.field, {
          ...context, input: { ...(context.input || {}), cascader }
        })
      }
    })
    restore()
  }, { immediate: true })
  watch(path, restore, { deep: true })
  onBeforeUnmount(() => runtime.configure({ source: 'STATIC', options: [] }))

  return {
    options: computed(() => state.value.options), loading: computed(() => state.value.loading),
    error: computed(() => state.value.error), revision: computed(() => state.value.revision), isLazy,
    loadChildren: runtime.loadChildren, ensurePath: runtime.ensurePath,
    // resolvePath 内部是纯 JS 状态；读取 ref 使两端标签 computed 随异步回显刷新。
    resolvePath: value => { state.value; return runtime.resolvePath(value) },
    clearError: runtime.clearError,
    retry: async () => { await runtime.retry(); await restore() }
  }
}
