package com.workflow.entity.data.infrastructure;

import com.workflow.core.database.DatabaseExceptionClassifier;
import com.workflow.core.database.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.DatabaseDialects;

import com.workflow.entity.data.application.SchemaDdlExecutor;
import com.workflow.integration.database.api.SchemaDdlDialect;
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

    /** 连接由应用基础设施装配；integration 方言只校验/生成 SQL，不建立连接。 */
    public JdbcSchemaDdlExecutor(DatabaseConnections connections, SchemaDdlDialect dialect) {
        this.jdbcTemplate = new JdbcTemplate(connections.schema());
        this.jdbcTemplate.setExceptionTranslator(new DatabaseSQLExceptionTranslator(
                new DatabaseExceptionClassifier(DatabaseDialects.errors(dialect.vendor()))));
        this.dialect = dialect;
    }

    @Override
    @SuppressWarnings("lgtm [java/concatenated-sql-query]")
    public void execute(String ddl) {
        dialect.validateStatement(ddl);
        jdbcTemplate.execute(ddl);
    }

}
