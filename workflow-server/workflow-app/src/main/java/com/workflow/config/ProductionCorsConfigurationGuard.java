package com.workflow.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Prevents permissive browser origins in production.
 */
@Component
@Profile("production")
public class ProductionCorsConfigurationGuard {

    /**
     * 初始化{@code production}{@code cors}配置保护，保存构造参数供后续方法使用。
     *
     * @param properties 属性集合，保存在对象中供后续校验、查询或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    public ProductionCorsConfigurationGuard(
            CorsProperties properties) {
        if (properties.getAllowedOrigins().stream()
                        .anyMatch(origin ->
                                origin == null
                                        || origin.isBlank()
                                        || origin.contains("*"))) {
            throw new IllegalStateException(
                    "Production CORS origins cannot use wildcards");
        }
    }
}
