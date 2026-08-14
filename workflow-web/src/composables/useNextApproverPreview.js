import { onScopeDispose, ref } from 'vue'
import { previewNextApproval } from '@/api/processTask'
import { normalizeNextApproverPreview } from '@/shared/next-approver'
import {
  BUSINESS_TRACE_HEADER,
  createBusinessTraceKey
} from '@/shared/request'

/**
 * 管理下一审批人预览的防抖、请求签名与过期响应保护。
 * 调用方只提供当前审批上下文，避免把具体表单组件耦合进请求层。
 */
export function useNextApproverPreview(options) {
  const preview = ref(normalizeNextApproverPreview())
  const loading = ref(false)
  const dirty = ref(false)
  let timer = null
  let version = 0
  let activePromise = null
  let inFlightSignature = ''
  let inFlightVersion = 0
  let lastSignature = ''
  let lastTraceKey = ''

  function enabled() {
    return options.isEnabled?.() !== false && Boolean(options.getTaskId?.())
  }

  function requestPayload() {
    return {
      action: options.getAction?.() || '',
      actionLabel: options.getActionLabel?.() || undefined,
      comment: options.getComment?.() ?? '',
      formData: options.getFormData?.() || {}
    }
  }

  function signature(payload = requestPayload()) {
    try {
      return JSON.stringify({
        taskId: options.getTaskId?.() || '',
        action: payload.action,
        actionLabel: payload.actionLabel,
        comment: payload.comment,
        formData: payload.formData
      })
    } catch {
      return `unserializable:${Date.now()}`
    }
  }

  function reset() {
    if (timer) clearTimeout(timer)
    timer = null
    version += 1
    activePromise = null
    inFlightSignature = ''
    inFlightVersion = 0
    lastSignature = ''
    lastTraceKey = ''
    loading.value = false
    dirty.value = false
    preview.value = normalizeNextApproverPreview()
  }

  async function refresh() {
    if (!enabled()) return false
    const taskId = String(options.getTaskId())
    const payload = requestPayload()
    const currentSignature = signature(payload)
    if (
      loading.value
      && activePromise
      && inFlightSignature === currentSignature
      && inFlightVersion === version
    ) return activePromise

    if (timer) clearTimeout(timer)
    timer = null
    dirty.value = false
    const requestVersion = ++version
    const requestTraceKey = createBusinessTraceKey()
    loading.value = true
    inFlightSignature = currentSignature
    inFlightVersion = requestVersion

    let runPromise
    runPromise = (async () => {
      try {
        const result = await previewNextApproval(taskId, payload, {
          headers: { [BUSINESS_TRACE_HEADER]: requestTraceKey }
        })
        if (
          requestVersion !== version
          || !enabled()
          || String(options.getTaskId?.() || '') !== taskId
        ) return false
        preview.value = normalizeNextApproverPreview(result)
        lastSignature = currentSignature
        lastTraceKey = requestTraceKey
        return true
      } catch (error) {
        if (requestVersion !== version || !enabled()) return false
        preview.value = normalizeNextApproverPreview({
          status: 'BLOCKED',
          message: error?.message || '下一节点审批人预览失败，请重试'
        })
        lastSignature = currentSignature
        lastTraceKey = ''
        return false
      } finally {
        if (requestVersion === version) {
          loading.value = false
          inFlightSignature = ''
          inFlightVersion = 0
          if (activePromise === runPromise) activePromise = null
        }
      }
    })()
    activePromise = runPromise
    return runPromise
  }

  function schedule() {
    if (!enabled()) return
    dirty.value = true
    version += 1
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      refresh()
    }, options.debounceMs ?? 350)
  }

  async function ensureCurrent() {
    const currentSignature = signature()
    if (
      loading.value
      && activePromise
      && inFlightSignature === currentSignature
      && inFlightVersion === version
    ) await activePromise
    if (dirty.value || lastSignature !== currentSignature) await refresh()
  }

  function getCurrentTraceKey() {
    return lastSignature === signature() ? lastTraceKey : ''
  }

  onScopeDispose(reset)
  return {
    preview,
    loading,
    dirty,
    reset,
    refresh,
    schedule,
    ensureCurrent,
    getCurrentTraceKey
  }
}
