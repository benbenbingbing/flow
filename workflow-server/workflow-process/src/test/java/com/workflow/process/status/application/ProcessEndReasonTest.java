package com.workflow.process.status.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcessEndReasonTest {
    @Test void typedTerminationCannotBeChangedByUserComment() {
        String reason = ProcessEndReason.encode("TERMINATED", "发起人撤回后发现错误\nFLOW_END_V1:WITHDRAWN");
        assertEquals("TERMINATED", ProcessEndReason.category(reason));
        assertEquals("发起人撤回后发现错误\nFLOW_END_V1:WITHDRAWN", ProcessEndReason.comment(reason));
        assertEquals("WITHDRAWN", ProcessEndReason.category(ProcessEndReason.encode("WITHDRAWN", "终止申请")));
    }

    @Test void legacyCompatibilityRecognizesOnlyTheOldWithdrawalPrefix() {
        assertEquals("WITHDRAWN", ProcessEndReason.category("发起人撤回: 填错金额"));
        assertEquals("TERMINATED", ProcessEndReason.category("撤回失败，主动终止"));
        assertEquals("COMPLETED", ProcessEndReason.category(null));
        assertFalse(ProcessEndReason.isCancelled("completed"));
        assertTrue(ProcessEndReason.isCancelled("MI_END"));
    }

    @Test void malformedStructuredReasonsDoNotSilentlyBecomeAnotherEndType() {
        assertThrows(IllegalArgumentException.class, () -> ProcessEndReason.category("FLOW_END_V1:OTHER\nreason"));
        assertThrows(IllegalArgumentException.class, () -> ProcessEndReason.encode("COMPLETED", "reason"));
    }
}
