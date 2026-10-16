import { useProcessDetail as useSharedProcessDetail } from '@flow/workflow-core/vue/useProcessDetail'
import { getProcessHistory } from '@/api/processTask'
import request from '@/utils/request'

/** PC 接入共享的实例发布版本加载逻辑，展示层仍由桌面组件负责。 */
export function useProcessDetail() {
  return useSharedProcessDetail({ request, getProcessHistory })
}
