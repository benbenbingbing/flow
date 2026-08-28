package com.workflow.entity.form.uniqueness.application;

import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormUniqueMutationContextTest {

    @Test
    void resolvesServerFormReleaseMetadata() {
        EntityMutationContext context = EntityMutationContext.builder(
                        EntityMutationSourceType.FORM,
                        "EDIT_RECORD",
                        "编辑")
                .sourceId("legacy-source")
                .extraParams(Map.of(
                        FormUniqueMutationContext.FORM_ID,
                        "form-1",
                        FormUniqueMutationContext.FORM_RELEASE_ID,
                        "release-3",
                        FormUniqueMutationContext.FORM_RELEASE_VERSION,
                        3,
                        FormUniqueMutationContext.FORM_EFFECTIVE_RELEASE_ID,
                        "hotfix-4",
                        FormUniqueMutationContext.FORM_EFFECTIVE_CONTENT_HASH,
                        "hash-4",
                        FormUniqueMutationContext.FORM_HOTFIX_TARGET_ID,
                        "target-4"))
                .build();

        FormUniqueMutationContext.Reference reference =
                FormUniqueMutationContext.resolve(context)
                        .orElseThrow();

        assertEquals("form-1", reference.formId());
        assertEquals("release-3", reference.releaseId());
        assertEquals(3, reference.releaseVersion());
        assertEquals(
                "hotfix-4",
                reference.effectiveReleaseId());
        assertEquals("hash-4", reference.effectiveContentHash());
        assertEquals("target-4", reference.hotfixTargetId());
    }

    @Test
    void nonFormSourceNeverActivatesFormRules() {
        EntityMutationContext context = EntityMutationContext.builder(
                        EntityMutationSourceType.LIST,
                        "EDIT_RECORD",
                        "编辑")
                .sourceId("form-1")
                .extraParams(Map.of(
                        FormUniqueMutationContext.FORM_ID,
                        "form-1"))
                .build();

        assertTrue(FormUniqueMutationContext
                .resolve(context)
                .isEmpty());
    }

    @Test
    void genericFormSourceWithoutPublishedFormMetadataIsSkipped() {
        EntityMutationContext context = EntityMutationContext.builder(
                        EntityMutationSourceType.FORM,
                        "EDIT_RECORD",
                        "编辑")
                .sourceId("form-1")
                .build();

        assertTrue(FormUniqueMutationContext
                .resolve(context)
                .isEmpty());
    }

    @Test
    void approvalTaskResolvesAllAppliedFormsWithoutChangingSourceType() {
        List<FormUniqueMutationContext.Reference> references = List.of(
                new FormUniqueMutationContext.Reference(
                        "form-1",
                        "release-1",
                        1,
                        "hotfix-2",
                        "hash-2",
                        "target-2"),
                new FormUniqueMutationContext.Reference(
                        "form-2", "release-3", 3, "release-3"));
        Map<String, Object> extra = new java.util.LinkedHashMap<>();
        extra.put("taskDefinitionKey", "Task_Review");
        extra.putAll(FormUniqueMutationContext.encodeReferences(
                references));
        EntityMutationContext context = EntityMutationContext.builder(
                        EntityMutationSourceType.APPROVAL_TASK,
                        "APPROVAL_FORM_EDIT",
                        "审批表单编辑")
                .extraParams(extra)
                .build();

        assertEquals(
                EntityMutationSourceType.APPROVAL_TASK,
                context.sourceType());
        assertEquals(
                references,
                FormUniqueMutationContext.resolveAll(context));
        assertTrue(!String.valueOf(context.extraParams())
                .contains("Token"));
        assertTrue(String.valueOf(context.extraParams())
                .contains("target-2"));
    }

    @Test
    void processRuntimeNeverActivatesApprovalFormReferences() {
        EntityMutationContext context = EntityMutationContext.builder(
                        EntityMutationSourceType.PROCESS_RUNTIME,
                        "PROCESS_END",
                        "流程结束")
                .extraParams(FormUniqueMutationContext.encodeReferences(
                        List.of(new FormUniqueMutationContext.Reference(
                                "form-1",
                                "release-1",
                                1,
                                "release-1"))))
                .build();

        assertTrue(FormUniqueMutationContext
                .resolveAll(context)
                .isEmpty());
    }
}
