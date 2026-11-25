package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiEventBinding;
import com.workflow.port.platform.SystemOperationLogPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 通过事件实际执行入口验证参数、授权读取、投影及无结果回填行为。 */
class EntityLookupEventTest {
    private final ObjectMapper json = new ObjectMapper();
    private final UiEventBindingService bindings = mock(UiEventBindingService.class);
    private final UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
    private final EntityActionCapabilityService permissions = mock(EntityActionCapabilityService.class);
    private final EntityDataDynamicService rows = mock(EntityDataDynamicService.class);
    private final SystemEntityReadService system = mock(SystemEntityReadService.class);
    private final EntityPublishedSnapshotService published = mock(EntityPublishedSnapshotService.class);
    private final EntityFormActionService forms = mock(EntityFormActionService.class);
    private final UiEventExecutionReceiptService receipts = mock(UiEventExecutionReceiptService.class);
    private final EntityLookupEventReader reader = new EntityLookupEventReader(permissions, rows, system, published, json);
    private final UiEventRuntimeService runtime = new UiEventRuntimeService(
            bindings, interfaces, new UiEventValueMapper(), mock(EntitySelectionRuntimeService.class),
            mock(SystemOperationLogPort.class), permissions, forms, receipts, rows, json,
            mock(ObjectProvider.class), reader);

    @Test
    void newFormUsesListLaunchParameterAndFillsOnlyPublishedFields() {
        UiEventExecuteRequest request = request("FORM", "FORM_OPEN", "OWNER");
        request.setInput(Map.of("form", Map.of("phone", "已有值"), "params", Map.of("customerId", "0007")));
        bind(request, formStep("BEFORE", "input.params.customerId"), parameterView("form", null));
        customer("0007");

        var result = runtime.execute(request);
        assertEquals(1, result.getEffects().size());
        var effect = result.getEffects().get(0);
        assertEquals("FIELD_MAPPING", effect.get("type"));
        assertEquals(Map.of("form", Map.of("phone", "010-1234")), effect.get("data"));
        assertEquals(List.of(mapping("phone", "phone")), effect.get("mappings"));
        verify(permissions).requireStandardPermission("customer", EntityPermissionAction.VIEW);
        verify(rows).findVisibleMainById("customer", "0007");
        verifyNoInteractions(interfaces);
    }

