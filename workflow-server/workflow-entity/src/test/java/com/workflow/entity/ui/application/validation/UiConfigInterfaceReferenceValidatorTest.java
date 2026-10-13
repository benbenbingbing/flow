package com.workflow.entity.ui.application.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 发布快照接口服务引用校验测试。
 */
class UiConfigInterfaceReferenceValidatorTest {

    @Test
    void acceptsMatchingOperationContextAndEntityScope() {
        TestContext context = context("FORM", "ENTITY", "entity-a");

        assertDoesNotThrow(() ->
                context.validator().validate(formSnapshot(
                        "queryApprovers")));
    }

    @Test
    void selectedEntitiesAllowEveryListOnlyWithinTheirEntityScope() {
        TestContext context = context("LIST", "ENTITY", null);
        context.definition().setScopeEntityIds(List.of("entity-a", "entity-b"));
        context.definition().setOutputSchemaDocument("{\"type\":\"object\",\"properties\":{\"records\":{\"type\":\"array\"}}}");
        assertDoesNotThrow(() -> context.validator().validateListDraft("list-a", "entity-a", "service-a", List.of()));
        assertDoesNotThrow(() -> context.validator().validateListDraft("list-a2", "entity-a", "service-a", List.of()));
        assertDoesNotThrow(() -> context.validator().validateListDraft("list-b", "entity-b", "service-a", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> context.validator().validateListDraft("list-c", "entity-c", "service-a", List.of()));
    }

    @Test
    void rejectsOperationContextMismatch() {
        TestContext context = context("LIST", "ENTITY", "entity-a");

        assertThrows(
                IllegalArgumentException.class,
                () -> context.validator().validate(formSnapshot(
                        "queryApprovers")));
    }

    @Test
    void rejectsMissingOperationCode() {
        TestContext context = context("FORM", "FORM", "form-a");
        Map<String, Object> snapshot = Map.of(
                "configType", "FORM",
                "form", Map.of(
                        "id", "form-a",
                        "entityId", "entity-a"),
                "nodes", List.of(Map.of(
                        "dataSourceBindings",
                        Map.of(
                                "FIELD_OPTIONS",
                                Map.of("serviceId", "service-a")))));

        assertThrows(
                IllegalArgumentException.class,
                () -> context.validator().validate(snapshot));
    }

    @Test
    void acceptsDedicatedListQueryOperationSlot() {
        TestContext context = context(
                "LIST",
                "LIST",
                "list-a");
        Map<String, Object> snapshot = Map.of(
                "configType", "LIST",
                "list", Map.of(
                        "id", "list-a",
                        "entityId", "entity-a",
                        "queryInterfaceExtensionId", "service-a"));

        assertDoesNotThrow(() ->
                context.validator().validate(snapshot));
    }

    @Test
    void acceptsDirectListColumnExtensionWithReadObjectContract() {
        TestContext context = context(
                "LIST", "ENTITY", "entity-a");
        EntityListField field = new EntityListField();
        field.setInterfaceExtensionId("service-a");

        assertDoesNotThrow(() -> context.validator().validateListDraft(
                "list-a", "entity-a", null, List.of(field)));
    }

    @Test
    void rejectsDirectListReferenceWhenExtensionIsMissing() {
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        UiConfigInterfaceReferenceValidator validator =
                new UiConfigInterfaceReferenceValidator(
                        mock(UiExtensionDefinitionMapper.class),
                        new JsonDocumentCodec(objectMapper));

        assertThrows(IllegalArgumentException.class,
                () -> validator.validateListDraft(
                        "list-a", "entity-a", "missing", List.of()));
    }

    @Test
    void rejectsListColumnWriteInterface() {
        TestContext context = context(
                "LIST", "ENTITY", "entity-a");
        context.definition().setInterfaceKind("WRITE");
        EntityListField field = new EntityListField();
        field.setInterfaceExtensionId("service-a");

        assertThrows(IllegalArgumentException.class,
                () -> context.validator().validateListDraft(
                        "list-a", "entity-a", null, List.of(field)));
    }

    @Test
    void acceptsCascaderInterfaceFromPersistedNodeJsonDocuments() {
        TestContext context = cascaderContext();
        assertDoesNotThrow(() -> context.validator().validate(cascaderSnapshot(true)));
    }

    @Test
    void acceptsCascaderInterfaceFromStructuredNodeWithoutRevalidatingItsPropsAsANode() {
        TestContext context = cascaderContext();
        assertDoesNotThrow(() -> context.validator().validate(cascaderSnapshot(false)));
    }

    @Test
    void rejectsCascaderWriteInterface() {
        TestContext context = cascaderContext();
        context.definition().setInterfaceKind("WRITE");
        assertThrows(IllegalArgumentException.class, () -> context.validator().validate(cascaderSnapshot(true)));
    }

    @Test
    void rejectsCascaderInterfaceWithWrongScopeOrOutput() {
        TestContext context = cascaderContext();
        context.definition().setScopeId("other-form");
        assertThrows(IllegalArgumentException.class, () -> context.validator().validate(cascaderSnapshot(true)));
        context.definition().setScopeId("form-a");
        context.definition().setOutputSchemaDocument("{\"type\":\"object\"}");
        assertThrows(IllegalArgumentException.class, () -> context.validator().validate(cascaderSnapshot(true)));
    }

    @Test
    void rejectsMalformedStaticOptionsInPersistedNode() {
        TestContext context = cascaderContext();
        Map<String, Object> snapshot = Map.of("configType", "FORM",
                "form", Map.of("id", "form-a", "entityId", "entity-a"),
                "nodes", List.of(Map.of("nodeType", "FIELD",
                        "propsDocument", "{\"componentType\":\"cascader\",\"componentProps\":{\"cascaderOptions\":[{\"label\":\"无编码\"}]}}")));
        assertThrows(IllegalArgumentException.class, () -> context.validator().validate(snapshot));
    }

    private TestContext cascaderContext() {
        TestContext context = context("FORM", "FORM", "form-a");
        context.definition().setOutputSchemaDocument("{\"type\":\"array\"}");
        return context;
    }

    private Map<String, Object> cascaderSnapshot(boolean documents) {
        Map<String, Object> props = Map.of("componentType", "cascader", "componentProps",
                Map.of("cascaderSource", "INTERFACE", "cascaderLoadMode", "CHILDREN"));
        Map<String, Object> bindings = Map.of("FIELD_OPTIONS", Map.of("extensionId", "service-a"));
        JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
        return Map.of("configType", "FORM", "form", Map.of("id", "form-a", "entityId", "entity-a"),
                "nodes", List.of(Map.of("nodeType", "FIELD",
                        documents ? "propsDocument" : "props", documents ? codec.write(props, "test") : props,
                        documents ? "dataSourceBindingsDocument" : "dataSourceBindings",
                        documents ? codec.write(bindings, "test") : bindings)));
    }

    private TestContext context(
            String contextType,
            String scopeType,
            String scopeId) {
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec =
                new JsonDocumentCodec(objectMapper);
        UiExtensionDefinitionMapper mapper =
                mock(UiExtensionDefinitionMapper.class);
        UiExtensionDefinition definition =
                new UiExtensionDefinition();
        definition.setId("service-a");
        definition.setExtensionType("INTERFACE");
        definition.setEnabled(true);
        definition.setDeleted(0);
        definition.setScopeType(scopeType);
        definition.setScopeId(scopeId);
        definition.setProviderOperationCode("queryApprovers");
        definition.setInterfaceKind("READ");
        definition.setInterfaceContextType(contextType);
        definition.setInputSchemaDocument("{}");
        definition.setOutputSchemaDocument(codec.write(
                "LIST".equals(contextType)
                        ? Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "records",
                                        Map.of("type", "array")))
                        : Map.of(),
                "测试接口输出 Schema"));
        when(mapper.selectById("service-a"))
                .thenReturn(definition);
        return new TestContext(
                new UiConfigInterfaceReferenceValidator(
                        mapper,
                        codec),
                definition);
    }

    private Map<String, Object> formSnapshot(
            String operationCode) {
        return Map.of(
                "configType", "FORM",
                "form", Map.of(
                        "id", "form-a",
                        "entityId", "entity-a"),
                "nodes", List.of(Map.of(
                        "dataSourceBindings",
                        Map.of(
                                "FIELD_OPTIONS",
                                Map.of(
                                        "serviceId", "service-a",
                                        "operationCode", operationCode)))));
    }

    /**
     * 测试依赖集合。
     *
     * @param validator 发布快照接口引用校验器
     */
    private record TestContext(
            UiConfigInterfaceReferenceValidator validator,
            UiExtensionDefinition definition) {
    }
}
