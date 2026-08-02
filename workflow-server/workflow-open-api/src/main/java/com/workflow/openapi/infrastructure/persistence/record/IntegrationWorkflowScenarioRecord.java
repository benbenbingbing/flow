package com.workflow.openapi.infrastructure.persistence.record;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IntegrationWorkflowScenarioRecord {

    private String id;
    private String applicationId;
    private String scenarioKey;
    private String displayName;
    private String processKey;
    private Integer processDefinitionVersion;
    private String status;
    private String inputSchemaJson;
    private String outcomeMappingJson;
    private String identityMappingJson;
    private String eventTypesJson;
    private Long revision;
    private String configHash;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
