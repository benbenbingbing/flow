import { uiDataSourceApi } from '@/api/uiConfig'
import { safeParseConfig } from '@/shared/config-runtime'

function parseBindings(value) {
  return safeParseConfig(value) || {}
}

function normalizeBinding(binding, usage) {
  if (!binding || typeof binding !== 'object' || Array.isArray(binding)) {
    return null
  }
  return {
    ...binding,
    serviceId: binding.serviceId,
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
    .filter(binding =>
      binding?.serviceId
      && binding?.operationCode)
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

function isMergeableObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
}

/**
 * 按映射目标路径递归合并步骤结果；同一对象下的不同叶子字段不能被后一步整体覆盖。
 */
function mergeMappedOutput(target, patch) {
  Object.entries(patch).forEach(([key, value]) => {
    if (isMergeableObject(value) && isMergeableObject(target[key])) {
      mergeMappedOutput(target[key], value)
      return
    }
    target[key] = value
  })
}

function mergeObject(target, result) {
  const value = result?.data ?? result
  if (isMergeableObject(value)) {
    mergeMappedOutput(target, value)
  }
  return value
}

function mappingPathParts(path) {
  return String(path || '').split('.')
    .filter(part => part.trim().length > 0)
}

function resolvePath(source, path) {
  return mappingPathParts(path)
    .reduce((current, key) => current?.[key], source)
}

function setPath(target, path, value) {
  const parts = mappingPathParts(path)
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

function mergeRuntimeContexts(base = {}, override = {}) {
  return {
    ...base,
    ...override,
    context: {
      ...(base.context || {}),
      ...(override.context || {})
    },
    input: {
      ...(base.input || {}),
      ...(override.input || {})
    }
  }
}

function getRuntimeFormId(form) {
  return form?.id || form?.formId || form?.entityFormId || ''
}

function firstRuntimeText(...values) {
  for (const value of values) {
    if (value === null || value === undefined) continue
    const normalized = String(value).trim()
    if (normalized) return normalized
  }
  return ''
}

function firstRuntimeVersion(...values) {
  for (const value of values) {
    const normalized = Number(value)
    if (Number.isInteger(normalized) && normalized > 0) return normalized
  }
  return undefined
}

/**
 * 构造 FORM datasource 原生请求的固定目标信封。
 *
 * Release 坐标只有在 id/version/token 三项齐全时才透传：坐标本身来自浏览器内存，
 * 不能作为授权依据；服务端只会相信 token 验签后恢复出的 exact target。缺少签名的
 * 草稿或历史表单继续使用普通 Flow 登录权限，Embed 下的非根目标则会安全拒绝。
 */
export function buildFormDataSourceExecutionRequest({
  options = {},
  runtimeContext = {},
  ownerId,
  bindingCode,
  targetType,
  targetKey,
  serviceId,
  operationCode,
  input
}) {
  const contextualForm = runtimeContext.form
  const fallbackForm = options.getForm?.()
  const releaseId = firstRuntimeText(
    contextualForm?.runtimeReleaseId,
    contextualForm?.formReleaseId,
    runtimeContext.formReleaseId,
    fallbackForm?.runtimeReleaseId,
    fallbackForm?.formReleaseId,
    runtimeContext.releaseId
  )
  const releaseVersion = firstRuntimeVersion(
    contextualForm?.runtimeReleaseVersion,
    contextualForm?.formReleaseVersion,
    runtimeContext.formReleaseVersion,
    fallbackForm?.runtimeReleaseVersion,
    fallbackForm?.formReleaseVersion,
    runtimeContext.releaseVersion
  )
  const releaseResolutionToken = firstRuntimeText(
    contextualForm?.releaseResolutionToken,
    contextualForm?.formReleaseResolutionToken,
    runtimeContext.formReleaseResolutionToken,
    fallbackForm?.releaseResolutionToken,
    fallbackForm?.formReleaseResolutionToken,
    runtimeContext.releaseResolutionToken
  )
  const entityCode = firstRuntimeText(
    contextualForm?.entityCode,
    runtimeContext.entityCode,
    fallbackForm?.entityCode,
    typeof options.entityCode === 'function'
      ? options.entityCode()
      : options.entityCode,
    options.getEntityCode?.(),
    options.getEntityDefinition?.()?.entityCode
  )
  const listKey = firstRuntimeText(
    contextualForm?.listKey,
    runtimeContext.listKey,
    fallbackForm?.listKey,
    options.getListKey?.()
  )
  const recordId = firstRuntimeText(
    runtimeContext.recordId,
    runtimeContext.record?.id,
    options.getRecordId?.()
  )
  const traversalToken = firstRuntimeText(
    runtimeContext.viewCompositionTraversalToken
  )
  const request = {
    ownerType: 'FORM',
    ownerId,
    bindingCode,
    targetType,
    targetKey,
    serviceId,
    operationCode,
    input
  }

  if (releaseId && releaseVersion && releaseResolutionToken) {
    request.releaseId = releaseId
    request.releaseVersion = releaseVersion
    request.releaseResolutionToken = releaseResolutionToken
  }
  if (entityCode) request.entityCode = entityCode
  if (listKey) request.listKey = listKey
  if (recordId) request.recordId = recordId
  if (traversalToken) {
    request.viewCompositionTraversalToken = traversalToken
  }
  return request
}

export function createFormDataSourceRuntime(options) {
  const initialized = new Set()

  function currentRecord() {
    return options.getRecord?.() || {}
  }

  function baseContext(runtimeContext = {}) {
    const form = runtimeContext.form || options.getForm?.()
    return {
      mode: runtimeContext.mode
        || options.getMode?.()
        || 'view',
      // 显式 runtime form 优先；只有嵌套运行时未携带 form 对象时才读取其 ownerId，
      // 最后回退根 runtime form，避免把父表单误当成子表单 datasource owner。
      formId: getRuntimeFormId(runtimeContext.form)
        || runtimeContext.formId
        || getRuntimeFormId(form),
      entityId: runtimeContext.entityId
        || form?.entityId
        || options.getEntityDefinition?.()?.id
    }
  }

  async function execute(binding, runtimeContext = {}) {
    const normalized = normalizeBinding(binding, runtimeContext.usage)
    if (!normalized?.serviceId) {
      throw new Error('数据源绑定缺少 serviceId')
    }
    if (!normalized.operationCode) {
      throw new Error('数据源绑定缺少 operationCode')
    }
    const usage = runtimeContext.usage || normalized.usage
    if (usage === 'BEFORE_SUBMIT' && !isClientPrevalidationBinding(normalized)) {
      throw new Error(
        '浏览器禁止执行普通 BEFORE_SUBMIT；仅允许同时标记 sideEffectFree=true 和 clientPrevalidate=true 的预校验绑定'
      )
    }
    const record = runtimeContext.record || currentRecord()
    const runtimeTargetContext = baseContext(runtimeContext)
    const context = {
      ...runtimeTargetContext,
      ...normalized.context,
      ...runtimeContext.context
    }
    const rawInput = {
      recordId: runtimeContext.recordId ?? options.getRecordId?.(),
      mode: runtimeTargetContext.mode,
      fieldCode: runtimeContext.fieldCode
        || runtimeContext.input?.fieldCode
        || normalized.targetKey,
      formData: record,
      changedField: runtimeContext.changedField || {},
      params: runtimeContext.params || runtimeContext.context?.params || {},
      parent: runtimeContext.parent || context.parent || {},
      row: runtimeContext.row || context.row || {},
      ...normalized.input,
      ...runtimeContext.input
    }
    const mappingSource = {
      data: record,
      context,
      input: rawInput,
      parent: runtimeContext.parent || context.parent || {},
      params: runtimeContext.params || context.params || {},
      row: runtimeContext.row || context.row || {},
      relation: runtimeContext.relation || context.relation || {}
    }
    const input = applyMapping(
      normalized.inputMapping,
      mappingSource,
      rawInput
    )
    const executeDataSource = options.executeDataSource
      || uiDataSourceApi.executeOperation
    const response = await executeDataSource(buildFormDataSourceExecutionRequest({
      options,
      runtimeContext,
      // ownerId 只能来自当前 runtime form；绑定业务 Context 中的同名字段
      // 仍可参与 datasource mapping，但不能改变 FORM_OWNER_BODY 安全信封。
      ownerId: runtimeTargetContext.formId,
      bindingCode: usage,
      targetType: runtimeContext.targetType || normalized.targetType || 'OWNER',
      targetKey: runtimeContext.targetKey || normalized.targetKey || '',
      serviceId: normalized.serviceId,
      operationCode: normalized.operationCode,
      input
    }))
    return applyMapping(
      normalized.outputMapping,
      { data: response?.data ?? response, response },
      response
    )
  }

  async function executeOwnerUsage(owner, usage, runtimeContext = {}) {
    const results = []
    const fieldCode = owner?.fieldCode || owner?.fieldKey || owner?.nodeKey || ''
    const targetType = fieldCode ? 'FIELD' : 'OWNER'
    for (const binding of bindingsFor(owner, usage)) {
      results.push(await execute(binding, {
        ...runtimeContext,
        usage,
        fieldCode,
        targetType,
        targetKey: fieldCode
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
    initializationKey: explicitInitializationKey,
    runtimeContext: initialRuntimeContext = {}
    }) {
    const runtimeMode = String(
      initialRuntimeContext.mode
      || options.getMode?.()
      || 'view'
    ).toLowerCase()
    const initializationKey = explicitInitializationKey || [
      getRuntimeFormId(form) || 'form',
      recordId ?? options.getRecordId?.() ?? 'new',
      runtimeMode
    ].join(':')
    if (initialized.has(initializationKey)) return
    initialized.add(initializationKey)

    const record = explicitRecord || currentRecord()
    const runtimeContext = mergeRuntimeContexts(initialRuntimeContext, {
      form,
      record,
      recordId
    })
    try {
      // FORM_INIT 只定义“新增记录的初始值”；编辑、查看和审批不得覆盖已有业务数据。
      if (runtimeMode === 'create') {
        for (const result of await executeOwnerUsage(
          form,
          'FORM_INIT',
          runtimeContext
        )) {
          mergeObject(record, result)
        }
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

  async function loadOptions(field, runtimeContext = {}) {
    const record = runtimeContext.record || currentRecord()
    const [result] = await executeOwnerUsage(field, 'FIELD_OPTIONS', {
      ...runtimeContext,
      input: {
        ...(runtimeContext.input || {}),
        fieldCode: field?.fieldCode,
        value: record?.[field?.fieldCode]
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

  async function prevalidateBeforeSubmit({
    form,
    fields = [],
    nodes = [],
    runtimeContext = {}
  }) {
    for (const owner of [form, ...fields, ...nodes]) {
      for (const binding of getClientBeforeSubmitBindings(owner)) {
        await execute(binding, mergeRuntimeContexts(runtimeContext, {
          usage: 'BEFORE_SUBMIT',
          context: {
            ...(runtimeContext.context || {}),
            clientPrevalidate: true,
            sideEffectFree: true
          }
        }))
      }
    }
    return runtimeContext.record || currentRecord()
  }

  async function beforeSubmit(configuration) {
    return prevalidateBeforeSubmit(configuration)
  }

  function withContext(baseRuntimeContext = {}) {
    const resolveBase = () => (
      typeof baseRuntimeContext === 'function'
        ? (baseRuntimeContext() || {})
        : (baseRuntimeContext || {})
    )
    const scoped = runtimeContext =>
      mergeRuntimeContexts(resolveBase(), runtimeContext || {})
    return {
      execute: (binding, runtimeContext = {}) =>
        execute(binding, scoped(runtimeContext)),
      executeOwnerUsage: (owner, usage, runtimeContext = {}) =>
        executeOwnerUsage(owner, usage, scoped(runtimeContext)),
      initialize: (configuration = {}) => initialize({
        ...configuration,
        runtimeContext: scoped(configuration.runtimeContext)
      }),
      loadOptions: (field, runtimeContext = {}) =>
        loadOptions(field, scoped(runtimeContext)),
      loadSubformRows: (owner, runtimeContext = {}) =>
        loadSubformRows(owner, scoped(runtimeContext)),
      prevalidateBeforeSubmit: (configuration = {}) =>
        prevalidateBeforeSubmit({
          ...configuration,
          runtimeContext: scoped(configuration.runtimeContext)
        }),
      beforeSubmit: (configuration = {}) =>
        prevalidateBeforeSubmit({
          ...configuration,
          runtimeContext: scoped(configuration.runtimeContext)
        }),
      withContext: nestedContext => withContext(() =>
        mergeRuntimeContexts(
          resolveBase(),
          typeof nestedContext === 'function'
            ? nestedContext()
            : nestedContext
        )
      )
    }
  }

  return {
    execute,
    executeOwnerUsage,
    initialize,
    loadOptions,
    loadSubformRows,
    prevalidateBeforeSubmit,
    beforeSubmit,
    withContext
  }
}

export { bindingsFor as getFormDataSourceBindings }
