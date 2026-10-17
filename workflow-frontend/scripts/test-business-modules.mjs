import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { discoverBusinessModules, frontendRoot } from './extensions/modules.mjs'

/** 测试所有自定义模块，不以交付选择跳过未启用业务；模块自己声明测试入口。 */
for (const module of discoverBusinessModules().values()) {
  const descriptor = JSON.parse(readFileSync(path.join(module.directory, 'package.json'), 'utf8'))
  if (typeof descriptor.scripts?.test !== 'string' || !descriptor.scripts.test.trim()) continue
  const result = spawnSync('npm', ['run', 'test', '--workspace', module.name], {
    cwd: frontendRoot, stdio: 'inherit', shell: process.platform === 'win32'
  })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status || 1)
}
