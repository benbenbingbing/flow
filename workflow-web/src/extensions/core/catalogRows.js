/** 构建目录投影为管理页行；仅构建类型和平台必需项不提供未实现的后台纳管操作。 */
export function localExtensionRows(catalog, remoteKeys, filters = {}) {
  const rows = catalog.map(item => {
    const buildOnly = !item.managed || item.origin === 'PLATFORM'
    return {
      rowKey: `LOCAL:${item.id}`, id: null, capabilityType: `UI_${item.type}`,
      key: item.name, displayName: item.label, description: item.description,
      implementationVersion: item.version, snapshotVersion: item.snapshotVersion, contractVersion: 1,
      sourceType: 'FRONTEND_BUNDLE', sourceName: item.sourceFile,
      implementationClass: item.implementation?.path || '',
      implementationOrigin: item.origin === 'BUSINESS' ? 'CUSTOM' : item.origin,
      status: buildOnly ? 'BUILD_ONLY' : 'DISCOVERED', buildOnly,
      configured: false, available: true, enabled: buildOnly,
      visibilityScope: 'GLOBAL', entityCodes: item.supportedEntityCodes || [],
      supportedModes: item.supportedModes || [], supportedNodeTypes: item.nodeTypes || [],
      supportedBindings: item.supportedBindings || [], configSchema: item.configSchema || [],
      capabilities: item.capabilities || {}, dynamicExtraParams: false, localManifest: item
    }
  })
  return rows.filter(row => row.buildOnly || !remoteKeys.has(`${row.capabilityType}:${row.key}:${row.implementationVersion}`))
    .filter(row => !filters.capabilityType || row.capabilityType === filters.capabilityType)
    .filter(row => !filters.status || row.status === filters.status)
    .filter(row => !filters.implementationOrigin || row.implementationOrigin === filters.implementationOrigin)
    .filter(row => !filters.keyword || [row.key, row.displayName, row.description, row.sourceName].some(value => String(value || '').toLowerCase().includes(filters.keyword.toLowerCase())))
}
