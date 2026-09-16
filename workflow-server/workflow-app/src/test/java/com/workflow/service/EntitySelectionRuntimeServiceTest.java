package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.SystemEntityService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.EntitySelectionRuntimeService;
import com.workflow.entity.ui.application.UiEventBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 单选实体选择后回填的权威数据加载测试。
 */
class EntitySelectionRuntimeServiceTest {

    private EntityDataDynamicService dataService;
    private SystemEntityService systemEntityService;
    private SystemEntityReadService systemEntityReadService;
    private EntityDefinitionMapper definitionMapper;
    private EntitySelectionRuntimeService service;

    @BeforeEach
    void setUp() {
        dataService = mock(EntityDataDynamicService.class);
        systemEntityService = mock(SystemEntityService.class);
        systemEntityReadService = mock(SystemEntityReadService.class);
        definitionMapper = mock(EntityDefinitionMapper.class);
        service = new EntitySelectionRuntimeService(
                dataService,
                systemEntityService,
                systemEntityReadService,
                definitionMapper,
                new ObjectMapper().findAndRegisterModules());
    }

    @ParameterizedTest
    @CsvSource({"true, false", "true, true", "false, false", "false, true"})
    void reloadsSystemEntityThroughCustomReferenceWithoutChangingMappingShape(
            boolean referenceById,
            boolean legacySnapshot) {
        if (referenceById) {
            EntityDefinition definition = new EntityDefinition();
            definition.setId("system-user-entity");
            definition.setEntityCode("sys_user");
            definition.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
            when(definitionMapper.selectById("system-user-entity"))
                    .thenReturn(definition);
        }
        when(systemEntityReadService.isSystemEntity("sys_user"))
                .thenReturn(true);
        EntityDataDTO detail = new EntityDataDTO();
        detail.setId("user-1");
        detail.setName("二级审批人");
        detail.setCode("approver");
        detail.setData(Map.of(
                "username", "approver",
                "nickname", "二级审批人"));
        when(systemEntityReadService.findById("sys_user", "user-1"))
                .thenReturn(detail);
        Map<String, Object> snapshot = referenceSnapshot(
                "REFERENCE",
                "CUSTOM",
                referenceById ? "system-user-entity" : null,
                referenceById ? null : "sys_user",
                "picker");
        if (legacySnapshot) {
            // 已发布的旧表单仍从 legacyFields 读取引用配置，无需重新保存或发布。
            Map<?, ?> node = (Map<?, ?>) ((List<?>) snapshot.get("nodes")).get(0);
            snapshot = Map.of(
                    "nodes", List.of(),
                    "legacyFields", List.of(node.get("propsDocument")));
        }

        Map<?, ?> selection = (Map<?, ?>) service.resolve(
                request(Map.of(
                        "id", "user-1",
                        "name", "伪造名称",
                        "code", "fake",
                        "data", Map.of("username", "fake", "password", "fake"),
                        "selectionData", Map.of("display", "所选用户"))),
                chain(snapshot));

        assertEquals("user-1", selection.get("id"));
        assertEquals("二级审批人", selection.get("name"));
        assertEquals("approver", selection.get("code"));
        assertEquals("CUSTOM", selection.get("entityType"));
        assertEquals(detail.getData(), selection.get("data"));
        assertFalse(((Map<?, ?>) selection.get("data")).containsKey("password"));
        assertEquals(Map.of("display", "所选用户"), selection.get("selectionData"));
        verify(systemEntityReadService).findById("sys_user", "user-1");
        verifyNoInteractions(dataService, systemEntityService);
    }

    @ParameterizedTest
    @CsvSource({"sys_menu, 无权访问", "sys_user, 数据不存在或无权访问"})
    void systemReferenceReadFailureDoesNotFallBackToClientOrDynamicData(
            String entityCode,
            String message) {
        when(systemEntityReadService.isSystemEntity(entityCode))
                .thenReturn(true);
        ForbiddenException failure = new ForbiddenException(message);
        when(systemEntityReadService.findById(entityCode, "denied"))
                .thenThrow(failure);

        ForbiddenException actual = assertThrows(
                ForbiddenException.class,
                () -> service.resolve(
                        request(Map.of("id", "denied", "name", "伪造名称")),
                        chain(referenceSnapshot(
                                "REFERENCE", "CUSTOM", null, entityCode, null))));

        assertSame(failure, actual);
        verifyNoInteractions(dataService, systemEntityService);
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
        verifyNoInteractions(systemEntityReadService, systemEntityService);
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
        verifyNoInteractions(systemEntityReadService, systemEntityService);
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
        verifyNoInteractions(systemEntityReadService, dataService);
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
