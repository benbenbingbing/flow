import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { discoverExtensions } from '../../../build/extensions/discover.mjs'
import { publishExtensionCatalog, getExtensionCatalog } from '../core/catalog.js'
import { localExtensionRows } from '../core/catalogRows.js'
import { createExtensionInstaller } from '../core/installer.js'
import { extensionAdapters } from '../core/adapters/index.js'
import {
  getCustomFormComponent, getCustomFormComponentOptions, getCustomFormComponentVersionOptions,
  getCustomListComponent, getCustomListComponentOptions, hasCustomListComponent
} from '../core/registries/customComponentRegistry.js'

const root = fileURLToPath(new URL('../../..', import.meta.url))

test('全部十类出现在目录中，远程纳管项去重，其余项只展示构建信息', () => {
  const entries = discoverExtensions(root).filter(item => item.enabled !== false)
  publishExtensionCatalog(entries)
  const catalog = getExtensionCatalog()
  const managed = catalog.find(item => item.type === 'FORM')
  const keys = new Set([`UI_FORM:${managed.name}:${managed.version}`])
  const rows = localExtensionRows(catalog, keys)
  assert.equal(new Set(rows.map(item => item.capabilityType)).size, 10)
  assert.ok(!rows.some(item => item.key === managed.name))
  const validator = rows.find(item => item.capabilityType === 'UI_VALIDATOR')
  assert.equal(validator.buildOnly, true)
  assert.equal(validator.status, 'BUILD_ONLY')
  assert.equal(validator.implementationOrigin, 'COMMON')
  assert.ok(rows.filter(item => item.implementationOrigin === 'PLATFORM').every(item => item.buildOnly))
  const form = rows.find(item => item.capabilityType === 'UI_FORM')
  assert.equal(form.buildOnly, false)
  assert.equal(form.status, 'DISCOVERED')
  assert.equal(localExtensionRows(catalog, keys, { capabilityType: 'UI_VALIDATOR', implementationOrigin: 'COMMON', keyword: 'AMOUNT', status: 'BUILD_ONLY' }).length, 1)
  assert.equal(localExtensionRows(catalog, keys, { capabilityType: 'UI_VALIDATOR', status: 'ACTIVE' }).length, 0)
})

test('页面与关联宿主使用相同上下文过滤，精确版本仍遵守声明', () => {
  const make = (type, name, usageContexts, version = 1) => ({
    descriptor: { type, name, version, implementation: { kind: 'COMPONENT' }, metadata: { usageContexts } },
    implementation: { name: `${name}${version}` }
  })
  createExtensionInstaller(extensionAdapters)([
    make('FORM', 'TestPageForm', ['PAGE']),
    make('FORM', 'TestRelatedForm', ['RELATED_CONTENT']),
    make('FORM', 'TestRelatedForm', ['RELATED_CONTENT'], 2),
    make('LIST', 'TestDualList', ['PAGE', 'RELATED_CONTENT'])
  ])
  assert.ok(getCustomFormComponent('TestPageForm'))
  assert.equal(getCustomFormComponent('TestPageForm', 1, undefined, 'RELATED_CONTENT'), undefined)
  assert.equal(getCustomFormComponent('TestRelatedForm'), undefined)
  assert.ok(getCustomFormComponent('TestRelatedForm', 1, undefined, 'RELATED_CONTENT'))
  assert.deepEqual(getCustomFormComponentOptions().map(item => item.name), ['TestPageForm'])
  assert.deepEqual(getCustomFormComponentOptions('RELATED_CONTENT').map(item => item.name), ['TestRelatedForm'])
  assert.equal(getCustomFormComponentVersionOptions('TestRelatedForm').length, 0)
  assert.equal(getCustomFormComponentVersionOptions('TestRelatedForm', 'RELATED_CONTENT').length, 2)
  assert.ok(getCustomListComponent('TestDualList'))
  assert.ok(hasCustomListComponent('TestDualList', 1, undefined, 'RELATED_CONTENT'))
  assert.equal(getCustomListComponentOptions('RELATED_CONTENT').length, 1)
})

test('对象、同步工厂和命名钩子按契约安装，服务显式传入工厂', () => {
  const service = { limit: 10 }
  const observed = []
  const install = createExtensionInstaller({ VALIDATOR: (_entry, value) => observed.push(value), NODE: (_entry, _value, metadata) => observed.push(metadata) })
  const entry = (name, kind, implementation) => ({ descriptor: { type: 'VALIDATOR', name, version: 1, implementation: { kind } }, implementation })
  const object = { validate: () => true }
  const migrate = value => ({ ...value, migrated: true })
  install([
    entry('object', 'OBJECT', object),
    entry('factory', 'FACTORY', ({ services }) => ({ validate: value => value <= services.rule.limit })),
    { descriptor: { type: 'NODE', name: 'hook', version: 1, implementation: { kind: 'COMPONENT' } }, implementation: {}, hooks: { migrateConfig: migrate } }
  ], { services: { rule: service } })
  assert.equal(observed[0], object)
  assert.equal(observed[1].validate(11), false)
  assert.deepEqual(observed[2].migrateConfig({ current: 1 }), { current: 1, migrated: true })
})

test('清单编辑器 Schema 路径可解析；业务实现没有第二套注册入口', () => {
  for (const item of discoverExtensions(root)) {
    const file = path.join(root, item.sourceFile)
    const manifest = JSON.parse(readFileSync(file, 'utf8'))
    assert.equal(path.resolve(path.dirname(file), manifest.$schema), path.resolve(root, '../extensions/schemas/extension.schema.json'))
    const source = readFileSync(path.join(root, item.implementation.path), 'utf8')
    assert.doesNotMatch(source, /\bregister(?:CustomFormComponent|CustomListComponent|FormFieldComponent|FormNodeComponent|CellComponent|ListButtonComponent|ListToolbarAction|ListRowAction|CustomValidator|EntityActionRuleCondition|EntityPermissionOptionProvider)\s*\(/)
  }
  for (const old of ['src/project', 'src/demo', 'src/contracts', 'src/components/form-fields', 'src/components/list-cells']) assert.equal(existsSync(path.join(root, old)), false, old)
})
