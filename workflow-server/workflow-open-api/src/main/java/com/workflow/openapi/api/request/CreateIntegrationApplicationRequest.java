package com.workflow.openapi.api.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 封装创建集成应用的不可变数据；各分量供后续校验、传递或结果展示使用。
 *
 * @param applicationName 应用名称，后续用于处理创建集成应用请求时匹配或展示
 * @param description 描述，保存在对象中供后续校验、查询或展示
 * @param ownerOrganizationId 归属方组织ID，后续用于处理创建集成应用请求时定位或关联目标
 * @param rateLimitPerMinute 频率上限每分钟，保存在对象中供后续校验、查询或展示
 * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
 * @param allowedSourceCidrs 允许来源{@code cidrs}，保存在对象中供后续校验、查询或展示
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record CreateIntegrationApplicationRequest(
        @NotBlank @Size(max = 128) String applicationName,
        @Size(max = 500) String description,
        @Size(max = 64) String ownerOrganizationId,
        @Min(1) @Max(10_000) Integer rateLimitPerMinute,
        @Min(1) @Max(1_000) Integer maxConcurrency,
        @Size(max = 32) List<@NotBlank @Size(max = 64) String> allowedSourceCidrs,
        @Future Instant expiresAt) {
}
