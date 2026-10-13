package com.workflow.entity.excel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExcelButtonAvailabilityServiceTest {
    private final EntityExcelConfigurationService config = mock(EntityExcelConfigurationService.class);
    private final UiEventBindingService bindings = mock(UiEventBindingService.class);
    private final UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ExcelButtonAvailabilityService service = new ExcelButtonAvailabilityService(config, provider(bindings), provider(interfaces), json);
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
    private void event(String mode, String provider) throws Exception {
        Map<String, Object> ref = "DEFAULT".equals(mode) ? Map.of("mode", mode) : Map.of("mode", mode, "schemeCode", "finance");
        String document = json.writeValueAsString(Map.of("providerCode", provider, "implementationConfigDocument",
                json.writeValueAsString(Map.of("operation", "EXPORT", "schemeRef", ref))));
        var chain = new UiEventBindingService.ResolvedEventChain(List.of(Map.of("strategy", "REPLACE", "executableSnapshot", document)),
                "release", 1, "entity", "employee", null, Map.of());
        when(bindings.resolvePublished(any())).thenReturn(chain);
    }
    @Test void formDefaultFailureDisablesOnlyEventModeButton() throws Exception {
        event("DEFAULT", "entity_excel_action");
        when(config.resolvePublished("employee", "EXPORT", "DEFAULT", null)).thenThrow(new BusinessConflictException("EXCEL_CONFIGURATION_CHANGED", "请先配置默认导出方案"));
        var form = new EntityForm(); form.setId("form1");
        assertThat(service.formReason("employee", form, Map.of("key", "export", "customMode", "event"))).contains("默认导出");
        clearInvocations(bindings, config);
        assertThat(service.formReason("employee", form, Map.of("key", "export", "customMode", "function"))).isNull();
        verifyNoInteractions(bindings, config);
    }
    @Test void listExplicitFailureDoesNotFallBackAndRequiresPublishedList() throws Exception {
        event("EXPLICIT", "entity_excel_action");
        when(config.resolvePublished("employee", "EXPORT", "EXPLICIT", "finance")).thenThrow(new IllegalArgumentException("指定方案已失效"));
        var list = new EntityListConfig(); list.setId("list1"); list.setListKey("employees"); list.setPublishedSnapshot(true);
        var buttons = List.<Map<String,Object>>of(Map.of("key", "finance", "type", "custom"));
        assertThat(service.listReasons("employee", list, buttons, "ROW_BUTTON_CLICK")).containsEntry("finance", "指定方案已失效");
        verify(config).resolvePublished("employee", "EXPORT", "EXPLICIT", "finance");
        clearInvocations(config, bindings);
        list.setPublishedSnapshot(false);
        assertThat(service.listReasons("employee", list, buttons, "ROW_BUTTON_CLICK")).isEmpty();
        verifyNoInteractions(config, bindings);
    }
    @Test void ordinaryEventProvidersNeverQueryExcelConfiguration() throws Exception {
        event("DEFAULT", "ordinary-provider");
        var form = new EntityForm(); form.setId("f1");
        assertThat(service.formReason("employee", form, Map.of("key", "ordinary"))).isNull();
        verifyNoInteractions(config);
    }
    @Test void newFormCannotExportCurrentUnsavedRecord() throws Exception {
        event("DEFAULT", "entity_excel_action");
        var form = new EntityForm(); form.setId("form1");
        assertThat(service.formReason("employee", form, Map.of("key", "export"))).contains("先保存");
        assertThat(service.formReason("employee", form, Map.of("key", "export"), "record1")).isNull();
    }
    private void builtin(String mode, String scope) {
        Map<String, Object> reference = "DEFAULT".equals(mode) ? Map.of("mode", mode)
                : Map.of("mode", mode, "schemeCode", "finance");
        var step = Map.<String, Object>of("actionType", "ENTITY_EXCEL", "strategy", "REPLACE", "actionConfig",
                Map.of("operation", "EXPORT", "dataScope", scope, "schemeRef", reference));
        when(bindings.resolvePublished(any())).thenReturn(new UiEventBindingService.ResolvedEventChain(
                List.of(step), "release", 1, "entity", "employee", null, Map.of()));
    }
    @Test void builtinFormUsesStaticConfigurationWithoutExtensionLookup() {
        builtin("DEFAULT", "CURRENT_RECORD");
        var form = new EntityForm(); form.setId("form1");
        assertThat(service.formReason("employee", form, Map.of("key", "export"))).contains("先保存");
        assertThat(service.formReason("employee", form, Map.of("key", "export"), "record1")).isNull();
        verifyNoInteractions(interfaces);
        verify(config, times(2)).resolvePublished("employee", "EXPORT", "DEFAULT", null);
    }
    @Test void builtinListUnavailableExplicitSchemeNeverFallsBackToDefault() {
        builtin("EXPLICIT", "SELECTED");
        when(config.resolvePublished("employee", "EXPORT", "EXPLICIT", "finance"))
                .thenThrow(new IllegalArgumentException("指定方案已失效"));
        var list = new EntityListConfig(); list.setId("list1"); list.setListKey("employees"); list.setPublishedSnapshot(true);
        assertThat(service.listReasons("employee", list, List.of(Map.of("key", "export", "type", "custom")), "TOOLBAR_BUTTON_CLICK"))
                .containsEntry("export", "指定方案已失效");
        verifyNoInteractions(interfaces);
        verify(config, never()).resolvePublished(any(), any(), eq("DEFAULT"), any());
    }
    @Test void legacyListReadsCurrentInterfaceRatherThanStaleExecutableSnapshot() throws Exception {
        String oldConfig = json.writeValueAsString(Map.of("providerCode", "entity_excel_action", "implementationConfigDocument",
                Map.of("operation", "EXPORT", "schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "old"))));
        String current = json.writeValueAsString(Map.of("providerCode", "entity_excel_action", "implementationConfigDocument",
                Map.of("operation", "EXPORT", "schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "finance"))));
        when(bindings.resolvePublished(any())).thenReturn(new UiEventBindingService.ResolvedEventChain(
                List.of(Map.of("extensionId", "legacy", "strategy", "REPLACE", "executableSnapshot", oldConfig)),
                "release", 1, "entity", "employee", null, Map.of()));
        when(interfaces.freezeOperation("legacy", null)).thenReturn(new UiInterfaceExtensionService.PublishedOperationSnapshot(
                "legacy", "legacy", 1, null, current, "hash"));
        var list = new EntityListConfig(); list.setId("list1"); list.setPublishedSnapshot(true);
        service.listReasons("employee", list, List.of(Map.of("key", "export", "type", "custom")), "TOOLBAR_BUTTON_CLICK");
        verify(config).resolvePublished("employee", "EXPORT", "EXPLICIT", "finance");
        verify(config, never()).resolvePublished(any(), any(), any(), eq("old"));
    }

}
