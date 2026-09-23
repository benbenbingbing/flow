package com.workflow.openapi.api.response;

import com.workflow.contracts.embed.launch.model.EmbedLaunchIssued;
import java.time.Instant;

/**
 * HTTP representation of a newly issued, one-time Embed launch.
 *
 * @param launchId 启动记录ID，后续用于处理打开嵌入式启动记录响应时定位或关联目标
 * @param embedUrl 嵌入式URL，保存在对象中供后续校验、查询或展示
 * @param launchCode 启动记录编码，后续用于处理打开嵌入式启动记录响应时定位或关联目标
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param view 视图，保存在对象中供后续校验、查询或展示
 * @param protocolVersion {@code protocol}版本，保存在对象中供后续校验、查询或展示
 */
public record OpenEmbedLaunchResponse(
        String launchId,
        String embedUrl,
        String launchCode,
        Instant expiresAt,
        View view,
        String protocolVersion) {

    /**
     * Maps the stable Embed result to the Open API response envelope payload.
     *
     * @param issued 已签发，作为 {@code OpenEmbedLaunchResponse} 的输入影响后续处理
     * @return 处理后的起始结果，供调用方继续处理
     */
    public static OpenEmbedLaunchResponse from(EmbedLaunchIssued issued) {
        return new OpenEmbedLaunchResponse(
                issued.launchId(),
                issued.embedUrl(),
                issued.launchCode(),
                issued.expiresAt(),
                new View(
                        issued.view().key(),
                        issued.view().surfaceType()),
                issued.protocolVersion());
    }

    /**
     * Stable Embed view identity selected for this launch.
     *
     * <p>The internal runtime snapshot is deliberately not part of the host
     * contract: a host stores the stable view key, while Flow resolves and
     * pins the current snapshot for each newly issued launch.</p>
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param surfaceType 界面类型标识，决定后续视图采用的处理分支
     */
    public record View(
            String key,
            String surfaceType) {
    }
}
