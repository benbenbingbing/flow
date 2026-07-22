package com.workflow.controller;

import com.workflow.common.ForbiddenException;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.dto.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.EntityForm;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityFormService;
import com.workflow.service.EntityListConfigService;
import com.workflow.service.UiConfigDraftMetadataService;
import com.workflow.service.UiConfigurationAccessService;
import com.workflow.service.UiExtensionDefinitionService;
import com.workflow.service.listfield.ListFieldDataProviderRegistry;
import com.workflow.service.permission.EntityActionCapabilityService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UiConfigurationWriteAccessControllerTest {

    @Test
    void formWriteStopsBeforeBusinessServiceWhenAccessIsDenied() {
        EntityFormService formService = mock(EntityFormService.class);
        UiConfigDraftMetadataService metadataService =
                mock(UiConfigDraftMetadataService.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        EntityForm form = new EntityForm();
        doThrow(new ForbiddenException("forbidden"))
                .when(accessService).requireNewFormAccess(form);
        EntityFormController controller = new EntityFormController(
                formService, metadataService, accessService);

        assertThrows(ForbiddenException.class, () -> controller.save(form));

        verifyNoInteractions(formService, metadataService);
    }

    @Test
    void formWriteChecksAccessBeforeCallingBusinessService() {
        EntityFormService formService = mock(EntityFormService.class);
        UiConfigDraftMetadataService metadataService =
                mock(UiConfigDraftMetadataService.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        EntityForm form = new EntityForm();
        EntityFormController controller = new EntityFormController(
                formService, metadataService, accessService);

        controller.save(form);

        InOrder order = inOrder(accessService, formService);
        order.verify(accessService).requireNewFormAccess(form);
        order.verify(formService).saveForm(form);
    }

    @Test
    void listWriteStopsBeforeBusinessServiceWhenAccessIsDenied() {
        EntityListConfigService listService =
                mock(EntityListConfigService.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        EntityListConfigDTO dto = new EntityListConfigDTO();
        doThrow(new ForbiddenException("forbidden"))
                .when(accessService).requireNewListAccess(dto);
        EntityListConfigController controller = listController(
                listService, accessService);

        assertThrows(ForbiddenException.class, () -> controller.save(dto));

        verifyNoInteractions(listService);
    }

    @Test
    void listWriteChecksAccessBeforeCallingBusinessService() {
        EntityListConfigService listService =
                mock(EntityListConfigService.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        EntityListConfigDTO dto = new EntityListConfigDTO();
        EntityListConfigController controller = listController(
                listService, accessService);

        controller.save(dto);

        InOrder order = inOrder(accessService, listService);
        order.verify(accessService).requireNewListAccess(dto);
        order.verify(listService).saveConfig(dto);
    }

    @Test
    void globalExtensionWriteChecksAccessBeforeCallingBusinessService() {
        UiExtensionDefinitionService extensionService =
                mock(UiExtensionDefinitionService.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        UiExtensionDefinitionSaveRequest request =
                new UiExtensionDefinitionSaveRequest();
        UiExtensionDefinitionController controller =
                new UiExtensionDefinitionController(
                        extensionService, accessService);

        controller.create(request);

        InOrder order = inOrder(accessService, extensionService);
        order.verify(accessService).requireGlobalConfigurationAccess();
        order.verify(extensionService).save(request);
    }

    @Test
    void globalExtensionWriteStopsBeforeBusinessServiceWhenAccessIsDenied() {
        UiExtensionDefinitionService extensionService =
                mock(UiExtensionDefinitionService.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        doThrow(new ForbiddenException("forbidden"))
                .when(accessService).requireGlobalConfigurationAccess();
        UiExtensionDefinitionController controller =
                new UiExtensionDefinitionController(
                        extensionService, accessService);

        assertThrows(
                ForbiddenException.class,
                () -> controller.create(new UiExtensionDefinitionSaveRequest()));

        verifyNoInteractions(extensionService);
    }

    private EntityListConfigController listController(
            EntityListConfigService listService,
            UiConfigurationAccessService accessService) {
        return new EntityListConfigController(
                listService,
                mock(UiConfigDraftMetadataService.class),
                mock(EntityDataDynamicService.class),
                mock(EntityActionCapabilityService.class),
                mock(ListFieldDataProviderRegistry.class),
                accessService);
    }
}
