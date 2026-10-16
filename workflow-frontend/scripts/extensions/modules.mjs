import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 扩展装配相对前端工作区定位；Git、部署和后端所在的仓库根在它的上一层。
export const frontendRoot = fileURLToPath(new URL('../..', import.meta.url))
export const moduleConfigFile = path.join(frontendRoot, 'config/frontend-modules.json')
export const businessModulePattern = /^biz-[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/

/**
 * 读取交付所选业务模块。环境变量用于 CI/单次构建，none 表示基础产品。
 * 模块必须有同名清单目录；拼错名称立即失败，避免误打出缺少业务的产物。
 */
export function resolveEnabledModules(directory, options = {}) {
  const available = new Set(readdirSync(directory, { withFileTypes: true })
    .filter(entry => entry.isDirectory() && businessModulePattern.test(entry.name)).map(entry => entry.name))
  const configFile = options.moduleConfigFile || moduleConfigFile
  const env = options.env || process.env
  let enabled = options.enabledModules
  if (enabled === undefined && Object.hasOwn(env, 'FLOW_FRONTEND_MODULES')) {
    const override = env.FLOW_FRONTEND_MODULES.trim()
    if (!override) throw new Error('FLOW_FRONTEND_MODULES 不能为空；禁用全部业务请填写 none')
    enabled = override === 'none' ? [] : override.split(',').map(item => item.trim())
  }
  if (enabled === undefined) {
    const config = JSON.parse(readFileSync(configFile, 'utf8'))
    if (!config || Array.isArray(config) || Object.keys(config).some(key => key !== 'enabledModules')) {
      throw new Error(`${configFile}: 仅允许 enabledModules 配置`)
    }
    enabled = config.enabledModules
  }
  if (!Array.isArray(enabled) || enabled.some(item => typeof item !== 'string' || !businessModulePattern.test(item))) {
    throw new Error(`${configFile}: enabledModules 必须为 biz-* 模块名称数组`)
  }
  if (new Set(enabled).size !== enabled.length) throw new Error(`${configFile}: enabledModules 不能重复`)
  for (const name of enabled) if (!available.has(name)) throw new Error(`未知前端业务模块：${name}`)
  return new Set(enabled)
}
