import assert from 'node:assert/strict'
import {
  SIDEBAR_MENU_REFRESH_EVENT,
  SIDEBAR_MENU_REVISION_KEY,
  notifySidebarMenuChanged
} from '../menuRefresh.js'

const originalWindow = globalThis.window
const originalCustomEvent = globalThis.CustomEvent
const storageWrites = []
const events = []

globalThis.CustomEvent = class {
  constructor(type, options) {
    this.type = type
    this.detail = options?.detail
  }
}
globalThis.window = {
  localStorage: {
    setItem(key, value) {
      storageWrites.push({ key, value })
    }
  },
  dispatchEvent(event) {
    events.push(event)
  }
}

notifySidebarMenuChanged()
assert.equal(storageWrites.length, 1)
assert.equal(storageWrites[0].key, SIDEBAR_MENU_REVISION_KEY)
assert.equal(events.length, 1)
assert.equal(events[0].type, SIDEBAR_MENU_REFRESH_EVENT)
assert.equal(events[0].detail.revision, storageWrites[0].value)

globalThis.window.localStorage.setItem = () => {
  throw new Error('storage disabled')
}
notifySidebarMenuChanged()
assert.equal(events.length, 2)
assert.equal(events[1].type, SIDEBAR_MENU_REFRESH_EVENT)

globalThis.window = originalWindow
globalThis.CustomEvent = originalCustomEvent

console.log('menu refresh tests passed')

