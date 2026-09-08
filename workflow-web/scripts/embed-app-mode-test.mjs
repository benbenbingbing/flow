import assert from 'node:assert/strict'
import {
  createEmbedAppearance,
  resolveEmbedLocale
} from '../src/embed/runtime/embedAppearance.js'
import {
  isEmbedPath,
  isEmbedRuntimeLocation,
  normalizeEmbedHosts,
  resolveRuntimeMode
} from '../src/runtime/app-mode.js'
import {
  EmbedEntryConfigError,
  clearEmbedEntryFragment,
  readEmbedLaunchId,
  resolveEmbedEntryConfig,
  validateEmbedEntryConfig
} from '../src/embed/entry/entryConfig.js'

assert.deepEqual(
  [...normalizeEmbedHosts('EMBED.FLOW.EXAMPLE.COM., local.test')],
  ['embed.flow.example.com', 'local.test']
)
assert.equal(isEmbedPath('/embed/v1/launches/lch_0123456789abcdef'), true)
assert.equal(isEmbedPath('/embed/v1/launches/lch_0123456789abcdef/extra'), false)
assert.equal(isEmbedPath('/embed/v1/runtime/bootstrap'), false)
assert.equal(isEmbedPath('/embed/v1'), false)

const embedHosts = ['embed.flow.example.com']
assert.equal(resolveRuntimeMode({
  hostname: 'embed.flow.example.com',
  pathname: '/embed/v1/launches/lch_0123456789abcdef'
}, { embedHosts }), 'embed')
assert.equal(resolveRuntimeMode({
  hostname: 'embed.flow.example.com',
  pathname: '/admin/users'
}, { embedHosts }), 'blocked', '专用 Embed Host 不得回退加载后台应用')
assert.equal(resolveRuntimeMode({
  hostname: 'admin.flow.example.com',
  pathname: '/embed/v1/launches/lch_0123456789abcdef'
}, { embedHosts }), 'blocked', '未授权 Host 不得仅凭路径启动 Embed')
assert.equal(resolveRuntimeMode({
  hostname: 'localhost',
  pathname: '/embed/v1/launches/lch_0123456789abcdef'
}, { allowEmbedPath: true }), 'embed')
assert.equal(resolveRuntimeMode({
  hostname: 'admin.flow.example.com',
  pathname: '/entity/list'
}, { embedHosts }), 'admin')
assert.equal(isEmbedRuntimeLocation({
  hostname: 'embed.flow.example.com',
  pathname: '/embed/v1/launches/lch_0123456789abcdef'
}, { embedHosts }), true)

const rawConfig = {
  launchId: 'lch_0123456789abcdef',
  expectedParentOrigin: 'https://portal.example.com',
  channelId: 'tenant:channel_001',
  protocolVersion: 'flow-embed/1'
}
const encoded = Buffer.from(JSON.stringify(rawConfig), 'utf8')
  .toString('base64url')
const documentRef = {
  querySelector(selector) {
    assert.equal(selector, 'meta[name="flow-embed-entry"]')
    return {
      getAttribute(name) {
        assert.equal(name, 'content')
        return encoded
      }
    }
  }
}
const locationRef = {
  pathname: '/embed/v1/launches/lch_0123456789abcdef',
  search: '',
  hash: ''
}
assert.deepEqual(resolveEmbedEntryConfig({ documentRef, locationRef }), rawConfig)
assert.equal(readEmbedLaunchId(locationRef), 'lch_0123456789abcdef')

assert.throws(
  () => readEmbedLaunchId({ ...locationRef, pathname: '/embed/v1/launches/lch_too_short' }),
  error => error.errorCode === 'EMBED_ENTRY_PATH_INVALID'
)
assert.throws(
  () => validateEmbedEntryConfig({ ...rawConfig, channelId: 'short-channel' }, {
    launchId: 'lch_0123456789abcdef'
  }),
  error => error.errorCode === 'EMBED_ENTRY_CONFIG_CHANNEL_INVALID'
)

