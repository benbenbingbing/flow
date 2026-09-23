package com.workflow.config.database;

import com.workflow.integration.database.api.DatabaseJdbcProfiles;
import com.workflow.core.database.InitializedDriverDataSource;

import com.workflow.core.database.port.DatabaseConnections;
import com.workflow.integration.database.api.SchemaDdlDialect;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

/** 业务池与专用连接共享产品配置；专用连接不注册为 DataSource bean，避免影响事务管理器选取。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
public class DatabaseDataSourceConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(DataSource.class)
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties, SchemaDdlDialect dialect) {
        DatabaseJdbcProfiles.requireCompatibleUrl(dialect.vendor(), properties.getUrl());
        var pool = new HikariDataSource();
        pool.setJdbcUrl(properties.getUrl());
        pool.setUsername(properties.getUsername());
        pool.setPassword(properties.getPassword());
        pool.setDriverClassName(DatabaseJdbcProfiles.driver(properties.getUrl(), properties.getDriverClassName()));
        pool.setConnectionInitSql(DatabaseJdbcProfiles.connectionInitSql(dialect.vendor()));
        return pool;
    }

    @Bean
    @ConditionalOnMissingBean(DatabaseConnections.class)
    public DatabaseConnections databaseConnections(DataSourceProperties properties, SchemaDdlDialect dialect,
            @Value("${workflow.schema-publisher.datasource.url:}") String schemaUrl,
            @Value("${workflow.schema-publisher.datasource.username:}") String schemaUser,
            @Value("${workflow.schema-publisher.datasource.password:}") String schemaPassword,
            @Value("${workflow.schema-publisher.datasource.driver-class-name:}") String schemaDriver,
            @Value("${spring.datasource.hikari.connection-init-sql:}") String configuredInitSql) {
        String initSql = configuredInitSql.isBlank() ? DatabaseJdbcProfiles.connectionInitSql(dialect.vendor()) : configuredInitSql;
        DataSource application = new InitializedDriverDataSource(properties.getUrl(), properties.getUsername(), properties.getPassword(),
                properties.getDriverClassName(), initSql);
        return new DatabaseConnections() {
            @Override public DataSource application() { return application; }
            @Override public DataSource schema() {
                // 与原专用 DDL 入口保持一致，不允许误用空身份执行结构发布。
                if (schemaPassword.isBlank()) throw new IllegalStateException("SCHEMA_DB_PASSWORD is required");
                DatabaseJdbcProfiles.requireCompatibleUrl(dialect.vendor(), schemaUrl);
                return new InitializedDriverDataSource(schemaUrl, schemaUser, schemaPassword, schemaDriver, initSql);
            }
        };
    }
}
