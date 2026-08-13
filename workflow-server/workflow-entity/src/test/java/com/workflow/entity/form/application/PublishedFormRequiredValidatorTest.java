package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublishedFormRequiredValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityDataDynamicService dataService =
            mock(EntityDataDynamicService.class);
    private final EntityFieldMapper entityFieldMapper =
            mock(EntityFieldMapper.class);
    private final EntityFieldFileItemMapper fileItemMapper =
            mock(EntityFieldFileItemMapper.class);
    private final PublishedFormRequiredValidator validator =
            new PublishedFormRequiredValidator(
                    dataService,
                    entityFieldMapper,
                    fileItemMapper,
                    new PublishedFormConditionEvaluator(objectMapper),
                    new JsonDocumentCodec(objectMapper),
                    objectMapper);

    @Test
    void requiresOneFileWhenNestedConditionMatches() {
        EntityForm form = form(false, false);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> validator.validate(
                        form,
                        "requirement",
                        null,
                        "create",
                        Map.of(
                                "stage", "REVIEW",
                                "urgent", false,
                                "amount", 120)));

        assertEquals(
                PublishedFormRequiredValidator.ERROR_CODE,
                failure.getErrorCode());
        assertTrue(failure.getMessage().contains("项目附件"));
        assertTrue(failure.getMessage().contains("合同终稿"));
        assertDoesNotThrow(() -> validator.validate(
                form,
                "requirement",
                null,
                "create",
                Map.of(
                        "stage", "DRAFT",
                        "urgent", true,
                        "amount", 120)));
    }

    @Test
    void acceptsOldAttachmentNameAndMergesExistingEditData() {
        EntityForm form = form(false, false);
        EntityDataDTO existing = new EntityDataDTO();
        existing.setId("record-1");
        existing.setData(new LinkedHashMap<>(Map.of(
                "stage", "REVIEW",
                "urgent", true,
                "amount", 10,
                "documents", Map.of(
                        "合同初稿",
                        List.of(Map.of(
                                "url",
                                "/files/contract.pdf"))))));
        when(dataService.findById("requirement", "record-1"))
                .thenReturn(existing);

        assertDoesNotThrow(() -> validator.validate(
                form,
                "requirement",
                "record-1",
                "edit",
                Map.of("amount", 20)));

        existing.setData(new LinkedHashMap<>(Map.of(
                "stage", "REVIEW",
                "urgent", true,
                "amount", 10)));
        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> validator.validate(
                        form,
                        "requirement",
                        "record-1",
                        "approve",
                        Map.of()));
        assertEquals(
                PublishedFormRequiredValidator.ERROR_CODE,
                failure.getErrorCode());

        EntityField documents = new EntityField();
        documents.setId("field-documents");
        documents.setFieldCode("documents");
        EntityFieldFileItem renamed = new EntityFieldFileItem();
        renamed.setItemKey("afi_contract");
        renamed.setItemName("合同归档件");
        renamed.setNameAliases("[\"合同终稿\",\"合同初稿\"]");
        when(entityFieldMapper.findByEntityId("entity-requirement"))
                .thenReturn(List.of(documents));
        when(fileItemMapper.findByFieldId("field-documents"))
                .thenReturn(List.of(renamed));
        existing.setData(new LinkedHashMap<>(Map.of(
                "stage", "REVIEW",
                "urgent", true,
                "documents", Map.of(
                        "合同终稿", List.of(),
                        "合同归档件", List.of(Map.of(
                                "url",
                                "/files/current-name.pdf"))))));
        assertDoesNotThrow(() -> validator.validate(
                form,
                "requirement",
                "record-1",
                "edit",
                Map.of()));
    }

    @Test
    void submittedAttachmentFieldCannotBorrowOmittedItemFromOldValue() {
        EntityForm form = form(false, false);
        EntityDataDTO existing = new EntityDataDTO();
        existing.setId("record-1");
        existing.setData(new LinkedHashMap<>(Map.of(
                "stage", "REVIEW",
                "urgent", true,
                "documents", Map.of(
                        "合同初稿",
                        List.of(Map.of(
                                "url",
                                "/files/contract.pdf"))))));
        when(dataService.findById("requirement", "record-1"))
                .thenReturn(existing);

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> validator.validate(
                        form,
                        "requirement",
                        "record-1",
                        "edit",
                        Map.of(
                                "documents",
                                Map.of(
                                        "许可证",
                                        List.of(Map.of(
                                                "url",
                                                "/files/license.pdf"))))));

        assertEquals(
                PublishedFormRequiredValidator.ERROR_CODE,
                failure.getErrorCode());
        assertTrue(failure.getMessage().contains("合同终稿"));

        BusinessConflictException forgedValueFailure = assertThrows(
                BusinessConflictException.class,
                () -> validator.validate(
                        form,
                        "requirement",
                        "record-1",
                        "edit",
                        Map.of(
                                "documents",
                                Map.of(
                                        "合同终稿",
                                        Map.of(
                                                "name", "fake.pdf",
                                                "status", "success")))));
        assertEquals(
                PublishedFormRequiredValidator.ERROR_CODE,
                forgedValueFailure.getErrorCode());
    }

    @Test
    void hiddenFieldSkipsLogicalRuleButNotFrozenEntityRequiredItem() {
        assertDoesNotThrow(() -> validator.validate(
                form(true, false),
                "requirement",
                null,
                "create",
                Map.of(
                        "stage", "REVIEW",
                        "urgent", true,
                        "amount", 120)));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> validator.validate(
                        form(true, true),
                        "requirement",
                        null,
                        "create",
                        Map.of(
                                "stage", "DRAFT",
                                "urgent", false,
                                "amount", 1)));
        assertTrue(failure.getMessage().contains("许可证"));
    }

    private EntityForm form(
            boolean hidden,
            boolean staticItemRequired) {
        EntityForm form = new EntityForm();
        form.setEntityId("entity-requirement");
        form.setFields(List.of(
                field("stage", "STRING", null),
                field("urgent", "BOOLEAN", null),
                field("amount", "DECIMAL", null),
                field(
                        "documents",
                        "FILE",
                        attachmentComponentProps(
                                hidden,
                                staticItemRequired))));
        return form;
    }

    private EntityFormField field(
            String code,
            String type,
            String componentProps) {
        EntityFormField field = new EntityFormField();
        field.setFieldCode(code);
        field.setFieldName("documents".equals(code)
                ? "项目附件" : code);
        field.setFieldLabel(field.getFieldName());
        field.setFieldType(type);
        field.setIsRequired(0);
        field.setIsHidden(0);
        field.setComponentProps(componentProps);
        return field;
    }

    private String attachmentComponentProps(
            boolean hidden,
            boolean staticItemRequired) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("fileItems", List.of(
                Map.of(
                        "itemKey", "afi_contract",
                        "itemName", "合同终稿",
                        "nameAliases", List.of(
                                "合同初稿",
                                "合同"),
                        "required", false,
                        "fileTypes", ".pdf",
                        "maxSize", 20,
                        "maxCount", 3),
                Map.of(
                        "itemKey", "afi_license",
                        "itemName", "许可证",
                        "nameAliases", List.of(),
                        "required", staticItemRequired,
                        "fileTypes", ".pdf",
                        "maxSize", 10,
                        "maxCount", 1)));
        props.put("attachmentItemRequiredRules", Map.of(
                "version", 1,
                "items", List.of(Map.of(
                        "itemKey", "afi_contract",
                        "requiredConditionConfig", nestedCondition()))));
        if (hidden) {
            props.put("linkageRules", Map.of(
                    "visibilityConditionConfig", condition(
                            "showDocuments",
                            "==",
                            "true")));
        }
        try {
            return objectMapper.writeValueAsString(props);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, Object> nestedCondition() {
        return Map.of(
                "version", 1,
                "root", Map.of(
                        "type", "GROUP",
                        "logic", "AND",
                        "children", List.of(
                                Map.of(
                                        "type", "CONDITION",
                                        "property", "stage",
                                        "operator", "==",
                                        "value", "REVIEW"),
                                Map.of(
                                        "type", "GROUP",
                                        "logic", "OR",
                                        "children", List.of(
                                                Map.of(
                                                        "type", "CONDITION",
                                                        "property", "urgent",
                                                        "operator", "==",
                                                        "value", "true"),
                                                Map.of(
                                                        "type", "CONDITION",
                                                        "property", "amount",
                                                        "operator", ">=",
                                                        "value", "100"))))));
    }

    private Map<String, Object> condition(
            String property,
            String operator,
            String value) {
        return Map.of(
                "version", 1,
                "root", Map.of(
                        "type", "GROUP",
                        "logic", "AND",
                        "children", List.of(Map.of(
                                "type", "CONDITION",
                                "property", property,
                                "operator", operator,
                                "value", value))));
    }
}
