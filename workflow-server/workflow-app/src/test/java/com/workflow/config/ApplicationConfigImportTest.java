package com.workflow.config;

import com.workflow.admin.identity.provisioning.application.IdentityWriteProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
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
                    assertThat(environment.getProperty("workflow.security.session.cookie-secure")).isEqualTo("false");
                    assertThat(environment.getProperty("file.storage.local.path")).isEqualTo("./uploads");
                    assertThat(environment.getProperty("file.storage.s3.trust-all-certificates"))
                            .isEqualTo("false");
                    assertThat(environment.getProperty("file.storage.minio.trust-all-certificates"))
                            .isEqualTo("false");
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

    /** 新增条目须由真实 YAML 提供默认值，避免只依赖业务类的 @Value 默认值掩盖漏配。 */
    @Test
    void importsSupplementedRuntimeDefaultsAndEmptyIdentitySources() {
        context(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            Map.ofEntries(
                    Map.entry("workflow.process.storage-zone", "Asia/Shanghai"),
                    Map.entry("workflow.task-sla.batch-size", "50"),
                    Map.entry("workflow.task-sla.lease-seconds", "120"),
                    Map.entry("workflow.task-sla.poll-delay-ms", "5000"),
                    Map.entry("workflow.assignee-incident.retry-scan-ms", "30000"),
                    Map.entry("workflow.outbox.retention-cron", "0 15 3 * * *"),
                    Map.entry("workflow.outbox.retention-batch-size", "1000"),
                    Map.entry("workflow.outbox.retention-max-rows", "10000"),
                    Map.entry("workflow.outbox.retention-max-seconds", "10"),
                    Map.entry("workflow.audit.retention-cron", "0 30 3 * * *"),
                    Map.entry("workflow.audit.retention-batch-size", "1000"),
                    Map.entry("workflow.audit.retention-max-rows", "10000"),
                    Map.entry("workflow.audit.retention-max-seconds", "10"),
                    Map.entry("workflow.security.login-throttle.cleanup-cron", "0 45 3 * * *"),
                    Map.entry("workflow.position.leader-projection-reconcile-ms", "60000"),
                    Map.entry("workflow.open-api.rate-limit-cleanup-cron", "0 20 3 * * *"),
                    Map.entry("workflow.open-api.request-lease-cleanup-cron", "0 */10 * * * *"),
                    Map.entry("entity.excel.cleanup-delay-ms", "3600000"))
                    .forEach((property, expected) ->
                            assertThat(environment.getProperty(property)).as(property).isEqualTo(expected));

            var identity = Binder.get(environment).bind("workflow.identity", IdentityWriteProperties.class).get();
            assertThat(identity.getScanPackages()).containsExactly("com.workflow.biz");
            assertThat(identity.getSources()).isEmpty();
            assertThat(environment.containsProperty("ui.release-resolution.ttl-seconds")).isFalse();
        });
    }

    /** 验证各补齐分组的部署变量及显式来源可覆盖默认值，空来源映射不能阻止后续接入。 */
    @Test
    void environmentOverridesSupplementedDefaultsAndIdentityBinding() {
        context(Map.of(
                "WORKFLOW_IDENTITY_SCAN_PACKAGES", "com.acme.identity,com.workflow.biz",
                "WORKFLOW_OPEN_API_RATE_LIMIT_CLEANUP_CRON", "0 10 2 * * *",
                "PROCESS_STORAGE_ZONE", "UTC",
                "TASK_SLA_BATCH_SIZE", "25",
                "ASSIGNEE_INCIDENT_RETRY_SCAN_MS", "45000",
                "OUTBOX_RETENTION_MAX_ROWS", "2000",
                "AUDIT_RETENTION_BATCH_SIZE", "500",
                "LOGIN_THROTTLE_CLEANUP_CRON", "0 40 2 * * *",
                "POSITION_LEADER_PROJECTION_RECONCILE_MS", "90000",
                "ENTITY_EXCEL_CLEANUP_DELAY_MS", "1800000"))
                .withPropertyValues(
                        "workflow.identity.sources.project-directory.write-permission=system:user:manage",
                        "workflow.identity.sources.project-directory.service-user-id=directory-service-test",
                        "workflow.identity.sources.project-directory.default-role-code=employee")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    Map.of(
                            "workflow.open-api.rate-limit-cleanup-cron", "0 10 2 * * *",
                            "workflow.process.storage-zone", "UTC",
                            "workflow.task-sla.batch-size", "25",
                            "workflow.assignee-incident.retry-scan-ms", "45000",
                            "workflow.outbox.retention-max-rows", "2000",
                            "workflow.audit.retention-batch-size", "500",
                            "workflow.security.login-throttle.cleanup-cron", "0 40 2 * * *",
                            "workflow.position.leader-projection-reconcile-ms", "90000",
                            "entity.excel.cleanup-delay-ms", "1800000")
                            .forEach((property, expected) ->
                                    assertThat(environment.getProperty(property)).as(property).isEqualTo(expected));

                    var identity = Binder.get(environment).bind("workflow.identity", IdentityWriteProperties.class).get();
                    assertThat(identity.getScanPackages()).containsExactly("com.acme.identity", "com.workflow.biz");
                    assertThat(identity.getSources()).containsOnlyKeys("project-directory");
                    var source = identity.getSources().get("project-directory");
                    assertThat(source.isPermissionCheckEnabled()).isTrue();
                    assertThat(source.getWritePermission()).isEqualTo("system:user:manage");
                    assertThat(source.getServiceUserId()).isEqualTo("directory-service-test");
                    assertThat(source.getDefaultRoleCode()).isEqualTo("employee");
                    assertThat(source.getVersionMode()).isEqualTo(IdentityWriteProperties.VersionMode.VERSIONED);
                    assertThat(source.getExtensions()).isEmpty();
                });
    }

    /** 来源级开关必须独立绑定，显式免来源权限的来源可省略权限码，其他来源继续沿用默认校验。 */
    @Test
    void identitySourcePermissionCheckCanBeDisabledIndependently() {
        context(Map.of()).withPropertyValues(
                        "workflow.identity.sources.project-directory.permission-check-enabled=false",
                        "workflow.identity.sources.project-directory.service-user-id=directory-service-test",
                        "workflow.identity.sources.other.write-permission=other:write")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var identity = Binder.get(context.getEnvironment())
                            .bind("workflow.identity", IdentityWriteProperties.class).get();
                    var trusted = identity.getSources().get("project-directory");
                    assertThat(trusted.isPermissionCheckEnabled()).isFalse();
                    assertThat(trusted.getWritePermission()).isNull();
                    assertThat(trusted.getServiceUserId()).isEqualTo("directory-service-test");
                    assertThat(trusted.getDefaultRoleCode()).isNull();
                    assertThat(identity.getSources().get("other").isPermissionCheckEnabled()).isTrue();
                    assertThat(identity.getSources().get("other").getDefaultRoleCode()).isNull();
                });
    }

    /** 可选覆盖须保持未声明状态，空配置会阻断 Excel 密钥回退或覆盖数据库自动初始化策略。 */
    @Test
    void optionalOverridesStayAbsentAndExcelSecretsFallBackToJwt() {
        context(Map.of("JWT_SECRET", "config-import-jwt-test-secret")).run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.containsProperty("workflow.database.flowable-type")).isFalse();
            assertThat(environment.containsProperty("spring.datasource.hikari.connection-init-sql")).isFalse();
            assertThat(environment.containsProperty("entity.excel.action-secret")).isFalse();
            assertThat(environment.containsProperty("entity.excel.file-secret")).isFalse();
            assertThat(environment.containsProperty("ui.release-resolution.secret")).isFalse();
            assertThat(environment.resolveRequiredPlaceholders(
                    "${entity.excel.action-secret:${ui.release-resolution.secret:${jwt.secret}}}"))
                    .isEqualTo("config-import-jwt-test-secret");
            assertThat(environment.resolveRequiredPlaceholders(
                    "${entity.excel.file-secret:${entity.excel.action-secret:${ui.release-resolution.secret:${jwt.secret}}}}"))
                    .isEqualTo("config-import-jwt-test-secret");
        });
    }

    /** 沿用历史密钥的部署与显式提供独立密钥的部署，均须按原有优先级解析 Excel 密钥。 */
    @Test
    void excelSecretsUseLegacyFallbackAndDedicatedOverrides() {
        context(Map.of("JWT_SECRET", "config-import-jwt-test-secret",
                "UI_RELEASE_RESOLUTION_SECRET", "config-import-legacy-test-secret"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.resolveRequiredPlaceholders(
                            "${entity.excel.action-secret:${ui.release-resolution.secret:${jwt.secret}}}"))
                            .isEqualTo("config-import-legacy-test-secret");
                    assertThat(environment.resolveRequiredPlaceholders(
                            "${entity.excel.file-secret:${entity.excel.action-secret:${ui.release-resolution.secret:${jwt.secret}}}}"))
                            .isEqualTo("config-import-legacy-test-secret");
                });
        context(Map.of("JWT_SECRET", "config-import-jwt-test-secret",
                "UI_RELEASE_RESOLUTION_SECRET", "config-import-legacy-test-secret",
                "ENTITY_EXCEL_ACTION_SECRET", "config-import-action-test-secret",
                "ENTITY_EXCEL_FILE_SECRET", "config-import-file-test-secret"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var environment = context.getEnvironment();
                    assertThat(environment.resolveRequiredPlaceholders(
                            "${entity.excel.action-secret:${ui.release-resolution.secret:${jwt.secret}}}"))
                            .isEqualTo("config-import-action-test-secret");
                    assertThat(environment.resolveRequiredPlaceholders(
                            "${entity.excel.file-secret:${entity.excel.action-secret:${ui.release-resolution.secret:${jwt.secret}}}}"))
                            .isEqualTo("config-import-file-test-secret");
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
            assertThat(environment.getProperty("workflow.security.session.cookie-secure")).isEqualTo("true");
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
                "AUTH_SESSION_COOKIE_SECURE", "false",
                "FILE_STORAGE_S3_TRUST_ALL_CERTIFICATES", "true",
                "FILE_STORAGE_MINIO_TRUST_ALL_CERTIFICATES", "false",
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
                    assertThat(environment.getProperty("file.storage.s3.trust-all-certificates"))
                            .isEqualTo("true");
                    assertThat(environment.getProperty("file.storage.minio.trust-all-certificates"))
                            .isEqualTo("false");
                    assertThat(environment.getProperty("logging.level.com.workflow")).isEqualTo("DEBUG");
                    assertThat(environment.getProperty("mybatis-plus.configuration.log-impl"))
                            .isEqualTo("org.apache.ibatis.logging.stdout.StdOutImpl");
                    assertThat(environment.getProperty("jwt.secret")).isEqualTo("config-import-test-only-secret");
                    // HTTP 生产部署的显式选择必须覆盖生产 profile 默认值，才能下发浏览器可携带的刷新 Cookie。
                    assertThat(environment.getProperty("workflow.security.session.cookie-secure")).isEqualTo("false");
                    assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");
                });
    }
}
