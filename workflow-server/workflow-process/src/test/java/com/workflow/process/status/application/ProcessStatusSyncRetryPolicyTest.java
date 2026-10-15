package com.workflow.process.status.application;

import com.workflow.core.error.BusinessConflictException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessStatusSyncRetryPolicyTest {
    private final ProcessStatusSyncOutboxHandler handler =
            new ProcessStatusSyncOutboxHandler(null, null, null, null, null, null);

    @Test
    void staleVersionScopeRequiresExplicitRecoveryEvenWhenWrapped() {
        var stale = new BusinessConflictException("ENTITY_VERSION_SCOPE_STALE", "需重新保存配置");
        assertFalse(handler.retryable(stale));
        assertFalse(handler.retryable(new IllegalStateException("wrapped", stale)));
        // 中文文案相同不应误判；错误码以外的业务冲突仍可能通过退避恢复。
        assertTrue(handler.retryable(new BusinessConflictException("OTHER_CONFLICT", stale.getMessage())));
        assertTrue(handler.retryable(new IllegalStateException("temporary database failure")));
    }

    @Test
    void cyclicCauseChainDoesNotHangFailureHandling() {
        var first = new IllegalStateException("first");
        var second = new IllegalStateException("second", first);
        first.initCause(second);
        assertTrue(handler.retryable(first));
    }
}
