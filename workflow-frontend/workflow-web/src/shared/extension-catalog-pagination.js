export const CATALOG_FETCH_PAGE_SIZE = 200
const CATALOG_FETCH_BATCH_SIZE = 6
const MAX_CATALOG_FETCH_PAGES = 500

function resultRows(result) {
  const values = result?.list ?? result?.records ?? []
  return Array.isArray(values) ? values : []
}

/**
 * 读取符合筛选条件的完整远端扩展目录，供页面与本地待纳管扩展统一分页。
 * 页数以首个响应的 total 固定，避免异常响应造成无界请求。
 */
export async function loadAllExtensionCatalogRows(fetchPage, params = {}) {
  const firstResult = await fetchPage({
    ...params,
    pageNum: 1,
    pageSize: CATALOG_FETCH_PAGE_SIZE
  })
  const allRows = [...resultRows(firstResult)]
  const reportedTotal = Number(firstResult?.total)
  const total = Number.isFinite(reportedTotal) && reportedTotal >= 0
    ? Math.max(Math.ceil(reportedTotal), allRows.length)
    : allRows.length
  const pageCount = Math.max(
    1,
    Math.ceil(total / CATALOG_FETCH_PAGE_SIZE)
  )
  if (pageCount > MAX_CATALOG_FETCH_PAGES) {
    throw new Error('扩展目录返回的数据量异常，请缩小筛选范围后重试。')
  }

  for (
    let batchStart = 2;
    batchStart <= pageCount;
    batchStart += CATALOG_FETCH_BATCH_SIZE
  ) {
    const batchEnd = Math.min(
      pageCount,
      batchStart + CATALOG_FETCH_BATCH_SIZE - 1
    )
    const pageNumbers = Array.from(
      { length: batchEnd - batchStart + 1 },
      (_, index) => batchStart + index
    )
    const results = await Promise.all(pageNumbers.map(pageNum => fetchPage({
      ...params,
      pageNum,
      pageSize: CATALOG_FETCH_PAGE_SIZE
    })))
    results.forEach(result => allRows.push(...resultRows(result)))
  }
  return allRows
}

/** 将已合并的目录按当前页面大小切片，并把越界页收敛到最后一页。 */
export function paginateExtensionCatalogRows(values, pageNum, pageSize) {
  const allRows = Array.isArray(values) ? values : []
  const normalizedSize = Math.max(1, Number(pageSize) || 20)
  const maxPage = Math.max(1, Math.ceil(allRows.length / normalizedSize))
  const normalizedPage = Math.min(
    Math.max(1, Number(pageNum) || 1),
    maxPage
  )
  const start = (normalizedPage - 1) * normalizedSize
  return {
    list: allRows.slice(start, start + normalizedSize),
    total: allRows.length,
    pageNum: normalizedPage,
    pageSize: normalizedSize
  }
}
