package com.workflow.listener;

import com.workflow.service.ProcessCcRuntimeService;
import com.workflow.service.cc.ProcessCcConfigService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 流程知会事件监听器单元测试。
 *
 * <p>被测对象为 {@link ProcessCcEventListener}，验证监听器在主事务提交后才触发，
 * 且异常不会导致工作流失败。</p>
 */
class ProcessCcEventListenerTest {
    /**
     * 监听器应在主事务提交后触发，且异常不应中断工作流。
     *
     * <p>断言 isFireOnTransactionLifecycleEvent 为 true、onTransaction 为 COMMITTED、
     * isFailOnException 为 false。</p>
     */
    @Test
    void dispatchesAfterMainTransactionCommitsWithoutFailingWorkflow() {
        ProcessCcEventListener listener = new ProcessCcEventListener(
                mock(ProcessCcRuntimeService.class),
                mock(ProcessCcConfigService.class),
                mock(RuntimeService.class),
                mock(HistoryService.class),
                mock(RepositoryService.class));

        assertTrue(listener.isFireOnTransactionLifecycleEvent());
        assertEquals("COMMITTED", listener.getOnTransaction());
        assertFalse(listener.isFailOnException());
    }
}
