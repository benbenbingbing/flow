package com.workflow.dto;

import com.workflow.entity.EntityListField;
import lombok.Data;

import java.util.Set;

@Data
public class EntityListFieldSaveRequest {

    private Integer expectedRevision;
    private EntityListField field;
    private Set<String> clearFields;
}
