package com.workflow.config.database;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.JdbcDatabaseClock;
import com.workflow.core.database.JdbcIdempotentInsert;
import com.workflow.core.database.JdbcLockedRow;
import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.core.database.DatabaseExceptionClassifier;
import com.workflow.core.database.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.error.DatabaseErrorDialect;
import com.workflow.integration.database.api.write.DatabaseInsertDialect;

import com.workflow.integration.database.api.schema.SchemaDdlDialect;
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
    /**
     * 处理数据库错误方言，并将结果传给后续步骤。
     *
     * @param dialect 方言，作为 {@code DatabaseDialects.errors} 的输入影响后续处理
     * @return 处理后的数据库错误方言结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseErrorDialect.class)
    public DatabaseErrorDialect databaseErrorDialect(SchemaDdlDialect dialect) {
        return DatabaseDialects.errors(dialect.vendor());
    }

    /**
     * 处理数据库异常{@code classifier}，并将结果传给后续步骤。
     *
     * @param dialect 方言，作为 {@code DatabaseExceptionClassifier} 的输入影响后续处理
     * @return 处理后的数据库异常{@code classifier}结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseExceptionClassifier.class)
    public DatabaseExceptionClassifier databaseExceptionClassifier(DatabaseErrorDialect dialect) {
        return new DatabaseExceptionClassifier(dialect);
    }

    /**
     * Boot 的 JdbcTemplate 自动装配会采用此翻译器，同时保留超时、fetchSize 等原有配置。
     *
     * @param classifier {@code classifier}，作为 {@code DatabaseSQLExceptionTranslator} 的输入影响后续处理
     * @return 处理后的数据库SQL异常{@code translator}结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(SQLExceptionTranslator.class)
    public SQLExceptionTranslator databaseSQLExceptionTranslator(DatabaseExceptionClassifier classifier) {
        return new DatabaseSQLExceptionTranslator(classifier);
    }

    /**
     * 处理JDBC写入{@code attempt}，并将结果传给后续步骤。
     *
     * @param jdbc JDBC，作为 {@code JdbcWriteAttempt} 的输入影响后续处理
     * @param dialect 方言，作为 {@code JdbcWriteAttempt} 的输入影响后续处理
     * @return 处理后的JDBC写入{@code attempt}结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(JdbcWriteAttempt.class)
    public JdbcWriteAttempt jdbcWriteAttempt(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        return new JdbcWriteAttempt(jdbc, dialect);
    }

    /**
     * 处理JDBC已锁定行，并将结果传给后续步骤。
     *
     * @param jdbc JDBC，作为 {@code JdbcLockedRow} 的输入影响后续处理
     * @param dialect 方言，作为 {@code JdbcLockedRow} 的输入影响后续处理
     * @return 处理后的JDBC已锁定行结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(JdbcLockedRow.class)
    public JdbcLockedRow jdbcLockedRow(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        return new JdbcLockedRow(jdbc, dialect);
    }

    /**
     * 处理数据库{@code insert}方言，并将结果传给后续步骤。
     *
     * @param dialect 方言，作为 {@code DatabaseDialects.insert} 的输入影响后续处理
     * @return 处理后的数据库{@code insert}方言结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseInsertDialect.class)
    public DatabaseInsertDialect databaseInsertDialect(SchemaDdlDialect dialect) {
        return DatabaseDialects.insert(dialect.vendor());
    }

    /**
     * 处理JDBC幂等{@code insert}，并将结果传给后续步骤。
     *
     * @param jdbc JDBC，作为 {@code JdbcIdempotentInsert} 的输入影响后续处理
     * @param dialect 方言，作为 {@code JdbcIdempotentInsert} 的输入影响后续处理
     * @return 处理后的JDBC幂等{@code insert}结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(JdbcIdempotentInsert.class)
    public JdbcIdempotentInsert jdbcIdempotentInsert(JdbcTemplate jdbc, DatabaseInsertDialect dialect) {
        return new JdbcIdempotentInsert(jdbc, dialect);
    }

    /**
     * 处理数据库时钟端口，并将结果传给后续步骤。
     *
     * @param jdbc JDBC，作为 {@code JdbcDatabaseClock} 的输入影响后续处理
     * @param dialect 方言，作为 {@code JdbcDatabaseClock} 的输入影响后续处理
     * @return 处理后的数据库时钟端口结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseClockPort.class)
    public DatabaseClockPort databaseClockPort(JdbcTemplate jdbc, SchemaDdlDialect dialect) {
        return new JdbcDatabaseClock(jdbc, dialect.vendor());
    }

    /**
     * 处理结构变更队列端口，并将结果传给后续步骤。
     *
     * @param connections {@code connections}，作为 {@code JdbcSchemaChangeQueue} 的输入影响后续处理
     * @param dialect 方言，作为 {@code JdbcSchemaChangeQueue} 的输入影响后续处理
     * @return 处理后的结构变更队列端口结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(SchemaChangeQueuePort.class)
    public SchemaChangeQueuePort schemaChangeQueuePort(DatabaseConnections connections, SchemaDdlDialect dialect) {
        return new JdbcSchemaChangeQueue(connections, dialect);
    }

    /**
     * 处理数据库锁定端口，并将结果传给后续步骤。
     *
     * @param connections {@code connections}，作为 {@code JdbcDatabaseLock} 的输入影响后续处理
     * @param dialect 方言，供本方法处理数据库锁定端口时使用
     * @return 处理后的数据库锁定端口结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(DatabaseLockPort.class)
    public DatabaseLockPort databaseLockPort(DatabaseConnections connections, SchemaDdlDialect dialect) {
        return new JdbcDatabaseLock(connections.application(), dialect.vendor());
    }

    /**
     * 处理结构元数据端口，并将结果传给后续步骤。
     *
     * @param jdbc JDBC，作为 {@code JdbcSchemaMetadata} 的输入影响后续处理
     * @param dialect 方言，作为 {@code JdbcSchemaMetadata} 的输入影响后续处理
     * @return 处理后的结构元数据端口结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(SchemaMetadataPort.class)
    public SchemaMetadataPort schemaMetadataPort(JdbcTemplate jdbc, SchemaDdlDialect dialect) {
        return new JdbcSchemaMetadata(jdbc, dialect);
    }

    /**
     * 处理结构DDL方言，并将结果传给后续步骤。
     *
     * @param vendor 供应商，作为 {@code DatabaseDialects.forVendor} 的输入影响后续处理
     * @param url URL，作为 {@code DatabaseDialects.forVendor} 的输入影响后续处理
     * @return 处理后的结构DDL方言结果，供调用方继续处理
     */
    @Bean
    @ConditionalOnMissingBean(SchemaDdlDialect.class)
    public SchemaDdlDialect schemaDdlDialect(@Value("${workflow.database.vendor:auto}") String vendor,
                                           @Value("${spring.datasource.url:}") String url) {
        return DatabaseDialects.forVendor(DatabaseDialects.resolve(vendor, url));
    }
}
