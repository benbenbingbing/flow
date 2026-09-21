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
    childFormReleaseId: nodeProps.childFormReleaseId
      || nodeProps.refFormReleaseId
      || nodeProps.publishedFormReleaseId
      || linked?.childFormReleaseId
      || subFormConfig.childFormReleaseId
      || subFormConfig.refFormReleaseId
      || subFormConfig.publishedFormReleaseId,
    childFormReleaseVersion: nodeProps.childFormReleaseVersion
      ?? nodeProps.refFormReleaseVersion
      ?? nodeProps.publishedFormReleaseVersion
      ?? linked?.childFormReleaseVersion
      ?? subFormConfig.childFormReleaseVersion
      ?? subFormConfig.refFormReleaseVersion
      ?? subFormConfig.publishedFormReleaseVersion,
    runtimeNodes: nestedNodes,
    runtimeRootParentId: node.id,
    runtimeFields: fields
  }
}

/** 统一解码发布节点；保留稳定 ID 和所有发布版本上下文，不修改原配置。 */
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
