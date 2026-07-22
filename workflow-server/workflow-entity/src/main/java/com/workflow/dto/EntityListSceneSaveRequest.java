package com.workflow.dto;

import lombok.Data;

@Data
public class EntityListSceneSaveRequest {

    private Integer expectedRevision;
    private String sceneCode;
    private Integer sortOrder;
}