assert.throws(
  () => readEmbedLaunchId({ ...locationRef, search: '?token=secret' }),
  error => error instanceof EmbedEntryConfigError
    && error.errorCode === 'EMBED_ENTRY_QUERY_FORBIDDEN'
)
assert.throws(
  () => validateEmbedEntryConfig({ ...rawConfig, accessToken: 'secret' }, {
    launchId: 'lch_0123456789abcdef'
  }),
  error => error.errorCode === 'EMBED_ENTRY_CONFIG_KEY_FORBIDDEN'
)
assert.throws(
  () => validateEmbedEntryConfig({ ...rawConfig, protocolVersion: 'flow-embed/2' }, {
    launchId: 'lch_0123456789abcdef'
  }),
  error => error.errorCode === 'EMBED_ENTRY_CONFIG_PROTOCOL_UNSUPPORTED'
)
assert.throws(
  () => validateEmbedEntryConfig({ ...rawConfig, expectedParentOrigin: 'https://portal.example.com/path' }, {
    launchId: 'lch_0123456789abcdef'
  }),
  error => error.errorCode === 'EMBED_BRIDGE_ORIGIN_INVALID'
)

const historyCalls = []
assert.equal(clearEmbedEntryFragment({
  locationRef: {
    pathname: '/embed/v1/launches/lch_0123456789abcdef',
    search: '',
    hash: '#code=one-time-secret'
  },
  historyRef: {
    state: { existing: true },
    replaceState(...args) {
      historyCalls.push(args)
    }
  }
}), true)
assert.deepEqual(historyCalls, [[
  { existing: true },
  '',
  '/embed/v1/launches/lch_0123456789abcdef'
]])

assert.equal(resolveEmbedLocale('zh-cn'), 'zh-CN')
assert.throws(
  () => resolveEmbedLocale('en-US'),
  error => error.errorCode === 'EMBED_OPERATION_NOT_ALLOWED'
    && error.message.includes('zh-CN')
)

const rootClasses = new Set(['existing-class'])
const attributes = new Map([['lang', 'original-language']])
const root = {
  classList: {
    contains: value => rootClasses.has(value),
    toggle(value, enabled) {
      if (enabled) rootClasses.add(value)
      else rootClasses.delete(value)
    }
  },
  style: { colorScheme: 'normal' },
  getAttribute: name => attributes.get(name) ?? null,
  setAttribute: (name, value) => attributes.set(name, value),
  removeAttribute: name => attributes.delete(name)
}
let themeChangeListener
const media = {
  matches: false,
  addEventListener(type, listener) {
    assert.equal(type, 'change')
    themeChangeListener = listener
  },
  removeEventListener(type, listener) {
    assert.equal(type, 'change')
    assert.equal(listener, themeChangeListener)
    themeChangeListener = null
  }
}
const appearance = createEmbedAppearance({
  documentRef: { documentElement: root },
  windowRef: {
    matchMedia(query) {
      assert.equal(query, '(prefers-color-scheme: dark)')
      return media
    }
  }
})
appearance.apply({ theme: 'dark', locale: 'zh-cn' })
assert.equal(rootClasses.has('dark'), true, '暗色必须应用到根节点，包含 body 下的弹窗')
assert.equal(attributes.get('lang'), 'zh-CN')
assert.equal(root.style.colorScheme, 'dark')
appearance.apply({ theme: 'system', locale: 'zh-CN' })
assert.equal(rootClasses.has('dark'), false)
media.matches = true
themeChangeListener()
assert.equal(rootClasses.has('dark'), true, 'system 模式必须跟随系统主题变化')
appearance.apply({ theme: 'light', locale: 'zh-CN' })
themeChangeListener()
assert.equal(rootClasses.has('dark'), false, '显式浅色不受系统暗色影响')
assert.throws(() => appearance.apply({ theme: 'dark', locale: 'en-US' }))
assert.equal(rootClasses.has('dark'), false, '语言被拒绝时不得部分应用外观状态')
appearance.dispose()
appearance.dispose()
assert.equal(themeChangeListener, null)
assert.deepEqual([...rootClasses], ['existing-class'])
assert.equal(attributes.get('lang'), 'original-language')
assert.equal(root.style.colorScheme, 'normal')
appearance.apply({ theme: 'dark', locale: 'zh-CN' })
assert.equal(rootClasses.has('dark'), false, '销毁后不得继续修改文档主题')

console.log('embed app mode tests passed')