    @Test
    void formButtonResolvesPublishedDefaultsAndDiscardsUndeclaredParameters() {
        UiEventExecuteRequest request = request("FORM", "FORM_BUTTON_CLICK", "BUTTON");
        request.setInput(Map.of("form", Map.of(), "params", Map.of("unknown", "discarded")));
        request.setContext(Map.of("params", Map.of("customerId", "client-context-id")));
        var snapshot = parameterView("form", "default-id");
        bind(request, formStep("REPLACE", "input.params.customerId"), snapshot);
        when(forms.requireCustomButton(any(), any(), anyString(), anyInt())).thenAnswer(invocation -> {
            request.setServerPublishedButton(Map.of("key", "lookup"));
            return "create";
        });
        when(receipts.execute(eq(request), any(), any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(2).get());
        customer("default-id");

        assertEquals(1, runtime.execute(request).getEffects().size());
        assertEquals(Map.of("customerId", "default-id"), request.getInput().get("params"));
        verify(rows).findVisibleMainById("customer", "default-id");
    }

    @Test
    void rowButtonUsesServerRowAndProducesAProjectedResult() {
        UiEventExecuteRequest request = request("LIST", "ROW_BUTTON_CLICK", "BUTTON");
        request.setRecordId("order-1");
        request.setInput(Map.of("row", Map.of("data", Map.of("customer_id", "spoofed"))));
        var order = new EntityDataDTO();
        order.setId("order-1");
        order.setData(Map.of("customer_id", "0007"));
        when(rows.findAccessibleById("orders", "order-1", "main")).thenReturn(order);
        bind(request, listStep("input.row.data.customer_id"), listSnapshot("rowActionConfig", Map.of()));
        customer("0007");

        var result = runtime.execute(request);
        assertEquals(Map.of("id", "0007", "data", Map.of("phone", "010-1234")), result.getData());
        assertTrue(result.getEffects().isEmpty());
        verify(rows, never()).findVisibleMainById("customer", "spoofed");
    }

    @Test
    void toolbarCanUsePageParameterAndSingleSelectionIsEnforced() {
        UiEventExecuteRequest request = request("LIST", "TOOLBAR_BUTTON_CLICK", "BUTTON");
        request.setInput(Map.of("params", Map.of("customerId", "0007")));
        var listConfig = parameterView("list", null).get("list");
        var snapshot = listSnapshot("toolbarConfig", (Map<?, ?>) listConfig);
        bind(request, listStep("input.params.customerId"), snapshot);
        customer("0007");
        assertNotNull(runtime.execute(request).getData());

        bind(request, listStep("input.selectedRows.0.id"), snapshot);
        assertThrows(IllegalArgumentException.class, () -> runtime.execute(request));
        verify(rows, times(1)).findVisibleMainById(anyString(), anyString());
    }

    @Test
    void blankMissingAndDeniedIdsProduceNoMapping() {
        UiEventExecuteRequest request = request("FORM", "FORM_OPEN", "OWNER");
        bind(request, formStep("BEFORE", "input.params.customerId"), parameterView("form", null));
        customer("unused");
        for (String id : List.of("", "missing", "out-of-scope", "denied")) {
            request.setInput(Map.of("params", Map.of("customerId", id), "form", Map.of("phone", "既有数据")));
            if ("denied".equals(id)) {
                doThrow(new ForbiddenException("不可读车辆信息")).when(permissions)
                        .requireStandardPermission("customer", EntityPermissionAction.VIEW);
            }
            var result = runtime.execute(request);
            assertTrue(result.getEffects().isEmpty());
            assertNull(result.getData());
        }
        verify(rows).findVisibleMainById("customer", "missing");
        verify(rows).findVisibleMainById("customer", "out-of-scope");
        verify(rows, never()).findVisibleMainById("customer", "denied");
        verify(rows, never()).findVisibleMainById("customer", "");
    }

    @Test
    void skipsMissingFieldsAndKeepsTheSourceIdForStaleResultDetection() {
        UiEventExecuteRequest request = request("FORM", "FIELD_CHANGE", "FIELD");
        request.setInput(Map.of("form", Map.of("customer_id", "0007")));
        bind(request, formStep("AFTER", "input.form.customer_id"), Map.of());
        customer("0007");
        var effect = runtime.execute(request).getEffects().get(0);
        assertEquals("customer_id", effect.get("sourceField"));
        assertEquals("0007", effect.get("sourceValue"));

        var unpublished = new LinkedHashMap<>(formStep("AFTER", "input.form.customer_id"));
        unpublished.put("outputMapping", List.of(mapping("retired", "phone")));
        bind(request, unpublished, Map.of());
        assertTrue(runtime.execute(request).getEffects().isEmpty());
    }

    @Test
    void readerHonorsSystemWhitelistAndSerializesPreciseNumbersLikeEntityDetails() {
        var row = customer("0007");
        assertEquals(Map.of("id", "0007", "data", Map.of("large", "9007199254740993")),
                reader.read("customer", "0007", Set.of("large", "retired", "children")));
        when(system.isSystemEntity("sys_user")).thenReturn(true);
        when(system.findVisibleById("sys_user", "0007")).thenReturn(row);
        var projected = reader.read("sys_user", "0007", Set.of("phone", "password"));
        assertEquals(Map.of("phone", "010-1234"), projected.get("data"));
        assertNull(reader.read("sys_user", "out-of-scope", Set.of("phone")));
        verify(system).findVisibleById("sys_user", "0007");
    }

    @Test
    void snapshotPreservesLookupConfigAndRejectsExternalImplementationCoordinates() {
        UiEventBindingMapper mapper = mock(UiEventBindingMapper.class);
        var codec = new JsonDocumentCodec(json);
        var snapshots = new UiEventBindingSnapshotService(mapper, mock(UiExtensionDefinitionMapper.class), interfaces, codec);
        var binding = new UiEventBinding();
        binding.setOwnerType("FORM");
        binding.setOwnerId("form-1");
        binding.setTargetType("BUTTON");
        binding.setTargetKey("lookup");
        binding.setEventCode("FORM_BUTTON_CLICK");
        binding.setInheritanceMode("REPLACE");
        var step = formStep("REPLACE", "input.params.customerId");
        binding.setStepsDocument(codec.write(List.of(step), "实体查询绑定"));
        when(mapper.findForSnapshot("FORM", "form-1", "entity-1")).thenReturn(List.of(binding));
        var frozen = snapshots.snapshot("FORM", "form-1", "entity-1", true);
        assertEquals(List.of(step), frozen.get(0).get("steps"));
        assertEquals(frozen, snapshots.activationReferenceSnapshot(Map.of("eventBindings", frozen)).get("eventBindings"));
        var invalid = new LinkedHashMap<>(step);
        invalid.put("extensionId", "unsafe-extension");
        binding.setStepsDocument(codec.write(List.of(invalid), "错误查询绑定"));
        assertThrows(IllegalArgumentException.class, () -> snapshots.snapshot("FORM", "form-1", "entity-1", true));
        verifyNoInteractions(interfaces);
    }

    private UiEventExecuteRequest request(String owner, String event, String target) {
        var request = new UiEventExecuteRequest();
        request.setConfigType(owner);
        request.setConfigId(owner.toLowerCase() + "-1");
        request.setEventCode(event);
        request.setTargetType(target);
        request.setTargetKey("lookup");
        return request;
    }

    private void bind(UiEventExecuteRequest request, Map<String, Object> step, Map<String, Object> snapshot) {
        when(bindings.resolvePublished(request)).thenReturn(new UiEventBindingService.ResolvedEventChain(
                List.of(step), "release-1", 1, "entity-1", "orders", "main", snapshot));
    }

    private Map<String, Object> parameterView(String owner, String defaultValue) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        if (defaultValue != null) property.put("default", defaultValue);
        return Map.of(owner, Map.of("viewConfig", Map.of("inputParameterSchema",
                Map.of("properties", Map.of("customerId", property)))));
    }

