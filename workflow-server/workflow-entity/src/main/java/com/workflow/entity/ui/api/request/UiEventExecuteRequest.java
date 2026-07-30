package com.workflow.entity.ui.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * UI 事件运行请求。
 *
 * <p>客户端只声明事件来源与业务输入，实际接口服务和操作由服务端从已发布
 * 配置中解析，不能由客户端任意指定。</p>
 */
@Data
public class UiEventExecuteRequest {

    private String eventCode;
    private String configType;
    private String configId;
    private String releaseId;
    private Integer releaseVersion;
    private String releaseResolutionToken;
    private String entityCode;
    private String listKey;
    private String targetType;
    private String targetKey;
    private String recordId;
    private List<String> selectedIds;
    private Object selection;
    private Map<String, Object> input;
    private Map<String, Object> context;

    @JsonIgnore
    private boolean preview;

    @JsonIgnore
    private String serverIdempotencyKey;
}
