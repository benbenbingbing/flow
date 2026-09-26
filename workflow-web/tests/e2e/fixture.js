import { createApp, h, ref } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import { registerApplicationExtensions } from '/src/extensions/register'
import { useUserStore } from '/src/stores/user'
import Home from '/src/views/Home.vue'
import Progress from '/src/views/ProcessProgress.vue'
import EditDialog from '/src/views/entity/components/EntityDataFormDialog.vue'
import ApprovalDialog from '/src/views/entity/components/approval/EntityApprovalDialog.vue'

// 浏览器测试挂载生产页面/弹窗及真实控件；所有 API 由 Playwright 拦截。
export const field = { id: 'amount-field', fieldId: 'amount-field', fieldCode: 'amount', fieldLabel: '金额', fieldName: '金额', fieldType: 'DECIMAL', componentType: 'number', isReadonly: 0 }
export const form = { id: 'form-test', formKey: 'test', formName: '测试表单', runtimeReleaseId: 'release-pinned', runtimeReleaseVersion: 2, releaseResolutionToken: 'test-signed-token', fields: [field], nodes: [{ id: 'amount-node', nodeKey: 'amount', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'amount', props: field }] }
const entity = { id: 'entity-test', entityCode: 'test', entityName: '测试实体', storageMode: 'DYNAMIC', lifecycleMode: 'WORKFLOW', fields: [field] }
const Dialogs = { setup() {
  const edit = ref(), approval = ref(), refreshed = ref(0)
  const props = { entityCode: 'test', entityDefinition: entity, entityFields: [field], defaultForm: form, onSuccess: () => refreshed.value++ }
  return () => h('main', { style: 'padding:24px' }, [
    h('h1', 'PC 表单与审批页面回归'),
    h('button', { onClick: () => edit.value.openEdit({ id: 'record-test' }) }, '打开编辑'),
    h('button', { onClick: () => approval.value.openApprove({ id: 'record-test', taskId: 'task-test', processInstanceId: 'instance-test', entityCode: 'test' }) }, '打开审批'),
    h('output', { 'data-testid': 'refresh-count' }, String(refreshed.value)),
    h(EditDialog, { ...props, ref: edit }), h(ApprovalDialog, { ...props, ref: approval })
  ])
} }
const router = createRouter({ history: createMemoryHistory(), routes: [
  { path: '/home', component: Home }, { path: '/dialogs', component: Dialogs },
  { path: '/process/:instanceId', component: Progress }
] })
const app = createApp({ render: () => h(RouterView) }), pinia = createPinia()
registerApplicationExtensions({ enableDemo: true })
for (const [key, icon] of Object.entries(icons)) app.component(key, icon)
app.use(pinia).use(router).use(ElementPlus, { locale: zhCn })
const user = useUserStore()
user.applySession({ token: 'test-only', tokenExpiresAt: new Date(Date.now() + 3600000).toISOString(), username: 'fixture', nickname: '测试用户', roles: [] })
user.setPermissions(['*'])
await router.push(new URLSearchParams(location.search).get('view') === 'progress' ? '/process/instance-test' : `/${new URLSearchParams(location.search).get('view') || 'dialogs'}`)
await router.isReady()
app.mount('#app')
