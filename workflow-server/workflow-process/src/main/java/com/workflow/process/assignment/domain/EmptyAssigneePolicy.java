package com.workflow.process.assignment.domain;

/**
 * 已解析的空办理人策略快照。
 * 默认 BLOCK_PUBLISH，保证历史流程未配置新字段时仍采用 fail-closed 行为。
 *
 * @param strategy {@code strategy}，保存在对象中供后续校验、查询或展示
 * @param fallbackUser 兜底用户，主值不可用时供后续处理兜底
 * @param fallbackGroup 兜底分组，主值不可用时供后续处理兜底
 * @param maxRetries 最大{@code retries}，保存在对象中供后续校验、查询或展示
 * @param initialDelaySeconds 初始{@code delay}秒数，保存在对象中供后续校验、查询或展示
 * @param backoffMultiplier {@code backoff}{@code multiplier}，保存在对象中供后续校验、查询或展示
 * @param responsibilityOwner {@code responsibility}归属方，保存在对象中供后续校验、查询或展示
 */
public record EmptyAssigneePolicy(
        Strategy strategy,
        String fallbackUser,
        String fallbackGroup,
        int maxRetries,
        int initialDelaySeconds,
        double backoffMultiplier,
        String responsibilityOwner) {

    /**
     * 处理{@code secure}默认，并将结果传给后续步骤。
     *
     * @return 处理后的{@code secure}默认结果，供调用方继续处理
     */
    public static EmptyAssigneePolicy secureDefault() {
        return new EmptyAssigneePolicy(
                Strategy.BLOCK_PUBLISH, null, null, 3, 60, 2.0, "workflow-admin");
    }

    /**
     * 定义{@code strategy}的可选值；调用方据此选择对应的处理分支。
     */
    public enum Strategy {
        BLOCK_PUBLISH,
        CREATE_INCIDENT,
        FALLBACK_GROUP,
        FALLBACK_USER,
        WAIT_AND_RETRY
    }
}
