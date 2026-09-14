package com.workflow.migration.application;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证新环境导入时，接口扩展的全部作用域都能解析为目标环境 ID。 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationInterfaceScopeImportTest {

    @Mock
    private EntityFormMapper formMapper;
    @Mock
    private EntityListConfigMapper listConfigMapper;
    @Mock
    private EntityFormService entityFormService;
    @Mock
    private UiExtensionDefinitionMapper extensionDefinitionMapper;
    @Mock
    private UiInterfaceExtensionService interfaceExtensionService;

    @InjectMocks
    private ConfigMigrationImportApplyService service;

    /**
     * FORM/LIST 宿主在接口扩展之前创建，GLOBAL/ENTITY 不创建宿主；
     * 四类扩展最终都应携带可用的目标 scopeId 保存成功。
     */
    @Test
    void importsGlobalEntityFormAndListScopedInterfacesIntoEmptyEnvironment() {
        EntityDefinition entity = new EntityDefinition();
        entity.setId("target-entity-id");
        entity.setEntityCode("order");
        AtomicReference<EntityForm> savedForm = new AtomicReference<>();
        AtomicReference<EntityListConfig> savedList = new AtomicReference<>();

        when(formMapper.selectByEntityIdAndFormKey(
                "target-entity-id", "edit"))
                .thenAnswer(invocation -> savedForm.get());
        when(listConfigMapper.findByEntityIdAndListKey(
                "target-entity-id", "default"))
                .thenAnswer(invocation -> savedList.get());
        when(entityFormService.saveForm(any(EntityForm.class)))
                .thenAnswer(invocation -> {
                    EntityForm form = invocation.getArgument(0);
                    form.setId("target-form-id");
                    savedForm.set(form);
                    return form;
                });
        doAnswer(invocation -> {
            savedList.set(invocation.getArgument(0));
            return 1;
        }).when(listConfigMapper).insert(any(EntityListConfig.class));
        when(extensionDefinitionMapper.selectOne(any())).thenReturn(null);
        when(interfaceExtensionService.save(
                any(UiExtensionDefinitionSaveRequest.class)))
                .thenAnswer(invocation -> {
                    UiExtensionDefinitionSaveRequest request =
                            invocation.getArgument(0);
                    UiExtensionDefinition definition =
                            new UiExtensionDefinition();
                    definition.setId("id-" + request.getExtensionKey());
                    return definition;
                });

        List<Map<String, Object>> interfaces = List.of(
                interfaceDefinition("global-api", "GLOBAL", null),
                interfaceDefinition("entity-api", "ENTITY", "order"),
                interfaceDefinition("form-api", "FORM", "order/edit"),
                interfaceDefinition("list-api", "LIST", "order/default"));
        service.ensureInterfaceScopeOwners(
                entity,
                interfaces,
                List.of(Map.of(
                        "formKey", "edit",
                        "formName", "编辑订单")),
                List.of(Map.of(
                        "listKey", "default",
                        "listName", "订单列表")));

        Map<String, String> imported =
                service.applyInterfaceExtensions(entity, interfaces);

        assertEquals("id-global-api", imported.get("global-api"));
        assertEquals("id-entity-api", imported.get("entity-api"));
        assertEquals("id-form-api", imported.get("form-api"));
        assertEquals("id-list-api", imported.get("list-api"));
        assertEquals("target-form-id", savedForm.get().getId());
        assertEquals("编辑订单", savedForm.get().getFormName());
        assertEquals("订单列表", savedList.get().getListName());

        ArgumentCaptor<UiExtensionDefinitionSaveRequest> requests =
                ArgumentCaptor.forClass(
                        UiExtensionDefinitionSaveRequest.class);
        verify(interfaceExtensionService,
                org.mockito.Mockito.times(4)).save(requests.capture());
        Map<String, UiExtensionDefinitionSaveRequest> byKey =
                requests.getAllValues().stream().collect(
                        java.util.stream.Collectors.toMap(
                                UiExtensionDefinitionSaveRequest::getExtensionKey,
                                value -> value));
        assertNull(byKey.get("global-api").getScopeId());
        assertEquals("target-entity-id",
                byKey.get("entity-api").getScopeId());
        assertEquals("target-form-id",
                byKey.get("form-api").getScopeId());
        assertEquals(savedList.get().getId(),
                byKey.get("list-api").getScopeId());
    }

    private Map<String, Object> interfaceDefinition(
            String extensionKey,
            String scopeType,
            String scopeRef) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("extensionKey", extensionKey);
        value.put("displayName", extensionKey);
        value.put("implementationType", "STATIC_OPTIONS");
        value.put("scopeType", scopeType);
        if (scopeRef != null) {
            value.put("scopeRef", scopeRef);
        }
        return value;
    }
}
