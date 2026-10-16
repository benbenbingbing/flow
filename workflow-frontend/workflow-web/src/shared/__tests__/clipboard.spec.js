import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'

import {
  normalizeClipboardText,
  writeClipboardText
} from '../clipboard.js'

assert.equal(normalizeClipboardText(null), '')
assert.equal(normalizeClipboardText(0), '0')
assert.equal(normalizeClipboardText(false), 'false')
assert.equal(normalizeClipboardText(['研发', '测试']), '["研发","测试"]')
assert.equal(normalizeClipboardText({ name: '需求' }), '{"name":"需求"}')

let clipboardValue = ''
await writeClipboardText('当前列内容', {
  navigator: {
    clipboard: {
      writeText: async value => {
        clipboardValue = value
      }
    }
  }
})
assert.equal(clipboardValue, '当前列内容')

let fallbackTextarea
await writeClipboardText('兼容复制', {
  navigator: {},
  document: {
    body: {
      appendChild: element => {
        fallbackTextarea = element
      }
    },
    createElement: () => ({
      style: {},
      setAttribute() {},
      select() {},
      remove() {}
    }),
    execCommand: command => command === 'copy'
  }
})
assert.equal(fallbackTextarea.value, '兼容复制')

await assert.rejects(
  writeClipboardText('失败', { navigator: {}, document: null }),
  /不支持剪贴板写入/
)

const root = process.cwd()
const listDesigner = readFileSync(
  path.join(root, 'src/views/EntityListConfigDesign.vue'),
  'utf8'
)
const entityDataTable = readFileSync(
  path.join(root, 'src/views/entity/components/EntityDataTable.vue'),
  'utf8'
)
assert.match(listDesigner, /editingColumnConfig\.quickCopy/)
assert.match(listDesigner, /active-text="是"/)
assert.match(entityDataTable, /getColumnConfig\(field\)\.quickCopy === true/)
assert.match(entityDataTable, /ListQuickCopyCell/)

console.log('clipboard tests passed')
