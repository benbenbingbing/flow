package com.workflow.openapi.infrastructure.persistence.record;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class IntegrationProcessBindingRecord {

    private String id;
    private String applicationId;
    private String externalSystem;
    private String businessType;
    private String businessId;
    private String processInstanceId;
    private String processDefinitionKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
