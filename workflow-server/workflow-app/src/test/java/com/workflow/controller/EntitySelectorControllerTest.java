package com.workflow.controller;

import com.workflow.common.ForbiddenException;
import com.workflow.common.Result;
import com.workflow.dto.EntityDataDTO;
import com.workflow.service.DynamicTableService;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityDefinitionService;
import com.workflow.service.EntityFieldService;
import com.workflow.service.SystemEntityService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntitySelectorControllerTest {

    private final EntityDataDynamicService dynamicService =
            mock(EntityDataDynamicService.class);
    private final DynamicTableService tableService = mock(DynamicTableService.class);
    private final SystemEntityService systemEntityService =
            mock(SystemEntityService.class);
    private final EntityFieldService fieldService = mock(EntityFieldService.class);
    private final EntityDefinitionService definitionService =
            mock(EntityDefinitionService.class);
    private final EntitySelectorController controller = new EntitySelectorController(
            dynamicService,
            tableService,
            systemEntityService,
            fieldService,
            definitionService);

    @Test
    void customSelectorDetailUsesPermissionAwareLookup() {
        when(tableService.tableExists("expense")).thenReturn(true);
        EntityDataDTO dto = new EntityDataDTO();
        dto.setData(Map.of("id", "row-1", "name", "报销单"));
        when(dynamicService.findAccessibleById("expense", "row-1", null))
                .thenReturn(dto);

        Result<Map<String, Object>> result = controller.getById(
                "CUSTOM",
                "row-1",
                "expense",
                null);

        assertEquals(200, result.getCode());
        assertEquals("row-1", result.getData().get("id"));
        verify(dynamicService).findAccessibleById("expense", "row-1", null);
        verify(dynamicService, never()).findById("expense", "row-1");
    }

    @Test
    void customSelectorBatchOmitsRowsOutsidePermissionScope() {
        when(tableService.tableExists("expense")).thenReturn(true);
        EntityDataDTO allowed = new EntityDataDTO();
        allowed.setData(Map.of("id", "row-1", "name", "可见数据"));
        when(dynamicService.findAccessibleById("expense", "row-1", null))
                .thenReturn(allowed);
        when(dynamicService.findAccessibleById("expense", "row-2", null))
                .thenThrow(new ForbiddenException("无权访问"));

        Result<List<Map<String, Object>>> result = controller.getBatch(
                "CUSTOM",
                "row-1,row-2",
                "expense",
                null);

        assertEquals(1, result.getData().size());
        assertEquals("row-1", result.getData().get(0).get("id"));
    }
}
