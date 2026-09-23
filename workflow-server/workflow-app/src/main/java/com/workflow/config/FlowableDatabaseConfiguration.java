package com.workflow.config;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** 在 Flowable 引擎配置初始化前选择 SQL 语法族，避免国产数据库产品名无法自动识别。 */
@Configuration(proxyBeanMethods = false)
public class FlowableDatabaseConfiguration {
    /**
     * 处理Flowable数据库类型{@code configurer}，并将结果传给后续步骤。
     *
     * @param environment 环境，作为 {@code configuration.setDatabaseType} 的输入影响后续处理
     * @return 处理后的Flowable数据库类型{@code configurer}结果，供调用方继续处理
     */
    @Bean
    public static BeanPostProcessor flowableDatabaseTypeConfigurer(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof AbstractEngineConfiguration configuration) {
                    String explicit = environment.getProperty("workflow.database.flowable-type", "");
                    String url = environment.getProperty("spring.datasource.url", "");
                    if (!explicit.isBlank()) configuration.setDatabaseType(explicit);
                    else if (url.startsWith("jdbc:h2:")) configuration.setDatabaseType("h2");
                    else configuration.setDatabaseType(DatabaseJdbcProfiles.flowableType(DatabaseDialects.resolve(
                            environment.getProperty("workflow.database.vendor", "auto"), url)));
                }
                return bean;
            }
        };
    }
}
