package com.workflow.listener;

import com.workflow.entity.EntityStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessEndListenerTest {

    @Test
    void preservesExplicitStatusWhenCategoryMatchesProcessEnd() {
        EntityStatus status = new EntityStatus();
        status.setStatusCode("FINAL_SPECIAL");
        status.setStatusCategory("COMPLETED");

        assertTrue(ProcessEndListener.shouldPreserveStatus(status, "COMPLETED"));
    }

    @Test
    void replacesStatusWhenCategoryDoesNotMatchProcessEnd() {
        EntityStatus status = new EntityStatus();
        status.setStatusCode("IN_REVIEW");
        status.setStatusCategory("PROCESSING");

        assertFalse(ProcessEndListener.shouldPreserveStatus(status, "COMPLETED"));
    }
}
