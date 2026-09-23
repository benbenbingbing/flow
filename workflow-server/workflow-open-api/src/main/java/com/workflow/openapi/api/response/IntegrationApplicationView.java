package com.workflow.openapi.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 封装集成应用视图的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param clientId 客户端ID，后续用于处理集成应用视图时定位或关联目标
 * @param applicationName 应用名称，后续用于处理集成应用视图时匹配或展示
 * @param description 描述，保存在对象中供后续校验、查询或展示
 * @param ownerOrganizationId 归属方组织ID，后续用于处理集成应用视图时定位或关联目标
 * @param status 状态标识，决定后续集成应用视图采用的处理分支
 * @param rateLimitPerMinute 频率上限每分钟，保存在对象中供后续校验、查询或展示
 * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
 * @param allowedSourceCidrs 允许来源{@code cidrs}，保存在对象中供后续校验、查询或展示
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param version 版本，保存在对象中供后续校验、查询或展示
 * @param activeCredentialHint 活动凭据{@code hint}，保存在对象中供后续校验、查询或展示
 * @param activeCredentialExpiresAt 活动凭据过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param activeCredentialLastUsedAt 活动凭据最后{@code used}时间，后续用于判断有效期或展示该事件的发生时间
 * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
 * @param updateTime 更新时间，后续用于判断有效期或展示该事件的发生时间
 */
public record IntegrationApplicationView(
        String id,
        String clientId,
        String applicationName,
        String description,
        String ownerOrganizationId,
        String status,
        int rateLimitPerMinute,
        int maxConcurrency,
        List<String> allowedSourceCidrs,
        Instant expiresAt,
        long version,
        String activeCredentialHint,
        Instant activeCredentialExpiresAt,
        Instant activeCredentialLastUsedAt,
        Instant createTime,
        Instant updateTime) {
}
