<template>
  <main
    ref="shellElement"
    class="embed-shell"
    :class="`embed-shell--${runtime.theme}`"
    :lang="runtime.locale"
    :data-state="runtime.state"
  >
    <section
      v-if="isLoading"
      class="embed-shell__loading"
      role="status"
      aria-live="polite"
    >
      <span class="embed-shell__spinner" aria-hidden="true" />
      <p>{{ loadingText }}</p>
    </section>

    <EmbedListRuntime
      v-else-if="runtime.state === EMBED_RUNTIME_STATES.READY
        && runtime.navigation.surfaceType === 'LIST'"
      :bootstrap="runtime.bootstrap"
      :schema="runtime.schema"
      :page="runtime.page"
      :query-values="runtime.queryValues"
      :selected-record-ids="runtime.selectedRecordIds"
      :loading="runtime.listLoading"
      :error="runtime.listError"
      @query="refreshList"
      @retry="refreshList()"
      @selection-change="controller.emitSelection"
      @open-create="controller.openListCreate"
      @open-view="controller.openListView"
    />

    <EmbedFormRuntime
      v-else-if="runtime.state === EMBED_RUNTIME_STATES.READY
        && runtime.navigation.surfaceType === 'FORM'
        && runtime.form"
      :bootstrap="runtime.bootstrap"
      :form="runtime.form"
      :controller="controller"
      :can-back="runtime.navigation.canBack"
      :loading="runtime.formLoading"
      :error="runtime.formError"
      :submitting="runtime.formSubmitting"
      :submit-error="runtime.formSubmitError"
      :submit-result="runtime.formSubmitResult"
      @retry="controller.refreshForm"
      @back="controller.backToList"
    />

    <section
      v-else-if="runtime.state === EMBED_RUNTIME_STATES.READY
        && runtime.navigation.surfaceType === 'FORM'"
      class="embed-shell__navigation-error"
      role="alert"
    >
      <p>{{ runtime.formError?.message || '表单暂时无法加载' }}</p>
      <div>
        <button
          v-if="runtime.formError?.recoverable"
          type="button"
          @click="controller.refreshForm"
        >
          重试
        </button>
        <button type="button" class="secondary" @click="controller.backToList">
          返回列表
        </button>
      </div>
    </section>

    <EmbedErrorState
      v-else-if="runtime.state === EMBED_RUNTIME_STATES.SESSION_EXPIRED
        || runtime.state === EMBED_RUNTIME_STATES.FATAL_ERROR"
      :error="runtime.error"
      @retry="controller.retry"
    />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, shallowRef, ref } from 'vue'
import { createEmbedRequest } from './api/embedRequest.js'
import { createEmbedRuntimeApi } from './api/embedRuntimeApi.js'
import { createEmbedSession } from './session/embedSession.js'
import {
  EMBED_RUNTIME_STATES,
  createEmbedRuntimeController
} from './runtime/embedRuntimeController.js'
import EmbedErrorState from './runtime/EmbedErrorState.vue'
import EmbedListRuntime from './runtime/EmbedListRuntime.vue'
import EmbedFormRuntime from './runtime/EmbedFormRuntime.vue'

const props = defineProps({
  entryConfig: {
    type: Object,
    required: true
  }
})

const shellElement = ref(null)
const session = createEmbedSession()
// Exchange 与后续 Runtime 请求必须共享同一个仅内存 Session；否则一次性 code
// 已成功消费后，默认 HTTP client 仍会从另一个空 Session 读取 Token，无法安全重试。
const api = createEmbedRuntimeApi(createEmbedRequest({ session }))
const runtime = shallowRef({
  state: EMBED_RUNTIME_STATES.LOADING_ENTRY,
  phase: 'entry',
  bootstrap: null,
  schema: null,
  page: null,
  form: null,
  navigation: Object.freeze({ surfaceType: null, mode: null, recordId: null, canBack: false }),
  selectedRecordIds: Object.freeze([]),
  queryValues: Object.freeze({}),
  listLoading: false,
  listError: null,
  formLoading: false,
  formError: null,
  formSubmitting: false,
  formSubmitError: null,
  formSubmitResult: null,
  error: null,
  theme: 'light',
  locale: 'zh-CN'
})

