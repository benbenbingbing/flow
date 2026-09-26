package com.workflow.contracts.embed.launch.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable business input for issuing an Embed launch.
 *
 * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
 * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
 * @param channelId 通道ID，后续用于处理嵌入式启动记录命令时定位或关联目标
 * @param subject 主体，保存在对象中供后续校验、查询或展示
 * @param entry 入口，保存在对象中供后续校验、查询或展示
 * @param context 执行上下文，向后续嵌入式启动记录命令步骤传递身份、配置或状态
 * @param ui 界面，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchCommand(
        String viewKey,
        String parentOrigin,
        String channelId,
        EmbedLaunchSubject subject,
        EmbedLaunchEntry entry,
        Map<String, Object> context,
        EmbedLaunchUi ui) {

    /**
     * 初始化嵌入式启动记录命令，保存构造参数供后续方法使用。
     *
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @param parentOrigin 父级来源，保存在对象中供后续校验、查询或展示
     * @param channelId 通道ID，后续用于初始化嵌入式启动记录时定位或关联目标
     * @param subject 主体，保存在对象中供后续校验、查询或展示
     * @param entry 入口，保存在对象中供后续校验、查询或展示
     * @param context 执行上下文，向后续嵌入式启动记录步骤传递身份、配置或状态
     * @param ui 界面，保存在对象中供后续校验、查询或展示
     */
    public EmbedLaunchCommand {
        Objects.requireNonNull(viewKey, "viewKey");
        Objects.requireNonNull(parentOrigin, "parentOrigin");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(entry, "entry");
        context = context == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }
}
