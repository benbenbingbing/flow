import { getExtensionCatalog } from './core/catalog.js'
import { getBuiltInFormFieldComponentNames } from './core/registries/formFieldRegistry.js'

/** 当前构建实际安装的全部十类扩展，包含平台内置与可选示例。 */
export function getBundledExtensionManifest() { return getExtensionCatalog() }
/** 服务端目前只治理四类 UI 扩展；保留既有接口类型边界。 */
export function getManagedExtensionManifest() {
  return getExtensionCatalog().filter(item => item.managed && !(item.type === 'FIELD' && item.origin === 'PLATFORM'))
}
export function isPlatformBuiltInUiExtension(type, name) {
  return String(type || '').replace(/^UI_/, '').toUpperCase() === 'FIELD'
    && getBuiltInFormFieldComponentNames().includes(String(name || '').toLowerCase())
}

export function validateBundledExtensionManifest(manifest = getBundledExtensionManifest()) {
  const issues = []
  const ids = new Set()
  manifest.forEach((item, index) => {
    const location = item?.id || `第 ${index + 1} 项`
    if (!item?.id || !item?.type || !item?.name || !item?.label) {
      issues.push(`${location}: 缺少 id、type、name 或 label`)
    }
    if (!Number.isInteger(item?.version) || item.version < 1) {
      issues.push(`${location}: version 必须是正整数`)
    }
    if (!Array.isArray(item?.configSchema)) {
      issues.push(`${location}: configSchema 必须是数组`)
    }
    if (!item?.capabilities || typeof item.capabilities !== 'object') {
      issues.push(`${location}: capabilities 必须是对象`)
    }
    if (!Array.isArray(item?.permissions)) {
      issues.push(`${location}: permissions 必须是数组`)
    }
    if (ids.has(item?.id)) {
      issues.push(`${location}: 扩展 id 重复`)
    }
    ids.add(item?.id)
  })
  return issues
}
