package com.workflow.service;

import com.workflow.admin.extension.action.application.FlowActionCatalogService;
import com.workflow.admin.extension.catalog.application.ExtensionCatalogService;
import com.workflow.admin.extension.person.application.PersonResolverCatalogService;
import com.workflow.contracts.ui.catalog.UiExtensionCatalogItem;
import com.workflow.contracts.ui.catalog.UiExtensionCatalogPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 扩展目录实体范围过滤测试。
 */
class ExtensionCatalogServiceTest {

    @Test
    void filtersScopedUiFormsByCurrentEntity() {
        UiExtensionCatalogPort uiPort =
                mock(UiExtensionCatalogPort.class);
        when(uiPort.listCatalogItems()).thenReturn(List.of(
                item("ScopedForm", "ENTITY", Set.of("project")),
                item("GlobalForm", "GLOBAL", Set.of())));
        ExtensionCatalogService service = new ExtensionCatalogService(
                mock(FlowActionCatalogService.class),
                mock(PersonResolverCatalogService.class),
                uiPort);

        assertEquals(2, service.options(
                "UI_FORM", null, 20, null, null, "project").size());
        assertEquals(1, service.options(
                "UI_FORM", null, 20, null, null, "requirement").size());
        assertEquals(1, service.options(
                "UI_FORM", null, 20, null, null, null).size());
    }

    private UiExtensionCatalogItem item(
            String key,
            String visibilityScope,
            Set<String> entityCodes) {
        return new UiExtensionCatalogItem(
                key,
                "FORM",
                key,
                key,
                1,
                1,
                "ACTIVE",
                visibilityScope,
                entityCodes,
                Set.of("CREATE", "EDIT", "APPROVE", "VIEW"),
                Set.of(),
                Set.of(),
                List.of(),
                Map.of(),
                1);
    }
}
