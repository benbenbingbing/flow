import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, watch } from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const source = path.join(root, 'src')
const output = path.join(root, 'dist')
const watching = process.argv.includes('--watch')

/** 保留 ESM 模块边界，生产和开发均通过公开 dist 入口解析，避免隐式源码别名。 */
function build() {
  mkdirSync(output, { recursive: true })
  cpSync(source, output, { recursive: true })
  // 删除源模块后也清除旧产物，防止迁移期间错误 import 被残留 dist 掩盖。
  function prune(directory) {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const filename = path.join(directory, entry.name)
      if (!existsSync(path.join(source, path.relative(output, filename)))) rmSync(filename, { recursive: true, force: true })
      else if (entry.isDirectory()) prune(filename)
    }
  }
  prune(output)
  console.log(`${path.basename(root)}: built`)
}

build()
if (watching) {
  let timer
  const rebuild = () => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      try { build() } catch (error) { console.error(error); process.exitCode = 1 }
    }, 80)
  }
  watch(source, { recursive: true }, rebuild)
  console.log(`${path.basename(root)}: watching`)
}
