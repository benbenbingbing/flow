import assert from 'node:assert/strict'
import { readdir, readFile, stat } from 'node:fs/promises'
import { dirname, extname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { compileScript, compileTemplate, parse } from '@vue/compiler-sfc'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const webRoot = resolve(scriptDirectory, '..')
const embedRoot = join(webRoot, 'src/embed')

async function source(path) {
  return readFile(join(webRoot, path), 'utf8')
}

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

function assertDoesNotContain(text, patterns, label) {
  for (const pattern of patterns) {
    assert.equal(
      pattern.test(text),
      false,
      `${label} 不得命中旧 FORM 兼容链：${pattern}`
    )
  }
}

const viteConfig = await source('vite.embed.config.js')
assert.match(
  viteConfig,
  /['"]process\.env\.NODE_ENV['"]\s*:\s*JSON\.stringify\(['"]production['"]\)/
)
assert.match(viteConfig, /['"]@['"]\s*:\s*resolve\(__dirname, ['"]src['"]\)/)
assertDoesNotContain(viteConfig, [
  /trustedPublishedRuntimeBoundary/,
  /TrustedFormFieldRenderer/,
  /shared-stubs/,
  /customComponentRegistry.*alias/i,
  /formNodeRegistry.*alias/i,
  /formFieldsRegistry.*alias/i
], 'vite.embed.config.js')

const shellSource = await source('src/embed/EmbedShell.vue')
const controllerSource = await source('src/embed/runtime/embedRuntimeController.js')
const apiSource = await source('src/embed/api/embedRuntimeApi.js')
const nativePageSource = await source('src/embed/runtime/NativeEmbeddedFormPage.vue')
const nativeListPageSource = await source('src/embed/runtime/NativeEmbeddedListPage.vue')
const entityListSource = await source('src/views/entity/EntityDataList.vue')
const entityDataTableSource = await source(
  'src/views/entity/components/EntityDataTable.vue'
)
const entityListLauncherSource = await source('src/components/EntityListLauncher.vue')
const sharedRequestSource = await source('src/shared/request/index.js')
const embedMainSource = await source('src/embed/embed-main.js')
const adminMainSource = await source('src/main.js')
const extensionEntrySource = await source('src/extensions/register.js')
const projectExtensionSource = await source('src/project/index.js')
const entityDialogSource = await source(
  'src/views/entity/components/EntityDataFormDialog.vue'
)
const approvalDialogSource = await source(
  'src/views/entity/components/approval/EntityApprovalDialog.vue'
)
const entityFieldsSource = await source(
  'src/views/entity/components/EntityDataFormFields.vue'
)
const fieldRendererSource = await source(
  'src/components/FormFieldRendererLinkage.vue'
)
const fieldRegistrySource = await source('src/components/form-fields/index.js')
const formPreviewSource = await source('src/components/FormPreviewLinkage.vue')
const formNodeRuntimeSource = await source('src/components/FormNodeRuntimeItem.vue')
const canonicalFieldSources = await Promise.all([
  'DateField.vue',
  'SelectField.vue',
  'SwitchField.vue',
  'RichTextField.vue'
].map(filename => source(`src/components/form-fields/components/${filename}`)))
const bootstrapNormalizerSource = await source(
  'src/embed/projection/normalizeEmbedSchema.js'
)

assert.match(shellSource, /import NativeEmbeddedFormPage from/)
assert.match(shellSource, /import NativeEmbeddedListPage from/)
assert.match(shellSource, /<NativeEmbeddedFormPage/)
assert.match(shellSource, /<NativeEmbeddedListPage/)
assert.match(shellSource, /runtime\.navigation\.surfaceType === 'FORM'/)
assertDoesNotContain(shellSource, [
  /EmbedFormRuntime/,
  /TrustedPublishedFormRuntime/,
  /TrustedFormFieldRenderer/,
  /normalizeEmbedForm/
], 'EmbedShell FORM 活跃路径')
assert.doesNotMatch(shellSource, /EmbedListRuntime/)

assert.match(nativeListPageSource, /EntityDataList/)
assert.match(nativeListPageSource, /target\.listReleaseResolutionToken/)
assert.doesNotMatch(nativeListPageSource, /runtime-capabilities/,
  'View/Grant capability 只约束入口与 Bridge，不得重写原生页按钮')
assert.doesNotMatch(
  nativeListPageSource,
  /bootstrap\.ui\.(?:showSearch|showPagination|showToolbar|pageSize)/,
  'Embed UI 不得覆盖 exact LIST Release 的搜索、工具栏、分页或页大小'
)
for (const presentationProp of [
  ':show-search=',
  ':show-pagination=',
  ':show-toolbar=',
  ':show-row-actions='
]) {
  assert.equal(
    nativeListPageSource.includes(presentationProp),
    false,
    `NativeEmbeddedListPage 不得传入 ${presentationProp} 覆盖 Flow 原生列表`
  )
}
assert.match(
  nativeListPageSource,
  /:page-size="0"/,
  'pageSize=0 是不覆盖哨兵值，EntityDataList 必须使用 exact LIST Release 的分页配置'
)
assert.match(entityListSource, /listConfig\.value\?\.customComponent \|\| ''/)
assert.doesNotMatch(
  entityListSource,
  /props\.embedded \? '' : \(listConfig\.value\?\.customComponent/,
  'embedded 不得禁用 Flow 原生自定义列表'
)
assert.match(controllerSource, /nativeListTarget: bootstrap\.target/)
assert.match(controllerSource, /values: Object\.freeze\(\{\}\)/,
  '宿主选择事件不得透出原生整行')

assertDoesNotContain(controllerSource, [
  /from ['"].*normalizeEmbedForm\.js['"]/,
  /normalizeEmbedFormResult/,
  /buildEmbedCreateEvaluationRequest/,
  /buildEmbedOptionQuery/,
  /api\.getForm\s*\(/,
  /api\.getRecord\s*\(/,
  /api\.evaluateCreate\s*\(/,
  /api\.queryFormOptions\s*\(/,
  /api\.queryFormLookups\s*\(/
], 'embedRuntimeController FORM 活跃路径')
assert.match(
  controllerSource,
  /NativeEmbeddedFormPage|native-form|nativeForm/i,
  'controller 必须把直接 FORM 与 LIST→LOCAL_FORM 都路由到原生表单页'
)

assertDoesNotContain(apiSource, [
  /\/runtime\/form(?:\?|\/)/,
  /\bgetForm\s*\(/,
  /\bevaluateCreate\s*\(/,
  /\bqueryFormOptions\s*\(/,
  /\bqueryFormLookups\s*\(/
], 'Embed Runtime API')
assert.match(apiSource, /\/runtime\/records/,
  'CREATE 仍需保留幂等写入口，但响应只返回记录 ID/receipt，不投影表单字段')

assert.match(nativePageSource, /EntityDataFormDialog/)
assert.match(nativePageSource, /EntityApprovalDialog/)
assert.match(nativePageSource, /getFormRuntimeRelease/)
assert.match(nativePageSource, /entityApi\.getByCode/)
assert.match(nativePageSource, /submit-transport="submitNativeRecord"/)
assert.match(nativePageSource, /target\.entityCode/)
assert.match(nativePageSource, /target\.formId/)
assert.match(nativePageSource, /target\.formReleaseId/)
assert.match(nativePageSource, /target\.formReleaseVersion/)
assert.match(nativePageSource, /target\.formReleaseResolutionToken/)
assert.match(nativePageSource, /bootstrap\.ui\?\.formPresentation/)
assert.match(nativePageSource, /:form-presentation="formPresentation"/)
assertDoesNotContain(nativePageSource, [
  /normalizeEmbedForm/,
  /TrustedPublishedFormRuntime/,
  /TrustedFormFieldRenderer/,
  /cspSafe/,
  /componentMap\s*=/
], 'NativeEmbeddedFormPage')

assert.match(nativeListPageSource, /allow-default-form-resolve/)
assert.match(nativeListPageSource, /target\.defaultFormResolved/)
assert.match(nativeListPageSource, /bootstrap\.ui\?\.formPresentation/)
assert.match(nativeListPageSource, /:form-presentation="formPresentation"/)
assert.match(entityListSource, /allowDefaultFormResolve/)
assert.match(entityListSource, /embedded\.value && props\.formPresentation === 'seamless'/,
  '普通管理端列表不得启用 Embed seamless 表单容器')
assert.match(entityListSource, /:form-presentation="embeddedFormPresentation"/)
assert.match(entityListSource, /当前固定列表版本没有可用的默认表单/)
for (const marker of [
  'targetListReleaseId',
  'targetListReleaseVersion',
  'targetListReleaseResolutionToken',
  'targetDefaultFormResolved',
  'targetDefaultFormReleaseResolutionToken'
]) {
  assert.match(entityDataTableSource, new RegExp(marker))
}
assert.match(entityListLauncherSource, /:release-id="releaseId"/)
assert.match(entityListLauncherSource, /:release-version="releaseVersion"/)
assert.match(
  entityListLauncherSource,
  /:release-resolution-token="releaseResolutionToken"/
)
assert.match(entityListLauncherSource, /allow-default-form-resolve/)
assert.match(entityListLauncherSource, /getFormRuntimeRelease/)
assert.match(entityListLauncherSource, /defaultFormResolved/)

assert.match(sharedRequestSource, /export function configureEmbedDelegatedRequest/)
assert.match(sharedRequestSource, /export function resetEmbedDelegatedRequest/)
assert.match(sharedRequestSource, /X-Flow-Embed-Protocol/)
assert.match(sharedRequestSource, /Authorization/)
assert.match(sharedRequestSource, /isEmbedDelegatedRequestEnabled/)
assert.match(shellSource, /configureEmbedDelegatedRequest/)
assert.match(shellSource, /resetEmbedDelegatedRequest/)
assert.match(shellSource, /getAccessToken/)
assert.match(
  bootstrapNormalizerSource,
  /formReleaseResolutionToken/
)
assert.match(bootstrapNormalizerSource, /nativeRuntimeUrl|formReleaseId/)

// 主应用与 Embed 入口只调用同一个扩展注册入口。项目 canary 字段由同一个
// form-fields registry 注册；新组件不会要求在 src/embed 下再登记一次。
assert.match(adminMainSource, /registerApplicationExtensions/)
assert.match(embedMainSource, /registerApplicationExtensions/)
assert.match(extensionEntrySource, /registerProjectExtensions/)
assert.match(projectExtensionSource, /registerFormFieldComponent/)
assert.match(projectExtensionSource, /PROJECT_ACCEPTANCE_SCORE_FIELD/)
assert.match(fieldRegistrySource, /export function registerFormFieldComponent/)
assert.match(fieldRegistrySource, /export function resolveFieldComponent/)
assert.match(fieldRegistrySource, /extensionRegistry/)
assert.match(entityDialogSource, /EntityDataFormFields/)
assert.match(approvalDialogSource, /EntityApprovalBasicInfo/)
assert.match(entityFieldsSource, /FormPreviewLinkage/)
assert.match(entityFieldsSource, /FormFieldRendererLinkage/)
assert.match(entityFieldsSource, /getCustomFormComponent/)
assert.match(fieldRendererSource, /resolveFieldComponent/)
assertDoesNotContain(
  [formPreviewSource, formNodeRuntimeSource, ...canonicalFieldSources].join('\n'),
  [/cspSafe/, /TrustedFormFieldRenderer/, /isTrustedPublishedRuntime/],
  'Flow canonical form renderer'
)
assert.equal(
  /registerFormFieldComponent\s*\(/.test(nativePageSource),
  false,
  'NativeEmbeddedFormPage 不得建立第二份字段注册表'
)

const embedSources = (await collectFiles(embedRoot))
  .filter(file => ['.js', '.vue'].includes(extname(file)))
for (const file of embedSources) {
  const text = await readFile(file, 'utf8')
  const label = relative(webRoot, file)
  assert.equal(
    /\b(?:localStorage|sessionStorage)\s*\./.test(text),
    false,
    `${label} 不得持久化 Embed Session`
  )
  assert.equal(
    /\bdocument\s*\.\s*cookie\b/.test(text),
    false,
    `${label} 不得读取普通登录 Cookie`
  )
}

for (const path of [
  'src/embed/EmbedShell.vue',
  'src/embed/runtime/NativeEmbeddedListPage.vue',
  'src/embed/runtime/NativeEmbeddedFormPage.vue'
]) {
  const filename = join(webRoot, path)
  const text = await readFile(filename, 'utf8')
  const parsed = parse(text, { filename })
  assert.deepEqual(parsed.errors, [], `${path} SFC 解析失败`)
  const descriptor = parsed.descriptor
  if (descriptor.script || descriptor.scriptSetup) {
    assert.doesNotThrow(
      () => compileScript(descriptor, { id: `embed-native-${path}` }),
      `${path} script 编译失败`
    )
  }
  if (descriptor.template) {
    const compiled = compileTemplate({
      id: `embed-native-${path}`,
      filename,
      source: descriptor.template.content,
      scoped: descriptor.styles.some(style => style.scoped)
    })
    assert.deepEqual(compiled.errors, [], `${path} template 编译失败`)
  }
}

console.log('embed native LIST/FORM architecture tests passed')
