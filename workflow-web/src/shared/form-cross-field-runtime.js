import { getFieldModeAccess, isFieldReadonlyForMode, resolveRuntimeNodeFieldRules, safeParseConfig } from './config-runtime/index.js'
import LinkageEngine from '../utils/linkageEngine.js'
import { buildCrossFieldDependencyIndex, evaluateCrossField, getCrossFieldConfig } from './form-cross-field-validation.js'
import { resolveFormNodeBinding } from './form-node-property-schema.js'

const flag = value => value === true || value === 1 || value === '1'
const nodeProps = node => safeParseConfig(node?.propsDocument ?? node?.props)

/**
 * 从发布节点投影本实体字段，保留隐藏的比较来源。实体元数据只补齐，不带入实体级校验。
 * 字段节点规则优先，包括显式清空；子表节点及后代不进入当前记录的比较作用域。
 */
export function collectCrossFieldRuntimeFields(form = {}, entityFields = []) {
  const fields = form.fields || []
  const nodes = form.nodes || []
  const metadata = field => entityFields.find(item => item.fieldCode === field.fieldCode) || {}
  if (!nodes.length) return fields.map(field => ({ ...metadata(field), ...field, validationRules: field.validationRules }))
  const byId = new Map(nodes.map(node => [String(node.id), node]))
  return nodes.filter(node => {
    if (node.nodeType !== 'FIELD') return false
    const props = nodeProps(node)
    const field = fields.find(item => String(item.id) === String(node.id)) || {}
    // 与设计器共用绑定推断，兼容旧快照的 NONE + 实体字段 ID；不修改发布快照。
    const { bindingType } = resolveFormNodeBinding({
      ...field, ...props, bindingType: node.bindingType, bindingRef: node.bindingRef
    }, 'FIELD')
    if (bindingType !== 'ENTITY_FIELD') return false
    let current = node
    const seen = new Set()
    while (current) {
      if (seen.has(String(current.id)) || ['SUB_FORM', 'REPEATER'].includes(current.nodeType)) return false
      seen.add(String(current.id))
      current = byId.get(String(current.parentId))
    }
    return true
  }).map(node => {
    const props = nodeProps(node)
    const code = props.fieldCode || node.bindingRef
    const field = fields.find(item => item.fieldCode === code || String(item.id) === String(node.id)) || { fieldCode: code }
    const merged = { ...metadata(field), ...field }
    const rules = resolveRuntimeNodeFieldRules(field, node.rulesDocument ?? node.rules)
    return {
      ...merged,
      id: node.id,
      fieldCode: code,
      fieldType: props.fieldType || merged.fieldType,
      fieldLabel: props.label || merged.fieldLabel || merged.fieldName,
      componentProps: props.componentProps ?? merged.componentProps,
      isHidden: props.hidden === undefined ? merged.isHidden : flag(props.hidden),
      isReadonly: props.readonly === undefined ? merged.isReadonly : flag(props.readonly),
      ...rules
    }
  })
}

/** 使用现有字段联动和权限语义计算适用状态；Tab 未激活或面板收起不会改变业务可见性。 */
export function resolveCrossFieldRuntimeState(field, {
  form = {}, record = {}, mode = 'view', readonly = false, context = {}, rootParentId = '', excludedNodeIds = []
} = {}) {
  const access = getFieldModeAccess(field, mode)
  let visible = access.visible && LinkageEngine.shouldShowField(field, record)
  let editable = !isFieldReadonlyForMode(field, mode, readonly || flag(form.isReadonly))
    && !LinkageEngine.shouldDisableField(field, record)
  const byId = new Map((form.nodes || []).map(node => [String(node.id), node]))
  let node = byId.get(String(field.id))
  let inScope = !rootParentId
  const excluded = new Set(excludedNodeIds.map(String))
  const seen = new Set()
  while (node) {
    const key = String(node.id)
    if (seen.has(key) || excluded.has(key)) return { visible: false, editable: false }
    seen.add(key)
    if (key === String(rootParentId)) inScope = true
    const props = nodeProps(node)
    if (flag(props.hidden) || String(props.modeAccess?.[mode] || '').toUpperCase() === 'HIDDEN') visible = false
    if (flag(props.readonly) || flag(props.disabled) || String(props.modeAccess?.[mode] || '').toUpperCase() === 'READONLY') editable = false
    const permission = String(props.permissionCode || '')
    if (permission) {
      const allowed = typeof context.hasPermission === 'function'
        ? context.hasPermission(permission)
        : (context.permissions || []).some(item => item === '*' || item === permission)
      if (!allowed) visible = false
    }
    node = byId.get(String(node.parentId))
  }
  return { visible: visible && inScope, editable }
}

/**
 * 管理当前表单的错误与交互状态。touch 只由用户编辑入口调用；初始化/联动仅 refresh，
 * 从而首次加载不闪红，已触碰的规则又能随程序赋值、隐藏或禁用及时更新。
 */
export function createCrossFieldController({ getFields, getRecord, getState, onErrorsChange = () => {} }) {
  let touched = new Set()
  let submitted = false
  let serverErrors = {}
  let serverSnapshot = ''
  let errors = {}
  let deferred = false
  const publish = next => {
    if (JSON.stringify(errors) !== JSON.stringify(next)) {
      errors = next
      onErrorsChange(errors)
    }
  }
  const refresh = () => {
    const next = {}
    deferred = false
    const fields = getFields()
    // 服务端比较的是最终数据；任一值或规则变化后，旧响应不能继续阻止本地修正。
    const snapshot = JSON.stringify([getRecord(), fields])
    if (snapshot !== serverSnapshot) serverErrors = {}
    for (const field of fields) {
      const code = field.fieldCode
      if (!submitted && !touched.has(code)) continue
      const state = getState(field)
      if (!state.visible || !state.editable) { delete serverErrors[code]; continue }
      const result = evaluateCrossField(field, getRecord(), fields, state)
      deferred ||= result.deferred
      if (result.error) next[code] = result.error
      else if (serverErrors[code]) next[code] = serverErrors[code]
    }
    publish(next)
    return { valid: !Object.keys(next).length, deferred, errors: Object.values(next) }
  }
  return {
    refresh,
    touch(codes) {
      const index = buildCrossFieldDependencyIndex(getFields())
      for (const code of Array.isArray(codes) ? codes : [codes]) {
        for (const owner of index.get(code) || []) { touched.add(owner); delete serverErrors[owner] }
      }
    },
    validate() { submitted = true; return refresh() },
    reset() { touched = new Set(); submitted = false; serverErrors = {}; publish({}) },
    /** 服务端终检错误只映射到当前仍可编辑且配置了该规则的字段，不接受失效响应。 */
    applyServerErrors(items) {
      serverErrors = {}
      const fields = getFields()
      serverSnapshot = JSON.stringify([getRecord(), fields])
      for (const error of items || []) {
        const field = fields.find(item => item.fieldCode === error.fieldCode)
        if (!field || !getCrossFieldConfig(field)?.rules?.some(rule => rule.id === error.ruleId)) continue
        const state = getState(field)
        if (!state.visible || !state.editable) continue
        touched.add(error.fieldCode)
        serverErrors[error.fieldCode] = error
      }
      return refresh()
    },
    clearServerErrors() { serverErrors = {} },
    getErrors: () => errors
  }
}
