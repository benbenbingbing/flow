import request from '@/utils/request'
import { createProcessTaskApi } from '@flow/workflow-api/processTask'

export const { getTodoList, getDoneList, getStatistics, getTaskDetail, completeTask, previewNextApproval, getNextApproverOptions, claimTask, getTaskSla, acknowledgeTask, pauseTaskSla, resumeTaskSla, getTaskOperations, previewAddSign, addSignTask, cancelAddSign, ccTask, getMyCcList, markCcRead, withdrawProcess, getProcessHistory, getMyStartedList, terminateProcess, rejectTask, resubmitProcess, checkRejectedStatus, processTaskApi } = createProcessTaskApi(request)
