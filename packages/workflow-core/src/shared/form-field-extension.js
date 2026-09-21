export const FORM_FIELD_EXTENSION_TYPE = 'FIELD'

function normalizedNodeType(node) {
  return String(node?.nodeType || '').trim().toUpperCase()
}

function nodeProps(node) {
  return node?.props && typeof node.props === 'object'
    ? node.props
    : node || {}
}

/**
 * 判断表单 FIELD 节点的 componentName 是否表示字段组件扩展。
 *
 * componentName 也可承载 NODE 扩展，因此必须通过显式类型标记区分，
 * 不能仅凭本地是否注册同名组件推断持久化语义。
 */
export function isFormFieldExtensionNode(node) {
  const props = nodeProps(node)
  return normalizedNodeType(node) === 'FIELD'
    && String(props.componentExtensionType || '')
      .trim()
      .toUpperCase() === FORM_FIELD_EXTENSION_TYPE
    && Boolean(String(
      node?.componentName || node?.fieldComponentName || ''
    ).trim())
}

export function resolveFormFieldExtensionName(node) {
  if (!isFormFieldExtensionNode(node)) return ''
  return String(
    node?.componentName || node?.fieldComponentName || ''
  ).trim()
}

/**
 * 解析 FIELD 节点运行时组件。扩展不存在时保留内置 componentType 回退，
 * 让已发布表单仍能以基础控件展示，而不是直接变成空白。
 */
export function resolveRuntimeFormFieldComponentType(
  node,
  fallbackComponentType,
  hasRegisteredComponent
) {
  const extensionName = resolveFormFieldExtensionName(node)
  return extensionName
    && hasRegisteredComponent(extensionName)
    ? extensionName
    : fallbackComponentType
}
