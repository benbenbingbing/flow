package com.workflow.port.process;

import com.workflow.port.process.instance.model.*;
import com.workflow.port.process.instance.model.ProcessRuntimeViews.InstanceCapabilities;
import com.workflow.port.process.instance.model.ProcessRuntimeViews.Progress;
import com.workflow.port.process.model.ProcessCommandReceipt;
import com.workflow.port.process.model.ProcessPage;
import com.workflow.port.process.query.model.*;
import com.workflow.port.process.query.model.ActionCapability;
import java.util.List;
import java.util.Optional;

/** 查询实例与进度，以及在当前身份权限允许时撤回、终止或触发接收节点。 */
public interface ProcessInstancePort {
    /** 读取运行或历史实例，只有目标不存在才返回 empty。 */
    Optional<ProcessInstanceView> findInstance(String processInstanceId);

    /** 读取必需的实例；不存在抛 PROCESS_NOT_FOUND。 */
    ProcessInstanceView requireInstance(String processInstanceId);

    /** 按实际身份范围及明确筛选条件分页，返回准确的总数。 */
    ProcessPage<ProcessInstanceView> pageInstances(ProcessInstanceQuery query);

    /** 返回与锚点属于同一业务记录且逐轮授权通过的流程轮次。 */
    List<ProcessRoundView> listRounds(String anchorProcessInstanceId);

    /** 从业务记录定位已登记流程轮次，按轮次倒序，仅返回当前身份可读的实例。 */
    List<ProcessRoundView> listRoundsByRecord(String entityCode, String entityRecordId);

    /** 返回活动执行位置，包括接收任务；并行分支保留独立 executionId。 */
    List<ProcessActivityView> listActiveActivities(String processInstanceId);

    /** 查看实际运行轮次的节点与已执行连线。 */
    Progress getProgress(String processInstanceId);

    /** 查询撤回、终止及接收任务的当前可执行性；执行命令仍需重新校验。 */
    InstanceCapabilities getInstanceCapabilities(String processInstanceId);

    /** 验证指定的接收分支和消息标识；并行分支可通过 executionId 精确定位。 */
    ActionCapability previewReceive(String processInstanceId, String executionId, String activityId, String messageRef);

    /** 按发起人和活动节点规则撤回，持久化 WITHDRAWN 结束类型。 */
    ProcessCommandReceipt withdraw(WithdrawProcessCommand command);

    /** 按平台规则终止，持久化 TERMINATED 结束类型。 */
    ProcessCommandReceipt terminate(TerminateProcessCommand command);

    /** 在同一事务写入允许的变量并触发明确的接收任务分支。 */
    ReceiveTaskTriggerResult triggerReceiveTask(TriggerReceiveTaskCommand command);
}
