package com.workflow.service.permission;

import com.workflow.entity.permission.application.EntityListScopeAuditService;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EntityListScopeAuditServiceTest {

    @Test
    void writesAuditRecordsInAnIndependentTransaction() throws Exception {
        Method record = EntityListScopeAuditService.class.getMethod(
                "record",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Object.class);

        Transactional transactional =
                AnnotatedElementUtils.findMergedAnnotation(
                        record,
                        Transactional.class);

        assertNotNull(transactional);
        assertEquals(
                Propagation.REQUIRES_NEW,
                transactional.propagation());
    }
}
