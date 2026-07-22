package com.workflow.dto;

import lombok.Data;

@Data
public class EntityFormNodeReorderRequest {

    private Integer expectedRevision;
    private String parentId;
    private String previousNodeId;
    private String nextNodeId;
}
