const DEFAULT_PAGE_SIZE = 20
const MAX_PAGE_SIZE = 100
const LEGACY_PAGE_FALLBACK_STATUSES = new Set([404, 405])

/** 仅在新分页路由尚不存在时读取旧数组接口，其他错误保持原样抛出。 */
export async function loadEntityVersionConfigPageWithLegacyFallback(
  { loadPage, loadLegacy },
  params = {}
) {
  try {
    return await loadPage()
  } catch (error) {
    if (!isLegacyPageRouteMiss(error)) throw error
  }
  return paginateLegacyEntityVersionConfigs(await loadLegacy(), params)
}

function isLegacyPageRouteMiss(error) {
  const status = Number(error?.status)
  if (LEGACY_PAGE_FALLBACK_STATUSES.has(status)) return true
  // 旧 Controller 会把静态路径 page 命中 /configs/{entityCode}，因此返回这一条
  // 稳定的 400；只能识别该精确路由冲突，不能把任意参数错误降级成旧列表请求。
  return status === 400 && String(error?.message || '').trim() === '实体不存在: page'
}

/**
 * 将旧 Controller 返回的配置摘要数组适配为当前分页契约。
 *
 * 该逻辑只用于 /configs/page 在滚动部署中确认新路由不存在后的回退；筛选语义
 * 必须与新服务端一致，尤其 enabled=false 要包含显式停用和未配置行。
 */
export function paginateLegacyEntityVersionConfigs(payload, params = {}) {
  const keyword = String(params.keyword || '').trim().toLowerCase()
  const enabled = normalizeEnabled(params.enabled)
  const pageNum = boundedInteger(params.pageNum, 1)
  const pageSize = boundedInteger(
    params.pageSize,
    DEFAULT_PAGE_SIZE,
    MAX_PAGE_SIZE
  )
  const filtered = (Array.isArray(payload) ? payload : []).filter(row => {
    const searchable = `${String(row?.entityName || '')}\n${String(row?.entityCode || '')}`
      .toLowerCase()
    if (keyword && !searchable.includes(keyword)) return false
    // 旧发布模型可能出现 enabled=true 但 runtimeEnabled=false 的草稿；筛选必须
    // 以运行态优先，未配置或过渡占位行同样归入“未启用”。
    const runtimeEnabled = (row?.runtimeEnabled ?? row?.enabled) === true
    return enabled === undefined || runtimeEnabled === enabled
  })
  const start = Math.min((pageNum - 1) * pageSize, filtered.length)
  return {
    records: filtered.slice(start, start + pageSize),
    total: filtered.length,
    pageNum,
    pageSize
  }
}

function normalizeEnabled(value) {
  if (value === true || value === 'true') return true
  if (value === false || value === 'false') return false
  return undefined
}

function boundedInteger(value, fallback, maximum = Number.MAX_SAFE_INTEGER) {
  const number = Number(value)
  if (!Number.isFinite(number)) return fallback
  return Math.min(maximum, Math.max(1, Math.trunc(number)))
}
