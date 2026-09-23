package com.workflow.embed.domain;

/**
 * Current launch configuration resolved by application id and stable view key.
 *
 * @param application 应用，保存在对象中供后续校验、查询或展示
 * @param view 视图，保存在对象中供后续校验、查询或展示
 * @param currentConfigJson 当前配置JSON，保存在对象中供后续校验、查询或展示
 * @param grant 授权，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchConfiguration(
        EmbedApplicationSnapshot application,
        EmbedViewSnapshot view,
        String currentConfigJson,
        EmbedGrantSnapshot grant) {
}
