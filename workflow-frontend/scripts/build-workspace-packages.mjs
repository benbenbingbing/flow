import { spawnSync } from 'node:child_process'
import { frontendRoot } from './extensions/modules.mjs'
import { selectedWorkspacePackages } from './workspace-packages.mjs'

/** 按依赖顺序构建产品能力和本次交付的业务；关闭模块不要求其 dist 存在。 */
for (const workspace of selectedWorkspacePackages()) {
  const result = spawnSync('npm', ['run', 'build', '--workspace', workspace], {
    cwd: frontendRoot, stdio: 'inherit', shell: process.platform === 'win32'
  })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status || 1)
}
