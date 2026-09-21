import assert from 'node:assert/strict'
import { resolveFormLabelPosition, resolveFormLabelWidth } from '../form-layout.js'
import { resolveFormNodeLayoutSpan } from '@flow/workflow-core/form-node-property-schema'

// 历史快照中被布局模式覆盖的 gridSpan 不能因升级而突然生效。
for (const [layoutType, labelPosition, span] of [
  ['vertical', 'top', 24], ['horizontal', 'right', 12], ['grid', 'right', 8]
]) {
  const form = Object.freeze({ layoutType, viewConfig: Object.freeze({ labelWidth: 160 }) })
  assert.equal(resolveFormLabelPosition(form), labelPosition)
  assert.equal(resolveFormLabelWidth(form), labelPosition === 'top' ? 'auto' : '160px')
  assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD', gridSpan: 8 }, layoutType), span)
  assert.equal(form.viewConfig.labelPosition, undefined, '读取历史表单不能写入默认值')
  // 修改标签位置不改变旧表单的列数；新表单也可以把多列与顶部标签组合。
  for (const position of ['top', 'left', 'right']) {
    const saved = { ...form, viewConfig: JSON.stringify({ labelPosition: position, labelWidth: 180 }) }
    assert.equal(resolveFormLabelPosition(saved), position)
    assert.equal(resolveFormLabelWidth(saved), position === 'top' ? 'auto' : '180px')
    assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD', gridSpan: 8 }, saved.layoutType), span)
  }
}

for (const value of [null, '', '{invalid}', { labelPosition: 'invalid', labelWidth: -1 }]) {
  assert.equal(resolveFormLabelPosition({ layoutType: 'vertical', viewConfig: value }), 'top')
  assert.equal(resolveFormLabelWidth({ layoutType: 'grid', viewConfig: value }), '120px')
}
assert.equal(resolveFormLabelPosition(null), 'right')
assert.equal(resolveFormLabelWidth({ viewConfig: { labelWidth: '150' } }), '150px')
const form = { layoutType: 'grid', viewConfig: { labelPosition: 'right', labelWidth: 120 } }
const draft = { labelPosition: 'left', labelWidth: 200 }
assert.equal(resolveFormLabelPosition(form, draft), 'left', '设计器使用尚未保存的标签设置')
assert.equal(resolveFormLabelWidth(form, draft), '200px')
assert.equal(form.viewConfig.labelWidth, 120, '草稿不能覆盖已保存配置')

for (const span of [24, 12, 8, 16]) {
  assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD', gridSpan: span }, 'grid'), span)
}
for (const layout of ['vertical', 'horizontal']) {
  assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'ACTION_SLOT', gridSpan: 8 }, layout), 24)
}
assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD', props: { gridSpan: 8 } }, 'grid', 12), 8)
assert.equal(resolveFormNodeLayoutSpan({ nodeType: 'FIELD' }, 'grid', 12), 12, '显式 GRID 继续支持容器默认宽度')
console.log('form-layout tests passed')
