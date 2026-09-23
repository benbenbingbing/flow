package com.workflow.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证真实主配置的导入、生产覆盖及环境变量解析，不创建数据源或启动业务后台任务。 */
class ApplicationConfigImportTest {

    /** 用可控环境变量加载主配置，避免开发机设置和同名测试 YAML 使回归检查失真。 */
    private ApplicationContextRunner context(Map<String, Object> variables) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.config.location=file:src/main/resources/application.yml")
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new SystemEnvironmentPropertySource(
                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
                    new ConfigDataApplicationContextInitializer().initialize(context);
                });
    }

    @Test
    void importsEveryGroupAndResolvesNestedPlaceholders() {
        context(Map.of("DB_HOST", "database.example", "DB_PORT", "3307", "DB_NAME", "config_test"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    // 覆盖每个分组的有效属性，避免遗漏导入时仅靠代码中的默认值通过启动。
                    assertThat(environment.getProperty("server.port")).isEqualTo("8080");
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .startsWith("jdbc:mysql://database.example:3307/config_test?")
                            .contains("serverTimezone=UTC");
                    assertThat(environment.getProperty("flowable.history-level")).isEqualTo("full");
                    assertThat(environment.getProperty("jwt.expiration")).isEqualTo("900000");
                    assertThat(environment.getProperty("file.storage.local.path")).isEqualTo("./uploads");
                    assertThat(environment.getProperty("workflow.open-api.audience")).isEqualTo("flow-open-api");
                    assertThat(environment.getProperty("workflow.embed.crypto.context-key-version"))
                            .isEqualTo("embed-context-v1");
                    assertThat(environment.getProperty("management.metrics.tags.application"))
                            .isEqualTo("workflow-server");
                    assertThat(environment.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
                    assertThat(environment.getProperty("logging.structured.format.console")).isNull();
                    // 必填凭据不能因为配置拆分而悄悄获得默认值。
                    assertThatThrownBy(() -> environment.getRequiredProperty("jwt.secret"))
                            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JWT_SECRET");
                });
    }

    @Test
    void productionProfileOverridesGroupedDefaults() {
        context(Map.of()).withPropertyValues("spring.profiles.active=production").run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
            assertThat(environment.getProperty("mybatis-plus.configuration.log-impl"))
                    .isEqualTo("org.apache.ibatis.logging.nologging.NoLoggingImpl");
            assertThat(environment.getProperty("workflow.outbox.batch-size")).isEqualTo("100");
        });
    }

    @Test
    void environmentOverridesDefaultsAndProductionPlaceholders() {
        context(Map.of(
                "SERVER_PORT", "18080",
                "DB_POOL_MAX_SIZE", "37",
                "FILE_STORAGE_TYPE", "s3",
                "WORKFLOW_LOG_LEVEL", "DEBUG",
                "MYBATIS_LOG_IMPL", "org.apache.ibatis.logging.stdout.StdOutImpl",
                "SPRING_DATASOURCE_URL", "jdbc:mysql://override.example/explicit",
                "JWT_SECRET", "config-import-test-only-secret"))
                .withPropertyValues("spring.profiles.active=production")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("server.port")).isEqualTo("18080");
                    assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("37");
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:mysql://override.example/explicit");
                    assertThat(environment.getProperty("file.storage.type")).isEqualTo("s3");
                    assertThat(environment.getProperty("logging.level.com.workflow")).isEqualTo("DEBUG");
                    assertThat(environment.getProperty("mybatis-plus.configuration.log-impl"))
                            .isEqualTo("org.apache.ibatis.logging.stdout.StdOutImpl");
                    assertThat(environment.getProperty("jwt.secret")).isEqualTo("config-import-test-only-secret");
                    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
                });
    }
}
