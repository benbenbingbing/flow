<template>
  <section class="native-embedded-list" :aria-busy="loading">
    <div v-if="loading" class="native-embedded-list__state" role="status">
      <span class="native-embedded-list__spinner" aria-hidden="true" />
      <p>正在打开 Flow 列表…</p>
    </div>

    <div v-else-if="error" class="native-embedded-list__state" role="alert">
      <p>{{ error }}</p>
      <el-button type="primary" @click="prepareRuntime">重试</el-button>
    </div>

    <!--
      直接挂载 Flow 正式列表页，不读取 Embed External Schema，也不复制列、
      查询控件、按钮或弹窗。后续新增渲染器/数据源/组件会自动走同一 registry。
      pageSize=0 是 EntityDataList 的“不覆盖”哨兵值；真实页大小从 exact
      LIST Release 读取，不允许 Embed View 中的历史 ui 参数改写。
    -->
    <EntityDataList
      v-else
      ref="listRef"
      embedded
      scene="PAGE"
      :entity-code="target.entityCode"
      :list-key="target.listKey"
      :release-id="target.listReleaseId"
      :release-version="target.listReleaseVersion"
      :release-resolution-token="target.listReleaseResolutionToken"
      :context="target.runtimeContext"
      :create-initial-data="target.initialData"
      :create-context="createContext"
      :default-form="defaultForm"
      :allow-default-form-resolve="!target.defaultFormResolved"
      :page-size="0"
      :max-height="0"
      :form-presentation="formPresentation"
      @selection-change="controller.emitSelection"
    />
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { getFormRuntimeRelease } from '@/api/entityForm'
import { normalizeRuntimeFormRelease } from '@/shared/list-button-form-runtime'
import EntityDataList from '@/views/entity/EntityDataList.vue'

const props = defineProps({
  bootstrap: { type: Object, required: true },
  target: { type: Object, required: true },
  controller: { type: Object, required: true }
})

const target = props.target
const listRef = ref(null)
const loading = ref(true)
const error = ref('')
const defaultForm = ref(null)
let loadSequence = 0

const formPresentation = computed(() =>
  props.bootstrap.ui?.formPresentation === 'dialog' ? 'dialog' : 'seamless'
)

const createContext = computed(() => ({
  ...(target.runtimeContext || {}),
  parameters: {
    ...(target.runtimeContext?.parameters || {}),
    ...(target.parameters || {})
  }
}))

function hasFixedDefaultForm() {
  return Boolean(
    target.formId
      && target.formReleaseId
      && target.formReleaseVersion
      && target.formReleaseResolutionToken
  )
}

/**
 * 列表本体由 EntityDataList 读取 exact LIST Release；如果 Session 同时固定了
 * 默认表单，这里预先读取该 exact FORM Release 并注入原生列表弹窗，
 * 避免已打开 Session 在管理员激活新表单后中途漂移。
 */
async function prepareRuntime(options = {}) {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  defaultForm.value = null
  try {
    if (hasFixedDefaultForm()) {
      const release = await getFormRuntimeRelease(
        target.formId,
        target.formReleaseId,
        target.formReleaseVersion,
        target.formReleaseResolutionToken
      )
      if (sequence !== loadSequence) return
      defaultForm.value = normalizeRuntimeFormRelease(
        release,
        target.formId,
        release?.releaseResolutionToken
          || target.formReleaseResolutionToken
      )
    }
    if (sequence !== loadSequence) return
    loading.value = false
  } catch (cause) {
    if (sequence !== loadSequence) return
    console.error('加载 Flow 原生嵌入列表失败:', cause)
    loading.value = false
    error.value = cause?.message || 'Flow 列表暂时无法加载，请稍后重试'
    if (options?.throwOnError === true) throw cause
  }
}

function focus() {
  listRef.value?.focus?.()
}

/** 宿主刷新必须等待真实加载结果；预加载失败时先恢复固定表单，再挂载并刷新原生列表。 */
async function reload() {
  if (!listRef.value || error.value) {
    await prepareRuntime({ throwOnError: true })
    await nextTick()
  }
  if (!listRef.value?.reload) {
    throw Object.assign(new Error('Flow 列表暂时无法加载，请稍后重试'), {
      errorCode: 'EMBED_RUNTIME_UNAVAILABLE',
      status: 503
    })
  }
  return listRef.value.reload({ throwOnError: true })
}

defineExpose({ focus, reload })
onMounted(prepareRuntime)
onBeforeUnmount(() => {
  loadSequence += 1
})
</script>

<style scoped>
.native-embedded-list {
  min-height: 100vh;
  background: var(--el-bg-color, #fff);
}

.native-embedded-list__state {
  display: grid;
  min-height: 320px;
  place-content: center;
  justify-items: center;
  gap: 12px;
  color: var(--el-text-color-regular, #606266);
}

.native-embedded-list__spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--el-border-color, #dcdfe6);
  border-top-color: var(--el-color-primary, #409eff);
  border-radius: 50%;
  animation: native-embedded-list-spin 0.8s linear infinite;
}

@keyframes native-embedded-list-spin {
  to { transform: rotate(360deg); }
}
</style>
