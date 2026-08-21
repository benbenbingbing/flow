import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const srcRoot = join(dirname(fileURLToPath(import.meta.url)), '../..')
const backendRoot = join(srcRoot, '../../workflow-server')

const guide = readFileSync(join(srcRoot, 'views/system/ListFieldExtensionGuide.vue'), 'utf8')
const cellRegistry = readFileSync(join(srcRoot, 'utils/listCellRegistry.js'), 'utf8')
const listRuntime = readFileSync(join(srcRoot, 'shared/list-runtime/index.js'), 'utf8')
const cellRenderer = readFileSync(join(srcRoot, 'components/ListCellRenderer.vue'), 'utf8')
const entityTable = readFileSync(join(srcRoot, 'views/entity/components/EntityDataTable.vue'), 'utf8')
const listDesigner = readFileSync(join(srcRoot, 'views/EntityListConfigDesign.vue'), 'utf8')
const demoIndex = readFileSync(join(srcRoot, 'demo/index.js'), 'utf8')
const demoCell = readFileSync(join(srcRoot, 'demo/list-fields/DemoRiskProgressCell.vue'), 'utf8')
const extensionRegister = readFileSync(join(srcRoot, 'extensions/register.js'), 'utf8')
const mainJs = readFileSync(join(srcRoot, 'main.js'), 'utf8')
const listConfigApi = readFileSync(join(srcRoot, 'api/entityListConfig.js'), 'utf8')
const router = readFileSync(join(srcRoot, 'router/index.js'), 'utf8')

const provider = readFileSync(
  join(backendRoot, 'workflow-entity/src/main/java/com/workflow/entity/list/extension/ListFieldDataProvider.java'),
  'utf8'
)
const registry = readFileSync(
  join(backendRoot, 'workflow-entity/src/main/java/com/workflow/entity/list/extension/ListFieldDataProviderRegistry.java'),
  'utf8'
)
const templateProvider = readFileSync(
  join(backendRoot, 'workflow-entity/src/main/java/com/workflow/entity/list/extension/TemplateListFieldDataProvider.java'),
  'utf8'
)
const conditionEvaluator = readFileSync(
  join(backendRoot, 'workflow-entity/src/main/java/com/workflow/entity/list/extension/ListFieldConditionEvaluator.java'),
  'utf8'
)
const enrichService = readFileSync(
  join(backendRoot, 'workflow-entity/src/main/java/com/workflow/entity/list/application/EntityDataListConfigService.java'),
  'utf8'
)
const projectProvider = readFileSync(
  join(backendRoot, 'workflow-project/src/main/java/com/workflow/project/custom/ProjectCustomListFieldDataProvider.java'),
  'utf8'
)
const controller = readFileSync(
  join(backendRoot, 'workflow-entity/src/main/java/com/workflow/entity/list/api/web/EntityListConfigController.java'),
  'utf8'
)

assert.match(router, /path: '\/system\/list-field-guide'/)
assert.match(router, /title: '列表字段扩展2'/)
assert.match(guide, /列表字段扩展 2/)

assert.match(cellRegistry, /export function registerCellComponent/)
assert.match(guide, /registerCellComponent/)
assert.match(guide, /from '@\/utils\/listCellRegistry'/)

for (const prop of ['value', 'row', 'field', 'config', 'context']) {
  assert.match(cellRenderer, new RegExp(`:${prop}=`))
  assert.match(demoCell, new RegExp(`\\b${prop}\\b`))
  assert.match(guide, new RegExp(`\\b${prop}\\b`))
}

assert.match(listRuntime, /const extValue = getContainerValue\(row\?\.extData, fieldCode\)/)
assert.match(guide, /extData > data/)

assert.match(cellRenderer, /props\.field\?\.renderConfig/)
assert.match(guide, /renderConfig/)
assert.match(listDesigner, /v-model="editingField.renderConfig"|editingRenderConfig/)

