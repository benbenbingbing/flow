package com.workflow.embed.infrastructure.persistence.record;

/**
 * Current Flow user security flags queried by exact user id.
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param username 用户名称，后续用于身份匹配或操作展示
 * @param status 状态标识，决定后续嵌入式流程用户行采用的处理分支
 * @param deleted 已删除，保存在对象中供后续校验、查询或展示
 * @param passwordResetRequired 密码重置必填，保存在对象中供后续校验、查询或展示
 */
public record EmbedFlowUserRow(
        String id,
        String username,
        String status,
        int deleted,
        int passwordResetRequired) {
}
