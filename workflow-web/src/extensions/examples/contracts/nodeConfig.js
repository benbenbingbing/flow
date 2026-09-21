/**
 * 节点配置升级钩子：将 v1 的单个 fieldCode 转为 v2 的 fieldCodes 数组。
 * 返回新配置且保留未知属性；不修改发布快照，也不读写业务数据。
 * fromVersion/toVersion 为配置结构版本（snapshotVersion），不是组件实现版本。
 */
export function migrateSummaryConfig({ fromVersion, toVersion, config = {} }) {
  if (fromVersion >= 2 || toVersion < 2) return { ...config }
  const { fieldCode, ...rest } = config
  return { ...rest, fieldCodes: Array.isArray(rest.fieldCodes) ? [...rest.fieldCodes] : fieldCode ? [fieldCode] : [] }
}
