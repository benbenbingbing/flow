import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { build } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createFormActionRuntime, getFormId } from '../../../packages/workflow-core/src/shared/form-action-runtime.js'

const root = fileURLToPath(new URL('../', import.meta.url))
const metadata = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'))

test('公开的表单 ID 解析兼容普通表单、任务表单和历史实体表单，旧工厂接口保持一致', () => {
  const runtime = createFormActionRuntime({})
  for (const [form, expected] of [[{ id: 'form' }, 'form'], [{ formId: 'task-form' }, 'task-form'], [{ entityFormId: 'legacy-form' }, 'legacy-form'], [undefined, '']]) {
    assert.equal(getFormId(form), expected)
    assert.equal(runtime.getFormId(form), expected)
  }
})

for (const platform of ['pc', 'mobile']) {
  test(`${platform} 的全部公开业务入口可经 Vite 真实链接，内部产品依赖具有完整导出`, async () => {
    const entries = Object.fromEntries(Object.keys(metadata.exports)
      .filter(entry => entry.startsWith(`./${platform}/`))
      .map(entry => [entry.slice(2), fileURLToPath(import.meta.resolve(`@flow/biz-project/${entry.slice(2)}`))]))
    assert.ok(Object.keys(entries).length > 0)
    // 内部包必须参与真正的模块链接，否则仅编译 SFC 无法发现宿主能力或共享函数的缺失导出。
    // 外部 UI 框架保持 external，单测关注产品/业务边界，完整应用构建另行检查 UI 集成。
    const result = await build({
      root, configFile: false, logLevel: 'silent', plugins: [vue()], publicDir: false,
      build: {
        write: false, minify: false, lib: { entry: entries, formats: ['es'] },
        rollupOptions: {
          external: id => !id.startsWith('.') && !path.isAbsolute(id) && !id.startsWith('@flow/') && !id.startsWith('\0')
        }
      }
    })
    const outputs = Array.isArray(result) ? result : [result]
    const linkedModules = outputs.flatMap(output => output.output.filter(item => item.type === 'chunk').flatMap(chunk => Object.keys(chunk.modules)))
    assert.ok(linkedModules.some(id => id.includes('/biz-project/dist/')), '必须使用实际包导出的 dist 实现')
    if (platform === 'pc') assert.ok(!linkedModules.some(id => id.includes('/biz-project/dist/mobile/') || id.includes('/workflow-mobile-ui/')))
    else assert.ok(!linkedModules.some(id => id.includes('/biz-project/dist/pc/')))
  })
}
