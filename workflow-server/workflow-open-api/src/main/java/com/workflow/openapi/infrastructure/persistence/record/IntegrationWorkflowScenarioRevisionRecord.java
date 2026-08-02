package com.workflow.openapi.infrastructure.persistence.record;

import java.time.LocalDateTime;
import lombok.Data;

/** Immutable configuration snapshot for one published or draft scenario revision. */
@Data
public class IntegrationWorkflowScenarioRevisionRecord {

    private String id;
    private String scenarioId;
    private Long revision;
    private String status;
    private String displayName;
    private String processKey;
    private Integer processDefinitionVersion;
    private String inputSchemaJson;
    private String outcomeMappingJson;
    private String identityMappingJson;
    private String eventTypesJson;
    private String configHash;
    private String createdBy;
    private String publishedBy;
    private LocalDateTime createTime;
    private LocalDateTime publishedTime;
}
