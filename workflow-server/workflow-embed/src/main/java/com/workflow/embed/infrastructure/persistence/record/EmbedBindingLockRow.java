package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * Locked external identity binding state.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param status 状态标识，决定后续嵌入式绑定锁定行采用的处理分支
 * @param flowUserId 流程用户ID，后续用于处理嵌入式绑定锁定行时定位或关联目标
 * @param bindingVersion 绑定版本，保存在对象中供后续校验、查询或展示
 * @param effectiveAt 有效时间，后续用于判断有效期或展示该事件的发生时间
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record EmbedBindingLockRow(
        String id,
        String status,
        String flowUserId,
        long bindingVersion,
        LocalDateTime effectiveAt,
        LocalDateTime expiresAt) {
}
