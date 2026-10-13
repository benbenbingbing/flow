package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.excel.runtime.EntityExcelRuntimeResolver;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.api.request.UiEventBindingSaveRequest;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 内置 Excel 按钮动作的持久化、发布边界与真实事件授权回归。 */
class UiExcelBuiltinEventTest {
    private final ObjectMapper json = new ObjectMapper();
    private final JsonDocumentCodec codec = new JsonDocumentCodec(json);
    private final UiEventBindingMapper bindings = mock(UiEventBindingMapper.class);
    private final UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
    private final UiEventBindingSnapshotService snapshots = new UiEventBindingSnapshotService(
            bindings, mock(UiExtensionDefinitionMapper.class), interfaces, codec);

    @Test
    void savesBuiltinParametersForFormAndListWithoutResolvingAnInterface() {
        for (String ownerType : List.of("FORM", "LIST")) {
            UiEventBindingSaveRequest request = bindingRequest(ownerType);
            service().save(request);
        }
        ArgumentCaptor<UiEventBinding> saved = ArgumentCaptor.forClass(UiEventBinding.class);
        verify(bindings, times(2)).insert(saved.capture());
        for (UiEventBinding value : saved.getAllValues()) {
            assertEquals(List.of(step()), codec.readArray(value.getStepsDocument(), "测试绑定"));
        }
        verifyNoInteractions(interfaces);
    }

    @Test
    void rejectsMixedInterfaceReferenceAndUnsupportedTargetsBeforeWriting() {
        var mixed = new LinkedHashMap<>(step());
        mixed.put("extensionId", "old-extension");
        UiEventBindingSaveRequest request = bindingRequest("LIST");
        request.setSteps(List.of(mixed));
        assertThrows(IllegalArgumentException.class, () -> service().save(request));
        request.setSteps(List.of(step()));
        request.setTargetType("OWNER");
        assertThrows(IllegalArgumentException.class, () -> service().save(request));
        verify(bindings, never()).insert(any(UiEventBinding.class));
        verifyNoInteractions(interfaces);
    }

    @Test
    void preservesBuiltinConfigurationAcrossSnapshotAndRestoreForBothOwners() {
        for (String ownerType : List.of("FORM", "LIST")) {
            String ownerId = ownerType.toLowerCase() + "-1";
            UiEventBinding draft = draft(ownerType);
            when(bindings.findForSnapshot(ownerType, ownerId, "entity-1")).thenReturn(List.of(draft));
            var published = snapshots.snapshot(ownerType, ownerId, "entity-1", true);
            assertEquals(List.of(step()), published.get(0).get("steps"));
            // 草稿随后改为另一方案，发布参数仍是原先值，运行时和恢复都只接收该快照。
            draft.setStepsDocument("[]");
            var resolved = service().resolveSnapshotButtonSteps(ownerType, ownerId, "entity-1",
                    eventCode(ownerType), "excel", Map.of("eventBindings", published));
            assertEquals(step().get("actionConfig"), resolved.get(0).get("actionConfig"));
            snapshots.restoreLocalBindingsForRelease(ownerType, ownerId, published);
            if ("FORM".equals(ownerType)) {
                assertEquals(published, snapshots.activationReferenceSnapshot(Map.of("eventBindings", published))
                        .get("eventBindings"));
            }
        }
        ArgumentCaptor<UiEventBinding> restored = ArgumentCaptor.forClass(UiEventBinding.class);
        verify(bindings, times(2)).insert(restored.capture());
        for (UiEventBinding binding : restored.getAllValues()) {
            assertEquals(List.of(step()), codec.readArray(binding.getStepsDocument(), "恢复绑定"));
        }
        verifyNoInteractions(interfaces);
    }

    @Test
    void snapshotRejectsBuiltinActionOnInheritedOwnerLevel() {
        UiEventBinding draft = draft("LIST");
        draft.setTargetType("OWNER");
        when(bindings.findForSnapshot("LIST", "list-1", "entity-1")).thenReturn(List.of(draft));
        assertThrows(IllegalArgumentException.class,
                () -> snapshots.snapshot("LIST", "list-1", "entity-1", true));
    }

