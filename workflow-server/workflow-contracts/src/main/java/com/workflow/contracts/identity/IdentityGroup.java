package com.workflow.contracts.identity;

/**
 * 跨模块使用的最小用户组目录信息。
 */
public record IdentityGroup(String id, String code, String name) {
}
