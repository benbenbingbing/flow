const ENTITY_ACTION_BY_FORM_MODE = Object.freeze({
  create: 'create',
  edit: 'update',
  approve: 'approve'
})

function firstText(...values) {
  for (const value of values) {
    const text = String(value ?? '').trim()
    if (text) return text
  }
  return ''
}

/**
 * 从表单运行上下文构造最小实体文件上传授权坐标。
 *
 * 设计预览、纯展示、子表和缺少实体坐标的调用不冒充根实体业务操作，返回
 * null 后继续走原有通用存储权限；根实体新增、编辑和审批表单绑定对应动作。
 */
export function resolveEntityFileUploadContext(context, field) {
  // 只有真实根实体表单会携带 record 且不带 parentField；设计预览以及需要
  // 独立关系授权的子表单继续使用原上传入口，不能冒充根实体数据操作。
  if (!context?.record || context?.parentField) return null
  const mode = firstText(context?.mode).toLowerCase()
  const action = ENTITY_ACTION_BY_FORM_MODE[mode]
  const entityCode = firstText(
    context?.form?.entityCode,
    context?.entityCode,
    context?.entityDefinition?.entityCode
  )
  const fieldCode = firstText(
    field?.fieldCode,
    field?.fieldKey,
    context?.field?.fieldCode,
    context?.field?.fieldKey
  )
  if (!action || !entityCode || !fieldCode) return null
  return { entityCode, action, fieldCode }
}
