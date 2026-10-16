import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { parse } from '@babel/parser'
import { validateManifest } from '../../../scripts/extensions/validate.mjs'
import { workflowBoundaryPlugin } from '../../../scripts/workflow-boundary.mjs'

test('核心根入口的传递依赖保持无 Vue、DOM 和平台 UI', () => {
  const visited = new Set()
  function visit(filename) {
    if (visited.has(filename)) return
    visited.add(filename)
    const ast = parse(readFileSync(filename, 'utf8'), { sourceType: 'module' })
    for (const node of ast.program.body) {
      if (!['ImportDeclaration', 'ExportNamedDeclaration', 'ExportAllDeclaration'].includes(node.type) || !node.source) continue
      const source = node.source.value
      assert.doesNotMatch(source, /vue|vant|element-plus|workflow-web|workflow-mobile|pinia|router|\.css$/)
      if (source.startsWith('.')) visit(path.resolve(path.dirname(filename), source))
    }
    assert.doesNotMatch(readFileSync(filename, 'utf8'), /\b(?:window|document|localStorage|sessionStorage)\s*[.[]/)
  }
  visit(path.resolve('packages/workflow-core/src/index.js'))
  assert.ok(visited.size > 10, '必须遍历依赖，不能只检查入口文本')
})

test('模块图边界覆盖间接与懒加载模块', () => {
  for (const [platform, forbidden] of [['mobile', '/repo/node_modules/element-plus/es/button.js'], ['pc', '/repo/packages/workflow-mobile-ui/dist/index.js']]) {
    assert.throws(() => workflowBoundaryPlugin(platform).generateBundle.call({
      getModuleIds: () => ['/repo/entry.js', '/repo/lazy.js', forbidden],
      error(message) { throw new Error(message) }, emitFile() { assert.fail('越界不能输出产物') }
    }), /平台依赖越界/)
  }
})

test('扩展路径仅验证目标平台，且禁止移动实现越界', () => {
  const root = mkdtempSync(path.join(tmpdir(), 'flow-mobile-manifest-'))
  try {
    mkdirSync(path.join(root, 'src'))
    writeFileSync(path.join(root, 'src/Mobile.vue'), '<template />')
    const manifest = JSON.parse(readFileSync(new URL('../../../extensions/manifests/examples/demo/forms/DemoProjectForm.v1.extension.json', import.meta.url)))
    manifest.implementation.path = 'src/MissingDesktop.vue'
    manifest.platforms.mobile.implementation.path = 'src/Mobile.vue'
    assert.doesNotThrow(() => validateManifest(manifest, root, 'fixture', { platform: 'mobile', implementationRoot: root }))
    assert.throws(() => validateManifest(manifest, root, 'fixture'), /ENOENT/)
    manifest.platforms.mobile.implementation.path = 'src/../Outside.vue'
    assert.throws(() => validateManifest(manifest, root, 'fixture', { platform: 'mobile', implementationRoot: root }), /实现路径/)
  } finally { rmSync(root, { recursive: true, force: true }) }
})
