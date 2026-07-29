package com.workflow.entity.version.api.request;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 版本场景模拟匹配请求。
 */
@Data
public class EntityVersionSimulationRequest {

    private String sourceType;
    private String sourceId;
    private String operationType;
    private String businessIntentCode;
    private String businessIntentName;
    private String recordId;
    private Map<String, Object> beforeRecord =
            new LinkedHashMap<>();
    private Map<String, Object> afterRecord =
            new LinkedHashMap<>();
    private Map<String, Object> extraParams =
            new LinkedHashMap<>();
}
