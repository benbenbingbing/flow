import { existsSync, readFileSync, readdirSync, realpathSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 扩展装配相对前端工作区定位；Git、部署和后端所在的仓库根在它的上一层。
export const frontendRoot = fileURLToPath(new URL('../..', import.meta.url))
export const moduleConfigFile = path.join(frontendRoot, 'config/frontend-modules.json')
export const customDirectory = path.join(frontendRoot, 'custom')
export const businessModulePattern = /^biz-[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/

/** 固定工作区根，测试和部署工具可显式传入独立工作区，不从调用者 cwd 猜测目录。 */
export function extensionLayout(options = {}) {
  if (!options || typeof options !== 'object' || Array.isArray(options)) throw new Error('模块选项必须为对象；不再接受旧清单目录参数')
  const root = path.resolve(options.repositoryRoot || frontendRoot)
  return {
    root,
    customDirectory: path.join(root, 'custom'),
    manifestDirectory: options.manifestDirectory || path.join(root, 'packages/workflow-extensions/manifests'),
    moduleConfigFile: options.moduleConfigFile || path.join(root, 'config/frontend-modules.json')
  }
}

/**
 * custom/biz-* 是业务模块的唯一登记入口；包、清单和源码共用这个目录身份。
 * 这里只读包描述，不要求 src/dist 已生成，因而基础产品构建和元数据生成无需加载业务 UI。
 * 旧布局、目录别名和包名不匹配直接失败，避免双登记或悄悄漏装模块。
 */
export function discoverBusinessModules(options = {}) {
  const { root, customDirectory: directory } = extensionLayout(options)
  if (existsSync(path.join(root, 'extensions/manifests'))) throw new Error('旧扩展目录 extensions/manifests 已停用；产品清单放 packages/workflow-extensions/manifests，业务清单放 custom/biz-*/manifests')
  const packages = path.join(root, 'packages')
  if (existsSync(packages)) {
    for (const entry of readdirSync(packages, { withFileTypes: true })) {
      if (businessModulePattern.test(entry.name) && (entry.isDirectory() || entry.isSymbolicLink())) throw new Error(`旧业务目录 packages/${entry.name} 已停用；请使用 custom/${entry.name}`)
    }
  }
  const modules = new Map()
  if (!existsSync(directory)) return modules
  if (realpathSync(directory) !== path.join(realpathSync(root), 'custom')) throw new Error('custom 目录不能通过符号链接越界')
  for (const entry of readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    if (!entry.isDirectory() && !entry.isSymbolicLink()) continue
    if (!businessModulePattern.test(entry.name)) throw new Error(`非法业务模块目录：custom/${entry.name}，必须使用 biz-* 名称`)
    const moduleDirectory = path.join(directory, entry.name)
    if (entry.isSymbolicLink() || realpathSync(moduleDirectory) !== path.join(realpathSync(directory), entry.name)) throw new Error(`业务模块目录不能使用符号链接：${moduleDirectory}`)
    const packageFile = path.join(moduleDirectory, 'package.json')
    if (realpathSync(packageFile) !== path.join(realpathSync(moduleDirectory), 'package.json')) throw new Error(`业务包描述不能使用符号链接：${packageFile}`)
    const descriptor = JSON.parse(readFileSync(packageFile, 'utf8'))
    const name = `@flow/${entry.name}`
    if (descriptor.name !== name) throw new Error(`业务包名称不匹配：custom/${entry.name} 必须声明 ${name}`)
    const manifests = path.join(moduleDirectory, 'manifests')
    if (!statSync(manifests).isDirectory() || realpathSync(manifests) !== path.join(realpathSync(moduleDirectory), 'manifests')) throw new Error(`业务清单目录必须属于本模块：${manifests}`)
    modules.set(entry.name, { id: entry.name, name, directory: moduleDirectory, manifestDirectory: manifests })
  }
  return modules
}

/**
 * 读取交付所选业务模块。环境变量用于 CI/单次构建，none 表示基础产品。
 * 模块必须来自 custom 目录解析；拼错名称立即失败，避免误打出缺少业务的产物。
 */
export function resolveEnabledModules(options = {}) {
  const configFile = extensionLayout(options).moduleConfigFile
  const available = options.businessModules || discoverBusinessModules(options)
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
