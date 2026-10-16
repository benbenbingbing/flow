import path from 'node:path'
import { frontendRoot, resolveEnabledModules } from './extensions/modules.mjs'

/** 开发和生产使用相同装配列表，避免开发监听漏掉已选业务包。 */
export function selectedWorkspacePackages() {
  const modules = resolveEnabledModules(path.join(frontendRoot, 'extensions/manifests'))
  return [
    '@flow/workflow-core', '@flow/workflow-extension-sdk', '@flow/workflow-api',
    '@flow/workflow-mobile-ui', ...[...modules].map(name => `@flow/${name}`)
  ]
}
