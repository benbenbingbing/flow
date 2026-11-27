import { buildRuntimeContext } from './context.js'
import { resolvePageParameters, initializePageFields, pageParameterFields } from '../page-parameters.js'
import { safeParseConfig } from '../config-runtime/index.js'
import { projectRuntimeFields } from './formModel.js'
import { applyInitialFormulaDefaults, formRuleContext, LinkageEngine, withFormContext } from '../../utils/linkageEngine.js'

function parseBindings(value) {
  return safeParseConfig(value) || {}
}

function normalizeBinding(binding, usage) {
  if (!binding || typeof binding !== 'object' || Array.isArray(binding)) {
    return null
  }
  return {
    ...binding,
    // 历史发布的 serviceId 不能单独当作新接口 ID；完整 pair 由服务端解析并校验发布绑定。
    extensionId: binding.extensionId || binding.serviceId || '',
    ...(binding.serviceId
      && (!binding.extensionId || binding.extensionId === binding.serviceId)
      ? { legacyOperationCode: binding.operationCode } : {}),
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
    .filter(binding => binding?.enabled !== false)
    .map(binding => normalizeBinding(binding, usage))
    .filter(binding => binding?.extensionId)
}

function resolveFieldOptionQueryParameters(field, record, contextValues) {
  const rule = LinkageEngine.getFieldLinkageRules(field).optionsLinkage
  if (rule?.mode !== 'query' || !Array.isArray(rule.parameterMappings)) return null
  const source = withFormContext(record, contextValues)
  return Object.fromEntries(rule.parameterMappings.filter(row => row?.parameter && row.fieldCode)
    .map(({ parameter, fieldCode }) => [parameter, Object.hasOwn(source, fieldCode) ? source[fieldCode] ?? null : null]))
}

/** 用实际发送的参数检测依赖变化，避免无关字段或候选结果变化引起重查。 */
export function getFieldOptionQueryKey(field, record, form, mode) {
  const parameters = resolveFieldOptionQueryParameters(field, record, formRuleContext(form, mode).contextValues)
  return parameters ? JSON.stringify(parameters) : null
}

/** 仅声明无副作用且允许预校验的绑定可在浏览器执行；其他 BEFORE_SUBMIT 留给服务端事务处理。 */
export function isClientPrevalidationBinding(binding) {
  return Boolean(
    binding
    && typeof binding === 'object'
    && binding.sideEffectFree === true
    && binding.clientPrevalidate === true
  )
}

/** 提取浏览器可预校验步骤；返回结果用于提交前提示，不能代替服务端权威执行。 */
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
    if (['__proto__', 'prototype', 'constructor'].includes(key)) return
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
  if (typeof path === 'string' && path.startsWith('ctx.')) return source[path]
  return mappingPathParts(path)
    .reduce((current, key) => current?.[key], source)
}

