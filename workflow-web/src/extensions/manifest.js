import {
  getCustomFormComponentOptions,
  getCustomListComponentOptions
} from '@/utils/customComponentRegistry'
import { getCellComponentOptions } from '@/utils/listCellRegistry'
import { getFormNodeComponentOptions } from '@/utils/formNodeRegistry'
import {
  getBuiltInFormFieldComponentNames,
  getFormFieldComponentOptions,
  getRegisteredFormFieldComponentOptions
} from '@/components/form-fields'

const builtInFormFieldNames = new Set(
  getBuiltInFormFieldComponentNames().map(name => name.toLowerCase())
)

export function getBundledExtensionManifest() {
  const descriptors = [
    ...getCustomFormComponentOptions().map(item => governedDescriptor('FORM', item)),
    ...getCustomListComponentOptions().map(item => governedDescriptor('LIST', item)),
    ...getFormNodeComponentOptions().map(item => governedDescriptor('NODE', item)),
    ...getFormFieldComponentOptions().map(item => governedDescriptor('FIELD', item)),
    ...getCellComponentOptions().map(item => governedDescriptor('LIST_CELL', item))
  ]
  const unique = new Map()
  descriptors.forEach(item => unique.set(item.id, item))
  return Array.from(unique.values()).sort((left, right) => left.id.localeCompare(right.id))
}

export function getManagedExtensionManifest() {
  const descriptors = [
    ...getCustomFormComponentOptions().map(item => governedDescriptor('FORM', item)),
    ...getCustomListComponentOptions().map(item => governedDescriptor('LIST', item)),
    ...getFormNodeComponentOptions().map(item => governedDescriptor('NODE', item)),
    ...getRegisteredFormFieldComponentOptions().map(item =>
      governedDescriptor('FIELD', item))
  ]
  const unique = new Map()
  descriptors.forEach(item => unique.set(item.id, item))
  return Array.from(unique.values())
    .sort((left, right) => left.id.localeCompare(right.id))
}

export function isPlatformBuiltInUiExtension(type, name) {
  return String(type || '').replace(/^UI_/, '').toUpperCase() === 'FIELD'
    && builtInFormFieldNames.has(String(name || '').toLowerCase())
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

function governedDescriptor(type, descriptor = {}) {
  const version = positiveInteger(descriptor.version)
  const snapshotVersion = positiveInteger(descriptor.snapshotVersion)
  const name = descriptor.name || descriptor.value || descriptor.type
  return {
    id: `${type}:${name}@${version}`,
    type,
    name,
    label: descriptor.label || name,
    description: descriptor.description || '',
    version,
    snapshotVersion,
    configSchema: Array.isArray(descriptor.configSchema) ? descriptor.configSchema : [],
    capabilities: descriptor.capabilities || {},
    permissions: Array.isArray(descriptor.permissions) ? descriptor.permissions : [],
    supportedModes: Array.isArray(descriptor.supportedModes) ? descriptor.supportedModes : [],
    migrationSupported: snapshotVersion > 1 || descriptor.migrationSupported === true,
    deprecatedAt: descriptor.deprecatedAt || null,
    source: descriptor.source || 'bundled'
  }
}

function positiveInteger(value) {
  const normalized = Number(value || 1)
  return Number.isInteger(normalized) && normalized > 0 ? normalized : 1
}
