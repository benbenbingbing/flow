import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'

const frontendRoot = process.cwd()
const backendRoot = path.resolve(frontendRoot, '../workflow-server')
const frontendDefaultLimit = 900
const backendDefaultLimit = 800

// Existing debt is frozen at the audited baseline. Reducing these limits is encouraged;
// raising them requires an explicit review and an extraction plan.
const frontendGrandfatheredLimits = new Map([
  ['src/views/EntityFormDesignByEntity.vue', 3930],
  ['src/components/NodeConfigPanel.vue', 3840],
  ['src/views/EntityDesign.vue', 2517],
  ['src/views/EntityListConfigDesign.vue', 2313],
  ['src/data/user-manual/entity.js', 1425],
  ['src/views/EntityList.vue', 1406],
  ['src/views/system/Role.vue', 1356],
  ['src/data/user-manual/process.js', 1106],
  ['src/views/system/ConfigMigration.vue', 1066],
  ['src/views/system/FlowActionGuide.vue', 1038],
  ['src/views/Home.vue', 1061],
  ['src/components/LinkageConfigPanel.vue', 988],
  ['src/views/ProcessProgress.vue', 953]
])

const backendGrandfatheredLimits = new Map([
  ['workflow-entity/src/main/java/com/workflow/service/UiConfigReleaseService.java', 2793],
  ['workflow-entity/src/main/java/com/workflow/service/EntityFormNodeService.java', 2173],
  ['workflow-migration/src/main/java/com/workflow/service/migration/ConfigMigrationImportApplyService.java', 1394],
  ['workflow-entity/src/main/java/com/workflow/service/EntityDefinitionService.java', 1213],
  ['workflow-migration/src/main/java/com/workflow/service/migration/ConfigMigrationAssetService.java', 1210],
  ['workflow-entity/src/main/java/com/workflow/service/UiDataSourceService.java', 1132],
  ['workflow-process/src/main/java/com/workflow/process/runtime/ProcessProgressRuntimeService.java', 1122],
  ['workflow-entity/src/main/java/com/workflow/service/EntityDataDynamicService.java', 906],
  ['workflow-entity/src/main/java/com/workflow/service/EntityListConfigService.java', 901],
  ['workflow-entity/src/main/java/com/workflow/service/EntityFormNodePropertyPolicy.java', 900],
  ['workflow-entity/src/main/java/com/workflow/service/UiDataSourceExecutionAccessService.java', 876],
  ['workflow-entity/src/main/java/com/workflow/service/EntityFormService.java', 860],
  ['workflow-process/src/main/java/com/workflow/service/impl/TaskServiceImpl.java', 845],
  ['workflow-migration/src/main/java/com/workflow/service/migration/ConfigMigrationPackageService.java', 844],
  ['workflow-process/src/main/java/com/workflow/process/definition/ProcessDefinitionNodeSyncService.java', 835],
  ['workflow-entity/src/main/java/com/workflow/service/permission/PermissionSqlBuilder.java', 833],
  ['workflow-entity/src/main/java/com/workflow/service/EntityListRelationalConfigService.java', 805]
])

function walk(directory, includeFile, excludeDirectory = () => false) {
  const files = []
  for (const entry of readdirSync(directory)) {
    const fullPath = path.join(directory, entry)
    const stat = statSync(fullPath)
    if (stat.isDirectory()) {
      if (!excludeDirectory(fullPath)) files.push(...walk(fullPath, includeFile, excludeDirectory))
    } else if (includeFile(fullPath)) {
      files.push(fullPath)
    }
  }
  return files
}

function countLines(file) {
  const source = readFileSync(file, 'utf8')
  if (source === '') return 0
  const newlineCount = source.match(/\n/g)?.length || 0
  return newlineCount + (source.endsWith('\n') ? 0 : 1)
}

const frontendFiles = walk(
  path.join(frontendRoot, 'src'),
  file => /\.(vue|js|ts)$/.test(file) && !/(\.spec\.|\/__tests__\/)/.test(file)
)
const backendFiles = walk(
  backendRoot,
  file => file.endsWith('.java') && file.includes(`${path.sep}src${path.sep}main${path.sep}java${path.sep}`),
  directory => directory.endsWith(`${path.sep}target`) || directory.includes(`${path.sep}.idea`)
)

const issues = []
function audit(files, root, defaultLimit, grandfatheredLimits, label) {
  for (const file of files) {
    const relativePath = path.relative(root, file).split(path.sep).join('/')
    const lineCount = countLines(file)
    const limit = grandfatheredLimits.get(relativePath) ?? defaultLimit
    if (lineCount > limit) {
      const reason = grandfatheredLimits.has(relativePath)
        ? '超过审查基线，需先拆分职责后再扩展'
        : `超过新文件默认上限 ${defaultLimit}`
      issues.push(`${label} ${relativePath}: ${lineCount} 行，预算 ${limit} 行；${reason}`)
    }
  }
}

audit(frontendFiles, frontendRoot, frontendDefaultLimit, frontendGrandfatheredLimits, '前端')
audit(backendFiles, backendRoot, backendDefaultLimit, backendGrandfatheredLimits, '后端')

assert.equal(issues.length, 0, `可维护性预算审计失败:\n${issues.join('\n')}`)
console.log(
  `maintainability budget passed: ${frontendFiles.length} frontend files, `
    + `${backendFiles.length} backend files, `
    + `${frontendGrandfatheredLimits.size + backendGrandfatheredLimits.size} frozen debt files`
)
