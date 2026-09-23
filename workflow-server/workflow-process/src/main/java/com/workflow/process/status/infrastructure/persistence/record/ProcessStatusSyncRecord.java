package com.workflow.process.status.infrastructure.persistence.record;

import lombok.Data;

/**
 * 封装流程状态同步记录的数据访问；应用服务通过它读取或持久化业务状态。
 */
@Data
public class ProcessStatusSyncRecord {

    private String id;
    private String processInstanceId;
    private String eventType;
    private String eventSequence;
    private String entityCode;
    private String entityRecordId;
    private String targetStatus;
    private String statusCategory;
}