function focusFirstControl() {
  const control = shellElement.value?.querySelector?.(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex="0"]'
  )
  control?.focus?.()
}

const controller = createEmbedRuntimeController({
  entryConfig: props.entryConfig,
  api,
  session,
  onFocus: focusFirstControl
})

let unsubscribe
let resizeObserver
let resizeTimer

const isLoading = computed(() => [
  EMBED_RUNTIME_STATES.LOADING_ENTRY,
  EMBED_RUNTIME_STATES.WAITING_HANDSHAKE,
  EMBED_RUNTIME_STATES.EXCHANGING,
  EMBED_RUNTIME_STATES.BOOTSTRAPPING
].includes(runtime.value.state) || (
  runtime.value.state === EMBED_RUNTIME_STATES.READY
    && runtime.value.navigation.surfaceType === 'FORM'
    && runtime.value.formLoading
    && !runtime.value.form
))

const loadingText = computed(() => {
  const labels = {
    entry: '正在读取安全入口…',
    handshake: '正在等待宿主系统连接…',
    exchange: '正在建立安全会话…',
    bootstrap: '正在加载页面配置…',
    schema: '正在加载列表结构…',
    query: '正在查询数据…',
    form: '正在加载表单…'
  }
  return labels[runtime.value.phase] || '正在加载…'
})

function refreshList(options) {
  return controller.refreshList(options)
}

function reportHeight(entries) {
  const height = Math.ceil(entries?.[0]?.contentRect?.height || shellElement.value?.scrollHeight || 0)
  if (!height || resizeTimer !== undefined) return
  // 合并 ResizeObserver 抖动，消息频率最高约 10 次/秒。
  resizeTimer = globalThis.setTimeout(() => {
    resizeTimer = undefined
    controller.emitResize(height)
  }, 100)
}

onMounted(() => {
  unsubscribe = controller.subscribe(value => {
    runtime.value = value
  })
  controller.start()
  if (typeof globalThis.ResizeObserver === 'function') {
    resizeObserver = new globalThis.ResizeObserver(reportHeight)
    resizeObserver.observe(shellElement.value)
  }
})

onBeforeUnmount(() => {
  unsubscribe?.()
  resizeObserver?.disconnect?.()
  if (resizeTimer !== undefined) globalThis.clearTimeout(resizeTimer)
  controller.destroy().catch(() => {})
})
</script>

<style scoped>
.embed-shell {
  min-height: 100%;
  color-scheme: light;
  background: #fff;
}

.embed-shell--dark {
  color-scheme: dark;
  filter: invert(.9) hue-rotate(180deg);
  background: #101828;
}

.embed-shell--dark :deep(img),
.embed-shell--dark :deep(video) {
  filter: invert(1) hue-rotate(180deg);
}

.embed-shell__loading {
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 320px;
  color: #475467;
  font-family: Inter, ui-sans-serif, system-ui, sans-serif;
}

.embed-shell__navigation-error {
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 320px;
  gap: 16px;
  padding: 32px;
  color: #912018;
  font-family: Inter, ui-sans-serif, system-ui, sans-serif;
}

.embed-shell__navigation-error div {
  display: flex;
  gap: 10px;
}

.embed-shell__navigation-error button {
  min-height: 36px;
  padding: 7px 14px;
  color: #fff;
  border: 1px solid #175cd3;
  border-radius: 6px;
  background: #175cd3;
}

.embed-shell__navigation-error button.secondary {
  color: #344054;
  border-color: #d0d5dd;
  background: #fff;
}

.embed-shell__spinner {
  width: 28px;
  height: 28px;
  border: 3px solid #d1e0ff;
  border-top-color: #175cd3;
  border-radius: 50%;
  animation: embed-spin .8s linear infinite;
}

@keyframes embed-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .embed-shell__spinner { animation: none; }
}
</style>
