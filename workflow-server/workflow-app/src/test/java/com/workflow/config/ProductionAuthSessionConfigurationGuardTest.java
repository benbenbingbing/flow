package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.workflow.admin.auth.application.AuthSessionProperties;
import org.junit.jupiter.api.Test;

/**
 * 生产环境 Refresh Token Cookie 安全配置测试。
 */
class ProductionAuthSessionConfigurationGuardTest {

    @Test
    void productionRejectsCookieWithoutSecureFlag() {
        AuthSessionProperties properties =
                new AuthSessionProperties();

        assertThrows(
                IllegalStateException.class,
                () -> new ProductionAuthSessionConfigurationGuard(
                        properties));
    }

    @Test
    void productionAcceptsSecureCookie() {
        AuthSessionProperties properties =
                new AuthSessionProperties();
        properties.setCookieSecure(true);

        assertDoesNotThrow(
                () -> new ProductionAuthSessionConfigurationGuard(
                        properties));
    }
}
