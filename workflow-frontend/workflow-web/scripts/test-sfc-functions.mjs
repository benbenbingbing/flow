import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { parse, babelParse } from '@vue/compiler-sfc'

/**
 * 在确定性的单元测试中执行组件真实方法，浏览器/API 依赖由 scope 注入。
 * 只抽取显式列出的顶层声明；缺少声明即失败，避免测试悄悄执行旧的复制代码。
 * 文件路径可以是 URL；返回以方法名为键的对象。页面交互另由浏览器测试覆盖。
 */
export function extractSfcFunctions(file, names, scope) {
  const descriptor = parse(readFileSync(file, 'utf8')).descriptor
  const script = descriptor.scriptSetup || descriptor.script
  const ast = babelParse(script.content, { sourceType: 'module', plugins: script.lang === 'ts' ? ['typescript'] : [] })
  let code = names.map(name => {
    const node = ast.program.body.find(item =>
      (item.type === 'FunctionDeclaration' && item.id.name === name)
      || (item.type === 'VariableDeclaration' && item.declarations.some(declaration => declaration.id.name === name)))
    assert.ok(node, `源码缺少测试入口 ${name}`)
    return script.content.slice(node.start, node.end)
  }).join('\n')
  if (script.lang === 'ts') code = stripTypeScriptTypes(code)
  return Function(...Object.keys(scope), `${code}\nreturn { ${names.join(', ')} };`)(...Object.values(scope))
}
