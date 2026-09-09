/**
 * 将服务端实体版本能力收敛为严格布尔值；未知或异常响应一律按关闭处理。
 */
export function normalizeEntityVersionCapabilities(value = {}) {
  const runtimeEnabled = value?.runtimeEnabled === true
  return {
    runtimeEnabled,
    historyReadable: value?.historyReadable === true,
    // 手工固化依赖运行策略，避免服务端异常组合导致前端暴露无效入口。
    manualCaptureEnabled:
      runtimeEnabled && value?.manualCaptureEnabled === true
  }
}

/**
 * 普通业务列表具备查看权限后，运行中的策略或可读历史都可提供版本入口。
 */
export function canShowEntityVersionAction({
  selectionScene = false,
  isSystemEntity = false,
  canViewVersions = false,
  runtimeEnabled = false,
  historyReadable = false
} = {}) {
  return !selectionScene
    && !isSystemEntity
    && canViewVersions
    && (runtimeEnabled || historyReadable)
}

/** 手工固化同时受用户权限、当前配置启用状态和 MANUAL 能力约束。 */
export function canCaptureEntityRecordVersion({
  hasCapturePermission = false,
  runtimeEnabled = false,
  manualCaptureEnabled = false
} = {}) {
  return hasCapturePermission && runtimeEnabled && manualCaptureEnabled
}
