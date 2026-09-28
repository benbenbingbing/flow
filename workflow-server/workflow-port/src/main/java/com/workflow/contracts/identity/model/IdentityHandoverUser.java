package com.workflow.contracts.identity.model;

/**
 * 人员交接使用的最小身份快照；来源用户即使停用或删除，也需要保留身份以处理遗留待办。
 *
 * @param id 精确用户 ID，作为查询待办和提交交接的身份键，不能用昵称代替
 * @param username 用户名，供选择器区分同名人员
 * @param nickname 昵称，供选择器和交接审计展示
 * @param status 当前状态；仅值为 {@code 0} 的未删除用户可以接收待办
 * @param deleted 逻辑删除标志；删除用户仅能作为交接来源
 */
public record IdentityHandoverUser(
        String id,
        String username,
        String nickname,
        String status,
        boolean deleted) {
}
