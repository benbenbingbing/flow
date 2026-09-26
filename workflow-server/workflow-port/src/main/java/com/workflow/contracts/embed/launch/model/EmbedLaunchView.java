package com.workflow.contracts.embed.launch.model;

import java.util.Objects;

/**
 * Stable Embed view identity selected for an issued launch.
 *
 * @param key 键，后续用于授权校验、关联或幂等去重
 * @param surfaceType 界面类型标识，决定后续嵌入式启动记录视图采用的处理分支
 */
public record EmbedLaunchView(
        String key,
        String surfaceType) {

    /**
     * 初始化嵌入式启动记录视图，保存构造参数供后续方法使用。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param surfaceType 界面类型标识，决定后续嵌入式启动记录视图采用的处理分支
     */
    public EmbedLaunchView {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(surfaceType, "surfaceType");
    }
}
