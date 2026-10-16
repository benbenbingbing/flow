import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { renderToString } from '@vue/server-renderer'
import { parse, compileTemplate } from '@vue/compiler-sfc'
import { extractSfcFunctions } from '../../../scripts/test-sfc-functions.mjs'
import { hasCustomFormComponent, hasCustomListComponent, registerCustomFormComponent } from '../../extensions/core/registries/customComponentRegistry.js'
import { getMobileExtension } from '../../../../packages/workflow-mobile-ui/src/fields/registry.js'

const entityForm = new URL('../../views/entity/components/EntityDataFormFields.vue', import.meta.url)
const previewForm = new URL('../FormPreviewLinkage.vue', import.meta.url)
const entityList = new URL('../../views/entity/EntityDataList.vue', import.meta.url)
const mobileForm = new URL('../../../../packages/workflow-mobile-ui/src/form/MobileFormRenderer.vue', import.meta.url)
const { computed, ref, reactive } = vue
const valid = async () => ({ valid: true })

/** 渲染真实模板；视觉组件替换为轻量输出，仍执行模板的分支和用户可见错误内容。 */
async function renderTemplate(file, state) {
  const descriptor = parse(readFileSync(file, 'utf8')).descriptor
  const result = compileTemplate({ source: descriptor.template.content, id: 'missing-extension', filename: String(file) })
  assert.deepEqual(result.errors, [])
  const components = Object.fromEntries([...result.code.matchAll(/_resolveComponent\("([^"]+)"\)/g)].map(([, name]) => [name, {
    props: ['title', 'description'],
    setup(props, { slots }) { return () => vue.h('div', { 'data-component': name }, [props.title, props.description, slots.default?.()]) }
  }]))
  const code = result.code.replace(/^import \{([^}]+)\} from "vue"\n/gm, (_, bindings) => `const {${bindings.replace(/ as /g, ': ')}} = Vue\n`).replace('export function render', 'function render')
  const render = new Function('Vue', `${code}\nreturn render`)(vue)
  return renderToString(vue.createSSRApp({ components, setup: () => state, render }))
}

function formState(file, form) {
  const scope = {
    computed, hasCustomFormComponent, props: reactive({ form, defaultForm: form, skipDataSourcePrevalidation: true }),
    customCrossValidation: { validate: valid }, customUniquePrecheckController: { checkAll: valid },
    runtimeFormFields: ref([]), formData: ref({ data: {} }), customUniqueErrors: ref({}),
    customFormRef: ref(null), formRef: ref(null), previewRef: ref(null), nodeFormRef: ref(null), hasNodeTree: ref(false),
    crossValidation: { validate: valid }, uniquePrecheckController: { checkAll: valid }, uniquePrecheckFields: ref([]),
    customValidation: { validate: valid }, revealCrossFieldError: async () => {}, ElMessage: { error() {} }
  }
  const names = ['missingCustomForm', 'missingCustomFormMessage', 'validate']
  if (file === entityForm) names.push('showCustomForm', 'hasConfiguredForm')
  return { ...scope, ...extractSfcFunctions(file, names, scope) }
}

