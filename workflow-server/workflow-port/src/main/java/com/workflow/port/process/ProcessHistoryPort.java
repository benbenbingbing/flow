package com.workflow.port.process;

import com.workflow.port.process.model.ProcessPage;
import com.workflow.port.process.query.model.*;

/** 读取当前身份可见的流程任务历史与操作日志。 */
public interface ProcessHistoryPort {
    /** 查询独立任务历史项，同一任务的转办与重提记录不能按 taskId 去重。 */
    ProcessPage<ProcessTaskHistoryView> pageTaskHistory(ProcessHistoryQuery query);

    /** 查询流程操作历史，不附带业务数据字段值。 */
    ProcessPage<ProcessOperationView> pageOperations(ProcessHistoryQuery query);
}
