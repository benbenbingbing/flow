package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.excel.model.ExcelResolvedScheme;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.application.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityExcelAtomicImportPolicyTest {
    private final UiConfigReleaseService releases = mock(UiConfigReleaseService.class);
    private final UiEventBindingService bindings = mock(UiEventBindingService.class);
    private final UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
    private final EntityExcelAtomicImportPolicy policy = new EntityExcelAtomicImportPolicy(releases, bindings, interfaces, new ObjectMapper());

    @Test void checksLegacyServiceReferenceIncludingBlankExtensionId() {
        chain(List.of(Map.of("extensionId", " ", "serviceId", "legacy", "operationCode", "save", "strategy", "BEFORE")), Map.of());
        definition("legacy", "save", "WRITE", "{}");
        assertThatThrownBy(() -> policy.requireSupported(plan(), "signed")).hasMessageContaining("外部写入");
        verify(interfaces).freezeOperation("legacy", "save");
    }

    @Test void usesActualTokenResolvedSnapshotRatherThanOldActiveRelease() {
        chain(List.of(), Map.of("form", Map.of("dataSourceBindings", " {\"BEFORE_SUBMIT\":{\"extensionId\":\"before\",\"sideEffectFree\":false}}")));
        assertThatThrownBy(() -> policy.requireSupported(plan(), "signed")).hasMessageContaining("无副作用");
        verifyNoInteractions(releases, interfaces);
    }

    @Test void acceptsReadOnlySideEffectFreeBindingsAndReadEvents() {
        chain(List.of(Map.of("extensionId", "before", "strategy", "BEFORE")),
                Map.of("BEFORE_SUBMIT", List.of(Map.of("extensionId", "before", "sideEffectFree", true))));
        definition("before", null, "READ", "{}");
        assertThatCode(() -> policy.requireSupported(plan(), "signed")).doesNotThrowAnyException();
    }

    @Test void cannotReplaceStandardSaveOrItsResultOrIgnoreEventFailure() {
        for (var step : List.of(Map.<String, Object>of("strategy", "replace"),
                Map.<String, Object>of("strategy", "AFTER", "replaceResult", true), Map.<String, Object>of("failurePolicy", "CONTINUE"))) {
            chain(List.of(step), Map.of());
            assertThatThrownBy(() -> policy.requireSupported(plan(), "signed")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void interfaceFallbackCannotConvertValidationFailureIntoSuccess() {
        chain(List.of(Map.of("extensionId", "before")), Map.of());
        definition("before", null, "READ", "{\"failurePolicy\":\"EMPTY\"}");
        assertThatThrownBy(() -> policy.requireSupported(plan(), "signed")).hasMessageContaining("失败即终止");
    }

    private void chain(List<Map<String, Object>> steps, Map<String, Object> snapshot) {
        when(bindings.resolvePublished(any())).thenReturn(new UiEventBindingService.ResolvedEventChain(steps,
                "hash", 1, "entity", "employee", null, snapshot));
    }

    private void definition(String id, String operation, String kind, String executionPolicy) {
        try {
            String document = new ObjectMapper().writeValueAsString(Map.of("interfaceKind", kind, "executionPolicyDocument", executionPolicy));
            when(interfaces.freezeOperation(id, operation)).thenReturn(new UiInterfaceExtensionService.PublishedOperationSnapshot(id, id, 1, operation, document, "hash"));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private EntityExcelRuntimeResolver.Plan plan() {
        var form = new EntityForm(); form.setId("form");
        return new EntityExcelRuntimeResolver.Plan(null, new ExcelResolvedScheme("entity", "employee", "basic", null, "hash", null, "form"),
                null, null, new ResolvedEntityFormRelease(form, "hash", 1));
    }
}
