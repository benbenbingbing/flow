package com.workflow.entity.excel.runtime;

import com.workflow.contracts.entity.ui.context.CommonInvocationContext;
import com.workflow.contracts.entity.ui.context.ListInvocationContext;
import com.workflow.contracts.entity.ui.context.UiInvocationContext;
import com.workflow.contracts.entity.ui.model.EntityDescriptor;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.entity.ui.application.UiEventOriginContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EntityExcelActionProviderTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<EntityExcelRuntimeResolver> resolvers = mock(ObjectProvider.class);
    private final EntityExcelRuntimeResolver resolver = mock(EntityExcelRuntimeResolver.class);
    private final EntityExcelActionProvider provider = new EntityExcelActionProvider(resolvers);

    @Test
    void catalogMetadataAndUntrustedExecutionDoNotResolveRuntime() {
        assertThat(provider.getCode()).isEqualTo("entity_excel_action");
        assertThat(provider.getDisplayName()).isEqualTo("实体Excel导入导出");
        provider.configurationSchema();
        assertThatThrownBy(() -> provider.execute(context("export"), null, Map.of(), Map.of()))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(resolvers, resolver);
    }

    @Test
    void mismatchedButtonIsRejectedBeforeRuntimeResolution() {
        try (var ignored = UiEventOriginContext.open(origin(), chain())) {
            assertThatThrownBy(() -> provider.execute(context("different-button"), null, Map.of(), Map.of()))
                    .isInstanceOf(ForbiddenException.class);
        }
        verifyNoInteractions(resolvers, resolver);
    }

    @Test
    void trustedButtonResolvesRuntimeAndReturnsItsExcelEffect() {
        var configuration = Map.<String, Object>of("operation", "EXPORT", "schemeRef", Map.of("mode", "DEFAULT"));
        var effect = Map.<String, Object>of("type", "EXCEL_ACTION", "operation", "EXPORT", "actionContextToken", "token");
        when(resolvers.getObject()).thenReturn(resolver);
        when(resolver.prepareCustom("excel-interface", configuration)).thenReturn(effect);
        try (var ignored = UiEventOriginContext.open(origin(), chain())) {
            assertThat(provider.execute(context("export"), null, configuration, Map.of()))
                    .isEqualTo(Map.of("effects", List.of(effect)));
        }
        verify(resolvers).getObject();
        verify(resolver).prepareCustom("excel-interface", configuration);
        assertThat(UiEventOriginContext.current()).isNull();
    }

    private static UiEventExecuteRequest origin() {
        var origin = new UiEventExecuteRequest();
        origin.setConfigId("list1"); origin.setEntityCode("employee"); origin.setTargetKey("export");
        return origin;
    }

    private static UiEventBindingService.ResolvedEventChain chain() {
        return new UiEventBindingService.ResolvedEventChain(List.of(), "release", 1, "e1", "employee", "employees", Map.of());
    }

    private static UiInvocationContext context(String button) {
        var common = new CommonInvocationContext("excel-interface", null, "TOOLBAR_BUTTON_CLICK", "LIST", "list1",
                "BUTTON", button, "alice", "alice", null, null, null, "release", 1, "request");
        return new ListInvocationContext(common, new EntityDescriptor("e1", "employee", "员工", "DYNAMIC", 1),
                "list1", "employees", "员工列表", 1, 20, null, "PAGE");
    }
}
