package com.workflow.migration.runner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claims and applies dynamic business-table DDL with a dedicated identity.
 */
final class SchemaChangeWorker {

    private static final int MAX_ATTEMPTS = 5;
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?is)^(?:CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?"
                    + "|ALTER\\s+TABLE"
                    + "|DROP\\s+TABLE(?:\\s+IF\\s+EXISTS)?)"
                    + "\\s+`(biz_[a-z0-9_]{1,58})`"
                    + "(?=\\s|\\(|$)");
    private static final Pattern INDEX_PATTERN = Pattern.compile(
            "(?is)^CREATE\\s+(?:UNIQUE\\s+)?INDEX"
                    + "\\s+`[a-z][a-z0-9_]{0,62}`"
                    + "\\s+ON\\s+`(biz_[a-z0-9_]{1,58})`"
                    + "(?=\\s|\\(|$)");
    private static final Duration POLL_INTERVAL =
            Duration.ofMillis(500);
    private final String jdbcUrl =
            required("SCHEMA_DATASOURCE_URL");
    private final String username =
            required("SCHEMA_DB_USERNAME");
    private final String password =
            required("SCHEMA_DB_PASSWORD");
    private final String ownerId =
            environment(
                    "SCHEMA_WORKER_ID",
                    "schema-worker-" + UUID.randomUUID());
    private volatile boolean running = true;

    void run() {
        Runtime.getRuntime().addShutdownHook(
                new Thread(
                        () -> running = false,
                        "schema-worker-shutdown"));
        System.out.println(
                "Schema change worker started: owner=" + ownerId);
        while (running) {
            try {
                Claim claim = claim();
                if (claim == null) {
                    sleep();
                    continue;
                }
                apply(claim);
            } catch (SQLException exception) {
                System.err.println(
                        "Schema worker database operation failed: "
                                + safeMessage(exception));
                sleep();
            }
        }
    }

    private Claim claim() throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select =
                    connection.prepareStatement(
                            """
                            SELECT id, ddl_statement, lease_token, attempt
                            FROM workflow_schema_change
                            WHERE status IN ('PENDING', 'RUNNING')
                              AND attempt < ?
                              AND next_attempt_at <= UTC_TIMESTAMP(6)
                              AND (status = 'PENDING'
                                   OR lease_until < UTC_TIMESTAMP(6))
                            ORDER BY create_time
                            LIMIT 1
                            FOR UPDATE SKIP LOCKED
                            """)) {
                select.setInt(1, MAX_ATTEMPTS);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) {
                        connection.commit();
                        return null;
                    }
                    String id = rows.getString("id");
                    String ddl = rows.getString("ddl_statement");
                    long token = rows.getLong("lease_token") + 1;
                    int attempt = rows.getInt("attempt") + 1;
                    try (PreparedStatement update =
                            connection.prepareStatement(
                                    """
                                    UPDATE workflow_schema_change
                                    SET status = 'RUNNING',
                                        owner_id = ?,
                                        lease_token = ?,
                                        lease_until = DATE_ADD(
                                            UTC_TIMESTAMP(6),
                                            INTERVAL 120 SECOND),
                                        attempt = ?,
                                        last_error = NULL,
                                        update_time = UTC_TIMESTAMP(6)
                                    WHERE id = ?
                                    """)) {
                        update.setString(1, ownerId);
                        update.setLong(2, token);
                        update.setInt(3, attempt);
                        update.setString(4, id);
                        update.executeUpdate();
                    }
                    connection.commit();
                    return new Claim(
                            id,
                            ddl,
                            token,
                            attempt);
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void apply(Claim claim) {
        try (LeaseHeartbeat heartbeat = new LeaseHeartbeat(claim)) {
            validate(claim.ddl());
            try (Connection connection = connection();
                 Statement statement =
                         connection.createStatement()) {
                statement.execute(claim.ddl());
            }
            complete(claim);
        } catch (SQLException exception) {
            if (isIdempotentReplay(exception)) {
                complete(claim);
                return;
            }
            failOrRetry(claim, safeMessage(exception));
        } catch (RuntimeException exception) {
            failOrRetry(claim, safeMessage(exception));
        }
    }

    private void complete(Claim claim) {
        fencedUpdate(
                claim,
                """
                UPDATE workflow_schema_change
                SET status = 'APPLIED',
                    active_hash = NULL,
                    lease_until = NULL,
                    last_error = NULL,
                    completed_time = UTC_TIMESTAMP(6),
                    update_time = UTC_TIMESTAMP(6)
                WHERE id = ?
                  AND owner_id = ?
                  AND lease_token = ?
                  AND status = 'RUNNING'
                """,
                false,
                null);
    }

    private void failOrRetry(
            Claim claim,
            String error) {
        boolean terminal = claim.attempt() >= MAX_ATTEMPTS;
        fencedUpdate(
                claim,
                """
                UPDATE workflow_schema_change
                SET status = ?,
                    active_hash = CASE WHEN ? = 'FAILED'
                                       THEN NULL ELSE active_hash END,
                    lease_until = NULL,
                    last_error = ?,
                    next_attempt_at = DATE_ADD(
                        UTC_TIMESTAMP(6),
                        INTERVAL ? SECOND),
                    update_time = UTC_TIMESTAMP(6)
                WHERE id = ?
                  AND owner_id = ?
                  AND lease_token = ?
                  AND status = 'RUNNING'
                """,
                true,
                new Failure(
                        terminal ? "FAILED" : "PENDING",
                        truncate(error, 1000),
                        Math.min(
                                300,
                                5 * (1 << Math.min(
                                        claim.attempt() - 1,
                                        6)))));
    }

    private void fencedUpdate(
            Claim claim,
            String sql,
            boolean failure,
            Failure failureValues) {
        try (Connection connection = connection();
             PreparedStatement update =
                     connection.prepareStatement(sql)) {
            int offset = 1;
            if (failure) {
                update.setString(
                        offset++,
                        failureValues.status());
                update.setString(
                        offset++,
                        failureValues.status());
                update.setString(
                        offset++,
                        failureValues.error());
                update.setInt(
                        offset++,
                        failureValues.retrySeconds());
            }
            update.setString(offset++, claim.id());
            update.setString(offset++, ownerId);
            update.setLong(offset, claim.token());
            int updated = update.executeUpdate();
            if (updated != 1) {
                System.err.println(
                        "Schema worker lost lease before ACK: id="
                                + claim.id());
            }
        } catch (SQLException exception) {
            System.err.println(
                    "Schema worker could not persist result: "
                            + safeMessage(exception));
        }
    }

    private final class LeaseHeartbeat implements AutoCloseable {

        private final Claim claim;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final ScheduledExecutorService scheduler;

        private LeaseHeartbeat(Claim claim) {
            this.claim = claim;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(
                        task,
                        "schema-worker-lease-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
            scheduler.scheduleAtFixedRate(
                    this::renew,
                    30,
                    30,
                    TimeUnit.SECONDS);
        }

        private void renew() {
            if (!open.get()) {
                return;
            }
            try (Connection connection = connection();
                 PreparedStatement update = connection.prepareStatement("""
                         UPDATE workflow_schema_change
                         SET lease_until = DATE_ADD(
                               UTC_TIMESTAMP(6),
                               INTERVAL 120 SECOND),
                             update_time = UTC_TIMESTAMP(6)
                         WHERE id = ?
                           AND owner_id = ?
                           AND lease_token = ?
                           AND status = 'RUNNING'
                         """)) {
                update.setString(1, claim.id());
                update.setString(2, ownerId);
                update.setLong(3, claim.token());
                if (update.executeUpdate() != 1) {
                    open.set(false);
                    System.err.println(
                            "Schema worker lost lease during DDL: id="
                                    + claim.id());
                }
            } catch (SQLException exception) {
                System.err.println(
                        "Schema worker could not renew lease: "
                                + safeMessage(exception));
            }
        }

        @Override
        public void close() {
            open.set(false);
            scheduler.shutdownNow();
        }
    }

    static void validate(String ddl) {
        if (ddl == null
                || ddl.isBlank()
                || ddl.indexOf('\0') >= 0
                || ddl.contains("--")
                || ddl.contains("/*")
                || ddl.contains("*/")) {
            throw new IllegalArgumentException(
                    "Schema DDL contains forbidden input");
        }
        String trimmed = ddl.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(
                    0,
                    trimmed.length() - 1).trim();
        }
        if (trimmed.contains(";")) {
            throw new IllegalArgumentException(
                    "Schema worker accepts one statement");
        }
        Matcher table = TABLE_PATTERN.matcher(trimmed);
        Matcher index = INDEX_PATTERN.matcher(trimmed);
        if (!table.find() && !index.find()) {
            throw new IllegalArgumentException(
                    "Schema DDL is not an allowed biz_ table operation");
        }
    }

    private boolean isIdempotentReplay(
            SQLException exception) {
        return switch (exception.getErrorCode()) {
            case 1050, 1060, 1061, 1091 -> true;
            default -> false;
        };
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                username,
                password);
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is required");
        }
        return value;
    }

    private static String environment(
            String name,
            String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank()
                ? fallback
                : value;
    }

    private static String safeMessage(
            Throwable throwable) {
        String message = throwable.getMessage();
        return truncate(
                message == null
                        ? throwable.getClass()
                                .getSimpleName()
                        : message,
                1000);
    }

    private static String truncate(
            String value,
            int limit) {
        return value.length() <= limit
                ? value
                : value.substring(0, limit);
    }

    private record Claim(
            String id,
            String ddl,
            long token,
            int attempt) {
    }

    private record Failure(
            String status,
            String error,
            int retrySeconds) {
    }
}
