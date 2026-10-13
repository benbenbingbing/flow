package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.contracts.entity.ui.port.UiRuntimeAccessPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.web.UiEventRuntimeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 删除页面令牌后的关键边界：摘要不授权，任务每次回查，路径不能跳过已发布引用。 */
class UiRuntimeContextSimplificationTest {
    @Test void digestDoesNotAllowBypassingLiveTaskAuthorization() {
        UiConfigReleaseService service = mock(UiConfigReleaseService.class, CALLS_REAL_METHODS);
        UiRuntimeAccessPort access = mock(UiRuntimeAccessPort.class);
        ObjectProvider<UiRuntimeAccessPort> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(access);
        ReflectionTestUtils.setField(service, "runtimeAccessPort", provider);
        Map<String, Object> context = Map.of("taskId", "transferred-task");
        when(access.resolve("form", context)).thenThrow(new BusinessForbiddenException("TASK_DENIED", "任务已转办"));
        assertThrows(BusinessForbiddenException.class,
                () -> service.resolveAuthorizedRuntimeFormRelease("form", "previous-valid-hash", context));
        verify(service, never()).resolveRuntimeFormRelease("form");
    }

    @Test void changedConfigurationStopsBeforeLoadingExecutableForm() {
        UiConfigReleaseService service = mock(UiConfigReleaseService.class, CALLS_REAL_METHODS);
        doReturn(UiRuntimeResolutionContext.standalone()).when(service).authorizeRuntimeContext("form", Map.of());
        doReturn("new-child-graph").when(service).formConfigHash("form");
        assertEquals("FORM_CONFIGURATION_CHANGED", assertThrows(BusinessConflictException.class,
                () -> service.resolveAuthorizedRuntimeFormRelease("form", "old-child-graph", Map.of())).getErrorCode());
        verify(service, never()).resolveRuntimeFormRelease("form");
    }

    @Test void componentPathCannotJumpToAnUnreferencedForm() {
        UiConfigReleaseService service = mock(UiConfigReleaseService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "codec", new JsonDocumentCodec(new ObjectMapper()));
        EntityForm root = new EntityForm(); root.setId("root");
        EntityFormNode node = new EntityFormNode(); node.setNodeKey("items");
        node.setPropsDocument("{}"); root.setNodes(List.of(node));
        doReturn(new ResolvedEntityFormRelease(root, "hash", 1)).when(service).resolveRuntimeFormRelease("root");
        assertThrows(BusinessForbiddenException.class,
                () -> service.requireComponentPath("root", "arbitrary", List.of("missing")));
        assertThrows(BusinessForbiddenException.class,
                () -> service.requireComponentPath("root", "arbitrary", List.of("items")));
    }

    @Test void clientCannotSetDerivedProcessPermissionFields() throws Exception {
        var request = new ObjectMapper().readValue("{\"serverProcessInstanceId\":\"other-instance\",\"serverAuthorizedMode\":\"approve\",\"serverReadOnly\":false,\"serverRecordId\":\"other-record\"}", UiEventExecuteRequest.class);
        assertNull(request.getServerProcessInstanceId());
        assertNull(request.getServerAuthorizedMode());
        assertNull(request.getServerRecordId());
    }

    @Test void publicEventWithoutDigestNeverReachesExecution() {
        UiEventRuntimeService runtime = mock(UiEventRuntimeService.class);
        UiEventRuntimeController controller = new UiEventRuntimeController(runtime);
        assertThrows(BusinessConflictException.class,
                () -> controller.execute("FIELD_CHANGE", new UiEventExecuteRequest()));
        verifyNoInteractions(runtime);
    }
}
