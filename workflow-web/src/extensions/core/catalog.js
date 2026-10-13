let installedDescriptors = []
const managedTypes = new Set(['FORM', 'LIST', 'FIELD'])

/** 安装成功后发布纯元数据快照；目录不保存组件，执行实现只有各类型注册表一份。 */
export function publishExtensionCatalog(descriptors) {
  installedDescriptors = descriptors.map(entry => ({
    ...entry.metadata,
    id: `${entry.type}:${entry.name}@${entry.version}`,
    type: entry.type, name: entry.name, value: entry.name,
    label: entry.label, description: entry.description || '', version: entry.version,
    snapshotVersion: entry.metadata?.snapshotVersion || 1,
    configSchema: entry.metadata?.configSchema || [],
    // 同步目录时后端从能力文档检查实体字段类型，和设计器候选使用同一声明。
    capabilities: {
      ...entry.metadata?.capabilities,
      ...(entry.type === 'FIELD' ? { supportedFieldTypes: entry.metadata?.supportedFieldTypes || [] } : {})
    },
    permissions: entry.metadata?.permissions || [], supportedModes: entry.metadata?.supportedModes || [],
    supportedEntityCodes: entry.metadata?.supportedEntityCodes || [],
    deprecatedAt: entry.metadata?.deprecatedAt || null,
    source: 'bundled', origin: entry.origin, module: entry.module,
    sourceFile: entry.sourceFile, implementation: entry.implementation, targets: entry.targets || [],
    available: true, managed: managedTypes.has(entry.type)
  }))
}

/** 按类型/归属读取构建能力；返回副本，页面不能修改全局目录。 */
export function getExtensionCatalog({ type, origin } = {}) {
  return JSON.parse(JSON.stringify(installedDescriptors.filter(entry => (!type || entry.type === type) && (!origin || entry.origin === origin))))
}
