import { uiDataSourceApi } from '@/api/uiConfig'
import { safeParseConfig } from '@/shared/config-runtime'

function parseBindings(value) {
  return safeParseConfig(value) || {}
}

function normalizeBinding(binding, usage) {
  if (!binding) return null
  if (typeof binding === 'string') {
    return { sourceId: binding, usage }
  }
  return {
    ...binding,
    sourceId: binding.sourceId || binding.id,
    usage: binding.usage || usage
  }
}

function bindingsFor(owner, usage) {
  const bindings = parseBindings(
    owner?.dataSourceBindingsDocument || owner?.dataSourceBindings
  )
  const configured = bindings?.[usage]
  if (!configured) return []
  return (Array.isArray(configured) ? configured : [configured])
    .map(binding => normalizeBinding(binding, usage))
    .filter(binding => binding?.sourceId)
}

export function isClientPrevalidationBinding(binding) {
  return Boolean(
    binding
    && typeof binding === 'object'
    && binding.sideEffectFree === true
    && binding.clientPrevalidate === true
  )
}

export function getClientBeforeSubmitBindings(owner) {
  return bindingsFor(owner, 'BEFORE_SUBMIT')
    .filter(isClientPrevalidationBinding)
}

function mergeObject(target, result) {
  const value = result?.data ?? result
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    Object.assign(target, value)
  }
  return value
}

function resolvePath(source, path) {
  return String(path || '').split('.').filter(Boolean)
    .reduce((current, key) => current?.[key], source)
}

function setPath(target, path, value) {
  const parts = String(path || '').split('.').filter(Boolean)
  if (!parts.length) return
  let current = target
  parts.slice(0, -1).forEach(key => {
    if (!current[key] || typeof current[key] !== 'object') current[key] = {}
    current = current[key]
  })
  current[parts.at(-1)] = value
}

function applyMapping(mapping, source, fallback) {
  if (!mapping || typeof mapping !== 'object' || Array.isArray(mapping)
    || Object.keys(mapping).length === 0) return fallback
  const result = {}
  Object.entries(mapping).forEach(([targetPath, selector]) => {
    const value = selector && typeof selector === 'object'
      && Object.prototype.hasOwnProperty.call(selector, 'literal')
      ? selector.literal
      : resolvePath(source, selector)
    setPath(result, targetPath, value)
  })
  return result
}

