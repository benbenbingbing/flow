<template>
  <RelatedContentRuntime v-if="state.kind === 'related'" :key="sequence" ref="relatedRef"
    :composition="state.composition" owner-type="FORM" :owner-id="state.owner.id"
    :config-hash="state.owner.configHash" :runtime-context="{ ...state.context, form: state.owner }" :source-record-id="state.context.recordId"
    :source-data="state.context.formData" :source-parameters="state.context.parameters || {}" :mode="state.context.mode"
    :host-readonly="state.context.mode === 'view'" :composition-context="state.context.compositionContext" :show-trigger="false" />
  <EntityListLauncher v-if="state.kind === 'list'" :key="sequence" ref="listRef" :entity-code="state.config.entityCode" :list-key="state.config.listKey"
    :presentation="state.config.presentation || 'DIALOG'" :context="{sourceFormData: state.context.formData, sourceRecordId: state.context.recordId}" :title="state.label">
    <template #default></template>
  </EntityListLauncher>
  <EntityDataFormDialog v-if="state.kind === 'form' && state.config.formMode !== 'view'" :key="sequence" ref="formRef"
    :entity-code="state.config.entityCode" :entity-definition="state.entity" :entity-fields="state.entity.fields || []" :default-form="state.form" />
  <EntityApprovalDialog v-if="state.kind === 'form' && state.config.formMode === 'view'" :key="sequence" ref="viewRef"
    :entity-code="state.config.entityCode" :entity-definition="state.entity" :entity-fields="state.entity.fields || []" :default-form="state.form" />
  <el-dialog v-model="componentVisible" :title="state.label" width="80%" append-to-body destroy-on-close>
    <component ref="componentRef" v-if="state.kind === 'component' && componentVisible" :is="state.component"
      :form="state.owner" v-model="componentDraft" :entity-code="state.context.entityCode"
      :fields="state.owner.fields || []" :entity-fields="state.context.entityFields || []"
      :entity-definition="state.context.entityDefinition" :config="{}" :linkage-state="componentLinkageState"
      :mode="state.context.mode" :readonly="state.context.mode === 'view'" :context="state.context" @close="componentVisible = false" />
    <template #footer>
      <el-button @click="componentVisible = false">关闭</el-button>
      <el-button v-if="state.context?.mode !== 'view'" type="primary" @click="applyComponentDraft">应用到当前表单</el-button>
    </template>
  </el-dialog>
</template>
<script setup>
import { defineAsyncComponent, markRaw, nextTick, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import { entityApi } from '@/api/entity'
import { getFormRuntimeRelease } from '@/api/entityForm'
import { normalizeRuntimeFormRelease } from '@flow/workflow-core/form-runtime/release'
import { relatedContentIsVisible } from '@/shared/related-content-presentation'
import { getListRowAction, getListToolbarAction } from '@/extensions/core/registries/listActionRegistry'
import { getCustomFormComponent } from '@/extensions/core/registries/customComponentRegistry'
// 异步加载避免表单宿主与按钮打开的新表单形成静态循环依赖。
const EntityDataFormDialog = defineAsyncComponent(() => import('@/views/entity/components/EntityDataFormDialog.vue'))
const EntityApprovalDialog = defineAsyncComponent(() => import('@/views/entity/components/approval/EntityApprovalDialog.vue'))
const EntityListLauncher = defineAsyncComponent(() => import('./EntityListLauncher.vue'))
const RelatedContentRuntime = defineAsyncComponent(() => import('./related-content/RelatedContentRuntime.vue'))
const state = shallowRef({}), sequence = ref(0), componentVisible = ref(false)
const componentRef = ref(), componentDraft = ref({})
const componentLinkageState = { visibility: {}, disabled: {}, required: {}, options: {}, values: {} }

/** 弹窗编辑使用独立草稿，只有明确应用后回填主表；关闭或来源变化均不丢失主表输入。 */
async function applyComponentDraft() {
  const current = state.value
  if (current.kind !== 'component' || current.context.mode === 'view') return
  try {
    if (await componentRef.value?.validate?.() === false) return
    if (state.value !== current || (current.context.isCurrent && !current.context.isCurrent())) return
    current.context.updateFormData?.(JSON.parse(JSON.stringify(componentDraft.value)))
    componentVisible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '组件校验失败，请检查输入')
  }
}
const relatedRef = ref(), listRef = ref(), formRef = ref(), viewRef = ref()

