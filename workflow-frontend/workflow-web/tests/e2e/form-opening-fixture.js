import { createApp, h, ref, provide } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { registerApplicationExtensions } from '/src/extensions/register'
import Dialog from '/src/views/entity/components/EntityDataFormDialog.vue'
import List from '/src/views/entity/EntityDataList.vue'
import { getFormForNewData } from '/src/api/entityFormResolve.js'
import { useUserStore } from '/src/stores/user'
import { workspacePageKey } from '/src/shared/workspace-tabs.js'
const fields = [
  { id: 'amount', fieldId: 'amount', fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL', componentType: 'number', isReadonly: 0 },
  { id: 'category', fieldId: 'category', fieldCode: 'category', fieldName: '分类', fieldType: 'STRING', componentType: 'select', dictType: 'kinds' }
]
const entity = { id: 'entity-test', entityCode: 'test', entityName: '测试实体', storageMode: 'DYNAMIC', lifecycleMode: 'INDEPENDENT', fields }
const workspace = { active: ref(true), registerGuard: () => () => {} }
const app = createApp({ setup() {
  const dialog = ref(), refreshed = ref(0)
  if (new URLSearchParams(location.search).has('workspace')) provide(workspacePageKey, workspace)
  const open = (mode, id, options = {}) => mode === 'create'
    ? dialog.value.openCreate({ loadForm: () => getFormForNewData('test'), ...options })
    : dialog.value.openEdit({ id }, { loadForm: () => getFormForNewData('test'), ...options })
  window.openingTest = { open, workspace, refreshed, confirmDiscard: () => dialog.value.confirmDiscardChanges() }
  return () => h('main', { style: 'padding:24px' }, [
    h('button', { onClick: () => open('create') }, '打开新增'), h('button', { onClick: () => open('edit', 'a') }, '打开编辑'),
    new URLSearchParams(location.search).has('list') ? h(List, { entityCode: 'test', listKey: 'tests' }) : null,
    h(Dialog, { ref: dialog, defaultForm: null, entityCode: 'test', entityDefinition: entity, entityFields: fields, onSuccess: () => refreshed.value++ })
  ])
} })
const pinia = createPinia(); registerApplicationExtensions({ enableDemo: true })
app.use(pinia).use(ElementPlus).use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] }))
const user = useUserStore(pinia); user.applySession({ token: 'test-only', tokenExpiresAt: new Date(Date.now() + 3600000).toISOString(), username: 'fixture', roles: [] }); user.setPermissions(['*'])
app.mount('#app')