    @Test
    void executesOnlyPublishedParametersInsideAuthorizedListOrigin() {
        RuntimeFixture fixture = new RuntimeFixture();
        UiEventExecuteRequest request = runtimeRequest("LIST");
        // 客户端同名 actionConfig 只是普通输入，不能覆盖主动作参数。
        request.setInput(Map.of("actionConfig", Map.of("operation", "IMPORT")));
        var chain = fixture.chain(request, step());
        var effect = Map.<String, Object>of("type", "EXCEL_ACTION", "operation", "EXPORT");
        when(fixture.excel.prepareBoundAction(anyMap())).thenAnswer(call -> {
            assertSame(chain, UiEventOriginContext.current().chain());
            assertSame(request, UiEventOriginContext.current().request());
            assertEquals(step(), call.getArgument(0));
            assertNotNull(request.getServerIdempotencyKey());
            return effect;
        });
        var result = fixture.runtime.execute(request);
        assertEquals(List.of(effect), result.getEffects());
        assertTrue(result.isReplaced());
        assertEquals("ENTITY_EXCEL", result.getTrace().get(0).get("step"));
        assertFalse(result.isDefaultExecuted());
        assertNull(UiEventOriginContext.current());
        verify(fixture.capabilities).requirePublishedListButton(eq("demo"), eq("excel"), anyMap(),
                nullable(EntityDataDTO.class));
        verifyNoInteractions(interfaces);
    }

    @Test
    void formBuiltinActionUsesAuthorizedModeAndReceiptExecution() {
        RuntimeFixture fixture = new RuntimeFixture();
        UiEventExecuteRequest request = runtimeRequest("FORM");
        fixture.chain(request, step());
        when(fixture.forms.requireCustomButton(eq(request), anyMap(), eq("release-1"), eq(2))).thenAnswer(call -> {
            // 正式权限入口会将已发布按钮附到可信请求；测试保留此必要副作用。
            request.setServerPublishedButton(Map.of("key", "excel", "type", "custom"));
            return "view";
        });
        when(fixture.receipts.execute(eq(request), any(), any())).thenAnswer(call ->
                ((Supplier<UiEventExecutionResult>) call.getArgument(2)).get());
        when(fixture.excel.prepareBoundAction(anyMap())).thenAnswer(call -> {
            assertEquals("view", UiEventOriginContext.current().request().getServerAuthorizedMode());
            assertEquals("demo", UiEventOriginContext.current().request().getEntityCode());
            return Map.of("type", "EXCEL_ACTION");
        });
        assertNull(request.getEntityCode());
        assertEquals(1, fixture.runtime.execute(request).getEffects().size());
        request.setEntityCode("DEMO");
        assertEquals(1, fixture.runtime.execute(request).getEffects().size());
        assertEquals("demo", request.getEntityCode());
        verify(fixture.receipts, times(2)).execute(eq(request), any(), any());
        assertNull(UiEventOriginContext.current());
        verifyNoInteractions(interfaces);
    }

    @Test
    void deniedListButtonCannotPrepareExcelAndDoesNotLeakOrigin() {
        RuntimeFixture fixture = new RuntimeFixture();
        UiEventExecuteRequest request = runtimeRequest("LIST");
        fixture.chain(request, step());
        doThrow(new ForbiddenException("禁止执行")).when(fixture.capabilities)
                .requirePublishedListButton(eq("demo"), eq("excel"), anyMap(), nullable(EntityDataDTO.class));
        assertThrows(ForbiddenException.class, () -> fixture.runtime.execute(request));
        verifyNoInteractions(fixture.excel);
        assertNull(UiEventOriginContext.current());
    }

    @Test
    void conditionCannotSilentlySkipBuiltinActionInPublishedChain() {
        RuntimeFixture fixture = new RuntimeFixture();
        UiEventExecuteRequest request = runtimeRequest("LIST");
        var invalid = new LinkedHashMap<>(step());
        invalid.put("condition", Map.of("path", "input.enabled", "value", true));
        fixture.chain(request, invalid);
        assertThrows(IllegalArgumentException.class, () -> fixture.runtime.execute(request));
        verifyNoInteractions(fixture.excel);
        assertNull(UiEventOriginContext.current());
    }

