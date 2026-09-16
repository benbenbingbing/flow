import { readdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { build } from 'vite'
import vue from '@vitejs/plugin-vue'

const contractsRoot = fileURLToPath(new URL('..', import.meta.url))
const frontendRoot = fileURLToPath(new URL('../../..', import.meta.url))

/** 收集契约及示例作为真实打包入口，测试脚本自身不进入浏览器依赖图。 */
async function collectEntries(directory) {
  const result = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    if (entry.name === '__tests__') continue
    const filename = path.join(directory, entry.name)
    if (entry.isDirectory()) result.push(...await collectEntries(filename))
    else if (/\.(js|vue)$/.test(filename)) result.push(filename)
  }
  return result.sort()
}

const entries = await collectEntries(contractsRoot)
const virtualId = '\0contracts-verification'
await build({
  configFile: false,
  root: frontendRoot,
  logLevel: 'warn',
  plugins: [
    vue(),
    {
      name: 'contracts-verification-entry',
      resolveId(id) { return id === virtualId ? id : null },
      load(id) {
        if (id !== virtualId) return null
        // 保留所有模块命名空间，确保模板编译、转导出和宿主引用都被实际解析。
        return entries.map((file, index) => `import * as entry${index} from ${JSON.stringify(file)};`).join('\n')
          + `\nexport const contracts = [${entries.map((_, index) => `entry${index}`).join(',')}];`
      }
    }
  ],
  resolve: { alias: { '@': path.join(frontendRoot, 'src') } },
  build: {
    write: false,
    minify: false,
    reportCompressedSize: false,
    rollupOptions: { input: virtualId }
  }
})
console.info(`契约打包检查通过：${entries.length} 个 JS / Vue 入口，未写入构建产物。`)