for (const [label, file] of [['实体编辑', entityForm], ['预览/审批', previewForm]]) {
  test(`${label}遇到未装配的项目 FORM 显式报错并阻止提交，不显示标准表单`, async () => {
    const form = { customComponent: 'ProjectMemberChangeForm', customComponentVersion: 1, fields: [{ fieldCode: 'name' }], nodes: [{ nodeType: 'FIELD' }] }
    const state = formState(file, form)
    assert.equal(state.missingCustomForm.value, true)
    assert.equal(await state.validate(), false)
    const html = await renderTemplate(file, {
      ...state, form, defaultForm: form, customSubmissionError: '', showStartProcess: false,
      previewStyle: {}, showHeader: false, ownerRelatedContents: [], tabRelatedContents: []
    })
    assert.match(html, /ProjectMemberChangeForm 未安装/)
    assert.doesNotMatch(html, /data-component="(?:FormNodeRenderer|FormPreviewLinkage|el-form)"/)
  })

  test(`${label}普通表单不受影响，已安装扩展仍执行自己的校验`, async () => {
    const ordinary = formState(file, {})
    assert.equal(ordinary.missingCustomForm.value, false)
    assert.equal(await ordinary.validate(), true)
    const name = `available-form-${label}`
    registerCustomFormComponent(name, { render: () => null }, { version: 1 })
    const available = formState(file, { customComponent: name, customComponentVersion: 1 })
    let validationCalls = 0
    available.customFormRef.value = { validate: async () => { validationCalls++; return false } }
    assert.equal(available.missingCustomForm.value, false)
    assert.equal(await available.validate(), false)
    assert.equal(validationCalls, 1)
    available.props.form.customComponentVersion = 99
    assert.equal(available.missingCustomForm.value, true, '未安装的指定版本也必须阻断，不能回退其他版本')
  })
}

test('未装配的项目 LIST 渲染错误页，不静默降级为标准列表和操作栏', async () => {
  const scope = { computed, hasCustomListComponent, listConfig: ref({ customComponent: 'PROJECT_CUSTOM_LIST', customComponentVersion: 1 }) }
  const state = extractSfcFunctions(entityList, ['customListComponent', 'missingCustomList', 'missingCustomListMessage', 'usesCustomListComponent'], scope)
  assert.equal(state.missingCustomList.value, true)
  assert.equal(state.usesCustomListComponent.value, false)
  const html = await renderTemplate(entityList, { ...state, loading: false, loadError: '', entityCode: 'project', entityDefinition: { id: 'project-definition' }, embedded: false, loadDataList() {}, entityFields: [], runtimeDefaultForm: {}, listConfig: scope.listConfig, configHash: '', entityStatusOptions: [], embeddedFormPresentation: '', versionCapabilities: {} })
  assert.match(html, /PROJECT_CUSTOM_LIST 未安装/)
  assert.doesNotMatch(html, /data-component="(?:EntityDataSearchForm|EntityDataTable|EntityDataToolbar)"/)
  scope.listConfig.value = {}
  assert.equal(state.missingCustomList.value, false)
})

for (const [label, file] of [['平铺表单', previewForm], ['实体标准表单', entityForm]]) {
  test(`${label}提交校验会收集缺失自定义 FIELD 的失败结果`, async () => {
    const field = { fieldCode: 'score', fieldName: '项目评分', componentType: 'uninstalled-project-score' }
    let checked = 0
    const renderer = { validate: async () => { checked++; return false } }
    const scope = {
      getFieldKey: value => value.fieldCode, buildRuntimeFieldRules: () => [], isFieldRequired: () => false,
      appendFormUniqueBlurRule() {}, uniquePrecheckContext: {}, isFieldDisabled: () => false, isFieldVisible: () => true,
      linkageState: ref({ required: {}, attachmentItemRequired: {} }),
      fieldRendererRefs: file === previewForm ? ref({ score: renderer }) : new Map([['score', renderer]])
    }
    const { getFieldRules } = extractSfcFunctions(file, ['getFieldRules'], scope)
    const rule = getFieldRules(field).find(item => item.trigger === 'submit')
    assert.ok(rule)
    const error = await new Promise(resolve => rule.validator({}, 80, resolve))
    assert.match(error.message, /组件不可用/)
    assert.equal(checked, 1)
  })
}

test('手机 FORM 缺失时保留具体扩展身份并阻止提交', async () => {
  const scope = {
    computed, getMobileExtension, props: { form: { customComponent: 'ProjectMemberChangeForm', customComponentVersion: 1 } },
    loading: ref(false), loadError: ref(''), modelError: ref('')
  }
  const state = extractSfcFunctions(mobileForm, ['customForm', 'customFormUnavailableMessage', 'validate'], scope)
  assert.equal(await state.validate(), false)
  assert.match(scope.loadError.value, /ProjectMemberChangeForm v1 未安装/)
})
