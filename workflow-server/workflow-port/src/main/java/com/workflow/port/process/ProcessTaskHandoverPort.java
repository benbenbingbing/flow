package com.workflow.port.process;

import com.workflow.port.process.model.instance.ProcessInterventionModels.*;
import com.workflow.port.process.model.ProcessPage;

/** 查询可交接待办并在授权范围内交接任务。 */
public interface ProcessTaskHandoverPort {
    /** 读操作。分页列出来源用户可交接的待办。 */
    ProcessPage<HandoverTask> pageHandoverTasks(String sourceUserId, int pageNumber, int pageSize);

    /** 写操作。交接明确范围的待办；返回实际更新的数量。 */
    int handover(HandoverCommand command);
}
