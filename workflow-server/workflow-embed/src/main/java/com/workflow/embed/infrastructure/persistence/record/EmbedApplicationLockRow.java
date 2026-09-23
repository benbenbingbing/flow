package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * Locked Integration Application security state.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param status 状态标识，决定后续嵌入式应用锁定行采用的处理分支
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param version 版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedApplicationLockRow(String id, String status, LocalDateTime expiresAt, long version) {
}
