import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  createCustomFormActionSlotContract,
  formActionsForOwner,
  mergeResolvedFormActions,
  normalizeCustomButton,
  normalizeFormButtonAppearance,
  publishedFormButtonKeys,
  resolveFormButtonAppearanceProps,
  resolveLocalFormActions,
  validateFormActionConfiguration
} from '../form-actions.js'

const baseForm = {
  id: 'form-1',
  viewConfig: {}
}

const buttonAppearanceCases = [
  ['DEFAULT', { plain: false, round: false, circle: false }],
  ['PLAIN', { plain: true, round: false, circle: false }],
  ['ROUND', { plain: false, round: true, circle: false }],
  ['CIRCLE', { plain: false, round: false, circle: true }]
]

buttonAppearanceCases.forEach(([appearance, expectedProps]) => {
  assert.equal(
    normalizeCustomButton({ buttonAppearance: appearance }).buttonAppearance,
    appearance,
    `自定义按钮必须保留 ${appearance} 外观枚举`
  )
  const appearanceProps = resolveFormButtonAppearanceProps(appearance)
  assert.deepEqual(appearanceProps, expectedProps)
  assert.ok(
    Object.values(appearanceProps).filter(Boolean).length <= 1,
    '按钮外观必须映射为 plain、round、circle 三项互斥属性'
  )
})

;[undefined, null, '', 'rounded', 'plain-button', 123].forEach(value => {
  assert.equal(
    normalizeFormButtonAppearance(value),
    'DEFAULT',
    '缺省或非法外观必须回退为 DEFAULT，以保持既有按钮的默认效果'
  )
  assert.equal(
    normalizeCustomButton({ buttonAppearance: value }).buttonAppearance,
    'DEFAULT'
  )
  assert.deepEqual(
    resolveFormButtonAppearanceProps(value),
    { plain: false, round: false, circle: false }
  )
})

const invalidAppearanceResult = validateFormActionConfiguration({
  actionBar: {
    customButtons: [{
      key: 'invalid_appearance',
      label: '非法外观',
      enabled: false,
      modes: ['view'],
      placement: 'FOOTER',
      buttonAppearance: 'PILL'
    }]
  },
  requireEventBindings: false
})
assert.ok(invalidAppearanceResult.errors.some(item =>
  item.code === 'INVALID_BUTTON_APPEARANCE'
))

;['', 'UnknownIcon'].forEach(icon => {
  const missingCircleIconResult = validateFormActionConfiguration({
    actionBar: {
      customButtons: [{
        key: 'empty_circle',
        label: '无有效图标圆形按钮',
        enabled: false,
        modes: ['view'],
        placement: 'FOOTER',
        buttonAppearance: 'CIRCLE',
        icon
      }]
    },
    requireEventBindings: false
  })
  assert.ok(missingCircleIconResult.errors.some(item =>
    item.code === 'MISSING_CIRCLE_ICON'
  ))
})

assert.deepEqual(
  resolveLocalFormActions(baseForm, {
    mode: 'create',
    workflowReady: false
  }).map(item => item.key),
  ['close', 'reset', 'save']
)

assert.deepEqual(
  resolveLocalFormActions(baseForm, {
    mode: 'create',
    workflowReady: true
  }).map(item => item.key),
  ['close', 'reset', 'save', 'saveAndStart']
)

assert.deepEqual(
  resolveLocalFormActions(baseForm, {
    mode: 'view'
  }).map(item => item.key),
  ['close']
)

const configured = {
  id: 'form-2',
  viewConfig: {
    actionBar: {
      version: 1,
      builtInOverrides: {
        save: {
          enabled: true,
          labelByMode: { edit: '提交修改' }
        }
      },
      customButtons: [{
        key: 'generate_report',
        label: '生成报告',
        modes: ['view'],
        placement: 'FOOTER',
        perm: 'entity:demo:custom:generate_report'
      }]
    }
  }
}

assert.equal(
  resolveLocalFormActions(configured, { mode: 'edit' })
    .find(item => item.key === 'save')?.label,
  '提交修改'
)

assert.deepEqual(
  resolveLocalFormActions(configured, { mode: 'view' })
    .map(item => item.runtimeKey),
  ['close', 'form-2:generate_report']
)

assert.deepEqual(
  mergeResolvedFormActions([
    [{ key: 'close', type: 'built-in', sort: 10 }],
    [
      { key: 'close', type: 'built-in', sort: 10 },
      {
        ownerFormId: 'form-2',
        key: 'notify',
        type: 'custom',
        sort: 50
      }
    ]
  ]).map(item => item.runtimeKey || item.key),
  ['close', 'form-2:notify']
)

