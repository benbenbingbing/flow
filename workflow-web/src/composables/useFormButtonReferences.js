import { computed, ref, watch } from 'vue'
import { getFormReleases } from '@/api/entityForm'
import { uiEventBindingApi } from '@/api/uiConfig'
import { publishedFormButtonKeys } from '@/shared/form-actions'

/**
 * 统一维护表单按钮的草稿绑定、发布引用和本地新增状态。
 * 引用读取失败时保持稳定编码锁定，避免网络抖动产生无法清理的旧 targetKey。
 */
export function useFormButtonReferences(props) {
  const eventBindings = ref([])
  const publishedButtonKeySet = ref(new Set())
  const referenceLoading = ref(false)
  const referenceLoadFailed = ref(false)
  let referenceLoadSequence = 0
  let locallyCreatedButtons = new WeakSet()

  const persistedButtonKeySet = computed(() => new Set(
    props.persistedButtonKeys.map(key => String(key || '').trim()).filter(Boolean)
  ))

  watch(
    () => [props.formId, props.activeReleaseId, props.eventBindingRevision],
    loadButtonReferences,
    { immediate: true }
  )
  watch(
    () => props.persistenceRevision,
    (value, previous) => {
      if (previous !== undefined && value !== previous) {
        locallyCreatedButtons = new WeakSet()
      }
    }
  )

  function markLocallyCreated(button) {
    if (button) locallyCreatedButtons.add(button)
  }

  function isLocallyCreatedButton(button) {
    const key = String(button?.key || '').trim()
    return Boolean(button) && (
      locallyCreatedButtons.has(button)
      || !key
      || !persistedButtonKeySet.value.has(key)
    )
  }

  function buttonBindings(button) {
    const key = String(button?.key || '')
    return eventBindings.value.filter(binding =>
      String(binding?.targetType || '').toUpperCase() === 'BUTTON'
      && String(binding?.targetKey || '') === key
    )
  }

  function isKeyLocked(button) {
    if (referenceLoading.value || referenceLoadFailed.value) return true
    const key = String(button?.key || '')
    return publishedButtonKeySet.value.has(key)
      || buttonBindings(button).length > 0
      || (Boolean(props.formId) && !isLocallyCreatedButton(button))
  }

  function keyLockReason(button) {
    if (referenceLoading.value) return '正在检查发布与事件引用…'
    if (referenceLoadFailed.value) return '引用状态暂不可用，已安全锁定'
    if (buttonBindings(button).length) return '已绑定事件，稳定编码不可修改'
    if (publishedButtonKeySet.value.has(String(button?.key || ''))) {
      return '已进入发布快照，稳定编码不可修改'
    }
    return '已保存为稳定契约；如需更名请新建按钮并迁移事件链'
  }

  async function loadButtonReferences() {
    const sequence = ++referenceLoadSequence
    const currentFormId = String(props.formId || '')
    if (!currentFormId) {
      eventBindings.value = []
      publishedButtonKeySet.value = new Set()
      referenceLoading.value = false
      referenceLoadFailed.value = false
      return
    }
    referenceLoading.value = true
    referenceLoadFailed.value = false
    try {
      const [bindings, releases] = await Promise.all([
        uiEventBindingApi.list('FORM', currentFormId),
        props.activeReleaseId
          ? getFormReleases(currentFormId)
          : Promise.resolve([])
      ])
      if (sequence !== referenceLoadSequence) return
      eventBindings.value = Array.isArray(bindings) ? bindings : []
      const releaseRows = Array.isArray(releases)
        ? releases
        : (Array.isArray(releases?.data)
            ? releases.data
            : (Array.isArray(releases?.records) ? releases.records : []))
      publishedButtonKeySet.value = new Set(
        publishedFormButtonKeys(releaseRows, props.activeReleaseId)
      )
    } catch (error) {
      if (sequence !== referenceLoadSequence) return
      console.error('加载表单按钮引用失败:', error)
      referenceLoadFailed.value = true
    } finally {
      if (sequence === referenceLoadSequence) {
        referenceLoading.value = false
      }
    }
  }

  return {
    buttonBindings,
    isKeyLocked,
    isLocallyCreatedButton,
    keyLockReason,
    loadButtonReferences,
    markLocallyCreated,
    persistedButtonKeySet,
    referenceLoadFailed,
    referenceLoading
  }
}
