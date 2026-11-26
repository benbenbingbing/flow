import { uiEventBindingApi } from '@/api/uiConfig'

/**
 * 会话结束前派发已发布的关闭通知。响应与关闭解耦，旧会话的结果不会刷新、导航或回填新表单。
 * recordId 始终是当前打开入口的记录坐标；新增保存所得 ID 单独作为业务参数提供。
 */
export async function notifyFormClose({
  form, configHash = form?.configHash, entityCode, listKey, runtimeContext,
  mode, reason, recordId, savedRecordId, params = {}
}) {
  if (form?.hasFormCloseBinding !== true || !form.id || !configHash) return
  try {
    await uiEventBindingApi.execute('FORM_CLOSE', {
      configType: 'FORM',
      configId: String(form.id),
      configHash,
      entityCode,
      listKey,
      runtimeContext,
      targetType: 'OWNER',
      recordId,
      input: { mode, reason, savedRecordId, params }
    }, { silentError: true })
  } catch (error) {
    // 只记录排查用的坐标，避免把接口响应或表单业务数据写入浏览器日志。
    console.warn('关闭表单事件执行失败', { formId: form.id, reason, errorCode: error?.errorCode, traceId: error?.traceId })
  }
}
