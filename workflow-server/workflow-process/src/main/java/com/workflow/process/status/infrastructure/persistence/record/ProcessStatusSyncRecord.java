package com.workflow.process.status.infrastructure.persistence.record;

import lombok.Data;

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
