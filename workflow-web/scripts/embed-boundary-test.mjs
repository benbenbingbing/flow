import assert from 'node:assert/strict'
import { readdir, readFile, stat } from 'node:fs/promises'
import { dirname, extname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const webRoot = resolve(scriptDirectory, '..')
const embedRoot = join(webRoot, 'src/embed')

const embedViteConfig = await readFile(join(webRoot, 'vite.embed.config.js'), 'utf8')
assert.match(
  embedViteConfig,
  /['"]process\.env\.NODE_ENV['"]\s*:\s*JSON\.stringify\(['"]production['"]\)/,
  'Embed library build 必须消除浏览器中不存在的 Node process.env'
)

async function collectFiles(directory) {
  const files = []
  for (const name of await readdir(directory)) {
    const path = join(directory, name)
    const info = await stat(path)
    if (info.isDirectory()) files.push(...await collectFiles(path))
    else files.push(path)
  }
  return files
}

const sourceFiles = (await collectFiles(embedRoot))
  .filter(file => ['.js', '.vue'].includes(extname(file)))
assert.ok(sourceFiles.length >= 10, '应存在独立 Embed Runtime 文件集')

const requiredFiles = [
  'EmbedApp.vue',
  'EmbedShell.vue',
  'embed-main.js',
  'api/embedRequest.js',
  'api/embedRuntimeApi.js',
  'bridge/embedBridge.js',
  'entry/entryConfig.js',
  'projection/normalizeEmbedSchema.js',
  'runtime/EmbedErrorState.vue',
  'runtime/EmbedFormRuntime.vue',
  'runtime/EmbedListRuntime.vue',
  'runtime/embedRuntimeController.js',
  'session/embedSession.js'
]
for (const file of requiredFiles) {
  assert.ok(
    sourceFiles.includes(join(embedRoot, file)),
    `缺少 Embed 边界文件 ${file}`
  )
}

const importPattern = /(?:import\s+(?:[\s\S]*?\s+from\s+)?|import\s*\()(['"])([^'"]+)\1/g
const forbiddenImports = [
  /^axios$/,
  /^pinia$/,
  /^vue-router$/,
  /(?:^|\/)shared\/request/,
  /(?:^|\/)router(?:\/|$)/,
  /(?:^|\/)store(?:\/|$)/,
  /(?:^|\/)api\/entity/,
  /(?:^|\/)views\//,
  /(?:^|\/)components\/(?!\.\.)/
]

for (const file of sourceFiles) {
  const source = await readFile(file, 'utf8')
  const label = relative(webRoot, file)
  for (const match of source.matchAll(importPattern)) {
    const specifier = match[2]
    assert.equal(
      forbiddenImports.some(pattern => pattern.test(specifier)),
      false,
      `${label} 不得导入普通运行时依赖 ${specifier}`
    )
    if (specifier.startsWith('.')) {
      const resolvedImport = resolve(dirname(file), specifier)
      assert.equal(
        resolvedImport.startsWith(embedRoot),
        true,
        `${label} 的相对导入不得逃逸 Embed 边界：${specifier}`
      )
    } else {
      assert.equal(specifier, 'vue', `${label} 只允许外部依赖 vue，实际为 ${specifier}`)
    }
  }

  assert.equal(/\b(?:localStorage|sessionStorage)\s*\./.test(source), false, `${label} 不得持久化会话`)
  assert.equal(/\bdocument\s*\.\s*cookie\b/.test(source), false, `${label} 不得读取普通 Cookie`)
  assert.equal(/credentials\s*:\s*['"](?:include|same-origin)['"]/.test(source), false)
  assert.equal(/window\s*\.\s*location\s*=|location\s*\.\s*href\s*=/.test(source), false)

  if (extname(file) === '.vue') {
    const parsed = parse(source, { filename: file })
    assert.deepEqual(parsed.errors, [], `${label} SFC 解析失败`)
    const descriptor = parsed.descriptor
    assert.equal(
      /(?:^|\s)(?::style|style)\s*=/.test(descriptor.template?.content || ''),
      false,
      `${label} 不得使用会被 Entry CSP 阻断的 inline style`
    )
    if (descriptor.script || descriptor.scriptSetup) {
      assert.doesNotThrow(
        () => compileScript(descriptor, { id: `embed-${label}` }),
        `${label} script 编译失败`
      )
    }
    if (descriptor.template) {
      const compiled = compileTemplate({
        id: `embed-${label}`,
        filename: file,
        source: descriptor.template.content,
        scoped: descriptor.styles.some(style => style.scoped)
      })
      assert.deepEqual(compiled.errors, [], `${label} template 编译失败`)
    }
  }
}

const requestSource = await readFile(join(embedRoot, 'api/embedRequest.js'), 'utf8')
assert.match(requestSource, /credentials:\s*'omit'/)
assert.match(requestSource, /redirect:\s*'error'/)
assert.match(requestSource, /referrerPolicy:\s*'no-referrer'/)

const shellSource = await readFile(join(embedRoot, 'EmbedShell.vue'), 'utf8')
assert.match(
  shellSource,
  /createEmbedRuntimeApi\(createEmbedRequest\(\{ session \}\)\)/,
  'Embed Shell 的 Exchange 与 Runtime HTTP client 必须共享同一个仅内存 Session'
)

const listRuntimeSource = await readFile(join(embedRoot, 'runtime/EmbedListRuntime.vue'), 'utf8')
const formRuntimeSource = await readFile(join(embedRoot, 'runtime/EmbedFormRuntime.vue'), 'utf8')
const runtimeControllerSource = await readFile(
  join(embedRoot, 'runtime/embedRuntimeController.js'),
  'utf8'
)
assert.match(listRuntimeSource, /@click="\$emit\('open-create'\)"/)
assert.match(listRuntimeSource, /@click="\$emit\('open-view', record\.id\)"/)
assert.match(shellSource, /@open-create="controller\.openListCreate"/)
assert.match(shellSource, /@open-view="controller\.openListView"/)
assert.match(shellSource, /:selected-record-ids="runtime\.selectedRecordIds"/)
assert.match(formRuntimeSource, /@click="\$emit\('back'\)"/)
for (const forbiddenCoordinate of ['formId', 'entityCode', 'releaseId', 'RECORD_UPDATE', 'EDIT']) {
  assert.equal(
    listRuntimeSource.includes(forbiddenCoordinate),
    false,
    `列表组件不得接受 ${forbiddenCoordinate} 导航坐标或 V1 写能力`
  )
}
assert.equal(runtimeControllerSource.includes('IDEMPOTENCY_REQUEST_IN_PROGRESS'), false)
assert.match(runtimeControllerSource, /EMBED_REQUEST_IN_PROGRESS/)
assert.match(runtimeControllerSource, /EMBED_IDEMPOTENCY_KEY_REUSED/)

const runtimeApiSource = await readFile(join(embedRoot, 'api/embedRuntimeApi.js'), 'utf8')
for (const endpoint of [
  '/runtime/bootstrap',
  '/runtime/schema',
  '/runtime/list/query',
  '/session',
  '/session/heartbeat'
]) {
  assert.ok(runtimeApiSource.includes(endpoint), `缺少固定 Runtime API ${endpoint}`)
}
for (const forbiddenEndpoint of [
  '/auth/refresh',
  '/login',
  '/api/admin',
  '/api/open',
  '/runtime/arbitrary'
]) {
  assert.equal(runtimeApiSource.includes(forbiddenEndpoint), false)
}

console.log('embed boundary tests passed')
