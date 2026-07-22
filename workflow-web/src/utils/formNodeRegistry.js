import { normalizeExtensionDescriptor } from '@/shared/config-runtime'

const nodeRegistry = new Map()

function normalizeVersion(version) {
  const normalized = Number(version)
  return Number.isFinite(normalized) && normalized > 0 ? normalized : 1
}

function parseRequestedVersion(version) {
  const normalized = Number(version)
  return Number.isFinite(normalized) && normalized > 0 ? normalized : undefined
}

function getVersionRegistry(name) {
  return nodeRegistry.get(name)
}

function resolveRegisteredDescriptor(name, version) {
  const versions = getVersionRegistry(name)
  if (!versions) return undefined
  if (version !== undefined && version !== null && version !== '') {
    const requestedVersion = parseRequestedVersion(version)
    return requestedVersion === undefined ? undefined : versions.get(requestedVersion)
  }
  return Array.from(versions.values())
    .sort((left, right) => right.version - left.version)[0]
}

export function registerFormNodeComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  descriptor.version = normalizeVersion(descriptor.version)
  descriptor.nodeTypes = Array.isArray(metadata.nodeTypes)
    ? metadata.nodeTypes.map(value => String(value).toUpperCase())
    : []
  descriptor.supportedBindings = Array.isArray(metadata.supportedBindings)
    ? metadata.supportedBindings.map(value => String(value).toUpperCase())
    : []
  descriptor.snapshotVersion = Number(metadata.snapshotVersion || 1)
  descriptor.migrateConfig = typeof metadata.migrateConfig === 'function'
    ? metadata.migrateConfig
    : null
  const versions = getVersionRegistry(descriptor.name) || new Map()
  versions.set(descriptor.version, descriptor)
  nodeRegistry.set(descriptor.name, versions)
}

export function getFormNodeDescriptor(name, version) {
  return resolveRegisteredDescriptor(name, version)
}

export function getFormNodeComponent(name, version) {
  return getFormNodeDescriptor(name, version)?.component
}

export function hasFormNodeComponent(name, version) {
  return Boolean(getFormNodeDescriptor(name, version))
}

export function getFormNodeComponentOptions() {
  return Array.from(nodeRegistry.values())
    .map(versions => Array.from(versions.values())
      .sort((left, right) => right.version - left.version)[0])
    .sort((left, right) => left.name.localeCompare(right.name))
    .map(({ component, migrateConfig, ...item }) => item)
}

export function resolveFormNodeDescriptor(node) {
  const componentName = node?.componentName
    || node?.props?.componentName
    || node?.props?.nodeComponent
  const componentVersion = node?.componentVersion
    ?? node?.props?.componentVersion
  const descriptor = getFormNodeDescriptor(componentName, componentVersion)
  if (!descriptor) return null
  const nodeType = String(node?.nodeType || '').toUpperCase()
  const bindingType = String(node?.bindingType || 'NONE').toUpperCase()
  if (descriptor.nodeTypes.length && !descriptor.nodeTypes.includes(nodeType)) {
    return null
  }
  if (descriptor.supportedBindings.length
      && !descriptor.supportedBindings.includes(bindingType)) {
    return null
  }
  return descriptor
}

export function migrateFormNodeConfig(node, descriptor) {
  if (!descriptor) return node?.props || {}
  const sourceVersion = Number(node?.snapshotVersion || node?.props?.snapshotVersion || 1)
  if (sourceVersion >= descriptor.snapshotVersion || !descriptor.migrateConfig) {
    return node?.props || {}
  }
  return descriptor.migrateConfig(
    node?.props || {},
    sourceVersion,
    descriptor.snapshotVersion
  )
}
