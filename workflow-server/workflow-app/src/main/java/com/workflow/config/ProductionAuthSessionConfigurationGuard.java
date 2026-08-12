package com.workflow.config;

import com.workflow.admin.auth.application.AuthSessionProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 阻止生产环境以非安全 Cookie 下发 Refresh Token。
 */
@Component
@Profile("production")
public class ProductionAuthSessionConfigurationGuard {

    public ProductionAuthSessionConfigurationGuard(
            AuthSessionProperties properties) {
        if (!properties.isCookieSecure()) {
            throw new IllegalStateException(
                    "Production refresh token cookie must be Secure");
        }
    }
}
