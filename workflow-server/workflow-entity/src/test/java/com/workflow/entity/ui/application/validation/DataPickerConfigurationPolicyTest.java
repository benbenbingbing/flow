package com.workflow.entity.ui.application.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 发布和执行边界的业务断言，覆盖可绕过设计器直接提交配置的情况。 */
class DataPickerConfigurationPolicyTest {
    private static final Map<String, Object> SCHEMA = Map.of("schemaVersion", "1", "valueField", "code", "labelField", "name",
            "fields", List.of(Map.of("code", "code", "type", "STRING"), Map.of("code", "name", "type", "STRING")),
            "columns", List.of(Map.of("field", "name")));
    private static final Map<String, Object> BINDINGS = Map.of(
            "FIELD_PICKER_SCHEMA", Map.of("extensionId", "describe"),
            "FIELD_PICKER_QUERY", Map.of("extensionId", "query"),
            "FIELD_PICKER_RESOLVE", Map.of("extensionId", "resolve"));

    @Test
    void publicationRequiresCompleteBindingsAndStableStringIdentity() {
        assertEquals(3, DataPickerConfigurationPolicy.validate(Map.of("pickerSchema", SCHEMA), BINDINGS, "customer", "STRING").size());
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validate(Map.of(), BINDINGS, "customer", "STRING"));
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validate(Map.of("pickerSchema", SCHEMA), Map.of(), "customer", "STRING"));
        var changed = new LinkedHashMap<>(SCHEMA);
        changed.put("fields", List.of(Map.of("code", "code", "type", "INTEGER"), Map.of("code", "name", "type", "STRING")));
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validateSchema(changed));
        var badBindings = new LinkedHashMap<>(BINDINGS);
        badBindings.put("FIELD_PICKER_SCHEMA", Map.of("extensionId", "describe", "outputMapping", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validate(Map.of("pickerSchema", SCHEMA), badBindings, "customer", "STRING"));
    }

    @Test
    void defaultAndStaticDescriptionsOnlyRequireQueryAndResolve() {
        var dataBindings = new LinkedHashMap<>(BINDINGS);
        dataBindings.remove("FIELD_PICKER_SCHEMA");
        assertEquals(2, DataPickerConfigurationPolicy.validate(Map.of(), dataBindings, "customer", "STRING").size());
        assertEquals(2, DataPickerConfigurationPolicy.validate(
                Map.of("pickerSchemaSource", "MANUAL", "pickerSchema", SCHEMA), dataBindings, "customer", "STRING").size());
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validate(
                Map.of("pickerSchemaSource", "INTERFACE", "pickerSchema", SCHEMA), dataBindings, "customer", "STRING"));
        Map<String, Object> queryOutput = Map.of("properties", Map.of("records", Map.of("items",
                Map.of("properties", Map.of("code", Map.of("type", "string"), "name", Map.of("type", "string"))))));
        assertDoesNotThrow(() -> DataPickerConfigurationPolicy.validateQuerySchema(SCHEMA, queryOutput));
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validateQuerySchema(SCHEMA, Map.of()));
    }

    @Test
    void typedValueRequiresCompatibleDeclaredSourceWhileRecordIdentityStaysString() {
        for (String type : List.of("STRING", "TEXT", "INTEGER", "LONG", "DECIMAL", "DATE", "DATETIME", "BOOLEAN", "RICH_TEXT")) {
            var schema = new LinkedHashMap<>(SCHEMA);
            schema.put("fields", List.of(Map.of("code", "code", "type", "STRING"),
                    Map.of("code", "name", "type", "STRING"), Map.of("code", "businessValue", "type", type)));
            var props = Map.of("pickerSchema", schema, "pickerValueMode", "FIELD", "pickerWriteField", "businessValue");
            assertEquals(3, DataPickerConfigurationPolicy.validate(props, BINDINGS, "customer", type).size());
            assertThrows(IllegalArgumentException.class,
                    () -> DataPickerConfigurationPolicy.validate(props, BINDINGS, "customer", "SELECT"));
        }
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validate(
                Map.of("pickerSchema", SCHEMA, "pickerWriteField", "name"), BINDINGS, "customer", "DATE"));
    }

    @Test
    void entityListRequiresAnActiveFilterContractAndNoBusinessInterfaces() {
        var schema = new LinkedHashMap<>(SCHEMA);
        schema.put("valueField", "id");
        schema.put("fields", List.of(Map.of("code", "id", "type", "STRING"),
                Map.of("code", "name", "type", "STRING"), Map.of("code", "staffNo", "type", "STRING")));
        var props = Map.of("pickerSource", "ENTITY_LIST", "pickerSchema", schema, "pickerList", Map.of(
                "entityId", "e", "entityCode", "member", "listKey", "active",
                "parameterMappings", List.of(Map.of("parameter", "projectId", "sourceType", "ENTITY_FIELD", "sourceField", "project_id"))));
        assertTrue(DataPickerConfigurationPolicy.validate(props, Map.of(), "member", "STRING").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validate(props, BINDINGS, "member", "STRING"));
        var view = Map.of("inputParameterSchema", Map.of("properties", Map.of("projectId", Map.of("type", "string"))),
                "inputParameterBindings", List.of(Map.of("usage", "FILTER", "parameter", "projectId")));
        var types = Map.of("id", "STRING", "name", "STRING", "staffNo", "STRING");
        assertDoesNotThrow(() -> DataPickerConfigurationPolicy.validatePublishedList(props, new LinkedHashMap<>(view), types));
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validatePublishedList(props, Map.of(), types));
    }

    @Test
    void rejectsInvalidMappingSourcesDuplicateTargetsAndPrototypePaths() {
        for (var mappings : List.of(
                List.of(mapping("missing", "contact")),
                List.of(mapping("name", "customer")),
                List.of(mapping("name", "constructor")),
                List.of(mapping("name", "contact"), mapping("code", "contact")))) {
            assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validate(
                    Map.of("pickerSchema", SCHEMA, "pickerMappings", mappings), BINDINGS, "customer", "STRING"));
        }
    }

    @Test
    void executionBoundsPaginationAndRequiresExactStringKeys() {
        assertDoesNotThrow(() -> DataPickerConfigurationPolicy.validateRequest("FIELD_PICKER_QUERY", Map.of("picker", Map.of("pageNum", 1, "pageSize", 20, "keyword", "客户"))));
        assertDoesNotThrow(() -> DataPickerConfigurationPolicy.validateRequest("FIELD_PICKER_RESOLVE", Map.of("picker", Map.of("values", List.of("001")))));
        for (Object values : List.of(List.of(1), List.of("001", "001"), List.of(), List.of(""))) {
            assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validateRequest("FIELD_PICKER_RESOLVE", Map.of("picker", Map.of("values", values))));
        }
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validateRequest("FIELD_PICKER_QUERY", Map.of("picker", Map.of("pageNum", 1.5, "pageSize", 20))));
        assertThrows(IllegalArgumentException.class, () -> DataPickerConfigurationPolicy.validateRequest("FIELD_PICKER_QUERY", Map.of("picker", Map.of("pageNum", 1, "pageSize", 201))));
    }

    @Test
    void publishingChecksReadContractsAndActualTargetFieldTypes() {
        var mapper = mock(UiExtensionDefinitionMapper.class);
        var validator = new UiConfigInterfaceReferenceValidator(mapper, new JsonDocumentCodec(new ObjectMapper()));
        for (String id : List.of("describe", "query", "resolve")) {
            var definition = new UiExtensionDefinition();
            definition.setId(id); definition.setExtensionType("INTERFACE"); definition.setEnabled(true);
            definition.setInterfaceKind("READ"); definition.setInterfaceContextType("FORM"); definition.setScopeType("GLOBAL");
            definition.setOutputSchemaDocument(switch (id) {
                case "describe" -> "{\"type\":\"object\",\"properties\":{\"schemaVersion\":{\"type\":\"string\"},\"valueField\":{\"type\":\"string\"},\"labelField\":{\"type\":\"string\"},\"fields\":{\"type\":\"array\"},\"columns\":{\"type\":\"array\"}}}";
                case "query" -> "{\"type\":\"object\",\"properties\":{\"records\":{\"type\":\"array\"},\"total\":{\"type\":\"integer\"}}}";
                default -> "{\"type\":\"array\"}";
            });
            when(mapper.selectById(id)).thenReturn(definition);
        }
        assertDoesNotThrow(() -> validator.validate(snapshot("STRING")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(snapshot("INTEGER")));
        var unbound = new LinkedHashMap<>(snapshot("STRING"));
        unbound.put("nodes", List.of(Map.of("nodeType", "FIELD", "bindingType", "COMPUTED", "propsDocument", Map.of(
                "fieldCode", "customer", "fieldType", "STRING", "componentType", "data_picker", "componentProps", Map.of("pickerSchema", SCHEMA)),
                "dataSourceBindings", BINDINGS)));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unbound));
        var definition = mapper.selectById("resolve"); definition.setInterfaceKind("WRITE");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(snapshot("STRING")));
    }

    private Map<String, Object> snapshot(String targetType) {
        return Map.of("configType", "FORM", "form", Map.of("id", "f", "entityId", "e"), "nodes", List.of(
                Map.of("nodeType", "FIELD", "bindingType", "ENTITY_FIELD", "propsDocument", Map.of(
                        "fieldId", "customer-id", "fieldCode", "customer", "fieldType", "STRING", "componentType", "data_picker", "componentProps", Map.of(
                                "pickerSchema", SCHEMA, "pickerMappings", List.of(mapping("name", "contact")))), "dataSourceBindings", BINDINGS),
                Map.of("nodeType", "FIELD", "bindingType", "ENTITY_FIELD", "propsDocument", Map.of(
                        "fieldId", "contact-id", "fieldCode", "contact", "fieldType", targetType, "componentType", "input"))));
    }
    private Map<String, Object> mapping(String source, String target) {
        return Map.of("sourcePath", "selection.data." + source, "targetPath", "form." + target, "overwrite", "IF_EMPTY", "clearOnEmpty", false);
    }
}
