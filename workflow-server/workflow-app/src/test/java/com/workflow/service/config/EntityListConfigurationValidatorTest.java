package com.workflow.service.config;

import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import com.workflow.entity.ui.application.validation.UiConfigInterfaceReferenceValidator;
import com.workflow.entity.ui.application.validation.StructuredConfigValidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import com.workflow.entity.list.extension.TemplateListFieldDataProvider;
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

    /** Provider 声明支持查询也不能绕过统一约束，保存与发布都复用本校验器。 */
    @Test
    void rejectsVirtualQueriesAndSorts() {
        var mapper = mock(EntityFieldMapper.class);
        var validator = validator(mapper);
        var field = new EntityListField();
        field.setFieldId("virtual_summary");
        field.setFieldCode("summary");
        field.setDataSourceType("FIELD_TEMPLATE");
        field.setIsQuery(true);
        var dto = new EntityListConfigDTO();
        dto.setEntityId("entity-1");
        dto.setEntityCode("expense");
        dto.setListKey("default");
        dto.setFields(List.of(field));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> validator.validate(dto))
                .getMessage().contains("虚拟列"));

        // 没有列配置时仍应检查默认排序，防止仅修改 viewConfig 绕过字段规则。
        dto.setFields(null);
        dto.setViewConfig(Map.of("table", Map.of("defaultSortField", "summary")));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> validator.validate(dto))
                .getMessage().contains("排序字段"));
    }

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

    /** 扩展接口为独立来源，虚拟列无需伪造实体字段或模板参数。 */
    @Test
    void acceptsInterfaceVirtualColumnAndValidatesReference() {
        var references = mock(UiConfigInterfaceReferenceValidator.class);
        var validator = validator(mock(EntityFieldMapper.class), references);
        var field = column("INTERFACE");
        field.setInterfaceExtensionId("column-extension");
        var dto = listWith(field);

        assertDoesNotThrow(() -> validator.validate(dto));
        verify(references).validateListDraft(null, "entity-1", null, List.of(field));
    }

    /** 来源与接口绑定必须一致，直接 API 请求也不能恢复旧的隐式覆盖行为。 */
    @Test
    void rejectsMissingInterfaceConflictingSourceAndCustomProviderType() {
        var validator = validator(mock(EntityFieldMapper.class));
        var field = column("INTERFACE");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validate(listWith(field))).getMessage().contains("必须选择接口"));

        field.setInterfaceExtensionId("column-extension");
        for (String type : List.of("ENTITY_FIELD", "FIELD_TEMPLATE")) {
            field.setDataSourceType(type);
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> validator.validate(listWith(field))).getMessage().contains("只有扩展接口"));
        }
        field.setInterfaceExtensionId(null);
        field.setDataSourceType("PROJECT_CUSTOM_FIELD");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validate(listWith(field))).getMessage().contains("不支持的列取值方式"));
    }

    /** 实体列和虚拟列都可组合文本，但必须填写模板且不能把结果用于查询或排序。 */
    @Test
    void validatesTemplateForBothPhysicalAndVirtualColumns() {
        var mapper = mock(EntityFieldMapper.class);
        var physical = new EntityField();
        physical.setId("field-1");
        physical.setFieldCode("summary");
        when(mapper.findByEntityId("entity-1")).thenReturn(List.of(physical));
        var validator = validator(mapper);
        var field = column("FIELD_TEMPLATE");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validate(listWith(field))).getMessage().contains("template"));

        field.setDataSourceConfig("{\"template\":\"${code} - ${name}\"}");
        assertDoesNotThrow(() -> validator.validate(listWith(field)));
        field.setFieldId("field-1");
        assertDoesNotThrow(() -> validator.validate(listWith(field)));

        field.setIsQuery(true);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(listWith(field)));
        field.setIsQuery(false);
        var dto = listWith(field);
        dto.setViewConfig(Map.of("table", Map.of("defaultSortField", "summary")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(dto));
    }

    private EntityListField column(String type) {
        var field = new EntityListField();
        field.setFieldId("virtual_summary");
        field.setFieldCode("summary");
        field.setDataSourceType(type);
        field.setIsQuery(false);
        return field;
    }

    private EntityListConfigDTO listWith(EntityListField field) {
        var dto = new EntityListConfigDTO();
        dto.setEntityId("entity-1");
        dto.setEntityCode("expense");
        dto.setListKey("default");
        dto.setFields(List.of(field));
        return dto;
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
                        List.of(new TemplateListFieldDataProvider(new ObjectMapper())),
                        new ObjectMapper()),
                entityFieldMapper,
                referenceValidator);
    }
}
