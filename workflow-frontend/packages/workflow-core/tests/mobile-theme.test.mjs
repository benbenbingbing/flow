import { test } from 'node:test'
import assert from 'node:assert/strict'
import { DEFAULT_MOBILE_THEME as defaults, MOBILE_THEME_PRESETS, normalizeMobileTheme, mobileThemeVariables, colorContrast } from '../src/shared/mobile-theme.js'

test('预设是完整持久化配置，自定义不会误标为预设', () => {
  assert.equal(MOBILE_THEME_PRESETS.length, 7)
  assert.equal(new Set(MOBILE_THEME_PRESETS.map(item => item.primaryColor)).size, 7)
  for (const { id, name, ...preset } of MOBILE_THEME_PRESETS) {
    const config = { ...preset, preset: id }
    assert.deepEqual(normalizeMobileTheme(config), config)
  }
  assert.equal(normalizeMobileTheme({ ...defaults, primaryColor: '#abcdef' }).preset, 'custom')
  assert.equal(normalizeMobileTheme({ ...defaults, primaryColor: '#abcdef' }).primaryColor, '#ABCDEF')
  assert.equal(defaults.primaryColor, '#196B62')
})
test('拒绝任意 CSS、未知字段、版本和深色背景', () => {
  for (const patch of [{ version: 2 }, { preset: 'unknown' }, { primaryColor: 'var(--red)' }, { primaryColor: '#123' }, { css: 'body{}' }, { backgroundColor: '#222222' }, { surfaceColor: '#000000' }]) assert.throws(() => normalizeMobileTheme({ ...defaults, ...patch }))
  for (const value of [null, [], {}, 'green']) assert.throws(() => normalizeMobileTheme(value))
})
test('浅主色与极端颜色下文字可读，业务通过色不随品牌色变化', () => {
  for (const primaryColor of ['#FFFFFF', '#000000', '#FFFF00', '#888888', ...MOBILE_THEME_PRESETS.map(item => item.primaryColor)]) {
    const config = { ...defaults, preset: 'custom', primaryColor, backgroundColor: '#EEEEEE', surfaceColor: '#FFFFEE' }
    const vars = mobileThemeVariables(config)
    assert.ok(colorContrast(vars['--flow-mobile-on-accent'], primaryColor) >= 4.5)
    for (const key of ['--flow-mobile-accent-text', '--flow-mobile-muted', '--flow-mobile-text']) {
      assert.ok(colorContrast(vars[key], config.surfaceColor) >= 4.5, key)
      assert.ok(colorContrast(vars[key], config.backgroundColor) >= 4.5, key)
      assert.ok(colorContrast(vars[key], vars['--flow-mobile-accent-soft']) >= 4.5, key)
    }
    assert.equal(vars['--flow-mobile-success'], '#196B62')
    assert.equal(vars['--van-popup-background'], config.surfaceColor)
  }
})
