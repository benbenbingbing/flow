import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'

const frontendRoot = process.cwd()
const backendRoot = path.resolve(frontendRoot, '../workflow-server')
const frontendDefaultLimit = 900
const backendDefaultLimit = 800
const extractionPlanPath = path.resolve(
  frontendRoot,
  '../docs/maintainability-extraction-plan-2026-07-27.md'
)

// Existing debt is frozen at the audited baseline. Reducing these limits is encouraged;
// raising them requires an explicit review and an extraction plan.
const frontendGrandfatheredLimits = new Map([
  ['src/views/EntityFormDesignByEntity.vue', 3930],
  ['src/components/NodeConfigPanel.vue', 3840],
  ['src/views/EntityDesign.vue', 2517],
  ['src/views/EntityListConfigDesign.vue', 2313],
  ['src/data/user-manual/entity.js', 1431],
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
  ['workflow-entity/src/main/java/com/workflow/entity/ui/application/UiConfigReleaseService.java', 2793],
  ['workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormNodeService.java', 2173],
  ['workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationImportApplyService.java', 1417],
  ['workflow-entity/src/main/java/com/workflow/entity/definition/application/EntityDefinitionService.java', 1291],
  ['workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationAssetService.java', 1223],
  ['workflow-entity/src/main/java/com/workflow/entity/ui/application/UiDataSourceService.java', 1132],
  ['workflow-process/src/main/java/com/workflow/process/instance/application/ProcessProgressRuntimeService.java', 1122],
  ['workflow-entity/src/main/java/com/workflow/entity/data/application/EntityDataDynamicService.java', 1029],
  ['workflow-entity/src/main/java/com/workflow/entity/list/application/EntityListConfigService.java', 922],
  ['workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormNodePropertyPolicy.java', 900],
  ['workflow-entity/src/main/java/com/workflow/entity/ui/application/UiDataSourceExecutionAccessService.java', 876],
  ['workflow-entity/src/main/java/com/workflow/entity/form/application/EntityFormService.java', 895],
  ['workflow-process/src/main/java/com/workflow/process/task/application/TaskServiceImpl.java', 870],
  ['workflow-migration/src/main/java/com/workflow/migration/application/ConfigMigrationPackageService.java', 890],
  ['workflow-process/src/main/java/com/workflow/process/definition/application/ProcessDefinitionNodeSyncService.java', 835],
  ['workflow-entity/src/main/java/com/workflow/entity/permission/application/PermissionSqlBuilder.java', 833],
  ['workflow-entity/src/main/java/com/workflow/entity/list/application/EntityListRelationalConfigService.java', 805]
])

// Reviewed growth remains fixed at the exact audited size and is only allowed
// while the linked extraction plan is present. Any further line still fails.
const reviewedGrowthLimits = new Map([
  ['src/components/LinkageConfigPanel.vue', {
    limit: 994,
    planMarker: 'LinkageConfigPanel.vue'
  }],
  ['src/components/NodeConfigPanel.vue', {
    limit: 3943,
    planMarker: 'NodeConfigPanel.vue'
  }],
  ['workflow-process/src/main/java/com/workflow/process/definition/application/ProcessBpmnPublishSanitizer.java', {
    limit: 1156,
    planMarker: 'ProcessBpmnPublishSanitizer.java'
  }],
  ['workflow-process/src/main/java/com/workflow/process/definition/application/ProcessDefinitionNodeSyncService.java', {
    limit: 837,
    planMarker: 'ProcessDefinitionNodeSyncService.java'
  }],
  ['workflow-process/src/main/java/com/workflow/process/instance/application/ProcessProgressRuntimeService.java', {
    limit: 1183,
    planMarker: 'ProcessProgressRuntimeService.java'
  }]
])

const extractionPlan = readFileSync(extractionPlanPath, 'utf8')
for (const [relativePath, review] of reviewedGrowthLimits) {
  assert.ok(
    extractionPlan.includes(review.planMarker),
    `已评审增长缺少拆分计划: ${relativePath}`
  )
}

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
    const reviewedGrowth = reviewedGrowthLimits.get(relativePath)
    const limit = reviewedGrowth?.limit
      ?? grandfatheredLimits.get(relativePath)
      ?? defaultLimit
    if (lineCount > limit) {
      const reason = reviewedGrowth
        ? `超过已评审增长基线，拆分计划见 ${path.basename(extractionPlanPath)}`
        : grandfatheredLimits.has(relativePath)
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
    + `${frontendGrandfatheredLimits.size + backendGrandfatheredLimits.size} frozen debt files, `
    + `${reviewedGrowthLimits.size} reviewed growth files`
)
