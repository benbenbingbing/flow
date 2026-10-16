package com.workflow.process.instance.application;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 旧页面继续识别统一办理路径的驳回状态，重提成功后不残留可重提提示。 */
class ProcessRollbackStatusTest {
    @Test
    void readsCanonicalRejectionAndClearsAfterResubmit() {
        var runtime = mock(RuntimeService.class);
        var query = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        when(runtime.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mock(org.flowable.engine.runtime.ProcessInstance.class));
        var service = new ProcessRollbackService(runtime, null, null, null, null, null, null, null);
        when(runtime.getVariables("instance")).thenReturn(Map.of("approved", "reject", "rejectBy", "alice", "comment", "需补充"));
        var result = service.checkRejectedStatus("instance");
        assertEquals(true, result.get("canResubmit"));
        assertEquals("alice", result.get("rejectBy"));
        assertEquals("需补充", result.get("rejectComment"));
        when(runtime.getVariables("instance")).thenReturn(Map.of("approved", "approve", "_rejected_", false));
        assertNull(service.checkRejectedStatus("instance"));
    }

    @Test
    void retainsLegacyRejectionMetadataAndNeverOffersResubmitAfterEnd() {
        var runtime = mock(RuntimeService.class);
        var query = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        when(runtime.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(mock(org.flowable.engine.runtime.ProcessInstance.class));
        var service = new ProcessRollbackService(runtime, null, null, null, null, null, null, null);
        when(runtime.getVariables("instance")).thenReturn(Map.of("_rejected_", true, "_rejectBy_", "legacy", "_rejectComment_", "旧意见"));
        assertEquals("legacy", service.checkRejectedStatus("instance").get("rejectBy"));
        when(query.singleResult()).thenReturn(null);
        assertNull(service.checkRejectedStatus("instance"));
    }
}