assert.match(entityTable, /entityCode,/)
assert.match(entityTable, /entityDefinition,/)
assert.match(entityTable, /entityStatusMap,/)
assert.match(entityTable, /refresh,/)
assert.match(entityTable, /refEntityNameMap/)
assert.match(guide, /refEntityNameMap/)

assert.match(demoIndex, /DemoRiskProgressCell/)
assert.match(demoIndex, /warningAt/)
assert.match(extensionRegister, /enableDemo/)
assert.match(mainJs, /registerApplicationExtensions/)
assert.ok(existsSync(join(srcRoot, 'demo/list-fields/DemoRiskProgressCell.vue')))

assert.match(provider, /void enrich\(/)
assert.match(provider, /boolean supportsQuery/)
assert.match(provider, /boolean supportsVirtualField/)
assert.match(registry, /\[A-Z\]\[A-Z0-9_\]\{1,63\}/)
assert.match(guide, /\[A-Z\]\[A-Z0-9_\]\{1,63\}/)

assert.match(templateProvider, /return "FIELD_TEMPLATE"/)
assert.match(templateProvider, /\$\{fieldCode\}/)
assert.match(guide, /FIELD_TEMPLATE/)
assert.match(guide, /\$\{dataNo\} - \$\{name\}/)

assert.match(projectProvider, /PROJECT_CUSTOM_FIELD/)
assert.match(projectProvider, /record.getExtData\(\)\.put/)
assert.match(guide, /PROJECT_CUSTOM_FIELD/)

for (const key of ['entityCode', 'listKey', 'listConfigId', 'userId', 'userName']) {
  assert.match(enrichService, new RegExp(`context\\.put\\("${key}"`))
  assert.match(guide, new RegExp(`\\b${key}\\b`))
}

assert.doesNotMatch(
  enrichService,
  /context\.put\("scene"/,
  '字段 Provider context 实际没有 scene'
)
assert.doesNotMatch(
  enrichService,
  /context\.put\("DataScopePlan"/,
  '字段 Provider context 实际没有 DataScopePlan'
)
assert.match(guide, /五个入参|五个 props|五个名字/)
assert.match(guide, /不要改名/)
assert.match(guide, /getExtData\(\)\.put/)
assert.doesNotMatch(guide, /自定义列表组件|自定义表单组件|旧版「列表字段扩展」|另一本手册/)

assert.match(conditionEvaluator, /\{fieldCode\}_op/)
assert.match(guide, /\{fieldCode\}_op/)
assert.match(guide, /\{fieldCode\}_start/)

assert.match(controller, /@GetMapping\("\/extension-options"\)/)
assert.match(listConfigApi, /\/entity-list-config\/extension-options/)
assert.match(guide, /GET \/api\/entity-list-config\/extension-options/)

assert.match(listDesigner, /添加虚拟列/)
assert.match(listDesigner, /当前列已保存，尚未发布/)
assert.match(guide, /当前列已保存，尚未发布/)

assert.match(guide, /先总后分|先看全貌/)
assert.match(guide, /ListCellRenderer 传下来的/)
assert.match(guide, /每个属性都填上/)
assert.match(guide, /riskScore_display|字段_display/)
assert.match(guide, /queryConfig/)
assert.match(guide, /columnConfig/)
assert.match(guide, /visibleWhen/)
assert.match(guide, /showOverflowTooltip/)
assert.match(guide, /supportedEntityCodes/)
assert.match(guide, /getSupportedEntityCodes/)
assert.match(guide, /filterOptionsByEntity/)
assert.match(listDesigner, /selectableCellComponentOptions/)
assert.match(listDesigner, /filterOptionsByEntity/)
assert.match(guide, /refEntityNameMap/)
assert.match(guide, /actionCapabilities/)
assert.match(listRuntime, /\$\{fieldCode\}_display/)
assert.match(listDesigner, /editingQueryConfig\.placeholder/)
assert.match(listDesigner, /editingColumnConfig\.minWidth/)

console.log('list field extension guide source check passed')
