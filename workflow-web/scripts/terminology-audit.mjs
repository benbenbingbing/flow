import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { parse as parseSfc } from '@vue/compiler-sfc'
import { listFiles } from './file-tree.mjs'

const root = process.cwd()
const vueFiles = listFiles(['src/views', 'src/components'], '.vue')

const issues = []
for (const file of vueFiles) {
  const source = readFileSync(path.join(root, file), 'utf8')
  const { descriptor } = parseSfc(source, { filename: file })
  const template = descriptor.template?.content || ''

  if (/抄送/.test(template)) {
    issues.push(`${file}: 用户界面应统一使用“知会”，不得使用“抄送”`)
  }
  if (/<el-button\b[^>]*>\s*确定\s*<\/el-button>/.test(template)) {
    issues.push(`${file}: 按钮应说明操作结果，不得只写“确定”`)
  }
  if (/(admin\s*\/\s*admin|123456)/i.test(source)) {
    issues.push(`${file}: 不得在前端源码中提供固定默认凭据`)
  }
}

const terminologyFile = path.join(root, 'src/constants/productTerminology.js')
assert.equal(existsSync(terminologyFile), true, '缺少共享产品术语常量')
const terminologySource = readFileSync(terminologyFile, 'utf8')
;['知会', '知会我的', '未读知会', '标识', '保存草稿', '发布', '应用到画布'].forEach((term) => {
  assert.ok(terminologySource.includes(term), `共享产品术语缺少：${term}`)
})

const terminologyDoc = path.resolve(root, '../docs/产品术语表.md')
assert.equal(existsSync(terminologyDoc), true, '缺少产品术语表文档')

assert.equal(issues.length, 0, `产品术语审计失败:\n${issues.join('\n')}`)
console.log(`terminology audit passed: ${vueFiles.length} user interface files`)
