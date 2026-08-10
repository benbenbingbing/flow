package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.response.UiAvailableOperation;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiAvailableOperationServiceTest {

    private UiDataSourceDefinitionMapper sourceMapper;
    private EntityDefinitionMapper definitionMapper;
    private EntityFormMapper formMapper;
    private EntityListConfigMapper listMapper;
    private ObjectMapper objectMapper;
    private UiAvailableOperationService service;

    @BeforeEach
    void setUp() {
        sourceMapper = mock(UiDataSourceDefinitionMapper.class);
        definitionMapper = mock(EntityDefinitionMapper.class);
        formMapper = mock(EntityFormMapper.class);
        listMapper = mock(EntityListConfigMapper.class);
        objectMapper = new ObjectMapper();
        service = new UiAvailableOperationService(
                sourceMapper,
                definitionMapper,
                formMapper,
                listMapper,
                objectMapper,
                mock(UiConfigurationAccessService.class));

        EntityDefinition entity = entity("entity-a", "expense");
        when(definitionMapper.selectById("entity-a"))
                .thenReturn(entity);
        when(definitionMapper.selectById("entity-b"))
                .thenReturn(entity("entity-b", "order"));

        EntityForm form = new EntityForm();
        form.setId("form-a");
        form.setEntityId("entity-a");
        when(formMapper.selectById("form-a"))
                .thenReturn(form);

        EntityListConfig list = new EntityListConfig();
        list.setId("list-a");
        list.setEntityId("entity-a");
        when(listMapper.selectById("list-a"))
                .thenReturn(list);
    }

    @Test
    void filtersFormOperationsByScopeContextKindAndSchema() throws Exception {
        when(sourceMapper.selectList(any())).thenReturn(List.of(
                definition(
                        "entity-form",
                        "ENTITY",
                        "entity-a",
                        "STATIC_OPTIONS",
                        operation(
                                "options",
                                "FORM",
                                "READ",
                                arraySchema())),
                definition(
                        "form-only",
                        "FORM",
                        "form-a",
                        "STATIC_OPTIONS",
                        operation(
                                "formOptions",
                                "FORM",
                                "READ",
                                arraySchema())),
                definition(
                        "other-entity",
                        "ENTITY",
                        "entity-b",
                        "STATIC_OPTIONS",
                        operation(
                                "other",
                                "FORM",
                                "READ",
                                arraySchema())),
                definition(
                        "wrong-context",
                        "ENTITY",
                        "entity-a",
                        "STATIC_OPTIONS",
                        operation(
                                "listOptions",
                                "LIST",
                                "READ",
                                arraySchema())),
                definition(
                        "wrong-kind",
                        "ENTITY",
                        "entity-a",
                        "STATIC_OPTIONS",
                        operation(
                                "writeOptions",
                                "FORM",
                                "WRITE",
                                arraySchema())),
                definition(
                        "wrong-schema",
                        "ENTITY",
                        "entity-a",
                        "STATIC_OPTIONS",
                        operation(
                                "objectOptions",
                                "FORM",
                                "READ",
                                Map.of("type", "object")))));

        List<UiAvailableOperation> result = service.available(
                "FORM",
                "form-a",
                "FIELD_OPTIONS");

        assertEquals(
                List.of("entity-form", "form-only"),
                result.stream()
                        .map(UiAvailableOperation::serviceId)
                        .toList());
    }

    @Test
    void listQueryRequiresListReadOperationWithPageSchema() throws Exception {
        when(sourceMapper.selectList(any())).thenReturn(List.of(
                definition(
                        "page-query",
                        "LIST",
                        "list-a",
                        "STATIC_OPTIONS",
                        operation(
                                "query",
                                "LIST",
                                "READ",
                                pageSchema())),
                definition(
                        "plain-object",
                        "ENTITY",
                        "entity-a",
                        "STATIC_OPTIONS",
                        operation(
                                "plain",
                                "LIST",
                                "READ",
                                Map.of("type", "object")))));

        List<UiAvailableOperation> result = service.available(
                "LIST",
                "list-a",
                "LIST_QUERY");

        assertEquals(
                List.of("page-query"),
                result.stream()
                        .map(UiAvailableOperation::serviceId)
                        .toList());
    }

    @Test
    void listLoadEventDoesNotReuseListQueryPageSchema() throws Exception {
        when(sourceMapper.selectList(any())).thenReturn(List.of(
                definition(
                        "load-audit",
                        "LIST",
                        "list-a",
                        "STATIC_OPTIONS",
                        operation(
                                "auditLoad",
                                "LIST",
                                "READ",
                                Map.of("type", "object")))));

        List<UiAvailableOperation> result = service.available(
                "LIST",
                "list-a",
                "LIST_LOAD");

        assertEquals(
                List.of("load-audit"),
                result.stream()
                        .map(UiAvailableOperation::serviceId)
                        .toList());
    }

    @Test
    void entityMutationRequiresEntityWriteOperationInSameEntity() throws Exception {
        when(sourceMapper.selectList(any())).thenReturn(List.of(
                definition(
                        "entity-write",
                        "ENTITY",
                        "entity-a",
                        "REGISTERED_PROVIDER",
                        operation(
                                "prepare",
                                "ENTITY",
                                "WRITE",
                                Map.of("type", "object"))),
                definition(
                        "entity-read",
                        "ENTITY",
                        "entity-a",
                        "STATIC_OPTIONS",
                        operation(
                                "inspect",
                                "ENTITY",
                                "READ",
                                Map.of("type", "object"))),
                definition(
                        "other-write",
                        "ENTITY",
                        "entity-b",
                        "REGISTERED_PROVIDER",
                        operation(
                                "prepareOther",
                                "ENTITY",
                                "WRITE",
                                Map.of("type", "object")))));

        List<UiAvailableOperation> result = service.available(
                "ENTITY",
                "entity-a",
                "ENTITY_MUTATION_PREPARE");

        assertEquals(
                List.of("entity-write"),
                result.stream()
                        .map(UiAvailableOperation::serviceId)
                        .toList());
    }

    private EntityDefinition entity(
            String id,
            String code) {
        EntityDefinition value = new EntityDefinition();
        value.setId(id);
        value.setEntityCode(code);
        value.setEntityName(code);
        return value;
    }

    private UiDataSourceDefinition definition(
            String id,
            String scopeType,
            String scopeId,
            String sourceType,
            Map<String, Object> operation) throws Exception {
        UiDataSourceDefinition value =
                new UiDataSourceDefinition();
        value.setId(id);
        value.setSourceCode(id);
        value.setSourceName(id);
        value.setSourceType(sourceType);
        value.setScopeType(scopeType);
        value.setScopeId(scopeId);
        value.setEnabled(true);
        value.setDeleted(0);
        value.setOperationsDocument(
                objectMapper.writeValueAsString(
                        List.of(operation)));
        return value;
    }

    private Map<String, Object> operation(
            String code,
            String contextType,
            String kind,
            Map<String, Object> outputSchema) {
        return Map.of(
                "code", code,
                "name", code,
                "contextType", contextType,
                "kind", kind,
                "outputSchema", outputSchema);
    }

    private Map<String, Object> arraySchema() {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "object"));
    }

    private Map<String, Object> pageSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "records", arraySchema(),
                        "total", Map.of("type", "integer")));
    }
}
