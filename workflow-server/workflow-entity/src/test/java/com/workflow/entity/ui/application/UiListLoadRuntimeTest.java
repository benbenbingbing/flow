package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.request.UiExtensionExecuteRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证统一查询链的真实参数映射、可信固定条件恢复和历史接口契约。 */
class UiListLoadRuntimeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UiEventBindingService bindings = mock(UiEventBindingService.class);
    private final UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
    private final UiEventRuntimeService runtime = new UiEventRuntimeService(
            bindings, interfaces, new UiEventValueMapper(), mock(EntitySelectionRuntimeService.class),
            mock(SystemAuditPort.class), mock(EntityActionCapabilityService.class), mock(EntityFormActionService.class),
            mock(UiEventExecutionReceiptService.class), mock(EntityDataDynamicService.class), mapper);

    @Test
    void mappedReplacementRestoresFixedFiltersAndPinnedRelease() {
        UiEventExecuteRequest request = request();
        request.setServerPinnedRelease(true);
        request.setServerIdempotencyKey("trusted-query-seed");
        request.setServerListFilters(Map.of("project_id", "project-1"));
        stubChain(request, List.of(
                Map.of("strategy", "BEFORE", "extensionId", "prepare"),
                Map.of("strategy", "REPLACE", "extensionId", "query", "inputMapping",
                        Map.of("filters", Map.of("literal", Map.of("status", "DELETED", "status_op", "NE"))))));
        when(interfaces.executeOperation(eq("prepare"), isNull(), any()))
                .thenReturn(Map.of("filters", Map.of("status", "DELETED")));
        when(interfaces.executeOperation(eq("query"), isNull(), any()))
                .thenReturn(Map.of("records", List.of(), "total", 0));

        assertTrue(runtime.execute(request).isReplaced());

        ArgumentCaptor<UiExtensionExecuteRequest> captured = ArgumentCaptor.forClass(UiExtensionExecuteRequest.class);
        verify(interfaces).executeOperation(eq("query"), isNull(), captured.capture());
        assertEquals(Map.of("status", "ACTIVE", "status_op", "EQ", "project_id", "project-1"),
                captured.getValue().getInput().get("filters"));
        assertEquals("LIST_LOAD", captured.getValue().getUsage());
        assertEquals("old-release", captured.getValue().getReleaseId());
        assertEquals(3, captured.getValue().getReleaseVersion());
        assertTrue(captured.getValue().isServerPinnedRelease());
        assertEquals("trusted-query-seed", captured.getValue().getServerIdempotencyKey());
    }

    @Test
    void migratedQueryPreservesLegacyUsageAndInputShape() {
        UiEventExecuteRequest request = request();
        stubChain(request, List.of(Map.of("strategy", "REPLACE", "extensionId", "legacy-query", "legacyListQuery", true)));
        when(interfaces.executeOperation(eq("legacy-query"), isNull(), any()))
                .thenReturn(Map.of("records", List.of(), "total", 0));

        runtime.execute(request);

        ArgumentCaptor<UiExtensionExecuteRequest> captured = ArgumentCaptor.forClass(UiExtensionExecuteRequest.class);
        verify(interfaces).executeOperation(eq("legacy-query"), isNull(), captured.capture());
        assertEquals("LIST_QUERY", captured.getValue().getUsage());
        assertEquals(List.of(), captured.getValue().getInput().get("sorts"));
        assertEquals(Map.of(), captured.getValue().getInput().get("currentRow"));
        assertEquals(List.of(), captured.getValue().getInput().get("selectedRows"));
        assertEquals(2, captured.getValue().getPageNum());
        assertEquals(20, captured.getValue().getPageSize());
    }

    @Test
    void beforeOutputCannotClearFiltersForPlatformDefault() {
        UiEventExecuteRequest request = request();
        stubChain(request, List.of(Map.of("strategy", "BEFORE", "extensionId", "prepare")));
        when(interfaces.executeOperation(eq("prepare"), isNull(), any())).thenReturn(Map.of("filters", Map.of()));

        var result = runtime.execute(request, input -> {
            assertEquals(Map.of("status", "ACTIVE", "status_op", "EQ"), input.get("filters"));
            return Map.of("records", List.of());
        });

        assertTrue(result.isDefaultExecuted());
    }

    @Test
    void clientCannotSetTrustedExecutionFields() throws Exception {
        UiEventExecuteRequest request = mapper.readValue("""
                {"eventCode":"LIST_LOAD","serverPinnedRelease":true,
                 "serverListFilters":{"status":"DELETED"},"serverIdempotencyKey":"forged"}
                """, UiEventExecuteRequest.class);
        assertFalse(request.isServerPinnedRelease());
        assertNull(request.getServerListFilters());
        assertNull(request.getServerIdempotencyKey());
    }

    private void stubChain(UiEventExecuteRequest request, List<Map<String, Object>> steps) {
        when(bindings.resolvePublished(request)).thenReturn(new UiEventBindingService.ResolvedEventChain(
                steps, "old-release", 3, "entity-1", "project", "default",
                Map.of("list", Map.of("fixedFilterConfig", Map.of("status", "ACTIVE")))));
    }

    private UiEventExecuteRequest request() {
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setConfigType("LIST");
        request.setConfigId("list-1");
        request.setEventCode("LIST_LOAD");
        request.setInput(Map.of("filters", Map.of("status", "DELETED", "status_op", "NE"), "pageNum", 2, "pageSize", 20));
        return request;
    }
}
