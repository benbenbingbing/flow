package com.workflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Map;

@Data
public class UiDataSourceExecuteRequest {

    private String usage;
    private String configType;
    private String configId;
    private String releaseId;
    private Integer releaseVersion;
    private String entityCode;
    private String listKey;
    private Map<String, Object> input;
    private Map<String, Object> context;
    private Integer pageNum;
    private Integer pageSize;

    @JsonIgnore
    private String serverIdempotencyKey;

    @JsonIgnore
    private boolean serverPinnedRelease;
}
