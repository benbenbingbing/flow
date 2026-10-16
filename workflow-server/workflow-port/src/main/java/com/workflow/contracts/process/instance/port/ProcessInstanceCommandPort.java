package com.workflow.contracts.process.instance.port;

import com.workflow.contracts.process.model.ProcessCommandReceipt;
import com.workflow.contracts.process.instance.model.*;

/** 实例控制入口，复用平台身份、节点限制及结束状态同步；不替代实体绑定流程的启动编排。 */
public interface ProcessInstanceCommandPort {
    /** 按发起人和活动节点规则撤回，持久化 WITHDRAWN 结束类型。 */
    ProcessCommandReceipt withdraw(WithdrawProcessCommand command);
    /** 按平台规则终止，持久化 TERMINATED 结束类型。 */
    ProcessCommandReceipt terminate(TerminateProcessCommand command);
    /** 在同一事务写入允许的变量并触发明确的接收任务分支。 */
    ReceiveTaskTriggerResult triggerReceiveTask(TriggerReceiveTaskCommand command);
}
