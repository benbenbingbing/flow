package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 实体范围保存、权限检查与历史数据边界回归。 */
class UiInterfaceEntityScopesTest {
    private final UiExtensionDefinitionMapper mapper = mock(UiExtensionDefinitionMapper.class);
    private final EntityDefinitionAccessPolicy access = mock(EntityDefinitionAccessPolicy.class);
    private final JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
    private final UiInterfaceExtensionService service = new UiInterfaceExtensionService(mapper,
            mock(EntityFormMapper.class), mock(EntityListConfigMapper.class), access,
            mock(EntityUiConfigurationPolicy.class), mock(SysDictItemService.class),
            mock(UiDataSourceExecutionAccessService.class), mock(UiInvocationContextFactory.class),
            new UiExtensionDefinitionValidator(codec), List.of(), codec, Runnable::run);

    @BeforeEach
    void returnInsertedDefinitionLikePersistenceLayer() {
        when(mapper.insert(any(UiExtensionDefinition.class))).thenAnswer(call -> {
            UiExtensionDefinition inserted = call.getArgument(0);
            inserted.setId("saved-extension");
            when(mapper.selectById("saved-extension")).thenReturn(inserted);
            return 1;
        });
    }

    @Test
    void savesNormalizedEntitySetAndChecksEveryEntity() {
        var request = request("ENTITY", "LIST");
        request.setScopeEntityIds(List.of(" entity-b ", "entity-a", "entity-b"));
        var result = service.save(request);
        assertEquals(List.of("entity-a", "entity-b"), result.getScopeEntityIds());
        assertNull(result.getScopeId());
        verify(access).requireDynamicById("entity-a");
        verify(access).requireDynamicById("entity-b");
        verify(mapper).insert(result);
    }

    @Test
    void refusesPartiallyAuthorizedEntitySetBeforeWriting() {
        var request = request("ENTITY", "FORM");
        request.setScopeEntityIds(List.of("entity-a", "entity-b"));
        doThrow(new SecurityException("无权限")).when(access).requireDynamicById("entity-b");
        assertThrows(SecurityException.class, () -> service.save(request));
        verify(mapper, never()).insert(any(UiExtensionDefinition.class));
    }

    @Test
    void supportsGlobalProviderForEveryContextAndClearsStaleSelection() {
        for (String context : List.of("FORM", "LIST", "ENTITY")) {
            var request = request("GLOBAL", context);
            request.setImplementationType("REGISTERED_PROVIDER");
            request.setProviderCode("project-provider");
            request.setScopeId("old-page");
            request.setScopeEntityIds(List.of("entity-a"));
            var result = service.save(request);
            assertNull(result.getScopeId());
            assertEquals(List.of(), result.getScopeEntityIds());
            assertEquals("[]", result.getScopeEntityIdsDocument());
        }
        verifyNoInteractions(access);
    }

    @Test
    void refusesEmptySelectionAndNewPageScopes() {
        assertThrows(IllegalArgumentException.class, () -> service.save(request("ENTITY", "FORM")));
        for (String type : List.of("FORM", "LIST")) {
            var request = request(type, type);
            request.setScopeId("page-a");
            assertThrows(IllegalArgumentException.class, () -> service.save(request));
        }
        verify(mapper, never()).insert(any(UiExtensionDefinition.class));
    }

    @Test
    void legacyEntityIdIsReadOnlyFallbackAndExplicitEmptyArrayCannotBroadenIt() {
        var definition = new UiExtensionDefinition();
        definition.setScopeType("ENTITY");
        definition.setScopeId("entity-a");
        assertTrue(definition.matchesScope("entity-a", "LIST", "list-a"));
        definition.setScopeEntityIds(List.of());
        assertFalse(definition.matchesScope("entity-a", "LIST", "list-a"));
        definition.setScopeEntityIdsDocument("[1]");
        assertThrows(IllegalArgumentException.class, definition::getScopeEntityIds);
    }

    @Test
    void listFilteringFindsAnyMemberOfEntitySet() {
        var selected = new UiExtensionDefinition();
        selected.setScopeType("ENTITY");
        selected.setScopeEntityIds(List.of("entity-a", "entity-b"));
        when(mapper.selectList(any())).thenReturn(List.of(selected));
        assertEquals(List.of(selected), service.list("ENTITY", "entity-b", null));
        assertEquals(List.of(), service.list("ENTITY", "entity-c", null));
    }

    private UiExtensionDefinitionSaveRequest request(String scope, String context) {
        var request = new UiExtensionDefinitionSaveRequest();
        request.setExtensionType("INTERFACE");
        request.setExtensionKey("scope_test");
        request.setDisplayName("范围测试");
        request.setImplementationType("STATIC_OPTIONS");
        request.setScopeType(scope);
        request.setInterfaceContextType(context);
        request.setInterfaceKind("READ");
        request.setImplementationConfig(Map.of("options", List.of("one")));
        return request;
    }
}
