package com.workflow.admin.auth.application;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 浏览器登录会话的生命周期和 Cookie 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "workflow.security.session")
public class AuthSessionProperties {

    /** 用户持续无刷新操作时允许保留会话的最长时间。 */
    private Duration idleTimeout = Duration.ofHours(2);

    /** 从登录开始计算的会话绝对最长时间。 */
    private Duration absoluteTimeout = Duration.ofHours(12);

    /** 已过期或已撤销会话在数据库中的保留时间。 */
    private Duration retention = Duration.ofDays(7);

    /** Refresh Token Cookie 名称。 */
    private String cookieName = "flow_refresh_token";

    /** Refresh Token Cookie 路径。 */
    private String cookiePath = "/api/auth";

    /** Refresh Token Cookie 的 SameSite 策略。 */
    private String cookieSameSite = "Lax";

    /** 是否为 Refresh Token Cookie 设置 Secure 属性。 */
    private boolean cookieSecure;
}
