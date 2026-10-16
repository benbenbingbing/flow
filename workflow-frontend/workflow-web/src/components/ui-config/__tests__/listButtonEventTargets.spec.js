import assert from 'node:assert/strict'
import { findEventBinding, listButtonEventOptions, listEventTargets } from '../listButtonEventTargets.js'

const button = (key, extra = {}) => ({ key, label: key, type: 'custom', customMode: 'event', ...extra })
const options = listButtonEventOptions(
  [button('shared'), button('export'), button('create', { type: 'built-in' }), button('handler', { customMode: 'handler' })],
  [button('shared'), button('archive', { enabled: false }), button('')]
)
assert.equal(options.length, 4, '仅业务接口按钮支持服务端事件；停用按钮仍可提前配置')
const rowTargets = listEventTargets('ROW_BUTTON_CLICK', options)
const toolbarTargets = listEventTargets('TOOLBAR_BUTTON_CLICK', options)
assert.deepEqual(rowTargets.map(target => target.targetKey), ['shared', 'archive', ''])
assert.deepEqual(toolbarTargets.map(target => target.targetKey), ['shared', 'export', ''])
assert.deepEqual(listEventTargets('LIST_LOAD', options), [{ targetType: 'OWNER', targetKey: '' }])

const publicBinding = { targetType: 'OWNER', eventCode: 'ROW_BUTTON_CLICK' }
const specificBinding = { targetType: 'BUTTON', targetKey: 'shared', eventCode: 'ROW_BUTTON_CLICK' }
const rows = [publicBinding, specificBinding]
assert.equal(findEventBinding(rows, 'ROW_BUTTON_CLICK', rowTargets[0]), specificBinding)
assert.equal(findEventBinding(rows, 'ROW_BUTTON_CLICK', rowTargets[1]), undefined, '公共链和其他按钮不占用新按钮绑定')
assert.equal(findEventBinding(rows, 'ROW_BUTTON_CLICK', rowTargets[2]), publicBinding)
assert.equal(findEventBinding(rows, 'TOOLBAR_BUTTON_CLICK', toolbarTargets[0]), undefined, '行按钮与同编码工具栏按钮互不占用')
assert.equal(findEventBinding(rows, 'LIST_LOAD', { targetType: 'OWNER' }), undefined)
console.log('list button event targets passed: exact target uniqueness, shared defaults, separate positions, executable buttons')
