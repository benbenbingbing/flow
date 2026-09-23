package com.workflow.embed.domain;

/**
 * External identity provider security snapshot.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param type 类型标识，决定后续嵌入式身份提供者快照采用的处理分支
 * @param status 状态标识，决定后续嵌入式身份提供者快照采用的处理分支
 * @param issuer 签发方，保存在对象中供后续校验、查询或展示
 * @param subjectNamespace 主体命名空间，保存在对象中供后续校验、查询或展示
 * @param audiencesJson {@code audiences}JSON，保存在对象中供后续校验、查询或展示
 * @param algorithmsJson {@code algorithms}JSON，保存在对象中供后续校验、查询或展示
 * @param jwksMode JWKS模式标识，决定后续嵌入式身份提供者快照采用的处理分支
 * @param jwksJson JWKSJSON，保存在对象中供后续校验、查询或展示
 * @param jwksUrl JWKSURL，保存在对象中供后续校验、查询或展示
 * @param clockSkewSeconds 时钟{@code skew}秒数，保存在对象中供后续校验、查询或展示
 * @param maxAssertionLifetimeSeconds 最大断言{@code lifetime}秒数，保存在对象中供后续校验、查询或展示
 * @param keyVersion 键版本，保存在对象中供后续校验、查询或展示
 * @param securityVersion 安全版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedIdentityProviderSnapshot(
        String id,
        String type,
        String status,
        String issuer,
        String subjectNamespace,
        String audiencesJson,
        String algorithmsJson,
        String jwksMode,
        String jwksJson,
        String jwksUrl,
        int clockSkewSeconds,
        int maxAssertionLifetimeSeconds,
        long keyVersion,
        long securityVersion) {

    /**
     * 判断是否活动；判断结果决定调用方的后续分支。
     *
     * @return 活动条件成立时为 true，否则为 false
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
