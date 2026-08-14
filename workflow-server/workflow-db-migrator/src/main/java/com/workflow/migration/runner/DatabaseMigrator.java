package com.workflow.migration.runner;

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

    private DatabaseMigrator() {
    }

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
        System.out.println("Database migrations completed");
    }

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

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required");
        }
        return value;
    }

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
