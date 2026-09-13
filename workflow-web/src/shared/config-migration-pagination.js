export const CONFIG_MIGRATION_PAGE_SIZES = Object.freeze([10, 20, 50, 100])
export const DEFAULT_CONFIG_MIGRATION_PAGE_SIZE = 20

/**
 * 创建配置迁移表格的独立分页状态，避免切换 Tab 时互相覆盖页码。
 */
export function createConfigMigrationPage(pageSize = DEFAULT_CONFIG_MIGRATION_PAGE_SIZE) {
  return {
    pageNum: 1,
    pageSize: normalizePageSize(pageSize),
    total: 0
  }
}

/**
 * 将服务端 PageResult 合并为 UI 分页状态，并兼容请求层生成的 list 别名。
 */
export function applyConfigMigrationPageResult(currentPage, payload) {
  const records = Array.isArray(payload?.records)
    ? payload.records
    : Array.isArray(payload?.list)
      ? payload.list
      : []
  const pageSize = normalizePageSize(payload?.pageSize ?? currentPage?.pageSize)
  const total = normalizeNonNegativeInteger(payload?.total)
  const requestedPage = normalizePositiveInteger(payload?.pageNum ?? currentPage?.pageNum)
  const lastPage = Math.max(1, Math.ceil(total / pageSize))

  return {
    records,
    page: {
      pageNum: Math.min(requestedPage, lastPage),
      pageSize,
      total
    }
  }
}

/**
 * 判断服务端结果是否因数据收缩落在越界空页；调用方应使用修正页码重查一次。
 */
export function shouldReloadConfigMigrationPage(requestedPage, normalizedResult) {
  const requestedPageNum = normalizePositiveInteger(requestedPage?.pageNum)
  return normalizedResult?.page?.total > 0
    && normalizedResult.records?.length === 0
    && normalizedResult.page.pageNum !== requestedPageNum
}

/**
 * 对必须完整保留在浏览器中的对比结果分页；完整数组仍供校验摘要和映射使用。
 */
export function paginateConfigMigrationRows(rows, currentPage) {
  const source = Array.isArray(rows) ? rows : []
  const pageSize = normalizePageSize(currentPage?.pageSize)
  const lastPage = Math.max(1, Math.ceil(source.length / pageSize))
  const pageNum = Math.min(normalizePositiveInteger(currentPage?.pageNum), lastPage)
  const start = (pageNum - 1) * pageSize
  return source.slice(start, start + pageSize)
}

/** 同步客户端分页总数，并在数据集缩小时收敛到最后一个有效页。 */
export function updateConfigMigrationClientPage(currentPage, total) {
  const pageSize = normalizePageSize(currentPage?.pageSize)
  const normalizedTotal = normalizeNonNegativeInteger(total)
  const lastPage = Math.max(1, Math.ceil(normalizedTotal / pageSize))
  return {
    pageNum: Math.min(normalizePositiveInteger(currentPage?.pageNum), lastPage),
    pageSize,
    total: normalizedTotal
  }
}

function normalizePageSize(value) {
  const normalized = normalizePositiveInteger(value, DEFAULT_CONFIG_MIGRATION_PAGE_SIZE)
  return Math.min(CONFIG_MIGRATION_PAGE_SIZES.at(-1), normalized)
}

function normalizePositiveInteger(value, fallback = 1) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? Math.trunc(number) : fallback
}

function normalizeNonNegativeInteger(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? Math.trunc(number) : 0
}
