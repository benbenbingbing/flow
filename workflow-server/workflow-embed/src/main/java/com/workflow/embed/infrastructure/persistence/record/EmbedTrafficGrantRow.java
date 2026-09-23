package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * 配额事务中锁定的当前 Grant 服务端快照。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param applicationId 应用ID，后续用于处理嵌入式{@code traffic}授权行时定位或关联目标
 * @param status 状态标识，决定后续嵌入式{@code traffic}授权行采用的处理分支
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param launchLimitPerMinute 启动记录上限每分钟，保存在对象中供后续校验、查询或展示
 * @param runtimeLimitPerMinute 运行时上限每分钟，保存在对象中供后续校验、查询或展示
 * @param maxConcurrency 最大{@code concurrency}，保存在对象中供后续校验、查询或展示
 */
public record EmbedTrafficGrantRow(
        String id,
        String applicationId,
        String status,
        LocalDateTime expiresAt,
        int launchLimitPerMinute,
        int runtimeLimitPerMinute,
        int maxConcurrency) {
}
