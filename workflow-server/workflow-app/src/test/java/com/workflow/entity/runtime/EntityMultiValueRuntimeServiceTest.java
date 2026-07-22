package com.workflow.entity.runtime;

import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.service.DynamicTableService;
import com.workflow.service.EntityPhysicalTableResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityMultiValueRuntimeServiceTest {

    @Test
    void extractsConfiguredCodeAndEntityMultiValuesFromMainRecord() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        DynamicTableService dynamicTableService = mock(DynamicTableService.class);
        EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
        EntityMultiValueRuntimeService service = new EntityMultiValueRuntimeService(
                jdbcTemplate,
                fieldMapper,
                definitionMapper,
                dynamicTableService,
                tableResolver);

        EntityDefinition definition = new EntityDefinition();
        definition.setId("expense-id");

        EntityField tags = new EntityField();
        tags.setFieldCode("expenseTags");
        tags.setFieldType(EntityField.FieldType.MULTI_SELECT);
        tags.setDictType("expense_type");

        EntityField projects = new EntityField();
        projects.setFieldCode("projects");
        projects.setFieldType(EntityField.FieldType.MULTI_REFERENCE);
        projects.setRefEntityId("project-id");

        when(fieldMapper.findByEntityId("expense-id")).thenReturn(List.of(tags, projects));

        Map<String, Object> record = new HashMap<>();
        record.put("name", "差旅报销");
        record.put("expenseTags", new ArrayList<>(List.of("TRAVEL", "OFFICE", "TRAVEL")));
        record.put("projects", "[\"project-1\",\"project-2\"]");

        Map<String, List<String>> values = service.extractConfiguredValues(definition, record);

        assertEquals(List.of("TRAVEL", "OFFICE"), values.get("expenseTags"));
        assertEquals(List.of("project-1", "project-2"), values.get("projects"));
        assertFalse(record.containsKey("expenseTags"));
        assertFalse(record.containsKey("projects"));
        assertEquals("差旅报销", record.get("name"));
    }

    @Test
    void compilesEntityMultiValueConditionToParameterizedExists() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntityFieldMapper fieldMapper = mock(EntityFieldMapper.class);
        EntityDefinitionMapper definitionMapper = mock(EntityDefinitionMapper.class);
        DynamicTableService dynamicTableService = mock(DynamicTableService.class);
        EntityPhysicalTableResolver tableResolver = mock(EntityPhysicalTableResolver.class);
        EntityMultiValueRuntimeService service = new EntityMultiValueRuntimeService(
                jdbcTemplate,
                fieldMapper,
                definitionMapper,
                dynamicTableService,
                tableResolver);

        EntityDefinition definition = new EntityDefinition();
        definition.setId("expense-id");
        definition.setEntityCode("expense");

        EntityField projects = new EntityField();
        projects.setEntityId("expense-id");
        projects.setFieldCode("projects");
        projects.setFieldType(EntityField.FieldType.MULTI_REFERENCE);
        projects.setRefEntityId("project-id");

        when(fieldMapper.findByEntityId("expense-id")).thenReturn(List.of(projects));
        when(dynamicTableService.getTableName("expense")).thenReturn("biz_expense");
        when(dynamicTableService.getMultiValueTableName("expense")).thenReturn("biz_expense_multi");

        EntityMultiValueRuntimeService.PreparedConditions prepared = service.prepareConditions(
                definition,
                Map.of(
                        "projects", List.of("project-1", "project-2"),
                        "projects_op", "CONTAINS_ANY"));

        assertFalse(prepared.condition().containsKey("projects"));
        assertTrue(prepared.condition().containsValue("project-id"));
        assertTrue(prepared.condition().containsValue("project-1"));
        assertTrue(prepared.condition().containsValue("project-2"));
        assertTrue(prepared.sqlCondition().contains("FROM biz_expense_multi mv"));
        assertTrue(prepared.sqlCondition().contains("mv.record_id = biz_expense.id"));
        assertTrue(prepared.sqlCondition().contains("mv.target_record_id IN"));
    }
}
