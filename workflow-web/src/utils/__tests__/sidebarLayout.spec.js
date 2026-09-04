import assert from 'node:assert/strict'
import {
  SIDEBAR_COLLAPSED_STORAGE_KEY,
  SIDEBAR_DEFAULT_WIDTH,
  SIDEBAR_MAX_WIDTH,
  SIDEBAR_MIN_WIDTH,
  SIDEBAR_WIDTH_STORAGE_KEY,
  calculateSidebarWidth,
  normalizeSidebarWidth,
  persistSidebarLayout,
  readSidebarLayout
} from '../sidebarLayout.js'

assert.equal(normalizeSidebarWidth(null), SIDEBAR_DEFAULT_WIDTH)
assert.equal(normalizeSidebarWidth('invalid'), SIDEBAR_DEFAULT_WIDTH)
assert.equal(normalizeSidebarWidth(120), SIDEBAR_MIN_WIDTH)
assert.equal(normalizeSidebarWidth(520), SIDEBAR_MAX_WIDTH)
assert.equal(normalizeSidebarWidth(266.6), 267)

assert.equal(calculateSidebarWidth(200, 300, 360), 260)
assert.equal(calculateSidebarWidth(200, 300, 100), SIDEBAR_MIN_WIDTH)
assert.equal(calculateSidebarWidth(460, 100, 180), SIDEBAR_MAX_WIDTH)

const values = new Map([
  [SIDEBAR_WIDTH_STORAGE_KEY, '312'],
  [SIDEBAR_COLLAPSED_STORAGE_KEY, 'true']
])
const storage = {
  getItem(key) {
    return values.get(key) ?? null
  },
  setItem(key, value) {
    values.set(key, value)
  }
}

assert.deepEqual(readSidebarLayout(storage), { width: 312, collapsed: true })
assert.equal(persistSidebarLayout({ width: 240, collapsed: false }, storage), true)
assert.deepEqual(readSidebarLayout(storage), { width: 240, collapsed: false })

const unavailableStorage = {
  getItem() {
    throw new Error('storage disabled')
  },
  setItem() {
    throw new Error('storage disabled')
  }
}
assert.deepEqual(readSidebarLayout(unavailableStorage), {
  width: SIDEBAR_DEFAULT_WIDTH,
  collapsed: false
})
assert.equal(persistSidebarLayout({ width: 240, collapsed: true }, unavailableStorage), false)

console.log('sidebar layout tests passed')
