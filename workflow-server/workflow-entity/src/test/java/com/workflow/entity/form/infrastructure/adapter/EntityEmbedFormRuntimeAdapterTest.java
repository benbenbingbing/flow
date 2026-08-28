package com.workflow.entity.form.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRuntimeFormPort;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.list.application.EntityListRuntimeService;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityEmbedFormRuntimeAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UiConfigReleaseService releaseService;
    private EntityDefinitionMapper definitionMapper;
    private EntityDataActionService dataActionService;
    private EntityListRuntimeService listRuntimeService;
    private EntityActionCapabilityService capabilityService;
    private EntityEmbedFormRuntimeAdapter adapter;
    private EntityForm form;

    @BeforeEach
    void setUp() {
        releaseService = mock(UiConfigReleaseService.class);
        definitionMapper = mock(EntityDefinitionMapper.class);
        dataActionService = mock(EntityDataActionService.class);
        listRuntimeService = mock(EntityListRuntimeService.class);
        capabilityService = mock(EntityActionCapabilityService.class);
        adapter = new EntityEmbedFormRuntimeAdapter(
                releaseService, definitionMapper, dataActionService,
                listRuntimeService, capabilityService,
                new PublishedFormConditionEvaluator(objectMapper), objectMapper);

        form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setFormName("工单详情");
        form.setLayoutType("grid");
        form.setFields(List.of(titleField(), conditionalField()));
        when(releaseService.resolveRuntimeFormRelease(
                "form-1", "form-release-4", 4,
                UiRuntimeResolutionContext.historical(null, null)))
                .thenReturn(new ResolvedEntityFormRelease(
                        form, "form-release-4", 4, true));
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("work_order");
        when(definitionMapper.findByEntityCode("work_order"))
                .thenReturn(Optional.of(definition));
    }

    @Test
    void resolvesExactPublishedFormAndEvaluatesModeAndLinkageState() throws Exception {
        var snapshot = adapter.resolveForm(new EmbedRuntimeFormPort.ResolveQuery(
                target(false), EmbedRuntimeFormPort.RuntimeMode.VIEW, "record-1",
                Map.of("status", "CLOSED")));

        assertEquals("工单详情", snapshot.title());
        assertEquals("grid", snapshot.layoutType());
        assertEquals(List.of("title", "conditional"), snapshot.fields().stream()
                .map(EmbedRuntimeFormPort.Field::code).toList());
        assertEquals(1, snapshot.fields().get(0).validation().get("minimum"));
        assertEquals(20, snapshot.fields().get(0).validation().get("maxLength"));
        assertFalse(snapshot.fields().get(0).validation().containsKey("message"));
        assertFalse(snapshot.fields().get(1).modeVisible());
        assertFalse(snapshot.fields().get(1).modeEditable());
        assertTrue(snapshot.actions().isEmpty());

        String json = objectMapper.writeValueAsString(snapshot);
        assertFalse(json.contains("componentProps"));
        assertFalse(json.contains("visibilityConditionConfig"));
        assertFalse(json.contains("provider"));
        assertFalse(json.contains("serviceId"));
        assertFalse(json.contains("operationCode"));
        assertFalse(json.contains("script"));
        assertFalse(json.contains("event"));
    }

    @Test
    void recordIntersectsPinnedListAndCurrentDetailAuthorization() {
        EntityDataDTO candidate = row("record-1");
        candidate.setActionCapabilities(Map.of(
                "view", EntityActionCapabilityDTO.allowed()));
        when(listRuntimeService.queryPinned(
                "work_order", "supplier_open", "list-release-7", 7,
                1, 2, Map.of(),
                Map.of("supplier_id", "S-10086", "supplier_id_op", "EQ",
                        "id", "record-1", "id_op", "EQ")))
                .thenReturn(new PageResult<>(List.of(candidate), 1, 1, 2));
        EntityDataDTO detail = row("record-1");
        detail.setData(Map.of(
                "title", "设备维修", "conditional", "可见值",
                "secret", "must-not-leak"));
        detail.setCreatedAt(LocalDateTime.of(2026, 8, 26, 8, 0));
        detail.setUpdatedAt(LocalDateTime.of(2026, 8, 27, 8, 0));
        when(dataActionService.getDetailReadOnly(
                "work_order", "record-1", "supplier_open"))
                .thenReturn(detail);

        Optional<EmbedRuntimeFormPort.RecordSnapshot> result =
                adapter.findRecord(new EmbedRuntimeFormPort.RecordQuery(
                        target(true), "record-1",
                        Map.of("supplier_id", "S-10086", "supplier_id_op", "EQ")));

        assertTrue(result.isPresent());
        assertNull(result.orElseThrow().recordVersion());
        assertEquals(Map.of("title", "设备维修", "conditional", "可见值"),
                result.orElseThrow().values());
        assertEquals("2026-08-26T08:00:00Z",
                result.orElseThrow().createdAt().toString());
        verify(dataActionService).getDetailReadOnly(
                "work_order", "record-1", "supplier_open");
    }

    @Test
    void hiddenPinnedRowAndCurrentPermissionDenialBothBecomeEmpty() {
        when(listRuntimeService.queryPinned(
                "work_order", "supplier_open", "list-release-7", 7,
                1, 2, Map.of(), Map.of("id", "record-1", "id_op", "EQ")))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 2));

        Optional<EmbedRuntimeFormPort.RecordSnapshot> hidden =
                adapter.findRecord(new EmbedRuntimeFormPort.RecordQuery(
                        target(true), "record-1", Map.of()));

        assertTrue(hidden.isEmpty());
        verifyNoInteractions(dataActionService);

        EntityDataDTO candidate = row("record-1");
        candidate.setActionCapabilities(Map.of(
                "view", EntityActionCapabilityDTO.allowed()));
        when(listRuntimeService.queryPinned(
                "work_order", "supplier_open", "list-release-7", 7,
                1, 2, Map.of(), Map.of("id", "record-2", "id_op", "EQ")))
                .thenReturn(new PageResult<>(List.of(rowWithAllowedView("record-2")), 1, 1, 2));
        when(dataActionService.getDetailReadOnly(
                "work_order", "record-2", "supplier_open"))
                .thenThrow(new ForbiddenException("数据不存在或无权访问"));

        Optional<EmbedRuntimeFormPort.RecordSnapshot> denied =
                adapter.findRecord(new EmbedRuntimeFormPort.RecordQuery(
                        target(true), "record-2", Map.of()));

        assertTrue(denied.isEmpty());
    }

    @Test
    void infrastructureFailureIsNotMisreportedAsMissingRecord() {
        when(listRuntimeService.queryPinned(
                "work_order", "supplier_open", "list-release-7", 7,
                1, 2, Map.of(), Map.of("id", "record-1", "id_op", "EQ")))
                .thenThrow(new IllegalStateException("snapshot hash mismatch"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> adapter.findRecord(new EmbedRuntimeFormPort.RecordQuery(
                        target(true), "record-1", Map.of())));

        assertEquals("snapshot hash mismatch", error.getMessage());
    }

    @Test
    void largeImmutableOptionSetUsesBoundedQueryButDynamicBindingIsDenied() {
        EntityFormField category = selectField(101);
        form.setFields(List.of(category));

        var snapshot = adapter.resolveForm(new EmbedRuntimeFormPort.ResolveQuery(
                target(false), EmbedRuntimeFormPort.RuntimeMode.CREATE,
                null, Map.of()));
        assertEquals("save", snapshot.actions().get(0).key());
        assertTrue(snapshot.actions().get(0).enabled());
        assertTrue(snapshot.fields().get(0).runtimeOptions());
        assertTrue(snapshot.fields().get(0).options().isEmpty());

        var page = adapter.queryOptions(new EmbedRuntimeFormPort.OptionQuery(
                target(false), EmbedRuntimeFormPort.RuntimeMode.CREATE,
                null, "category", "选项", Map.of(), Map.of(), 2, 50));
        assertEquals(50, page.items().size());
        assertTrue(page.hasMore());
        assertEquals("选项-51", page.items().get(0).label());

        category.setDataSourceBindings(Map.of(
                "FIELD_OPTIONS", Map.of("serviceId", "mutable-service")));
        assertThrows(EmbedRuntimeFormPort.OperationNotAllowedException.class,
                () -> adapter.queryOptions(new EmbedRuntimeFormPort.OptionQuery(
                        target(false), EmbedRuntimeFormPort.RuntimeMode.CREATE,
                        null, "category", null, Map.of(), Map.of(), 1, 50)));
    }

    @Test
    void lookupWithoutPinnedCandidateReleaseFailsClosed() {
        assertThrows(EmbedRuntimeFormPort.OperationNotAllowedException.class,
                () -> adapter.queryLookups(new EmbedRuntimeFormPort.LookupQuery(
                        target(false), EmbedRuntimeFormPort.RuntimeMode.VIEW,
                        "record-1", "lineId", null,
                        Map.of(), Map.of(), 1, 20)));
    }

    private static EntityDataDTO row(String id) {
        EntityDataDTO row = new EntityDataDTO();
        row.setId(id);
        return row;
    }

    private static EntityDataDTO rowWithAllowedView(String id) {
        EntityDataDTO row = row(id);
        row.setActionCapabilities(Map.of(
                "view", EntityActionCapabilityDTO.allowed()));
        return row;
    }

    private static EntityFormField titleField() {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("title");
        field.setFieldLabel("标题");
        field.setFieldType("STRING");
        field.setIsRequired(1);
        field.setIsReadonly(0);
        field.setIsHidden(0);
        field.setGridSpan(24);
        field.setValidationRules(
                "{\"min\":1,\"maxLength\":20,\"message\":\"内部提示\"}");
        field.setOptionsJson("[]");
        return field;
    }

    private static EntityFormField conditionalField() {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("conditional");
        field.setFieldLabel("条件字段");
        field.setFieldType("TEXT");
        field.setIsRequired(0);
        field.setIsReadonly(0);
        field.setIsHidden(0);
        field.setGridSpan(12);
        field.setOptionsJson("[]");
        field.setExtensionConfig(
                "{\"modes\":{\"view\":{\"visible\":true,\"editable\":false}}}");
        field.setComponentProps("""
                {"linkageRules":{"visibilityConditionConfig":{"version":1,
                "root":{"type":"CONDITION","property":"status",
                "operator":"==","value":"OPEN"}}}}
                """);
        return field;
    }

    private static EntityFormField selectField(int count) {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("category");
        field.setFieldLabel("类型");
        field.setFieldType("SELECT");
        field.setIsRequired(0);
        field.setIsReadonly(0);
        field.setIsHidden(0);
        field.setGridSpan(24);
        List<String> values = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            values.add("{\"label\":\"选项-" + index
                    + "\",\"value\":\"VALUE-" + index + "\"}");
        }
        field.setOptionsJson("[" + String.join(",", values) + "]");
        return field;
    }

    private static EmbedRuntimeFormPort.Target target(boolean listPinned) {
        return new EmbedRuntimeFormPort.Target(
                "work_order", "form-1", "form-release-4", 4,
                listPinned ? "supplier_open" : null,
                listPinned ? "list-release-7" : null,
                listPinned ? 7 : null);
    }
}
