import test from 'node:test'
import assert from 'node:assert/strict'
import { createSSRApp } from 'vue'
import { provideExtensionHost, useExtensionHost } from '../src/host/index.js'

test('扩展宿主能力按应用隔离，嵌入与主应用不会交换客户端', () => {
  const admin = createSSRApp({}), embed = createSSRApp({})
  const adminApi = { getDetail: () => 'admin' }, embedApi = { getDetail: () => 'embed' }
  provideExtensionHost(admin, { entityDataApi: adminApi })
  provideExtensionHost(embed, { entityDataApi: embedApi })
  assert.equal(admin.runWithContext(useExtensionHost).entityDataApi.getDetail(), 'admin')
  assert.equal(embed.runWithContext(useExtensionHost).entityDataApi.getDetail(), 'embed')
  assert.throws(() => { admin.runWithContext(useExtensionHost).entityDataApi = embedApi }, TypeError)
})

test('独立预览缺少宿主时明确失败，不回退到全局应用服务', () => {
  assert.throws(useExtensionHost, /扩展宿主能力未安装/)
  assert.throws(() => createSSRApp({}).runWithContext(useExtensionHost), /扩展宿主能力未安装/)
  assert.throws(() => provideExtensionHost(null, {}), TypeError)
})
