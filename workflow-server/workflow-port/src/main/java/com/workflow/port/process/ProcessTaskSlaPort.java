package com.workflow.port.process;

import com.workflow.port.process.model.instance.ProcessRuntimeViews.TaskSla;
import com.workflow.port.process.model.ProcessPage;
import java.util.Map;

/** 任务 SLA 计时与监控查询，由调用方授权。 */
public interface ProcessTaskSlaPort {
    /** 读操作。查询 SLA 当前状态；未配置时返回 null。 */
    TaskSla read(String taskId);

    /** 写操作。记录任务响应，重复调用沿用首次响应时间。 */
    TaskSla acknowledge(String taskId);

    /** 写操作。申请按已发布规则暂停任务 SLA，原因写入计时历史。 */
    TaskSla pause(String taskId, String reason, String pauseType);

    /** 写操作。恢复计时，按任务策略重算到期时间。 */
    TaskSla resume(String taskId);

    /** 按状态、流程编码、实例和办理人等条件分页。 */
    ProcessPage<MonitorItem> pageMonitor(MonitorQuery query);

    /** 按流程编码或实例统计各状态任务数，包含零值状态。 */
    Map<String, Long> countMonitorByStatus(String processKey, String processInstanceId);

    record MonitorQuery(int pageNumber, int pageSize, String status, String processKey,
                        String processInstanceId, String assigneeId, String keyword) { }

    /** 保留实例、节点和办理人，供看板定位任务并提示 SLA 状态。 */
    record MonitorItem(String processInstanceId, String processKey, String nodeName,
                       String assigneeId, TaskSla sla) { }
}
