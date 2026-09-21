function normalizedFieldCode(value) {
  return String(value ?? '').trim()
}

/** 按运行节点的所有字段绑定别名匹配服务端返回的 fieldCode。 */
export function formNodeMatchesValidationField(node = {}, fieldCode = '') {
  const target = normalizedFieldCode(fieldCode)
  if (!target) return false
  const props = node?.props && typeof node.props === 'object'
    ? node.props
    : {}
  return [
    node?.bindingRef,
    props.fieldCode,
    props.fieldId,
    node?.nodeKey
  ].some(value => normalizedFieldCode(value) === target)
}

/**
 * 判断指定节点或任意后代是否绑定目标字段。visited 用于防御异常
 * 发布快照中的循环父子关系，避免错误定位引发无限递归。
 */
export function formNodeSubtreeContainsValidationField(
  node,
  childrenFor,
  fieldCode,
  visited = new Set()
) {
  if (!node || typeof childrenFor !== 'function') return false
  const nodeId = normalizedFieldCode(node.id)
  if (nodeId && visited.has(nodeId)) return false
  if (nodeId) visited.add(nodeId)
  if (formNodeMatchesValidationField(node, fieldCode)) return true
  return (childrenFor(node.id) || []).some(child =>
    formNodeSubtreeContainsValidationField(
      child,
      childrenFor,
      fieldCode,
      visited
    )
  )
}

/** 在候选 Tab 或容器中找到包含目标字段的第一个节点。 */
export function findFormNodeContainingValidationField(
  nodes = [],
  childrenFor,
  fieldCode
) {
  return (nodes || []).find(node =>
    formNodeSubtreeContainsValidationField(
      node,
      childrenFor,
      fieldCode,
      new Set()
    )
  ) || null
}
