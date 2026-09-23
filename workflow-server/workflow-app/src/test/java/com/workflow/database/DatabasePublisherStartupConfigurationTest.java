package com.workflow.database;

import com.workflow.config.database.DatabaseConfiguration;
import com.workflow.config.database.DatabaseDataSourceConfiguration;
import com.workflow.core.database.port.DatabaseConnections;
import com.workflow.entity.data.infrastructure.JdbcSchemaDdlExecutor;
import com.workflow.entity.data.infrastructure.QueuedSchemaDdlExecutor;
import com.workflow.entity.data.infrastructure.schema.SchemaChangeQueuePort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 读取生产 YAML 复现 start.sh 的进程配置边界，不能用测试 YAML 或直接属性覆盖掩盖嵌套占位符错误。 */
class DatabasePublisherStartupConfigurationTest {
    /** 按真实 Config Data 导入链加载主配置，复现指定发布模式且不继承开发机凭据的启动环境。 */
    private ApplicationContextRunner context(String mode) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    // 启动脚本不会把结构账号交给运行进程；测试也不继承开发机上的同名变量。
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    // 单独解析 YAML 不会处理 spring.config.import，必须使用启动时相同的配置加载机制。
                    new ConfigDataApplicationContextInitializer().initialize(context);
                    context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
                })
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class,
                        DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(DatabaseConfiguration.class, DatabaseDataSourceConfiguration.class,
                        QueuedSchemaDdlExecutor.class, JdbcSchemaDdlExecutor.class)
                // 显式指定主配置，避免同名测试 application.yml 遮蔽实际部署配置。
                .withPropertyValues("spring.config.location=file:src/main/resources/application.yml",
                        "SPRING_DATASOURCE_URL=jdbc:mysql://localhost/workflow",
                        "DB_USERNAME=runtime_test", "DB_PASSWORD=test",
                        "spring.flyway.enabled=false", "FLOWABLE_SCHEMA_UPDATE=false",
                        "WORKFLOW_SCHEMA_PUBLISHER_MODE=" + mode);
    }

    @Test
    void queueStartsWithoutAnySchemaVariablesAndKeepsDdlCredentialsUnavailable() {
        context("queue").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(QueuedSchemaDdlExecutor.class)
                    .hasSingleBean(SchemaChangeQueuePort.class).doesNotHaveBean(JdbcSchemaDdlExecutor.class)
                    .doesNotHaveBean(Flyway.class);
            var connections = context.getBean(DatabaseConnections.class);
            assertThat(connections.application()).isNotNull();
            // 可启动不等于可用普通账号执行 DDL；误用直接结构连接仍须明确失败。
            assertThat(assertThrows(IllegalStateException.class, connections::schema))
                    .hasMessage("SCHEMA_DB_PASSWORD is required");
        });
    }

    @Test
    void queueAlsoAllowsSchemaUrlWithoutSchemaIdentity() {
        context("queue").withPropertyValues("SCHEMA_DATASOURCE_URL=jdbc:mysql://localhost/workflow")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(QueuedSchemaDdlExecutor.class));
    }

    @Test
    void directModeStillRejectsMissingSchemaCredentialsDuringStartup() {
        context("direct").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("SCHEMA_DB_PASSWORD is required");
        });
    }

    @Test
    void directModeStartsWithItsDedicatedSchemaIdentity() {
        context("direct").withPropertyValues("SCHEMA_DATASOURCE_URL=jdbc:mysql://localhost/workflow",
                        "SCHEMA_DB_USERNAME=schema_test", "SCHEMA_DB_PASSWORD=test")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JdbcSchemaDdlExecutor.class)
                        .doesNotHaveBean(QueuedSchemaDdlExecutor.class));
    }
}
