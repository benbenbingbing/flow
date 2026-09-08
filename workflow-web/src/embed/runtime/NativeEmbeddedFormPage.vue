<template>
  <section class="native-embedded-form" :aria-busy="loading">
    <div v-if="loading" class="native-embedded-form__state" role="status">
      <span class="native-embedded-form__spinner" aria-hidden="true" />
      <p>正在打开 Flow 表单…</p>
    </div>

    <div v-else-if="error" class="native-embedded-form__state" role="alert">
      <p>{{ error }}</p>
      <el-button type="primary" @click="retryLoad">重试</el-button>
    </div>

    <!--
      这里刻意直接挂载 Flow 管理端使用的两个原生 Dialog，而不是在 Embed 中
      逐个复刻字段。日期、下拉、弹窗以及以后注册的新组件都会自动走同一 registry。
    -->
    <EntityDataFormDialog
      v-if="mode === 'CREATE' && runtimeReady"
      ref="dataFormDialogRef"
      :entity-code="target.entityCode"
      :entity-definition="entityDefinition"
      :entity-fields="entityFields"
      :default-form="runtimeForm"
      :list-key="target.listKey || undefined"
      :list-release-id="target.listReleaseId || undefined"
      :list-release-version="target.listReleaseVersion"
      :list-release-resolution-token="target.listReleaseResolutionToken || undefined"
      :entity-status-options="entityStatusOptions"
      :submit-transport="submitNativeRecord"
      :reload-pending="loading"
      :form-presentation="formPresentation"
      @success="handleSuccess"
      @closed="handleClosed"
    />

    <EntityApprovalDialog
      v-else-if="mode === 'VIEW' && runtimeReady"
      ref="approvalDialogRef"
      :entity-code="target.entityCode"
      :entity-definition="entityDefinition"
      :entity-fields="entityFields"
      :default-form="runtimeForm"
      :list-key="target.listKey || undefined"
      :list-release-id="target.listReleaseId || undefined"
      :list-release-version="target.listReleaseVersion"
      :list-release-resolution-token="target.listReleaseResolutionToken || undefined"
      :entity-status-options="entityStatusOptions"
      :form-presentation="formPresentation"
      @closed="handleClosed"
    />
  </section>
</template>

<script>
// 多个表单实例可能在同一渲染周期交替挂载；按节点计数，旧实例的收尾不能解锁新实例。
const nativeFormReloadLocks = new WeakMap()
</script>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { entityApi } from '@/api/entity'
import { getFormRuntimeRelease } from '@/api/entityForm'
import { getEntityStatusList } from '@/api/entityStatus'
import { normalizeRuntimeFormRelease } from '@/shared/list-button-form-runtime'
import { getEffectiveEntityStatusOptions } from '@/shared/entity-status-runtime'
import EntityDataFormDialog from '@/views/entity/components/EntityDataFormDialog.vue'
import EntityApprovalDialog from '@/views/entity/components/approval/EntityApprovalDialog.vue'

const props = defineProps({
  bootstrap: { type: Object, required: true },
  target: { type: Object, required: true },
  controller: { type: Object, required: true },
  canBack: { type: Boolean, default: false }
})

const target = props.target
const mode = computed(() => String(
  target.mode || props.bootstrap.view?.entryMode || ''
).toUpperCase())
const formPresentation = computed(() =>
  props.bootstrap.ui?.formPresentation === 'dialog' ? 'dialog' : 'seamless'
)
const loading = ref(true)
const error = ref('')
const entityDefinition = ref({})
const entityFields = ref([])
const runtimeForm = ref(null)
const entityStatusOptions = ref(getEffectiveEntityStatusOptions())
const dataFormDialogRef = ref(null)
const approvalDialogRef = ref(null)
const runtimeReady = computed(() => Boolean(
  runtimeForm.value && entityDefinition.value?.id
))
let loadSequence = 0
let closeRequested = false
let saveSucceeded = false
let pendingReload = null
let releaseReloadInteraction = null

/**
 * 暂停加载开始时已有的 iframe 内容，包括 Teleport 到 body 的下拉/日期弹层。
 * 只锁已有节点，让 FORM_OPEN 新创建的业务确认框仍可操作；返回幂等恢复函数。
 */
function lockReloadInteraction() {
  const elements = Array.from(globalThis.document?.body?.children || [])
  for (const element of elements) {
    const lock = nativeFormReloadLocks.get(element) || { count: 0, inert: Boolean(element.inert) }
    lock.count += 1
    nativeFormReloadLocks.set(element, lock)
    element.inert = true
  }
  let released = false
  return () => {
    if (released) return
    released = true
    for (const element of elements) {
      const lock = nativeFormReloadLocks.get(element)
      if (!lock || --lock.count > 0) continue
      element.inert = lock.inert
      nativeFormReloadLocks.delete(element)
    }
  }
}

async function loadEntityStatuses(entityCode, runtimeContext = {}) {
  // 状态属于原生表单定义的一部分；委托策略已明确开放该 API，读取失败时必须
  // 整体失败并展示重试，不能悄悄退回默认状态造成与 Flow 主界面不一致。
  return getEffectiveEntityStatusOptions(
    await getEntityStatusList(entityCode, runtimeContext)
  )
}

/**
 * 按 Bootstrap 固定坐标读取 Flow 自己的发布态 URL，然后调用原生 Dialog。
 * 不解析字段类型、不选择组件，也不消费 Embed 的投影表单端点。
 */
