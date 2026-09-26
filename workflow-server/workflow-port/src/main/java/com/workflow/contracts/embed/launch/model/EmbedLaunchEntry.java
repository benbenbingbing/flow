package com.workflow.contracts.embed.launch.model;

import java.util.Objects;

/**
 * Entry point requested for an Embed launch.
 *
 * @param mode 模式标识，决定后续嵌入式启动记录入口采用的处理分支
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 */
public record EmbedLaunchEntry(
        String mode,
        String recordId) {

    /**
     * 初始化嵌入式启动记录入口，保存构造参数供后续方法使用。
     *
     * @param mode 模式标识，决定后续嵌入式启动记录入口采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public EmbedLaunchEntry {
        Objects.requireNonNull(mode, "mode");
    }
}
