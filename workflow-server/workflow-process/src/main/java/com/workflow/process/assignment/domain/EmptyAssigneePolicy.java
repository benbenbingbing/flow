package com.workflow.process.assignment.domain;

/**
 * 已解析的空办理人策略快照。
 * 默认 BLOCK_PUBLISH，保证历史流程未配置新字段时仍采用 fail-closed 行为。
 */
public record EmptyAssigneePolicy(
        Strategy strategy,
        String fallbackUser,
        String fallbackGroup,
        int maxRetries,
        int initialDelaySeconds,
        double backoffMultiplier,
        String responsibilityOwner) {

    public static EmptyAssigneePolicy secureDefault() {
        return new EmptyAssigneePolicy(
                Strategy.BLOCK_PUBLISH, null, null, 3, 60, 2.0, "workflow-admin");
    }

    public enum Strategy {
        BLOCK_PUBLISH,
        CREATE_INCIDENT,
        FALLBACK_GROUP,
        FALLBACK_USER,
        WAIT_AND_RETRY
    }
}
