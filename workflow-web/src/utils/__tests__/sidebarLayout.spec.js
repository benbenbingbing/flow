import assert from 'node:assert/strict'
import {
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
  ['workflow:sidebar-collapsed', 'true']
])
const storage = {
  getItem(key) {
    return values.get(key) ?? null
  },
  setItem(key, value) {
    values.set(key, value)
  }
}

// 旧设备折叠值不带用户归属，不能覆盖服务端给出的系统默认值或当前用户偏好。
assert.deepEqual(readSidebarLayout(storage), { width: 312 })
assert.equal(persistSidebarLayout({ width: 240, collapsed: false }, storage), true)
assert.deepEqual(readSidebarLayout(storage), { width: 240 })
assert.equal(values.get('workflow:sidebar-collapsed'), 'true', '调宽不得写入折叠状态')
values.delete('workflow:sidebar-collapsed')
persistSidebarLayout({ width: 260, collapsed: true }, storage)
assert.equal(values.has('workflow:sidebar-collapsed'), false, '新浏览器也不创建通用折叠值')

const unavailableStorage = {
  getItem() {
    throw new Error('storage disabled')
  },
  setItem() {
    throw new Error('storage disabled')
  }
}
assert.deepEqual(readSidebarLayout(unavailableStorage), {
  width: SIDEBAR_DEFAULT_WIDTH
})
assert.equal(persistSidebarLayout({ width: 240, collapsed: true }, unavailableStorage), false)

console.log('sidebar layout tests passed')
