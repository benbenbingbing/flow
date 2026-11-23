package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.port.process.ProcessActionDefinitionPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowActionDependenciesTest {
    @Test void rejectsCyclesCrossTimingCrossBindingDisabledAndSynchronousDependencies() {
        var a = action("a", null); var b = action("b", "a");
        assertDoesNotThrow(() -> FlowActionDependencies.validate(List.of(b, a)));
        a.setPredecessorActionId("b"); assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validate(List.of(a,b)));
        a.setPredecessorActionId(null); b.setPredecessorActionId("b"); assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validate(List.of(a,b)));
        b.setPredecessorActionId("a"); b.setTriggerTiming("TASK_CREATED"); assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validate(List.of(a,b)));
        b.setTriggerTiming(a.getTriggerTiming()); b.setElementId("other"); assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validate(List.of(a,b)));
        b.setElementId(null); a.setEnabled(false); assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validate(List.of(a,b)));
        a.setEnabled(true); b.setExecutionMode("IN_TRANSACTION"); assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validate(List.of(a,b)));
        assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validate(List.of(b)));
    }

    @Test void resultReferencesRequireExplicitDependencyAndSafeFieldPath() {
        var mapper = new ObjectMapper(); var b = action("b", "a");
        for (String path : List.of("contractNo", "contract.id", "items.0.id")) {
            b.setParamsJson("{\"id\":{\"$predecessor\":\"" + path + "\"}}");
            assertDoesNotThrow(() -> FlowActionDependencies.validateParams(b, mapper));
        }
        b.setPredecessorActionId(null); assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validateParams(b, mapper));
        b.setPredecessorActionId("a"); b.setParamsJson("{\"id\":{\"$predecessor\":\"getClass()\"}}");
        assertThrows(IllegalArgumentException.class, () -> FlowActionDependencies.validateParams(b, mapper));
    }

    @Test void publishingRemapsDependenciesForEachVersionAndNeverChangesDraftIds() {
        var mapper = mock(FlowActionMapper.class);
        var service = new FlowActionService(mapper, mock(FlowActionConfigurationValidator.class), mock(ProcessActionDefinitionPort.class));
        var a = action("a", null); var b = action("b", "a");
        when(mapper.findDraftActionsByProcessConfigId("process")).thenReturn(List.of(b,a));
        service.publishActions("process", "v1"); service.publishActions("process", "v2");
        var capture = ArgumentCaptor.forClass(FlowAction.class); verify(mapper, times(4)).insert(capture.capture());
        var published = capture.getAllValues();
        assertEquals(published.get(1).getId(), published.get(0).getPredecessorActionId());
        assertEquals(published.get(3).getId(), published.get(2).getPredecessorActionId());
        assertNotEquals(published.get(0).getPredecessorActionId(), published.get(2).getPredecessorActionId());
        assertEquals("a", b.getPredecessorActionId()); assertEquals("a", a.getId());
    }

    @Test void deletingOrDisablingPredecessorIsRejected() {
        var mapper = mock(FlowActionMapper.class);
        var service = new FlowActionService(mapper, mock(FlowActionConfigurationValidator.class), mock(ProcessActionDefinitionPort.class));
        var a = action("a", null); var b = action("b", "a"); a.setStatus("DRAFT");
        when(mapper.findDraftActionsByProcessConfigId("process")).thenReturn(List.of(a,b)); when(mapper.selectById("a")).thenReturn(a);
        assertThrows(IllegalArgumentException.class, () -> service.deleteAction("a"));
        assertThrows(IllegalArgumentException.class, () -> service.toggleEnabled("a"));
        verify(mapper, never()).logicDeleteById("a");
    }

    private FlowAction action(String id, String predecessor) {
        var action = new FlowAction(); action.setId(id); action.setPredecessorActionId(predecessor);
        action.setProcessConfigId("process"); action.setScopeType("PROCESS"); action.setTriggerTiming("PROCESS_COMPLETED");
        action.setExecutionMode("AFTER_COMMIT"); action.setEnabled(true); return action;
    }
}
