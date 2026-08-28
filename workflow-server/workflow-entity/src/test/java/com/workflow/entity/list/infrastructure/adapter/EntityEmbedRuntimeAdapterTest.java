package com.workflow.entity.list.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.api.response.EntityListSchemaDTO;
import com.workflow.entity.list.application.EntityListRuntimeService;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityEmbedRuntimeAdapterTest {

    private EntityListRuntimeService runtimeService;
    private EntityEmbedRuntimeAdapter adapter;
    private EntityListField listField;

    @BeforeEach
    void setUp() {
        runtimeService = mock(EntityListRuntimeService.class);
        adapter = new EntityEmbedRuntimeAdapter(runtimeService, new ObjectMapper());

        listField = new EntityListField();
        listField.setFieldCode("status");
        listField.setFieldName("状态");
        listField.setShowInList(true);
        listField.setIsQuery(true);
        listField.setQueryType("EQ");
        listField.setQueryConfig("{\"fieldType\":\"SELECT\",\"options\":["
                + "{\"label\":\"处理中\",\"value\":\"PROCESSING\"}]}");
        EntityListSchemaDTO schema = new EntityListSchemaDTO();
        schema.setEntityCode("work_order");
        schema.setEntityName("工单");
        schema.setListKey("supplier_open");
        schema.setListName("供应商工单");
        schema.setSelectionConfig(Map.of("mode", "SINGLE"));
        schema.setFields(List.of(listField));
        schema.setToolbarConfig(List.of());
        schema.setRowActionConfig(List.of(Map.of("key", "view", "label", "查看")));
        when(runtimeService.schemaPinned(
                "work_order", "supplier_open", "list-release-7", 7))
                .thenReturn(schema);
    }

    @Test
    void mapsOnlyPublishedSchemaFieldsAndPinsRuntimeCoordinates() {
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        row.setData(Map.of("status", "PROCESSING", "secret", "hidden"));
        row.setUpdatedAt(LocalDateTime.of(2026, 8, 27, 8, 20));
        row.setActionCapabilities(Map.of(
                "view", EntityActionCapabilityDTO.allowed()));
        when(runtimeService.queryPinned(
                "work_order", "supplier_open", "list-release-7", 7,
                1, 20, Map.of("status", "PROCESSING", "status_op", "EQ"),
                Map.of("supplier_id", "S-10086")))
                .thenReturn(new PageResult<>(List.of(row), 1, 1, 20));

        var schema = adapter.loadListSchema(
                "work_order", "supplier_open", "list-release-7", 7);
        var page = adapter.queryList(
                "work_order", "supplier_open", "list-release-7", 7,
                1, 20, Map.of("status", "PROCESSING", "status_op", "EQ"),
                Map.of("supplier_id", "S-10086"));

        assertEquals("SELECT", schema.fields().get(0).type());
        assertEquals("PROCESSING", schema.fields().get(0).options().get(0).value());
        assertEquals(Map.of("status", "PROCESSING"), page.rows().get(0).values());
        assertEquals(true, page.rows().get(0).actionCapabilities().get("view").enabled());
        verify(runtimeService).queryPinned(
                "work_order", "supplier_open", "list-release-7", 7,
                1, 20, Map.of("status", "PROCESSING", "status_op", "EQ"),
                Map.of("supplier_id", "S-10086"));
    }

    @Test
    void mapsPublishedComparisonAliasesToExternalOperators() {
        listField.setQueryType("GE");
        assertEquals("GTE", adapter.loadListSchema(
                "work_order", "supplier_open", "list-release-7", 7)
                .fields().get(0).queryOperator());

        listField.setQueryType("LE");
        assertEquals("LTE", adapter.loadListSchema(
                "work_order", "supplier_open", "list-release-7", 7)
                .fields().get(0).queryOperator());

        listField.setQueryType("LIKE");
        assertEquals("CONTAINS", adapter.loadListSchema(
                "work_order", "supplier_open", "list-release-7", 7)
                .fields().get(0).queryOperator());
    }
}
