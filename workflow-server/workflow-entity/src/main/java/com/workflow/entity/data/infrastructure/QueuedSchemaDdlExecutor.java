package com.workflow.entity.data.infrastructure;

import com.workflow.entity.data.application.SchemaDdlExecutor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Publishes validated DDL to the fenced schema worker and waits for completion.
 */
@Component
@ConditionalOnProperty(
        name = "workflow.schema-publisher.mode",
        havingValue = "queue")
public class QueuedSchemaDdlExecutor implements SchemaDdlExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final Duration timeout;
    private final Duration pollInterval;

    public QueuedSchemaDdlExecutor(
            @Value("${spring.datasource.url}")
            String url,
            @Value("${spring.datasource.username}")
            String username,
            @Value("${spring.datasource.password}")
            String password,
            @Value("${workflow.schema-publisher.wait-timeout:120s}")
            Duration timeout,
            @Value("${workflow.schema-publisher.poll-interval:500ms}")
            Duration pollInterval) {
        requireText(url, "SPRING_DATASOURCE_URL");
        requireText(username, "DB_USERNAME");
        requireText(password, "DB_PASSWORD");
        requirePositive(timeout, "wait-timeout");
        requirePositive(pollInterval, "poll-interval");
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource();
        dataSource.setDriverClassName(
                "com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.timeout = timeout;
        this.pollInterval = pollInterval;
    }

    @Override
    public void execute(String ddl) {
        requireText(ddl, "DDL statement");
        String hash = sha256(ddl);
        String requestId = findRequestId(hash);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            try {
                jdbcTemplate.update(
                        """
                        INSERT INTO workflow_schema_change
                            (id, ddl_hash, active_hash, ddl_statement, status,
                             attempt, lease_token, next_attempt_at,
                             create_time, update_time)
                        VALUES (?, ?, ?, ?, 'PENDING',
                                0, 0, UTC_TIMESTAMP(6),
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                        """,
                        requestId,
                        hash,
                        hash,
                        ddl);
            } catch (DuplicateKeyException exception) {
                requestId = findRequestId(hash);
            }
        }
        if (requestId == null) {
            throw new IllegalStateException(
                    "Schema change request could not be created");
        }
        awaitCompletion(requestId);
    }

    private void awaitCompletion(String requestId) {
        long deadline = System.nanoTime()
                + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            SchemaChangeState state = jdbcTemplate.query(
                    """
                    SELECT status, last_error
                    FROM workflow_schema_change
                    WHERE id = ?
                    """,
                    resultSet -> resultSet.next()
                            ? new SchemaChangeState(
                                    resultSet.getString("status"),
                                    resultSet.getString("last_error"))
                            : null,
                    requestId);
            if (state == null) {
                throw new IllegalStateException(
                        "Schema change request disappeared");
            }
            if ("APPLIED".equals(state.status())) {
                return;
            }
            if ("FAILED".equals(state.status())) {
                throw new IllegalStateException(
                        "Schema change failed: "
                                + safeError(state.error()));
            }
            sleep();
        }
        throw new IllegalStateException(
                "Timed out waiting for schema worker");
    }

    private String findRequestId(String hash) {
        return jdbcTemplate.query(
                """
                SELECT id
                FROM workflow_schema_change
                WHERE active_hash = ?
                  AND status IN ('PENDING', 'RUNNING')
                ORDER BY create_time DESC
                LIMIT 1
                """,
                resultSet -> resultSet.next()
                        ? resultSet.getString("id")
                        : null,
                hash);
    }

    private void sleep() {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for schema worker",
                    exception);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception);
        }
    }

    private static void requireText(
            String value,
            String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    label + " is required");
        }
    }

    private static void requirePositive(
            Duration value,
            String label) {
        if (value == null
                || value.isZero()
                || value.isNegative()) {
            throw new IllegalStateException(
                    label + " must be positive");
        }
    }

    private static String safeError(String error) {
        return StringUtils.hasText(error)
                ? error
                : "schema worker reported an unknown error";
    }

    private record SchemaChangeState(
            String status,
            String error) {
    }
}
