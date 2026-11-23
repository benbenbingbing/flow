package com.workflow.contracts.process.task.port;

import com.workflow.contracts.process.instance.model.ProcessRuntimeViews.TaskSla;

/** 任务办理人的 SLA 计时操作，受任务权限和已发布策略约束。 */
public interface ProcessTaskSlaPort {
    /** 查询 SLA 当前状态；未配置时返回 null。 */
    TaskSla read(String taskId);

    /** 记录任务响应，重复调用沿用首次响应时间。 */
    TaskSla acknowledge(String taskId);

    /** 申请按已发布规则暂停任务 SLA，原因写入计时历史。 */
    TaskSla pause(String taskId, String reason, String pauseType);

    /** 恢复计时，按任务策略重算到期时间。 */
    TaskSla resume(String taskId);
}
