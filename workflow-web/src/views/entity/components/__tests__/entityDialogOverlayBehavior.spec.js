import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createApp } from 'vue'
import ElementPlus, { ElDialog, ElDrawer } from 'element-plus'
import { configureElementPlusPopupDefaults } from '../../../../shared/element-plus-defaults.js'

function source(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

for (const [name, componentSource] of [
  ['实体数据新增/编辑弹窗', source('../EntityDataFormDialog.vue')],
  ['实体数据查看/审批弹窗', source('../approval/EntityApprovalDialog.vue')]
]) {
  const dialogOpeningTag = componentSource.match(/<el-dialog\b[^>]*>/s)?.[0] || ''
  assert.match(
    dialogOpeningTag,
    /:close-on-click-modal="false"/,
    `${name}点击遮罩时不得关闭`
  )
}

const originalDialogDefault = ElDialog.props.closeOnClickModal.default
const originalDrawerDefault = ElDrawer.props.closeOnClickModal.default
try {
  ElDialog.setPropsDefaults({ closeOnClickModal: true })
  ElDrawer.setPropsDefaults({ closeOnClickModal: true })
  configureElementPlusPopupDefaults()
  assert.equal(ElDialog.props.closeOnClickModal.default, false)
  assert.equal(ElDrawer.props.closeOnClickModal.default, false)
  const app = createApp({ render: () => null })
  app.use(ElementPlus)
  assert.equal(app.component('ElDialog'), ElDialog)
  assert.equal(app.component('ElDrawer'), ElDrawer)
} finally {
  ElDialog.setPropsDefaults({ closeOnClickModal: originalDialogDefault })
  ElDrawer.setPropsDefaults({ closeOnClickModal: originalDrawerDefault })
}

for (const [name, entrySource] of [
  ['主应用入口', source('../../../../main.js')],
  ['独立 Embed 入口', source('../../../../embed/embed-main.js')]
]) {
  const configureIndex = entrySource.indexOf('configureElementPlusPopupDefaults()')
  const mountIndex = entrySource.indexOf('.mount(')
  assert.ok(
    configureIndex >= 0 && configureIndex < mountIndex,
    `${name}必须在挂载前应用弹出面板默认值`
  )
}

console.log('entity dialog overlay behavior contract tests passed')
