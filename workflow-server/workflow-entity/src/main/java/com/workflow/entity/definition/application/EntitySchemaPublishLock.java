package com.workflow.entity.definition.application;

import com.workflow.core.logging.LogValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Cross-pod, connection-scoped lock for entity schema publication.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntitySchemaPublishLock {

    private static final String LOCK_KEY_SQL =
            "CONCAT('flow:entity:', LEFT(SHA2(?, 256), 40))";
    private final JdbcTemplate jdbcTemplate;
    private final ThreadLocal<Connection> heldConnection = new ThreadLocal<>();

    public boolean tryAcquire(String entityId) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource != null) {
            try {
                Connection connection = dataSource.getConnection();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT GET_LOCK(" + LOCK_KEY_SQL + ", 0)")) {
                    statement.setString(1, entityId);
                    try (ResultSet result = statement.executeQuery()) {
                        boolean acquired = result.next() && result.getInt(1) == 1;
                        if (acquired) {
                            heldConnection.set(connection);
                        } else {
                            connection.close();
                        }
                        return acquired;
                    }
                }
            } catch (Exception exception) {
                throw new IllegalStateException("获取实体结构发布锁失败", exception);
            }
        }
        // 保留无 DataSource 的轻量测试适配；生产路径始终在同一连接持有与释放命名锁。
        Integer acquired = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(" + LOCK_KEY_SQL + ", 0)",
                Integer.class,
                entityId);
        return Integer.valueOf(1).equals(acquired);
    }

    public void release(String entityId) {
        Connection connection = heldConnection.get();
        if (connection != null) {
            heldConnection.remove();
            try (connection;
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT RELEASE_LOCK(" + LOCK_KEY_SQL + ")")) {
                statement.setString(1, entityId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        log.warn("Entity schema publish lock was not owned when released: entityId={}",
                                LogValue.safe(entityId));
                    }
                }
            } catch (Exception exception) {
                log.warn("Failed to explicitly release entity schema publish lock: entityId={}",
                        LogValue.safe(entityId), LogValue.failureType(exception));
            }
            return;
        }
        try {
            Integer released = jdbcTemplate.queryForObject(
                    "SELECT RELEASE_LOCK(" + LOCK_KEY_SQL + ")",
                    Integer.class,
                    entityId);
            if (!Integer.valueOf(1).equals(released)) {
                log.warn(
                        "Entity schema publish lock was not owned when released: entityId={}",
                        LogValue.safe(entityId));
            }
        } catch (RuntimeException exception) {
            // Connection loss releases MySQL named locks; do not mask the publish result.
            log.warn(
                    "Failed to explicitly release entity schema publish lock: entityId={}",
                    LogValue.safe(entityId),
                    LogValue.failureType(exception));
        }
    }
}
