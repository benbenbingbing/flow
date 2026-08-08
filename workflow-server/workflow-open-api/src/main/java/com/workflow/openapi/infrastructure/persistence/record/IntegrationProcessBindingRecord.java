package com.workflow.openapi.infrastructure.persistence.record;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IntegrationProcessBindingRecord {

    private String id;
    private String applicationId;
    private String scenarioId;
    private String scenarioKey;
    private Long scenarioRevision;
    private String scenarioConfigHash;
    private String externalSystem;
    private String businessType;
    private String businessId;
    private String businessVersion;
    private String processInstanceId;
    private String processDefinitionKey;
    private String inputSnapshotJson;
    private String inputHash;
    private String outcomeMappingSnapshotJson;
    private String eventTypesSnapshotJson;
    private String externalInitiatorId;
    private String identityNamespace;
    private String identityMappingSnapshotJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
