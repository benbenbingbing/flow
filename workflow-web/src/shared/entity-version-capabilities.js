/**
 * 将服务端实体版本能力收敛为严格布尔值；未知或异常响应一律按关闭处理。
 */
export function normalizeEntityVersionCapabilities(value = {}) {
  const runtimeEnabled = value?.runtimeEnabled === true
  return {
    runtimeEnabled,
    // 手工固化依赖运行策略，避免服务端异常组合导致前端暴露无效入口。
    manualCaptureEnabled:
      runtimeEnabled && value?.manualCaptureEnabled === true
  }
}

/**
 * 版本入口只在普通业务列表、具备查看权限且实体运行策略启用时展示。
 */
export function canShowEntityVersionAction({
  selectionScene = false,
  isSystemEntity = false,
  canViewVersions = false,
  runtimeEnabled = false
} = {}) {
  return !selectionScene
    && !isSystemEntity
    && canViewVersions
    && runtimeEnabled
}

/** 手工固化同时受用户权限和实体已发布运行策略中的 MANUAL 能力约束。 */
export function canCaptureEntityRecordVersion({
  hasCapturePermission = false,
  manualCaptureEnabled = false
} = {}) {
  return hasCapturePermission && manualCaptureEnabled
}
