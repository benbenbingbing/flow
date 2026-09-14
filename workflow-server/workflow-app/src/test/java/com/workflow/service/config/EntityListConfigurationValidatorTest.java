package com.workflow.service.config;

import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import com.workflow.entity.ui.application.UiConfigInterfaceReferenceValidator;
import com.workflow.entity.ui.application.validation.StructuredConfigValidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体列表配置校验器测试。
 *
 * <p>被测对象：{@link EntityListConfigurationValidator}，覆盖将空白 JSON 列配置归一化为 null 的场景。
 */
class EntityListConfigurationValidatorTest {

    /** 测试将空白 JSON 列配置归一化为 null：验证 columnConfig/queryConfig/renderConfig 空白值被置为 null */
    @Test
    void normalizesBlankJsonColumnsToNull() {
        EntityFieldMapper entityFieldMapper = mock(EntityFieldMapper.class);
        EntityField entityField = new EntityField();
        entityField.setId("field-1");
        when(entityFieldMapper.findByEntityId("entity-1")).thenReturn(List.of(entityField));

        EntityListConfigurationValidator validator = new EntityListConfigurationValidator(
                new StructuredConfigValidator(new ObjectMapper()),
                new JsonDocumentCodec(new ObjectMapper()),
                new ListFieldDataProviderRegistry(List.of(), new ObjectMapper()),
                entityFieldMapper,
                mock(UiConfigInterfaceReferenceValidator.class));
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
        dto.setViewConfig(Map.of());
        dto.setFields(List.of(field));

        validator.validate(dto);

        assertEquals(Map.of(), dto.getViewConfig());
        assertNull(field.getColumnConfig());
        assertNull(field.getQueryConfig());
        assertNull(field.getRenderConfig());
    }

    /** mutable 列表只保存直接 extensionId，不再要求旧 operationCode。 */
    @Test
    void acceptsDirectListQueryExtensionIdWithoutOperation() {
        UiConfigInterfaceReferenceValidator referenceValidator =
                mock(UiConfigInterfaceReferenceValidator.class);
        EntityListConfigurationValidator validator = validator(
                mock(EntityFieldMapper.class), referenceValidator);
        EntityListConfigDTO dto = new EntityListConfigDTO();
        dto.setId("list-1");
        dto.setEntityId("entity-1");
        dto.setEntityCode("demo_project");
        dto.setListKey("default");
        dto.setQueryInterfaceExtensionId("extension-1");

        assertDoesNotThrow(() -> validator.validate(dto));
        verify(referenceValidator).validateListDraft(
                eq("list-1"),
                eq("entity-1"),
                eq("extension-1"),
                isNull());
    }

    /** 新列表 DTO 只接受并输出 extensionId 身份，不再把旧服务 ID 当作扩展 ID。 */
    @Test
    void mutableListDtoExposesOnlyExtensionIdentity() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        EntityListField field = new EntityListField();
        field.setInterfaceExtensionId("column-extension");
        EntityListConfigDTO dto = new EntityListConfigDTO();
        dto.setQueryInterfaceExtensionId("query-extension");
        dto.setFields(List.of(field));

        String json = mapper.writeValueAsString(dto);

        assertTrue(json.contains("\"queryInterfaceExtensionId\""));
        assertTrue(json.contains("\"interfaceExtensionId\""));
        assertFalse(json.contains("\"queryDataSourceId\""));
        assertFalse(json.contains("\"dataSourceId\""));
        assertFalse(json.contains("\"queryOperationCode\""));
        assertFalse(json.contains("\"dataSourceOperationCode\""));
        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue(
                        "{\"queryDataSourceId\":\"legacy-service\"}",
                        EntityListConfigDTO.class));
        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue(
                        "{\"dataSourceId\":\"legacy-service\","
                                + "\"dataSourceOperationCode\":\"query\"}",
                        EntityListField.class));
    }

    /** 创建列表配置校验器测试实例。 */
    private EntityListConfigurationValidator validator(
            EntityFieldMapper entityFieldMapper) {
        return validator(
                entityFieldMapper,
                mock(UiConfigInterfaceReferenceValidator.class));
    }

    private EntityListConfigurationValidator validator(
            EntityFieldMapper entityFieldMapper,
            UiConfigInterfaceReferenceValidator referenceValidator) {
        return new EntityListConfigurationValidator(
                new StructuredConfigValidator(
                        new ObjectMapper()),
                new JsonDocumentCodec(
                        new ObjectMapper()),
                new ListFieldDataProviderRegistry(
                        List.of(),
                        new ObjectMapper()),
                entityFieldMapper,
                referenceValidator);
    }
}
