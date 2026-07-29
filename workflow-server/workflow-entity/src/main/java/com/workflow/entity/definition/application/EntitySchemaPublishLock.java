package com.workflow.entity.definition.application;

import com.workflow.core.logging.LogValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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

    public boolean tryAcquire(String entityId) {
        Integer acquired = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(" + LOCK_KEY_SQL + ", 0)",
                Integer.class,
                entityId);
        return Integer.valueOf(1).equals(acquired);
    }

    public void release(String entityId) {
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
