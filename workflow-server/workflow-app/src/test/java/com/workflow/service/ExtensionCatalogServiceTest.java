package com.workflow.service;

import com.workflow.admin.extension.action.application.FlowActionCatalogService;
import com.workflow.admin.extension.catalog.application.ExtensionCatalogService;
import com.workflow.admin.extension.person.application.PersonResolverCatalogService;
import com.workflow.contracts.entity.ui.port.UiExtensionCatalogPort;
import com.workflow.contracts.entity.ui.spi.UiActionCommandPlanProvider;
import com.workflow.contracts.entity.ui.spi.UiDataSourceProvider;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.ui.catalog.UiExtensionCatalogItem;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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
                uiPort,
                mock(ObjectProvider.class),
                mock(ObjectProvider.class));

        assertEquals(2, service.options(
                "UI_FORM", null, 20, null, null, "project").size());
        assertEquals(1, service.options(
                "UI_FORM", null, 20, null, null, "requirement").size());
        assertEquals(1, service.options(
                "UI_FORM", null, 20, null, null, null).size());
    }

    @Test
    void resolvesAndFiltersInterfaceImplementationOrigin() {
        UiExtensionCatalogPort uiPort = mock(UiExtensionCatalogPort.class);
        when(uiPort.listCatalogItems()).thenReturn(List.of(
                interfaceItem("dictionary", "DICTIONARY", null),
                interfaceItem(
                        "projectProvider",
                        "REGISTERED_PROVIDER",
                        "projectCustom")));
        UiDataSourceProvider projectProvider = mock(
                UiDataSourceProvider.class);
        when(projectProvider.getCode()).thenReturn("projectCustom");
        when(projectProvider.implementationOrigin()).thenReturn(
                ExtensionImplementationOrigin.CUSTOM);
        ObjectProvider<UiDataSourceProvider> dataSourceProviders =
                mock(ObjectProvider.class);
        when(dataSourceProviders.stream()).thenAnswer(
                ignored -> Stream.of(projectProvider));
        ExtensionCatalogService service = new ExtensionCatalogService(
                mock(FlowActionCatalogService.class),
                mock(PersonResolverCatalogService.class),
                uiPort,
                dataSourceProviders,
                mock(ObjectProvider.class));

        var customPage = service.manage(
                null, null, null, "CUSTOM", 1, 1);

        assertEquals(1, customPage.getTotal());
        assertEquals("projectProvider",
                customPage.getRecords().get(0).getKey());
        assertEquals("CUSTOM", customPage.getRecords().get(0)
                .getImplementationOrigin());
        assertEquals("PLATFORM", service.allItems().stream()
                .filter(item -> "dictionary".equals(item.getKey()))
                .findFirst()
                .orElseThrow()
                .getImplementationOrigin());
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
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                1);
    }

    private UiExtensionCatalogItem interfaceItem(
            String key,
            String implementationType,
            String providerCode) {
        return new UiExtensionCatalogItem(
                key,
                "INTERFACE",
                key,
                key,
                1,
                1,
                "ACTIVE",
                "GLOBAL",
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                List.of(),
                Map.of(),
                implementationType,
                providerCode,
                "GLOBAL",
                null,
                "READ",
                "ENTITY",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                1);
    }
}
