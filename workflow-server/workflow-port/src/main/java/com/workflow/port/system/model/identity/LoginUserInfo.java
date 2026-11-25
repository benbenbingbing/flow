package com.workflow.port.system.model.identity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 登录后供业务使用的用户资料和访问凭证。 */
@Getter
@AllArgsConstructor
public final class LoginUserInfo {

    /** 平台用户 ID，供业务关联用户。 */
    private final String id;
    private final String username;
    private final String nickname;
    private final String avatar;
    private final String email;
    private final String phone;

    /** 用户的角色编码，供调用方识别用户角色。 */
    private final List<String> roles;

    /** 调用受保护接口的 Access Token。读取当前用户资料时为空。 */
    private final String token;

    /** Access Token 的 ISO-8601 过期时间，供调用方判断凭证有效期。 */
    private final String tokenExpiresAt;

    /** 是否须先完成临时密码修改，调用方可据此引导用户改密。 */
    private final Boolean passwordResetRequired;
}
