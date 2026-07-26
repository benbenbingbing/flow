package com.workflow.listener;

import com.workflow.entity.policy.EntityProcessStatusPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程结束监听器单元测试。
 *
 * <p>被测对象为 {@link ProcessEndListener}，验证实体状态在流程结束时
 * 是否需要保留(取决于状态分类是否匹配流程结束分类)。</p>
 */
class ProcessEndListenerTest {

    /**
     * 实体状态分类与流程结束分类匹配时应保留显式状态。
     *
     * <p>场景：状态分类为 COMPLETED，断言 shouldPreserveStatus 返回 true。</p>
     */
    @Test
    void preservesExplicitStatusWhenCategoryMatchesProcessEnd() {
        assertTrue(EntityProcessStatusPolicy.shouldPreserve("COMPLETED", "COMPLETED"));
    }

    /**
     * 实体状态分类与流程结束分类不匹配时应替换状态。
     *
     * <p>场景：状态分类为 PROCESSING，断言 shouldPreserveStatus 返回 false。</p>
     */
    @Test
    void replacesStatusWhenCategoryDoesNotMatchProcessEnd() {
        assertFalse(EntityProcessStatusPolicy.shouldPreserve("PROCESSING", "COMPLETED"));
    }
}
