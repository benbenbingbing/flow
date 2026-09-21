import assert from 'node:assert/strict'
import test from 'node:test'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { build } from 'vite'
import { flowExtensionsPlugin } from '../../../build/extensions/vite-plugin.mjs'
import { migrateSummaryConfig } from '../examples/contracts/nodeConfig.js'
import { createTextLengthValidator } from '../examples/contracts/validatorFactory.js'

// 真实 bundler 校验具名导出；仅检查路径存在无法发现该类错误。
test('缺少具名导出阻止构建；只修正 JSON 即可恢复', async () => {
  const root = mkdtempSync(path.join(tmpdir(), 'flow-extension-build-'))
  try {
    const folder = path.join(root, 'src/extensions/manifests/common/actions')
    mkdirSync(folder, { recursive: true })
    writeFileSync(path.join(root, 'src/action.js'), 'export const action = () => true')
    const entry = { schemaVersion: 1, type: 'LIST_ACTION', name: 'sample', label: '示例', version: 1, targets: ['ROW'], implementation: { path: 'src/action.js', export: 'missing', kind: 'FUNCTION' } }
    const file = path.join(folder, 'sample.extension.json')
    writeFileSync(file, JSON.stringify(entry))
    const bundle = () => build({ root, configFile: false, logLevel: 'silent', plugins: [flowExtensionsPlugin()], build: { write: false, minify: false, rollupOptions: { input: 'virtual:flow-extension-manifest' } } })
    await assert.rejects(bundle, /missing|export/)
    entry.implementation.export = 'action'
    writeFileSync(file, JSON.stringify(entry))
    await assert.doesNotReject(bundle)
  } finally { rmSync(root, { force: true, recursive: true }) }
})

test('可复制的工厂/迁移示例遵守参数边界且不修改旧快照', () => {
  const validator = createTextLengthValidator()
  assert.equal(validator.validate('😀你', { params: { maxLength: 2 } }), true)
  assert.equal(validator.validate('😀你好', { params: { maxLength: 2 } }), '文本不能超过 2 个字符')
  assert.throws(() => validator.validate('a', { params: { maxLength: -1 } }), /正整数/)
  const previous = Object.freeze({ fieldCode: 'amount', title: '金额' })
  assert.deepEqual(migrateSummaryConfig({ fromVersion: 1, toVersion: 2, config: previous }), { fieldCodes: ['amount'], title: '金额' })
  assert.deepEqual(previous, { fieldCode: 'amount', title: '金额' })
})
