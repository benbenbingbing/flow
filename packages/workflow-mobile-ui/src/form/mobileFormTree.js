import { normalizeRuntimeNodes } from '@flow/workflow-core/form-runtime/nodeProjection'
import { runtimeFieldState } from '@flow/workflow-core/form-runtime/formModel'
import { resolveFormFieldExtensionName } from '@flow/workflow-core/form-field-extension'
import { resolveRuntimeFormTabLayout } from '@flow/workflow-core/form-runtime/runtimeFormTabs'

/** 移动布局投影：Tab 转分组、Grid 纵向排列，原节点身份和字段绑定全部保留。 */
export function buildMobileFormTree(form, fields, options, linkage = {}) {
  const nodes = normalizeRuntimeNodes(form.nodes || [])
  const byId = new Map(fields.map(field => [String(field.id), field]))
  const visible = node => runtimeFieldState({ id: node.id, fieldCode: node.nodeKey }, options, linkage).visible
  const seen = new Set()
  const visit = parent => nodes.filter(node => String(node.parentId || '') === String(parent)).flatMap(node => {
    if (seen.has(String(node.id))) throw new Error('表单分组存在循环引用')
    seen.add(String(node.id))
    if (!visible(node)) return []
    if (node.componentName && !resolveFormFieldExtensionName(node)) return [{ kind: 'extension', id: String(node.id), node }]
    const field = byId.get(String(node.id))
    if (field) return runtimeFieldState(field, options, linkage).visible ? [{ kind: 'field', id: String(node.id), field, extensionName: resolveFormFieldExtensionName(node) }] : []
    if (node.nodeType === 'TEXT') return [{ kind: 'text', id: String(node.id), text: node.props.text || node.props.content || node.props.label || '', title: node.props.textStyle === 'SECTION_TITLE' }]
    if (node.nodeType === 'ACTION_SLOT') return [{ kind: 'actions', id: String(node.id), slotKey: node.nodeKey }]
    if (node.componentName) return [{ kind: 'extension', id: String(node.id), node }]
    const children = visit(node.id)
    if (!children.length) return []
    if (['GRID', 'TAB_SET'].includes(node.nodeType)) {
      if (node.nodeType === 'TAB_SET') {
        const target = node.props.defaultActiveTabKey
        children.forEach((child, index) => { child.defaultExpanded = target ? [child.id, child.nodeKey].includes(String(target)) : index === 0 })
      }
      return children
    }
    return [{ kind: 'group', id: String(node.id), nodeKey: node.nodeKey, title: node.props.label || node.props.title || node.nodeKey || '资料', defaultExpanded: node.props.defaultExpanded !== false, children }]
  })
  if (nodes.length) return visit(options.rootParentId || '')
  return fields.filter(field => runtimeFieldState(field, options, linkage).visible).map((field, index) => ({ kind: 'field', id: String(field.id || field.fieldCode || index), field }))
}

/** 提交错误定位递归展开所有祖先，支持分组里继续嵌套分组。 */
export function groupsContainingField(tree, fieldCode) {
  const groups = []
  function visit(items, ancestors) {
    for (const item of items) {
      if ((item.kind === 'field' && String(item.field.fieldCode || item.field.fieldKey) === String(fieldCode)) || (item.kind === 'extension' && item.id === String(fieldCode))) groups.push(...ancestors)
      if (item.children) visit(item.children, [...ancestors, item.id])
    }
  }
  visit(tree, []); return [...new Set(groups)]
}

/** 复用 PC 的根级 Tab 布局，只移除被提升 Tab 的折叠外壳；嵌套 Tab 和区块保持原树结构。 */
export function buildMobileFormPages(form, tree, relatedContents = [], tabbed = false) {
  const basic = { name: 'basic', label: '基本信息', items: tree, relatedContents: [] }
  if (!tabbed) return { pages: [{ ...basic, relatedContents }], defaultActiveTabName: 'basic' }
  const layout = resolveRuntimeFormTabLayout(form)
  const tabIds = new Set(layout.tabs.map(tab => String(tab.id)))
  basic.items = tree.filter(item => !tabIds.has(item.id))
  const pages = layout.tabs.map(tab => ({ ...tab, id: String(tab.id), items: tree.find(item => item.id === String(tab.id))?.children || [], relatedContents: [] }))
  const nodes = normalizeRuntimeNodes(form.nodes || []), byId = new Map(nodes.map(node => [String(node.id), node]))
  // 关联内容跟随锚点所在的第一层页签；表单级内容保留在基本信息中，避免提升页签后丢失或重复展示。
  for (const composition of relatedContents) {
    let node = String(composition.anchorType).toUpperCase() === 'FORM_NODE' ? nodes.find(node => [node.id, node.nodeKey].map(String).includes(String(composition.anchorKey))) : null
    let target
    const seen = new Set()
    while (node && !seen.has(String(node.id))) {
      seen.add(String(node.id))
      target = pages.find(page => page.id === String(node.id))
      if (target) break
      node = byId.get(String(node.parentId))
    }
    const owner = target || basic
    owner.relatedContents.push(composition)
  }
  const showBasic = layout.hasBaseContent || !pages.length || basic.relatedContents.length > 0
  if (showBasic) pages.unshift(basic)
  return { pages, defaultActiveTabName: showBasic ? 'basic' : layout.defaultActiveTabName }
}
