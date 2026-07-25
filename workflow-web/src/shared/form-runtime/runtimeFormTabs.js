import { safeParseConfig } from '../config-runtime/index.js'

function normalizeNode(node) {
  return {
    ...node,
    nodeType: String(node?.nodeType || 'FIELD').toUpperCase(),
    parentId: node?.parentId || '',
    props: safeParseConfig(node?.propsDocument || node?.props)
  }
}

function compareOrder(left, right) {
  return Number(left?.orderKey || 0) - Number(right?.orderKey || 0)
}

export function resolveRuntimeFormTabLayout(form) {
  if (!form || form.customComponent) {
    return {
      tabs: [],
      liftedRootNodeIds: [],
      hasBaseContent: Boolean(form?.customComponent || form?.fields?.length)
    }
  }

  const nodes = (form.nodes || []).map(normalizeNode).sort(compareOrder)
  if (nodes.length === 0) {
    return {
      tabs: [],
      liftedRootNodeIds: [],
      hasBaseContent: Boolean(form.fields?.length)
    }
  }

  const childrenMap = new Map()
  nodes.forEach(node => {
    const parentId = node.parentId || ''
    if (!childrenMap.has(parentId)) childrenMap.set(parentId, [])
    childrenMap.get(parentId).push(node)
  })

  const rootNodes = childrenMap.get('') || []
  const tabs = []
  const liftedRootNodeIds = []

  rootNodes.forEach(tabSet => {
    if (tabSet.nodeType !== 'TAB_SET') return
    const tabNodes = (childrenMap.get(tabSet.id) || [])
      .filter(node => node.nodeType === 'TAB')
      .sort(compareOrder)
    if (tabNodes.length === 0) return

    liftedRootNodeIds.push(tabSet.id)
    tabNodes.forEach(tabNode => {
      tabs.push({
        id: tabNode.id,
        name: `form_tab_${tabNode.id}`,
        label: tabNode.props.label || tabNode.props.title || tabNode.nodeKey || '未命名页签',
        rootParentId: tabNode.id,
        tabSetId: tabSet.id
      })
    })
  })

  const liftedIds = new Set(liftedRootNodeIds.map(id => String(id)))
  return {
    tabs,
    liftedRootNodeIds,
    hasBaseContent: rootNodes.some(node => !liftedIds.has(String(node.id)))
  }
}
