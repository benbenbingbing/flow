package com.workflow.dto;

import lombok.Data;

@Data
public class EntityListItemReorderRequest {

    private Integer expectedRevision;
    private String previousId;
    private String nextId;
}
