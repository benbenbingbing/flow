package com.workflow.dto;

import com.workflow.entity.EntityField;
import lombok.Data;

/**
 * 实体字段DTO
 */
@Data
public class EntityFieldDTO {
    private String id;
    private String fieldCode;
    private String fieldName;
    private EntityField.FieldType fieldType;
    private String dbType;
    private Integer fieldLength;
    private Boolean isRequired;
    private Boolean isUnique;
    private String defaultValue;
    private String optionsJson;
    private String validateRules;
    private Integer sortOrder;
    private Boolean showInList;
    private Boolean showInForm;
    private Boolean isQuery;
}
