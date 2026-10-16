/** 按运行时的锚点规则定位关联内容，节点 ID 和稳定 nodeKey 均可用于绑定。 */
export function formRelatedContentsAt(items, node = null, { preview = false, side = 'AFTER' } = {}) {
  const keys = new Set([node?.id, node?.nodeKey].filter(value => value != null && value !== '').map(String))
  return (items || []).filter(item => {
    if (item?.config?.enabled === false) return false
    // 设计态仅展开内嵌布局，不执行关联查询，也不将弹窗误画成内嵌表单。
    const position = String(item?.config?.presentation?.position || 'INLINE').toUpperCase()
    if (preview && !['INLINE', 'TAB'].includes(position)) return false
    // 节点前锚点用于在关联内容下方继续插入字段；缺省保持历史的节点后展示。
    if (String(item.config?.presentation?.anchorSide || 'AFTER') !== side) return false
    const anchorType = String(item.anchorType || '').toUpperCase()
    return node
      ? anchorType === 'FORM_NODE' && keys.has(String(item.anchorKey || ''))
      : anchorType === 'OWNER'
  }).sort((left, right) => Number(left.orderKey || 0) - Number(right.orderKey || 0))
}
