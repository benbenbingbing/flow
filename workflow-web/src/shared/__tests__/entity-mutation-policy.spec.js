import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import {
  alignMutationStepPhase,
  allowedMutationStepPhases,
  normalizeSupportedPhases,
  validateMutationStep
} from '../entity-mutation-policy.js'

assert.deepEqual(
  allowedMutationStepPhases({ stepType: 'FIELD_MAPPING' }),
  ['PREPARE', 'BEFORE_WRITE']
)
assert.deepEqual(
  allowedMutationStepPhases({ stepType: 'MANAGED_INTERFACE' }),
  ['PREPARE']
)
assert.deepEqual(
  normalizeSupportedPhases('["BEFORE_WRITE","AFTER_COMMIT"]'),
  ['BEFORE_WRITE', 'AFTER_COMMIT']
)

const javaStep = {
  stepName: '同步扩展',
  stepType: 'JAVA_PROVIDER',
  providerCode: 'sync-provider',
  phase: 'AFTER_WRITE',
  supportedPhases: ['BEFORE_WRITE']
}
assert.match(validateMutationStep(javaStep), /不支持/)
alignMutationStepPhase(javaStep)
assert.equal(javaStep.phase, 'BEFORE_WRITE')
assert.equal(validateMutationStep(javaStep), '')

assert.equal(validateMutationStep({
  stepName: '字段处理',
  stepType: 'FIELD_MAPPING',
  phase: 'AFTER_WRITE'
}), '字段映射不支持写入后阶段')

assert.equal(validateMutationStep({
  stepName: '规则校验',
  stepType: 'BUILT_IN_RULE',
  phase: 'BEFORE_WRITE'
}), '内置规则必须选择规则实现')

assert.equal(validateMutationStep({
  stepName: '调用接口',
  stepType: 'MANAGED_INTERFACE',
  phase: 'PREPARE',
  providerCode: 'service'
}), '受管理接口必须选择接口服务及操作')

const read = relativePath => readFileSync(
  fileURLToPath(new URL(relativePath, import.meta.url)),
  'utf8'
)
const apiSource = read('../../api/entityMutationPolicy.js')
const pageSource = read('../../views/system/EntityMutationPolicyManagement.vue')
const dialogSource = read('../../views/system/components/EntityVersionConfigDialogs.vue')

assert.ok(apiSource.includes("request.get('/entity-mutation-policies/catalog')"))
assert.ok(apiSource.includes("request.get('/entity-mutation-policies/catalog/options'"))
assert.ok(pageSource.includes('row.runtimeEnabled ?? (row.activeReleaseVersion && row.enabled)'))
assert.ok(pageSource.includes('validateMutationStep(stepEditor)'))
assert.ok(pageSource.includes('normalizeSupportedPhases(item.capability)'))
assert.ok(dialogSource.includes('availablePhaseOptions'))
assert.ok(dialogSource.includes('字段映射仅能在准备或写入前阶段执行'))

console.log('entity-mutation-policy tests passed')
