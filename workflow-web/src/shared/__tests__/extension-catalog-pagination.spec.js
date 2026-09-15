import assert from 'node:assert/strict'
import {
  CATALOG_FETCH_PAGE_SIZE,
  loadAllExtensionCatalogRows,
  paginateExtensionCatalogRows
} from '../extension-catalog-pagination.js'

function rows(count, offset = 0) {
  return Array.from({ length: count }, (_, index) => ({ id: offset + index }))
}

async function verifyRemoteBoundary(total) {
  const calls = []
  const result = await loadAllExtensionCatalogRows(async params => {
    calls.push(params)
    const start = (params.pageNum - 1) * params.pageSize
    const size = Math.max(0, Math.min(params.pageSize, total - start))
    return { records: rows(size, start), total }
  }, { implementationOrigin: 'CUSTOM' })
  assert.equal(result.length, total)
  assert.equal(calls.length, Math.max(
    1,
    Math.ceil(total / CATALOG_FETCH_PAGE_SIZE)
  ))
  assert.ok(calls.every(call =>
    call.implementationOrigin === 'CUSTOM'
      && call.pageSize === CATALOG_FETCH_PAGE_SIZE))
}

await verifyRemoteBoundary(0)
await verifyRemoteBoundary(200)
await verifyRemoteBoundary(201)

const combined = [
  { id: 'local-1' },
  ...rows(20).map(item => ({ ...item, id: `remote-${item.id}` }))
]
const secondPage = paginateExtensionCatalogRows(combined, 2, 20)
assert.equal(secondPage.total, 21)
assert.equal(secondPage.list.length, 1)
assert.equal(secondPage.list[0].id, 'remote-19')

const recoveredPage = paginateExtensionCatalogRows(combined.slice(0, 5), 3, 20)
assert.equal(recoveredPage.pageNum, 1)
assert.equal(recoveredPage.list.length, 5)

console.log('extension catalog pagination tests passed')
