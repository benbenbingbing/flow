import { normalizeRuntimeNodes } from '@flow/workflow-core/form-runtime/nodeProjection'
import { runtimeFieldState } from '@flow/workflow-core/form-runtime/formModel'
import { resolveFormFieldExtensionName } from '@flow/workflow-core/form-field-extension'

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
      if (item.kind === 'field' && String(item.field.fieldCode || item.field.fieldKey) === String(fieldCode)) groups.push(...ancestors)
      if (item.children) visit(item.children, [...ancestors, item.id])
    }
  }
  visit(tree, []); return [...new Set(groups)]
}
