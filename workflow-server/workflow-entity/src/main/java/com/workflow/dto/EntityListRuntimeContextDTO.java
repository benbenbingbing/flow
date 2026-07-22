package com.workflow.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class EntityListRuntimeContextDTO {
    private String sourceEntityCode;
    private String sourceRecordId;
    private String relationKey;
    private Map<String, Object> parameters = new LinkedHashMap<>();
}
