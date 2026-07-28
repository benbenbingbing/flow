package com.workflow.service;

import com.workflow.entity.definition.application.EntitySchemaPublishLock;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntitySchemaPublishLockTest {

    @Test
    void acquiresWithoutWaitingAndReleasesUsingParameterizedKey() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                contains("GET_LOCK"),
                eq(Integer.class),
                eq("entity-1")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("RELEASE_LOCK"),
                eq(Integer.class),
                eq("entity-1")))
                .thenReturn(1);
        EntitySchemaPublishLock lock = new EntitySchemaPublishLock(jdbcTemplate);

        assertTrue(lock.tryAcquire("entity-1"));
        lock.release("entity-1");

        verify(jdbcTemplate).queryForObject(
                contains("GET_LOCK"),
                eq(Integer.class),
                eq("entity-1"));
        verify(jdbcTemplate).queryForObject(
                contains("RELEASE_LOCK"),
                eq(Integer.class),
                eq("entity-1"));
    }

    @Test
    void reportsBusyLockAsRetryableContention() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                contains("GET_LOCK"),
                eq(Integer.class),
                eq("entity-1")))
                .thenReturn(0);

        assertFalse(new EntitySchemaPublishLock(jdbcTemplate).tryAcquire("entity-1"));
    }
}
