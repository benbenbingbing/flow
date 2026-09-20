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

class EntityDataExportServiceTest {

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
