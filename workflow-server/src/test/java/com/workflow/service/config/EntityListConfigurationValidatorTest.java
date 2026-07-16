package com.workflow.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.service.listfield.ListFieldDataProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityListConfigurationValidatorTest {

    @Test
    void normalizesBlankJsonColumnsToNull() {
        EntityFieldMapper entityFieldMapper = mock(EntityFieldMapper.class);
        EntityField entityField = new EntityField();
        entityField.setId("field-1");
        when(entityFieldMapper.findByEntityId("entity-1")).thenReturn(List.of(entityField));

        EntityListConfigurationValidator validator = new EntityListConfigurationValidator(
                new StructuredConfigValidator(new ObjectMapper()),
                new ListFieldDataProviderRegistry(List.of(), new ObjectMapper()),
                entityFieldMapper);
        EntityListField field = new EntityListField();
        field.setFieldId("field-1");
        field.setFieldCode("riskScore");
        field.setDataSourceType("ENTITY_FIELD");
        field.setColumnConfig("");
        field.setQueryConfig(" ");
        field.setRenderConfig("\n");
        EntityListConfigDTO dto = new EntityListConfigDTO();
        dto.setEntityId("entity-1");
        dto.setEntityCode("demo_project");
        dto.setListKey("default");
        dto.setViewConfig("\t");
        dto.setFields(List.of(field));

        validator.validate(dto);

        assertNull(dto.getViewConfig());
        assertNull(field.getColumnConfig());
        assertNull(field.getQueryConfig());
        assertNull(field.getRenderConfig());
    }
}
