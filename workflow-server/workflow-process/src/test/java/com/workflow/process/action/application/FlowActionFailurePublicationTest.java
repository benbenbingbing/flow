package com.workflow.process.action.application;

import com.workflow.contracts.process.action.model.FlowActionDefinitionDescriptor;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.process.action.api.request.FlowActionSaveRequest;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 防止新增策略字段只存在草稿中，发布时遗漏后悄悄丢失失败处理规则。 */
class FlowActionFailurePublicationTest {
    @Test void savingAndPublishingCopyCustomStrategyVersionAndParameters() {
        var mapper = mock(FlowActionMapper.class);
        var validator = mock(FlowActionConfigurationValidator.class);
        var catalog = mock(FlowActionCatalogPort.class);
        when(catalog.requireSelectable(any(), any(), any())).thenReturn(new FlowActionDefinitionDescriptor("definition", "handler", "处理器"));
        var service = new FlowActionService(mapper, validator, catalog);
        var request = new FlowActionSaveRequest();
        request.setProcessConfigId("process"); request.setScopeType("PROCESS"); request.setActionName("业务动作");
        request.setTriggerTiming("PROCESS_COMPLETED"); request.setExecutionMode("AFTER_COMMIT"); request.setFailurePolicy("CUSTOM");
        request.setFailureStrategyCode("PROJECT_FAILURE_MANUAL"); request.setFailureStrategyVersion("1");
        request.setFailureStrategyConfig("{\"message\":\"人工核查\"}");
        request.setRetryConfig("{\"maxRetries\":3,\"semanticsVersion\":2}");
        FlowAction draft = service.saveAction(request);
        verify(validator).validate(draft);
        when(mapper.findDraftActionsByProcessConfigId("process")).thenReturn(List.of(draft));
        service.publishActions("process", "version-1");
        var captor = ArgumentCaptor.forClass(FlowAction.class);
        verify(mapper, times(2)).insert(captor.capture());
        var published = captor.getAllValues().get(1);
        assertNotSame(draft, published);
        assertEquals("version-1", published.getVersionId());
        assertEquals("CUSTOM", published.getFailurePolicy());
        assertEquals(request.getFailureStrategyCode(), published.getFailureStrategyCode());
        assertEquals(request.getFailureStrategyVersion(), published.getFailureStrategyVersion());
        assertEquals(request.getFailureStrategyConfig(), published.getFailureStrategyConfig());
        assertEquals(request.getRetryConfig(), published.getRetryConfig());
        draft.setFailureStrategyVersion("2");
        assertEquals("1", published.getFailureStrategyVersion());
    }

    @Test void publishingImportedCustomActionFreezesValidatedDefaults() {
        var mapper = mock(FlowActionMapper.class);
        var validator = mock(FlowActionConfigurationValidator.class);
        var action = new FlowAction(); action.setFailurePolicy("CUSTOM"); action.setEnabled(true);
        when(mapper.findDraftActionsByProcessConfigId("process")).thenReturn(List.of(action));
        doAnswer(invocation -> {
            ((FlowAction) invocation.getArgument(0)).setFailureStrategyConfig("{\"delay\":60}");
            return null;
        }).when(validator).validate(action);
        new FlowActionService(mapper, validator, mock(FlowActionCatalogPort.class)).publishActions("process", "v1");
        var captured = ArgumentCaptor.forClass(FlowAction.class);
        verify(mapper).insert(captured.capture());
        assertEquals("{\"delay\":60}", captured.getValue().getFailureStrategyConfig());
    }
}
