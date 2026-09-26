package com.workflow.contracts.embed.launch.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One-time launch credentials returned after a launch is successfully issued.
 *
 * @param launchId 启动记录ID，后续用于处理嵌入式启动记录已签发时定位或关联目标
 * @param embedUrl 嵌入式URL，保存在对象中供后续校验、查询或展示
 * @param launchCode 启动记录编码，后续用于处理嵌入式启动记录已签发时定位或关联目标
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param view 视图，保存在对象中供后续校验、查询或展示
 * @param protocolVersion {@code protocol}版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedLaunchIssued(
        String launchId,
        String embedUrl,
        String launchCode,
        Instant expiresAt,
        EmbedLaunchView view,
        String protocolVersion) {

    /**
     * 初始化嵌入式启动记录已签发，保存构造参数供后续方法使用。
     *
     * @param launchId 启动记录ID，后续用于初始化嵌入式启动记录已签发时定位或关联目标
     * @param embedUrl 嵌入式URL，保存在对象中供后续校验、查询或展示
     * @param launchCode 启动记录编码，后续用于初始化嵌入式启动记录已签发时定位或关联目标
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param view 视图，保存在对象中供后续校验、查询或展示
     * @param protocolVersion {@code protocol}版本，保存在对象中供后续校验、查询或展示
     */
    public EmbedLaunchIssued {
        Objects.requireNonNull(launchId, "launchId");
        Objects.requireNonNull(embedUrl, "embedUrl");
        Objects.requireNonNull(launchCode, "launchCode");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
    }

    /**
     * 生成当前对象的文本表示，供日志和排障使用。
     *
     * @return 转换为后的字符串文本，供调用方比较或展示
     */
    @Override
    public String toString() {
        return "EmbedLaunchIssued[launchId=" + launchId
                + ", embedUrl=" + embedUrl
                + ", launchCode=<redacted>"
                + ", expiresAt=" + expiresAt
                + ", view=" + view
                + ", protocolVersion=" + protocolVersion + "]";
    }
}
