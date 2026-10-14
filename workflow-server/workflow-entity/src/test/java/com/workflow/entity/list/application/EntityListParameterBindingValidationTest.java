package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.ui.application.validation.StructuredConfigValidator;
import com.workflow.entity.ui.application.validation.UiConfigInterfaceReferenceValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 保存、元数据补丁和发布共用的参数校验，应允许隐藏实体字段，同时拒绝不存在或计算出的字段。 */
class EntityListParameterBindingValidationTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsHiddenEntityFieldRegardlessOfListColumnConfiguration(boolean configured) {
        var dto = configuration("projectId");
        dto.setFields(configured ? List.of(hiddenColumn()) : null);
        assertDoesNotThrow(() -> validator().validate(dto));
    }

    @Test
    void rejectsTargetThatDoesNotBelongToEntity() {
        var dto = configuration("missing");
        dto.setFields(List.of());
        assertEquals("参数用途字段不可用: missing",
                assertThrows(IllegalArgumentException.class, () -> validator().validate(dto)).getMessage());
    }

    @Test
    void rejectsComputedColumnEvenWhenItUsesEntityFieldIdentity() {
        var dto = configuration("projectId");
        var field = hiddenColumn();
        field.setDataSourceType("FIELD_TEMPLATE");
        field.setDataSourceConfig("{\"template\":\"${name}\"}");
        dto.setFields(List.of(field));
        assertEquals("参数用途字段不可用: projectId",
                assertThrows(IllegalArgumentException.class, () -> validator().validate(dto)).getMessage());
    }

    private EntityListConfigurationValidator validator() {
        var json = new ObjectMapper();
        var mapper = mock(EntityFieldMapper.class);
        var field = new EntityField();
        field.setId("field-project");
        field.setFieldCode("projectId");
        when(mapper.findByEntityId("entity-1")).thenReturn(List.of(field));
        return new EntityListConfigurationValidator(new StructuredConfigValidator(json),
                new JsonDocumentCodec(json), new ListFieldDataProviderRegistry(List.of(), json), mapper,
                mock(UiConfigInterfaceReferenceValidator.class));
    }

    private EntityListConfigDTO configuration(String target) {
        var dto = new EntityListConfigDTO();
        dto.setEntityId("entity-1");
        dto.setEntityCode("project");
        dto.setListKey("default");
        dto.setViewConfig(Map.of(
                "inputParameterSchema", Map.of("properties", Map.of("project", Map.of("type", "string"))),
                "inputParameterBindings", List.of(Map.of("parameter", "project", "usage", "FILTER",
                        "targetField", target, "operator", "EQ"))));
        return dto;
    }

    private EntityListField hiddenColumn() {
        var field = new EntityListField();
        field.setFieldId("field-project");
        field.setFieldCode("projectId");
        field.setDataSourceType("ENTITY_FIELD");
        field.setShowInList(false);
        field.setIsQuery(false);
        return field;
    }
}
