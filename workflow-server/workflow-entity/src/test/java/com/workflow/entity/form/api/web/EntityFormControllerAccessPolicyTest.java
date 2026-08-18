package com.workflow.entity.form.api.web;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.application.UiConfigDraftMetadataService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityFormControllerAccessPolicyTest {

    @Test
    void entityFieldsUseRuntimeMetadataAccessInsteadOfFormAdmin() {
        EntityFormService formService = mock(EntityFormService.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        EntityActionCapabilityService capability =
                mock(EntityActionCapabilityService.class);
        EntityFormController controller = new EntityFormController(
                formService,
                mock(UiConfigDraftMetadataService.class),
                accessService,
                capability);
        when(formService.requireEntityCode("entity-1"))
                .thenReturn("ZDWREQ");
        when(formService.getEntityFields("entity-1"))
                .thenReturn(List.of(new EntityField()));

        controller.getEntityFields("entity-1");

        verify(capability).requireEntityMetadataAccess("ZDWREQ");
        verify(formService).getEntityFields("entity-1");
        verify(accessService, never()).requireEntityFormAccess("entity-1");
    }
}
