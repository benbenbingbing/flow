package com.workflow.migration.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigEnvironmentMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证接口范围以实体编码跨环境迁移，旧表单/列表坐标也统一恢复到实体级。 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationInterfaceScopeImportTest {
    @Mock private EntityDefinitionMapper entityMapper;
    @Mock private EntityFormMapper formMapper;
    @Mock private EntityListConfigMapper listConfigMapper;
    @Mock private EntityFormService entityFormService;
    @Mock private ConfigEnvironmentMappingMapper environmentMappingMapper;
    @Mock private UiExtensionDefinitionMapper extensionDefinitionMapper;
    @Mock private UiInterfaceExtensionService interfaceExtensionService;
    @InjectMocks private ConfigMigrationImportApplyService service;

    @Test
    void importsLegacyScopesAsEntitySelectionsWithoutRequiringFormsOrLists() {
        EntityDefinition entity = entity("target-entity-id", "order");
        when(interfaceExtensionService.save(any(UiExtensionDefinitionSaveRequest.class)))
                .thenAnswer(invocation -> {
                    UiExtensionDefinitionSaveRequest request = invocation.getArgument(0);
                    UiExtensionDefinition definition = new UiExtensionDefinition();
                    definition.setId("id-" + request.getExtensionKey());
                    return definition;
                });

        List<Map<String, Object>> interfaces = List.of(
                definition("global-api", "GLOBAL", null),
                definition("entity-api", "ENTITY", "order"),
                definition("form-api", "FORM", "order/edit"),
                definition("list-api", "LIST", "order/default"));
        Map<String, String> imported = service.applyInterfaceExtensions(entity, interfaces);
        assertEquals(4, imported.size());
        ArgumentCaptor<UiExtensionDefinitionSaveRequest> requests =
                ArgumentCaptor.forClass(UiExtensionDefinitionSaveRequest.class);
        verify(interfaceExtensionService, times(4)).save(requests.capture());
        for (UiExtensionDefinitionSaveRequest request : requests.getAllValues()) {
            assertNull(request.getScopeId(), "导入后不得保存旧表单/列表范围 ID");
            if ("global-api".equals(request.getExtensionKey())) {
                assertEquals("GLOBAL", request.getScopeType());
                assertEquals(List.of(), request.getScopeEntityIds());
            } else {
                assertEquals("ENTITY", request.getScopeType());
                assertEquals(List.of("target-entity-id"), request.getScopeEntityIds());
            }
        }
    }

    @Test
    void mapsEverySelectedEntityToTargetEnvironmentAndDeduplicates() {
        EntityDefinition current = entity("target-order-id", "order");
        EntityDefinition customer = entity("target-customer-id", "customer_target");
        ConfigEnvironmentMapping mapping = new ConfigEnvironmentMapping();
        mapping.setTargetKey("customer_target");
        when(environmentMappingMapper.selectOne(any())).thenReturn(null, mapping, mapping);
        when(entityMapper.findByEntityCode("customer_target")).thenReturn(Optional.of(customer));

        assertEquals(List.of("target-order-id", "target-customer-id"),
                service.resolveInterfaceScopeEntityIds(current, "ENTITY", Map.of(
                        "scopeEntityRefs", List.of("order", "customer_source", "customer_source"))));
    }

    @Test
    void missingOrEmptyEntityScopeCannotSilentlyBecomeGlobalOrCurrentEntity() {
        EntityDefinition current = entity("target-order-id", "order");
        assertThrows(IllegalStateException.class, () -> service.resolveInterfaceScopeEntityIds(
                current, "ENTITY", Map.of("scopeEntityRefs", List.of())));
        assertThrows(IllegalStateException.class, () -> service.resolveInterfaceScopeEntityIds(
                current, "ENTITY", Map.of("scopeEntityRefs", List.of("order", "missing"))));
        assertThrows(IllegalStateException.class, () -> service.resolveInterfaceScopeEntityIds(
                current, "ENTITY", Map.of("scopeEntityRefs", List.of("order/edit"))));
    }

    @Test
    void legacyEntityWithoutScopeRefStillMeansTheCurrentImportedEntity() {
        assertEquals(List.of("target-order-id"), service.resolveInterfaceScopeEntityIds(
                entity("target-order-id", "order"), "ENTITY", Map.of()));
    }

    @Test
    void uiReferenceOwnersAreStillPreparedAndReusedWithoutAnyInterfaceScope() {
        EntityDefinition current = entity("target-order-id", "order");
        var savedForm = new java.util.concurrent.atomic.AtomicReference<EntityForm>();
        var savedList = new java.util.concurrent.atomic.AtomicReference<EntityListConfig>();
        when(formMapper.selectByEntityIdAndFormKey("target-order-id", "edit"))
                .thenAnswer(invocation -> savedForm.get());
        when(listConfigMapper.findByEntityIdAndListKey("target-order-id", "default"))
                .thenAnswer(invocation -> savedList.get());
        when(entityFormService.saveForm(any(EntityForm.class))).thenAnswer(invocation -> {
            EntityForm form = invocation.getArgument(0);
            form.setId("target-form-id"); savedForm.set(form); return form;
        });
        doAnswer(invocation -> { savedList.set(invocation.getArgument(0)); return 1; })
                .when(listConfigMapper).insert(any(EntityListConfig.class));
        var snapshot = Map.of("forms", List.of(Map.of("formKey", "edit", "formName", "编辑订单")),
                "lists", List.of(Map.of("listKey", "default", "listName", "订单列表")));

        // 两轮宿主准备模拟重复引用，必须复用业务 key 对应记录，不能重复创建或触发接口保存。
        ReflectionTestUtils.invokeMethod(service, "prepareUiOwners", current, snapshot);
        ReflectionTestUtils.invokeMethod(service, "prepareUiOwners", current, snapshot);
        assertEquals("编辑订单", savedForm.get().getFormName());
        assertEquals("target-order-id", savedForm.get().getEntityId());
        assertEquals("订单列表", savedList.get().getListName());
        assertNotNull(savedList.get().getId());
        verify(entityFormService, times(1)).saveForm(any(EntityForm.class));
        verify(listConfigMapper, times(1)).insert(any(EntityListConfig.class));
        verifyNoInteractions(interfaceExtensionService);
    }

    private EntityDefinition entity(String id, String code) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(id); entity.setEntityCode(code);
        return entity;
    }

    private Map<String, Object> definition(String key, String scopeType, String scopeRef) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("extensionKey", key);
        value.put("displayName", key);
        value.put("implementationType", "STATIC_OPTIONS");
        value.put("scopeType", scopeType);
        if (scopeRef != null) value.put("scopeRef", scopeRef);
        return value;
    }
}
