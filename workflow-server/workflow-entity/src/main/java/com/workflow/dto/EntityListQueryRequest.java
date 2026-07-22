package com.workflow.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class EntityListQueryRequest {
    private long pageNum = 1;
    private long pageSize = 10;
    private String scene = "PAGE";
    private Map<String, Object> filters = new LinkedHashMap<>();
    private EntityListRuntimeContextDTO context;
}
