package com.workflow.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Container health endpoint that also verifies the database connection.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;

    @GetMapping("/livez")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                if (bootstrapReady(connection)) {
                    return ResponseEntity.ok(Map.of("status", "UP"));
                }
                return ResponseEntity.status(
                                HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "status",
                                "BOOTSTRAPPING"));
            }
        } catch (SQLException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "DOWN"));
    }

    private boolean bootstrapReady(Connection connection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM workflow_bootstrap_job
                WHERE (job_name = 'system-entity-catalog'
                         AND completed_version >= 1)
                   OR (job_name = 'entity-permission-catalog'
                         AND completed_version >= 1)
                   OR (job_name = 'bootstrap-administrator'
                         AND completed_version >= 1)
                """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) == 3;
        }
    }
}