function setPath(target, path, value) {
  const parts = mappingPathParts(path)
  if (!parts.length || parts.some(part => ['__proto__', 'prototype', 'constructor'].includes(part))) return
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

/** 生命周期条件与后端提交链共用路径和值语义，空数组和纯空白文本按假值处理。 */
function matchesLifecycleCondition(condition, source) {
  if (!condition?.path) return true
  const actual = resolvePath(source, condition.path)
  if (typeof condition.path === 'string' && condition.path.startsWith('ctx.')
      && actual == null && !Object.hasOwn(condition, 'exists')) return false
  if (Object.hasOwn(condition, 'equals')) return (actual ?? null) === condition.equals
  if (Object.hasOwn(condition, 'notEquals')) return (actual ?? null) !== condition.notEquals
  if (Object.hasOwn(condition, 'exists')) return (actual != null) === condition.exists
  if (Object.hasOwn(condition, 'truthy')) {
    const truthy = Array.isArray(actual) ? actual.length > 0 : Boolean(typeof actual === 'string' ? actual.trim() : actual)
    return truthy === condition.truthy
  }
  return true
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


/**
 * 构造 FORM datasource 原生请求的固定目标信封。
 *
 * configHash 只检查配置变化；业务入口交给服务端实时鉴权。
 */
export function buildFormDataSourceExecutionRequest({
  options = {},
  runtimeContext = {},
  ownerId,
  bindingCode,
  targetType,
  targetKey,
  extensionId,
  legacyOperationCode,
  formMode,
  input
}) {
  const contextualForm = runtimeContext.form
  const fallbackForm = options.getForm?.()
  const configHash = firstRuntimeText(
    contextualForm?.configHash,
    contextualForm?.formConfigHash,
    runtimeContext.formConfigHash,
    fallbackForm?.configHash,
    fallbackForm?.formConfigHash,
    runtimeContext.configHash
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
  const recordId = runtimeContext.row?.isNew ? '' : firstRuntimeText(
    runtimeContext.recordId,
    runtimeContext.record?.id,
    options.getRecordId?.()
  )
  const request = {
    ownerType: 'FORM',
    runtimeContext: buildRuntimeContext(runtimeContext, contextualForm || fallbackForm),
    ownerId,
    bindingCode,
    targetType,
    targetKey,
    extensionId,
    input
  }
  if (legacyOperationCode) request.legacyOperationCode = legacyOperationCode
  if (formMode) request.formMode = formMode

  if (configHash) request.configHash = configHash
  if (entityCode) request.entityCode = entityCode
  if (listKey) request.listKey = listKey
  if (recordId) request.recordId = recordId
  return request
}

/**
 * 创建表单数据源执行器。options 提供当前记录、表单、目标实体和实际请求函数，
 * 后续 FORM_INIT、AFTER_LOAD 及受限的 BEFORE_SUBMIT 共享同一发布目标和映射语义。
 * @param {object} options 运行时宿主的取值及接口调用能力；嵌套表单可传自己的 form/ownerId。
 * @returns {object} 按绑定和生命周期执行并合并结果的方法集合。
 */
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
    if (!normalized?.extensionId) {
      throw new Error('数据接口绑定缺少 extensionId')
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
      ...withFormContext({}, formRuleContext(runtimeContext.form || options.getForm?.(), runtimeTargetContext.mode,
        false, runtimeContext).contextValues),
      data: record,
      context,
      input: rawInput,
      parent: runtimeContext.parent || context.parent || {},
      params: runtimeContext.params || context.params || {},
      row: runtimeContext.row || context.row || {},
      relation: runtimeContext.relation || context.relation || {}
    }
    if (['AFTER_LOAD', 'BEFORE_SUBMIT'].includes(usage)
      && (normalized.enabled === false || !matchesLifecycleCondition(normalized.condition, mappingSource))) return
    const input = applyMapping(
      normalized.inputMapping,
      mappingSource,
      rawInput
    )
    // 级联协议坐标由组件生成，映射仅处理业务参数，不能丢弃/改写加载层级。
    if (usage === 'FIELD_OPTIONS' && runtimeContext.input?.cascader) {
      input.cascader = runtimeContext.input.cascader
    }
    // 面板生成的分页和所选键不属于业务参数映射，不能因只映射公司等参数而丢失。
    if (usage.startsWith('FIELD_PICKER_')) input.picker = runtimeContext.input?.picker || {}
    const executeDataSource = options.executeDataSource
    if (typeof executeDataSource !== 'function') throw new Error('宿主未提供数据源执行能力')
    const response = await executeDataSource(buildFormDataSourceExecutionRequest({
      options,
      runtimeContext,
      // ownerId 只能来自当前 runtime form；绑定业务 Context 中的同名字段
      // 仍可参与 datasource mapping，但不能改变 FORM_OWNER_BODY 安全信封。
      ownerId: runtimeTargetContext.formId,
      bindingCode: usage,
      targetType: runtimeContext.targetType || normalized.targetType || 'OWNER',
      targetKey: runtimeContext.targetKey || normalized.targetKey || '',
      extensionId: normalized.extensionId,
      legacyOperationCode: normalized.legacyOperationCode,
      formMode: usage === 'FIELD_OPTIONS' ? runtimeTargetContext.mode : undefined,
      input
    }))
    return applyMapping(
      normalized.outputMapping,
      { data: response?.data ?? response, response },
      response
    )
  }

  async function executeOwnerUsage(owner, usage, runtimeContext = {}, applyResult) {
    const results = []
    const fieldCode = owner?.fieldCode || owner?.fieldKey || owner?.nodeKey || ''
    const targetType = fieldCode ? 'FIELD' : 'OWNER'
    for (const binding of bindingsFor(owner, usage)) {
      const result = await execute(binding, {
        ...runtimeContext,
        usage,
        fieldCode,
        targetType,
        targetKey: fieldCode
      })
      results.push(result)
      applyResult?.(result)
    }
    return results
  }

  /**
   * 同一表单、记录和模式只初始化一次；initializationKey 由宿主在重新加载时显式更换。
   * record 是后续渲染/提交使用的可变模型，先应用参数与接口绑定，再用公式填补空字段。
   */
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
    const record = explicitRecord || currentRecord()
    const runtimeContext = mergeRuntimeContexts(initialRuntimeContext, {
      form,
      record,
      recordId,
      params: resolvePageParameters(form?.viewConfig, initialRuntimeContext.params || initialRuntimeContext.parameters || {})
    })
    // 先占用键可防止并发挂载重复请求；执行失败再释放，允许用户重试初始化。
    initialized.add(initializationKey)
    try {
      // 目标页面声明用途，子表单也复用同一规则；查看/审批不通过参数改变显示记录。
      if (['create', 'edit'].includes(runtimeMode) && ![true, 1, '1'].includes(form?.isReadonly)) {
        Object.assign(record, initializePageFields(record, form?.viewConfig, runtimeContext.params,
          pageParameterFields([...fields, ...nodes]), [initialRuntimeContext.relation?.childRefFieldCode].filter(Boolean), runtimeMode))
      }
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
      // 先回填再评估下一步，下一步的条件和映射才能使用已加载的字段。
      const mergeAfterLoad = result => mergeObject(record, result)
      await executeOwnerUsage(form, 'AFTER_LOAD', runtimeContext, mergeAfterLoad)
      for (const owner of [...fields, ...nodes]) {
        await executeOwnerUsage(owner, 'AFTER_LOAD', runtimeContext, mergeAfterLoad)
      }
      const isNew = runtimeContext.row?.isNew ?? (runtimeMode === 'create')
      const canApplyDefaults = isNew && ['create', 'edit'].includes(runtimeMode)
        && !runtimeContext.readonly && ![true, 1, '1'].includes(form?.isReadonly)
      if (!canApplyDefaults) return
      // 节点绑定和来源配置属于当前表单或子表行。旧版扁平字段仍可提供历史绑定。
      const defaultFields = nodes.length
        ? projectRuntimeFields(
          { fields, nodes }, [], { rootParentId: runtimeContext.rootParentId || '' }
        ).fields
        : fields
      const fieldKey = field => field.fieldCode || field.fieldKey
      const isEmpty = value => value == null || value === ''
      const originalFields = new Map(fields.map(field => [fieldKey(field), field]))
      const projectedFields = new Map(defaultFields.filter(field => !field.isDisplayOnly).map(field => [fieldKey(field), field]))
      for (const fieldCode of new Set([...originalFields.keys(), ...projectedFields.keys()])) {
        if (!fieldCode) continue
        const field = originalFields.get(fieldCode)
        const projected = projectedFields.get(fieldCode)
        const source = (projected || field).defaultValueMode
        // 接口默认值统一补齐新增记录；编辑主表时新增的子表行仍是新记录。
        if ((!source || source === 'dataSource') && isEmpty(record[fieldCode])) {
          const node = projected?.node
          const defaultOwner = bindingsFor(node, 'FIELD_DEFAULT').length ? { ...node, fieldCode } : field
          const [defaultResult] = await executeOwnerUsage(defaultOwner, 'FIELD_DEFAULT', {
            ...runtimeContext,
            input: { fieldCode, value: record[fieldCode] }
          })
          const defaultValue = defaultResult?.data ?? defaultResult
          const value = defaultValue && Object.hasOwn(defaultValue, 'value') ? defaultValue.value : defaultValue
          // 子表可能在请求返回前已可填写，迟到的默认值不得覆盖用户输入。
          if (!isEmpty(value) && isEmpty(record[fieldCode])) {
            record[fieldCode] = value
          }
        }
      }
      const parameterFields = (safeParseConfig(form?.viewConfig).inputParameterBindings || [])
        .filter(binding => binding.usage === 'INITIALIZE' && runtimeContext.params[binding.parameter] !== undefined)
        .map(binding => binding.targetField)
      applyInitialFormulaDefaults(defaultFields, record, {
        ...runtimeContext,
        contextValues: runtimeMode === 'create' ? formRuleContext(form, runtimeMode, false, runtimeContext).contextValues : undefined,
        contextProtectedFields: new Set([...parameterFields, ...Object.keys(initialRuntimeContext.initialData || {})])
      })
    } catch (error) {
      initialized.delete(initializationKey)
      throw error
    }
  }

  async function loadOptions(field, runtimeContext = {}) {
    if (runtimeContext.input?.cascader) {
      const bindings = bindingsFor(field, 'FIELD_OPTIONS')
      if (bindings.length !== 1) throw new Error('级联字段必须配置一个 FIELD_OPTIONS 数据接口')
      if (bindings[0].modes?.length && !bindings[0].modes.includes(runtimeContext.mode || options.getMode?.() || 'view')) {
        throw new Error('当前表单模式未启用级联数据接口')
      }
    }
    const record = runtimeContext.record || currentRecord()
    const ruleContext = formRuleContext(runtimeContext.form || options.getForm?.(), baseContext(runtimeContext).mode, false, runtimeContext)
    const optionParams = resolveFieldOptionQueryParameters(field, record, ruleContext.contextValues)
    const [result] = await executeOwnerUsage(field, 'FIELD_OPTIONS', {
      ...runtimeContext,
      record,
      input: {
        ...(runtimeContext.input || {}),
        ...(optionParams && { optionParams }),
        fieldCode: field?.fieldCode,
        value: record?.[field?.fieldCode]
      }
    })
    const value = result?.data ?? result
    if (runtimeContext.input?.cascader && !Array.isArray(value)) {
      throw new Error('级联接口必须返回节点数组')
    }
    return Array.isArray(value) ? value : []
  }

  async function loadSubformRows(owner, runtimeContext = {}) {
    const [result] = await executeOwnerUsage(owner, 'SUBFORM_ROWS', runtimeContext)
    const value = result?.data ?? result
    if (Array.isArray(value)) return value
    return Array.isArray(value?.rows) ? value.rows : []
  }

  /**
   * 仅执行声明无副作用的提交前预校验，供 UI 提前显示错误；正式提交仍由服务端
   * 在事务中重新执行全部 BEFORE_SUBMIT，不能把此结果当成最终写入数据。
   */
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

  /** 为子表单等运行时预置上下文，调用时再合并最新记录，避免复用旧参数或父表单目标。 */
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
