package com.workflow.embed.domain;

/**
 * Minimal Flow user state required by the Embed trust boundary.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param username 用户名称，后续用于身份匹配或操作展示
 * @param enabled 启用，保存在对象中供后续校验、查询或展示
 * @param deleted 已删除，保存在对象中供后续校验、查询或展示
 * @param passwordResetRequired 密码重置必填，保存在对象中供后续校验、查询或展示
 */
public record EmbedFlowUser(
        String id,
        String username,
        boolean enabled,
        boolean deleted,
        boolean passwordResetRequired) {

    /**
     * 判断{@code may}{@code use}嵌入式条件是否成立，供调用方选择后续分支。
     *
     * @return {@code may}{@code use}嵌入式条件成立时为 true，否则为 false
     */
    public boolean mayUseEmbed() {
        return enabled && !deleted && !passwordResetRequired;
    }
}
