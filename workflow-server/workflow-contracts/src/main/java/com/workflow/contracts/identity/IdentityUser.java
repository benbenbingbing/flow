package com.workflow.contracts.identity;

/**
 * 跨模块使用的最小用户目录信息。
 */
public record IdentityUser(
        String id,
        String username,
        String nickname,
        String organizationId,
        String departmentId) {
}
