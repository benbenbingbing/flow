package com.workflow.service;

import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.EntitySelectionRuntimeService;
import com.workflow.entity.ui.application.UiDataSourceService;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import com.workflow.entity.ui.application.UiEventValueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UiEventRuntimeServiceTest {

    @Mock
    private UiEventBindingService bindingService;
    @Mock
    private UiDataSourceService dataSourceService;
    @Mock
    private UiEventValueMapper valueMapper;
    @Mock
    private EntitySelectionRuntimeService selectionRuntimeService;
    @Mock
    private SystemAuditPort auditPort;
    @Mock
    private EntityActionCapabilityService actionCapabilityService;
    @Mock
    private EntityFormActionService formActionService;

    private UiEventRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new UiEventRuntimeService(
                bindingService,
                dataSourceService,
                valueMapper,
                selectionRuntimeService,
                auditPort,
                actionCapabilityService,
                formActionService);
    }

    @Test
    void customFormButtonRequiresUpdatePermission() {
        UiEventExecuteRequest request = request("FORM_BUTTON_CLICK", "submit");
        stubPublishedChain(request);

        service.execute(request);

        verify(formActionService).requireCustomButton(request);
    }

    @Test
    void customRowButtonRequiresUpdatePermission() {
        UiEventExecuteRequest request = request("ROW_BUTTON_CLICK", "archive");
        request.setRecordId("record-1");
        stubPublishedChain(request);

        service.execute(request);

        verify(actionCapabilityService).requireStandardPermission(
                "expense",
                EntityPermissionAction.UPDATE);
    }

    @Test
    void standardDeleteButtonKeepsDeletePermission() {
        UiEventExecuteRequest request = request("ROW_BUTTON_CLICK", "delete");
        request.setRecordId("record-1");
        stubPublishedChain(request);

        service.execute(request);

        verify(actionCapabilityService).requireStandardPermission(
                "expense",
                EntityPermissionAction.DELETE);
    }

    private UiEventExecuteRequest request(String eventCode, String targetKey) {
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setEventCode(eventCode);
        request.setConfigType("LIST");
        request.setConfigId("list-1");
        request.setTargetType("BUTTON");
        request.setTargetKey(targetKey);
        request.setInput(Map.of());
        return request;
    }

    private void stubPublishedChain(UiEventExecuteRequest request) {
        when(bindingService.resolvePublished(request)).thenReturn(
                new UiEventBindingService.ResolvedEventChain(
                        List.of(),
                        "release-1",
                        1,
                        "entity-1",
                        "expense",
                        "default",
                        Map.of()));
    }
}
