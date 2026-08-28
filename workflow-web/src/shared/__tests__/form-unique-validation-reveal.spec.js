import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  findFormNodeContainingValidationField,
  formNodeMatchesValidationField,
  formNodeSubtreeContainsValidationField
} from '../form-runtime/validationReveal.js'

const nodes = [
  { id: 'tabs', nodeKey: 'tabs', nodeType: 'TAB_SET', props: {} },
  { id: 'tab-base', parentId: 'tabs', nodeKey: 'base', nodeType: 'TAB', props: {} },
  { id: 'tab-risk', parentId: 'tabs', nodeKey: 'risk', nodeType: 'TAB', props: {} },
  { id: 'collapse-risk', parentId: 'tab-risk', nodeKey: 'riskPanel', nodeType: 'COLLAPSE', props: {} },
  { id: 'grid-risk', parentId: 'collapse-risk', nodeKey: 'riskGrid', nodeType: 'GRID', props: {} },
  {
    id: 'field-project-name',
    parentId: 'grid-risk',
    nodeKey: 'projectNameNode',
    nodeType: 'FIELD',
    bindingRef: 'entity-field-1',
    props: { fieldId: 'field-id-1', fieldCode: 'projectName' }
  }
]
const childrenFor = parentId => nodes.filter(node =>
  String(node.parentId || '') === String(parentId || '')
)

assert.equal(
  formNodeMatchesValidationField(nodes.at(-1), 'projectName'),
  true
)
assert.equal(
  formNodeMatchesValidationField(nodes.at(-1), 'entity-field-1'),
  true
)
assert.equal(
  formNodeMatchesValidationField(nodes.at(-1), 'field-id-1'),
  true
)
assert.equal(
  formNodeMatchesValidationField(nodes.at(-1), 'projectNameNode'),
  true
)
assert.equal(
  formNodeSubtreeContainsValidationField(
    nodes.find(node => node.id === 'collapse-risk'),
    childrenFor,
    'projectName'
  ),
  true,
  '关闭的 Collapse 必须能通过多层后代找到唯一错误字段'
)
assert.equal(
  findFormNodeContainingValidationField(
    childrenFor('tabs'),
    childrenFor,
    'projectName'
  )?.id,
  'tab-risk',
  '嵌套 Tab 必须定位到包含唯一错误字段的页签'
)

function source(path) {
  return readFileSync(new URL(path, import.meta.url), 'utf8')
}

const renderer = source('../../components/FormNodeRenderer.vue')
const item = source('../../components/FormNodeRuntimeItem.vue')
const preview = source('../../components/FormPreviewLinkage.vue')
const subFormRow = source(
  '../../components/form-fields/components/SubFormRowRuntime.vue'
)

assert.match(renderer, /:reveal-field-code="revealFieldCode"/)
assert.match(
  renderer,
  /revealValidationField[\s\S]*?revealFieldCode\.value\s*=\s*''[\s\S]*?await nextTick\(\)[\s\S]*?revealFieldCode\.value\s*=\s*target/,
  '连续提交同一错误字段时也必须重新触发定位'
)
assert.match(renderer, /defineExpose\(\{ validate, revealValidationField \}\)/)
assert.match(item, /revealFieldCode:\s*\{ type: String/)
assert.match(item, /revealFieldCode:\s*props\.revealFieldCode/)
assert.match(
  item,
  /node\.nodeType === 'TAB_SET'[\s\S]*?findFormNodeContainingValidationField[\s\S]*?activeTab\.value\s*=\s*targetTab\.id/,
  'TAB_SET 必须切换到包含目标字段的 Tab'
)
assert.match(
  item,
  /node\.nodeType === 'COLLAPSE'[\s\S]*?formNodeSubtreeContainsValidationField[\s\S]*?activeCollapseNames\.value\s*=\s*\[props\.node\.id\]/,
  'COLLAPSE 必须展开包含目标字段的容器'
)
assert.match(
  preview,
  /uniquePrecheckController\.checkAll[\s\S]*?!uniqueResult\.valid[\s\S]*?revealValidationField/,
  '根表仅在提交唯一预检失败时触发节点定位'
)
assert.match(
  subFormRow,
  /uniquePrecheckController\.checkAll[\s\S]*?!uniqueResult\.valid[\s\S]*?revealValidationField/,
  '子表行提交唯一预检失败时也必须定位内部节点'
)

console.log('form unique validation reveal tests passed')
