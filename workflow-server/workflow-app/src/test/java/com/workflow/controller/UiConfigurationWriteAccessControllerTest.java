package com.workflow.controller;

import com.workflow.entity.form.api.web.EntityFormController;
import com.workflow.entity.list.api.web.EntityListConfigController;
import com.workflow.entity.ui.api.web.UiExtensionDefinitionController;

import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.ui.application.UiConfigDraftMetadataService;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiExtensionDefinitionService;
import com.workflow.entity.list.extension.ListFieldDataProviderRegistry;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * UI 配置写操作权限控制器单元测试。
 *
 * <p>被测对象为 {@link EntityFormController}、{@link EntityListConfigController}、
 * {@link UiExtensionDefinitionController}，验证写操作在权限拒绝时不会触达业务服务，
 * 以及权限通过时访问校验先于业务调用执行。</p>
 */
class UiConfigurationWriteAccessControllerTest {

    /**
     * 表单写操作在权限拒绝时应抛出异常且不调用表单/元数据服务。
     */
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
                formService,
                metadataService,
                accessService,
                mock(EntityActionCapabilityService.class));

        assertThrows(ForbiddenException.class, () -> controller.save(form));

        verifyNoInteractions(formService, metadataService);
    }

    /**
     * 表单写操作应先执行权限校验再调用业务保存，两者顺序固定。
     */
    @Test
    void formWriteChecksAccessBeforeCallingBusinessService() {
        EntityFormService formService = mock(EntityFormService.class);
        UiConfigDraftMetadataService metadataService =
                mock(UiConfigDraftMetadataService.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        EntityForm form = new EntityForm();
        EntityFormController controller = new EntityFormController(
                formService,
                metadataService,
                accessService,
                mock(EntityActionCapabilityService.class));

        controller.save(form);

        InOrder order = inOrder(accessService, formService);
        order.verify(accessService).requireNewFormAccess(form);
        order.verify(formService).saveForm(form);
    }

    /**
     * 列表配置写操作在权限拒绝时应抛出异常且不调用列表服务。
     */
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

    /**
     * 列表配置写操作应先执行权限校验再调用业务保存，两者顺序固定。
     */
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

    /**
     * 全局扩展写操作应先执行权限校验再调用扩展保存，两者顺序固定。
     */
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

    /**
     * 全局扩展写操作在权限拒绝时应抛出异常且不调用扩展服务。
     */
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

    /**
     * 构造列表配置控制器实例，注入列表服务与访问服务，其余依赖使用 mock。
     *
     * @param listService 列表配置业务服务
     * @param accessService UI 配置访问权限服务
     * @return 已组装的列表配置控制器
     */
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
