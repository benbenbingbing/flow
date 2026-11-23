package com.workflow.contracts.process.instance.port;

import com.workflow.contracts.process.instance.model.ProcessInterventionModels.*;
import com.workflow.contracts.process.model.ProcessPage;

import java.util.List;
import java.util.Map;

/** 有独立管理权限的流程异常干预；权限和范围从当前真实身份取得。 */
public interface ProcessInterventionAdminPort {
    /** 分页读取指定状态的办理人事件；status 可为空。 */
    ProcessPage<Map<String, Object>> pageAssigneeIncidents(String status, int pageNumber, int pageSize);

    /** 读取处置前的事件状态与候选摘要。 */
    Map<String, Object> readAssigneeIncident(String incidentId);

    /** 利用现有处置事务和幂等键处理一次事件。 */
    Map<String, Object> handleAssigneeIncident(HandleAssigneeIncident command);

    /** 查找流程内的动作执行状态和失败摘要；沿用平台超级管理员权限。 */
    List<ActionExecution> listActionExecutions(String processInstanceId);

    /** 通过平台动作重放机制重新执行所选记录。 */
    void retryActionExecution(String executionId);

    /** 登记人工处理说明，保留原执行失败状态与历史。 */
    void resolveActionExecution(String executionId, String note);

    /** 分页列出来源用户可交接的待办。 */
    ProcessPage<HandoverTask> pageHandoverTasks(String sourceUserId, int pageNumber, int pageSize);

    /** 交接明确范围的待办；返回实际更新的数量。 */
    int handover(HandoverCommand command);
}
