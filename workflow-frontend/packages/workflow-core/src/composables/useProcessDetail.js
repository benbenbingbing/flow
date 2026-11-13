import { ref } from 'vue'
import { normalizeRuntimeFormConfigs, mergeRuntimeFormConfigs } from '../shared/form-runtime/index.js'
import { resolveEntityStatusLabel } from '../shared/entity-status-runtime.js'

/**
 * 流程详情加载组合式函数
 * 用于审批弹窗、编辑弹窗等需要展示流程图和审批历史的场景
 */
export function useProcessDetail({ request }) {
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
  let formSequence = 0
  let loadedProgressId = ''
  let pendingProgress = null
  const progressLoading = ref(false)
  const progressError = ref('')

  /** 首屏只读取实体与生效表单；图和历史的迟到响应不会覆盖已经展示或编辑的数据。 */
  async function loadProcessFormRuntime(instanceId, options = {}) {
    const sequence = ++formSequence
    entityData.value = null
    formConfig.value = null
    formConfigs.value = []
    approvalConfig.value = null
    try {
      const result = await request.get(`/process-instance/${encodeURIComponent(instanceId)}/form-runtime`, {
        params: options.taskId ? { taskId: options.taskId } : undefined
      })
      if (sequence !== formSequence) return false
      if (!result) throw new Error('流程表单数据为空')
      processRuntimeMetadata.value = {
        processInstanceId: result.processInstanceId || instanceId,
        processKey: result.processKey || '', processVersion: result.processVersion ?? null
      }
      progressData.value = { ...progressData.value, status: result.status }
      entityData.value = result.entityData || null
      if (entityData.value?.status) entityData.value._statusText ||= resolveEntityStatusLabel(entityData.value.status)
      formConfigs.value = normalizeRuntimeFormConfigs(result)
      formConfig.value = mergeRuntimeFormConfigs(formConfigs.value)
      approvalConfig.value = result.approvalConfig || null
      options.onLoad?.(result)
      return true
    } catch (error) {
      if (sequence === formSequence) console.error('加载流程表单失败:', error)
      return false
    }
  }

  /** 同一实例的页签切换复用进度请求；失败可重试，刷新时显式传 force。 */
  function loadProcessDetail(instanceId, options = {}) {
    const id = String(instanceId)
    if (!options.force && loadedProgressId === id) return Promise.resolve(true)
    if (pendingProgress?.id === id) return pendingProgress.promise
    const promise = readProcessProgress(id, options)
    pendingProgress = { id, promise }
    promise.finally(() => { if (pendingProgress?.promise === promise) pendingProgress = null })
    return promise
  }

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
   * @param {function} options.onLoad 加载成功回调，参数为后端返回的 progressRes
   */
  async function readProcessProgress(instanceId, options = {}) {
    const sequence = ++loadSequence
    loadedProgressId = ''
    const { startUserName = '', onLoad } = options
    progressLoading.value = true
    progressError.value = ''
    bpmnXml.value = ''
    progressData.value = {
      completedNodes: [],
      activeNodes: [],
      executedSequenceFlows: [],
      nodeAssigneeMap: {},
      nodeAssigneesMap: {},
      status: progressData.value.status
    }
    processHistory.value = []
    try {
      const progressRes = await request.get(
        `/process-instance/${encodeURIComponent(instanceId)}/progress`
      )
      if (sequence !== loadSequence) return false
      if (!progressRes) throw new Error('流程进度数据为空')
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
          terminatedNodes: progressRes.cancelledNodes || progressRes.terminatedNodes || [],
          cancelledNodes: progressRes.cancelledNodes || progressRes.terminatedNodes || [],
          endType: progressRes.endType || null,
          endReason: progressRes.endReason || null,
          executedSequenceFlows: progressRes.executedSequenceFlows || [],
          nodeAssigneeMap: progressRes.nodeAssigneeMap || {},
          nodeAssigneesMap: progressRes.nodeAssigneesMap || {},
          rounds: progressRes.rounds || [],
          status: progressRes.status
        }
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
          else if (node.action === 'WITHDRAWN') actionText = '撤回'
          else if (node.action === 'CANCELLED' || node.status === 'CANCELLED') actionText = '已取消'
          else if (node.action === 'COMPLETED') actionText = '完成'
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
            type: node.action === 'TRANSFERRED' ? 'warning' : (['TERMINATED', 'CANCELLED'].includes(node.status) ? 'danger' : node.status === 'WITHDRAWN' ? 'warning' : (node.status === 'COMPLETED' ? 'success' : 'primary')),
            status: node.status,
            action: node.action
          }
        }).reverse()
      } else {
        processHistory.value = []
      }
      loadedProgressId = instanceId
      return true
    } catch (e) {
      if (sequence === loadSequence) {
        progressError.value = '流程进度加载失败，请重试'
        console.error('加载流程详情失败:', e)
      }
      return false
    } finally {
      if (sequence === loadSequence) progressLoading.value = false
    }
  }

  // 关闭/切换普通编辑记录时让尚未返回的请求失效，避免旧图覆盖新实体。
  function resetProcessDetail() {
    loadSequence++
    formSequence++
    loadedProgressId = ''
    pendingProgress = null
    progressLoading.value = false
    progressError.value = ''
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
    loadProcessFormRuntime,
    progressLoading,
    progressError,
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
