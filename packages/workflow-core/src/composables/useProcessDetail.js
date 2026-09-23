import { ref } from 'vue'
import { normalizeRuntimeFormConfigs, mergeRuntimeFormConfigs } from '../shared/form-runtime/index.js'
import { resolveEntityStatusLabel } from '../shared/entity-status-runtime.js'

/**
 * 流程详情加载组合式函数
 * 用于审批弹窗、编辑弹窗等需要展示流程图和审批历史的场景
 */
export function useProcessDetail({ request, getProcessHistory }) {
  const bpmnXml = ref('')
  const progressData = ref({
    completedNodes: [],
    activeNodes: [],
    executedSequenceFlows: [],
    nodeAssigneeMap: {},
    nodeAssigneesMap: {}
  })
  const processHistory = ref([])
  const entityData = ref(null)
  const formConfig = ref(null)
  const formConfigs = ref([])
  const approvalConfig = ref(null)
  const processRuntimeMetadata = ref({})

  let loadSequence = 0

  // 获取流程状态显示文本
  function getProcessStatusText(status) {
    const textMap = {
      'NOT_STARTED': '未发起',
      'RUNNING': '运行中',
      'COMPLETED': '已完成',
      'SUSPENDED': '运行中',
      'TERMINATED': '已完成'
    }
    return textMap[status] || status || '-'
  }

  /**
   * 加载流程详情
   * @param {string} instanceId 流程实例 ID
   * @param {object} options 选项
   * @param {string} options.startUserName 发起人名称回退值
   * @param {string} options.taskId 当前任务 ID，并行实例下用于读取对应节点配置
   * @param {function} options.onLoad 加载成功回调，参数为后端返回的 progressRes
   */
  async function loadProcessDetail(instanceId, options = {}) {
    const sequence = ++loadSequence
    const { startUserName = 'admin', taskId = '', onLoad } = options
    bpmnXml.value = ''
    progressData.value = {
      completedNodes: [],
      activeNodes: [],
      executedSequenceFlows: [],
      nodeAssigneeMap: {},
      nodeAssigneesMap: {}
    }
    processHistory.value = []
    entityData.value = null
    formConfig.value = null
    formConfigs.value = []
    approvalConfig.value = null
    processRuntimeMetadata.value = {}
    try {
      const progressRes = await request.get(
        `/process-instance/${instanceId}/progress`,
        { params: taskId ? { taskId } : undefined }
      )
      if (sequence !== loadSequence) return false
      if (progressRes) {
        // 这些坐标描述实例实际运行的不可变流程版本，诊断展示不得从当前草稿或最新发布反推。
        processRuntimeMetadata.value = {
          processInstanceId: progressRes.processInstanceId || instanceId,
          processKey: progressRes.processKey || '',
          processVersion: progressRes.processVersion ?? null
        }
        bpmnXml.value = progressRes.bpmnXml || ''
        progressData.value = {
          completedNodes: progressRes.completedNodes || [],
          activeNodes: progressRes.activeNodes || [],
          terminatedNodes: progressRes.terminatedNodes || [],
          executedSequenceFlows: progressRes.executedSequenceFlows || [],
          nodeAssigneeMap: progressRes.nodeAssigneeMap || {},
          nodeAssigneesMap: progressRes.nodeAssigneesMap || {},
          status: progressRes.status
        }
        entityData.value = progressRes.entityData || null
        if (entityData.value && entityData.value.status) {
          // 服务端返回实体配置的名称；无配置时沿用统一内置名称，不能被流程状态覆盖。
          entityData.value._statusText ||= resolveEntityStatusLabel(entityData.value.status)
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
          if (node.actionLabel) actionText = node.actionLabel
          else if (node.action === 'APPROVED') actionText = '通过'
          else if (node.action === 'REJECTED') actionText = '驳回'
          else if (node.action === 'TRANSFERRED') actionText = '转办'
          else if (node.action === 'TERMINATED') actionText = '终止'
          else if (node.action) actionText = node.action
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
        if (sequence !== loadSequence) return false
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
      return true
    } catch (e) {
      console.error('加载流程详情失败:', e)
      return false
    }
  }

  // 关闭/切换普通编辑记录时让尚未返回的请求失效，避免旧图覆盖新实体。
  function resetProcessDetail() {
    loadSequence++
    bpmnXml.value = ''
    progressData.value = { completedNodes: [], activeNodes: [], executedSequenceFlows: [], nodeAssigneeMap: {}, nodeAssigneesMap: {} }
    entityData.value = null
    formConfig.value = null
    formConfigs.value = []
    approvalConfig.value = null
    processHistory.value = []
    processRuntimeMetadata.value = {}
  }

  return {
    resetProcessDetail,
    bpmnXml,
    progressData,
    processHistory,
    entityData,
    formConfig,
    formConfigs,
    approvalConfig,
    processRuntimeMetadata,
    getProcessStatusText,
    loadProcessDetail
  }
}
