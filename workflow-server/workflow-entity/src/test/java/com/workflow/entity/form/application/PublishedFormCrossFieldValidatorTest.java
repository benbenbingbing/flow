package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.FormCrossFieldValidationException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PublishedFormCrossFieldValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PublishedFormCrossFieldValidator validator = new PublishedFormCrossFieldValidator(
            mock(EntityDataDynamicService.class), mapper, new JsonDocumentCodec(mapper), new PublishedFormConditionEvaluator(mapper));

    static EntityForm form() {
        EntityFormField start = new EntityFormField();
        start.setId("s"); start.setFieldCode("start"); start.setFieldType("INTEGER"); start.setFieldLabel("开始");
        start.setIsHidden(1);
        EntityFormField end = new EntityFormField();
        end.setId("e"); end.setFieldCode("end"); end.setFieldType("INTEGER"); end.setFieldLabel("结束");
        end.setValidationRules("{\"crossField\":{\"version\":1,\"rules\":[{\"id\":\"range\",\"operator\":\"GE\",\"targetFieldCode\":\"start\",\"message\":\"结束不得早于开始\"}]}}");
        EntityForm form = new EntityForm(); form.setId("form-1"); form.setEntityId("entity-1"); form.setFields(List.of(start, end));
        return form;
    }

    @Test
    void historicalFieldValidationArraysDoNotEnableCrossFieldChecks() {
        EntityForm form = form();
        form.getFields().get(1).setValidationRules("[{\"min\":1}]");
        assertFalse(validator.hasRules(form));
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", Map.of("start", 10, "end", 5)));
    }

    @Test
    void comparesHiddenReferenceAndReportsStableFieldIdentity() {
        EntityForm form = form();
        var error = assertThrows(FormCrossFieldValidationException.class, () -> validator.validateRecord(form, "edit", Map.of("start", 10, "end", 5)));
        assertEquals("FORM_CROSS_FIELD_VALIDATION_FAILED", error.getErrorCode());
        assertEquals(new FormCrossFieldValidationException.FieldError("end", "range", "start", "结束不得早于开始"), error.getFieldErrors().get(0));
        assertDoesNotThrow(() -> validator.validateRecord(form, "create", Map.of("start", 10, "end", 10)));
        assertDoesNotThrow(() -> validator.validateRecord(form, "approve", Map.of("start", 0, "end", 0)));
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", Map.of("start", 10, "end", "")));
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", Map.of()));
        assertThrows(FormCrossFieldValidationException.class, () -> validator.validateRecord(form, "edit", Map.of("start", 10, "end", "invalid")));
    }

    @Test
    void recomputesStateWithFinalDataAndSkipsReadonlyHiddenDisabled() {
        EntityForm form = form(); EntityFormField end = form.getFields().get(1);
        Map<String, Object> invalid = Map.of("start", 10, "end", 5);
        assertDoesNotThrow(() -> validator.validateRecord(form, "view", invalid));
        end.setIsReadonly(1);
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", invalid));
        end.setIsReadonly(0); end.setIsHidden(1);
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", invalid));
        end.setIsHidden(0); end.setExtensionConfig("{\"modes\":{\"approve\":{\"editable\":false}}}");
        assertDoesNotThrow(() -> validator.validateRecord(form, "approve", invalid));
        end.setExtensionConfig(null);
        end.setComponentProps("{\"linkageRules\":{\"visibilityRule\":\"show == true\",\"disabledRule\":\"locked == true\"}}");
        Map<String, Object> values = new LinkedHashMap<>(invalid); values.put("show", false);
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", values));
        values.put("show", true); values.put("locked", true);
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", values));
        values.put("locked", false);
        assertThrows(FormCrossFieldValidationException.class, () -> validator.validateRecord(form, "edit", values));
    }

    @Test
    void layoutFoldDoesNotSkipButBusinessHiddenAncestorDoes() {
        EntityForm form = form();
        EntityFormNode tab = new EntityFormNode(); tab.setId("tab"); tab.setNodeType("TAB_PANE"); tab.setPropsDocument("{\"collapsed\":true}");
        EntityFormNode start = new EntityFormNode(); start.setId("s"); start.setNodeType("FIELD"); start.setBindingType("ENTITY_FIELD");
        EntityFormNode end = new EntityFormNode(); end.setId("e"); end.setParentId("tab"); end.setNodeType("FIELD"); end.setBindingType("ENTITY_FIELD");
        form.setNodes(List.of(tab, start, end));
        assertThrows(FormCrossFieldValidationException.class, () -> validator.validateRecord(form, "edit", Map.of("start", 10, "end", 5)));
        tab.setPropsDocument("{\"hidden\":true}");
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", Map.of("start", 10, "end", 5)));
        tab.setPropsDocument("{\"modeAccess\":{\"edit\":\"READONLY\"}}");
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", Map.of("start", 10, "end", 5)));
    }

    @Test
    void partialPatchRetainsReferenceAndExplicitNullClearsWithoutSpoofingSystemValues() {
        Map<String, Object> existing = Map.of("data", Map.of("start", 10, "end", 12), "status", "DRAFT");
        Map<String, Object> result = PublishedFormRecordView.merge(existing, Map.of("data", Map.of("end", 8), "status", "APPROVED"));
        assertEquals(10, result.get("start")); assertEquals("DRAFT", result.get("status"));
        assertThrows(FormCrossFieldValidationException.class, () -> validator.validateRecord(form(), "edit", result));
        Map<String, Object> clear = new LinkedHashMap<>(); clear.put("end", null);
        Map<String, Object> cleared = PublishedFormRecordView.merge(existing, Map.of("data", clear));
        assertTrue(cleared.containsKey("end")); assertNull(cleared.get("end"));
        assertDoesNotThrow(() -> validator.validateRecord(form(), "edit", cleared));
    }

    @Test
    void legacyNoneBindingStillEnforcesDatetimeComparison() {
        EntityForm form = form();
        List<EntityFormNode> nodes = form.getFields().stream().map(field -> {
            EntityFormNode node = new EntityFormNode();
            node.setId(field.getId()); node.setNodeType("FIELD"); node.setBindingType("NONE");
            field.setFieldId("entity-" + field.getFieldCode()); field.setFieldType("DATETIME");
            return node;
        }).toList();
        form.setNodes(nodes);
        Map<String, Object> invalid = Map.of("start", "2026-09-16 10:00:00", "end", "2026-09-16 09:00:00");
        assertThrows(FormCrossFieldValidationException.class, () -> validator.validateRecord(form, "edit", invalid));
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", Map.of("start", "2026-09-16 10:00:00", "end", "2026-09-16 10:00:00")));
        form.getFields().get(1).setIsHidden(1);
        assertDoesNotThrow(() -> validator.validateRecord(form, "edit", invalid));
    }
}
