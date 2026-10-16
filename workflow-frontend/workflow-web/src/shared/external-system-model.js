export const EXTERNAL_SYSTEM_PARAMETER_NAME_PATTERN =
  /^[A-Za-z][A-Za-z0-9_.-]{0,99}$/

/**
 * 校验外部系统参数。英文名作为定制接口读取参数的稳定键，因此忽略大小写后也不能重复。
 *
 * @param {Array<{nameZh?: unknown, nameEn?: unknown, value?: unknown}>} parameters 参数草稿
 * @returns {string} 空字符串表示校验通过，否则返回可直接展示的中文错误
 */
export function validateExternalSystemParameters(parameters = []) {
  if (parameters.length > 200) return '单个外部系统最多配置 200 个参数'

  const seenNames = new Set()

  for (let index = 0; index < parameters.length; index += 1) {
    const parameter = parameters[index] || {}
    const nameZh = String(parameter.nameZh ?? '').trim()
    const nameEn = String(parameter.nameEn ?? '').trim()
    const value = parameter.value == null ? '' : String(parameter.value)
    const rowNumber = index + 1

    if (!nameZh) return `第 ${rowNumber} 行参数缺少中文名`
    if (!nameEn) return `第 ${rowNumber} 行参数缺少英文名`
    if (!value.trim()) return `第 ${rowNumber} 行参数缺少参数值`
    if (value.length > 65535) return `第 ${rowNumber} 行参数值不能超过 65535 个字符`
    if (!EXTERNAL_SYSTEM_PARAMETER_NAME_PATTERN.test(nameEn)) {
      return `第 ${rowNumber} 行英文名不合法，应以字母开头且只包含字母、数字、下划线、点和短横线`
    }

    const normalizedName = nameEn.toLowerCase()
    if (seenNames.has(normalizedName)) {
      return `参数英文名不能重复（忽略大小写）：${nameEn}`
    }
    seenNames.add(normalizedName)
  }

  return ''
}

/**
 * 构造写接口参数：名称去除无意义首尾空格，参数值保持原样，并用当前行序固化展示顺序。
 * 前端临时 rowKey 不进入请求；已有 id 只用于详情回填兼容，服务端按聚合语义重建参数行。
 */
export function normalizeExternalSystemParameters(parameters = []) {
  return parameters.map((parameter, index) => ({
    ...(parameter.id ? { id: String(parameter.id) } : {}),
    nameZh: String(parameter.nameZh ?? '').trim(),
    nameEn: String(parameter.nameEn ?? '').trim(),
    value: parameter.value == null ? '' : String(parameter.value),
    sortOrder: index
  }))
}
