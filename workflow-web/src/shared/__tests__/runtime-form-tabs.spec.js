import assert from 'node:assert/strict'
import { resolveRuntimeFormTabLayout } from '../form-runtime/runtimeFormTabs.js'

const form = {
  fields: [{ id: 'field-a', fieldCode: 'name' }],
  nodes: [
    { id: 'section', nodeType: 'SECTION', parentId: '', orderKey: 1_000_000 },
    { id: 'nested-tab-set', nodeType: 'TAB_SET', parentId: 'section', orderKey: 1_000_000 },
    { id: 'nested-tab', nodeType: 'TAB', parentId: 'nested-tab-set', orderKey: 1_000_000 },
    { id: 'root-tab-set', nodeType: 'TAB_SET', parentId: '', orderKey: 2_000_000 },
    {
      id: 'root-tab-a',
      nodeType: 'TAB',
      parentId: 'root-tab-set',
      orderKey: 1_000_000,
      propsDocument: JSON.stringify({ label: '第一个页签' })
    },
    {
      id: 'root-tab-b',
      nodeType: 'TAB',
      parentId: 'root-tab-set',
      orderKey: 2_000_000,
      props: { title: '第二个页签' }
    }
  ]
}

const layout = resolveRuntimeFormTabLayout(form)
assert.deepEqual(layout.liftedRootNodeIds, ['root-tab-set'])
assert.equal(layout.hasBaseContent, true)
assert.deepEqual(
  layout.tabs.map(tab => ({
    name: tab.name,
    label: tab.label,
    rootParentId: tab.rootParentId
  })),
  [
    {
      name: 'form_tab_root-tab-a',
      label: '第一个页签',
      rootParentId: 'root-tab-a'
    },
    {
      name: 'form_tab_root-tab-b',
      label: '第二个页签',
      rootParentId: 'root-tab-b'
    }
  ]
)

const tabsOnlyLayout = resolveRuntimeFormTabLayout({
  nodes: [
    { id: 'tabs', nodeType: 'TAB_SET', parentId: '' },
    { id: 'tab', nodeType: 'TAB', parentId: 'tabs', props: { label: '唯一页签' } }
  ]
})
assert.equal(tabsOnlyLayout.hasBaseContent, false)
assert.equal(tabsOnlyLayout.tabs.length, 1)

const customComponentLayout = resolveRuntimeFormTabLayout({
  customComponent: 'CustomRuntimeForm',
  fields: [{ id: 'field-a' }],
  nodes: form.nodes
})
assert.equal(customComponentLayout.tabs.length, 0)
assert.equal(customComponentLayout.hasBaseContent, true)

console.log('runtime-form-tabs tests passed')
