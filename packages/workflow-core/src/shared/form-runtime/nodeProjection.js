import LinkageEngine from '../../utils/linkageEngine.js'
import { resolveRuntimeNodeFieldRules, safeParseConfig } from '../config-runtime/index.js'
import { resolveRuntimeFormFieldComponentType } from '../form-field-extension.js'

/** 两端共用发布节点到字段的投影；宿主只注入目标平台字段注册查询。 */
export function resolveRuntimeNodeField(node, fields = [], nestedNodes = [], hasFormFieldComponent = () => false) {
  if (!['FIELD', 'SUB_FORM', 'REPEATER'].includes(node.nodeType)) return null
  const nodeProps = node.props || {}
  const componentProps = nodeProps.componentProps || {}
  const ref = node.bindingRef || nodeProps.fieldCode || nodeProps.fieldId
  const linked = fields.find(field =>
    String(field.id) === String(ref)
      || String(field.fieldId) === String(ref)
      || field.fieldCode === ref
  )
  const subFormConfig = componentProps.subFormConfig || nodeProps.subFormConfig || {}
  const nodeFieldRules = resolveRuntimeNodeFieldRules(
    linked || {},
    node.rules
  )
  const fallback = node.nodeType === 'REPEATER'
    ? {
        fieldType: 'SUB_FORM',
        componentType: 'sub_form'
      }
    : {
        fieldType: node.nodeType,
        componentType: 'sub_form'
      }
  const fallbackComponentType =
    nodeProps.componentType
    || linked?.componentType
    || fallback.componentType
  return {
    ...(linked || {}),
    ...fallback,
    id: node.id,
    nodeType: node.nodeType,
    fieldId: nodeProps.fieldId ?? linked?.fieldId,
    fieldCode: nodeProps.fieldCode || linked?.fieldCode || node.nodeKey,
    fieldName: nodeProps.fieldName || linked?.fieldName || nodeProps.label || node.nodeKey,
    fieldLabel: nodeProps.label || linked?.fieldLabel || linked?.fieldName || node.nodeKey,
    fieldType: nodeProps.fieldType || linked?.fieldType || fallback.fieldType,
    componentType: resolveRuntimeFormFieldComponentType(
      node,
      fallbackComponentType,
      hasFormFieldComponent
    ),
    placeholder: nodeProps.placeholder ?? linked?.placeholder,
    defaultValue: nodeProps.defaultValue ?? linked?.defaultValue,
    isRequired: nodeProps.required === true ? 1 : (linked?.isRequired || 0),
    isReadonly: nodeProps.readonly === true ? 1 : (linked?.isReadonly || 0),
    isHidden: nodeProps.hidden === true ? 1 : (linked?.isHidden || 0),
    options: nodeProps.options ?? linked?.options,
    optionsJson: nodeProps.optionsJson ?? linked?.optionsJson,
    componentProps: Object.keys(componentProps).length ? componentProps : linked?.componentProps,
    validationRules: nodeFieldRules.validationRules,
    extensionConfig: nodeFieldRules.extensionConfig,
    relationType: nodeProps.relationType
      || linked?.relationType
      || subFormConfig.relationType,
    childEntityId: nodeProps.childEntityId
      || linked?.childEntityId
      || subFormConfig.childEntityId
      || subFormConfig.refEntityId,
    refEntityId: nodeProps.refEntityId
      || linked?.refEntityId
      || subFormConfig.refEntityId,
    childRefFieldCode: nodeProps.childRefFieldCode
      || linked?.childRefFieldCode
      || subFormConfig.childRefFieldCode
      || subFormConfig.refFieldCode,
    childFormId: nodeProps.childFormId
      || nodeProps.refFormId
      || nodeProps.publishedFormId
      || linked?.childFormId
      || subFormConfig.childFormId
      || subFormConfig.refFormId
      || subFormConfig.publishedFormId,
    runtimeNodes: nestedNodes,
    runtimeRootParentId: node.id,
    runtimeFields: fields
  }
}

/** 统一解码发布节点；保留稳定 ID 和发布节点业务配置，不修改原配置。 */
export function normalizeRuntimeNodes(nodes = []) {
  return nodes.map(node => ({
    ...node,
    nodeType: String(node.nodeType || 'FIELD').toUpperCase(),
    bindingType: String(node.bindingType || 'NONE').toUpperCase(),
    props: safeParseConfig(node.propsDocument || node.props),
    rules: safeParseConfig(node.rulesDocument || node.rules),
    dataSourceBindings: safeParseConfig(node.dataSourceBindingsDocument || node.dataSourceBindings),
    legacyProps: safeParseConfig(node.legacyPropsDocument || node.legacyProps),
    localOverrides: safeParseConfig(node.localOverridesDocument || node.localOverrides)
  })).sort((a, b) => Number(a.orderKey || 0) - Number(b.orderKey || 0))
}

/**
 * 非字段节点的显示规则控制整个区域；不改变内部字段的编辑权限，也不删除已有值。
 * 兼容旧 Tab 的 HIDDEN 写法。字段本身仍由字段运行时处理显隐和校验。
 */
export function isFormNodeVisible(node, { data = {}, mode = 'view', context = {} } = {}) {
  if (!node || String(node.nodeType || 'FIELD').toUpperCase() === 'FIELD') return true
  const props = safeParseConfig(node.propsDocument || node.props)
  const config = { ...safeParseConfig(props.componentProps), ...props }
  const access = config.modeAccess?.[mode]
  if (config.hidden === true || String(access || '').toUpperCase() === 'HIDDEN'
      || access?.visible === false) return false
  const permission = String(config.permissionCode || '').trim()
  if (permission && !(typeof context.hasPermission === 'function'
    ? context.hasPermission(permission)
    : context.permissions?.includes('*') || context.permissions?.includes(permission))) return false
  return LinkageEngine.shouldShowField(config, data)
}

/**
 * 提升到外层的 Tab 和独立校验没有递归父组件，需显式检查祖先显隐。
 * rootParentId 是子表行的独立数据作用域，不使用子记录去求值父表条件。
 */
export function createFormNodeVisibility(nodes = [], options = {}) {
  const byId = new Map(nodes.map(node => [String(node.id), node]))
  return node => {
    const seen = new Set()
    let current = node
    while (current && String(current.id) !== String(options.rootParentId || '')) {
      if (seen.has(String(current.id)) || !isFormNodeVisible(current, options)) return false
      seen.add(String(current.id))
      current = byId.get(String(current.parentId || ''))
    }
    return true
  }
}
