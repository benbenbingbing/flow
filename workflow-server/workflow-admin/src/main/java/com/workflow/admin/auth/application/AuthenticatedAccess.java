package com.workflow.admin.auth.application;

/**
 * Access Token 校验通过后写入请求上下文的可信身份。
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
