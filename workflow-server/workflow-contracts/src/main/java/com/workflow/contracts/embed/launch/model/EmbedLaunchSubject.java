package com.workflow.contracts.embed.launch.model;

import java.util.Objects;

/**
 * External subject assertion supplied by the host application for a launch.
 *
 * @param type 类型标识，决定后续嵌入式启动记录主体采用的处理分支
 * @param assertion 断言，保存在对象中供后续校验、查询或展示
 * @param namespace 命名空间，保存在对象中供后续校验、查询或展示
 * @param externalUserId 外部用户ID，后续用于处理嵌入式启动记录主体时定位或关联目标
 */
public record EmbedLaunchSubject(
        String type,
        String assertion,
        String namespace,
        String externalUserId) {

    /**
     * 初始化嵌入式启动记录主体，保存构造参数供后续方法使用。
     *
     * @param type 类型标识，决定后续嵌入式启动记录主体采用的处理分支
     * @param assertion 断言，保存在对象中供后续校验、查询或展示
     * @param namespace 命名空间，保存在对象中供后续校验、查询或展示
     * @param externalUserId 外部用户ID，后续用于初始化嵌入式启动记录主体时定位或关联目标
     */
    public EmbedLaunchSubject {
        Objects.requireNonNull(type, "type");
    }

    /**
     * 生成当前对象的文本表示，供日志和排障使用。
     *
     * @return 转换为后的字符串文本，供调用方比较或展示
     */
    @Override
    public String toString() {
        return "EmbedLaunchSubject[type=" + type
                + ", assertion=<redacted>"
                + ", namespace=" + namespace
                + ", externalUserId=" + externalUserId + "]";
    }
}
