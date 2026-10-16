import { createApp, h, ref, shallowRef, onMounted, onBeforeUnmount } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import { registerApplicationExtensions } from '/src/extensions/register'
import { useUserStore } from '/src/stores/user'

const mode = new URLSearchParams(location.search).get('view') || 'list'
const field = { id: 'amount-field', fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL', showInList: true, isQuery: true }
let component, path, props
if (mode === 'list') { component = (await import('/src/views/EntityListConfigDesign.vue')).default; path = '/list/:id'; props = {} }
if (mode === 'entity') { component = (await import('/src/views/EntityDesign.vue')).default; path = '/entity/:id'; props = {} }
if (mode === 'form') { component = (await import('/src/views/EntityFormDesignByEntity.vue')).default; path = '/form/:id'; props = {} }
if (mode === 'entities') { component = (await import('/src/views/EntityList.vue')).default; path = '/entities'; props = {} }
if (mode === 'data') { component = (await import('/src/views/entity/EntityDataList.vue')).default; path = '/data'; props = { entityCode: 'test', listKey: 'main' } }
if (mode === 'related') {
  const Related = (await import('/src/components/related-content/RelatedContentConfigDialog.vue')).default
  component = { setup() { const dialog = ref(); return () => h('main', [h('button', { onClick: () => dialog.value.open(null, { extension: true }) }, '打开关联内容'), h(Related, { ref: dialog, ownerType: 'FORM', ownerId: 'form-test', sourceEntity: { id: 'entity-test', entityCode: 'test', entityName: '测试实体' }, sourceFields: [field] })]) } }
  path = '/related'
}
if (mode === 'events') {
  const Events = (await import('/src/components/ui-config/EventBindingEditor.vue')).default
  component = { setup() { return () => h(Events, { ownerType: 'FORM', ownerId: 'form-test', targetType: 'OWNER', targetKey: '', allowedEvents: ['ON_LOAD'], fieldOptions: [{ label: '金额', value: 'amount' }] }) } }
  path = '/events'
}
if (mode === 'node') {
  const Modeler = (await import('bpmn-js/lib/Modeler')).default
  const moddle = (await import('/src/assets/flowable.json')).default
  const Panel = (await import('/src/components/NodeConfigPanel.vue')).default
  const { updateBpmnExtensionProperty } = await import('/src/components/node-config/bpmnExtensionProperties.js')
  component = { setup() {
    const canvas = ref(), element = shallowRef(), value = ref('before'); let modeler
    const read = () => { value.value = element.value.businessObject.extensionElements?.values?.find(x => x.$type === 'flowable:Properties')?.values?.find(x => x.name === 'test')?.value || '' }
    onMounted(async () => {
      modeler = new Modeler({ container: canvas.value, moddleExtensions: { flowable: moddle } })
      await modeler.createDiagram()
      const shape = modeler.get('elementFactory').createShape({ type: 'bpmn:UserTask' })
      modeler.get('modeling').createShape(shape, { x: 300, y: 200 }, modeler.get('canvas').getRootElement())
      modeler.get('modeling').updateProperties(shape, { name: '审批节点', 'flowable:assignee': 'fixture' })
      shape._modeler = modeler; element.value = shape
      updateBpmnExtensionProperty(shape, modeler.get('moddle'), modeler.get('modeling'), 'test', 'before')
      read()
    })
    onBeforeUnmount(() => modeler?.destroy())
    return () => h('main', [h('div', { ref: canvas, style: 'height:220px' }),
      h('button', { onClick: () => { updateBpmnExtensionProperty(element.value, modeler.get('moddle'), modeler.get('modeling'), 'test', 'after'); read() } }, '测试扩展更新'),
      h('button', { onClick: () => { modeler.get('commandStack').undo(); read() } }, '撤销'),
      h('button', { onClick: () => { modeler.get('commandStack').redo(); read() } }, '重做'),
      h('output', { 'data-testid': 'extension-value' }, value.value),
      element.value && h(Panel, { element: element.value, processId: 'process-test' })])
  } }
  path = '/node'
}
const router = createRouter({ history: createMemoryHistory(), routes: [{ path, component, props }] })
const app = createApp({ render: () => h(RouterView) }), pinia = createPinia()
registerApplicationExtensions({ enableDemo: true })
for (const [key, icon] of Object.entries(icons)) app.component(key, icon)
app.use(pinia).use(router).use(ElementPlus, { locale: zhCn })
const user = useUserStore()
user.applySession({ token: 'test-only', tokenExpiresAt: new Date(Date.now() + 3600000).toISOString(), username: 'fixture', nickname: '测试用户', roles: [] })
user.setPermissions(['*'])
await router.push(path.replace(':id', mode === 'list' ? 'list-test' : mode === 'form' ? 'form-test' : 'entity-test') + (mode === 'form' ? '?entityId=entity-test' : ''))
await router.isReady(); app.mount('#app')
