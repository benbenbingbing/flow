package com.workflow.config;

import com.workflow.runner.DeptIdDataFixRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 启动数据修复 Runner 单元测试。
 *
 * <p>被测对象为 {@link DeptIdDataFixRunner}，验证其仅在配置项
 * workflow.data-fix.dept-id.enabled=true 时才执行。</p>
 */
class StartupDataFixRunnerTest {

    /**
     * 部门 ID 数据修复 Runner 应仅在配置启用时执行。
     *
     * <p>反射读取 @ConditionalOnProperty 注解，断言前缀为 workflow.data-fix.dept-id、
     * 名称为 enabled、值为 true、缺失时默认不匹配。</p>
     */
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
