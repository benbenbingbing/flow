import assert from 'node:assert/strict'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { readFileSync, mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { discoverExtensions } from '../../../build/extensions/discover.mjs'
import { validateManifest, validateCollisions } from '../../../build/extensions/validate.mjs'
import { generateExtensionModule, writeFieldDefinitions } from '../../../build/extensions/generate.mjs'
import { createExtensionInstaller } from '../core/installer.js'
import { extensionAdapters } from '../core/adapters/index.js'
import { getListToolbarAction, getListRowAction } from '../core/registries/listActionRegistry.js'
import { getEntityActionRuleCondition } from '../core/registries/entityActionRuleRegistry.js'
import { resolveFieldComponent, getFormFieldComponentOptions } from '../core/registries/formFieldRegistry.js'
import { getDefaultFormFieldComponentType } from '@flow/workflow-core/extensions/core/fieldPolicy'
import { getCustomValidator } from '@flow/workflow-core/extensions/core/registries/validatorRegistry'
import { publishExtensionCatalog, getExtensionCatalog } from '../core/catalog.js'
import { installTestValidators } from './helpers/install-node-extensions.mjs'

const root = fileURLToPath(new URL('../../..', import.meta.url))
const entries = discoverExtensions(root)
const sample = entries.find(item => item.name === 'project_acceptance_score')
const copy = value => JSON.parse(JSON.stringify(value))

test('现有清单覆盖十类，保留业务名称和 Demo 开关；生成字段策略与清单一致', () => {
  assert.equal(new Set(entries.map(item => item.type)).size, 10)
  for (const name of ['ProjectMemberChangeForm', 'PROJECT_CUSTOM_LIST_SCHEMA', 'project_acceptance_score', 'PROJECT:CUSTOM_CONDITION', 'amount', 'DefaultText']) {
    assert.ok(entries.some(item => item.name === name && item.enabled !== false), name)
  }
  assert.equal(entries.find(item => item.name === 'DemoProjectForm').origin, 'EXAMPLE')
  assert.equal(entries.find(item => item.name === 'ContractExampleForm').enabled, false)
  writeFieldDefinitions(entries, root, { check: true })
  assert.equal(getDefaultFormFieldComponentType('TEXT'), 'textarea')
  assert.equal(getDefaultFormFieldComponentType('STRING'), 'input')
  assert.equal(getDefaultFormFieldComponentType('DEPT'), 'reference')
})

test('新模块只增加实现和 JSON 就能被发现，具名导出生成明确 import', () => {
  const fixture = mkdtempSync(path.join(tmpdir(), 'flow-extensions-'))
  try {
    mkdirSync(path.join(fixture, 'src/modules/new'), { recursive: true })
    mkdirSync(path.join(fixture, 'src/extensions/manifests/business/new/fields'), { recursive: true })
    writeFileSync(path.join(fixture, 'src/modules/new/Field.js'), 'export const Field = { name: "new-field" }')
    const manifest = { schemaVersion: 1, type: 'FIELD', name: 'new_field', label: '新字段', version: 1, implementation: { path: 'src/modules/new/Field.js', export: 'Field', kind: 'COMPONENT' } }
    writeFileSync(path.join(fixture, 'src/extensions/manifests/business/new/fields/field.extension.json'), JSON.stringify(manifest))
    const discovered = discoverExtensions(fixture)
    assert.equal(discovered.length, 1)
    assert.equal(discovered[0].module, 'new')
    assert.match(generateExtensionModule(discovered, fixture), /import \{ Field as implementation0 \}/)
  } finally { rmSync(fixture, { recursive: true, force: true }) }
})

test('错拼配置、越界路径、错误类型、冲突别名和单版本覆盖均拒绝', () => {
  const invalid = modify => { const value = copy(sample); delete value.origin; delete value.module; delete value.sourceFile; modify(value); return value }
  assert.throws(() => validateManifest(invalid(v => v.metadtaa = {}), root, 'bad.json'), /未知属性/)
  assert.throws(() => validateManifest(invalid(v => v.implementation.path = 'src/../outside.js'), root, 'bad.json'), /实现路径/)
  assert.throws(() => validateManifest(invalid(v => v.implementation.kind = 'FUNCTION'), root, 'bad.json'), /需要 COMPONENT/)
  assert.throws(() => validateManifest(invalid(v => v.metadata.configSchema.push(v.metadata.configSchema[0])), root, 'bad.json'), /key 重复/)
  assert.throws(() => validateCollisions([sample, { ...sample, version: 2 }]), /冲突/)
  assert.throws(() => validateCollisions([sample, { ...sample, name: 'another', aliases: [sample.name.toUpperCase()] }]), /冲突/)
  const form = entries.find(item => item.type === 'FORM')
  assert.doesNotThrow(() => validateCollisions([form, { ...form, version: 2 }]))
})

test('导出验证先于写入；函数不在启动时执行，工厂类同步实例化且安装幂等', () => {
  let executed = 0
  const values = []
  const install = createExtensionInstaller({ LIST_ACTION: (_entry, value) => values.push(value), VALIDATOR: (_entry, value) => values.push(value) })
  const action = { descriptor: { type: 'LIST_ACTION', name: 'action', version: 1, implementation: { kind: 'FUNCTION' } }, implementation: () => executed++ }
  class Validator { validate() { return true } }
  const validator = { descriptor: { type: 'VALIDATOR', name: 'class', version: 1, implementation: { kind: 'CLASS' } }, implementation: Validator }
  install([action, validator]); install([action, validator])
  assert.equal(executed, 0)
  assert.equal(values.length, 2)
  assert.ok(values[1] instanceof Validator)
  const writes = []
  const broken = createExtensionInstaller({ LIST_ACTION: entry => writes.push(entry) })
  assert.throws(() => broken([action, { ...action, implementation: undefined }]), /导出必须/)
  assert.equal(writes.length, 0)
  assert.throws(() => broken([action]), /曾失败/)
  const asynchronous = createExtensionInstaller({ VALIDATOR() {} })
  assert.throws(() => asynchronous([{ descriptor: { ...validator.descriptor, implementation: { kind: 'FACTORY' } }, implementation: () => Promise.resolve(new Validator()) }]), /必须同步/)
})

test('Demo 默认不安装，启用后安装；统一目录返回副本且正确区分治理边界', () => {
  const records = []
  const source = [{ descriptor: { ...sample, origin: 'EXAMPLE' }, implementation: {} }]
  createExtensionInstaller({ FIELD: entry => records.push(entry) })(source)
  assert.equal(records.length, 0)
  createExtensionInstaller({ FIELD: entry => records.push(entry) }, publishExtensionCatalog)(source, { enableDemo: true })
  assert.equal(records.length, 1)
  const catalog = getExtensionCatalog()
  assert.equal(catalog[0].managed, true)
  catalog[0].name = 'changed'
  assert.equal(getExtensionCatalog()[0].name, sample.name)
})

test('真实适配器保留字段特殊解析、双位置动作和独立条件默认值', () => {
  const platform = entries.filter(item => item.type === 'FIELD' && item.origin === 'PLATFORM')
  const components = new Map()
  const plan = platform.map(descriptor => {
    const component = components.get(descriptor.implementation.path) || { name: descriptor.name }
    components.set(descriptor.implementation.path, component)
    return { descriptor, implementation: component }
  })
  createExtensionInstaller(extensionAdapters)(plan)
  assert.equal(resolveFieldComponent({ componentType: 'text' }), resolveFieldComponent({ componentType: 'input' }))
  assert.equal(resolveFieldComponent({ componentType: 'input', fieldType: 'SUB_FORM' }), resolveFieldComponent({ componentType: 'sub_form' }))
  assert.equal(resolveFieldComponent({ componentType: 'input', refEntityType: 'USER' }), resolveFieldComponent({ componentType: 'reference' }))
  assert.equal(getFormFieldComponentOptions().length, platform.length)
  const action = entries.find(item => item.name === 'projectAcceptanceSelectionAction')
  const handler = () => 'selection'
  const condition = entries.find(item => item.type === 'ACTION_CONDITION' && item.origin === 'BUSINESS')
  createExtensionInstaller(extensionAdapters)([{ descriptor: action, implementation: handler }, { descriptor: condition, implementation: {} }])
  assert.equal(getListToolbarAction(action.name), handler)
  assert.equal(getListRowAction(action.name), handler)
  const rule = getEntityActionRuleCondition(condition.name)
  const first = rule.createDefault(); first.field = 'changed'
  assert.equal(rule.createDefault().field, 'acceptance_scene')
})

test('金额校验器通过正式清单安装，参数行为不变', async () => {
  await installTestValidators()
  const validator = getCustomValidator('amount', 1).validator
  assert.equal(validator.validate(60, { params: { maxAmount: 100 } }), true)
  assert.equal(validator.validate(101, { params: { maxAmount: 100 } }), '金额不能超过 100')
})
