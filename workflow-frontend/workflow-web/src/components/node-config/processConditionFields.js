import { getProcessConditionFieldType } from '../../shared/process-config/index.js'

/**
 * 为流程条件补齐选择字段的代码表选项，不修改接口返回的原字段。
 * 同一代码表只加载一次；绑定代码表后以代码项编码作为条件值，不能回退到旧内嵌选项。
 * 单个代码表失败仅清空其候选，其他字段仍可使用；流程切换的过期响应由调用方丢弃。
 */
export async function loadProcessConditionFieldOptions(fields, loadDictionary) {
  const dictionaryCode = field => getProcessConditionFieldType(field) === 'select'
    ? String(field.dictType || '').trim()
    : ''
  const codes = [...new Set(fields.map(dictionaryCode).filter(Boolean))]
  const results = await Promise.allSettled(codes.map(code => loadDictionary(code)))
  const optionsByCode = new Map(codes.map((code, index) => {
    const result = results[index]
    if (result.status === 'rejected') {
      console.error('加载流程条件代码表失败:', code, result.reason)
    }
    return [code, result.status === 'fulfilled' ? flattenDictionaryItems(result.value) : []]
  }))
  return fields.map(field => {
    const code = dictionaryCode(field)
    return getProcessConditionFieldType(field) === 'select'
      ? { ...field, options: code ? optionsByCode.get(code) : [] }
      : field
  })
}

/** 代码表可分层维护；各级代码项都可作为比较值，停用项保留名称供历史条件回显。 */
function flattenDictionaryItems(items) {
  if (!Array.isArray(items)) return []
  return items.flatMap(item => [
    { value: item.itemCode, label: item.itemLabel, disabled: String(item.status) !== '0' },
    ...flattenDictionaryItems(item.children)
  ])
}
