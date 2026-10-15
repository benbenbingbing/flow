import assert from 'node:assert/strict'
import test from 'node:test'
import { mkdtempSync, rmSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { build } from 'vite'
import vue from '@vitejs/plugin-vue'
import { createExtensionInstaller } from '../core/installer.js'
import { publishExtensionCatalog, getExtensionCatalog } from '../core/catalog.js'
import { registerFormFieldComponent, resolveFieldComponentDescriptor } from '../core/registries/formFieldRegistry.js'
import { validateFieldScriptConfiguration } from '../../shared/field-script-configuration.js'
import { discoverExtensions } from '../../../build/extensions/discover.mjs'
import { extensionAdapters } from '../core/adapters/index.js'
import { getFieldEventHandler } from '@flow/workflow-core/extensions/core/registries/fieldEventHandlerRegistry'

const root = fileURLToPath(new URL('../../..', import.meta.url))

test('真实 TextField 经压缩构建后保留静态事件，安装器与目录使用同一声明，别名和版本精确解析', async () => {
  const fixture = mkdtempSync(path.join(root, '.script-metadata-test-'))
  try {
    await build({ configFile: false, root, logLevel: 'silent', plugins: [vue()], build: {
      minify: true, outDir: fixture, lib: { entry: path.join(root, 'src/extensions/builtin/fields/components/TextField.vue'), formats: ['es'], fileName: () => 'TextField.mjs' },
      rollupOptions: { external: id => id === 'vue' || id.startsWith('@flow/') }
    } })
    const { default: component } = await import(pathToFileURL(path.join(fixture, 'TextField.mjs')))
    const events = component.scriptEvents
    assert.ok(events.some(item => item.name === 'onKeydown' && item.parameters.some(parameter => parameter.name === 'isComposing')))
    const entries = ['input','password','textarea'].map(name => ({ descriptor: {
      type: 'FIELD', name, version: 1, origin: 'PLATFORM', aliases: name === 'input' ? ['string','text'] : [], implementation: { kind:'COMPONENT' }
    }, implementation: component }))
    createExtensionInstaller(extensionAdapters, publishExtensionCatalog)(entries)
    assert.ok(resolveFieldComponentDescriptor({ componentType:'string' }).capabilities.scriptEvents.some(item => item.name === 'onClear'))
    assert.equal(resolveFieldComponentDescriptor({ componentType:'textarea' }).capabilities.scriptEvents.some(item => item.name === 'onClear'), false)
    assert.equal(resolveFieldComponentDescriptor({ componentType:'input', componentVersion:2 }), undefined)
    const descriptor = resolveFieldComponentDescriptor({ componentType:'input' })
    assert.deepEqual(getExtensionCatalog({ type:'FIELD' }).find(item => item.name === 'input').capabilities.scriptEvents, descriptor.capabilities.scriptEvents)
    registerFormFieldComponent('reference', { scriptEvents: [{ name:'onChange',label:'选择' }] }, { origin:'PLATFORM' })
    assert.equal(resolveFieldComponentDescriptor({ componentType:'input',refEntityType:'USER' }).name, 'reference')
    assert.equal(validateFieldScriptConfiguration([{ componentType:'textarea',fieldCode:'note',componentProps:{ events:{ onClear:{mode:'body',code:'setValue(1)'} } } }]).length,1)
  } finally { rmSync(fixture,{recursive:true,force:true}) }
})

test('真实函数清单静态加载，精确版本注册且发布前检查缺失函数', async () => {
  const entries = discoverExtensions(root).filter(entry => entry.type === 'FIELD_EVENT_HANDLER')
  const plan = await Promise.all(entries.map(async descriptor => ({ descriptor, implementation:(await import(pathToFileURL(path.join(root,descriptor.implementation.path))))[descriptor.implementation.export] })))
  createExtensionInstaller(extensionAdapters)(plan)
  const trim = getFieldEventHandler('trimText',1)
  assert.equal(typeof trim.handler,'function')
  assert.equal(getFieldEventHandler('trimText',2),undefined)
  const field = {componentType:'input',fieldType:'STRING',fieldCode:'name',componentProps:{events:{onChange:{mode:'function',handler:'trimText',version:1}}}}
  assert.deepEqual(validateFieldScriptConfiguration([field]),[])
  field.componentProps.events.onChange.version=3
  assert.match(validateFieldScriptConfiguration([field])[0].message,/未安装/)
})
