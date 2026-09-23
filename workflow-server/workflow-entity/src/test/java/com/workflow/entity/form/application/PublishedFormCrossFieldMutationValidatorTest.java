package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.core.error.FormCrossFieldValidationException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublishedFormCrossFieldMutationValidatorTest {
    @Test
    void finalRecordUsesExactTrustedSnapshotAndIgnoresUnrelatedSources() {
        var release = mock(UiConfigReleaseService.class);
        var mapper = new ObjectMapper();
        var validator = new PublishedFormCrossFieldMutationValidator(release,
                new PublishedFormCrossFieldValidator(mock(EntityDataDynamicService.class), mapper,
                        new JsonDocumentCodec(mapper), new PublishedFormConditionEvaluator(mapper)));
        var form = PublishedFormCrossFieldValidatorTest.form();
        when(release.resolveTrustedEffectiveFormRelease("form-1", "release-1", 1, "effective-2", "hash", "target"))
                .thenReturn(new ResolvedEntityFormRelease(form, "release-1", 1, true));
        Map<String, Object> identity = Map.of("formId", "form-1", "formReleaseId", "release-1", "formReleaseVersion", 1,
                "formEffectiveReleaseId", "effective-2", "formEffectiveContentHash", "hash", "formHotfixTargetId", "target");
        var context = EntityMutationContext.builder(EntityMutationSourceType.FORM, "UPDATE", "更新").extraParams(identity).build();
        var command = EntityMutationCommand.update("expense", "record-1", Map.of("end", 10), context);
        assertThrows(FormCrossFieldValidationException.class, () -> validator.validate(command, Map.of("data", Map.of("start", 12, "end", 10))));
        verify(release).resolveTrustedEffectiveFormRelease("form-1", "release-1", 1, "effective-2", "hash", "target");
        clearInvocations(release);
        var readonly = EntityMutationContext.builder(EntityMutationSourceType.APPROVAL_TASK, "APPROVE", "审批")
                .extraParams(Map.of("formReferences", List.of(identity), FormCrossFieldRuntimeContext.READONLY_FORM_IDS, List.of("form-1"))).build();
        assertDoesNotThrow(() -> validator.validate(EntityMutationCommand.update("expense", "record-1", Map.of(), readonly), Map.of("start", 12, "end", 10)));
        verifyNoInteractions(release);
        var noForm = EntityMutationContext.builder(EntityMutationSourceType.APPROVAL_TASK, "APPROVE", "审批").extraParams(identity).build();
        validator.validate(EntityMutationCommand.update("expense", "record-1", Map.of(), noForm), Map.of("start", 12, "end", 10));
        verifyNoInteractions(release);
    }
}
