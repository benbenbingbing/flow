package com.workflow.service;

import com.workflow.common.ForbiddenException;
import com.workflow.dto.EntityDataDTO;
import com.workflow.dto.permission.EntityActionCapabilityDTO;
import com.workflow.entity.EntityListConfig;
import com.workflow.service.permission.EntityActionCapabilityService;
import com.workflow.service.permission.EntityListActionConfigService;
import com.workflow.service.permission.EntityListScopeAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private EntityListScopeAuditService scopeAuditService;

    @Mock
    private PublishedFormSubmissionService formSubmissionService;

    @Mock
    private FormSubmissionTraceService formSubmissionTraceService;

    @InjectMocks
    private EntityDataActionService service;

    @Test
    void processInstanceDetailUsesResolvedListPermissionScope() {
        EntityListConfig config = new EntityListConfig();
        config.setId("list-1");
        config.setListKey("default");
        when(actionConfigService.resolveListConfig("asset", "default"))
                .thenReturn(config);
        EntityDataDTO row = row("1", "A-1");
        when(dynamicService.findAccessibleByProcessInstanceId(
                "asset",
                "process-1",
                "default")).thenReturn(row);

        service.getDetailByProcessInstance("asset", "process-1", "default");

        verify(dynamicService).findAccessibleByProcessInstanceId(
                "asset",
                "process-1",
                "default");
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

    @Test
    void createExecutesServerBeforeSubmitExactlyOnce() {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("asset");
        dto.setData(Map.of("name", "Laptop"));
        FormSubmissionExecutionContext context =
                context("create-trace", "ENTITY_CREATE");
        when(formSubmissionTraceService.current(
                eq("ENTITY_CREATE"),
                isNull(),
                anyMap())).thenReturn(context);
        when(formSubmissionService.applyDefaultForm(
                "asset",
                null,
                "create",
                dto.getData(),
                context)).thenReturn(
                        Map.of(
                                "name",
                                "Laptop",
                                "normalized",
                                true));
        when(dynamicService.save(dto)).thenReturn(dto);

        service.create(dto);

        verify(formSubmissionService, times(1))
                .applyDefaultForm(
                        "asset",
                        null,
                        "create",
                        Map.of("name", "Laptop"),
                        context);
        verify(dynamicService, times(1)).save(dto);
    }

    @Test
    void updateExecutesServerBeforeSubmitExactlyOnce() {
        EntityDataDTO existing = row("1", "A-1");
        when(dynamicService.findAccessibleById(
                "asset",
                "1",
                null)).thenReturn(existing);
        FormSubmissionExecutionContext context =
                context("update-trace", "ENTITY_UPDATE");
        when(formSubmissionTraceService.current(
                eq("ENTITY_UPDATE"),
                isNull(),
                anyMap())).thenReturn(context);
        when(formSubmissionService.applyDefaultForm(
                "asset",
                "1",
                "edit",
                Map.of("name", "Laptop"),
                context)).thenReturn(
                        Map.of(
                                "name",
                                "Laptop",
                                "normalized",
                                true));

        service.update(
                "asset",
                "1",
                null,
                Map.of("name", "Laptop"));

        verify(formSubmissionService, times(1))
                .applyDefaultForm(
                        "asset",
                        "1",
                        "edit",
                        Map.of("name", "Laptop"),
                        context);
        verify(dynamicService, times(1)).update(
                "asset",
                "1",
                Map.of(
                        "data",
                        Map.of(
                                "name",
                                "Laptop",
                                "normalized",
                                true)));
    }

    private EntityDataDTO row(String id, String dataNo) {
        EntityDataDTO row = new EntityDataDTO();
        row.setId(id);
        row.setDataNo(dataNo);
        return row;
    }

    private FormSubmissionExecutionContext context(
            String traceKey,
            String operation) {
        return new FormSubmissionExecutionContext(
                traceKey,
                operation,
                Map.of());
    }
}
