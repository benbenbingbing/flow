package com.workflow.process.publish;

import com.workflow.process.publish.application.ProcessUiReleaseBindingService;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.infrastructure.persistence.record.ProcessUiReleaseBinding;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.publish.infrastructure.persistence.mapper.ProcessUiReleaseBindingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessUiReleaseBindingServiceTest {
    @Test void indexesOnlyStableRootIdentityAndIgnoresOldReleaseCoordinates() {
        var mapper = mock(ProcessUiReleaseBindingMapper.class);
        var service = new ProcessUiReleaseBindingService(mapper);
        ProcessNodeForm root = node("task-root", "form-root");
        root.setFormReleaseId("old-release"); root.setFormReleaseVersion(7);
        assertEquals(1, service.replaceBindings(history(), List.of(root)));
        var row = ArgumentCaptor.forClass(ProcessUiReleaseBinding.class);
        verify(mapper).deleteByHistoryId("history-1"); verify(mapper).insert(row.capture());
        assertEquals("task-root", row.getValue().getNodeId());
        assertEquals("form-root", row.getValue().getConfigId());
        assertEquals("", row.getValue().getPinnedReleaseId());
        assertEquals(0, row.getValue().getPinnedReleaseVersion());
        assertEquals(3, row.getValue().getProcessVersion());
    }
    @Test void skipsEmptyFormsAndDeduplicatesSameNodeButKeepsOtherNodes() {
        var mapper = mock(ProcessUiReleaseBindingMapper.class);
        var service = new ProcessUiReleaseBindingService(mapper);
        assertEquals(2, service.replaceBindings(history(), List.of(
                node("a", "form"), node("a", "form"), node("b", "form"), node("c", ""))));
        verify(mapper,times(2)).insert(any(ProcessUiReleaseBinding.class));
    }
    @Test void invalidHistoryNeverDeletesExistingBindings() {
        var mapper = mock(ProcessUiReleaseBindingMapper.class);
        assertThrows(IllegalArgumentException.class, () -> new ProcessUiReleaseBindingService(mapper).replaceBindings(null,List.of()));
        verifyNoInteractions(mapper);
    }
    private ProcessVersionHistory history() {
        var row = new ProcessVersionHistory(); row.setId("history-1");
        row.setProcessConfigId("process-1"); row.setProcessKey("expense"); row.setVersion(3);
        return row;
    }
    private ProcessNodeForm node(String nodeId,String formId) {
        var row=new ProcessNodeForm(); row.setNodeId(nodeId); row.setFormId(formId); return row;
    }
}
