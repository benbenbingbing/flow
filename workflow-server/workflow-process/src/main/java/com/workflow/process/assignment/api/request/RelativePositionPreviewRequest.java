package com.workflow.process.assignment.api.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程设计器使用样例用户试算相对组织职务的请求。
 */
public record RelativePositionPreviewRequest(
        String sampleUserId,
        Map<String, Object> config) {

    public RelativePositionPreviewRequest {
        config = config == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(config));
    }
}