/** 等待异步宿主挂载；超时或来源表单已关闭时不继续打开旧记录。 */
async function mounted(target, context) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (context.isCurrent && !context.isCurrent()) throw new Error('来源表单已关闭')
    await nextTick()
    if (target.value) return target.value
    await new Promise(resolve => setTimeout(resolve, 20))
  }
  throw new Error('目标页面尚未加载完成，请重试')
}

/** 只消费动作运行时重新鉴权后的配置；目标页面仍使用自己的详情、保存及发布接口。 */
async function execute(action, owner, context) {
  const config = action.execution || {}
  if (action.customMode === 'handler') {
    const handler = getListRowAction(config.name) || getListToolbarAction(config.name)
    if (!handler) throw new Error(`未注册动作函数：${config.name}`)
    return handler({ ...context, row: { ...context.formData, id: context.recordId }, selectedRows: context.recordId ? [{ ...context.formData, id: context.recordId }] : [], config: action })
  }
  let next = { config, context, owner, label: action.label }
  let recordId = context.recordId
  if (action.customMode === 'open-related-content') {
    const composition = (owner.viewCompositions || []).find(item => item.compositionKey === config.compositionKey)
    if (!composition || composition.config?.enabled === false || !relatedContentIsVisible(composition.config?.presentation, context.formData, context.mode)) throw new Error('该关联内容当前不可用')
    if (!['DIALOG', 'DRAWER', 'PAGE'].includes(composition.config?.presentation?.position)) throw new Error('请将关联内容设置为弹窗、抽屉或全屏打开')
    if (!recordId) throw new Error('请先保存主表记录，再打开关联内容')
    next = { ...next, kind: 'related', composition }
  } else if (action.customMode === 'open-list') {
    next.kind = 'list'
  } else if (action.customMode === 'open-form') {
    const mode = config.formMode || 'create'
    if (mode !== 'create') {
      const value = config.recordIdField ? context.formData?.[config.recordIdField] : context.recordId
      recordId = value && typeof value === 'object' ? value.id : value
      if (!recordId || (!config.recordIdField && config.entityCode !== context.entityCode)) throw new Error('请选择目标记录 ID 来源，或先保存当前记录')
    }
    const release = await getFormRuntimeRelease(config.formId)
    const form = normalizeRuntimeFormRelease(release, config.formId, release.configHash)
    const entity = await entityApi.getByCode(config.entityCode)
    if (String(form.entityId) !== String(entity.id)) throw new Error('目标表单与实体不匹配，请重新配置')
    next = { ...next, kind: 'form', form, entity, config: { ...config, formMode: mode } }
  } else if (action.customMode === 'component') {
    const component = getCustomFormComponent(config.name, config.componentVersion || undefined, config.artifactDigest || undefined)
    if (!component) throw new Error(`未注册表单组件：${config.name}`)
    next = { ...next, kind: 'component', component: markRaw(component) }
  } else throw new Error('不支持的按钮执行方式')
  if (context.isCurrent && !context.isCurrent()) return
  state.value = {}; await nextTick()
  sequence.value++; state.value = next
  if (next.kind === 'related') await (await mounted(relatedRef, context)).open()
  if (next.kind === 'list') await (await mounted(listRef, context)).open()
  if (next.kind === 'form') {
    if (next.config.formMode === 'view') await (await mounted(viewRef, context)).openView({ id: recordId, entityCode: config.entityCode }, { form: next.form })
    else if (next.config.formMode === 'edit') await (await mounted(formRef, context)).openEdit({ id: recordId }, { form: next.form })
    else await (await mounted(formRef, context)).openCreate({ form: next.form })
  }
  if (next.kind === 'component') {
    componentDraft.value = JSON.parse(JSON.stringify(context.formData || {}))
    componentVisible.value = true
  }
}
defineExpose({ execute })
</script>
