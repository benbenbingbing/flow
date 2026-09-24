package com.workflow.bootstrap.guard;

import com.workflow.admin.auth.application.AuthSessionProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 阻止生产环境以非安全 Cookie 下发 Refresh Token。
 */
@Component
@Profile("production")
public class ProductionAuthSessionConfigurationGuard {

    /**
     * 初始化{@code production}认证会话配置保护，保存构造参数供后续方法使用。
     *
     * @param properties 属性集合，保存在对象中供后续校验、查询或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    public ProductionAuthSessionConfigurationGuard(
            AuthSessionProperties properties) {
        if (!properties.isCookieSecure()) {
            throw new IllegalStateException(
                    "Production refresh token cookie must be Secure");
        }
    }
}