async function loadRuntime() {
  const sequence = ++loadSequence
  // 先取得丢弃确认，再修改加载状态和目标，取消刷新时保留整个原生表单。
  if (dataFormDialogRef.value
    && !(await dataFormDialogRef.value.confirmDiscardChanges())) return false
  // 用户确认期间 Session 可能已销毁或已关闭表单，不能让确认续体复活旧组件。
  if (sequence !== loadSequence || closeRequested) return false
  closeRequested = false
  saveSucceeded = false
  loading.value = true
  error.value = ''
  const releaseInteraction = lockReloadInteraction()
  releaseReloadInteraction = releaseInteraction
  try {
    const [entity, release, statuses] = await Promise.all([
      entityApi.getByCode(target.entityCode, target.runtimeContext),
      getFormRuntimeRelease(
        target.formId,
        target.formReleaseId,
        target.formReleaseVersion,
        target.formReleaseResolutionToken || undefined
      ),
      loadEntityStatuses(
        target.entityCode,
        target.runtimeContext
      )
    ])
    if (sequence !== loadSequence) return false
    entityDefinition.value = entity || {}
    entityFields.value = Array.isArray(entity?.fields) ? entity.fields : []
    entityStatusOptions.value = statuses
    runtimeForm.value = normalizeRuntimeFormRelease(
      release,
      target.formId,
      release?.releaseResolutionToken
        || target.formReleaseResolutionToken
        || null
    )
    await nextTick()
    if (sequence !== loadSequence) return false

    const dialog = mode.value === 'CREATE' ? dataFormDialogRef.value : approvalDialogRef.value
    if (!dialog) {
      throw Object.assign(new Error('Flow 表单尚未完成加载，请稍后重试'), {
        errorCode: 'EMBED_RUNTIME_UNAVAILABLE', status: 503
      })
    }
    if (mode.value === 'CREATE') {
      await dialog.openCreate({
        form: runtimeForm.value,
        initialData: target.initialData,
        parameters: target.parameters,
        context: {
          ...target.runtimeContext,
          params: target.parameters,
          parameters: target.parameters,
          releaseResolutionToken:
            runtimeForm.value.releaseResolutionToken || undefined
        }
      })
      if (sequence !== loadSequence) return false
      loading.value = false
      return true
    }
    const opened = await dialog.openView({
      id: target.recordId,
      entityCode: target.entityCode,
      processInstanceId: target.processInstanceId || undefined,
      name: props.bootstrap.view?.name || '数据详情'
    }, {
      form: runtimeForm.value,
      context: target.runtimeContext
    })
    if (opened === false) {
      throw new Error('Flow 记录详情暂时无法加载，请稍后重试')
    }
    if (sequence !== loadSequence) return false
    loading.value = false
    return true
  } catch (cause) {
    if (sequence !== loadSequence) return false
    console.error('加载 Flow 原生嵌入表单失败:', cause)
    loading.value = false
    error.value = cause?.message || 'Flow 表单暂时无法加载，请稍后重试'
    // 宿主 refresh 必须收到真实失败，不能因局部错误页已经显示就返回成功 ACK。
    throw cause
  } finally {
    releaseInteraction()
    if (releaseReloadInteraction === releaseInteraction) releaseReloadInteraction = null
  }
}

/** 合并同时到达的刷新，避免重复确认以及晚返回的请求覆盖新输入。 */
function reload() {
  if (!pendingReload) {
    pendingReload = loadRuntime().finally(() => { pendingReload = null })
  }
  return pendingReload
}

function retryLoad() {
  // 页面首次加载/手工重试由局部错误态承接；宿主调用 reload 保留拒绝语义。
  return reload().catch(() => {})
}

function submitNativeRecord(submission) {
  return props.controller.submitNativeRecord(
    submission.data,
    submission.actionKey
  )
}

function handleSuccess() {
  // form.saved 由 controller 在服务端返回受控 receipt 后发送；这里不伪造收据。
  saveSucceeded = true
}

function handleClosed() {
  if (closeRequested) return
  closeRequested = true
  loadSequence += 1
  if (props.canBack) {
    const shouldRefreshList = saveSucceeded
    props.controller.backToList()
    if (shouldRefreshList) props.controller.refreshList()
    return
  }
  props.controller.requestClose('native-flow-form-closed')
}

function focus() {
  const control = globalThis.document?.querySelector?.(
    '.entity-form-dialog button:not([disabled]), '
      + '.entity-form-dialog input:not([disabled]), '
      + '.entity-form-dialog [tabindex="0"]'
  )
  control?.focus?.()
}

defineExpose({ focus, reload })
onMounted(retryLoad)
onBeforeUnmount(() => {
  // 使在途原生 API 响应失效，避免列表返回/Session 销毁后重新打开已卸载 Dialog。
  loadSequence += 1
  closeRequested = true
  releaseReloadInteraction?.()
  releaseReloadInteraction = null
})
</script>

<style scoped>
.native-embedded-form {
  min-height: 100vh;
  background: var(--el-bg-color, #fff);
}

.native-embedded-form__state {
  display: grid;
  min-height: 320px;
  place-content: center;
  justify-items: center;
  gap: 16px;
  padding: 32px;
  color: var(--el-text-color-regular, #475467);
}

.native-embedded-form__spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--el-border-color-lighter, #eaecf0);
  border-top-color: var(--el-color-primary, #2563eb);
  border-radius: 50%;
  animation: native-embed-spin .8s linear infinite;
}

@keyframes native-embed-spin {
  to { transform: rotate(360deg); }
}
</style>
