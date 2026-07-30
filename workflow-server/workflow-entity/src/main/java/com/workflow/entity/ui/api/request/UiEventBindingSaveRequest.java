package com.workflow.entity.ui.api.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * UI 事件绑定链保存请求。
 */
@Data
public class UiEventBindingSaveRequest {

    private String id;
    private Integer expectedRevision;
    private String ownerType;
    private String ownerId;
    private String targetType;
    private String targetKey;
    private String eventCode;
    private String inheritanceMode;
    private List<Map<String, Object>> steps;
    private Boolean enabled;
}
