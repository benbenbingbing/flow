package com.workflow.config;

import com.workflow.runner.DeptIdDataFixRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StartupDataFixRunnerTest {

    @Test
    void fullTestProcessFixRunsOnlyWhenEnabled() {
        ConditionalOnProperty condition = DataFixRunner.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(condition);
        assertEquals("workflow.data-fix.full-test-process", condition.prefix());
        assertArrayEquals(new String[]{"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
        assertEquals(false, condition.matchIfMissing());
    }

    @Test
    void deptIdFixRunsOnlyWhenEnabled() {
        ConditionalOnProperty condition = DeptIdDataFixRunner.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(condition);
        assertEquals("workflow.data-fix.dept-id", condition.prefix());
        assertArrayEquals(new String[]{"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
        assertEquals(false, condition.matchIfMissing());
    }
}
