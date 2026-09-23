package com.workflow.admin.auth.application;

/**
 * Access Token 校验通过后写入请求上下文的可信身份。
 *
 * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param username 用户名称，后续用于身份匹配或操作展示
 * @param sessionId 会话ID，后续用于处理已认证访问时定位或关联目标
 * @param passwordResetRequired 密码重置必填，保存在对象中供后续校验、查询或展示
 */
public record AuthenticatedAccess(
        /** 当前用户 ID。 */
        String userId,
        /** 当前用户名。 */
        String username,
        /** 当前刷新会话 ID。 */
        String sessionId,
        /** 是否必须先修改密码。 */
        boolean passwordResetRequired) {
}
