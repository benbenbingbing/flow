package com.workflow.port.process;

import com.workflow.port.process.model.instance.ProcessInterventionModels.*;
import com.workflow.port.process.model.ProcessPage;
import java.util.Map;

/** 查询与处置流程办理人异常，由调用方授权。 */
public interface ProcessAssigneeIncidentPort {
    /** 读操作。分页读取指定状态的办理人事件；status 可为空。 */
    ProcessPage<Map<String, Object>> pageIncidents(String status, int pageNumber, int pageSize);

    /** 读操作。读取处置前的事件状态与候选摘要。 */
    Map<String, Object> findIncident(String incidentId);

    /** 写操作。利用现有处置事务和幂等键处理一次事件。 */
    Map<String, Object> handleIncident(HandleAssigneeIncident command);
}