assert.deepEqual(
  formActionsForOwner([
    { key: 'close', type: 'built-in' },
    { key: 'own', type: 'custom', ownerFormId: 'form-2' },
    { key: 'other', type: 'custom', ownerFormId: 'form-3' },
    { key: 'unscoped', type: 'custom' }
  ], configured).map(item => item.key),
  ['close', 'own'],
  '多表单运行时只向组件暴露当前表单的自定义动作'
)

const validActionConfig = {
  customButtons: [{
    key: 'generate_report',
    label: '生成报告',
    enabled: true,
    perm: 'entity:report:generate',
    modes: ['edit', 'view'],
    placement: 'ACTION_SLOT',
    slotKey: 'report_actions'
  }]
}
const validActionBindings = [{
  targetType: 'BUTTON',
  targetKey: 'generate_report',
  eventCode: 'FORM_BUTTON_CLICK',
  enabled: true,
  inheritanceMode: 'INHERIT'
}]

assert.deepEqual(
  validateFormActionConfiguration({
    actionBar: validActionConfig,
    nodes: [{ nodeType: 'ACTION_SLOT', nodeKey: 'report_actions' }],
    eventBindings: validActionBindings
  }),
  { valid: true, errors: [] }
)

const invalidActionResult = validateFormActionConfiguration({
  actionBar: {
    customButtons: [{
      key: '',
      label: '无效按钮',
      enabled: true,
      perm: '',
      modes: [],
      placement: 'ACTION_SLOT',
      slotKey: 'missing_slot'
    }]
  },
  nodes: [],
  eventBindings: []
})
assert.equal(invalidActionResult.valid, false)
assert.deepEqual(
  new Set(invalidActionResult.errors.map(item => item.code)),
  new Set([
    'INVALID_KEY',
    'MISSING_PERMISSION',
    'INVALID_MODES',
    'INVALID_SLOT'
  ])
)

const missingBindingResult = validateFormActionConfiguration({
  actionBar: {
    customButtons: [{
      key: 'notify_owner',
      label: '通知负责人',
      enabled: true,
      perm: 'entity:demo:notify',
      modes: ['edit'],
      placement: 'FOOTER'
    }]
  },
  eventBindings: []
})
assert.ok(missingBindingResult.errors.some(item =>
  item.code === 'MISSING_EVENT_BINDING'
))

const structuralValidationResult = validateFormActionConfiguration({
  actionBar: {
    customButtons: [
      {
        key: 'duplicate_action',
        label: '启用按钮',
        enabled: true,
        perm: 'invalid-permission',
        modes: ['edit'],
        placement: 'FOOTER'
      },
      {
        key: 'duplicate_action',
        label: '',
        enabled: false,
        modes: [],
        placement: 'ACTION_SLOT',
        slotKey: 'missing_slot',
        confirm: { enabled: true, message: '' }
      },
      {
        key: 'save',
        label: '占用平台编码',
        enabled: false,
        modes: ['view'],
        placement: 'FOOTER'
      }
    ]
  },
  eventBindings: validActionBindings
})
const structuralErrorCodes = new Set(
  structuralValidationResult.errors.map(item => item.code)
)
;[
  'DUPLICATE_KEY',
  'MISSING_PERMISSION',
  'INVALID_LABEL',
  'INVALID_MODES',
  'INVALID_SLOT',
  'INVALID_CONFIRM_MESSAGE',
  'RESERVED_KEY'
].forEach(code => {
  assert.ok(
    structuralErrorCodes.has(code),
    `保存前结构校验缺少服务端一致性错误: ${code}`
  )
})

const orphanBindingResult = validateFormActionConfiguration({
  actionBar: { customButtons: [] },
  eventBindings: [{
    targetType: 'BUTTON',
    targetKey: 'close',
    eventCode: 'FORM_BUTTON_CLICK'
  }]
})
assert.ok(orphanBindingResult.errors.some(item =>
  item.code === 'ORPHAN_EVENT_BINDING'
))
assert.equal(
  validateFormActionConfiguration({
    actionBar: { customButtons: [] },
    eventBindings: [{
      targetType: 'BUTTON',
      targetKey: 'removed_list_button',
      eventCode: 'ROW_BUTTON_CLICK'
    }]
  }).valid,
  true,
  '表单校验不应把其他事件范围的历史记录误判为 FORM_BUTTON_CLICK 遗留绑定'
)

assert.deepEqual(
  publishedFormButtonKeys([{
    id: 'release-1',
    status: 'ACTIVE',
    snapshotDocument: JSON.stringify({
      form: {
        viewConfig: {
          actionBar: {
            customButtons: [{ key: 'published_action' }]
          }
        }
      }
    })
  }], 'release-1'),
  ['published_action']
)

