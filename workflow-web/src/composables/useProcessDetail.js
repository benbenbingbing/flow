import { ref } from 'vue'
import { getProcessHistory } from '@/api/processTask'
import request from '@/utils/request'
import { normalizeRuntimeFormConfigs, mergeRuntimeFormConfigs } from '@/shared/form-runtime'

/**
 * 流程详情加载组合式函数
 * 用于审批弹窗、编辑弹窗等需要展示流程图和审批历史的场景
 */
export function useProcessDetail() {
  const bpmnXml = ref('')
  const progressData = ref({
    completedNodes: [],
    activeNodes: [],
    executedSequenceFlows: [],
    nodeAssigneeMap: {}
  })
  const processHistory = ref([])
  const entityData = ref(null)
  const formConfig = ref(null)
  const formConfigs = ref([])
  const approvalConfig = ref(null)

  // 判断是否为 Tab 模式的子表单
  function isTabSubForm(field) {
    if (!field) return false
    const type = (field.componentType || field.fieldType || '').toUpperCase()
    if (!['SUB_FORM', 'SUB_FORM_LIST'].includes(type)) return false
    if (field.displayMode === 'tab') return true
    if (field.componentProps) {
      try {
        const compProps = typeof field.componentProps === 'string'
          ? JSON.parse(field.componentProps)
          : field.componentProps
        return compProps.subFormConfig?.displayMode === 'tab'
      } catch (e) {}
    }
    return false
  }

  // 获取流程状态显示文本
  function getProcessStatusText(status) {
    const textMap = {
      'RUNNING': '运行中',
      'COMPLETED': '已完成',
      'SUSPENDED': '已挂起',
      'TERMINATED': '已终止'
    }
    return textMap[status] || status || '-'
  }

  /**
   * 加载流程详情
   * @param {string} instanceId 流程实例 ID
   * @param {object} options 选项
   * @param {string} options.startUserName 发起人名称回退值
   * @param {function} options.onLoad 加载成功回调，参数为后端返回的 progressRes
   */
  async function loadProcessDetail(instanceId, options = {}) {
    const { startUserName = 'admin', onLoad } = options
    try {
      const progressRes = await request.get(`/process-instance/${instanceId}/progress`)
      if (progressRes) {
        bpmnXml.value = progressRes.bpmnXml || ''
        progressData.value = {
          completedNodes: progressRes.completedNodes || [],
          activeNodes: progressRes.activeNodes || [],
          terminatedNodes: progressRes.terminatedNodes || [],
          executedSequenceFlows: progressRes.executedSequenceFlows || [],
          nodeAssigneeMap: progressRes.nodeAssigneeMap || {},
          status: progressRes.status
        }
        entityData.value = progressRes.entityData || null
        if (entityData.value && entityData.value.status) {
          const statusMap = {
            'DRAFT': '草稿',
            'PENDING': '审批中',
            'APPROVED': '已通过',
            'REJECTED': '已驳回',
            'COMPLETED': '已完成',
            'WITHDRAWN': '已撤回'
          }
          entityData.value._statusText = statusMap[entityData.value.status] || entityData.value.status
        }
        formConfigs.value = normalizeRuntimeFormConfigs(progressRes)
        formConfig.value = mergeRuntimeFormConfigs(formConfigs.value)
        approvalConfig.value = progressRes.approvalConfig || null

        if (onLoad) {
          onLoad(progressRes)
        }
      }

      // 加载审批历史
      if (progressRes?.nodeHistory && progressRes.nodeHistory.length > 0) {
        processHistory.value = progressRes.nodeHistory.map((node) => {
          const isStartNode = node.nodeId?.toLowerCase().includes('start') || node.nodeName === '开始'
          let actionText = ''
          if (node.action === 'APPROVED') actionText = '通过'
          else if (node.action === 'REJECTED') actionText = '驳回'
          else if (node.action === 'TRANSFERRED') actionText = '转办'
          else if (node.action === 'TERMINATED') actionText = '终止'
          else if (node.status === 'COMPLETED') actionText = '完成'
          else if (node.status === 'TERMINATED') actionText = '终止'
          else actionText = '进行中'
          const commentText = node.comment ? `（${node.comment}）` : ''
          return {
            title: node.nodeName || node.nodeId,
            description: isStartNode
              ? `发起人: ${node.assigneeName || node.assignee || startUserName}`
              : (node.assignee ? `执行人: ${node.assigneeName || node.assignee} ${actionText}${commentText}` : `${actionText}${commentText}`),
            time: node.endTime || node.startTime,
            type: node.action === 'TRANSFERRED' ? 'warning' : (node.status === 'TERMINATED' ? 'danger' : (node.status === 'COMPLETED' ? 'success' : 'primary')),
            status: node.status,
            action: node.action
          }
        }).reverse()
      } else {
        const historyRes = await getProcessHistory(instanceId)
        processHistory.value = (historyRes || []).map((h) => {
          const isStart = h.action === '发起' || h.taskName?.toLowerCase().includes('start')
          const isTransfer = h.result === 'transfer' || (h.comment && h.comment.includes('转办'))
          return {
            title: h.taskName || '流程节点',
            description: isStart
              ? `发起人: ${h.assigneeName || h.assignee || startUserName}`
              : `${h.assigneeName || h.assignee || '系统'} ${isTransfer ? '转办' : (h.action || '处理')}`,
            time: h.endTime || h.startTime,
            type: isStart ? 'primary' : (isTransfer ? 'warning' : (h.action === '通过' ? 'success' : 'info')),
            status: h.endTime ? 'COMPLETED' : 'ACTIVE',
            action: h.result
          }
        }).reverse()
      }
    } catch (e) {
      console.error('加载流程详情失败:', e)
    }
  }

  return {
    bpmnXml,
    progressData,
    processHistory,
    entityData,
    formConfig,
    formConfigs,
    approvalConfig,
    isTabSubForm,
    getProcessStatusText,
    loadProcessDetail
  }
}
