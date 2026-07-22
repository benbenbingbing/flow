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

class ProcessCcEventListenerTest {
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