const triggeredActions = []
const slotContract = createCustomFormActionSlotContract([
  {
    key: 'generate_report',
    runtimeKey: 'form-2:generate_report',
    placement: 'ACTION_SLOT',
    slotKey: 'report_actions',
    enabled: true,
    visible: true,
    confirm: { enabled: false, message: '' }
  },
  {
    key: 'disabled_action',
    runtimeKey: 'form-2:disabled_action',
    placement: 'ACTION_SLOT',
    slotKey: 'report_actions',
    enabled: false,
    visible: true
  },
  {
    key: 'hidden_action',
    runtimeKey: 'form-2:hidden_action',
    placement: 'ACTION_SLOT',
    slotKey: 'report_actions',
    enabled: true,
    visible: false
  },
  {
    key: 'footer_action',
    runtimeKey: 'form-2:footer_action',
    placement: 'FOOTER',
    enabled: true,
    visible: true
  },
  {
    key: 'constructor_action',
    runtimeKey: 'form-2:constructor_action',
    placement: 'ACTION_SLOT',
    slotKey: 'constructor',
    enabled: true,
    visible: true
  }
], action => triggeredActions.push(action))
assert.deepEqual(
  slotContract.slots.report_actions.map(action => action.key),
  ['generate_report', 'disabled_action']
)
assert.equal(Object.isFrozen(slotContract.slots.report_actions), true)
assert.equal(
  slotContract.slots.constructor[0].key,
  'constructor_action',
  '合法插槽键不能与 Object.prototype 属性冲突'
)
assert.equal(Object.isFrozen(slotContract.slots.report_actions[1]), true)
assert.equal(
  Object.isFrozen(slotContract.slots.report_actions[0].confirm),
  true,
  '动作插槽向自定义组件暴露的嵌套配置也必须只读'
)
assert.throws(() => {
  slotContract.slots.report_actions[0].confirm.enabled = true
}, TypeError)
assert.equal(slotContract.trigger('generate_report'), true)
assert.equal(slotContract.trigger('form-2:generate_report'), true)
assert.equal(slotContract.trigger({
  key: 'disabled_action',
  enabled: true,
  placement: 'ACTION_SLOT'
}), false, '伪造动作对象不能绕过宿主解析出的 disabled 状态')
assert.equal(slotContract.trigger('hidden_action'), false)
assert.equal(slotContract.trigger('footer_action'), false)
assert.deepEqual(
  triggeredActions.map(action => action.key),
  ['generate_report', 'generate_report']
)
assert.deepEqual(
  triggeredActions.map(action => action.confirm.enabled),
  [false, false],
  '自定义组件不能通过暴露副本修改宿主权威动作'
)

const formButtonPanelSource = readFileSync(
  new URL('../../components/FormButtonConfigPanel.vue', import.meta.url),
  'utf8'
)
const formButtonReferencesSource = readFileSync(
  new URL('../../composables/useFormButtonReferences.js', import.meta.url),
  'utf8'
)
const formButtonFeatureSource = `${formButtonPanelSource}\n${formButtonReferencesSource}`
const formSettingsSource = readFileSync(
  new URL(
    '../../components/form-designer/FormDesignerSettingsDrawer.vue',
    import.meta.url
  ),
  'utf8'
)
const formDesignerSource = readFileSync(
  new URL('../../views/EntityFormDesignByEntity.vue', import.meta.url),
  'utf8'
)
const nodeDesignItemSource = readFileSync(
  new URL('../../components/FormNodeDesignItem.vue', import.meta.url),
  'utf8'
)
const formPreviewSource = readFileSync(
  new URL('../../components/FormPreviewLinkage.vue', import.meta.url),
  'utf8'
)
const entityFormFieldsSource = readFileSync(
  new URL(
    '../../views/entity/components/EntityDataFormFields.vue',
    import.meta.url
  ),
  'utf8'
)
const formActionBarSource = readFileSync(
  new URL('../../components/FormActionBar.vue', import.meta.url),
  'utf8'
)

