package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityStatusMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityDataDynamicServiceSubFormTest {

    @Test
    void saveWritesSubFormRowsToReferencedEntityTableInsteadOfParentTable() {
        Fixture fixture = new Fixture();
        EntityDataDynamicService service = fixture.service();

        EntityDataDTO dto = new EntityDataDTO();
        dto.setEntityCode("parent");
        dto.setSubmitterId("admin");
        dto.setSubmitterName("管理员");
        dto.setData(new HashMap<>(Map.of(
                "name", "主数据",
                "detailList", new ArrayList<>(List.of(Map.of("itemName", "明细一")))
        )));

        service.save(dto);

        ArgumentCaptor<Map<String, Object>> parentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(eq("wf_parent"), parentCaptor.capture());
        assertFalse(parentCaptor.getValue().containsKey("detail_list"));
        assertFalse(parentCaptor.getValue().containsKey("detailList"));

        ArgumentCaptor<Map<String, Object>> childCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).insert(eq("wf_child"), childCaptor.capture());
        Map<String, Object> childData = childCaptor.getValue();
        assertEquals("明细一", childData.get("itemName"));
        assertEquals(dto.getId(), childData.get("parentId"));
        assertEquals(0, childData.get("deleted"));
    }

    @Test
    void findByIdLoadsSubFormRowsByReferenceField() {
        Fixture fixture = new Fixture();
        EntityDataDynamicService service = fixture.service();

        when(fixture.dynamicMapper.selectById("wf_parent", "parent-1")).thenReturn(new HashMap<>(Map.of(
                "id", "parent-1",
                "name", "主数据",
                "deleted", 0
        )));
        when(fixture.dynamicMapper.selectByCondition(eq("wf_child"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(List.of(new HashMap<>(Map.of(
                        "id", "child-1",
                        "parent_id", "parent-1",
                        "item_name", "明细一",
                        "deleted", 0
                ))));

        EntityDataDTO dto = service.findById("parent", "parent-1");

        assertNotNull(dto.getData().get("detailList"));
        List<?> rows = (List<?>) dto.getData().get("detailList");
        assertEquals(1, rows.size());
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("child-1", row.get("id"));
        assertEquals("明细一", row.get("itemName"));
        assertEquals("parent-1", row.get("parentId"));
        verify(fixture.dynamicMapper).selectByCondition(eq("wf_child"), org.mockito.ArgumentMatchers.argThat(condition ->
                "parent-1".equals(condition.get("parentId")) && "EQ".equals(condition.get("parentId_op"))));
    }

    private static class Fixture {
        final EntityDataDynamicMapper dynamicMapper = mock(EntityDataDynamicMapper.class);
        final EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        final EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        final EntityStatusMapper entityStatusMapper = mock(EntityStatusMapper.class);
        final DynamicTableService dynamicTableService = mock(DynamicTableService.class);
        final EntityCodeGeneratorService codeGeneratorService = mock(EntityCodeGeneratorService.class);

        Fixture() {
            EntityDefinition parent = entity("parent-id", "parent");
            EntityDefinition child = entity("child-id", "child");
            EntityField subForm = subFormField();

            when(definitionMapper.findByEntityCode("parent")).thenReturn(Optional.of(parent));
            when(definitionMapper.selectById("child-id")).thenReturn(child);
            when(fieldMapper.findByEntityId("parent-id")).thenReturn(List.of(subForm));
            when(fieldMapper.findByEntityId("child-id")).thenReturn(List.of());
            when(dynamicTableService.getTableName("parent")).thenReturn("wf_parent");
            when(dynamicTableService.getTableName("child")).thenReturn("wf_child");
            when(dynamicTableService.tableExists("parent")).thenReturn(true);
            when(dynamicTableService.tableExists("child")).thenReturn(true);
            when(codeGeneratorService.generateCode("parent")).thenReturn("P001");
            when(entityStatusMapper.findByCategory("parent", "NEW")).thenReturn(List.of());
        }

        EntityDataDynamicService service() {
            return new EntityDataDynamicService(
                    dynamicMapper, definitionMapper, fieldMapper, entityStatusMapper,
                    null, null, dynamicTableService, null, null, null, null,
                    codeGeneratorService, new ObjectMapper(), null, null, null, null);
        }

        private static EntityDefinition entity(String id, String code) {
            EntityDefinition entity = new EntityDefinition();
            entity.setId(id);
            entity.setEntityCode(code);
            entity.setEnableProcess(false);
            return entity;
        }

        private static EntityField subFormField() {
            EntityField field = new EntityField();
            field.setFieldCode("detailList");
            field.setFieldName("明细");
            field.setFieldType(EntityField.FieldType.SUB_FORM);
            field.setRefEntityId("child-id");
            field.setRefFieldCode("parentId");
            return field;
        }
    }
}
