import {
  FORM_NODE_MAX_DEPTH,
  canContainFormNode,
  canPlaceFormNodeAtRoot,
  isFormNodeContainer,
  normalizeFormNodeType
} from './form-node-hierarchy.js'

function normalizeNodeId(value) {
  return value == null ? '' : String(value)
}

function nodeTypeOf(node) {
  return normalizeFormNodeType(
    node?.nodeType || node?.fieldType || node?.componentType
  )
}

export function findFormNode(nodes, nodeId) {
  const normalizedId = normalizeNodeId(nodeId)
  return (nodes || []).find(node => normalizeNodeId(node?.id) === normalizedId)
}

export function getFormNodeChildren(nodes, parentId) {
  const normalizedParentId = normalizeNodeId(parentId)
  return (nodes || [])
    .filter(node => normalizeNodeId(node?.parentId) === normalizedParentId)
    .sort((left, right) =>
      Number(left?.orderKey || left?.sortOrder || 0)
        - Number(right?.orderKey || right?.sortOrder || 0)
    )
}

export function collectFormNodeDescendantIds(nodes, nodeId) {
  const descendants = new Set()
  const pending = [...getFormNodeChildren(nodes, nodeId)]
  while (pending.length) {
    const child = pending.shift()
    const childId = normalizeNodeId(child?.id)
    if (!childId || descendants.has(childId)) continue
    descendants.add(childId)
    pending.push(...getFormNodeChildren(nodes, childId))
  }
  return descendants
}

export function getFormNodeDepth(nodes, nodeId) {
  if (!nodeId) return 0
  let depth = 0
  let current = findFormNode(nodes, nodeId)
  const visited = new Set()
  while (current) {
    const currentId = normalizeNodeId(current.id)
    if (visited.has(currentId)) return FORM_NODE_MAX_DEPTH + 1
    visited.add(currentId)
    depth += 1
    current = current.parentId
      ? findFormNode(nodes, current.parentId)
      : null
  }
  return depth
}

export function getFormNodeSubtreeHeight(nodes, nodeId, visiting = new Set()) {
  if (!nodeId) return 1
  const normalizedId = normalizeNodeId(nodeId)
  if (visiting.has(normalizedId)) return FORM_NODE_MAX_DEPTH + 1
  const nextVisiting = new Set(visiting)
  nextVisiting.add(normalizedId)
  const children = getFormNodeChildren(nodes, nodeId)
  if (!children.length) return 1
  return 1 + Math.max(
    ...children.map(child =>
      getFormNodeSubtreeHeight(nodes, child.id, nextVisiting)
    )
  )
}

export function orderFormNodesParentFirst(nodes) {
  const source = Array.isArray(nodes) ? nodes : []
  const byId = new Map(
    source
      .filter(node => node?.id != null)
      .map(node => [normalizeNodeId(node.id), node])
  )
  const depthById = new Map()

  function resolveDepth(node, visiting = new Set()) {
    const nodeId = normalizeNodeId(node?.id)
    if (!nodeId) return 0
    if (depthById.has(nodeId)) return depthById.get(nodeId)
    if (visiting.has(nodeId)) {
      throw new Error('表单节点父子关系存在循环')
    }
    const parentId = normalizeNodeId(node?.parentId)
    if (!parentId) {
      depthById.set(nodeId, 0)
      return 0
    }
    const parent = byId.get(parentId)
    if (!parent) {
      throw new Error(`表单节点父级不存在: ${parentId}`)
    }
    const nextVisiting = new Set(visiting)
    nextVisiting.add(nodeId)
    const depth = resolveDepth(parent, nextVisiting) + 1
    depthById.set(nodeId, depth)
    return depth
  }

  return source
    .map((node, index) => ({
      node,
      index,
      depth: resolveDepth(node)
    }))
    .sort((left, right) =>
      left.depth - right.depth || left.index - right.index
    )
    .map(item => item.node)
}

export function validateFormNodeDrop(
  nodes,
  node,
  targetParentId,
  maxDepth = FORM_NODE_MAX_DEPTH
) {
  if (!node?.id) {
    return { valid: false, code: 'NODE_REQUIRED', message: '未找到待移动节点' }
  }
  const normalizedTargetParentId = normalizeNodeId(targetParentId)
  const normalizedNodeId = normalizeNodeId(node.id)
  if (!normalizedTargetParentId) {
    if (!canPlaceFormNodeAtRoot(nodeTypeOf(node))) {
      return {
        valid: false,
        code: 'ROOT_TYPE_FORBIDDEN',
        message: 'Tab 页只能放在 Tab 集合下'
      }
    }
    if (getFormNodeSubtreeHeight(nodes, node.id) > maxDepth) {
      return {
        valid: false,
        code: 'MAX_DEPTH_EXCEEDED',
        message: `移动后节点树不能超过 ${maxDepth} 层`
      }
    }
    return { valid: true, code: 'OK', message: '' }
  }
  if (normalizedTargetParentId === normalizedNodeId) {
    return {
      valid: false,
      code: 'SELF_PARENT',
      message: '节点不能放入自身'
    }
  }
  const parent = findFormNode(nodes, normalizedTargetParentId)
  if (!parent) {
    return {
      valid: false,
      code: 'PARENT_NOT_FOUND',
      message: '目标父容器不存在'
    }
  }
  if (!isFormNodeContainer(nodeTypeOf(parent))
      || !canContainFormNode(nodeTypeOf(parent), nodeTypeOf(node))) {
    return {
      valid: false,
      code: 'INCOMPATIBLE_PARENT',
      message: `${nodeTypeOf(node)} 不能放入 ${nodeTypeOf(parent)}`
    }
  }
  if (collectFormNodeDescendantIds(nodes, node.id).has(normalizedTargetParentId)) {
    return {
      valid: false,
      code: 'DESCENDANT_PARENT',
      message: '节点不能放入自己的后代容器'
    }
  }
  if (getFormNodeDepth(nodes, parent.id)
      + getFormNodeSubtreeHeight(nodes, node.id) > maxDepth) {
    return {
      valid: false,
      code: 'MAX_DEPTH_EXCEEDED',
      message: `移动后节点树不能超过 ${maxDepth} 层`
    }
  }
  return { valid: true, code: 'OK', message: '' }
}

export function buildFormNodeDropPlan(
  nodes,
  node,
  targetParentId,
  targetIndex
) {
  const validation = validateFormNodeDrop(nodes, node, targetParentId)
  if (!validation.valid) return validation
  const parentId = normalizeNodeId(targetParentId)
  const orderedSiblings = getFormNodeChildren(nodes, parentId)
    .filter(sibling => normalizeNodeId(sibling.id) !== normalizeNodeId(node.id))
  const normalizedIndex = Math.max(
    0,
    Math.min(Number(targetIndex) || 0, orderedSiblings.length)
  )
  orderedSiblings.splice(normalizedIndex, 0, node)
  return {
    ...validation,
    parentId,
    targetIndex: normalizedIndex,
    orderedSiblings,
    previousNodeId: orderedSiblings[normalizedIndex - 1]?.id || null,
    nextNodeId: orderedSiblings[normalizedIndex + 1]?.id || null
  }
}
