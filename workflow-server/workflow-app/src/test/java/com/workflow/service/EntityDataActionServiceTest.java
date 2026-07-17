package com.workflow.service;

import com.workflow.common.ForbiddenException;
import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.EntityActionCapabilityDTO;
import com.workflow.entity.EntityListConfig;
import com.workflow.service.permission.EntityActionCapabilityService;
import com.workflow.service.permission.EntityListActionConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityDataActionServiceTest {

    @Mock
    private EntityDataDynamicService dynamicService;

    @Mock
    private EntityListActionConfigService actionConfigService;

    @Mock
    private EntityActionCapabilityService capabilityService;

    @InjectMocks
    private EntityDataActionService service;

    @Test
    void processInstanceDetailUsesResolvedListPermissionScope() {
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        when(actionConfigService.resolveListConfig("asset", "default"))
                .thenReturn(config);
        EntityDataDTO row = row("1", "A-1");
        when(dynamicService.findAccessibleByProcessInstanceId(
                "asset",
                "process-1",
                "list-1")).thenReturn(row);

        service.getDetailByProcessInstance("asset", "process-1", "default");

        verify(dynamicService).findAccessibleByProcessInstanceId(
                "asset",
                "process-1",
                "list-1");
    }

    @Test
    void batchDeleteIsAllOrNothing() {
        EntityDataDTO allowed = row("1", "A-1");
        EntityDataDTO denied = row("2", "A-2");
        when(dynamicService.findAccessibleById("asset", "1", null)).thenReturn(allowed);
        when(dynamicService.findAccessibleById("asset", "2", null)).thenReturn(denied);
        when(capabilityService.evaluateRowAction("asset", null, "batchDelete", allowed))
                .thenReturn(EntityActionCapabilityDTO.allowed());
        when(capabilityService.evaluateRowAction("asset", null, "batchDelete", denied))
                .thenReturn(EntityActionCapabilityDTO.hidden("仅本人草稿可以删除"));

        assertThrows(
                ForbiddenException.class,
                () -> service.batchDelete("asset", List.of("1", "2"), null));

        verify(dynamicService, never()).delete("asset", "1");
        verify(dynamicService, never()).delete("asset", "2");
    }

    @Test
    void batchDeleteDeletesAllAfterValidation() {
        EntityDataDTO first = row("1", "A-1");
        EntityDataDTO second = row("2", "A-2");
        when(dynamicService.findAccessibleById("asset", "1", null)).thenReturn(first);
        when(dynamicService.findAccessibleById("asset", "2", null)).thenReturn(second);
        when(capabilityService.evaluateRowAction("asset", null, "batchDelete", first))
                .thenReturn(EntityActionCapabilityDTO.allowed());
        when(capabilityService.evaluateRowAction("asset", null, "batchDelete", second))
                .thenReturn(EntityActionCapabilityDTO.allowed());

        service.batchDelete("asset", List.of("1", "2"), null);

        verify(dynamicService).delete("asset", "1");
        verify(dynamicService).delete("asset", "2");
    }

    private EntityDataDTO row(String id, String dataNo) {
        EntityDataDTO row = new EntityDataDTO();
        row.setId(id);
        row.setDataNo(dataNo);
        return row;
    }
}
