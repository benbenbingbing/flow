package com.workflow.entity.data.application;

import com.workflow.entity.data.api.request.EntityDataExportRequest;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.core.error.ForbiddenException;
import org.springframework.mock.web.MockHttpServletResponse;

class EntityDataExportServiceTest {

    @Test
    void selectedIdsAndOriginalConditionsReachQueryAndDeniedRowsPreventAnyOutput() {
        var listService = mock(EntityDataListConfigService.class);
        var capability = mock(EntityActionCapabilityService.class);
        var service = new EntityDataExportService(listService, mock(EntityListPublishedRuntimeService.class),
                mock(EntityListFieldMapper.class), capability);
        var request = new EntityDataExportRequest();
        request.setExportType("SELECTED");
        request.setIds(List.of(" 42 ", "42"));
        request.setCondition(Map.of("status", "OPEN", "id", "42", "id_op", "EQ"));
        var row = new EntityDataDTO(); row.setId("42");
        when(listService.findExportBatchWithResolvedConfig("expense", null, null,
                request.getCondition(), List.of("42"), null)).thenReturn(new EntityExportBatch(List.of(row), null));
        when(capability.evaluateRowActionForConfig("expense", null, "exportSelected", row))
                .thenReturn(EntityActionCapabilityDTO.hidden("无权限"));
        var response = new MockHttpServletResponse();
        assertThrows(ForbiddenException.class, () -> service.export("expense", request, response));
        assertEquals(0, response.getContentAsByteArray().length);
        verify(listService).findExportBatchWithResolvedConfig("expense", null, null,
                request.getCondition(), List.of("42"), null);
        verify(listService, never()).findListWithResolvedConfig(anyString(), any(), any(), any());
    }

    @Test
    void fullExportWritesBatchesInOrderWithoutLoadingFullList() throws Exception {
        var listService = mock(EntityDataListConfigService.class);
        var service = new EntityDataExportService(listService, mock(EntityListPublishedRuntimeService.class),
                mock(EntityListFieldMapper.class), mock(EntityActionCapabilityService.class));
        var request = new EntityDataExportRequest(); request.setExportType("ALL");
        var first = new EntityDataDTO(); first.setName("第一条");
        var second = new EntityDataDTO(); second.setName("第二条");
        var cursor = new EntityExportBatch.Cursor(null, "2");
        when(listService.findExportBatchWithResolvedConfig("expense", null, null, null, null, null))
                .thenReturn(new EntityExportBatch(List.of(first), cursor));
        when(listService.findExportBatchWithResolvedConfig("expense", null, null, null, null, cursor))
                .thenReturn(new EntityExportBatch(List.of(second), null));
        var response = new MockHttpServletResponse();
        service.export("expense", request, response);
        String csv = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(csv.indexOf("第一条") < csv.indexOf("第二条"));
        verify(listService, never()).findListWithResolvedConfig(anyString(), any(), any(), any());
    }

    @Test
    void selectedExportRejectsEmptySelectionBeforeQueryingData() {
        EntityDataListConfigService listService =
                mock(EntityDataListConfigService.class);
        EntityActionCapabilityService capabilityService =
                mock(EntityActionCapabilityService.class);
        EntityDataExportService service = new EntityDataExportService(
                listService,
                mock(EntityListPublishedRuntimeService.class),
                mock(EntityListFieldMapper.class),
                capabilityService);
        EntityDataExportRequest request = new EntityDataExportRequest();
        request.setExportType("SELECTED");
        request.setIds(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.export(
                        "expense", request,
                        mock(HttpServletResponse.class)));

        verifyNoInteractions(listService, capabilityService);
    }

    /** 导出字段使用公开列名，不能依赖 DTO 的 Java 属性拼写。 */
    @Test
    void readsCanonicalAuditFieldsForExport() throws Exception {
        EntityDataExportService service = new EntityDataExportService(null, null, null, null);
        EntityDataDTO row = new EntityDataDTO();
        LocalDateTime created = LocalDateTime.of(2026, 9, 20, 10, 0);
        row.setCreateTime(created);
        row.setUpdateTime(created.plusHours(1));
        row.setCreateBy("creator");
        row.setUpdateBy("updater");
        var getter = EntityDataExportService.class.getDeclaredMethod("getFieldValue", EntityDataDTO.class, String.class);
        getter.setAccessible(true);
        for (var entry : Map.of("create_time", created, "update_time", created.plusHours(1),
                "create_by", "creator", "update_by", "updater").entrySet()) {
            assertEquals(entry.getValue(), getter.invoke(service, row, entry.getKey()));
        }
    }
}
