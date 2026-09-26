package com.workflow.entity.data.infrastructure.schema;

import com.workflow.core.database.jdbc.DatabaseExceptionClassifier;
import com.workflow.core.database.jdbc.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.DatabaseDialects;

import com.workflow.entity.data.application.port.SchemaDdlExecutor;
import com.workflow.integration.database.api.schema.SchemaDdlDialect;
import com.workflow.core.database.port.DatabaseConnections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dedicated, unpooled connection for infrequent schema publication.
 */
@Component
@ConditionalOnProperty(
        name = "workflow.schema-publisher.mode",
        havingValue = "direct",
        matchIfMissing = true)
public class JdbcSchemaDdlExecutor implements SchemaDdlExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final SchemaDdlDialect dialect;

    /**
     * 连接由应用基础设施装配；integration 方言只校验/生成 SQL，不建立连接。
     *
     * @param connections {@code connections}，保存在对象中供后续校验、查询或展示
     * @param dialect 方言依赖，保存到当前对象供后续业务方法调用
     */
    public JdbcSchemaDdlExecutor(DatabaseConnections connections, SchemaDdlDialect dialect) {
        this.jdbcTemplate = new JdbcTemplate(connections.schema());
        this.jdbcTemplate.setExceptionTranslator(new DatabaseSQLExceptionTranslator(
                new DatabaseExceptionClassifier(DatabaseDialects.errors(dialect.vendor()))));
        this.dialect = dialect;
    }

    /**
     * 执行JDBC结构DDL执行器，并将结果传给后续步骤。
     *
     * @param ddl DDL，作为 {@code dialect.validateStatement} 的输入影响后续处理
     */
    @Override
    @SuppressWarnings("lgtm [java/concatenated-sql-query]")
    public void execute(String ddl) {
        dialect.validateStatement(ddl);
        jdbcTemplate.execute(ddl);
    }

}
