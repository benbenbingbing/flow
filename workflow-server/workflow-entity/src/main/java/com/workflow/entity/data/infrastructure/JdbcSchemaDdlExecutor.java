package com.workflow.entity.data.infrastructure;

import com.workflow.entity.data.application.SchemaDdlExecutor;
import com.workflow.entity.data.application.SchemaDdlPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    public JdbcSchemaDdlExecutor(
            @Value("${workflow.schema-publisher.datasource.url}") String url,
            @Value("${workflow.schema-publisher.datasource.username}") String username,
            @Value("${workflow.schema-publisher.datasource.password}") String password) {
        requireText(url, "SCHEMA_DATASOURCE_URL");
        requireText(username, "SCHEMA_DB_USERNAME");
        requireText(password, "SCHEMA_DB_PASSWORD");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void execute(String ddl) {
        SchemaDdlPolicy.requireSafe(ddl);
        // The policy permits only one generated schema DDL statement before this sink.
        // codeql[java/concatenated-sql-query]
        jdbcTemplate.execute(ddl);
    }

    private static void requireText(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentVariable + " is required");
        }
    }
}
