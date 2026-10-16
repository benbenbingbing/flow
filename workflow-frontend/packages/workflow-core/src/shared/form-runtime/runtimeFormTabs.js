import { createFormNodeVisibility } from './nodeProjection.js'
import { safeParseConfig } from '../config-runtime/index.js'
import { formRelatedContentsAt } from '../form-related-content.js'

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

export function resolveRuntimeFormTabLayout(form, visibilityOptions = {}) {
  // 弹窗、抽屉和全屏入口归属 OWNER；即使所有布局节点都提升为 Tab，
  // 仍需保留基本信息区挂载这些入口，否则已发布的展示配置没有运行时入口。
  const hasOwnerRelatedContent = formRelatedContentsAt(form?.viewCompositions).length > 0
  if (!form || form.customComponent) {
    return {
      tabs: [],
      liftedRootNodeIds: [],
      hasBaseContent: hasOwnerRelatedContent || Boolean(form?.customComponent || form?.fields?.length)
    }
  }

  const nodes = (form.nodes || []).map(normalizeNode).sort(compareOrder)
  if (nodes.length === 0) {
    return {
      tabs: [],
      liftedRootNodeIds: [],
      hasBaseContent: hasOwnerRelatedContent || Boolean(form.fields?.length)
    }
  }

  const isVisible = createFormNodeVisibility(nodes, visibilityOptions)
  const childrenMap = new Map()
  nodes.forEach(node => {
    const parentId = node.parentId || ''
    if (!childrenMap.has(parentId)) childrenMap.set(parentId, [])
    childrenMap.get(parentId).push(node)
  })

  const rootNodes = childrenMap.get('') || []
  const tabs = []
  const liftedRootNodeIds = []
  let defaultActiveTabName = ''

  rootNodes.forEach(tabSet => {
    if (tabSet.nodeType !== 'TAB_SET') return
    const tabNodes = (childrenMap.get(tabSet.id) || [])
      .filter(node => node.nodeType === 'TAB')
      .sort(compareOrder)
    if (tabNodes.length === 0) return

    // 隐藏的集合仍从基本信息中排除，避免换一条路径重新渲染。
    liftedRootNodeIds.push(tabSet.id)
    if (!isVisible(tabSet)) return
    const configuredDefault = String(tabSet.props.defaultActiveTabKey || '')
    tabNodes.filter(isVisible).forEach(tabNode => {
      const tab = {
        id: tabNode.id,
        name: `form_tab_${tabNode.id}`,
        label: tabNode.props.label || tabNode.props.title || tabNode.nodeKey || '未命名页签',
        rootParentId: tabNode.id,
        tabSetId: tabSet.id,
        stableKey: tabNode.nodeKey || tabNode.id,
        defaultActive: configuredDefault !== ''
          && [String(tabNode.nodeKey || ''), String(tabNode.id)].includes(configuredDefault)
      }
      tabs.push(tab)
      if (!defaultActiveTabName && tab.defaultActive) {
        defaultActiveTabName = tab.name
      }
    })
  })

  const liftedIds = new Set(liftedRootNodeIds.map(id => String(id)))
  return {
    tabs,
    liftedRootNodeIds,
    hasBaseContent: hasOwnerRelatedContent || rootNodes.some(node => !liftedIds.has(String(node.id)) && isVisible(node)),
    defaultActiveTabName: defaultActiveTabName || tabs[0]?.name || ''
  }
}