    private UiEventBindingService service() {
        EntityFormMapper forms = mock(EntityFormMapper.class);
        EntityForm form = new EntityForm();
        form.setEntityId("entity-1");
        when(forms.selectById("form-1")).thenReturn(form);
        EntityListConfigMapper lists = mock(EntityListConfigMapper.class);
        EntityListConfig list = new EntityListConfig();
        list.setEntityId("entity-1");
        when(lists.selectById("list-1")).thenReturn(list);
        EntityDefinitionMapper definitions = mock(EntityDefinitionMapper.class);
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-1"); entity.setEntityCode("demo");
        when(definitions.selectById("entity-1")).thenReturn(entity);
        return new UiEventBindingService(bindings, mock(UiConfigReleaseMapper.class), definitions,
                forms, lists, mock(EntityDefinitionAccessPolicy.class), mock(UiConfigurationAccessService.class),
                interfaces, snapshots, mock(UiConfigReleaseService.class), codec, json);
    }

    private Map<String, Object> step() {
        return Map.of("stepCode", "excel_action", "strategy", "REPLACE", "actionType", "ENTITY_EXCEL",
                "actionConfig", Map.of("operation", "EXPORT", "schemeRef",
                        Map.of("mode", "EXPLICIT", "schemeCode", "report"), "dataScope", "CURRENT_RECORD"),
                "order", 10, "failurePolicy", "STOP");
    }

    private String eventCode(String ownerType) {
        return "FORM".equals(ownerType) ? "FORM_BUTTON_CLICK" : "TOOLBAR_BUTTON_CLICK";
    }

    private UiEventBindingSaveRequest bindingRequest(String ownerType) {
        UiEventBindingSaveRequest request = new UiEventBindingSaveRequest();
        request.setOwnerType(ownerType); request.setOwnerId(ownerType.toLowerCase() + "-1");
        request.setTargetType("BUTTON"); request.setTargetKey("excel");
        request.setEventCode(eventCode(ownerType)); request.setSteps(List.of(step()));
        request.setInheritanceMode("REPLACE"); request.setEnabled(true);
        return request;
    }

    private UiEventBinding draft(String ownerType) {
        UiEventBinding draft = new UiEventBinding();
        draft.setId(ownerType + "-binding"); draft.setOwnerType(ownerType);
        draft.setOwnerId(ownerType.toLowerCase() + "-1");
        draft.setTargetType("BUTTON"); draft.setTargetKey("excel");
        draft.setEventCode(eventCode(ownerType)); draft.setInheritanceMode("REPLACE");
        draft.setStepsDocument(codec.write(List.of(step()), "测试绑定")); draft.setRevision(1);
        return draft;
    }

    private UiEventExecuteRequest runtimeRequest(String ownerType) {
        var request = new UiEventExecuteRequest();
        request.setConfigType(ownerType); request.setConfigId(ownerType.toLowerCase() + "-1");
        request.setTargetType("BUTTON"); request.setTargetKey("excel");
        request.setEventCode(eventCode(ownerType)); request.setInput(Map.of());
        return request;
    }

    private class RuntimeFixture {
        final UiEventBindingService resolvedBindings = mock(UiEventBindingService.class);
        final EntityExcelRuntimeResolver excel = mock(EntityExcelRuntimeResolver.class);
        final EntityActionCapabilityService capabilities = mock(EntityActionCapabilityService.class);
        final EntityFormActionService forms = mock(EntityFormActionService.class);
        final UiEventExecutionReceiptService receipts = mock(UiEventExecutionReceiptService.class);
        final UiEventRuntimeService runtime;

        @SuppressWarnings("unchecked")
        RuntimeFixture() {
            ObjectProvider<EntityExcelRuntimeResolver> provider = mock(ObjectProvider.class);
            when(provider.getObject()).thenReturn(excel);
            runtime = new UiEventRuntimeService(resolvedBindings, interfaces, new UiEventValueMapper(),
                    mock(EntitySelectionRuntimeService.class), mock(SystemAuditPort.class), capabilities, forms,
                    receipts, mock(EntityDataDynamicService.class), json, provider);
        }

        UiEventBindingService.ResolvedEventChain chain(UiEventExecuteRequest request, Map<String, Object> step) {
            var button = Map.of("key", "excel", "type", "custom", "customMode", "EVENT", "selectionRequirement", "NONE");
            var snapshot = Map.<String, Object>of("list", Map.of("toolbarConfig", List.of(button)));
            var chain = new UiEventBindingService.ResolvedEventChain(List.of(step), "release-1", 2,
                    "entity-1", "demo", "default", snapshot, "release-1", "published-hash");
            when(resolvedBindings.resolvePublished(request)).thenReturn(chain);
            return chain;
        }
    }
}
