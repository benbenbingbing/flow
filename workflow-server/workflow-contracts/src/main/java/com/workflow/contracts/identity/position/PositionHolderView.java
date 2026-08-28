package com.workflow.contracts.identity.position;

import java.time.Instant;

/**
 * 组织节点下的有效职务任职人投影。
 */
public record PositionHolderView(
        String userId,
        String username,
        String displayName,
        boolean primary,
        int sortOrder,
        Instant effectiveFrom) {

    public PositionHolderView {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("任职用户 ID 不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("任职用户名不能为空");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("任职生效时间不能为空");
        }
    }
}
