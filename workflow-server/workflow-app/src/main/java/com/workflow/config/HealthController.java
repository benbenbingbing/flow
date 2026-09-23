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

    /**
     * 整理{@code live}数据，供调用方遍历或继续处理。
     *
     * @return {@code live}键值结果，供调用方继续处理
     */
    @GetMapping("/livez")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }

    /**
     * 处理{@code health}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code health}结果，供调用方继续处理
     */
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

    /**
     * 判断初始化就绪条件是否成立，供调用方选择后续分支。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @return 初始化就绪条件成立时为 true，否则为 false
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
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
