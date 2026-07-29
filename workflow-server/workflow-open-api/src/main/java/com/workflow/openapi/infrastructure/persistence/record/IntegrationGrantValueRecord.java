package com.workflow.openapi.infrastructure.persistence.record;

import lombok.Data;

@Data
public class IntegrationGrantValueRecord {

    private String applicationId;
    private String grantValue;
}
