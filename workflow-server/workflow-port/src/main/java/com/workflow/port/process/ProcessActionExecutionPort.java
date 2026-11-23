package com.workflow.port.process;

import com.workflow.port.process.instance.model.ProcessInterventionModels.*;
import java.util.List;

/** 查询动作执行记录，重试或登记人工处置结果。 */
public interface ProcessActionExecutionPort {
    /** 读操作。查找流程内的动作执行状态和失败摘要；沿用平台超级管理员权限。 */
    List<ActionExecution> listExecutions(String processInstanceId);

    /** 写操作。通过平台动作重放机制重新执行所选记录。 */
    void retryExecution(String executionId);

    /** 写操作。登记人工处理说明，保留原执行失败状态与历史。 */
    void resolveExecution(String executionId, String note);
}
