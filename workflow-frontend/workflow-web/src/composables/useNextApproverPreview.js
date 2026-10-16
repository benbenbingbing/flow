import { useNextApproverPreview as useSharedPreview } from '@flow/workflow-core/vue/useNextApproverPreview'
import { previewNextApproval } from '@/api/processTask'
import { BUSINESS_TRACE_HEADER, createBusinessTraceKey } from '@/shared/request'

/** PC 请求适配；防抖、版本保护与预览签名由两端共用的控制器处理。 */
export function useNextApproverPreview(options) {
  return useSharedPreview({ ...options, previewNextApproval, createBusinessTraceKey, traceHeader: BUSINESS_TRACE_HEADER })
}
