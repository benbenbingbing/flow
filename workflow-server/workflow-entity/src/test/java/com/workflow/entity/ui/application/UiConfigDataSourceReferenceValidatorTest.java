package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
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
class UiConfigDataSourceReferenceValidatorTest {

    @Test
    void acceptsMatchingOperationContextAndEntityScope() {
        TestContext context = context("FORM", "ENTITY", "entity-a");

        assertDoesNotThrow(() ->
                context.validator().validate(formSnapshot(
                        "queryApprovers")));
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
                        "queryDataSourceId", "service-a",
                        "queryOperationCode",
                        "queryApprovers"));

        assertDoesNotThrow(() ->
                context.validator().validate(snapshot));
    }

    private TestContext context(
            String contextType,
            String scopeType,
            String scopeId) {
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        JsonDocumentCodec codec =
                new JsonDocumentCodec(objectMapper);
        UiDataSourceDefinitionMapper mapper =
                mock(UiDataSourceDefinitionMapper.class);
        UiDataSourceDefinition definition =
                new UiDataSourceDefinition();
        definition.setId("service-a");
        definition.setEnabled(true);
        definition.setDeleted(0);
        definition.setScopeType(scopeType);
        definition.setScopeId(scopeId);
        definition.setOperationsDocument(codec.write(
                List.of(Map.of(
                        "code", "queryApprovers",
                        "name", "查询审批人",
                        "kind", "READ",
                        "contextType", contextType,
                        "inputSchema", Map.of(),
                        "outputSchema",
                        "LIST".equals(contextType)
                                ? Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "records",
                                                Map.of(
                                                        "type",
                                                        "array")))
                                : Map.of())),
                "测试接口操作"));
        when(mapper.selectById("service-a"))
                .thenReturn(definition);
        return new TestContext(
                new UiConfigDataSourceReferenceValidator(
                        mapper,
                        codec));
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
            UiConfigDataSourceReferenceValidator validator) {
    }
}
