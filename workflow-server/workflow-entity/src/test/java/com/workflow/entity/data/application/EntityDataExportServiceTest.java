package com.workflow.entity.data.application;

import com.workflow.entity.data.api.request.EntityDataExportRequest;
import com.workflow.entity.list.application.EntityDataListConfigService;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