    private Map<String, Object> listSnapshot(String buttonConfig, Map<?, ?> config) {
        Map<Object, Object> list = new LinkedHashMap<>(config);
        list.put(buttonConfig, List.of(Map.of("key", "lookup", "type", "CUSTOM", "customMode", "EVENT")));
        return Map.of("list", list);
    }

    private EntityDataDTO customer(String id) {
        var snapshot = new EntityPublishedSnapshot();
        snapshot.setFields(List.of(field("phone", EntityField.FieldType.STRING),
                field("large", EntityField.FieldType.LONG), field("children", EntityField.FieldType.SUB_FORM)));
        when(published.findLatestByEntityCode("customer")).thenReturn(snapshot);
        var row = new EntityDataDTO();
        row.setId(id);
        row.setData(Map.of("phone", "010-1234", "large", 9_007_199_254_740_993L,
                "retired", "旧字段", "children", List.of(Map.of("private", true))));
        when(rows.findVisibleMainById("customer", id)).thenReturn(row);
        return row;
    }

    private EntityField field(String code, EntityField.FieldType type) {
        var field = new EntityField();
        field.setFieldCode(code);
        field.setFieldType(type);
        return field;
    }

    private Map<String, Object> formStep(String strategy, String idPath) {
        return Map.of("actionType", "ENTITY_LOOKUP", "strategy", strategy, "order", 10,
                "actionConfig", Map.of("entityCode", "customer", "idPath", idPath),
                "outputMapping", List.of(mapping("phone", "phone")));
    }

    private Map<String, Object> listStep(String idPath) {
        return Map.of("actionType", "ENTITY_LOOKUP", "strategy", "REPLACE", "order", 10,
                "actionConfig", Map.of("entityCode", "customer", "idPath", idPath, "fields", List.of("phone")));
    }

    private Map<String, Object> mapping(String source, String target) {
        return Map.of("sourcePath", "data.data." + source, "targetPath", "form." + target,
                "overwrite", "IF_EMPTY", "clearOnEmpty", false);
    }
}
