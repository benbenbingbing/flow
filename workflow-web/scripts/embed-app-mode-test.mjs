import assert from 'node:assert/strict'
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

console.log('embed app mode tests passed')