export function createFormDataSourceRuntime(options) {
  const initialized = new Set()

  function currentRecord() {
    return options.getRecord?.() || {}
  }

  function baseContext(runtimeContext = {}) {
    const form = runtimeContext.form || options.getForm?.()
    return {
      mode: options.getMode?.() || 'view',
      formId: runtimeContext.formId || form?.id,
      entityId: runtimeContext.entityId
        || form?.entityId
        || options.getEntityDefinition?.()?.id
    }
  }

  async function execute(binding, runtimeContext = {}) {
    const normalized = normalizeBinding(binding, runtimeContext.usage)
    if (!normalized?.sourceId) {
      throw new Error('数据源绑定缺少 sourceId')
    }
    const usage = runtimeContext.usage || normalized.usage
    if (usage === 'BEFORE_SUBMIT' && !isClientPrevalidationBinding(normalized)) {
      throw new Error(
        '浏览器禁止执行普通 BEFORE_SUBMIT；仅允许同时标记 sideEffectFree=true 和 clientPrevalidate=true 的预校验绑定'
      )
    }
    const record = runtimeContext.record || currentRecord()
    const context = {
      ...baseContext(runtimeContext),
      ...normalized.context,
      ...runtimeContext.context
    }
    const rawInput = {
      recordId: runtimeContext.recordId ?? options.getRecordId?.(),
      data: record,
      ...normalized.input,
      ...runtimeContext.input
    }
    const input = applyMapping(
      normalized.inputMapping,
      { data: record, context, input: rawInput },
      rawInput
    )
    const executeDataSource = options.executeDataSource || uiDataSourceApi.execute
    const response = await executeDataSource(normalized.sourceId, {
      usage,
      entityCode: options.entityCode,
      listKey: options.getListKey?.(),
      input,
      context
    })
    return applyMapping(
      normalized.outputMapping,
      { data: response?.data ?? response, response },
      response
    )
  }

  async function executeOwnerUsage(owner, usage, runtimeContext = {}) {
    const results = []
    for (const binding of bindingsFor(owner, usage)) {
      results.push(await execute(binding, {
        ...runtimeContext,
        usage
      }))
    }
    return results
  }

  async function initialize({
    form,
    fields = [],
    nodes = [],
    record: explicitRecord,
    recordId,
    initializationKey: explicitInitializationKey
  }) {
    const initializationKey = explicitInitializationKey || [
      form?.id || 'form',
      recordId ?? options.getRecordId?.() ?? 'new',
      options.getMode?.() || 'view'
    ].join(':')
    if (initialized.has(initializationKey)) return
    initialized.add(initializationKey)

    const record = explicitRecord || currentRecord()
    const runtimeContext = {
      form,
      record,
      recordId
    }
    try {
      for (const result of await executeOwnerUsage(
        form,
        'FORM_INIT',
        runtimeContext
      )) {
        mergeObject(record, result)
      }
      for (const result of await executeOwnerUsage(
        form,
        'AFTER_LOAD',
        runtimeContext
      )) {
        mergeObject(record, result)
      }
      for (const owner of [...fields, ...nodes]) {
        for (const result of await executeOwnerUsage(
          owner,
          'AFTER_LOAD',
          runtimeContext
        )) {
          mergeObject(record, result)
        }
      }
      for (const field of fields) {
        const fieldCode = field.fieldCode || field.fieldKey
        if (!fieldCode) continue
        if (record[fieldCode] === null || record[fieldCode] === undefined || record[fieldCode] === '') {
          const [defaultResult] = await executeOwnerUsage(field, 'FIELD_DEFAULT', {
            ...runtimeContext,
            input: { fieldCode, value: record[fieldCode] }
          })
          const defaultValue = defaultResult?.data ?? defaultResult
          if (defaultValue !== null && defaultValue !== undefined) {
            record[fieldCode] = defaultValue?.value ?? defaultValue
          }
        }
        const [computedResult] = await executeOwnerUsage(field, 'FIELD_COMPUTE', {
          ...runtimeContext,
          input: { fieldCode, value: record[fieldCode] }
        })
        const computedValue = computedResult?.data ?? computedResult
        if (computedValue !== null && computedValue !== undefined) {
          record[fieldCode] = computedValue?.value ?? computedValue
        }
      }
    } catch (error) {
      initialized.delete(initializationKey)
      throw error
    }
  }

  async function loadOptions(field) {
    const [result] = await executeOwnerUsage(field, 'FIELD_OPTIONS', {
      input: {
        fieldCode: field?.fieldCode,
        value: currentRecord()?.[field?.fieldCode]
      }
    })
    const value = result?.data ?? result
    return Array.isArray(value) ? value : []
  }

  async function loadSubformRows(owner, runtimeContext = {}) {
    const [result] = await executeOwnerUsage(owner, 'SUBFORM_ROWS', runtimeContext)
    const value = result?.data ?? result
    if (Array.isArray(value)) return value
    return Array.isArray(value?.rows) ? value.rows : []
  }

  async function prevalidateBeforeSubmit({ form, fields = [], nodes = [] }) {
    for (const owner of [form, ...fields, ...nodes]) {
      for (const binding of getClientBeforeSubmitBindings(owner)) {
        await execute(binding, {
          usage: 'BEFORE_SUBMIT',
          context: {
            clientPrevalidate: true,
            sideEffectFree: true
          }
        })
      }
    }
    return currentRecord()
  }

  async function beforeSubmit(configuration) {
    return prevalidateBeforeSubmit(configuration)
  }

  return {
    execute,
    executeOwnerUsage,
    initialize,
    loadOptions,
    loadSubformRows,
    prevalidateBeforeSubmit,
    beforeSubmit
  }
}

export { bindingsFor as getFormDataSourceBindings }
