package com.workflow.contracts.process.query.port;

import com.workflow.contracts.process.model.ProcessPage;
import com.workflow.contracts.process.query.model.*;
import java.util.List;
import java.util.Optional;

/**
 * 平台与 biz 共用的流程读取入口。身份由宿主绑定，集合先授权和筛选再分页。
 * 返回独立投影；技术失败及授权失败抛出异常，不以空集合表示。
 */
public interface ProcessQueryPort {
    /** 读取运行或历史实例，只有目标不存在才返回 empty。 */
    Optional<ProcessInstanceView> findInstance(String processInstanceId);
    /** 读取必需的实例；不存在抛 PROCESS_NOT_FOUND。 */
    ProcessInstanceView requireInstance(String processInstanceId);
    /** 按实际身份范围及明确筛选条件分页，返回准确的总数。 */
    ProcessPage<ProcessInstanceView> pageInstances(ProcessInstanceQuery query);
    /** 返回与锚点属于同一业务记录且逐轮授权通过的流程轮次。 */
    List<ProcessRoundView> listRounds(String anchorProcessInstanceId);
    /** 查询仍活动的引擎或本地加签任务，不存在/已完成返回 empty。 */
    Optional<ProcessTaskView> findActiveTask(String taskId);
    /** 列出实例中全部可见活动任务，调用方不能假定只有一条。 */
    List<ProcessTaskView> listActiveTasks(String processInstanceId);
    /** 查询当前身份待办/已办或指定实例的活动任务。 */
    ProcessPage<ProcessTaskView> pageTasks(ProcessTaskQuery query);
    /** 查询独立任务历史项，同一任务的转办与重提记录不能按 taskId 去重。 */
    ProcessPage<ProcessTaskHistoryView> pageTaskHistory(ProcessHistoryQuery query);
    /** 查询流程操作历史，不附带业务数据字段值。 */
    ProcessPage<ProcessOperationView> pageOperations(ProcessHistoryQuery query);
    /** 返回活动执行位置，包括接收任务；并行分支保留独立 executionId。 */
    List<ProcessActivityView> listActiveActivities(String processInstanceId);
    /** 查询操作可用性提示，命令执行时仍重新校验。 */
    TaskCapabilities getTaskCapabilities(String taskId);
    /** 无副作用地预览下一审批节点，无法提前解析时明确返回 DEFERRED。 */
    NextApprovalPreview previewNextApproval(NextApprovalQuery query);
    /** 根据同一预览输入与作用域查询可选审批人。 */
    ProcessPage<ApproverOption> pageNextApproverOptions(NextApproverOptionsQuery query);
}
