package com.workflow.embed.domain;

import java.time.Instant;

/**
 * Security-relevant Integration Application state read for launch issuance.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param status 状态标识，决定后续嵌入式应用快照采用的处理分支
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param version 版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedApplicationSnapshot(
        String id,
        String status,
        Instant expiresAt,
        long version) {

    /**
     * 判断是否活动时间；判断结果决定调用方的后续分支。
     *
     * @param now 当前时间，供本方法判断是否活动时间时使用
     * @return 活动时间条件成立时为 true，否则为 false
     */
    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }
}
