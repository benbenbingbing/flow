package com.workflow.dbmigrator.runner;

import org.flowable.app.engine.AppEngine;
import org.flowable.app.engine.AppEngineConfiguration;
import org.flowable.cmmn.engine.CmmnEngine;
import org.flowable.cmmn.engine.CmmnEngineConfiguration;
import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.engine.DmnEngineConfiguration;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Runs business and Flowable schema migrations outside application Pods.
 */
public final class DatabaseMigrator {

    private static final String MYSQL_DRIVER =
            "com.mysql.cj.jdbc.Driver";

    /**
     * 初始化数据库{@code migrator}，保存构造参数供后续方法使用。
     */
    private DatabaseMigrator() {
    }

    /**
     * 启动数据库{@code migrator}；命令行参数决定后续执行环境。
     *
     * @param args {@code args}，供本方法处理主时使用
     */
    public static void main(String[] args) {
        if ("schema-worker".equalsIgnoreCase(
                System.getenv("MIGRATION_COMMAND"))) {
            new SchemaChangeWorker().run();
            return;
        }
        String jdbcUrl = required("SCHEMA_DATASOURCE_URL");
        String username = required("SCHEMA_DB_USERNAME");
        String password = required("SCHEMA_DB_PASSWORD");
        int connectRetries = nonNegativeInt(
                "MIGRATION_CONNECT_RETRIES",
                30);

        System.out.println(
                "Applying validated business schema migrations");
        verifyBusinessMigrationPreconditions(
                jdbcUrl, username, password);
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .placeholderReplacement(false)
                .connectRetries(connectRetries)
                .load()
                .migrate();

        System.out.println("Applying Flowable schema migrations");
        ProcessEngineConfiguration processConfiguration =
                ProcessEngineConfiguration
                        .createStandaloneProcessEngineConfiguration();
        configure(processConfiguration, jdbcUrl, username, password);
        processConfiguration.setAsyncExecutorActivate(false);
        ProcessEngine processEngine =
                processConfiguration.buildProcessEngine();
        processEngine.close();

        DmnEngineConfiguration dmnConfiguration =
                DmnEngineConfiguration
                        .createStandaloneDmnEngineConfiguration();
        configure(dmnConfiguration, jdbcUrl, username, password);
        DmnEngine dmnEngine = dmnConfiguration.buildDmnEngine();
        dmnEngine.close();

        CmmnEngineConfiguration cmmnConfiguration =
                CmmnEngineConfiguration
                        .createStandaloneCmmnEngineConfiguration();
        configure(cmmnConfiguration, jdbcUrl, username, password);
        CmmnEngine cmmnEngine =
                cmmnConfiguration.buildCmmnEngine();
        cmmnEngine.close();

        AppEngineConfiguration appConfiguration =
                AppEngineConfiguration
                        .createStandaloneAppEngineConfiguration();
        configure(appConfiguration, jdbcUrl, username, password);
        AppEngine appEngine = appConfiguration.buildAppEngine();
        appEngine.close();

        System.out.println(
                "Normalizing database character set and collation");
        normalizeDatabaseCollation(jdbcUrl, username, password);
        System.out.println("Database migrations completed");
    }

    /**
     * Flowable 的 MySQL 建表脚本显式使用 utf8/utf8_bin，因此必须在四个引擎
     * 完成 schema 更新并关闭后，再复用 V074 安装的过程收敛新增表。
     *
     * @param jdbcUrl JDBCURL，作为 {@code try} 的输入影响后续处理
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param password 密码，作为 {@code try} 的输入影响后续处理
     */
    private static void normalizeDatabaseCollation(
            String jdbcUrl,
            String username,
            String password) {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl, username, password);
             var statement = connection.prepareCall(
                     "{call workflow_v074_unify_database_collation_v2()}")) {
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "数据库字符集与排序规则统一失败",
                    exception);
        }
    }

    /**
     * 验证业务迁移{@code preconditions}；不满足约束时阻止后续处理。
     *
     * @param jdbcUrl JDBCURL，作为 {@code try} 的输入影响后续处理
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param password 密码，作为 {@code try} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static void verifyBusinessMigrationPreconditions(
            String jdbcUrl,
            String username,
            String password) {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl, username, password)) {
            BusinessMigrationPreflight.verify(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "业务数据库迁移预检失败",
                    exception);
        }
    }

    /**
     * 处理{@code configure}，并将结果传给后续步骤。
     *
     * @param configuration 配置内容，决定后续{@code configure}的处理规则
     * @param jdbcUrl JDBCURL，作为 {@code configuration.setJdbcUrl} 的输入影响后续处理
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param password 密码，作为 {@code configuration.setJdbcPassword} 的输入影响后续处理
     */
    private static void configure(
            AbstractEngineConfiguration configuration,
            String jdbcUrl,
            String username,
            String password) {
        configuration.setJdbcDriver(MYSQL_DRIVER);
        configuration.setJdbcUrl(jdbcUrl);
        configuration.setJdbcUsername(username);
        configuration.setJdbcPassword(password);
        configuration.setDatabaseSchemaUpdate(
                AbstractEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    }

    /**
     * 生成必填文本，供后续匹配或展示。
     *
     * @param name 名称，后续用于处理必填时匹配或展示
     * @return 处理后的必填文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required");
        }
        return value;
    }

    /**
     * 处理非{@code negative}整数，并将结果传给后续步骤。
     *
     * @param name 名称，后续用于处理非{@code negative}整数时匹配或展示
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 处理后的非{@code negative}整数结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private static int nonNegativeInt(
            String name,
            int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    name + " must be an integer",
                    exception);
        }
        if (parsed < 0 || parsed > 300) {
            throw new IllegalStateException(
                    name + " must be between 0 and 300");
        }
        return parsed;
    }
}
