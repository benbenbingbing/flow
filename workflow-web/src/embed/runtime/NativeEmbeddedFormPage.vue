<template>
  <section class="native-embedded-form" :aria-busy="loading">
    <div v-if="loading" class="native-embedded-form__state" role="status">
      <span class="native-embedded-form__spinner" aria-hidden="true" />
      <p>正在打开 Flow 表单…</p>
    </div>

    <div v-else-if="error" class="native-embedded-form__state" role="alert">
      <p>{{ error }}</p>
      <el-button type="primary" @click="reload">重试</el-button>
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
      @closed="handleClosed"
    />
  </section>
</template>

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
async function reload() {
  const sequence = ++loadSequence
  closeRequested = false
  saveSucceeded = false
  loading.value = true
  error.value = ''
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
    if (sequence !== loadSequence) return
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
    loading.value = false
    await nextTick()
    if (sequence !== loadSequence) return

    if (mode.value === 'CREATE') {
      await dataFormDialogRef.value?.openCreate({
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
      return
    }
    const opened = await approvalDialogRef.value?.openView({
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
  } catch (cause) {
    if (sequence !== loadSequence) return
    console.error('加载 Flow 原生嵌入表单失败:', cause)
    loading.value = false
    error.value = cause?.message || 'Flow 表单暂时无法加载，请稍后重试'
  }
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
onMounted(reload)
onBeforeUnmount(() => {
  // 使在途原生 API 响应失效，避免列表返回/Session 销毁后重新打开已卸载 Dialog。
  loadSequence += 1
  closeRequested = true
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
