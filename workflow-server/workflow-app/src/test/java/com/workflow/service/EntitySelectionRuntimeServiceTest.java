package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.SystemEntityService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.EntitySelectionRuntimeService;
import com.workflow.entity.ui.application.UiEventBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单选实体选择后回填的权威数据加载测试。
 */
class EntitySelectionRuntimeServiceTest {

    private EntityDataDynamicService dataService;
    private SystemEntityService systemEntityService;
    private EntityDefinitionMapper definitionMapper;
    private EntitySelectionRuntimeService service;

    @BeforeEach
    void setUp() {
        dataService = mock(EntityDataDynamicService.class);
        systemEntityService = mock(SystemEntityService.class);
        definitionMapper = mock(EntityDefinitionMapper.class);
        service = new EntitySelectionRuntimeService(
                dataService,
                systemEntityService,
                definitionMapper,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void reloadsAuthoritativeCustomEntityDetailBySelectedId() {
        EntityDefinition definition = new EntityDefinition();
        definition.setId("customer-entity");
        definition.setEntityCode("customer");
        when(definitionMapper.selectById("customer-entity"))
                .thenReturn(definition);
        EntityDataDTO detail = new EntityDataDTO();
        detail.setId("customer-1");
        detail.setName("权威客户");
        detail.setData(Map.of(
                "phone", "13800000000",
                "level", "A"));
        when(dataService.findAccessibleById(
                "customer",
                "customer-1",
                "picker"))
                .thenReturn(detail);

        Object resolved = service.resolve(
                request(Map.of(
                        "id", "customer-1",
                        "name", "伪造名称",
                        "data", Map.of("phone", "fake"),
                        "selectionData", Map.of("display", "客户一"))),
                chain(referenceSnapshot(
                        "REFERENCE",
                        "CUSTOM",
                        "customer-entity",
                        "",
                        "picker")));

        Map<?, ?> selection = (Map<?, ?>) resolved;
        assertEquals("customer-1", selection.get("id"));
        assertEquals("权威客户", selection.get("name"));
        assertEquals(
                "13800000000",
                ((Map<?, ?>) selection.get("data")).get("phone"));
        assertEquals(
                Map.of("display", "客户一"),
                selection.get("selectionData"));
        verify(dataService).findAccessibleById(
                "customer",
                "customer-1",
                "picker");
    }

    @Test
    void propagatesPermissionFailureWithoutUsingClientData() {
        EntityDefinition definition = new EntityDefinition();
        definition.setId("customer-entity");
        definition.setEntityCode("customer");
        when(definitionMapper.selectById("customer-entity"))
                .thenReturn(definition);
        when(dataService.findAccessibleById(
                "customer",
                "denied",
                null))
                .thenThrow(new ForbiddenException("无权访问"));

        assertThrows(
                ForbiddenException.class,
                () -> service.resolve(
                        request(Map.of(
                                "id", "denied",
                                "data", Map.of("phone", "fake"))),
                        chain(referenceSnapshot(
                                "REFERENCE",
                                "CUSTOM",
                                "customer-entity",
                                "",
                                null))));
    }

    @Test
    void clearSelectionKeepsNullForEmptyHandlingPolicies() {
        Object resolved = service.resolve(
                request(null),
                chain(referenceSnapshot(
                        "REFERENCE",
                        "CUSTOM",
                        "customer-entity",
                        "customer",
                        null)));

        assertNull(resolved);
        verify(dataService, never()).findAccessibleById(
                "customer",
                "",
                null);
    }

    @Test
    void multiReferenceRemainsOutsideSingleSelectionHydration() {
        List<Map<String, Object>> selected =
                List.of(Map.of("id", "customer-1"));

        Object resolved = service.resolve(
                request(selected),
                chain(referenceSnapshot(
                        "MULTI_REFERENCE",
                        "CUSTOM",
                        "customer-entity",
                        "customer",
                        null)));

        assertSame(selected, resolved);
        verify(dataService, never()).findAccessibleById(
                "customer",
                "customer-1",
                null);
    }

    @Test
    void loadsSystemEntityUsingConfiguredReferenceType() {
        when(systemEntityService.selectById("DEPT", "dept-1"))
                .thenReturn(Map.of(
                        "id", "dept-1",
                        "name", "研发部",
                        "code", "RD"));

        Object resolved = service.resolve(
                request(Map.of("id", "dept-1")),
                chain(referenceSnapshot(
                        "DEPT",
                        "DEPT",
                        null,
                        null,
                        null)));

        assertEquals(
                "研发部",
                ((Map<?, ?>) resolved).get("name"));
        verify(systemEntityService)
                .selectById("DEPT", "dept-1");
    }

    private UiEventExecuteRequest request(Object selection) {
        UiEventExecuteRequest request =
                new UiEventExecuteRequest();
        request.setEventCode("ENTITY_SELECTED");
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setTargetType("FIELD");
        request.setTargetKey("customerId");
        request.setSelection(selection);
        return request;
    }

    private UiEventBindingService.ResolvedEventChain chain(
            Map<String, Object> snapshot) {
        return new UiEventBindingService.ResolvedEventChain(
                List.of(Map.of(
                        "stepCode",
                        "ENTITY_SELECTION_FILL",
                        "strategy",
                        "AFTER",
                        "outputMapping",
                        List.of(Map.of(
                                "sourcePath",
                                "selection.data.phone",
                                "targetPath",
                                "form.phone")))),
                "release-1",
                1,
                "entity-1",
                "change_request",
                null,
                snapshot);
    }

    private Map<String, Object> referenceSnapshot(
            String fieldType,
            String refEntityType,
            String refEntityId,
            String entityCode,
            String listKey) {
        Map<String, Object> refConfig =
                new LinkedHashMap<>();
        refConfig.put("refEntityType", refEntityType);
        refConfig.put("refEntityId", refEntityId);
        refConfig.put("entityCode", entityCode);
        refConfig.put("listKey", listKey);
        Map<String, Object> props =
                new LinkedHashMap<>();
        props.put("fieldCode", "customerId");
        props.put("fieldType", fieldType);
        props.put("componentType", fieldType);
        props.put(
                "componentProps",
                Map.of("refConfig", refConfig));
        return Map.of(
                "configType",
                "FORM",
                "nodes",
                List.of(Map.of(
                        "nodeKey",
                        "customerId",
                        "propsDocument",
                        props)),
                "legacyFields",
                List.of());
    }
}
