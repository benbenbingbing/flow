package com.workflow.process.assignment.api.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程设计器使用样例用户试算相对组织职务的请求。
 *
 * @param sampleUserId {@code sample}用户ID，后续用于处理相对位置预览请求时定位或关联目标
 * @param config 配置内容，决定后续相对位置预览请求的处理规则
 */
public record RelativePositionPreviewRequest(
        String sampleUserId,
        Map<String, Object> config) {

    /**
     * 初始化相对位置预览请求，保存构造参数供后续方法使用。
     *
     * @param sampleUserId {@code sample}用户ID，后续用于初始化相对位置预览时定位或关联目标
     * @param config 配置内容，决定后续相对位置预览的处理规则
     */
    public RelativePositionPreviewRequest {
        config = config == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(config));
    }
}