const advancedBasicSectionStart = formButtonPanelSource.indexOf(
  '<section class="advanced-basic-section"'
)
const conditionDividerStart = formButtonPanelSource.indexOf(
  '<el-divider content-position="left">显示与启用条件</el-divider>'
)
const buttonAppearanceControlStart = formButtonPanelSource.indexOf(
  'v-model="advancedButton.buttonAppearance"'
)
assert.ok(
  advancedBasicSectionStart >= 0
    && buttonAppearanceControlStart > advancedBasicSectionStart
    && buttonAppearanceControlStart < conditionDividerStart,
  '自定义按钮外观必须在更多设置的基础设置区，且位于显示与启用条件之前'
)
assert.match(
  formButtonPanelSource,
  /<el-radio-group\s+v-model="advancedButton\.buttonAppearance"[\s\S]*?<\/el-radio-group>/,
  '按钮外观必须使用单选控件，避免 plain、round、circle 被组合保存'
)
assert.ok(
  formButtonPanelSource.includes("{ label: '默认', value: 'DEFAULT' }")
    && formButtonPanelSource.includes("{ label: '朴素', value: 'PLAIN' }")
    && formButtonPanelSource.includes("{ label: '圆角', value: 'ROUND' }")
    && formButtonPanelSource.includes("{ label: '圆形', value: 'CIRCLE' }"),
  '按钮外观四选一必须具备稳定的枚举值和可读标签'
)
assert.match(
  formActionBarSource,
  /v-bind="appearanceProps\(action\)"/,
  '运行时表单操作栏必须把外观枚举映射为 Element Plus Button 属性'
)
assert.match(
  formActionBarSource,
  /resolveFormButtonAppearanceProps\(/,
  '运行时必须复用共享的互斥外观映射，避免自行组合 plain、round、circle'
)
assert.match(
  formActionBarSource,
  /:aria-label="isCircleAction\(action\) \? action\.label : undefined"/,
  '圆形按钮隐藏文本后必须保留按钮名称作为无障碍标签'
)
assert.match(
  formActionBarSource,
  /<span v-if="!isCircleAction\(action\)">\{\{ action\.label \}\}<\/span>/,
  '圆形按钮必须隐藏可见文本，其他外观继续显示按钮名称'
)
assert.equal(
  (formButtonPanelSource.match(/@click="addCustomButton"/g) || []).length,
  1,
  '自定义按钮只能保留一个统一添加入口'
)
assert.match(
  formButtonPanelSource,
  /<el-button type="primary" :icon="Plus" @click="addCustomButton">\s*添加按钮\s*<\/el-button>/,
  '统一入口必须显示为“添加按钮”'
)
assert.ok(
  !formButtonPanelSource.includes('添加底部按钮')
    && !formButtonPanelSource.includes('添加内嵌按钮'),
  '统一入口不得再按底部按钮和内嵌按钮拆分'
)
assert.match(
  formButtonPanelSource,
  /function addCustomButton\(\)[\s\S]*?placement: 'FOOTER'/,
  '新建按钮必须默认放入底部操作栏'
)
assert.ok(
  formButtonPanelSource.includes('@change="handlePlacementChange(row)"')
    && formButtonPanelSource.includes("row.placement === 'ACTION_SLOT'")
    && formButtonPanelSource.includes('v-model="row.slotKey"'),
  '统一添加后仍须通过位置列切换动作插槽并维护 slotKey'
)

;[
  ':disabled="isKeyLocked(row)"',
  'locallyCreatedButtons.has(button)',
  'isEventConfigurationBlocked(row)',
  'props.eventBindingRevision',
  'persistedButtonKeySet',
  'publishedFormButtonKeys',
  'buttonBindings(button)',
  '@changed="handleEventBindingsChanged"'
].forEach(marker => {
  assert.ok(
    formButtonFeatureSource.includes(marker),
    `按钮面板缺少快捷创建或引用保护: ${marker}`
  )
})
assert.ok(
  formSettingsSource.includes('@changed="handleEventBindingsChanged"')
    && formSettingsSource.includes(':event-binding-revision="eventBindingRevision"')
    && formSettingsSource.includes(':persistence-revision="formActionPersistenceRevision"')
    && formSettingsSource.includes(':persisted-button-keys="persistedFormButtonKeys"')
    && formSettingsSource.includes(':create-action-slot="createActionSlotForButton"'),
  '任一事件入口变更都必须刷新按钮引用缓存和设计器差异'
)
assert.ok(
  formDesignerSource.includes('async function validateFormActionsForPersistence()')
    && formDesignerSource.includes('formActionPersistenceRevision.value += 1')
    && formDesignerSource.includes('rememberPersistedFormButtonKeys()')
    && formDesignerSource.match(/async function handlePublish\(\)[\s\S]*?validateFormActionsForPersistence\(\)/)
    && formDesignerSource.match(/async function handleSave\(\)[\s\S]*?validateFormActionsForPersistence\(\)/),
  '保存和发布必须共用启用按钮与事件绑定校验'
)
assert.ok(
  nodeDesignItemSource.includes('associatedActionButtons')
    && nodeDesignItemSource.includes('尚未关联按钮'),
  'ACTION_SLOT 设计画布必须显示关联按钮或空状态'
)
;[formPreviewSource, entityFormFieldsSource].forEach(source => {
  assert.ok(
    source.includes(':form-action-slots="customFormActionSlots"')
      && source.includes('triggerCustomFormAction'),
    '整页自定义表单运行入口必须传入受控动作插槽契约'
  )
})

console.log('form-actions.spec.js passed')
