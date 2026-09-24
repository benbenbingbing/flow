package com.workflow.process.task.infrastructure.persistence.record;

import lombok.Data;

/** 列表能力批量查询的最小投影；assignedFlag 由引擎身份计算，不能读取实体的任务摘要代替。 */
@Data
public class ActionableTaskSummaryRow {
    private String taskId;
    private String nodeName;
    private String entityCode;
    private String entityDataId;
    private String processInstanceId;
    private Integer assignedFlag;
}
