import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeBuiltInActionBar, builtInActionBarError, resolveBuiltInButton, builtInButtonStyle, builtInActionBarStyle, builtInActionBarHasPosition } from '../src/shared/builtin-button-appearance.js'

test('历史配置不注入默认值，各位置的文案和样式仍由原宿主决定', () => {
  for (const input of [undefined, null, {}, [], '']) assert.deepEqual(normalizeBuiltInActionBar(input), {})
  const defaults = { label: '保存子表单', buttonAppearance: 'LINK', buttonType: 'primary' }
  assert.deepEqual(resolveBuiltInButton(undefined, 'save', defaults), defaults)
  assert.equal(builtInActionBarHasPosition({}, 'TOP'), true)
  assert.equal(builtInActionBarHasPosition({}, 'BOTTOM'), false)
  assert.equal(builtInActionBarHasPosition({ position: 'DEFAULT' }, 'BOTTOM', 'BOTTOM'), true)
})

test('完整外观往返保留，运行时合并后仍为固定动作入口', () => {
  const appearance = { label: '保存验收信息', icon: 'Check', buttonType: 'success', buttonAppearance: 'PLAIN', size: 'large', textColor: '#fff', backgroundColor: '#123456', borderColor: '#01234567', borderRadius: 12, sort: 20 }
  const config = { position: 'BOTH', align: 'RIGHT', gap: 12, buttons: { save: appearance } }
  assert.equal(builtInActionBarError(config, ['save', 'open']), '')
  assert.deepEqual(normalizeBuiltInActionBar(JSON.parse(JSON.stringify(config))), config)
  assert.deepEqual(resolveBuiltInButton(config, 'save'), appearance)
  assert.deepEqual(builtInActionBarStyle(config), { justifyContent: 'flex-end', gap: '12px' })
  assert.equal(builtInActionBarHasPosition(config, 'TOP'), true)
  assert.equal(builtInActionBarHasPosition(config, 'BOTTOM'), true)
  assert.equal(builtInButtonStyle(appearance)['--el-button-bg-color'], '#123456')
  assert.equal(builtInButtonStyle(appearance).borderRadius, '12px')
  assert.ok(!Object.keys(builtInButtonStyle(appearance)).some(key => key.includes('disabled')))
})

test('展示配置既不能替换行为、取消权限，也不能创建不属于当前类型的按钮', () => {
  for (const field of ['visible', 'enabled', 'handler', 'action', 'onclick', 'permission', '__proto__']) {
    const config = JSON.parse(`{"buttons":{"save":{"label":"保存","${field}":false}}}`)
    assert.ok(builtInActionBarError(config))
    assert.deepEqual(normalizeBuiltInActionBar(config), { buttons: { save: { label: '保存' } } })
  }
  assert.ok(builtInActionBarError({ buttons: { remove: {} } }, ['save', 'open']))
  assert.deepEqual(normalizeBuiltInActionBar({ buttons: { remove: { label: '删除' }, save: { label: '保存' } } }, ['save']), { buttons: { save: { label: '保存' } } })
  assert.ok(builtInActionBarError({ buttons: { custom: { label: '自定义' } } }))
})

test('非法样式在保存时拒绝，在运行时回退默认值', () => {
  for (const [key, values] of Object.entries({ label: ['', ' ', '文'.repeat(81)], icon: ['NotAnIcon'], size: ['huge'], buttonType: ['custom'], buttonAppearance: ['unknown'], borderRadius: [-1, 41, 0.5, '4'], sort: [-1, 1001, 1.5], backgroundColor: ['red', 'url(https://example.com)', '#12', '#12345', '#gggggg'] })) {
    for (const value of values) {
      const config = { buttons: { save: { [key]: value } } }
      assert.ok(builtInActionBarError(config), `${key}=${value}`)
      assert.deepEqual(normalizeBuiltInActionBar(config), {})
    }
  }
  for (const config of [{ position: 'HIDDEN' }, { align: 'SIDE' }, { gap: 41 }, { gap: '4' }, { buttons: [] }, { buttons: { save: [] } }, { visible: false }]) assert.ok(builtInActionBarError(config))
  assert.equal(resolveBuiltInButton({ buttons: { save: { buttonAppearance: 'CIRCLE', icon: '' } } }, 'save', { label: '保存', icon: 'Check' }).buttonAppearance, 'DEFAULT')
})
