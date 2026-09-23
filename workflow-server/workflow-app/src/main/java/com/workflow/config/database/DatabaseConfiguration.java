package com.workflow.config.database;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.JdbcDatabaseClock;
import com.workflow.core.database.JdbcIdempotentInsert;
import com.workflow.core.database.JdbcLockedRow;
import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.core.database.DatabaseExceptionClassifier;
import com.workflow.core.database.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.DatabaseErrorDialect;
import com.workflow.integration.database.api.DatabaseInsertDialect;

import com.workflow.integration.database.api.SchemaDdlDialect;
import com.workflow.core.database.port.SchemaMetadataPort;
import com.workflow.core.database.port.DatabaseConnections;
import com.workflow.core.database.port.DatabaseLockPort;
import com.workflow.core.database.port.DatabaseClockPort;
import com.workflow.entity.data.infrastructure.schema.SchemaChangeQueuePort;
import com.workflow.entity.data.infrastructure.schema.JdbcSchemaChangeQueue;
import com.workflow.core.database.lock.JdbcDatabaseLock;
import com.workflow.core.database.schema.JdbcSchemaMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 应用选择 integration 的纯方言，并装配 core/业务模块负责执行的基础设施。 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfiguration {
    @Bean
    @ConditionalOnMissingBean(DatabaseErrorDialect.class)
    public DatabaseErrorDialect databaseErrorDialect(SchemaDdlDialect dialect) {
        return DatabaseDialects.errors(dialect.vendor());
    }

    @Bean
    @ConditionalOnMissingBean(DatabaseExceptionClassifier.class)
    public DatabaseExceptionClassifier databaseExceptionClassifier(DatabaseErrorDialect dialect) {
        return new DatabaseExceptionClassifier(dialect);
    }

    /** Boot 的 JdbcTemplate 自动装配会采用此翻译器，同时保留超时、fetchSize 等原有配置。 */
    @Bean
    @ConditionalOnMissingBean(SQLExceptionTranslator.class)
    public SQLExceptionTranslator databaseSQLExceptionTranslator(DatabaseExceptionClassifier classifier) {
        return new DatabaseSQLExceptionTranslator(classifier);
    }

    @Bean
    @ConditionalOnMissingBean(JdbcWriteAttempt.class)
    public JdbcWriteAttempt jdbcWriteAttempt(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        return new JdbcWriteAttempt(jdbc, dialect);
    }

    @Bean
    @ConditionalOnMissingBean(JdbcLockedRow.class)
    public JdbcLockedRow jdbcLockedRow(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        return new JdbcLockedRow(jdbc, dialect);
    }

    @Bean
    @ConditionalOnMissingBean(DatabaseInsertDialect.class)
    public DatabaseInsertDialect databaseInsertDialect(SchemaDdlDialect dialect) {
        return DatabaseDialects.insert(dialect.vendor());
    }

    @Bean
    @ConditionalOnMissingBean(JdbcIdempotentInsert.class)
    public JdbcIdempotentInsert jdbcIdempotentInsert(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        return new JdbcIdempotentInsert(jdbc, dialect);
    }

    @Bean
    @ConditionalOnMissingBean(DatabaseClockPort.class)
    public DatabaseClockPort databaseClockPort(JdbcTemplate jdbc, SchemaDdlDialect dialect) {
        return new JdbcDatabaseClock(jdbc, dialect.vendor());
    }

    @Bean
    @ConditionalOnMissingBean(SchemaChangeQueuePort.class)
    public SchemaChangeQueuePort schemaChangeQueuePort(DatabaseConnections connections, SchemaDdlDialect dialect) {
        return new JdbcSchemaChangeQueue(connections, dialect);
    }

    @Bean
    @ConditionalOnMissingBean(DatabaseLockPort.class)
    public DatabaseLockPort databaseLockPort(DatabaseConnections connections, SchemaDdlDialect dialect) {
        return new JdbcDatabaseLock(connections.application(), dialect.vendor());
    }

    @Bean
    @ConditionalOnMissingBean(SchemaMetadataPort.class)
    public SchemaMetadataPort schemaMetadataPort(JdbcTemplate jdbc, SchemaDdlDialect dialect) {
        return new JdbcSchemaMetadata(jdbc, dialect);
    }

    @Bean
    @ConditionalOnMissingBean(SchemaDdlDialect.class)
    public SchemaDdlDialect schemaDdlDialect(@Value("${workflow.database.vendor:auto}") String vendor,
                                           @Value("${spring.datasource.url:}") String url) {
        return DatabaseDialects.forVendor(DatabaseDialects.resolve(vendor, url));
    }
}
