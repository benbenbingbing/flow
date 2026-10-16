export const SIDEBAR_DEFAULT_WIDTH = 200
export const SIDEBAR_MIN_WIDTH = 180
export const SIDEBAR_MAX_WIDTH = 480
export const SIDEBAR_COLLAPSED_WIDTH = 64
export const SIDEBAR_RESIZE_STEP = 16

export const SIDEBAR_WIDTH_STORAGE_KEY = 'workflow:sidebar-width'

/**
 * 将用户输入或持久化的侧栏宽度收敛到桌面布局允许的范围内。
 * 无效值会回退到默认宽度，避免损坏的 localStorage 数据挤压主内容区。
 */
export function normalizeSidebarWidth(value) {
  if (value === null || value === undefined || value === '') {
    return SIDEBAR_DEFAULT_WIDTH
  }

  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) {
    return SIDEBAR_DEFAULT_WIDTH
  }

  return Math.min(
    SIDEBAR_MAX_WIDTH,
    Math.max(SIDEBAR_MIN_WIDTH, Math.round(numericValue))
  )
}

/**
 * 根据拖拽起点计算新的侧栏宽度，并统一应用最小、最大宽度限制。
 */
export function calculateSidebarWidth(startWidth, startPointerX, currentPointerX) {
  const initialWidth = normalizeSidebarWidth(startWidth)
  const pointerDelta = Number(currentPointerX) - Number(startPointerX)
  if (!Number.isFinite(pointerDelta)) {
    return initialWidth
  }
  return normalizeSidebarWidth(initialWidth + pointerDelta)
}

function resolveStorage(storage) {
  if (storage) return storage
  if (typeof window === 'undefined') return null

  try {
    return window.localStorage
  } catch {
    return null
  }
}

/**
 * 只读取设备上的侧栏宽度；折叠状态由账号设置提供，忽略旧浏览器通用值。
 */
export function readSidebarLayout(storage) {
  const targetStorage = resolveStorage(storage)
  if (!targetStorage) {
    return { width: SIDEBAR_DEFAULT_WIDTH }
  }

  try {
    return {
      width: normalizeSidebarWidth(targetStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY))
    }
  } catch {
    return { width: SIDEBAR_DEFAULT_WIDTH }
  }
}

/**
 * 仅保存侧栏宽度，避免拖拽布局意外创建或覆盖用户的折叠偏好。
 * 存储不可用不应影响导航本身，因此失败时仅返回 false。
 */
export function persistSidebarLayout(layout, storage) {
  const targetStorage = resolveStorage(storage)
  if (!targetStorage) return false

  try {
    targetStorage.setItem(
      SIDEBAR_WIDTH_STORAGE_KEY,
      String(normalizeSidebarWidth(layout?.width))
    )
    return true
  } catch {
    return false
  }
}
