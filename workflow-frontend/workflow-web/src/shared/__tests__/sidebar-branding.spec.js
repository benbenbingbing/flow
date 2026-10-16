import assert from 'node:assert/strict'
import { DEFAULT_SIDEBAR_BRANDING, SIDEBAR_BRANDING_MAX_VALUE_BYTES, normalizeSidebarBranding,
  validateSidebarBrandImage, readSidebarBrandImageFile } from '../sidebar-branding.js'
import { serializeSettingInput } from '../setting-value.js'

// 历史双字段配置不得因新增图片字段丢失原名称和图标。
assert.deepEqual(normalizeSidebarBranding({ title: '已有平台', icon: 'OfficeBuilding' }),
  { title: '已有平台', icon: 'OfficeBuilding', imageBase64: '' })
const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII='
assert.equal(validateSidebarBrandImage(png), png)
assert.equal(normalizeSidebarBranding({ ...DEFAULT_SIDEBAR_BRANDING, imageBase64: png }).imageBase64, png)
assert.equal(validateSidebarBrandImage(''), '')
for (const invalid of [null, true, 'https://example.test/logo.png', 'data:image/svg+xml;base64,PHN2Zy8+',
  'data:image/png;base64,bm90LWFuLWltYWdl', 'data:image/png;base64,%%%', 'data:image/png;base64,iVBORw=',
  'data:image/jpeg;base64,' + png.split(',')[1]]) {
  assert.throws(() => validateSidebarBrandImage(invalid))
  assert.deepEqual(normalizeSidebarBranding({ title: '保留名称', icon: 'OfficeBuilding', imageBase64: invalid }),
    { title: '保留名称', icon: 'OfficeBuilding', imageBase64: '' })
}
const bytes = Buffer.alloc(32 * 1024)
Buffer.from(png.split(',')[1], 'base64').copy(bytes)
const boundaryImage = 'data:image/png;base64,' + bytes.toString('base64')
assert.equal(validateSidebarBrandImage(boundaryImage), boundaryImage)
const brandingJson = JSON.stringify({ ...DEFAULT_SIDEBAR_BRANDING, imageBase64: boundaryImage })
assert.throws(() => serializeSettingInput('JSON', brandingJson), /16 KiB/)
assert.equal(serializeSettingInput('JSON', brandingJson, SIDEBAR_BRANDING_MAX_VALUE_BYTES), brandingJson)
assert.throws(() => validateSidebarBrandImage('data:image/png;base64,' + Buffer.concat([bytes, Buffer.from([0])]).toString('base64')), /32 KiB/)
await assert.rejects(readSidebarBrandImageFile({ type: 'image/png', size: 32 * 1024 + 1 }), /32 KiB/)
await assert.rejects(readSidebarBrandImageFile({ type: 'image/svg+xml', size: 10 }), /PNG/)
console.log('sidebar branding image tests passed (legacy values, image validation, size limits, fallbacks)')
